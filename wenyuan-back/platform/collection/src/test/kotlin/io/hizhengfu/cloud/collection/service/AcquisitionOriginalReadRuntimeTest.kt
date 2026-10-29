package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.extend.md5
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.common.web.FileViewController
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.api.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.domain.ParamsFile
import io.hizhengfu.cloud.sys.file.service.*
import io.hizhengfu.cloud.sys.file.service.storage.*
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.mock.web.*
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDate

class AcquisitionOriginalReadRuntimeTest {
    @TempDir lateinit var dir:Path
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement(proxyTargetClass=true) @EnableCaching class Transactions
    private class Runtime(val c:AnnotationConfigApplicationContext,val tx:TransactionTemplate,val root:Path) {
        val files=c.getBean(FileService::class.java)
        val storage=c.getBean(FileObjectStorageService::class.java)
        val access=c.getBean(FileReadAccess::class.java)
        val originals=c.getBean(AcquisitionOriginalFiles::class.java)
        val compliance=c.getBean(AcquisitionComplianceService::class.java)
        val acquisition=c.getBean(AcquisitionService::class.java)
        val archives=c.getBean(CollectionPermanentArchiveService::class.java)
        val controller=FileViewController(c,false,root.toString(),false,"unused-watermark",storage,access)
        fun <T> db(body:()->T):T=tx.execute {body()}
        fun read(path:String="proof.txt",original:Boolean=false,conditional:Boolean=true):MockHttpServletResponse {
            val request=MockHttpServletRequest("GET","/userfiles/${if(original)"original/" else ""}$path")
            if(conditional)request.addHeader("If-Modified-Since","Tue, 01 Sep 2026 10:00:00 GMT")
            val response=MockHttpServletResponse()
            if(original)controller.downloadOriginalFile(request,response)else controller.downloadFile(request,response)
            return response
        }
        fun allow(path:String="proof.txt") { val response=read(path);assertEquals("SECRET-VALUATION",response.contentAsString);assertEquals("private, no-store",response.getHeader("Cache-Control")) }
        fun deny(path:String="proof.txt") {
            for(original in listOf(false,true)) {
                val response=MockHttpServletResponse();val request=MockHttpServletRequest("GET","/userfiles/${if(original)"original/" else ""}$path")
                request.addHeader("Range","bytes=0-3");request.addHeader("If-Modified-Since","Tue, 01 Sep 2026 10:00:00 GMT")
                assertThrows(BadRequestException::class.java){if(original)controller.downloadOriginalFile(request,response)else controller.downloadFile(request,response)}
                assertEquals(0,response.contentAsByteArray.size);assertNotEquals(304,response.status)
            }
        }
        fun revoke()=db {RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.ACQUIRE}}
        fun snapshot()=db {listOf(Acquisitions,AcquisitionComplianceCases,AcquisitionExpertOpinions,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents).map {t->t.selectAll().map {r->t.columns.map {r[it]}}}}
    }
    private fun fixture(mode:String="single",backendDefault:Boolean=false,body:(Runtime)->Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply {url="jdbc:sqlite:${dir.resolve("acquisition-read.db")}"},DatabaseConfig {})
        AnnotationConfigApplicationContext().use {c->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("localStorage",LocalFileObjectStorage(dir.toString()))
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator",org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope",if(backendDefault)CollectionScopeResolver(mode,CollectionDefaultOrgSettings())else CollectionScopeResolver(mode,"museum"))
            c.beanFactory.registerSingleton("flow",mock(FlowInstanceService::class.java))
            c.registerBean(FileObjectStorageService::class.java,java.util.function.Supplier {FileObjectStorageService(c.getBean(LocalFileObjectStorage::class.java),mock(FileStorageConfigReader::class.java),mock(RustFsStorageClient::class.java),c.getBean(FileReadAccess::class.java))})
            c.registerBean(FileService::class.java,java.util.function.Supplier {FileService(dir.toString(),FileFolderService(),c.getBean(FileObjectStorageService::class.java),listOf(CollectionArchiveFileRetentionGuard()),c.getBean(FileReadAccess::class.java))})
            c.register(Transactions::class.java,CollectionActorDirectory::class.java,CollectionFileReadAuthorization::class.java,FileReadAccess::class.java,
                AcquisitionOriginalFiles::class.java,AcquisitionFileReadGuard::class.java,BusinessAttachmentBinder::class.java,
                CollectionPermanentArchiveService::class.java,CollectionRetainedLedgerService::class.java,
                AcquisitionComplianceService::class.java,AcquisitionService::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c,TransactionTemplate(manager),dir)
            r.db {
                originalReaderSchema();SchemaUtils.create(CollectionOrgs,Acquisitions,AcquisitionComplianceCases,AcquisitionExpertOpinions,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FileContents,FileUsers)
                for(id in listOf("museum","other"))CollectionOrgDao.new(id){name=id;code=id;systemGroupId=id}
                if(backendDefault) {
                    SchemaUtils.create(Configs)
                    CollectionDefaultOrgSettings.seed()
                    Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]="museum"}
                }
                originalReader("reader",CollectionConst.ACQUIRE)
                for(id in listOf("proof","neutral","public")) {
                    FileDao.new(id){name="$id.txt";path="$id.txt";hash=id;ext="txt";size=16;secure=false}
                    FileUsers.insert {it[fileId]=id;it[userId]="reader";it[uploadDate]=java.time.LocalDateTime.now()}
                }
                AcquisitionDao.new("acq"){orgId="museum";name="陶器";source=EnumSource.PURCHASE;proposedPrice=12345;appraisedValue=23456}
            }
            for(id in listOf("proof","neutral","public"))java.nio.file.Files.writeString(dir.resolve("$id.txt"),if(id=="proof")"SECRET-VALUATION" else "PUBLIC-CONTENT")
            val stale=SecurityUser(id="reader",name="旧登录",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=setOf(EnumRole.ADMIN.value),roleCodes=setOf(CollectionConst.ROLE_DIRECTOR),authorities=listOf("ADMIN",CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority),enabled=true)
            SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(stale,null,stale.authorities)
            try {body(r)}finally{SecurityContextHolder.clearContext()}
        }
    }
    private val date=LocalDate.of(2026,5,1)
    private fun retainedAcquisition(r:Runtime) = r.db {
        SchemaUtils.create(Collections,EntryVouchers,CollectionRetainedLedgers)
        AcquisitionDao["acq"].collectionId="moved-collection"
        AcquisitionDao.new("normal"){orgId="museum";name="正常征集单"}
        AcquisitionDao.new("foreign"){orgId="other";name="外馆征集单"}
        EntryVoucherDao.new("old-voucher"){orgId="museum";voucherNo="OLD-VOUCHER";acquisitionIds="acq";voucherStatus=EnumVoucherStatus.REGISTERED}
        val snapshot=CollectionRetainedLedgerSnapshot(
            CollectionVo(id="moved-collection",orgId="museum",name="原馆冻结瓷碗",totalRegNo="000011",entryVoucherNo="OLD-VOUCHER"),
            originalFileIds=listOf("proof"),
            identity=CollectionRetainedLedgerIdentity("museum","moved-collection","transfer","other"),
        )
        val retained=CollectionRetainedLedgerDao.new("retained") {
            orgId="museum";collectionId="moved-collection";transferId="transfer";targetOrgId="other"
            totalRegNo="000011";name="原馆冻结瓷碗";archiveId="";this.snapshot=snapshot
        }
        retained.archiveId=r.archives.seal("museum","COLLECTION_RETAINED_LEDGER","retained","TRANSFERRED",snapshot,"proof")
    }

    @Test fun `acquisition list isolates a replaced retained original without revealing its historical snapshot`()=fixture {r->
        retainedAcquisition(r)
        val before=r.db {r.snapshot()}
        val replacement=dir.resolve("copied-proof.txt")
        java.nio.file.Files.copy(dir.resolve("proof.txt"),replacement)
        java.nio.file.Files.move(replacement,dir.resolve("proof.txt"),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val page=r.acquisition.findPage(MockHttpServletRequest())
        val rows=page.list as List<AcquisitionVo>
        assertEquals(2L,page.total);assertEquals(setOf("acq","normal"),rows.map {it.id}.toSet())
        val affected=rows.single {it.id=="acq"}
        assertEquals("陶器",affected.name)
        assertTrue(affected.collectionEvidenceIssue!!.contains("文件身份无法核实"))
        assertNull(affected.collectionName);assertNull(affected.collectionTotalRegNo)
        assertNull(rows.single {it.id=="normal"}.collectionEvidenceIssue)
        assertThrows(BadRequestException::class.java){r.acquisition.get("acq")}
        assertThrows(BadRequestException::class.java){r.c.getBean(CollectionRetainedLedgerService::class.java).get("retained")}
        assertEquals(before,r.db {r.snapshot()})
        r.revoke()
        assertThrows(BadRequestException::class.java){r.acquisition.findPage(MockHttpServletRequest())}
    }

    @Test fun `historical number search retains an issue locator while damaged snapshot detail stays blocked`()=fixture {r->
        retainedAcquisition(r)
        java.nio.file.Files.writeString(dir.resolve("proof.txt"),"CHANGED-CONTENT")
        val rows=r.acquisition.findPage(MockHttpServletRequest(),keyword="000011").list as List<AcquisitionVo>
        val affected=rows.single()
        assertEquals("acq",affected.id);assertFalse(affected.collectionEvidenceIssue.isNullOrBlank())
        assertNull(affected.collectionName);assertNull(affected.collectionTotalRegNo)
        assertThrows(BadRequestException::class.java){r.acquisition.get("acq")}
    }

    @Test fun `verified original museum names still enrich acquisition lists and historical searches`()=fixture {r->
        retainedAcquisition(r)
        val affected=(r.acquisition.findPage(MockHttpServletRequest()).list as List<AcquisitionVo>).single {it.id=="acq"}
        assertEquals("原馆冻结瓷碗",affected.collectionName);assertEquals("000011",affected.collectionTotalRegNo)
        assertNull(affected.collectionEvidenceIssue)
        val found=(r.acquisition.findPage(MockHttpServletRequest(),keyword="000011").list as List<AcquisitionVo>).single()
        assertEquals("acq",found.id);assertEquals("000011",found.collectionTotalRegNo)
        assertEquals("原馆冻结瓷碗",r.acquisition.get("acq")!!.collectionName)
    }
    @Test fun `missing retained original reports the missing file while preserving its frozen baseline`()=fixture {r->
        retainedAcquisition(r)
        val before=r.snapshot()
        java.nio.file.Files.delete(dir.resolve("proof.txt"))
        val failure=assertThrows(BadRequestException::class.java){r.acquisition.get("acq")}
        assertTrue(failure.message!!.contains("原件文件缺失"));assertTrue(failure.message!!.contains("文件编号：proof"))
        val affected=(r.acquisition.findPage(MockHttpServletRequest()).list as List<AcquisitionVo>).single {it.id=="acq"}
        assertTrue(affected.collectionEvidenceIssue!!.contains("原件文件缺失"))
        assertNull(affected.collectionName);assertNull(affected.collectionTotalRegNo)
        assertEquals(before,r.snapshot())
    }
    private fun dossier()=AcquisitionDossierVo(EnumCollectionNature.RELIC,date,"neutral","neutral","neutral",true,"neutral",setOf("seller"),"neutral")
    private fun completion()=AcquisitionCompletionVo(date.plusDays(10),"neutral","neutral",date.plusDays(11),date.plusDays(12),"neutral","实际公布管理使用情况",comparablePriceFileIds="neutral",suggestedPriceCents=10000,valuationReason="真实比较依据",highValueAssessmentRequired=false,highValueDeterminationFileIds="neutral",negotiatedPriceCents=9000,negotiatedOn=date.plusDays(10),negotiationFileIds="neutral",paymentFileIds="neutral")
    private fun expert(n:Int=1)=AcquisitionExpertOpinionVo(expertKey="expert$n",expertName="专家$n",organization="机构$n",professionalTitle="研究馆员",specialty="陶器",qualificationFileIds="neutral",independenceFileIds="neutral",authenticityOpinion="真",valueOpinion="有研究价值",provenanceOpinion="来源已核实",proposedPriceCents=10000,agree=true,signedOpinionFileIds="neutral",assessedOn=date.plusDays(5))
    private inline fun <reified T:Any> field(value:T,key:String):T {
        val tree=JsonUtils.mapper.readTree(JsonUtils.toJson(value)) as tools.jackson.databind.node.ObjectNode
        tree.put(key,"proof");return JsonUtils.mapper.treeToValue(tree,T::class.java)
    }
    private fun place(r:Runtime,source:String)=r.db {
        when {
            source.startsWith("dossier:")->AcquisitionComplianceCaseDao.new{orgId="museum";acquisitionId="acq";subject="legacy";dossier=field(this@AcquisitionOriginalReadRuntimeTest.dossier(),source.substringAfter(':'))}
            source.startsWith("completion:")->AcquisitionComplianceCaseDao.new{orgId="museum";acquisitionId="acq";subject="legacy";dossier=this@AcquisitionOriginalReadRuntimeTest.dossier();completion=field(this@AcquisitionOriginalReadRuntimeTest.completion(),source.substringAfter(':'))}
            source.startsWith("expert:")->AcquisitionExpertOpinionDao.new{orgId="museum";acquisitionId="acq";expertKey="original";content=field(expert(),source.substringAfter(':'))}
            source=="attachment"->FileContents.insert {it[fileId]="proof";it[contentId]="acq"}
            source=="archive"->r.archives.seal("museum",CollectionConst.BIZ_ACQUISITION,"acq","LEGACY",mapOf("source" to "actual-old-original"),"proof")
            else -> AcquisitionDao["acq"].apply {when(source){"images"->images="proof";"expertOpinionAttachmentIds"->expertOpinionAttachmentIds="proof";"approvalAttachmentIds"->approvalAttachmentIds="proof";"contractAttachmentIds"->contractAttachmentIds="proof";else->invoiceAttachmentIds="proof"}}
        }
    }
    @ParameterizedTest @ValueSource(strings=["images","expertOpinionAttachmentIds","approvalAttachmentIds","contractAttachmentIds","invoiceAttachmentIds",
        "dossier:standardsFileIds","dossier:authenticityValueFileIds","dossier:sourceInvestigationFileIds","dossier:conflictDisclosureFileIds","dossier:intentionFileIds",
        "completion:collectiveDecisionFileIds","completion:agreementFileIds","completion:handoverReceiptFileIds","completion:comparablePriceFileIds","completion:highValueDeterminationFileIds","completion:assetAssessmentFileIds","completion:negotiationFileIds","completion:paymentFileIds",
        "expert:qualificationFileIds","expert:independenceFileIds","expert:signedOpinionFileIds","attachment","archive"])
    fun `all current and legacy original fields protect actual file bytes without a new index`(source:String)=fixture {r->
        place(r,source);r.allow();val before=r.snapshot();r.revoke();r.deny();assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["symlink","hardlink","rename"])
    fun `batch metadata preserves native aliases and current confidential authority`(kind:String)=fixture {r->
        place(r,"invoiceAttachmentIds");r.originals.remember("acq")
        val alias=dir.resolve("alias.txt")
        when(kind) {
            "symlink"->java.nio.file.Files.createSymbolicLink(alias,dir.resolve("proof.txt"))
            "hardlink"->java.nio.file.Files.createLink(alias,dir.resolve("proof.txt"))
            else->{java.nio.file.Files.move(dir.resolve("proof.txt"),alias);r.db {FileDao["proof"].path="alias.txt"}}
        }
        val original=FileMetadataLocation("alias.txt","LOCAL")
        val public=FileMetadataLocation("public.txt","LOCAL")
        val before=r.snapshot()
        val readable=r.access.requireMetadataReadableMany(listOf(original,public))
        assertTrue(readable.getValue(original).getOrThrow())
        assertFalse(readable.getValue(public).getOrThrow())
        r.revoke()
        val denied=r.access.requireMetadataReadableMany(listOf(original,public))
        assertThrows(BadRequestException::class.java) {denied.getValue(original).getOrThrow()}
        assertFalse(denied.getValue(public).getOrThrow())
        assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["ownership","metadata"])
    fun `batch metadata retains per source rejection and corrupt index protection`(kind:String)=fixture {r->
        place(r,"archive")
        r.db {AcquisitionDao["acq"].images="neutral"}
        val proof=FileMetadataLocation("proof.txt","LOCAL")
        val neutral=FileMetadataLocation("neutral.txt","LOCAL")
        val public=FileMetadataLocation("public.txt","LOCAL")
        r.db {
            val reference=CollectionPermanentArchiveFileDao.all().single()
            if(kind=="ownership")reference.orgId="other" else reference.metadata="{invalid"
        }
        val before=r.snapshot()
        val results=r.access.requireMetadataReadableMany(listOf(proof,neutral,public))
        assertThrows(BadRequestException::class.java) {results.getValue(proof).getOrThrow()}
        if(kind=="metadata") {
            assertThrows(BadRequestException::class.java) {results.getValue(neutral).getOrThrow()}
            assertThrows(BadRequestException::class.java) {results.getValue(public).getOrThrow()}
        } else {
            assertTrue(results.getValue(neutral).getOrThrow())
            assertFalse(results.getValue(public).getOrThrow())
        }
        assertEquals(before,r.snapshot())
    }
    @ParameterizedTest @ValueSource(strings=["anonymous","disabled","removed-role","moved","cross-only","general-reader"])
    fun `current identity is required despite a cached administrator login`(kind:String)=fixture {r->
        place(r,"invoiceAttachmentIds");r.allow()
        when(kind) {
            "anonymous"->SecurityContextHolder.clearContext()
            "disabled"->r.db {UserDao["reader"].enabled=false}
            "removed-role"->r.db {RoleUsers.deleteAll()}
            "moved"->r.db {UserDao["reader"].groupId="other"}
            else->{r.revoke();r.db {actorRole("reader","EXTRA",setOf(if(kind=="cross-only")CollectionConst.CROSS_ORG else CollectionConst.SELECT))}}
        }
        r.deny();assertEquals("PUBLIC-CONTENT",r.read("public.txt",conditional=false).contentAsString)
    }
    @Test fun `confidential original can be read with a dedicated read only permission`()=fixture { r ->
        place(r,"invoiceAttachmentIds")
        r.revoke()
        r.deny()
        r.db { actorRole("reader","CONFIDENTIAL_READER",setOf(CollectionConst.SELECT,CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT)) }
        r.allow()
        assertTrue(r.db { AcquisitionConfidentialAccess.canRead(r.c.getBean(CollectionScopeResolver::class.java),"museum") })
        assertEquals(12345L,r.acquisition.get("acq")!!.proposedPrice)
    }
    @ParameterizedTest @ValueSource(strings=["single:admin","single:cross-acquire","single:cross-confidential","single:cross-only",
        "multi:admin","multi:cross-acquire","multi:cross-confidential","multi:cross-only"])
    fun `foreign confidential entries respect backend default museum independently of current cross permission`(kind:String)=fixture(mode=kind.substringBefore(':'),backendDefault=true) {r->
        val actor=kind.substringAfter(':')
        val fileId="2102704108634521999"
        java.nio.file.Files.writeString(dir.resolve("foreign.txt"),"FOREIGN-SECRET")
        val archiveId=r.db {
            // 在该馆合法的单馆配置与征集资格下归档，再切回被测默认馆及当前身份。
            RoleUsers.deleteAll()
            actorRole("reader","ARRANGE_FOREIGN",setOf(CollectionConst.ACQUIRE,CollectionConst.SELECT,CollectionConst.CROSS_ORG))
            Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]="other"}
            FileDao.new(fileId){name="foreign.txt";path="foreign.txt";hash="foreign";ext="txt";size=14;secure=false}
            AcquisitionDao.new("foreign-acq"){orgId="other";name="外馆陶器";source=EnumSource.PURCHASE;proposedPrice=98765;appraisedValue=87654;invoiceAttachmentIds=fileId}
            val sealed=r.archives.seal("other",CollectionConst.BIZ_ACQUISITION,"foreign-acq","AGREEMENT",mapOf("negotiatedPrice" to 98765),fileId)
            Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]="museum"}
            RoleUsers.deleteAll()
            if(actor=="admin") {
                Roles.insert {it[id]=EnumRole.ADMIN.value;it[code]="ADMIN";it[name]="实际管理员"}
                RoleUsers.insert {it[userId]="reader";it[roleId]=EnumRole.ADMIN.value}
            } else {
                val duty=when(actor) {"cross-acquire"->setOf(CollectionConst.ACQUIRE);"cross-confidential"->setOf(CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT);else->emptySet()}
                actorRole("reader","CURRENT_CROSS",duty+setOf(CollectionConst.SELECT,CollectionConst.CROSS_ORG))
            }
            sealed
        }
        val allowed=kind.startsWith("multi:") && actor!="cross-only"
        val before=r.snapshot()
        assertAll(
            {if(allowed)assertEquals(98765L,(r.compliance.detail("foreign-acq")["acquisition"] as AcquisitionVo).proposedPrice)
                else assertThrows(BadRequestException::class.java){r.compliance.detail("foreign-acq")}},
            {if(allowed)assertDoesNotThrow {r.compliance.requireArchivedOriginal("foreign-acq",archiveId,fileId)}
                else assertThrows(BadRequestException::class.java){r.compliance.requireArchivedOriginal("foreign-acq",archiveId,fileId)}},
            {if(allowed)assertEquals(98765L,(r.acquisition.getFlowInfo("foreign-acq")!!["formData"] as Map<*,*>)["proposedPrice"])
                else assertThrows(BadRequestException::class.java){r.acquisition.getFlowInfo("foreign-acq")}},
            {val form=r.db {r.acquisition.buildFormData(AcquisitionDao["foreign-acq"])}
                if(allowed)assertEquals(98765L,form["proposedPrice"])else assertEquals(setOf("name","source"),form.keys)},
            {if(kind.startsWith("single:"))assertThrows(BadRequestException::class.java){r.acquisition.get("foreign-acq")}
                else assertEquals(if(allowed)98765L else null,r.acquisition.get("foreign-acq")!!.proposedPrice)},
            {if(allowed)for(original in listOf(false,true))assertEquals("FOREIGN-SECRET",r.read("foreign.txt",original,conditional=false).contentAsString)
                else r.deny("foreign.txt")},
        )
        assertEquals(before,r.snapshot())
    }
    @Test fun `confidential readers immediately follow the backend default museum after it changes`()=fixture(backendDefault=true) {r->
        r.db {
            actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
            AcquisitionDao.new("foreign-acq"){orgId="other";name="外馆陶器";source=EnumSource.PURCHASE;proposedPrice=98765}
        }
        assertEquals(12345L,(r.compliance.detail("acq")["acquisition"] as AcquisitionVo).proposedPrice)
        assertThrows(BadRequestException::class.java){r.compliance.detail("foreign-acq")}
        r.db {Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]="other"}}
        assertEquals(98765L,(r.compliance.detail("foreign-acq")["acquisition"] as AcquisitionVo).proposedPrice)
        assertThrows(BadRequestException::class.java){r.compliance.detail("acq")}
        assertThrows(BadRequestException::class.java){r.acquisition.getFlowInfo("acq")}
        assertEquals(setOf("name","source"),r.db {r.acquisition.buildFormData(AcquisitionDao["acq"])}.keys)
        r.revoke()
        assertThrows(BadRequestException::class.java){r.compliance.detail("foreign-acq")}
        assertEquals(setOf("name","source"),r.db {r.acquisition.buildFormData(AcquisitionDao["foreign-acq"])}.keys)
    }
    @ParameterizedTest @ValueSource(strings=["disabled-setting","blank-ambiguous","missing-museum","disabled-museum"])
    fun `invalid backend default museum fails before exposing confidential details`(kind:String)=fixture(backendDefault=true) {r->
        r.db {
            actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))
            when(kind) {
                "disabled-setting"->Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[enable]=false}
                "blank-ambiguous"->Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]=""}
                "missing-museum"->Configs.update({Configs.name eq CollectionDefaultOrgSettings.KEY}) {it[content]="missing"}
                else->CollectionOrgDao["museum"].orgStatus=EnumOrgStatus.DISABLED
            }
        }
        val before=r.snapshot()
        assertAll(
            {assertThrows(BadRequestException::class.java){r.compliance.detail("acq")}},
            {assertThrows(BadRequestException::class.java){r.acquisition.getFlowInfo("acq")}},
            {assertThrows(BadRequestException::class.java){r.acquisition.get("acq")}},
            {assertThrows(BadRequestException::class.java){r.db {r.acquisition.buildFormData(AcquisitionDao["acq"])}}},
        )
        assertEquals(before,r.snapshot())
    }
    @Test fun `metadata preview temporary reads and zip remain protected after cache population`()=fixture {r->
        place(r,"attachment")
        val front=FileFrontController(mock(FileSystemStorageService::class.java),r.files)
        fun listing()=front.findByContent("acq",ParamsFile(),MockHttpServletRequest())
        listing();listing();r.revoke()
        assertThrows(BadRequestException::class.java){listing()}
        val converter=mock(FileConvertService::class.java)
        val response=MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){FilePreviewController(r.files,converter,r.storage,r.access).previewDocument("proof","pdf",response)}
        verifyNoInteractions(converter);assertEquals(0,response.contentAsByteArray.size)
        val zipped=MockHttpServletResponse();assertThrows(BadRequestException::class.java){r.files.zipContentFile("acq",zipped)};assertEquals(0,zipped.contentAsByteArray.size)
        assertThrows(BadRequestException::class.java){r.storage.withTempFile("proof.txt","LOCAL",".txt"){fail("must not open")}}
    }
    @Test fun `confidential detail main record and flow projection use current museum and permissions`()=fixture {r->
        assertTrue(r.compliance.detail("acq")["acquisition"]!=null)
        assertTrue(r.acquisition.get("acq")!!.proposedPrice==12345L)
        r.revoke()
        assertThrows(BadRequestException::class.java){r.compliance.detail("acq")}
        assertThrows(BadRequestException::class.java){r.acquisition.get("acq")}
        r.db {actorRole("reader","GENERAL_READER",setOf(CollectionConst.SELECT))}
        assertTrue(r.acquisition.get("acq")!!.proposedPrice==null)
        assertEquals(setOf("name","source"),r.db {r.acquisition.buildFormData(AcquisitionDao["acq"])}.keys)
        r.db {actorRole("reader","CROSS_ACQUIRE",setOf(CollectionConst.ACQUIRE,CollectionConst.CROSS_ORG));UserDao["reader"].groupId="other"}
        assertTrue(r.acquisition.get("acq")!!.proposedPrice==12345L)
    }
    @ParameterizedTest @ValueSource(strings=["symlink","hardlink","rename"])
    fun `saved draft identities protect old targets after path changes and material replacement`(kind:String)=fixture {r->
        val original="proof.txt";val alias="original-alias.txt"
        if(kind=="symlink")java.nio.file.Files.createSymbolicLink(dir.resolve(alias),Path.of(original))
        else if(kind=="hardlink")java.nio.file.Files.createLink(dir.resolve(alias),dir.resolve(original))
        r.db {FileDao["proof"].path=if(kind=="rename")original else alias}
        r.acquisition.save(AcquisitionVo(id="acq",name="陶器",source=EnumSource.PURCHASE,images="proof"))
        val count=r.db {CollectionPermanentArchiveDao.count()};assertTrue(count>0)
        r.originals.remember("acq");assertEquals(count,r.db {CollectionPermanentArchiveDao.count()})
        if(kind!="rename")java.nio.file.Files.delete(dir.resolve(alias))
        java.nio.file.Files.writeString(dir.resolve("new.txt"),"SECRET-VALUATION")
        r.db {FileDao["proof"].path="new.txt"}
        r.acquisition.save(AcquisitionVo(id="acq",name="陶器",source=EnumSource.PURCHASE,images="neutral"))
        r.revoke();r.deny(original);r.deny("new.txt")
    }
    @Test fun `dossier expert and completion registration retain identity without asserting business completion`()=fixture {r->
        r.compliance.saveDossier("acq",dossier().copy(intentionFileIds="proof"))
        assertTrue(r.db {CollectionPermanentArchiveDao.all().any {it.phase.startsWith(AcquisitionOriginalFiles.IDENTITY_PHASE)}})
        assertTrue(r.db {AcquisitionComplianceCaseDao.all().single().completion==null})
        for(n in 1..3)r.compliance.recordExpert("acq",expert(n))
        r.compliance.complete("acq",completion().copy(negotiationFileIds="proof"))
        r.allow();r.revoke();r.deny()
    }
    @Test fun `failed material replacement rolls back both current state and original bindings`()=fixture {r->
        r.acquisition.save(AcquisitionVo(id="acq",name="陶器",source=EnumSource.PURCHASE,images="proof"))
        val before=r.snapshot()
        assertThrows(BadRequestException::class.java){r.acquisition.save(AcquisitionVo(id="acq",name="陶器",source=EnumSource.PURCHASE,images="neutral,missing"))}
        assertEquals(before,r.snapshot());r.revoke();r.deny()
    }
    @Test fun `retained original location join observes uncommitted file path and storage updates`()=fixture {r->
        place(r,"invoice")
        r.db {r.originals.remember("acq");AcquisitionDao["acq"].invoiceAttachmentIds="neutral"}
        r.db {
            val original=FileDao["proof"]
            original.path="rustfs/renamed-original.txt"
            original.storageType=EnumFileStorageType.RUSTFS
            assertEquals(setOf("museum"),r.originals.organizations(original.path,"RUSTFS"))
        }
        r.revoke()
        r.deny("proof.txt")
    }

    @Test fun `one original shared by two museums requires actual business and cross museum access`()=fixture(mode="multi") {r->
        place(r,"images");r.db {AcquisitionDao.new("other-acq"){orgId="other";name="另馆";images="proof"}}
        r.deny();r.db {actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))};r.allow()
        r.revoke();r.deny()
    }
    @ParameterizedTest @ValueSource(strings=["identity", "metadata", "ownership"])
    fun `corrupt frozen identity cannot silently release an old protected original`(kind:String)=fixture {r->
        r.acquisition.save(AcquisitionVo(id="acq",name="陶器",source=EnumSource.PURCHASE,images="proof"))
        r.db {
            val a=CollectionPermanentArchiveDao.all().first {it.phase.startsWith(AcquisitionOriginalFiles.IDENTITY_PHASE)}
            val f=CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq a.id.value}.single()
            when(kind) {
                "identity"->a.snapshot="{}"
                "metadata"->f.metadata="{}"
                else->f.orgId="other"
            }
        }
        val before=r.snapshot();r.revoke();r.deny();assertEquals(before,r.snapshot())
    }

    @Test fun `printing rejects a currently foreign reader before formatting confidential fields`()=fixture {r->
        val template=mock(CollectionPrintTemplateService::class.java)
        val print=CollectionPrintService(mock(CollectionService::class.java),r.c.getBean(CollectionScopeResolver::class.java),template,mock(LoanStatutoryService::class.java))
        r.db {UserDao["reader"].groupId="other"}
        assertThrows(BadRequestException::class.java){r.db {print.acquisitionPrint("acq")}}
        verifyNoInteractions(template)
    }

    @ParameterizedTest @ValueSource(strings=["docx:pdf","docx:html","xlsx:html","pptx:pdf","pdf:pdf"])
    fun `actual Office previews ignore old public cache and recheck current acquisition qualification`(kind:String)=fixture {r->
        val ext=kind.substringBefore(':');val format=kind.substringAfter(':')
        val marker="DEV-SYNTHETIC-SECRET-VALUATION"
        val source=dir.resolve("office.$ext")
        java.nio.file.Files.newOutputStream(source).use {out->when(ext) {
            "docx"->org.apache.poi.xwpf.usermodel.XWPFDocument().use {doc->doc.createParagraph().createRun().setText(marker);doc.write(out)}
            "xlsx"->org.apache.poi.xssf.usermodel.XSSFWorkbook().use {book->book.createSheet("synthetic").createRow(0).createCell(0).setCellValue(marker);book.write(out)}
            "pptx"->org.apache.poi.xslf.usermodel.XMLSlideShow().use {slides->slides.createSlide().createTextBox().apply {text=marker;anchor=java.awt.Rectangle(20,20,640,120)};slides.write(out)}
            else->org.apache.pdfbox.pdmodel.PDDocument().use {pdf->
                val page=org.apache.pdfbox.pdmodel.PDPage();pdf.addPage(page)
                org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page).use {content->content.beginText();content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12f);content.newLineAtOffset(30f,700f);content.showText(marker);content.endText()};pdf.save(out)
            }
        }}
        val fid="2102704108634521999"
        r.db {FileDao.new(fid){name="office.$ext";path="office.$ext";this.ext=ext;hash="office";size=java.nio.file.Files.size(source);secure=false}}
        val delegate:io.hizhengfu.cloud.sys.file.converter.FileConverter?=when(ext) {
            "docx"->io.hizhengfu.cloud.sys.file.converter.WordFileConverter()
            "xlsx"->io.hizhengfu.cloud.sys.file.converter.ExcelFileConverter()
            "pptx"->io.hizhengfu.cloud.sys.file.converter.PptxFileConverter()
            else->null
        }
        var revokeAfterConversion=false
        val converters=delegate?.let {actual->listOf(object:io.hizhengfu.cloud.sys.file.converter.FileConverter {
            override fun supportedExtensions()=actual.supportedExtensions()
            override fun defaultOutputFormat()=actual.defaultOutputFormat()
            override fun convert(input:java.io.InputStream,format:String,output:java.io.OutputStream) {
                actual.convert(input,format,output)
                if(revokeAfterConversion)r.db {RolePermissions.deleteWhere {RolePermissions.permissionId eq CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT}}
            }
        })}.orEmpty()
        val convert=FileConvertService(r.files,converters)
        val preview=FilePreviewController(r.files,convert,r.storage,r.access)
        fun read()=MockHttpServletResponse().also {preview.previewDocument(fid,format,it)}
        fun verifyOutput(response:MockHttpServletResponse) {
            assertEquals(200,response.status)
            if(format=="html")assertTrue(response.contentAsString.contains(marker))
            else org.apache.pdfbox.Loader.loadPDF(response.contentAsByteArray).use {pdf->
                assertEquals(1,pdf.numberOfPages)
                if(ext=="pptx")assertTrue(pdf.getPage(0).resources.xObjectNames.any())
                else assertTrue(org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains(marker))
            }
        }
        verifyOutput(read())
        if(delegate!=null)assertTrue(convert.isPreviewCached(fid,format))
        val cache=dir.resolve("preview-cache/${"FILE_PREVIEW_${fid}_${format}".md5()}.$format")
        val cached=if(java.nio.file.Files.exists(cache))java.nio.file.Files.readAllBytes(cache)else null
        r.db {AcquisitionDao["acq"].invoiceAttachmentIds=fid;r.originals.remember("acq")}
        val before=r.snapshot()
        val protected=read();verifyOutput(protected);assertEquals("private, no-store",protected.getHeader("Cache-Control"))
        assertEquals(false,preview.previewCached(fid,format)["cached"])
        if(delegate!=null)for(original in listOf(false,true))assertThrows(BadRequestException::class.java){r.read("preview-cache/${cache.fileName}",original)}
        r.revoke()
        val denied=MockHttpServletResponse();assertThrows(BadRequestException::class.java){preview.previewDocument(fid,format,denied)};assertEquals(0,denied.contentAsByteArray.size)
        assertEquals(false,preview.previewCached(fid,format)["supported"])
        r.db {actorRole("reader","CURRENT_READONLY",setOf(CollectionConst.ACQUIRE_CONFIDENTIAL_SELECT))}
        verifyOutput(read())
        if(delegate!=null) {
            revokeAfterConversion=true
            val late=read();assertEquals(400,late.status);assertEquals(0,late.contentAsByteArray.size)
        }
        if(cached!=null)assertArrayEquals(cached,java.nio.file.Files.readAllBytes(cache))
        assertEquals(before,r.snapshot())
    }

}
