package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.time.LocalDateTime

/** One missing-return-item persistence result; does not pretend the absent object was received. */
class ReturnMissingItemPersistenceTest {
    @TempDir lateinit var originalRoot: Path

    private fun ordinaryActor(id: String) {
        val authorities=listOf(CollectionConst.STORAGE,CollectionConst.INCIDENT).map(::SimpleGrantedAuthority)
        val user=SecurityUser(id=id,name=id,username=id,password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=authorities,enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,authorities)
    }

    private fun field(fixture: ReturnCompliancePersistenceTest, name: String): Any =
        fixture.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(fixture)

    private fun invoke(fixture: ReturnCompliancePersistenceTest, name: String, vararg values: Any?): Any? {
        val method=fixture.javaClass.declaredMethods.single { it.name==name && it.parameterCount==values.size }.apply { isAccessible=true }
        return try { method.invoke(fixture,*values) } catch (error: InvocationTargetException) { throw error.targetException }
    }

    @Test
    fun `missing return item retains exact problem explanation investigation and not recovered restriction after commit`() {
        val fixture=ReturnCompliancePersistenceTest().also { it.originalRoot=originalRoot }
        fixture.prepare()
        try {
            val returns=field(fixture,"service") as CollectionReturnService
            val statutory=field(fixture,"statutory") as ReturnStatutoryService
            val incidents=field(fixture,"incidents") as CollectionIncidentService
            val locks=field(fixture,"locks") as CollectionOperationLockService
            val at=field(fixture,"at") as LocalDateTime
            var incidentId=""
            var factsId=""
            var handlingId=""
            var pendingId=""
            var input: IncidentFactsVo?=null
            var result: IncidentHandlingVo?=null
            val afterCommit: (org.jetbrains.exposed.v1.jdbc.Database,String,String)->Unit={ db,loanId,returnId ->
                transaction(db) {
                    ordinaryActor("operator")
                    assertEquals("one,two",LoanDao[loanId].collectionIds)
                    assertFalse(LoanDao[loanId].returned)
                    assertEquals("one",CollectionReturnDao[returnId].acceptedCollectionIds)
                    assertEquals("two",CollectionReturnDao[pendingId].collectionIds)
                    assertNull(CollectionReturnDao[pendingId].inspection)
                    assertEquals(EnumCollectionStatus.IN_STORE,CollectionDao["one"].lifeStatus)
                    assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["two"].lifeStatus)
                    val recordedFacts=JsonUtils.fromJson(CollectionIncidentRecordDao[factsId].content,IncidentFactsSnapshot::class.java)!!
                    assertEquals(input,recordedFacts.facts)
                    assertNull(recordedFacts.facts.source,"Text identifies the source; no unsupported typed RETURN linkage is claimed")
                    val recordedHandling=JsonUtils.fromJson(CollectionIncidentRecordDao[handlingId].content,IncidentHandlingVo::class.java)!!
                    assertEquals(result,recordedHandling)
                    assertEquals(factsId,recordedHandling.factsId)
                    assertEquals(EnumIncidentDisposition.NOT_RECOVERED,recordedHandling.items.single().disposition)
                    assertTrue(CollectionIncidentDao[incidentId].closed)
                    val restricted=CollectionIncidentItemDao.find { CollectionIncidentItems.incidentId eq incidentId }.single()
                    assertEquals("two",restricted.collectionId)
                    assertTrue(restricted.restricted)
                    for (recordId in listOf(factsId,handlingId)) {
                        val record=CollectionIncidentRecordDao[recordId]
                        val archive=CollectionPermanentArchiveDao.all().single { it.bizType==CollectionConst.BIZ_INCIDENT && it.bizId==incidentId && it.phase=="${record.kind}_$recordId" }
                        assertEquals(JsonUtils.mapper.readTree(record.content),JsonUtils.mapper.readTree(archive.snapshot))
                        assertEquals(CollectionCareEvidence.digest(archive.snapshot),archive.digest)
                    }
                    val refusal=assertThrows(BadRequestException::class.java) { locks.requireUnlocked("two") }
                    assertTrue(refusal.message!!.contains("事故"),refusal.message)
                    println("RETURN_MISSING_ITEM_RESULT "+JsonUtils.toJson(mapOf("passed" to true,"database" to if(System.getProperty("collection.test.pg.config")==null)"SQLite" else "PostgreSQL","committedThenFreshTransaction" to true,"receivedIds" to "one","outstandingIds" to "two","missingLifeStatus" to CollectionDao["two"].lifeStatus.name,"problem" to recordedFacts.facts.items.single().findings,"explanation" to recordedFacts.facts.description,"immediateMeasures" to recordedFacts.facts.immediateMeasures,"investigationCause" to recordedHandling.cause,"investigationActions" to recordedHandling.actions,"disposition" to recordedHandling.items.single().disposition.name,"followUp" to recordedHandling.items.single().followUp,"restrictionRetained" to true,"investigationFactsIdMatches" to (recordedHandling.factsId==factsId),"sourceTextOnlyNoTypedReturnLinkClaimed" to true)))
                }
            }
            val block: (LoanDao,CollectionReturnDao)->Unit={ loan,received ->
                ordinaryActor("operator")
                returns.save(received.toModel().copy(collectionIds="one"))
                val checked=invoke(fixture,"inspection",listOf("one")) as ReturnInspectionVo
                statutory.recordInspection(received.id.value,checked)
                invoke(fixture,"approve",received)
                returns.executeApprovedReturn(received.id.value)
                assertFalse(loan.returned)
                pendingId=returns.ensureDraftFromLoan(loan.id.value)
                assertEquals("two",CollectionReturnDao[pendingId].collectionIds)
                input=IncidentFactsVo(title="DEV-SYNTHETIC 部分归还缺件问题",discoveredAt=at.plusMinutes(2),location="DEV-SYNTHETIC 接收核对点",reporterUserId="operator",description="DEV-SYNTHETIC 原借用 ${loan.id.value} 清单 one,two；归还 ${received.id.value} 只收到 one，two 未收到，仍在源未归清单。只记录软件合成事实，不代表真实实物核查。",immediateMeasures="DEV-SYNTHETIC 登记 two 缺件，通知责任部门、保留原清单和出库对照，禁止将 two 虚报归还或重新提用。",items=listOf(IncidentItemVo("two",false,EnumIncidentSecurity.LOSS,false,"DEV-SYNTHETIC 本次接收缺少 two；未见实物，不做已实收或安全恢复声明")),evidenceFileIds="report",source=null)
                incidentId=incidents.create(input!!)
                factsId=incidents.detail(incidentId)["factsId"] as String
                val reports=listOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR,EnumIncidentTarget.POLICE,EnumIncidentTarget.COMPETENT).associate { target ->
                    val report=IncidentReportVo(factsId,setOf("two"),target,EnumIncidentReportKind.INCIDENT,"DEV-SYNTHETIC $target 接收测试单位",when(target){EnumIncidentTarget.KEEPER->"keeper";EnumIncidentTarget.DIRECTOR->"leader";else->null},"DEV-SYNTHETIC-MISSING-$target",at.plusMinutes(5),"DEV-SYNTHETIC two 未实收，按本缺件事实报告；没有现实报案或法律签章声明","report")
                    incidents.report(incidentId,report) to report
                }
                result=IncidentHandlingVo(factsId,at.plusMinutes(30),"operator","DEV-SYNTHETIC 对照源清单及本次接收，two 缺件原因未查明；不假称已找回","DEV-SYNTHETIC 已登记问题并核对借入方说明、报告记录和调查依据，继续追查 two，保留原未归状态","investigation",listOf(IncidentItemResolutionVo("two",EnumIncidentDisposition.NOT_RECOVERED,false,"DEV-SYNTHETIC 未找到 two，无法进行实物真实性及保存状况核验","DEV-SYNTHETIC 未找到，位置待调查",null,"DEV-SYNTHETIC 维持缺件限制，不新建实物接收或恢复安全状态","DEV-SYNTHETIC 持续调查和催回；找回后再依法逐件验收和办理解除","result")),reports.filter {it.value.target in setOf(EnumIncidentTarget.POLICE,EnumIncidentTarget.COMPETENT)}.map {(id,report)->IncidentAuthorityResultVo(report.target,report.authorityName,setOf("two"),at.plusMinutes(25),setOf(id),"DEV-SYNTHETIC 已形成阶段调查记录，two 尚未找回","report")},"keeper","leader","DEV-SYNTHETIC missing-$incidentId","approval")
                handlingId=incidents.recordHandling(incidentId,result!!)
                for ((who,minutes) in listOf("keeper" to 40L,"leader" to 45L)) {
                    ordinaryActor(who)
                    incidents.confirm(incidentId,IncidentConfirmationVo(incidents.detail(incidentId)["basisRevision"] as String,at.plusMinutes(minutes),"DEV-SYNTHETIC 本测试账号核对缺件调查记录；NOT_RECOVERED 不解除限制、不虚报已归","approval"))
                }
                ordinaryActor("operator")
                assertFalse(loan.returned)
                assertEquals(EnumCollectionStatus.ON_LOAN,CollectionDao["two"].lifeStatus)
            }
            invoke(fixture,"fixture",afterCommit,block)
        } finally { fixture.cleanup() }
    }
}
