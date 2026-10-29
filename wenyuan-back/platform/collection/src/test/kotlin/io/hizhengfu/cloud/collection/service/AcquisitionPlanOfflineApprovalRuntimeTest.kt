package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.LocalFileObjectStorage
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.nio.file.Path
import java.time.LocalDateTime

class AcquisitionPlanOfflineApprovalRuntimeTest {
    @TempDir lateinit var dir:Path
    private val approval=AcquisitionPlanOfflineApprovalVo(LocalDateTime.of(2026,1,2,12,0),"馆长","2026年征集计划批准件","signed","此前已线下批准，本次整理档案")
    private fun fixture(body:(AcquisitionPlanService,FlowInstanceService)->Unit) {
        val db=Database.connect("jdbc:sqlite:${dir.resolve("offline.db")}","org.sqlite.JDBC")
        val user=SecurityUser(id="archivist",name="归档人员",username="archivist",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority(CollectionConst.ACQUIRE)),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        try{transaction(db){
            originalReaderSchema();originalReader("archivist",CollectionConst.ACQUIRE)
            SchemaUtils.create(CollectionOrgs,AcquisitionPlans,Acquisitions,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files)
            for(org in listOf("museum","other"))CollectionOrgDao.new(org){name=org;code=org;systemGroupId=org}
            AcquisitionPlanDao.new("plan"){orgId="museum";title="年度征集计划";year=2026;createTime=LocalDateTime.of(2026,10,1,0,0)}
            AcquisitionPlanDao.new("foreign"){orgId="other";title="他馆计划";year=2026}
            java.nio.file.Files.writeString(dir.resolve("signed.txt"),"真实签署批准计划")
            FileDao.new("signed"){name="签署计划.txt";path="signed.txt";ext="txt";size=java.nio.file.Files.size(dir.resolve("signed.txt"));hash="fixture"}
            val binder=mock(BusinessAttachmentBinder::class.java).also{`when`(it.resolveSingle(anyString())).thenAnswer{call->call.getArgument<String>(0)}}
            val scope=CollectionScopeResolver("multi","museum")
            val archives=CollectionPermanentArchiveService(binder,scope,LocalFileObjectStorage(dir.toString()),unprotectedArchiveReadAccessProvider())
            val flow=mock(FlowInstanceService::class.java)
            body(AcquisitionPlanService(flow,scope,binder,permanentArchives=archives),flow)
        }}finally{SecurityContextHolder.clearContext()}
    }
    @Test fun `offline approved original is permanently archived without a fabricated online instance`()=fixture {service,flow->
        assertEquals("plan",service.archiveOfflineApproval("plan",approval))
        val result=service.get("plan")!!
        assertEquals(EnumFlowStatus.PASS,result.flowStatus);assertNull(result.flowInstanceId);assertEquals(approval,result.offlineApproval)
        assertEquals("signed",result.attachmentIds)
        assertEquals(1L,CollectionPermanentArchiveDao.count());assertEquals(1L,CollectionPermanentArchiveFileDao.count())
        verifyNoInteractions(flow)
        assertEquals("plan",service.archiveOfflineApproval("plan",approval));assertEquals(1L,CollectionPermanentArchiveDao.count())
        assertThrows(BadRequestException::class.java){service.archiveOfflineApproval("plan",approval.copy(approvedBy="另一个人"))}
        assertThrows(BadRequestException::class.java){service.save(result.copy(title="覆盖原计划"))}
        assertThrows(BadRequestException::class.java){service.submit("plan")}
        assertEquals("年度征集计划",AcquisitionPlanDao["plan"].title)
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("signed")}
        java.nio.file.Files.writeString(dir.resolve("signed.txt"),"被替换的内容")
        assertThrows(BadRequestException::class.java){service.get("plan")}
    }
    @Test fun `missing or future approval and retrospective recording without explanation cannot approve a draft`()=fixture {service,_->
        for(invalid in listOf(approval.copy(approvedAt=null),approval.copy(approvedAt=LocalDateTime.now().plusDays(1)),approval.copy(approvedBy=" "),approval.copy(approvalBasis=null),approval.copy(signedFileIds=""),approval.copy(supplementaryReason=null))) {
            assertThrows(BadRequestException::class.java){service.archiveOfflineApproval("plan",invalid)}
            assertEquals(EnumFlowStatus.DRAFT,AcquisitionPlanDao["plan"].flowStatus);assertEquals(0L,CollectionPermanentArchiveDao.count())
        }
        AcquisitionPlanDao["plan"].flowInstanceId="online"
        assertThrows(BadRequestException::class.java){service.archiveOfflineApproval("plan",approval)}
    }
    @Test fun `current scope and permissions protect offline approval writes`()=fixture{service,_->
        assertThrows(BadRequestException::class.java){service.archiveOfflineApproval("foreign",approval)}
        RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.archiveOfflineApproval("plan",approval)}
        assertEquals(0L,CollectionPermanentArchiveDao.count());assertEquals(EnumFlowStatus.DRAFT,AcquisitionPlanDao["plan"].flowStatus)
    }
    @Test fun `historical plan and timestamp precision follow existing year and millisecond contracts`()=fixture {service,_->
        AcquisitionPlanDao["plan"].year=1999
        val request=approval.copy(approvedAt=approval.approvedAt!!.withNano(123456789))
        service.archiveOfflineApproval("plan",request)
        assertEquals(1999,service.get("plan")!!.year)
        assertEquals(request.copy(approvedAt=request.approvedAt!!.withNano(123000000)),service.get("plan")!!.offlineApproval)
        assertEquals("plan",service.archiveOfflineApproval("plan",request))
    }
}
