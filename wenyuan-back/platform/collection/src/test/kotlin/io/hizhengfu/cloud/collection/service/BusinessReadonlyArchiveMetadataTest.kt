package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.RolePermissions
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.LocalDateTime

/** Real DAO/archive metadata reads on isolated SQLite; synthetic file ownership/binding edges, no native API or GUI. */
class BusinessReadonlyArchiveMetadataTest {
    private val scope=CollectionScopeResolver("single","museum")
    private val binder=mock(BusinessAttachmentBinder::class.java).also {
        `when`(it.resolveSingle(anyString())).thenAnswer { call -> call.getArgument<String>(0) }
    }
    private val archives=spy(CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider()))
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val donation=DonationRecognitionService(scope,archives)
    private val designated=DesignatedReceptionService(scope,users,archives)
    private val institution=InstitutionSuccessionCaseService(scope,users,archives)

    private fun fixture(body:()->Unit) {
        val context=StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh()
        SpringContextHolder().setApplicationContext(context)
        val actor=SecurityUser(id="reader",name="普通办理人",username="reader",password="",groupId="museum",groupIds=setOf("museum"),
            postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.ACQUIRE),SimpleGrantedAuthority(CollectionConst.STORAGE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(actor,null,actor.authorities)
        val file=java.nio.file.Files.createTempFile("business-readonly-metadata-",".sqlite")
        val db=Database.connect("jdbc:sqlite:$file",driver="org.sqlite.JDBC")
        try { transaction(db) {
            originalReaderSchema()
            SchemaUtils.create(CollectionOrgs,Collections,Warehouses,CollectionAuxiliaries,Acquisitions,AccessionReceipts,AcquisitionComplianceCases,
                DesignatedReceptions,DesignatedReceptionRecords,CollectionTransfers,CollectionRetainedLedgers,
                InstitutionSuccessionCases,InstitutionSuccessionItems,InstitutionSuccessionRecords,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
            CollectionOrgDao.new("museum"){name="本馆";code="M";systemGroupId="museum";ownership=EnumOwnership.STATE}
            originalReader("reader",CollectionConst.ACQUIRE)
            actorRole("reader","STORAGE_reader",setOf(CollectionConst.STORAGE))
            FileDao.new("same-file"){name="初始原件.pdf";path="/files/original.pdf";ext="pdf";hash="synthetic";size=100}
            body()
        } } finally { SecurityContextHolder.clearContext();context.close();java.nio.file.Files.deleteIfExists(file) }
    }
    private fun rename(name:String) { FileDao["same-file"].name=name }
    private fun name(files:Any?):String {
        val rows=files as List<*>;assertEquals(1,rows.size)
        val row=rows.single() as Map<*,*>;assertEquals("same-file",row["fileId"])
        return JsonUtils.mapper.readTree(row["metadata"] as String).get("name").asString()
    }
    private fun revokeWhileReading() {
        doAnswer { call -> RolePermissions.deleteAll();call.callRealMethod() }.`when`(archives)
            .phaseForDisplay(anyString(),anyString(),anyString(),anyString())
    }

    @Test fun `donation recognition returns each frozen archive metadata and rechecks current reader`()=fixture {
        val a=AcquisitionDao.new("donation"){orgId="museum";name="捐赠对象";source=EnumSource.DONATE;quantity=1;unit="件"}
        val first=DonationRecognitionVo("request-0001",DonationRecognitionKind.SPIRITUAL,"捐赠人",LocalDate.now(),"感谢", "same-file")
        rename("第一原件.pdf")
        val firstId=archives.seal("museum",CollectionConst.BIZ_ACQUISITION,a.id.value,"DONOR_RECOGNITION_${first.requestId}",first,"same-file")
        val second=first.copy(requestId="request-0002",correctsArchiveId=firstId,correctionReason="补正")
        rename("第二原件.pdf")
        val secondId=archives.seal("museum",CollectionConst.BIZ_ACQUISITION,a.id.value,"DONOR_RECOGNITION_${second.requestId}",second,"same-file")
        archives.seal("museum",CollectionConst.BIZ_ACQUISITION,a.id.value,"UNRELATED",mapOf("other" to true),"same-file")
        rename("当前文件名称已改变.pdf")
        val result=donation.list(a.id.value).associateBy {it["id"]}
        assertEquals(setOf(firstId,secondId),result.keys)
        assertEquals("第一原件.pdf",name(result[firstId]!!["files"]));assertEquals("第二原件.pdf",name(result[secondId]!!["files"]))
        val ref=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq firstId}.single()
        ref.orgId="other";assertThrows(BadRequestException::class.java){donation.list(a.id.value)};ref.orgId="museum"
        revokeWhileReading();assertThrows(BadRequestException::class.java){donation.list(a.id.value)}
    }

    @Test fun `designated reception metadata is keyed by its own notice and record phase without mixing archives`()=fixture {
        val at=LocalDateTime.now().minusSeconds(1)
        val notice=DesignatedNoticeVo("museum",EnumAuthorityLevel.PROVINCE,"authority","省文物局","notice",LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1),"本馆",EnumDesignatedSender.LAW_ENFORCEMENT,"移交人","sender","指定依据","same-file",
            DesignatedItemVo("item","指定对象",EnumCollectionNature.OTHER,1,"件",EnumCompleteness.COMPLETE,"完好","same-file"))
        rename("通知原件.pdf");val id=designated.create(notice)
        rename("评估原件.pdf")
        val record=designated.assess(id,DesignatedAssessmentVo(at,"reader","same-file","符合本馆标准",true,
            listOf(DesignatedProfessionalOpinionVo("专家","机构","专业","same-file","真实","有价值",true,"same-file")),"same-file"))
        archives.seal("museum",DesignatedReceptionEvidence.BIZ,id,"UNRELATED",mapOf("other" to true),"same-file")
        rename("当前文件名称已改变.pdf")
        val result=designated.detail(id)["archiveFiles"] as Map<*,*>
        assertEquals(setOf(id,record),result.keys)
        val ownNotice=result[id] as Map<*,*>;val ownRecord=result[record] as Map<*,*>
        assertEquals(setOf("NOTICE"),ownNotice.keys);assertEquals(setOf("ASSESSMENT"),ownRecord.keys)
        assertEquals("通知原件.pdf",name(ownNotice["NOTICE"]));assertEquals("评估原件.pdf",name(ownRecord["ASSESSMENT"]))
        val ref=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.bizId eq record}.single()
        ref.bizId=id;assertThrows(BadRequestException::class.java){designated.detail(id)};ref.bizId=record
        revokeWhileReading();assertThrows(BadRequestException::class.java){designated.detail(id)}
    }

    @Test fun `institution succession metadata stays with exact case item and record and rejects revoked reader`()=fixture {
        rename("机构决定.pdf")
        val id=institution.create(InstitutionSuccessionCaseVo("museum",EnumInstitutionSuccession.MERGER,"本馆","登记标识","机构决定",
            LocalDate.now().minusDays(2),LocalDate.now(),"same-file","实际非营利依据","same-file","same-file","keeper","representative","same-file"))
        rename("未登记对象.pdf")
        val item=institution.addUnrecorded(id,InstitutionUnrecordedVo("U-1","教学模型",1,"件","暂存区","保管人",LocalDateTime.now().minusSeconds(1),
            "reader","特征","same-file","same-file"))
        val key=InstitutionSuccessionItemDao[item].itemKey
        rename("跟进原件.pdf")
        val record=institution.followUp(id,InstitutionItemFollowUpVo(key,LocalDateTime.now().minusSeconds(1),"reader","reader","待核事项","处理措施",LocalDateTime.now(),"same-file"))
        archives.seal("museum",InstitutionSuccessionCaseEvidence.BIZ,id,"UNRELATED",mapOf("other" to true),"same-file")
        rename("当前文件名称已改变.pdf")
        val result=institution.detail(id)["archiveFiles"] as Map<*,*>
        assertEquals(setOf(id,item,record),result.keys)
        assertEquals("机构决定.pdf",name(result[id]));assertEquals("未登记对象.pdf",name(result[item]));assertEquals("跟进原件.pdf",name(result[record]))
        val ref=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.bizId eq item}.single()
        ref.orgId="other";assertThrows(BadRequestException::class.java){institution.detail(id)};ref.orgId="museum"
        revokeWhileReading();assertThrows(BadRequestException::class.java){institution.detail(id)}
    }
}
