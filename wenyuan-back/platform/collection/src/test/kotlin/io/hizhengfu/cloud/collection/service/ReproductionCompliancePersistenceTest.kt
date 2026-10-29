package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.dao.alertSubscribers
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.EnumSource as NatureSource
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDateTime

class ReproductionCompliancePersistenceTest {
    private val at=LocalDateTime.of(2026,9,1,10,0)
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c->c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val locks=CollectionOperationLockService()
    private val port=mock(CollectionConservationQualificationPort::class.java)
    private val incidents=mock(CollectionIncidentService::class.java)
    private val context=StaticApplicationContext()
    private val proof=ConservationQualificationEvidence("unit","资质单位","cert","文修-1","发证机关","本次材质复制拓印",at.minusYears(1).toLocalDate(),at.plusYears(1).toLocalDate(),"file")
    private val flow=mock(FlowInstanceService::class.java)
    private lateinit var compliance: ReproductionComplianceService
    private lateinit var service: ReproductionService
    @BeforeEach fun setup() {
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
        context.beanFactory.registerSingleton("qualification",port);context.refresh();SpringContextHolder().setApplicationContext(context)
        compliance=ReproductionComplianceService(scope,users,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),incidents)
        service=ReproductionService(flow,scope,binder,locks,compliance,DigitalAcquisitionService(scope,users,archives,CollectionOriginalAccessService(),locks,mock(io.hizhengfu.cloud.common.file.FileObjectStorage::class.java)))
        `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenReturn(proof)
        val user=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
    }
    @AfterEach fun clear() { SecurityContextHolder.clearContext();context.close() }
    private fun fixture(postgres: Boolean = false, block:()->Unit) {
        var cleanup: (() -> Unit)? = null
        val db = if (!postgres) Database.connect("jdbc:sqlite:file:reproduction-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC", databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig { useNestedTransactions=true })
        else {
            val config = System.getProperty("collection.test.pg.config") ?: error("Explicit isolated PostgreSQL configuration required")
            val properties = java.util.Properties().apply { java.nio.file.Files.newInputStream(java.nio.file.Path.of(config)).use(::load) }
            fun source() = org.postgresql.ds.PGSimpleDataSource().apply {
                setURL(properties.getProperty("url")); user = properties.getProperty("user"); password = properties.getProperty("password")
            }
            val admin = source(); val schema = "reproduction_originals_" + java.util.UUID.randomUUID().toString().replace("-", "")
            admin.connection.use { it.createStatement().use { sql -> sql.execute("CREATE SCHEMA $schema") } }
            cleanup = { admin.connection.use { it.createStatement().use { sql -> sql.execute("DROP SCHEMA $schema CASCADE") } } }
            Database.connect(source().apply { currentSchema = schema }, databaseConfig=org.jetbrains.exposed.v1.core.DatabaseConfig { useNestedTransactions=true })
        }
        try { transaction(db) {
            SchemaUtils.create(CollectionIncidentItems,CollectionOrgs,Collections,Reproductions,DigitalAcquisitionPlans,DigitalAcquisitionResources,DigitalAcquisitionFiles,DigitalAcquisitionFileAccesses,ReproductionPlans,ReproductionSafetyRecords,ReproductionOutputs,ReproductionOutputUses,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstances,FlowInstanceLogs,CollectionOperationLocks,Warehouses,WarehouseLogs,WarehouseEntryApplications,Requisitions)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            originalReaderSchema();originalReader("operator",CollectionConst.STORAGE)
            CollectionOrgDao.new("other"){name="外馆";code="O"}
            FileDao.new("file"){name="真实签署原件.pdf";path="/files/file.pdf";hash="proof";size=100;ext="pdf"}
            CollectionDao.new("piece"){orgId="museum";name="藏品";totalRegNo="M001";registeredAt=at.minusMonths(1);catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE}
            ReproductionDao.new("rep"){orgId="museum";collectionId="piece";repType=EnumRepType.COPY;purpose="科学研究";repDate=at.toLocalDate()}
            block()
        } } finally { cleanup?.invoke() }
    }
    private fun plan(id:String="rep")=ReproductionPlanVo(
        sourceRevision=compliance.detail(id)["sourceRevision"] as String,preparedAt=at,
        keeperUserId="keeper",leaderUserId="leader",undertakingUnitId="unit",undertakingUnitName="资质单位",
        scopeCheckedAt=at,scopeConfirmed=true,scopeReviewFileIds="file",maxQuantity=2,usesOriginal=false,massProduction=false,rubbingYuanOrEarlier=null,
        originalSuitable=true,noDamageMethod="按获批技术方案无损制作",noHardToolBeating=true,conditionFileIds="file",eraAndMethodBasisFileIds="file",limitedQuantityReason="本次研究仅需两件",siteAddress="制作室",safetyStandards="实际风险等级与防护标准核查",safetyCheckIntervalDays=1,safetyPlanFileIds="file",contractFileIds="file",signedPlanFileIds="file",confidentialityAssessment="依据本件资料核实保密要求",confidentialityFileIds="file")
    private fun permit(p:String,level:EnumAuthorityLevel=EnumAuthorityLevel.NATIONAL)=ReproductionAdministrativeVo(p,CollectionAdministrativeEvidenceVo(level=level,action=EnumAdministrativeAction.APPROVAL,authorityName="文物行政部门",documentNo="批复1",issuedAt=at.toLocalDate(),fileIds="file"))
    private fun prepared(v:ReproductionPlanVo=plan()):String { val p=compliance.savePlan("rep",v);compliance.administrative("rep",permit(p));return p }
    private fun approve(p:String,leader:Boolean=true) {
        val e=ReproductionDao["rep"];compliance.submit(e);e.flowInstanceId="flow-rep";e.flowStatus=EnumFlowStatus.AUDIT
        listOf("keeper","leader").filter { leader || it!="leader" }.forEachIndexed { i,node->FlowInstanceLogDao.new {instanceId="flow-rep";taskId="task-$node";nodeKey=node;operator=node;action="APPROVE";comment="本人核对本轮全部原件";createTime=at.plusMinutes(10+i.toLong())} }
        service.onFlowCompleted(e,FlowCompletedEvent("flow-rep",CollectionConst.FLOW_REPRODUCTION_2026,"复制拓印","rep",CollectionConst.BIZ_REPRODUCTION,emptyMap(),"operator","经办"))
    }
    private fun start(p:String)=ReproductionStartVo(p,at.plusHours(1),"operator",null,null,at,true,"file","file")
    private fun ready():String { val p=prepared();approve(p);compliance.start("rep",start(p));return p }
    private fun output(number:String="COPY-1",serial:Int=1)=ReproductionOutputVo(number,"研究复制品",at.plusHours(2),at.plusHours(3),true,"资质单位",null,serial,"file","辅助品保管室","keeper","科学研究","file")
    private fun completion()=ReproductionCompletionVo(at.plusHours(4),"operator",true,"原件未改变、产物逐件登记","file","file")

    private fun custodyValue(outId:String,hours:Long=5):ReproductionOutputCustodyVo {
        val out=ReproductionOutputDao[outId];val usage=ReproductionOutputCustody.ordered(ReproductionOutputUseDao.find {ReproductionOutputUses.outputId eq outId}.toList())
        val records=out.custody?.records.orEmpty();val pos=ReproductionOutputCustody.position(out,usage,records)
        return ReproductionOutputCustodyVo("museum",ReproductionOutputCustody.revision(out,usage,records),at.plusHours(hours),pos.custodianUserId,pos.location,"new-keeper","新保管室","逐件编号和现状已核对","岗位交接","file")
    }
    @Test fun `product custody nullable upgrade preserves old registration without inventing handovers`()=fixture {
        ready();val out=compliance.output("rep",output());val original=ReproductionOutputDao[out].content
        val tx=TransactionManager.current();tx.entityCache.flush()
        tx.exec("ALTER TABLE ${ReproductionOutputs.tableName} DROP COLUMN custody")
        SchemaUtils.createMissingTablesAndColumns(ReproductionOutputs)
        val current=ReproductionOutputs.selectAll().single()
        assertEquals(original,current[ReproductionOutputs.content]);assertNull(current[ReproductionOutputs.custody])
        assertEquals(out,current[ReproductionOutputs.id].value)
        assertNotNull(compliance.detail("rep")["plans"])
    }
    @Test fun `product custody ledger stores long history and reloads all original versions`()=fixture {
        ready();val out=compliance.output("rep",output())
        repeat(12) {n->compliance.transferCustody("rep",out,custodyValue(out,5L+n).copy(toLocation="实际保管位置$n",condition="实际保存状况".repeat(1000)))}
        TransactionManager.current().entityCache.flush();ReproductionOutputDao[out].refresh(flush=false)
        assertEquals(12,ReproductionOutputDao[out].custody!!.records.size)
        assertTrue(JsonUtils.toJson(ReproductionOutputDao[out].custody)!!.length>65535)
        assertNotNull(compliance.detail("rep")["plans"])
    }
    @Test fun `product custody handovers preserve registration and follow actual use returns`()=fixture {
        ready();val out=compliance.output("rep",output());compliance.complete("rep",completion())
        val original=ReproductionOutputDao[out].content
        val first=custodyValue(out);val id=compliance.transferCustody("rep",out,first)
        assertEquals(id,compliance.transferCustody("rep",out,first))
        assertEquals(original,ReproductionOutputDao[out].content)
        assertEquals(0L,ReproductionOutputUseDao.all().count())
        assertThrows(BadRequestException::class.java){compliance.transferCustody("rep",out,first.copy(toCustodianUserId="another"))}
        compliance.transferCustody("rep",out,custodyValue(out,6).copy(toCustodianUserId="second-keeper"))
        assertThrows(BadRequestException::class.java){compliance.recordUse("rep",out,ReproductionOutputUseVo(at.plusMinutes(350),"科学研究","研究室","operator","file"))}
        val use=compliance.recordUse("rep",out,ReproductionOutputUseVo(at.plusHours(7),"科学研究","研究室","operator","file"))
        assertThrows(BadRequestException::class.java){compliance.transferCustody("rep",out,custodyValue(out,8))}
        compliance.returnOutput("rep",out,use,ReproductionOutputReturnVo(at.plusHours(8),"接收室","returned-keeper","完好","file"))
        val afterReturn=custodyValue(out,9)
        assertEquals("returned-keeper",afterReturn.fromCustodianUserId)
        compliance.transferCustody("rep",out,afterReturn)
        assertEquals(3,ReproductionOutputDao[out].custody!!.records.size)
        assertEquals(original,ReproductionOutputDao[out].content)
        assertEquals("returned-keeper",ReproductionOutputUseDao[use].returned!!.custodianUserId)
        assertNotNull(compliance.detail("rep")["plans"])
    }

    @ParameterizedTest
    @CsvSource("ORG","REVISION","FROM_USER","FROM_LOCATION","EARLY","FUTURE","FILES","UNCHANGED")
    fun `product custody rejects invalid or stale handover facts`(change:String)=fixture {
        ready();val out=compliance.output("rep",output());val v=custodyValue(out)
        val invalid=when(change) {
            "ORG"->v.copy(expectedOrgId="other")
            "REVISION"->v.copy(expectedRevision="old")
            "FROM_USER"->v.copy(fromCustodianUserId="wrong")
            "FROM_LOCATION"->v.copy(fromLocation="wrong")
            "EARLY"->v.copy(handedOverAt=at)
            "FUTURE"->v.copy(handedOverAt=LocalDateTime.now().plusDays(1))
            "FILES"->v.copy(signedFileIds="")
            else->v.copy(toCustodianUserId=v.fromCustodianUserId,toLocation=v.fromLocation)
        }
        val before=CollectionPermanentArchiveDao.all().count()
        assertThrows(BadRequestException::class.java){compliance.transferCustody("rep",out,invalid)}
        assertNull(ReproductionOutputDao[out].custody);assertEquals(before,CollectionPermanentArchiveDao.all().count())
    }

    @ParameterizedTest
    @CsvSource("LEDGER_CLEAR","LEDGER_CHANGE","ARCHIVE_DELETE","ARCHIVE_CHANGE")
    fun `product custody history rejects missing or altered independent evidence`(change:String)=fixture {
        ready();val out=compliance.output("rep",output());val id=compliance.transferCustody("rep",out,custodyValue(out))
        when(change) {
            "LEDGER_CLEAR"->ReproductionOutputDao[out].custody=null
            "LEDGER_CHANGE"->{val old=ReproductionOutputDao[out].custody!!.records.single();ReproductionOutputDao[out].custody=ReproductionOutputCustodyLedger(listOf(old.copy(record=old.record.copy(toLocation="changed"))))}
            "ARCHIVE_DELETE"->CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "CUSTODY_$id"}.single().delete()
            else->CollectionPermanentArchiveDao.find {CollectionPermanentArchives.phase eq "CUSTODY_$id"}.single().snapshot="{}"
        }
        assertThrows(BadRequestException::class.java){compliance.detail("rep")}
        assertThrows(BadRequestException::class.java){compliance.transferCustody("rep",out,custodyValue(out,6))}
    }

    @ParameterizedTest
    @CsvSource("RECIPIENT_DISABLED","RECIPIENT_MOVED","ACTOR_REVOKED","OUTPUT_CHANGED","OLD_ARCHIVE_CHANGED","LEDGER_CHANGED")
    fun `product custody post archive changes roll back entire transfer`(change:String)=fixture {
        ready();val out=compliance.output("rep",output());val v=custodyValue(out)
        originalReader("new-keeper",CollectionConst.SELECT)
        val realUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        compliance=ReproductionComplianceService(scope,realUsers,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),incidents)
        val tx=TransactionManager.current();tx.entityCache.flush()
        val before=CollectionPermanentArchives.selectAll().associate {it[CollectionPermanentArchives.id].value to it[CollectionPermanentArchives.snapshot]}
        val sql=when(change) {
            "RECIPIENT_DISABLED"->"UPDATE sys_user SET enabled=0 WHERE id='new-keeper'"
            "RECIPIENT_MOVED"->"UPDATE sys_user SET group_id='other' WHERE id='new-keeper'"
            "ACTOR_REVOKED"->"DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='operator'"
            "OUTPUT_CHANGED"->"UPDATE ${ReproductionOutputs.tableName} SET number='wrong' WHERE id='$out'"
            "OLD_ARCHIVE_CHANGED"->"UPDATE ${CollectionPermanentArchives.tableName} SET snapshot='{}' WHERE phase='OUTPUT_$out'"
            else->"UPDATE ${ReproductionOutputs.tableName} SET custody=NULL WHERE id='$out'"
        }
        if(change=="LEDGER_CHANGED")tx.exec("CREATE TRIGGER changed_custody AFTER UPDATE OF custody ON ${ReproductionOutputs.tableName} WHEN NEW.custody IS NOT NULL BEGIN $sql; END")
        else tx.exec("CREATE TRIGGER changed_custody AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase LIKE 'CUSTODY_%' BEGIN $sql; END")
        assertThrows(BadRequestException::class.java){transaction(tx.db){compliance.transferCustody("rep",out,v)}}
        assertNull(ReproductionOutputs.selectAll().single()[ReproductionOutputs.custody])
        assertEquals("COPY-1",ReproductionOutputs.selectAll().single()[ReproductionOutputs.number])
        assertEquals(before,CollectionPermanentArchives.selectAll().associate {it[CollectionPermanentArchives.id].value to it[CollectionPermanentArchives.snapshot]})
        assertEquals("museum",CollectionActorDirectory(scope).requireActive("new-keeper").groupId)
    }

    @Test fun `product custody accepts current recipient after former custodian departure without requalifying manufacture`()=fixture {
        val p=ready();compliance.stop("rep",stopValue(p));val out=compliance.output("rep",output())
        originalReader("keeper",CollectionConst.SELECT);originalReader("new-keeper",CollectionConst.SELECT)
        TransactionManager.current().entityCache.flush()
        TransactionManager.current().exec("UPDATE sys_user SET enabled=0 WHERE id='keeper'")
        `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenThrow(BadRequestException("资质已失效"))
        val realUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        compliance=ReproductionComplianceService(scope,realUsers,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),incidents)
        val v=custodyValue(out).copy(handedOverAt=at.plusHours(5).withNano(123000000))
        compliance.transferCustody("rep",out,v)
        TransactionManager.current().entityCache.flush();ReproductionOutputDao[out].refresh(flush=false)
        assertEquals(v.handedOverAt,ReproductionOutputDao[out].custody!!.records.single().record.handedOverAt)
        assertNotNull(compliance.detail("rep")["plans"])
        assertNotNull(ReproductionPlanDao[p].stopped);assertNull(ReproductionPlanDao[p].completed)
    }

    private fun originalReady():String {
        val p=prepared(plan().copy(usesOriginal=true));approve(p)
        val handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(at.plusMinutes(40),at.plusMinutes(30),"keeper","file",setOf("keeper"),setOf("operator"),listOf(RequisitionConditionVo("piece","实际现状",false)),true,true,"file","file"),"req-task","operator",at.plusMinutes(40))
        RequisitionDao.new("req"){orgId="museum";collectionIds="piece";sourceBizType=CollectionConst.BIZ_REPRODUCTION;sourceBizId="rep";outExecuted=true;this.handover=handover}
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE
        CollectionDao["piece"].warehouseId="actual-work-site"
        archives.seal("museum",CollectionConst.BIZ_REQUISITION,"req","OUT_EXECUTED",mapOf("request" to mapOf("handover" to handover,"sourceBizType" to CollectionConst.BIZ_REPRODUCTION,"sourceBizId" to "rep")),"file")
        compliance.start("rep",start(p).copy(requisitionId="req"))
        return p
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["QUALIFICATION","NO_OUTPUT","UNSAFE"])
    fun `actual reproduction stop is needed before source return after interrupted work`(reason:String)=fixture {
        val p=originalReady()
        if(reason!="NO_OUTPUT")compliance.output("rep",output())
        if(reason=="QUALIFICATION")`when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenReturn(proof.copy(certificateNo="资质已变更"))
        if(reason=="UNSAFE")compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(190),"operator",false,"发现风险，已现场停止","保护原件","file"))
        assertThrows(BadRequestException::class.java){compliance.complete("rep",completion())}
        assertThrows(BadRequestException::class.java){locks.acquire("museum",CollectionConst.BIZ_RETURN,"return",listOf("piece"))}
        val stopped=stopValue(p).copy(safeForCustody=reason!="UNSAFE")
        assertEquals(p,compliance.stop("rep",stopped))
        assertEquals(p,compliance.stop("rep",stopped))
        assertNull(ReproductionPlanDao[p].completed)
        val guard=ReproductionOperationSafetyGuard(archives,incidents)
        if(reason=="UNSAFE")assertThrows(BadRequestException::class.java){guard.requireAllowed("piece",null,null)}
        else assertDoesNotThrow{guard.requireAllowed("piece",null,null)}
        CollectionOperationLockService(listOf(guard)).acquire("museum",CollectionConst.BIZ_RETURN,"return",listOf("piece"))
        assertFalse(RequisitionDao["req"].returned)
        assertEquals(EnumCollectionStatus.IN_USE,CollectionDao["piece"].lifeStatus)
        assertThrows(BadRequestException::class.java){compliance.start("rep",start(p).copy(requisitionId="req"))}
        assertThrows(BadRequestException::class.java){compliance.complete("rep",completion())}
        assertThrows(BadRequestException::class.java){compliance.output("rep",output("AFTER",2).copy(madeAt=at.plusHours(5),registeredAt=at.plusHours(6)))}
        val detail=compliance.detail("rep")
        assertNotNull((detail["plans"] as List<Map<String,Any?>>).single()["stopped"])
    }
    private fun stopValue(p:String)=ReproductionStopVo(p,at.plusHours(4),"operator","资质或实施情况变化，已实际停止",true,true,true,"实际制作室","keeper","原件现状核对","已停止制作并保护原件","办理来源归还，后续重新核实方案","file","file")

    @Test fun `actual reproduction stop allows truthful prior products and followup safety without current qualification`()=fixture {
        val p=originalReady();compliance.stop("rep",stopValue(p))
        `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenThrow(BadRequestException("资质已失效"))
        CollectionDao["piece"].name="后来已依法订正的名称"
        val out=compliance.output("rep",output().copy(registeredAt=at.plusHours(5)))
        assertNotNull(out)
        compliance.safety("rep",ReproductionSafetyVo(at.plusHours(6),"operator",true,"停止后原件在场","继续保管，等待正式归还","file"))
        assertThrows(BadRequestException::class.java){compliance.recordUse("rep",out,ReproductionOutputUseVo(at.plusHours(7),"科学研究","研究室","operator","file"))}
        assertThrows(BadRequestException::class.java){compliance.stop("rep",stopValue(p).copy(reason="不得覆盖停止原件"))}
        assertFalse(RequisitionDao["req"].returned)
    }
    @Test fun `actual reproduction stop unsafe facts require matching incident clearance before ordinary work`()=fixture {
        val p=originalReady();compliance.stop("rep",stopValue(p).copy(originalUndamaged=false,safeForCustody=false))
        val guard=ReproductionOperationSafetyGuard(archives,incidents)
        assertThrows(BadRequestException::class.java){guard.requireAllowed("piece",null,null)}
        val provider=ReproductionIncidentSourceProvider(archives);val source=IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",p)
        assertThrows(BadRequestException::class.java){provider.snapshot("museum",source,listOf(IncidentItemVo("piece",false,EnumIncidentSecurity.NONE,false,"事故说明")))}
        assertNotNull(provider.snapshot("museum",source,listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"事故说明"))))
        val value=IncidentClearanceVo("case",at.plusHours(5),"conservator","operator","原件已核实安全","实际保护措施已完成","file")
        val proof=mapOf("incidentId" to "case","basisRevision" to "actual-case-basis")
        `when`(incidents.requireClearance("case","museum","piece",source,value.confirmedAt,keeperUserId="operator")).thenReturn(proof)
        assertEquals(p,compliance.clearStop("rep",value))
        assertEquals(p,compliance.clearStop("rep",value))
        `when`(incidents.requireStoredClearance("case","museum","piece",source,value.confirmedAt,proof,keeperUserId="operator")).thenReturn(proof)
        assertDoesNotThrow{guard.requireAllowed("piece",null,null)}
        `when`(incidents.requireStoredClearance("case","museum","piece",source,value.confirmedAt,proof,keeperUserId="operator")).thenReturn(proof+mapOf("basisRevision" to "changed"))
        assertThrows(BadRequestException::class.java){guard.requireAllowed("piece",null,null)}
        assertNull(ReproductionPlanDao[p].completed)
    }
    @Test fun `actual reproduction stop links real incident reporting handling signatures and clearance`()=fixture {
        val p=originalReady();compliance.stop("rep",stopValue(p).copy(originalUndamaged=false,safeForCustody=false))
        SchemaUtils.create(CollectionIncidents,CollectionIncidentRecords)
        incidentReaderFixture("operator","leader")
        val actual=CollectionIncidentService(scope,users,archives,CollectionIncidentSourceService(listOf(ReproductionIncidentSourceProvider(archives))),listOf(ReproductionOperationSafetyGuard(archives)))
        compliance=ReproductionComplianceService(scope,users,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),actual)
        val source=IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",p)
        val id=actual.create(IncidentFactsVo(title="停止后实际损伤",discoveredAt=at.plusHours(4),location="实际制作室",reporterUserId="operator",description="停止时核实原件损伤",immediateMeasures="停止制作并隔离保护",items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"已核实损伤部位")),source=source))
        val detail=actual.detail(id);val facts=detail["factsId"] as String
        @Suppress("UNCHECKED_CAST") val requirements=detail["requirements"] as List<Map<String,Any>>
        assertTrue(requirements.any {it["target"]==EnumIncidentTarget.NATIONAL})
        val reports=requirements.filter {it["kind"]==EnumIncidentReportKind.INCIDENT}.groupBy {it["target"] as EnumIncidentTarget}.map { (target,rows) ->
            val v=IncidentReportVo(facts,rows.map {it["collectionId"] as String}.toSet(),target,EnumIncidentReportKind.INCIDENT,"实际接收机关",if(target==EnumIncidentTarget.KEEPER)"operator" else if(target==EnumIncidentTarget.DIRECTOR)"leader" else null,"报告编号",at.plusHours(4).plusMinutes(5),"实际报告内容","file")
            actual.report(id,v) to v
        }.toMap()
        val h=IncidentHandlingVo(facts,at.plusHours(4).plusMinutes(30),"operator","实际原因调查","实际处理措施","file",listOf(IncidentItemResolutionVo("piece",EnumIncidentDisposition.PRESENT_SAFE,true,"处理后现状","实际制作室","keeper","后续保护措施","后续检查安排","file")),reports.filter {it.value.target !in setOf(EnumIncidentTarget.KEEPER,EnumIncidentTarget.DIRECTOR)}.map {(rid,v)->IncidentAuthorityResultVo(v.target,v.authorityName,v.collectionIds,at.plusHours(4).plusMinutes(25),setOf(rid),"实际核查结果","file")},"operator","leader","案卷索引","file")
        actual.recordHandling(id,h)
        val clearance=IncidentClearanceVo(id,at.plusHours(5),"conservator","operator","已核实","实际保护措施","file")
        assertThrows(BadRequestException::class.java){compliance.clearStop("rep",clearance)}
        actual.confirm(id,IncidentConfirmationVo(actual.detail(id)["basisRevision"] as String,at.plusHours(4).plusMinutes(40),"保管负责人本人核对","file"))
        val previous=SecurityContextHolder.getContext().authentication
        try {
            val leader=SecurityUser(id="leader",name="负责人",username="leader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(leader,null,leader.authorities)
            actual.confirm(id,IncidentConfirmationVo(actual.detail(id)["basisRevision"] as String,at.plusHours(4).plusMinutes(45),"主要负责人本人核对","file"))
        } finally {SecurityContextHolder.getContext().authentication=previous}
        assertEquals(true,actual.detail(id)["closed"])
        assertEquals(p,compliance.clearStop("rep",clearance))
        assertDoesNotThrow{ReproductionOperationSafetyGuard(archives,actual).requireAllowed("piece",null,null)}
        assertFalse(RequisitionDao["req"].returned)
        assertNull(ReproductionPlanDao[p].completed)
    }

    @Test fun `actual reproduction stop rejects invented safe custody and time before existing records`()=fixture {
        val p=originalReady();compliance.output("rep",output())
        assertThrows(BadRequestException::class.java){compliance.stop("rep",stopValue(p).copy(physicallyPresent=false))}
        assertThrows(BadRequestException::class.java){compliance.stop("rep",stopValue(p).copy(custodianUserId=null))}
        assertThrows(BadRequestException::class.java){compliance.stop("rep",stopValue(p).copy(stoppedAt=at.plusMinutes(70)))}
        assertNull(ReproductionPlanDao[p].stopped)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
    }
    @Test fun `actual reproduction stop SQL mutation rolls back stopping and release`()=fixture {
        val p=originalReady();val tx=TransactionManager.current();tx.entityCache.flush()
        tx.exec("CREATE TRIGGER changed_stop AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase='STOPPED' BEGIN UPDATE ${ReproductionPlans.tableName} SET stopped=NULL WHERE id='$p'; END")
        assertThrows(BadRequestException::class.java){transaction(tx.db){compliance.stop("rep",stopValue(p))}}
        assertNull(ReproductionPlans.selectAll().single()[ReproductionPlans.stopped])
        assertEquals(1L,CollectionOperationLocks.selectAll().count())
        assertFalse(RequisitionDao["req"].returned)
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["INVENTORY","INCIDENT"])
    fun `actual reproduction stop preserves independent custody restrictions`(restriction:String)=fixture {
        val p=originalReady()
        if(restriction=="INVENTORY")CollectionDao["piece"].inventoryHold=true
        else CollectionIncidentItemDao.new {orgId="museum";incidentId="other-case";collectionId="piece";restricted=true}
        assertThrows(BadRequestException::class.java){compliance.stop("rep",stopValue(p))}
        assertNull(ReproductionPlanDao[p].stopped)
        compliance.stop("rep",stopValue(p).copy(safeForCustody=false))
        assertThrows(BadRequestException::class.java){ReproductionOperationSafetyGuard(archives,incidents).requireAllowed("piece",null,null)}
        if(restriction=="INVENTORY")assertTrue(CollectionDao["piece"].inventoryHold)
        else assertTrue(CollectionIncidentItemDao.all().single().restricted)
    }

    @Test fun `actual reproduction stop clearance rolls back if incident proof changes during archive binding`()=fixture {
        val p=originalReady();compliance.stop("rep",stopValue(p).copy(safeForCustody=false))
        val value=IncidentClearanceVo("case",at.plusHours(5),"conservator","operator","已核实","实际措施","file")
        val source=IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",p)
        val proof=mapOf("incidentId" to "case","basisRevision" to "original")
        `when`(incidents.requireClearance("case","museum","piece",source,value.confirmedAt,keeperUserId="operator")).thenReturn(proof)
        val tx=TransactionManager.current();tx.entityCache.flush()
        val before=CollectionPermanentArchives.selectAll().count()
        doAnswer {
            `when`(incidents.requireClearance("case","museum","piece",source,value.confirmedAt,keeperUserId="operator")).thenReturn(proof+mapOf("basisRevision" to "changed"))
            null
        }.`when`(binder).bindSingle(anyString(),anyString(),anyString())
        assertThrows(BadRequestException::class.java){transaction(tx.db){compliance.clearStop("rep",value)}}
        assertNull(ReproductionPlans.selectAll().single()[ReproductionPlans.clearance])
        assertEquals(before,CollectionPermanentArchives.selectAll().count())
        assertNotNull(ReproductionPlans.selectAll().single()[ReproductionPlans.stopped])
    }

    @Test fun `actual reproduction stop upgrade preserves prior plan and starts with no fabricated stop`()=fixture {
        val p=ready();val tx=TransactionManager.current();tx.entityCache.flush()
        val started=ReproductionPlans.selectAll().single()[ReproductionPlans.started]
        val archives=CollectionPermanentArchives.selectAll().map {it[CollectionPermanentArchives.snapshot]}.toSet()
        tx.exec("ALTER TABLE ${ReproductionPlans.tableName} DROP COLUMN stopped")
        tx.exec("ALTER TABLE ${ReproductionPlans.tableName} DROP COLUMN clearance")
        SchemaUtils.createMissingTablesAndColumns(ReproductionPlans)
        val after=ReproductionPlans.selectAll().single()
        assertNull(after[ReproductionPlans.stopped]);assertNull(after[ReproductionPlans.clearance])
        assertEquals(started,after[ReproductionPlans.started])
        assertEquals(archives,CollectionPermanentArchives.selectAll().map {it[CollectionPermanentArchives.snapshot]}.toSet())
        assertEquals(p,after[ReproductionPlans.id].value)
    }


    @ParameterizedTest
    @CsvSource("STOP", "SAFETY", "RETURN", "UNKNOWN_CUSTODY")
    fun `reproduction new responsibility preserves departed historical participants`(stage:String)=fixture {
        val p=ready()
        val out=if(stage=="RETURN")compliance.output("rep",output())else null
        if(stage=="RETURN")compliance.complete("rep",completion())
        val used=if(stage=="RETURN")compliance.recordUse("rep",out!!,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","keeper","file"))else null
        listOf("keeper","leader","participant").forEach {originalReader(it,CollectionConst.SELECT)}
        val tx=TransactionManager.current();tx.entityCache.flush()
        val oldPlan=ReproductionPlans.selectAll().single()[ReproductionPlans.content]
        val oldArchives=CollectionPermanentArchives.selectAll().associate {it[CollectionPermanentArchives.id].value to it[CollectionPermanentArchives.snapshot]}
        tx.exec("UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id IN ('keeper','leader')")
        val realUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        compliance=ReproductionComplianceService(scope,realUsers,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),incidents)
        when(stage) {
            "STOP"->compliance.stop("rep",stopValue(p).copy(operatorUserId="participant",custodianUserId="participant"))
            "UNKNOWN_CUSTODY"->compliance.stop("rep",stopValue(p).copy(operatorUserId="participant",custodianUserId=null,safeForCustody=false,physicallyPresent=false))
            "SAFETY"->compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90),"participant",true,"现状安全","实际保护措施","file"))
            else->compliance.returnOutput("rep",out!!,used!!,ReproductionOutputReturnVo(at.plusHours(6),"实际保管室","participant","完好","file"))
        }
        tx.entityCache.flush()
        assertEquals(oldPlan,ReproductionPlans.selectAll().single()[ReproductionPlans.content])
        val current=CollectionPermanentArchives.selectAll().associate {it[CollectionPermanentArchives.id].value to it[CollectionPermanentArchives.snapshot]}
        assertTrue(current.entries.containsAll(oldArchives.entries),"新登记不得覆盖离任人员的原方案、审批及历史原件")
        assertNotNull(compliance.detail("rep")["plans"],"历史人员停用不能使永久原件不可查")
        if(stage=="UNKNOWN_CUSTODY")assertTrue(ReproductionStopEvidence.unsafe(ReproductionPlanDao[p],archives),"保管人不明须保留不安全事实")
    }

    @ParameterizedTest
    @CsvSource(
        "PLAN_KEEPER,DISABLE", "PLAN_KEEPER,MOVE", "PLAN_KEEPER,ROLE",
        "PLAN_LEADER,DISABLE", "PLAN_LEADER,MOVE", "PLAN_LEADER,ROLE",
        "START,DISABLE", "START,MOVE",
        "SAFETY,DISABLE", "SAFETY,MOVE",
        "OUTPUT,DISABLE", "OUTPUT,MOVE",
        "COMPLETE,DISABLE", "COMPLETE,MOVE",
        "STOP_OPERATOR,DISABLE", "STOP_OPERATOR,MOVE",
        "STOP_CUSTODIAN,DISABLE", "STOP_CUSTODIAN,MOVE",
        "CLEAR,DISABLE", "CLEAR,MOVE", "CLEAR,ROLE", "CLEAR_KEEPER,ROLE",
        "USE,DISABLE", "USE,MOVE",
        "RETURN,DISABLE", "RETURN,MOVE"
    )
    fun `reproduction archive rejects changed current responsibility participant`(stage:String,change:String)=fixture {
        val value=plan()
        val p=when {
            stage.startsWith("PLAN") -> ""
            stage=="START" -> prepared().also {approve(it)}
            stage.startsWith("CLEAR") -> originalReady()
            else -> ready()
        }
        val out=if(stage in setOf("COMPLETE","USE","RETURN"))compliance.output("rep",output())else null
        if(stage in setOf("USE","RETURN"))compliance.complete("rep",completion())
        val used=if(stage=="RETURN")compliance.recordUse("rep",out!!,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file"))else null
        val clear=IncidentClearanceVo("case",at.plusHours(5),"participant","operator","实际安全现状","实际措施","file")
        if(stage.startsWith("CLEAR")) {
            compliance.stop("rep",stopValue(p).copy(safeForCustody=false))
            `when`(incidents.requireClearance("case","museum","piece",IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",p),clear.confirmedAt,keeperUserId="operator")).thenReturn(mapOf("incidentId" to "case"))
        }
        listOf("keeper","leader","participant").forEach {originalReader(it,CollectionConst.SELECT)}
        actorRole("keeper",CollectionConst.ROLE_STORAGE_HEAD)
        actorRole("leader",CollectionConst.ROLE_DIRECTOR)
        actorRole("participant",CollectionConst.ROLE_CONSERVATION)
        actorRole("operator",CollectionConst.ROLE_STORAGE_HEAD)
        val realUsers=CollectionResponsibleUserService(CollectionActorDirectory(scope),scope)
        compliance=ReproductionComplianceService(scope,realUsers,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,CollectionOriginalAccessService(),incidents)
        val subject=when(stage){"PLAN_KEEPER"->"keeper";"PLAN_LEADER"->"leader";"CLEAR_KEEPER"->"operator";else->"participant"}
        val phase=when {
            stage.startsWith("PLAN") -> "PLAN"
            stage.startsWith("STOP") -> "STOPPED"
            stage=="START" -> "STARTED"
            stage=="COMPLETE" -> "COMPLETED"
            stage.startsWith("CLEAR") -> "STOP_CLEARED_"
            else -> stage
        }
        val tx=TransactionManager.current();tx.entityCache.flush()
        val tables=listOf(Reproductions,ReproductionPlans,ReproductionOutputs,ReproductionOutputUses,ReproductionSafetyRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks,Requisitions)
        fun stored()=tables.associate {t->t.tableName to t.selectAll().map {row->t.columns.filterNot {it.name in setOf("update_time","update_by")}.associate {it.name to row[it]}}.toSet()}
        val before=stored()
        val mutation=when(change) {
            "DISABLE" -> "UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='$subject'"
            "MOVE" -> "UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET group_id='other' WHERE id='$subject'"
            else -> "DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='$subject'" + if(stage=="CLEAR_KEEPER") " AND role_id='role:${CollectionConst.ROLE_STORAGE_HEAD}'" else ""
        }
        tx.exec("CREATE TRIGGER participant_changed AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase LIKE '$phase%' BEGIN $mutation; END")
        assertThrows(BadRequestException::class.java){transaction(tx.db){
            when(stage) {
                "PLAN_KEEPER","PLAN_LEADER"->compliance.savePlan("rep",value)
                "START"->compliance.start("rep",start(p).copy(operatorUserId="participant"))
                "SAFETY"->compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90),"participant",true,"现状安全","实际保管措施","file"))
                "OUTPUT"->compliance.output("rep",output().copy(custodianUserId="participant"))
                "COMPLETE"->compliance.complete("rep",completion().copy(operatorUserId="participant"))
                "STOP_OPERATOR"->compliance.stop("rep",stopValue(p).copy(operatorUserId="participant"))
                "STOP_CUSTODIAN"->compliance.stop("rep",stopValue(p).copy(custodianUserId="participant"))
                "CLEAR","CLEAR_KEEPER"->compliance.clearStop("rep",clear)
                "USE"->compliance.recordUse("rep",out!!,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","participant","file"))
                else->compliance.returnOutput("rep",out!!,used!!,ReproductionOutputReturnVo(at.plusHours(6),"库房","participant","完好","file"))
            }
        }}
        assertEquals(before,stored(),"责任人员在归档期间变化须完整回滚本次登记、原件与占用")
        val restored=CollectionActorDirectory(scope).requireActive(subject)
        assertEquals("museum",restored.groupId)
        assertTrue(restored.roleCodes.isNotEmpty())
        if(stage=="CLEAR_KEEPER")assertTrue(CollectionConst.ROLE_STORAGE_HEAD in restored.roleCodes)
        assertTrue(CollectionConst.STORAGE in CollectionActorDirectory(scope).requireActive("operator").permissions,"经办人权限未变化，不能用经办授权复验代替责任人核验")
    }

    @ParameterizedTest
    @CsvSource(
        "PLAN,REVOKE",
        "PLAN,DISABLE",
        "PLAN,MOVE",
        "PLAN,ORG",
        "ADMIN,REVOKE",
        "ADMIN,DISABLE",
        "ADMIN,MOVE",
        "ADMIN,ORG",
        "START,REVOKE",
        "START,DISABLE",
        "START,MOVE",
        "START,ORG",
        "OUTPUT,REVOKE",
        "OUTPUT,DISABLE",
        "OUTPUT,MOVE",
        "OUTPUT,ORG",
        "SAFETY,REVOKE",
        "SAFETY,DISABLE",
        "SAFETY,MOVE",
        "SAFETY,ORG",
        "COMPLETE,REVOKE",
        "COMPLETE,DISABLE",
        "COMPLETE,MOVE",
        "COMPLETE,ORG",
        "STOP,REVOKE",
        "STOP,DISABLE",
        "STOP,MOVE",
        "STOP,ORG",
        "CLEAR,REVOKE",
        "CLEAR,DISABLE",
        "CLEAR,MOVE",
        "CLEAR,ORG",
        "USE,REVOKE",
        "USE,DISABLE",
        "USE,MOVE",
        "USE,ORG",
        "RETURN,REVOKE",
        "RETURN,DISABLE",
        "RETURN,MOVE",
        "RETURN,ORG"
    )
    fun `reproduction final authorization rejects actual archive time access change`(stage:String,change:String)=fixture {
        val planValue=plan()
        val p=when(stage) {
            "PLAN" -> ""
            "ADMIN" -> compliance.savePlan("rep",planValue)
            "START" -> prepared().also {approve(it)}
            "STOP","CLEAR" -> originalReady()
            else -> ready()
        }
        val out=if(stage in setOf("COMPLETE","USE","RETURN"))compliance.output("rep",output())else null
        if(stage in setOf("USE","RETURN"))compliance.complete("rep",completion())
        val used=if(stage=="RETURN")compliance.recordUse("rep",out!!,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file"))else null
        val clearance=IncidentClearanceVo("case",at.plusHours(5),"conservator","operator","实际安全现状","实际措施","file")
        if(stage=="CLEAR") {
            compliance.stop("rep",stopValue(p).copy(safeForCustody=false))
            `when`(incidents.requireClearance("case","museum","piece",IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",p),clearance.confirmedAt,keeperUserId="operator")).thenReturn(mapOf("incidentId" to "case"))
        }
        val tx=TransactionManager.current();tx.entityCache.flush()
        val tables=listOf(Reproductions,ReproductionPlans,ReproductionOutputs,ReproductionOutputUses,ReproductionSafetyRecords,CollectionAdministrativeRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,CollectionOperationLocks,Requisitions)
        fun stored()=tables.associate {t->t.tableName to t.selectAll().map {row->t.columns.filterNot {it.name in setOf("update_time","update_by")}.associate {it.name to row[it]}}.toSet()}
        val before=stored()
        val phase=when(stage){"START"->"STARTED";"COMPLETE"->"COMPLETED";"STOP"->"STOPPED";"CLEAR"->"STOP_CLEARED_";else->stage}
        val mutation=when(change) {
            "REVOKE" -> "DELETE FROM ${io.hizhengfu.cloud.sys.system.dao.RoleUsers.tableName} WHERE user_id='operator'"
            "DISABLE" -> "UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET enabled=0 WHERE id='operator'"
            "MOVE" -> "UPDATE ${io.hizhengfu.cloud.sys.system.dao.Users.tableName} SET group_id='other' WHERE id='operator'"
            else -> "UPDATE ${CollectionOrgs.tableName} SET org_status=1 WHERE id='museum'"
        }
        tx.exec("CREATE TRIGGER access_changed AFTER INSERT ON ${CollectionPermanentArchives.tableName} WHEN NEW.phase LIKE '$phase%' BEGIN $mutation; END")
        assertThrows(BadRequestException::class.java){transaction(tx.db){
            when(stage) {
                "PLAN"->compliance.savePlan("rep",planValue)
                "ADMIN"->compliance.administrative("rep",permit(p))
                "START"->compliance.start("rep",start(p))
                "OUTPUT"->compliance.output("rep",output())
                "SAFETY"->compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90),"operator",true,"现状安全","保管措施","file"))
                "COMPLETE"->compliance.complete("rep",completion())
                "STOP"->compliance.stop("rep",stopValue(p))
                "CLEAR"->compliance.clearStop("rep",clearance)
                "USE"->compliance.recordUse("rep",out!!,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file"))
                else->compliance.returnOutput("rep",out!!,used!!,ReproductionOutputReturnVo(at.plusHours(6),"库房","keeper","完好","file"))
            }
        }}
        assertEquals(before,stored(),"权限变化后本次业务、原件和占用应完整回滚")
        val actor=CollectionActorDirectory(scope).requireActive("operator")
        assertTrue(CollectionConst.STORAGE in actor.permissions)
        assertEquals("museum",actor.groupId)
        assertEquals(EnumOrgStatus.ENABLED,CollectionOrgs.selectAll().first {it[CollectionOrgs.id].value=="museum"}[CollectionOrgs.orgStatus])
    }

    @Test fun `actual reproduction SQL comparison preserves the millisecond storage contract`()=fixture {
        val p=prepared();approve(p)
        val began=start(p).copy(startedAt=at.plusHours(1).withNano(123456789))
        compliance.start("rep",began)
        compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90).withNano(234567891),"operator",true,"检查正常","持续防护","file"))
        val made=output().copy(madeAt=at.plusHours(2).withNano(345678912),registeredAt=at.plusHours(3).withNano(456789123))
        val out=compliance.output("rep",made)
        val completed=completion().copy(completedAt=at.plusHours(4).withNano(567891234))
        compliance.complete("rep",completed)
        val used=ReproductionOutputUseVo(at.plusHours(5).withNano(678912345),"科学研究","研究室","operator","file")
        val use=compliance.recordUse("rep",out,used)
        val returned=ReproductionOutputReturnVo(at.plusHours(6).withNano(789123456),"辅助品库","keeper","现状无差异","file")
        compliance.returnOutput("rep",out,use,returned)
        assertEquals(123000000,ReproductionPlans.selectAll().single()[ReproductionPlans.started]!!.startedAt.nano)
        assertEquals(567000000,ReproductionPlans.selectAll().single()[ReproductionPlans.completed]!!.completedAt.nano)
        assertEquals(345000000,ReproductionOutputs.selectAll().single()[ReproductionOutputs.content].madeAt.nano)
        assertEquals(456000000,ReproductionOutputs.selectAll().single()[ReproductionOutputs.content].registeredAt.nano)
        assertEquals(234000000,ReproductionSafetyRecords.selectAll().single()[ReproductionSafetyRecords.content].checkedAt.nano)
        assertEquals(678000000,ReproductionOutputUses.selectAll().single()[ReproductionOutputUses.content].occurredAt.nano)
        assertEquals(789000000,ReproductionOutputUses.selectAll().single()[ReproductionOutputUses.returned]!!.returnedAt.nano)
    }

    @ParameterizedTest
    @CsvSource(
        "START,DATE",
        "START,ORG",
        "START,OWNER",
        "START,DELETE",
        "START,PLAN",
        "START,BUSINESS",
        "START,ARCHIVE",
        "OUTPUT,DATE",
        "OUTPUT,ORG",
        "OUTPUT,OWNER",
        "OUTPUT,DELETE",
        "OUTPUT,PLAN",
        "OUTPUT,BUSINESS",
        "OUTPUT,ARCHIVE",
        "COMPLETE,DATE",
        "COMPLETE,ORG",
        "COMPLETE,OWNER",
        "COMPLETE,DELETE",
        "COMPLETE,PLAN",
        "COMPLETE,BUSINESS",
        "COMPLETE,ARCHIVE",
        "SAFETY,DATE",
        "SAFETY,ORG",
        "SAFETY,OWNER",
        "SAFETY,DELETE",
        "SAFETY,PLAN",
        "SAFETY,BUSINESS",
        "SAFETY,ARCHIVE",
        "USE,DATE",
        "USE,ORG",
        "USE,OWNER",
        "USE,DELETE",
        "USE,PLAN",
        "USE,BUSINESS",
        "USE,ARCHIVE",
        "RETURN,DATE",
        "RETURN,ORG",
        "RETURN,OWNER",
        "RETURN,DELETE",
        "RETURN,PLAN",
        "RETURN,BUSINESS",
        "RETURN,ARCHIVE"
    )
    fun `actual reproduction SQL writes must match signed activity and ownership`(stage:String,change:String)=fixture {
        val p=prepared();approve(p)
        if(stage!="START")compliance.start("rep",start(p))
        val out=if(stage in setOf("COMPLETE","USE","RETURN"))compliance.output("rep",output()) else null
        if(stage in setOf("USE","RETURN"))compliance.complete("rep",completion())
        val useValue=ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file")
        val use=if(stage=="RETURN")compliance.recordUse("rep",out!!,useValue) else null
        val safety=ReproductionSafetyVo(at.plusMinutes(90),"operator",true,"检查正常","继续防护","file")
        val returned=ReproductionOutputReturnVo(at.plusHours(6),"辅助品库","keeper","现状无差异","file")
        val target=when(stage) {
            "START","COMPLETE" -> ReproductionPlans
            "OUTPUT" -> ReproductionOutputs
            "SAFETY" -> ReproductionSafetyRecords
            else -> ReproductionOutputUses
        }
        val bodyColumn=when(stage){"START"->"started";"COMPLETE"->"completed";"RETURN"->"returned";else->"content"}
        val wrongBody:Any=when(stage) {
            "START" -> start(p).copy(startedAt=at.plusYears(2))
            "OUTPUT" -> output().copy(madeAt=at.plusYears(2))
            "COMPLETE" -> completion().copy(completedAt=at.plusYears(2))
            "SAFETY" -> safety.copy(checkedAt=at.plusYears(2))
            "USE" -> useValue.copy(occurredAt=at.plusYears(2))
            else -> returned.copy(returnedAt=at.plusYears(2))
        }
        val ownerColumn=when(stage){"START","COMPLETE"->"reproduction_id";"OUTPUT","SAFETY"->"plan_id";else->"output_id"}
        val tables=listOf(Reproductions,ReproductionPlans,ReproductionOutputs,ReproductionSafetyRecords,ReproductionOutputUses,CollectionPermanentArchives,CollectionPermanentArchiveFiles)
        fun stored()=tables.associate { table -> table.tableName to table.selectAll().map { row -> table.columns.filterNot { it.name in setOf("update_time","update_by") }.associate { col -> col.name to row[col] } }.toSet() }
        val tx=TransactionManager.current();tx.entityCache.flush();val before=stored()
        val mutation=when(change) {
            "DATE" -> "UPDATE ${target.tableName} SET $bodyColumn='${JsonUtils.toJson(wrongBody)!!.replace("'","''")}' WHERE id=NEW.id"
            "ORG" -> "UPDATE ${target.tableName} SET org_id='other' WHERE id=NEW.id"
            "OWNER" -> "UPDATE ${target.tableName} SET $ownerColumn='other-record' WHERE id=NEW.id"
            "DELETE" -> "DELETE FROM ${target.tableName} WHERE id=NEW.id"
            "PLAN" -> "UPDATE ${ReproductionPlans.tableName} SET version=version+1 WHERE id='$p'"
            "BUSINESS" -> "UPDATE ${Reproductions.tableName} SET collection_id='other-piece' WHERE id='rep'"
            else -> "UPDATE ${CollectionPermanentArchives.tableName} SET digest='changed' WHERE id=NEW.id"
        }
        val event=if(stage in setOf("START","COMPLETE","RETURN"))"UPDATE OF $bodyColumn" else "INSERT"
        if(change=="ARCHIVE") {
            tx.exec("CREATE TRIGGER actual_activity_archive AFTER INSERT ON ${CollectionPermanentArchives.tableName} BEGIN $mutation; END")
        } else tx.exec("CREATE TRIGGER actual_activity_record AFTER $event ON ${target.tableName} BEGIN $mutation; END")
        assertThrows(BadRequestException::class.java) { transaction(tx.db) {
            when(stage) {
                "START" -> compliance.start("rep",start(p))
                "OUTPUT" -> compliance.output("rep",output())
                "COMPLETE" -> compliance.complete("rep",completion())
                "SAFETY" -> compliance.safety("rep",safety)
                "USE" -> compliance.recordUse("rep",out!!,useValue)
                else -> compliance.returnOutput("rep",out!!,use!!,returned)
            }
        } }
        assertEquals(before,stored(),"异常活动、关联和归档均应回滚；不得修改先前记录")
    }

    @ParameterizedTest
    @CsvSource("START,COPY", "OUTPUT,COPY", "COMPLETE,COPY", "START,RUBBING", "OUTPUT,RUBBING", "COMPLETE,RUBBING")
    fun `ongoing reproduction qualification change during archive binding rolls back activity`(stage:String, type:EnumRepType)=fixture {
        ReproductionDao["rep"].repType=type
        val p=prepared(plan().copy(rubbingYuanOrEarlier=if(type==EnumRepType.RUBBING) false else null))
        approve(p)
        val product=output().copy(markedMadeAt=if(type==EnumRepType.RUBBING) output().madeAt else null)
        if(stage!="START")compliance.start("rep",start(p))
        if(stage=="COMPLETE")compliance.output("rep",product)
        val tx=TransactionManager.current();tx.entityCache.flush()
        val before=ReproductionPlans.selectAll().single()
        val archiveCount=CollectionPermanentArchives.selectAll().count()
        val outputCount=ReproductionOutputs.selectAll().count()
        doAnswer {
            `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate()))
                .thenReturn(proof.copy(certificateNo="写入期间已订正的证书"))
            null
        }.`when`(binder).bindSingle(anyString(),anyString(),anyString())
        val error=assertThrows(BadRequestException::class.java) {
            transaction(tx.db) {
                when(stage) {
                    "START" -> compliance.start("rep",start(p))
                    "OUTPUT" -> compliance.output("rep",product)
                    else -> compliance.complete("rep",completion())
                }
            }
        }
        assertTrue(error.message!!.contains("资质"))
        val after=ReproductionPlans.selectAll().single()
        assertEquals(before[ReproductionPlans.started],after[ReproductionPlans.started])
        assertEquals(before[ReproductionPlans.completed],after[ReproductionPlans.completed])
        assertEquals(archiveCount,CollectionPermanentArchives.selectAll().count())
        assertEquals(outputCount,ReproductionOutputs.selectAll().count())
    }

    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `first relic requires national originals and frozen catalog`(nature:EnumCollectionNature)=fixture {
        CollectionDao["piece"].nature=nature
        val p=compliance.savePlan("rep",plan())
        assertThrows(BadRequestException::class.java){compliance.administrative("rep",permit(p,EnumAuthorityLevel.PROVINCE))}
        assertThrows(BadRequestException::class.java){compliance.submit(ReproductionDao["rep"])}
        compliance.administrative("rep",permit(p));compliance.requirePrepared(ReproductionDao["rep"])
        CollectionDao["piece"].name="已改变名称"
        assertThrows(BadRequestException::class.java){compliance.requirePrepared(ReproductionDao["rep"])}
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `second relic uses provincial approval and photography has separate entry`(nature:EnumCollectionNature)=fixture {
        CollectionDao["piece"].nature=nature
        CollectionDao["piece"].level=EnumRelicLevel.SECOND
        val p=compliance.savePlan("rep",plan());compliance.administrative("rep",permit(p,EnumAuthorityLevel.PROVINCE));compliance.requirePrepared(ReproductionDao["rep"])
        ReproductionDao.new("photo"){orgId="museum";collectionId="piece";repType=EnumRepType.PHOTO}
        assertThrows(BadRequestException::class.java){compliance.detail("photo")}
        assertEquals(CollectionConst.FLOW_DIGITAL_ACQUISITION_2026,service.definitionKey(ReproductionDao["photo"]))
    }
    @Test fun `ungraded unsuitable early rubbing and original mass production reject`()=fixture {
        CollectionDao["piece"].level=EnumRelicLevel.UNRATED
        assertThrows(BadRequestException::class.java){compliance.savePlan("rep",plan())}
        CollectionDao["piece"].level=EnumRelicLevel.FIRST
        assertThrows(BadRequestException::class.java){compliance.savePlan("rep",plan().copy(originalSuitable=false))}
        assertThrows(BadRequestException::class.java){compliance.savePlan("rep",plan().copy(usesOriginal=true,massProduction=true))}
        ReproductionDao["rep"].repType=EnumRepType.RUBBING
        assertThrows(BadRequestException::class.java){compliance.savePlan("rep",plan().copy(usesOriginal=true,rubbingYuanOrEarlier=true))}
        assertThrows(BadRequestException::class.java){compliance.savePlan("rep",plan().copy(usesOriginal=true,rubbingYuanOrEarlier=false,noHardToolBeating=false))}
    }
    @Test fun `old PASS and missing leader logs never authorize actual start`()=fixture {
        val p=prepared();ReproductionDao["rep"].flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java){compliance.start("rep",start(p))}
        ReproductionDao["rep"].flowStatus=EnumFlowStatus.DRAFT
        assertThrows(BadRequestException::class.java){approve(p,false)}
        assertNull(ReproductionPlanDao[p].started)
    }
    @Test fun `actual start seals records and blocks rewriting cancellation and type switch`()=fixture {
        val p=ready();assertEquals(p,compliance.start("rep",start(p)))
        assertThrows(BadRequestException::class.java){service.save(ReproductionDao["rep"].toModel().copy(purpose="其他用途"))}
        assertThrows(BadRequestException::class.java){service.cancel("rep")}
        assertThrows(BadRequestException::class.java){service.save(ReproductionDao["rep"].toModel().copy(repType=EnumRepType.PHOTO))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("file")}
        assertEquals("piece",service.buildFormData(ReproductionDao["rep"])["collectionId"])
    }
    @ParameterizedTest
    @NatureSource(value=EnumCollectionNature::class,names=["RELIC","SCIENTIFIC_FOSSIL"])
    fun `qualification changes between approval and actual start require fresh verification`(nature:EnumCollectionNature)=fixture {
        CollectionDao["piece"].nature=nature
        val p=prepared();approve(p)
        `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenReturn(proof.copy(certificateNo="新证"))
        assertThrows(BadRequestException::class.java){compliance.start("rep",start(p))}
        assertNull(ReproductionPlanDao[p].started)
    }
    @Test fun `unresolved safety stops production and a later check cannot authorize earlier work`()=fixture {
        ready()
        val bad=compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(70),"operator",false,"场所风险","现场停止","file"))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output())}
        compliance.safety("rep",ReproductionSafetyVo(at.plusHours(3),"operator",true,"已处理","复查确认","file",setOf(bad)))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output())}
        compliance.output("rep",output().copy(madeAt=at.plusMinutes(181),registeredAt=at.plusMinutes(182)))
        assertThrows(BadRequestException::class.java){compliance.complete("rep",completion().copy(completedAt=at.plusMinutes(170)))}
        compliance.complete("rep",completion())
    }
    @Test fun `changed qualification during work prevents further production`()=fixture {
        ready()
        `when`(port.requireQualified(anyString(),anyString(),any() ?: at.toLocalDate())).thenReturn(proof.copy(certificateNo="新证"))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output())}
        assertEquals(0L,ReproductionOutputDao.count())
    }
    @Test fun `expired checks cannot authorize production`()=fixture {
        ready();assertThrows(BadRequestException::class.java){compliance.output("rep",output().copy(madeAt=at.plusDays(2),registeredAt=at.plusDays(2)))}
    }
    @Test fun `outputs enforce actual serial quantity labels and approved purpose`()=fixture {
        ready()
        assertThrows(BadRequestException::class.java){compliance.output("rep",output().copy(marked=false))}
        assertThrows(BadRequestException::class.java){compliance.output("rep",output().copy(purpose="销售"))}
        val one=compliance.output("rep",output());assertEquals(one,compliance.output("rep",output()))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output("COPY-2",1))}
        compliance.output("rep",output("COPY-2",2))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output("COPY-3",3))}
    }
    @Test fun `rubbings require marked manufacturing unit time and serial`()=fixture {
        ReproductionDao["rep"].repType=EnumRepType.RUBBING
        val p=prepared(plan().copy(rubbingYuanOrEarlier=true));approve(p);compliance.start("rep",start(p))
        assertThrows(BadRequestException::class.java){compliance.output("rep",output())}
        compliance.output("rep",output().copy(markedMadeAt=output().madeAt))
    }
    @Test fun `product custody requires separate actual return and rejects overlapping use`()=fixture {
        ready();val out=compliance.output("rep",output());compliance.complete("rep",completion())
        val use=ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file")
        val id=compliance.recordUse("rep",out,use);assertEquals(id,compliance.recordUse("rep",out,use))
        assertThrows(BadRequestException::class.java){compliance.recordUse("rep",out,use.copy(occurredAt=at.plusHours(6)))}
        val returned=ReproductionOutputReturnVo(at.plusHours(6),"辅助品库","keeper","标识、数量和现状核对无误","file")
        compliance.returnOutput("rep",out,id,returned);compliance.returnOutput("rep",out,id,returned)
        assertThrows(BadRequestException::class.java){compliance.returnOutput("rep",out,id,returned.copy(location="别处"))}
        assertThrows(BadRequestException::class.java){compliance.recordUse("rep",out,use.copy(occurredAt=at.plusMinutes(330)))}
        compliance.recordUse("rep",out,use.copy(occurredAt=at.plusHours(7)))
    }
    @Test fun `actual original access needs signed matching diary and keeps operation lock until completion`()=fixture {
        WarehouseDao.new("store"){orgId="museum";name="库房"};CollectionDao["piece"].warehouseId="store"
        val p=prepared(plan().copy(usesOriginal=true));approve(p)
        val diary=WarehouseDiaryVo(at,setOf("operator"),true,"实际在库复制","无异常",listOf(WarehouseEnvironmentReadingVo(at,22.0,50.0,"温湿度记录","file")),false,signedDiaryFileIds="file")
        val entry=WarehouseAttendanceVo(at,setOf("operator","keeper"),identityChecked=true,continuousAccompaniment=true,signedRecordFileIds="file")
        WarehouseEntryApplicationDao.new("entry") {
            orgId="museum";applicationNo="入库1";applicantId="operator";entrantIds="operator,keeper";warehouseIds="store";purpose="在库复制"
            plannedStart=at;plannedEnd=at.plusHours(8);actualEntryAt=at;entryRecord=entry
        }
        val entries=WarehouseDiaryEntriesVo(listOf(WarehouseDiaryVisitVo("entry","入库1","在库复制",entry,null,at,at.plusHours(8),exitedAt=null)))
        val log=WarehouseLogDao.new("log"){orgId="museum";warehouseId="store";logDate=at.toLocalDate();content="实际在库复制";this.diary=diary;this.entries=entries}
        val v=start(p).copy(onSiteWarehouseLogId="log")
        assertThrows(BadRequestException::class.java){compliance.start("rep",v)}
        archives.seal("museum",WarehouseDiaryService.BIZ,"log","RECORDED_1",mapOf("warehouseId" to "store","logDate" to log.logDate,"workContent" to log.content,"diary" to diary,"entries" to entries),"file")
        archives.seal("museum",CollectionConst.BIZ_WAREHOUSE_ENTRY,"entry","CHECKED_IN",entry,"file")
        compliance.start("rep",v)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        compliance.output("rep",output());compliance.complete("rep",completion());assertDoesNotThrow{locks.requireUnlocked("piece")}
    }
    @Test fun `foreign museum and altered archive cannot satisfy permanent proof`()=fixture {
        val p=prepared()
        ReproductionDao["rep"].orgId="other"
        assertThrows(BadRequestException::class.java){compliance.detail("rep")}
        ReproductionDao["rep"].orgId="museum"
        CollectionPermanentArchiveDao.all().first().snapshot="{}"
        assertThrows(BadRequestException::class.java){compliance.requirePrepared(ReproductionDao["rep"])}
        assertNull(ReproductionPlanDao[p].started)
    }
    @Test fun `original requisition must match actual signed handover archive and source`()=fixture {
        val p=prepared(plan().copy(usesOriginal=true));approve(p)
        val handover=RequisitionHandoverRecordVo(RequisitionHandoverVo(at.plusMinutes(40),at.plusMinutes(30),"keeper","file",setOf("keeper"),setOf("operator"),listOf(RequisitionConditionVo("piece","实际现状",false)),true,true,"file","file"),"req-task","operator",at.plusMinutes(40))
        val req=RequisitionDao.new("req"){orgId="museum";collectionIds="piece";sourceBizType=CollectionConst.BIZ_REPRODUCTION;sourceBizId="rep";outExecuted=true;this.handover=handover}
        CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_USE
        CollectionDao["piece"].warehouseId="actual-work-site"
        val v=start(p).copy(requisitionId="req")
        assertThrows(BadRequestException::class.java){compliance.start("rep",v)}
        archives.seal("museum",CollectionConst.BIZ_REQUISITION,"req","OUT_EXECUTED",mapOf("request" to req.toModel(),"additionalVerification" to emptyMap<String,String>()),"file")
        req.handover=handover.copy(recordedBy="someone-else")
        assertThrows(BadRequestException::class.java){compliance.start("rep",v)}
        req.handover=handover;compliance.start("rep",v)
        assertNotNull(ReproductionPlanDao[p].started)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
    }
    @Test fun `actual flow submit selects physical definition and digital cannot use legacy approval`()=fixture {
        prepared()
        val requests=mutableListOf<StartFlowRequest>()
        `when`(flow.startBusiness(any() ?: StartFlowRequest(definitionKey="unused",title="unused"),anyString(),anyString(), any<(FlowInstanceDao) -> Unit>() ?: {})).thenAnswer { call->
            val req=call.getArgument<StartFlowRequest>(0);requests+=req
            FlowInstanceDao.new {definitionId="def";definitionKey=req.definitionKey;definitionName="流程";title=req.title;initiator="operator";initiatorName="经办"}
                .also { call.getArgument<(FlowInstanceDao) -> Unit>(3)(it) }
        }
        service.submit("rep")
        assertEquals(CollectionConst.FLOW_REPRODUCTION_2026,requests.last().definitionKey)
        assertEquals(listOf("keeper"),requests.last().selfSelectAssignees!!["keeper"])
        locks.release(CollectionConst.BIZ_REPRODUCTION,"rep")
        ReproductionDao.new("photo"){orgId="museum";collectionId="piece";repType=EnumRepType.PHOTO;purpose="图像采集";repDate=at.toLocalDate()}
        assertThrows(BadRequestException::class.java){service.submit("photo")}
        assertEquals(1,requests.size)
    }
    @Test fun `current select duty reads physical plan but cannot repeat an administrative write`()=fixture {
        val p=prepared();val v=permit(p)
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        actorRole("operator","FILE_operator",setOf(CollectionConst.SELECT))
        assertDoesNotThrow {compliance.detail("rep")}
        assertThrows(BadRequestException::class.java){compliance.administrative("rep",v)}
        assertEquals(1L,CollectionAdministrativeRecordDao.count())
    }
    @Test fun `permanent original is bound to the selected reproduction plan stage and current read duty`()=fixture {
        val fileId="2102704108634521999"
        FileDao.new(fileId){name="方案现状原件.pdf";path="/files/reproduction-stage.pdf";hash="stage";size=100;ext="pdf"}
        val planId=compliance.savePlan("rep",plan().copy(conditionFileIds=fileId))
        @Suppress("UNCHECKED_CAST")
        val saved=(compliance.detail("rep")["plans"] as List<Map<String,Any?>>).single()
        @Suppress("UNCHECKED_CAST")
        val archive=(saved["archives"] as List<Map<String,Any?>>).single {it["phase"]=="PLAN"}
        val archiveId=archive["id"] as String
        assertDoesNotThrow {compliance.requireArchivedOriginal("rep",planId,archiveId,fileId)}
        assertThrows(BadRequestException::class.java) {compliance.requireArchivedOriginal("rep","2102704108634521998",archiveId,fileId)}
        assertThrows(BadRequestException::class.java) {compliance.requireArchivedOriginal("rep",planId,"2102704108634521997",fileId)}
        assertThrows(BadRequestException::class.java) {compliance.requireArchivedOriginal("rep",planId,archiveId,"2102704108634521996")}
        assertThrows(BadRequestException::class.java) {compliance.requireArchivedOriginal("missing",planId,archiveId,fileId)}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        actorRole("operator","REPRODUCTION_READER",setOf(CollectionConst.SELECT))
        assertDoesNotThrow {compliance.requireArchivedOriginal("rep",planId,archiveId,fileId)}
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java) {compliance.requireArchivedOriginal("rep",planId,archiveId,fileId)}
    }
    private fun retainedCase():Triple<String,String,String> {
        val p=ready()
        compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90),"operator",true,"场所和原件无异常","维持保护","file"))
        val out=compliance.output("rep",output());compliance.output("rep",output("COPY-2",2));compliance.complete("rep",completion())
        val use=compliance.recordUse("rep",out,ReproductionOutputUseVo(at.plusHours(5),"科学研究","研究室","operator","file"))
        compliance.returnOutput("rep",out,use,ReproductionOutputReturnVo(at.plusHours(6),"辅助品库","keeper","现状无差异","file"))
        return Triple(p,out,use)
    }

    private fun declaredOriginalCase(stage: String): Triple<String, String?, String?> {
        val p = ready()
        var out: String? = null; var use: String? = null
        if (stage == "SAFETY") compliance.safety("rep", ReproductionSafetyVo(at.plusMinutes(90), "operator", true, "检查无异常", "保持防护", "file"))
        if (stage in setOf("OUTPUT", "COMPLETED", "CUSTODY", "USE", "RETURN")) out = compliance.output("rep", output())
        if (stage in setOf("COMPLETED", "CUSTODY", "USE", "RETURN")) compliance.complete("rep", completion())
        if (stage == "CUSTODY") compliance.transferCustody("rep", out!!, custodyValue(out))
        if (stage in setOf("USE", "RETURN")) use = compliance.recordUse("rep", out!!, ReproductionOutputUseVo(at.plusHours(5), "科学研究", "研究室", "operator", "file"))
        if (stage == "RETURN") compliance.returnOutput("rep", out!!, use!!, ReproductionOutputReturnVo(at.plusHours(6), "辅助品库", "keeper", "现状无差异", "file"))
        if (stage in setOf("STOPPED", "STOP_CLEARED")) compliance.stop("rep", stopValue(p).copy(safeForCustody = stage != "STOP_CLEARED"))
        if (stage == "STOP_CLEARED") {
            val value = IncidentClearanceVo("case", at.plusHours(5), "participant", "operator", "处理后实际现状", "实际保护措施", "file")
            `when`(incidents.requireClearance("case", "museum", "piece", IncidentSourceVo(ReproductionComplianceService.BIZ, "rep", p), value.confirmedAt, keeperUserId="operator"))
                .thenReturn(mapOf("incidentId" to "case", "signedFileIds" to "independent-incident-file"))
            compliance.clearStop("rep", value)
        }
        return Triple(p, out, use)
    }

    private fun assertDeclaredOriginals(stage: String, change: String) {
        val originalFile = "2102704108634521981"; val otherFile = "2102704108634521982"
        listOf(originalFile, otherFile).forEach { id -> FileDao.new(id) { name="$id.pdf"; path="/files/$id.pdf"; hash=id; size=100; ext="pdf" } }
        `when`(binder.resolveSingle("file")).thenReturn(originalFile)
        val (planId, outId, useId) = declaredOriginalCase(stage)
        val archived = CollectionPermanentArchiveDao.all().single {
            it.bizId == planId && if (stage in setOf("PLAN", "STARTED", "COMPLETED", "STOPPED", "SUBMITTED")) it.phase == stage else it.phase.startsWith("${stage}_")
        }
        val extra = archives.seal("museum", ReproductionComplianceService.BIZ, planId, "INDEPENDENT_PROOF", mapOf("fact" to "独立保留原件"), otherFile)
        val replacementMetadata = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq extra }.single().metadata
        assertDoesNotThrow { compliance.detail("rep") }
        assertDoesNotThrow { compliance.requirePrepared(ReproductionDao["rep"]) }
        val saved = CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archived.id.value }.singleOrNull()
            ?.let { listOf(it.id.value, it.orgId, it.archiveId, it.bizId, it.fileId, it.metadata) }
        var added: String? = null
        fun stored() = listOf(Reproductions, ReproductionPlans, ReproductionSafetyRecords, ReproductionOutputs, ReproductionOutputUses,
            CollectionAdministrativeRecords, CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, CollectionOperationLocks)
            .associate { t -> t.tableName to t.selectAll().map { row -> t.columns.map { row[it]?.toString() } }.sortedBy { it.toString() } }
        try {
            when (change) {
                "MISSING" -> CollectionPermanentArchiveFileDao[saved!![0]].delete()
                "REPLACED" -> CollectionPermanentArchiveFileDao[saved!![0]].apply { fileId=otherFile; metadata=replacementMetadata }
                else -> added = CollectionPermanentArchiveFileDao.new {
                    orgId="museum"; archiveId=archived.id.value; bizId=planId; fileId=otherFile; metadata=replacementMetadata
                }.id.value
            }
            TransactionManager.current().entityCache.flush()
            // 完成准备数据及损坏引用的审计回调，再比较独立续办事务，不能把延迟的准备写入算作读取副作用。
            TransactionManager.current().alertSubscribers()
            TransactionManager.current().entityCache.flush()
            TransactionManager.current().alertSubscribers()
            TransactionManager.current().entityCache.flush()
            val before = stored()
            assertAll(
                { assertThrows(BadRequestException::class.java) { compliance.detail("rep") } },
                { assertThrows(BadRequestException::class.java) { compliance.formData(ReproductionDao["rep"]) } },
                { assertThrows(BadRequestException::class.java) { archives.listForAuthorizedOrg(ReproductionComplianceService.BIZ, planId, "museum") } },
                { assertThrows(BadRequestException::class.java) { compliance.requireArchivedOriginal("rep", planId, archived.id.value, if (change == "MISSING") originalFile else otherFile) } },
                { assertThrows(BadRequestException::class.java) {
                    when (stage) {
                        "COMPLETED", "CUSTODY", "RETURN" -> compliance.recordUse("rep", outId!!, ReproductionOutputUseVo(at.plusHours(7), "科学研究", "实际研究室", "operator", "file"))
                        "USE" -> compliance.returnOutput("rep", outId!!, useId!!, ReproductionOutputReturnVo(at.plusHours(8), "辅助品库", "keeper", "现状无差异", "file"))
                        else -> compliance.safety("rep", ReproductionSafetyVo(at.plusHours(6), "operator", true, "真实后续检查", "维持防护", "file"))
                    }
                } }
            )
            assertEquals(before, stored(), "Rejected reads and continuation must preserve all business and original rows")
        } finally {
            added?.let { CollectionPermanentArchiveFileDao.findById(it)?.delete() }
            saved?.let { v ->
                (CollectionPermanentArchiveFileDao.findById(v[0]) ?: CollectionPermanentArchiveFileDao.new(v[0]) {
                    orgId=v[1]; archiveId=v[2]; bizId=v[3]; fileId=v[4]; metadata=v[5]
                }).apply { orgId=v[1]; archiveId=v[2]; bizId=v[3]; fileId=v[4]; metadata=v[5] }
            }
            TransactionManager.current().entityCache.flush()
        }
        assertDoesNotThrow { compliance.detail("rep") }
        assertDoesNotThrow { compliance.formData(ReproductionDao["rep"]) }
        assertDoesNotThrow { archives.listForAuthorizedOrg(ReproductionComplianceService.BIZ, planId, "museum") }
    }

    @ParameterizedTest @CsvSource(
        "PLAN,MISSING", "PLAN,REPLACED", "PLAN,EXTRA", "ADMIN,MISSING", "ADMIN,REPLACED", "ADMIN,EXTRA",
        "STARTED,MISSING", "STARTED,REPLACED", "STARTED,EXTRA", "SAFETY,MISSING", "SAFETY,REPLACED", "SAFETY,EXTRA",
        "OUTPUT,MISSING", "OUTPUT,REPLACED", "OUTPUT,EXTRA", "COMPLETED,MISSING", "COMPLETED,REPLACED", "COMPLETED,EXTRA",
        "STOPPED,MISSING", "STOPPED,REPLACED", "STOPPED,EXTRA", "STOP_CLEARED,MISSING", "STOP_CLEARED,REPLACED", "STOP_CLEARED,EXTRA",
        "CUSTODY,MISSING", "CUSTODY,REPLACED", "CUSTODY,EXTRA", "USE,MISSING", "USE,REPLACED", "USE,EXTRA",
        "RETURN,MISSING", "RETURN,REPLACED", "RETURN,EXTRA", "SUBMITTED,EXTRA", "APPROVED,EXTRA"
    )
    fun `physical declared originals reject missing replacement and extra references`(stage: String, change: String) = fixture {
        assertDeclaredOriginals(stage, change)
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @CsvSource(
        "PLAN,MISSING", "PLAN,REPLACED", "PLAN,EXTRA", "ADMIN,MISSING", "ADMIN,REPLACED", "ADMIN,EXTRA",
        "STARTED,MISSING", "STARTED,REPLACED", "STARTED,EXTRA", "SAFETY,MISSING", "SAFETY,REPLACED", "SAFETY,EXTRA",
        "OUTPUT,MISSING", "OUTPUT,REPLACED", "OUTPUT,EXTRA", "COMPLETED,MISSING", "COMPLETED,REPLACED", "COMPLETED,EXTRA",
        "STOPPED,MISSING", "STOPPED,REPLACED", "STOPPED,EXTRA", "STOP_CLEARED,MISSING", "STOP_CLEARED,REPLACED", "STOP_CLEARED,EXTRA",
        "CUSTODY,MISSING", "CUSTODY,REPLACED", "CUSTODY,EXTRA", "USE,MISSING", "USE,REPLACED", "USE,EXTRA",
        "RETURN,MISSING", "RETURN,REPLACED", "RETURN,EXTRA", "SUBMITTED,EXTRA", "APPROVED,EXTRA"
    )
    fun `postgres physical declared originals reject missing replacement and extra references`(stage: String, change: String) = fixture(postgres=true) {
        assertDeclaredOriginals(stage, change)
    }

    private fun originalState() = listOf(Reproductions, ReproductionPlans, ReproductionSafetyRecords,
        ReproductionOutputs, ReproductionOutputUses, CollectionAdministrativeRecords,
        CollectionPermanentArchives, CollectionPermanentArchiveFiles, Files, CollectionOperationLocks,
        Collections, Requisitions, CollectionIncidentItems).associate { table ->
        table.tableName to table.selectAll().map { row -> table.columns.map { row[it]?.toString() } }.sortedBy { it.toString() }
    }

    private fun corruptStoppedOriginal(planId: String, stage: String, change: String, rejected: () -> Unit) {
        val archive=CollectionPermanentArchiveDao.all().single { it.bizId==planId &&
            (if(stage in setOf("SAFETY","STOP_CLEARED")) it.phase.startsWith("${stage}_") else it.phase==stage) }
        val saved=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq archive.id.value }.single()
            .let { listOf(it.id.value,it.orgId,it.archiveId,it.bizId,it.fileId,it.metadata) }
        val other="2102704108634521972"
        val independent=archives.seal("museum",ReproductionComplianceService.BIZ,planId,"INDEPENDENT_PROOF",mapOf("fact" to "独立原件"),other)
        val metadata=CollectionPermanentArchiveFileDao.find { CollectionPermanentArchiveFiles.archiveId eq independent }.single().metadata
        var added:String?=null
        try {
            when(change) {
                "MISSING" -> CollectionPermanentArchiveFileDao[saved[0]].delete()
                "REPLACED" -> CollectionPermanentArchiveFileDao[saved[0]].apply { fileId=other;this.metadata=metadata }
                else -> added=CollectionPermanentArchiveFileDao.new {
                    orgId="museum";archiveId=archive.id.value;bizId=planId;fileId=other;this.metadata=metadata
                }.id.value
            }
            val tx=TransactionManager.current()
            repeat(2) { tx.entityCache.flush();tx.alertSubscribers() };tx.entityCache.flush()
            val before=originalState()
            rejected()
            tx.entityCache.flush()
            val after=originalState()
            val differences=before.flatMap { (table,rows) ->
                val current=after.getValue(table)
                if(rows.size!=current.size) listOf("$table: row count ${rows.size} -> ${current.size}")
                else rows.zip(current).flatMapIndexed { index,(expected,actual) ->
                    expected.zip(actual).mapIndexedNotNull { column,(left,right) ->
                        if(left==right)null else "$table[$index][$column]: ${left?.take(90)} -> ${right?.take(90)}"
                    }
                }
            }
            assertTrue(differences.isEmpty(),"Independent evidence rejection must preserve all 13 tables including audits: ${differences.joinToString()}")
        } finally {
            added?.let { CollectionPermanentArchiveFileDao.findById(it)?.delete() }
            (CollectionPermanentArchiveFileDao.findById(saved[0]) ?: CollectionPermanentArchiveFileDao.new(saved[0]) {
                orgId=saved[1];archiveId=saved[2];bizId=saved[3];fileId=saved[4];this.metadata=saved[5]
            }).apply { orgId=saved[1];archiveId=saved[2];bizId=saved[3];fileId=saved[4];this.metadata=saved[5] }
            TransactionManager.current().entityCache.flush()
        }
    }

    private fun prepareIndependentOriginals() {
        listOf("2102704108634521971","2102704108634521972").forEach { id ->
            FileDao.new(id) { name="$id.pdf";path="/files/$id.pdf";hash=id;size=100;ext="pdf" }
        }
        `when`(binder.resolveSingle("file")).thenReturn("2102704108634521971")
    }

    private fun assertSafetySelectionDrift(stage:String,change:String) {
        val planId=originalReady()
        when(stage) {
            "STOPPED" -> compliance.stop("rep",stopValue(planId))
            "COMPLETED" -> { compliance.output("rep",output());compliance.complete("rep",completion()) }
        }
        val guard=ReproductionOperationSafetyGuard(archives,incidents)
        fun valid() {
            assertDoesNotThrow { guard.requireAllowed("piece",ReproductionComplianceService.BIZ,"rep") }
            if(stage=="ACTIVE") {
                assertThrows(BadRequestException::class.java) { guard.requireAllowed("piece","other-work","other") }
                assertThrows(BadRequestException::class.java) { guard.requireSafeResolution("piece") }
            } else {
                assertDoesNotThrow { guard.requireAllowed("piece","other-work","other") }
                assertDoesNotThrow { guard.requireSafeResolution("piece") }
            }
        }
        valid()
        val p=ReproductionPlanDao[planId]
        val started=p.started;val completed=p.completed;val content=p.content
        fun flush() {
            val tx=TransactionManager.current()
            repeat(2) { tx.entityCache.flush();tx.alertSubscribers() };tx.entityCache.flush()
        }
        try {
            when(change) {
                "STARTED" -> p.started=null
                "COMPLETED" -> p.completed=if(stage=="COMPLETED") null else completion()
                "ORIGINAL" -> p.content=p.content.copy(usesOriginal=false)
            }
            flush()
            val before=originalState()
            assertAll(
                { assertThrows(BadRequestException::class.java) { guard.requireAllowed("piece",ReproductionComplianceService.BIZ,"rep") } },
                { assertThrows(BadRequestException::class.java) { guard.requireAllowed("piece","other-work","other") } },
                { assertThrows(BadRequestException::class.java) { guard.requireSafeResolution("piece") } },
                { assertThrows(BadRequestException::class.java) { guard.requireExclusiveWork("piece","other-work","other") } }
            )
            flush()
            assertEquals(before,originalState(),"Safety selection rejection must preserve every field in all 13 tables")
        } finally {
            p.started=started;p.completed=completed;p.content=content;flush()
        }
        valid()
    }

    @ParameterizedTest @CsvSource("ACTIVE,STARTED","ACTIVE,COMPLETED","ACTIVE,ORIGINAL",
        "STOPPED,STARTED","STOPPED,COMPLETED","STOPPED,ORIGINAL",
        "COMPLETED,STARTED","COMPLETED,COMPLETED","COMPLETED,ORIGINAL")
    fun `safety selection rejects lifecycle flags that contradict frozen originals`(stage:String,change:String)=fixture {
        assertSafetySelectionDrift(stage,change)
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @CsvSource("ACTIVE,STARTED","ACTIVE,COMPLETED","ACTIVE,ORIGINAL",
        "STOPPED,STARTED","STOPPED,COMPLETED","STOPPED,ORIGINAL",
        "COMPLETED,STARTED","COMPLETED,COMPLETED","COMPLETED,ORIGINAL")
    fun `postgres safety selection rejects lifecycle flags that contradict frozen originals`(stage:String,change:String)=fixture(postgres=true) {
        assertSafetySelectionDrift(stage,change)
    }

    private fun assertSafetySelectionControls() {
        val guard=ReproductionOperationSafetyGuard(archives,incidents)
        val draft=compliance.savePlan("rep",plan().copy(usesOriginal=true))
        assertDoesNotThrow { guard.requireAllowed("piece","other-work","other") }
        assertDoesNotThrow { guard.requireSafeResolution("piece") }
        // 尚未实际开始的草稿不形成原件使用占用；合法的不使用原件制作也不得被误拦。
        val p=prepared(plan().copy(replacementReason="据实改用副版制作",replacementFileIds="file"))
        approve(p);compliance.start("rep",start(p))
        assertNotEquals(draft,p)
        assertFalse(ReproductionPlanDao[p].content.usesOriginal)
        assertDoesNotThrow { guard.requireAllowed("piece","other-work","other") }
        assertDoesNotThrow { guard.requireSafeResolution("piece") }
        compliance.output("rep",output());compliance.complete("rep",completion())
        assertDoesNotThrow { guard.requireAllowed("piece","other-work","other") }
        assertDoesNotThrow { guard.requireSafeResolution("piece") }
    }

    @Test fun `safety selection preserves unstarted drafts and legitimate work without original use`()=fixture {
        assertSafetySelectionControls()
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres safety selection preserves unstarted drafts and legitimate work without original use`()=fixture(postgres=true) {
        assertSafetySelectionControls()
    }

    private fun assertSafetyLockAudit() {
        originalReady()
        fun flush() {
            val tx=TransactionManager.current()
            repeat(2) { tx.entityCache.flush();tx.alertSubscribers() };tx.entityCache.flush()
        }
        flush()
        val before=originalState()
        val priorTime=CollectionOperationLocks.selectAll().single()[CollectionOperationLocks.updateTime]!!
        ReproductionOperationSafetyGuard(archives,incidents).requireAllowed("piece",ReproductionComplianceService.BIZ,"rep")
        flush()
        assertEquals(before,originalState(),"Reading the running source must preserve lock audits as well as the other 12 tables")
        val previous=SecurityContextHolder.getContext().authentication
        val keeper=SecurityUser(id="keeper",name="保管人",username="keeper",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        try {
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(keeper,null,keeper.authorities)
            CollectionOperationLockDao.all().single().lockedBy="keeper"
            flush()
            val updated=CollectionOperationLocks.selectAll().single()
            assertTrue(updated[CollectionOperationLocks.updateTime]!!.isAfter(priorTime))
            assertEquals("keeper",updated[CollectionOperationLocks.updateBy])
            assertEquals("keeper",updated[CollectionOperationLocks.lockedBy])
        } finally { SecurityContextHolder.getContext().authentication=previous }
    }

    @Test fun `safety selection lock audits preserve reads and record actual actor and business changes`()=fixture {
        assertSafetyLockAudit()
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres safety selection lock audits preserve reads and record actual actor and business changes`()=fixture(postgres=true) {
        assertSafetyLockAudit()
    }

    private fun assertIndependentStoppedOriginals(stage:String,change:String) {
        prepareIndependentOriginals()
        val planId=originalReady()
        compliance.safety("rep",ReproductionSafetyVo(at.plusMinutes(90),"operator",true,"停止前已核实场所安全","保持保护","file"))
        compliance.stop("rep",stopValue(planId).copy(originalUndamaged=false,safeForCustody=false))
        val source=IncidentSourceVo(ReproductionComplianceService.BIZ,"rep",planId)
        val clearance=IncidentClearanceVo("case",at.plusHours(5),"conservator","operator","已核实安全","实际保护措施","file")
        `when`(incidents.requireClearance("case","museum","piece",source,clearance.confirmedAt,keeperUserId="operator"))
            .thenReturn(mapOf("incidentId" to "case","basisRevision" to "实际事故原件"))
        compliance.clearStop("rep",clearance)
        val stored=ReproductionPlanDao[planId].clearance!!.incidentProof
        `when`(incidents.requireStoredClearance("case","museum","piece",source,clearance.confirmedAt,stored,keeperUserId="operator")).thenReturn(stored)
        val guard=ReproductionOperationSafetyGuard(archives,incidents)
        val provider=ReproductionIncidentSourceProvider(archives)
        val items=listOf(IncidentItemVo("piece",true,EnumIncidentSecurity.NONE,false,"停止时已核实损伤"))
        val facts=IncidentFactsVo("真实停止事故",at.plusHours(4),location="制作室",reporterUserId="operator",
            description="已核实原件损伤",immediateMeasures="停止制作并保护原件",items=items,source=source)
        fun accepted() {
            assertDoesNotThrow { guard.requireAllowed("piece",null,null) }
            assertDoesNotThrow { guard.requireSafeResolution("piece") }
            assertDoesNotThrow { guard.requireExclusiveWork("piece","other-work","other") }
            assertDoesNotThrow { guard.requireHandlingEvidence(facts,at.plusHours(6)) }
            assertDoesNotThrow { provider.snapshot("museum",source,items) }
            assertDoesNotThrow { provider.stoppedCustody("museum","piece",source) }
        }
        accepted()
        corruptStoppedOriginal(planId,stage,change) {
            val checks=mutableListOf<org.junit.jupiter.api.function.Executable>(
                org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { guard.requireAllowed("piece",null,null) } })
            if(stage!="STOP_CLEARED") {
                checks+=org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { guard.requireSafeResolution("piece") } }
                checks+=org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { guard.requireExclusiveWork("piece","other-work","other") } }
                checks+=org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { provider.snapshot("museum",source,items) } }
                checks+=org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { provider.stoppedCustody("museum","piece",source) } }
            }
            if(stage in setOf("PLAN","STARTED","STOPPED"))
                checks+=org.junit.jupiter.api.function.Executable { assertThrows(BadRequestException::class.java) { guard.requireHandlingEvidence(facts,at.plusHours(6)) } }
            assertAll(checks)
        }
        accepted()
    }

    @ParameterizedTest @CsvSource("PLAN,MISSING","PLAN,REPLACED","PLAN,EXTRA",
        "STARTED,MISSING","STARTED,REPLACED","STARTED,EXTRA","STOPPED,MISSING","STOPPED,REPLACED","STOPPED,EXTRA",
        "SAFETY,MISSING","SAFETY,REPLACED","SAFETY,EXTRA","STOP_CLEARED,MISSING","STOP_CLEARED,REPLACED","STOP_CLEARED,EXTRA")
    fun `independent stop evidence rejects missing replaced and extra declared originals`(stage:String,change:String)=fixture {
        assertIndependentStoppedOriginals(stage,change)
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @CsvSource("PLAN,MISSING","PLAN,REPLACED","PLAN,EXTRA",
        "STARTED,MISSING","STARTED,REPLACED","STARTED,EXTRA","STOPPED,MISSING","STOPPED,REPLACED","STOPPED,EXTRA",
        "SAFETY,MISSING","SAFETY,REPLACED","SAFETY,EXTRA","STOP_CLEARED,MISSING","STOP_CLEARED,REPLACED","STOP_CLEARED,EXTRA")
    fun `postgres independent stop evidence rejects missing replaced and extra declared originals`(stage:String,change:String)=fixture(postgres=true) {
        assertIndependentStoppedOriginals(stage,change)
    }

    private fun assertDraftOriginals(change:String) {
        prepareIndependentOriginals()
        val value=plan();val planId=compliance.savePlan("rep",value)
        val revised=value.copy(preparedAt=at.plusHours(1),replacementReason="依据原件订正",replacementFileIds="file")
        assertEquals(planId,compliance.savePlan("rep",value))
        corruptStoppedOriginal(planId,"PLAN",change) {
            assertAll(
                { assertThrows(BadRequestException::class.java) { compliance.savePlan("rep",value) } },
                { assertThrows(BadRequestException::class.java) { compliance.savePlan("rep",revised) } }
            )
        }
        assertEquals(planId,compliance.savePlan("rep",value))
        val next=compliance.savePlan("rep",revised)
        assertNotEquals(planId,next)
        assertEquals(2L,ReproductionPlanDao.count())
        assertEquals(value,ReproductionPlanDao[planId].content)
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["MISSING","REPLACED","EXTRA"])
    fun `draft repeat and revision require the preserved previous plan originals`(change:String)=fixture { assertDraftOriginals(change) }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings=["MISSING","REPLACED","EXTRA"])
    fun `postgres draft repeat and revision require the preserved previous plan originals`(change:String)=fixture(postgres=true) { assertDraftOriginals(change) }

    private fun assertSourceLedgerAudit() {
        fun flush() {
            val tx=TransactionManager.current()
            repeat(2) { tx.entityCache.flush();tx.alertSubscribers() };tx.entityCache.flush()
        }
        val p=originalReady();compliance.stop("rep",stopValue(p));flush()
        val before=originalState()
        val collectionTime=Collections.selectAll().single()[Collections.updateTime]!!
        val requisitionTime=Requisitions.selectAll().single()[Requisitions.updateTime]!!
        ReproductionOperationSafetyGuard(archives,incidents).requireAllowed("piece",null,null)
        compliance.detail("rep");flush()
        assertEquals(before,originalState(),"Reading the stopped source must not rewrite ledger or requisition audit fields")
        val previous=SecurityContextHolder.getContext().authentication
        val keeper=SecurityUser(id="keeper",name="保管人",username="keeper",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        try {
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(keeper,null,keeper.authorities)
            CollectionDao["piece"].lifeStatus=EnumCollectionStatus.IN_STORE
            RequisitionDao["req"].returned=true
            flush()
            assertTrue(Collections.selectAll().single()[Collections.updateTime]!!.isAfter(collectionTime))
            assertTrue(Requisitions.selectAll().single()[Requisitions.updateTime]!!.isAfter(requisitionTime))
            assertEquals("keeper",Collections.selectAll().single()[Collections.updateBy])
            assertEquals("keeper",Requisitions.selectAll().single()[Requisitions.updateBy])
        } finally { SecurityContextHolder.getContext().authentication=previous }
    }

    @Test fun `source ledger and requisition audits change only with actual business facts and actor`()=fixture { assertSourceLedgerAudit() }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres source ledger and requisition audits change only with actual business facts and actor`()=fixture(postgres=true) { assertSourceLedgerAudit() }

    private fun assertActualLifecycleAudit() {
        fun flushAudit() {
            val tx=TransactionManager.current()
            repeat(2) { tx.entityCache.flush(); tx.alertSubscribers() }
            tx.entityCache.flush()
        }
        val p=prepared(); approve(p); compliance.start("rep",start(p)); flushAudit()
        val startedAt=ReproductionPlans.selectAll().single()[ReproductionPlans.updateTime]!!
        assertEquals("operator",ReproductionPlanDao[p].updateBy)
        compliance.detail("rep"); compliance.formData(ReproductionDao["rep"]); flushAudit()
        assertEquals(startedAt,ReproductionPlans.selectAll().single()[ReproductionPlans.updateTime],"Read-only evidence must not become a business modification")
        compliance.output("rep",output()); compliance.complete("rep",completion()); flushAudit()
        assertTrue(ReproductionPlans.selectAll().single()[ReproductionPlans.updateTime]!!.isAfter(startedAt),"A second real lifecycle change must still refresh the audit")
        assertEquals("operator",ReproductionPlanDao[p].updateBy)
    }

    @Test fun `physical lifecycle audit distinguishes reading from a second actual business change`() = fixture {
        assertActualLifecycleAudit()
    }

    @org.junit.jupiter.api.Tag("postgres-concurrency")
    @Test fun `postgres physical lifecycle audit distinguishes reading from a second actual business change`() = fixture(postgres=true) {
        assertActualLifecycleAudit()
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["plan-org","permit-org","safety-org","output-org","use-org","plan-content","safety-content","output-number","use-content","return-content","started","completed","returned","missing-output","missing-use","foreign-archive","use-output"])
    fun `physical detail and flow preview reject changed or foreign retained records`(change:String)=fixture {
        val (planId,outId,useId)=retainedCase();val p=ReproductionPlanDao[planId];val out=ReproductionOutputDao[outId];val use=ReproductionOutputUseDao[useId]
        assertDoesNotThrow {compliance.detail("rep")};assertDoesNotThrow {compliance.formData(ReproductionDao["rep"])}
        when(change) {
            "plan-org"->p.orgId="other"
            "permit-org"->CollectionAdministrativeRecordDao.all().single().orgId="other"
            "safety-org"->ReproductionSafetyDao.all().single().orgId="other"
            "output-org"->out.orgId="other"
            "use-org"->use.orgId="other"
            "plan-content"->p.content=p.content.copy(siteAddress="改写场所")
            "safety-content"->ReproductionSafetyDao.all().single().content=ReproductionSafetyDao.all().single().content.copy(findings="改写检查")
            "output-number"->out.number="REPLACED"
            "use-content"->use.content=use.content.copy(useLocation="改写使用位置")
            "return-content"->use.returned=use.returned!!.copy(location="改写归还位置")
            "started"->p.started=null
            "completed"->p.completed=null
            "returned"->use.returned=null
            "missing-output"->out.delete()
            "missing-use"->use.delete()
            "use-output"->use.outputId=ReproductionOutputDao.all().first {it.id.value!=outId}.id.value
            else->CollectionPermanentArchiveDao.new {orgId="other";bizType=ReproductionComplianceService.BIZ;bizId=planId;phase="FOREIGN";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)}
        }
        assertAll(
            {assertThrows(BadRequestException::class.java){compliance.detail("rep")}},
            {assertThrows(BadRequestException::class.java){compliance.formData(ReproductionDao["rep"])}}
        )
    }
    @Test fun `original museum retains physical plans and output history after collection transfer`()=fixture {
        retainedCase()
        val before=CollectionPermanentArchiveDao.all().map {it.id.value to (it.snapshot to it.digest)}
        val source=ReproductionPlanDao.all().single().sourceSnapshot
        CollectionDao["piece"].orgId="other";CollectionDao["piece"].name="接收馆的新名称"
        val detail=compliance.detail("rep")
        assertNull(detail["sourceRevision"]);assertNotNull(detail["sourceIssue"])
        assertEquals(source,compliance.formData(ReproductionDao["rep"])["sourceSnapshot"])
        assertEquals(before,CollectionPermanentArchiveDao.all().map {it.id.value to (it.snapshot to it.digest)})
    }
    @Test fun `legacy use content remains readable without inventing frozen product association`()=fixture {
        val (_,outId,useId)=retainedCase();val use=ReproductionOutputUseDao[useId]
        val legacy=CollectionPermanentArchiveDao.all().filter {it.phase=="USE_$useId" || it.phase=="RETURN_$useId"}
        legacy.forEach {a->a.snapshot=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(if(a.phase.startsWith("USE_"))use.content else use.returned)!!;a.digest=CollectionCareEvidence.digest(a.snapshot)}
        val before=legacy.map {it.id.value to (it.snapshot to it.digest)}
        fun usages():List<Map<String,Any?>> {
            @Suppress("UNCHECKED_CAST") val plans=compliance.detail("rep")["plans"] as List<Map<String,Any?>>
            @Suppress("UNCHECKED_CAST") val outputs=plans.single()["outputs"] as List<Map<String,Any?>>
            @Suppress("UNCHECKED_CAST") return outputs.flatMap {it["uses"] as List<Map<String,Any?>>}
        }
        assertEquals(false,usages().single()["associationVerified"])
        assertEquals(before,legacy.map {it.id.value to (it.snapshot to it.digest)})
        val current=compliance.recordUse("rep",outId,ReproductionOutputUseVo(at.plusHours(7),"科学研究","研究室","operator","file"))
        assertEquals(true,usages().single {it["id"]==current}["associationVerified"])
        assertEquals(false,usages().single {it["id"]==useId}["associationVerified"])
        assertEquals(before,legacy.map {it.id.value to (it.snapshot to it.digest)})
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["col_reproduction_plan","col_reproduction_safety","col_reproduction_output","col_reproduction_output_use"])
    fun `physical record ownership is checked before malformed private content`(table:String)=fixture {
        retainedCase()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("UPDATE $table SET org_id='other', content='{}'")
        val failure=assertThrows(BadRequestException::class.java){compliance.detail("rep")}
        assertTrue(failure.message!!.contains("所属馆"))
    }
}
