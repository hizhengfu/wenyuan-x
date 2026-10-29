package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.EnumFlowStatus
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileObjectStorage
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.flow.dao.*
import io.hizhengfu.cloud.flow.domain.*
import io.hizhengfu.cloud.flow.service.FlowInstanceService
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.dao.Files
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.security.MessageDigest
import java.time.LocalDateTime

class DigitalAcquisitionPersistenceTest {
    private val at=LocalDateTime.of(2026,9,1,10,0)
    private val scope=CollectionScopeResolver("single","museum")
    private val users=mock(CollectionResponsibleUserService::class.java)
    private val binder=mock(BusinessAttachmentBinder::class.java).also { `when`(it.resolveSingle(anyString())).thenAnswer { c->c.getArgument<String>(0) } }
    private val archives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider())
    private val originals=mock(CollectionOriginalAccessService::class.java)
    private val locks=CollectionOperationLockService()
    private val storage=mock(FileObjectStorage::class.java)
    private val blobs=mutableMapOf("raw" to "RAW".toByteArray(),"processed" to "PROCESS".toByteArray(),"organized" to "ORG".toByteArray(),"stored" to "STORED".toByteArray())
    private val flow=mock(FlowInstanceService::class.java)
    private val context=StaticApplicationContext()
    private var service=DigitalAcquisitionService(scope,users,archives,originals,locks,storage)
    private lateinit var reproduction:ReproductionService
    @BeforeEach fun setup() {
        context.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager());context.refresh();SpringContextHolder().setApplicationContext(context)
        val physical=ReproductionComplianceService(scope,users,archives,context.getBeanProvider(CollectionConservationQualificationPort::class.java),locks,originals)
        reproduction=ReproductionService(flow,scope,binder,locks,physical,service)
        val user=SecurityUser(id="operator",name="经办",username="operator",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("ADMIN")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        `when`(storage.openStream(anyString(),anyString(),isNull(),isNull())).thenAnswer { c->blobs[c.getArgument<String>(0)]!!.inputStream() }
    }
    @AfterEach fun clear(){SecurityContextHolder.clearContext();context.close()}
    private fun fixture(block:()->Unit) {
        val db=Database.connect("jdbc:sqlite:file:digital-${System.nanoTime()}?mode=memory&cache=shared",driver="org.sqlite.JDBC")
        transaction(db) {
            seedFixture()
            block()
        }
    }
    private fun seedFixture() {
            SchemaUtils.create(CollectionOrgs,Collections,Reproductions,DigitalAcquisitionPlans,DigitalAcquisitionResources,DigitalAcquisitionFiles,DigitalAcquisitionFileAccesses,ReproductionPlans,CollectionPermanentArchives,CollectionPermanentArchiveFiles,Files,FlowInstances,FlowInstanceLogs,CollectionOperationLocks)
            CollectionOrgDao.new("museum"){name="本馆";code="M"}
            originalReaderSchema();originalReader("operator",CollectionConst.STORAGE)
            CollectionDao.new("piece"){orgId="museum";name="藏品";totalRegNo="M1";registeredAt=at.minusMonths(1);catalogStatus=EnumCatalogStatus.APPROVED;nature=EnumCollectionNature.RELIC;level=EnumRelicLevel.FIRST;lifeStatus=EnumCollectionStatus.IN_STORE}
            ReproductionDao.new("rep"){orgId="museum";collectionId="piece";repType=EnumRepType.SCAN_3D;purpose="数字化保护";repDate=at.toLocalDate()}
            FileDao.new("proof"){name="实际原件.pdf";path="proof";hash="proof";size=100;ext="pdf"}
            blobs.forEach { (key,value)->FileDao.new(key){name="$key.bin";path=key;hash="untrusted-upload-hash-$key";size=value.size.toLong();ext="bin"} }
    }
    private fun plan()=DigitalAcquisitionPlanVo(service.detail("rep")["sourceRevision"] as String,at,"it-department","information","keeper","三维扫描","数字采集室",at.plusMinutes(5),at.plusHours(1),"核实光照、温湿度及暴露时间","支撑及搬运防护","加工标准","命名、分类与元数据规则","主存储、独立备份和恢复验证","访问授权、保留与安全检查","权利及保密核查","proof","proof","proof","proof","proof")
    private fun approve(p:String,keeper:Boolean=true) {
        val r=ReproductionDao["rep"];service.submit(r);r.flowInstanceId="flow-rep";r.flowStatus=EnumFlowStatus.AUDIT
        listOf("information","keeper").filter { keeper || it!="keeper" }.forEachIndexed { i,node->FlowInstanceLogDao.new {instanceId="flow-rep";nodeKey=node;taskId="task-$node";action="APPROVE";operator=node;comment="本人核对并共同确认操作与安全方案";createTime=at.plusMinutes(1+i.toLong())} }
        reproduction.onFlowCompleted(r,FlowCompletedEvent("flow-rep",CollectionConst.FLOW_DIGITAL_ACQUISITION_2026,"数字采集","rep",CollectionConst.BIZ_REPRODUCTION,emptyMap(),"operator","经办"))
    }
    private fun start(p:String)=DigitalAcquisitionStartVo(p,at.plusMinutes(10),"operator","req",null,true,true,"落实支撑、光照及人员防护","proof","proof")
    private fun capture()=DigitalCaptureVo(at.plusMinutes(45),"operator","实际设备、精度、曝光及采集参数",true,"原件状态核查无差异","proof",setOf("raw"),"proof")
    private fun ready():String {val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p));service.capture("rep",capture());return p}
    private fun raw()=DigitalAcquisitionResourceDao.all().single { it.content.stage==EnumDigitalResourceStage.RAW }.id.value
    private fun stage(stage:EnumDigitalResourceStage,parent:String,file:String,hour:Long)=DigitalResourceStageVo(stage,at.plusHours(hour),"operator",setOf(parent),setOf(file),"实际完成本阶段操作","实际格式、精度、单位和质量参数","proof",if(stage==EnumDigitalResourceStage.STORED)"主存储" else null,if(stage==EnumDigitalResourceStage.STORED)"独立备份" else null,if(stage==EnumDigitalResourceStage.STORED)at.plusMinutes(150) else null,if(stage==EnumDigitalResourceStage.STORED)true else null,if(stage==EnumDigitalResourceStage.STORED)"proof" else null)
    private fun stored():String {val a=service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1));val b=service.resource("rep",stage(EnumDigitalResourceStage.ORGANIZED,a,"organized",2));return service.resource("rep",stage(EnumDigitalResourceStage.STORED,b,"stored",3))}
    private fun completion(id:String)=DigitalAcquisitionCompletionVo(at.plusHours(4),"information","keeper",setOf(id),"共同核查完整来源链、实际成果质量、恢复和授权安全","proof")

    @org.junit.jupiter.api.io.TempDir lateinit var originalsDir:java.nio.file.Path
    @Test fun `new business archive indexes its actual local object and retains the requested alias`()=fixture {
        java.nio.file.Files.writeString(originalsDir.resolve("proof"),"retained-original-bytes")
        java.nio.file.Files.createSymbolicLink(originalsDir.resolve("old-alias"),java.nio.file.Path.of("proof"))
        FileDao["proof"].path="old-alias"
        val local=io.hizhengfu.cloud.common.file.LocalFileObjectStorage(originalsDir.toString())
        val realArchives=CollectionPermanentArchiveService(binder,scope,local,readAccess=unprotectedArchiveReadAccessProvider())
        val realService=DigitalAcquisitionService(scope,users,realArchives,originals,locks,storage)
        realService.savePlan("rep",plan())
        val keys=DigitalAcquisitionFileAccessDao.all().map {it.pathKey}.toSet()
        assertTrue(keys.contains(io.hizhengfu.cloud.common.file.FileReadPath.key("old-alias","LOCAL")))
        assertTrue(keys.contains(io.hizhengfu.cloud.common.file.FileReadPath.key("proof","LOCAL")))
        java.nio.file.Files.createLink(originalsDir.resolve("later-link"),originalsDir.resolve("proof"))
        val identity=io.hizhengfu.cloud.common.file.FileReadPath.keys("later-link","LOCAL",local).single {it.startsWith("n")}
        assertTrue(identity in keys)
        assertTrue(CollectionPermanentArchiveFileDao.all().all {it.metadata.contains("old-alias")})
        java.nio.file.Files.delete(originalsDir.resolve("proof"))
        assertThrows(BadRequestException::class.java){realArchives.fileReadKeys(FileDao["proof"])}
    }
    @Test fun `digital plan requires actual joint departments complete operations and frozen source`()=fixture {
        assertThrows(BadRequestException::class.java){service.savePlan("rep",plan().copy(informationUserId="keeper"))}
        assertThrows(BadRequestException::class.java){service.savePlan("rep",plan().copy(resourceSecurityPlan=""))}
        val p=service.savePlan("rep",plan());assertEquals(p,service.savePlan("rep",plan()))
        verify(users,atLeastOnce()).requireDepartmentMember("information","museum","it-department")
        verify(users,atLeastOnce()).requireRole("information","museum",CollectionConst.ROLE_INFORMATION)
        CollectionDao["piece"].name="修改后的名称"
        assertThrows(BadRequestException::class.java){service.prepared(ReproductionDao["rep"])}
    }
    @Test fun `legacy PASS and storage only approval cannot authorize capture`()=fixture {
        val p=service.savePlan("rep",plan());ReproductionDao["rep"].flowStatus=EnumFlowStatus.PASS
        assertThrows(BadRequestException::class.java){service.start("rep",start(p))}
        ReproductionDao["rep"].flowStatus=EnumFlowStatus.DRAFT
        assertThrows(BadRequestException::class.java){approve(p,false)}
        assertNull(DigitalAcquisitionPlanDao[p].started)
    }
    @Test fun `actual capture holds original access lock and then preserves immutable raw data`()=fixture {
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        verify(originals,atLeastOnce()).requireAccess(ReproductionDao["rep"],at.plusMinutes(10),"operator","req",null)
        assertThrows(BadRequestException::class.java){locks.requireUnlocked("piece")}
        assertThrows(BadRequestException::class.java){reproduction.cancel("rep")}
        assertThrows(BadRequestException::class.java){reproduction.save(ReproductionDao["rep"].toModel().copy(repType=EnumRepType.COPY))}
        service.capture("rep",capture());service.capture("rep",capture())
        assertDoesNotThrow{locks.requireUnlocked("piece")}
        assertThrows(BadRequestException::class.java){service.capture("rep",capture().copy(acquisitionParameters="改写原始记录"))}
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("raw")}
    }
    @Test fun `actual byte SHA256 is computed independently of upload digest`()=fixture {
        ready();val f=DigitalAcquisitionFileDao.all().single().snapshot
        val expected=MessageDigest.getInstance("SHA-256").digest(blobs["raw"]!!).joinToString(""){"%02x".format(it)}
        assertEquals(expected,f.sha256);assertEquals(3L,f.size);assertNotEquals(FileDao["raw"].hash,f.sha256)
    }
    @Test fun `wrong byte size cannot be recorded as verified capture`()=fixture {
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p));FileDao["raw"].size=99
        assertThrows(BadRequestException::class.java){service.capture("rep",capture())}
        assertNull(DigitalAcquisitionPlanDao[p].captured);assertEquals(0L,DigitalAcquisitionResourceDao.count())
    }
    @Test fun `processing organizing storage require actual complete ordered lineage`()=fixture {
        ready()
        assertThrows(BadRequestException::class.java){service.resource("rep",stage(EnumDigitalResourceStage.STORED,raw(),"stored",3))}
        assertThrows(BadRequestException::class.java){service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,"other-plan","processed",1))}
        val id=stored();service.complete("rep",completion(id));service.complete("rep",completion(id))
        assertThrows(BadRequestException::class.java){service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",5))}
    }
    @Test fun `backup restoration and actual access verification are mandatory before stored status`()=fixture {
        ready();val a=service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1));val b=service.resource("rep",stage(EnumDigitalResourceStage.ORGANIZED,a,"organized",2));val valid=stage(EnumDigitalResourceStage.STORED,b,"stored",3)
        for(invalid in listOf(valid.copy(backupLocation="主存储"),valid.copy(restorationVerifiedAt=null),valid.copy(accessControlsVerified=false),valid.copy(securityCheckFileIds="")))assertThrows(BadRequestException::class.java){service.resource("rep",invalid)}
        service.resource("rep",valid)
    }
    @Test fun `uncovered derivative or changed bytes prevent final joint completion`()=fixture {
        ready();val id=stored()
        blobs["raw"]="BAD".toByteArray()
        assertThrows(BadRequestException::class.java){service.complete("rep",completion(id))}
        blobs["raw"]="RAW".toByteArray()
        service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1).copy(description="另一版加工成果"))
        assertThrows(BadRequestException::class.java){service.complete("rep",completion(id))}
    }
    @Test fun `actual actors and museum scope are checked independently of general browse permission`()=fixture {
        service.savePlan("rep",plan())
        assertThrows(BadRequestException::class.java){service.requireActor(ReproductionDao["rep"],"information","keeper","代填")}
        ReproductionDao["rep"].orgId="other"
        assertThrows(BadRequestException::class.java){service.detail("rep")}
    }
    @Test fun `actual digital resource index follows configured museum and retains original evidence`()=fixture {
        ready();actorRole("operator","DIGITAL_CROSS",setOf(CollectionConst.STORAGE,CollectionConst.CROSS_ORG))
        CollectionOrgDao.new("other"){name="其他馆";code="O"}
        val guard=DigitalAcquisitionFileReadGuard(CollectionFileReadAuthorization(scope,CollectionActorDirectory(scope)))
        val before=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        val indexes=DigitalAcquisitionFileAccessDao.all().map {it.id.value}.toSet()
        service.detail("rep");guard.requireReadable("raw","LOCAL")
        org.springframework.test.util.ReflectionTestUtils.setField(scope,"defaultOrgId","other")
        try {
            assertThrows(BadRequestException::class.java){service.detail("rep")}
            assertThrows(BadRequestException::class.java){service.originalFile("rep","raw")}
            FileDao["raw"].path="relocated-raw"
            for(path in listOf("raw","relocated-raw")) {
                assertTrue(guard.isProtected(path,"LOCAL"))
                assertThrows(BadRequestException::class.java){guard.requireReadable(path,"LOCAL")}
            }
            assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})
            assertEquals(indexes,DigitalAcquisitionFileAccessDao.all().map {it.id.value}.toSet())
        } finally {FileDao["raw"].path="raw";org.springframework.test.util.ReflectionTestUtils.setField(scope,"defaultOrgId","museum")}
        service.detail("rep");guard.requireReadable("raw","LOCAL")
    }

    @Test fun `resource originals need business permission and old paths stay protected after metadata changes`()=fixture {
        ready();originalReader("reader","COLLECTION_SELECT")
        val guard=DigitalAcquisitionFileReadGuard(CollectionFileReadAuthorization(scope,CollectionActorDirectory(scope)))
        assertTrue(guard.isProtected("/userfiles/raw","LOCAL"));assertDoesNotThrow{guard.requireReadable("/userfiles/raw","LOCAL")}
        val original=SecurityContextHolder.getContext().authentication
        SecurityContextHolder.clearContext()
        assertThrows(BadRequestException::class.java){guard.requireReadable("/userfiles/raw","LOCAL")}
        val reader=SecurityUser(id="reader",name="查询人员",username="reader",password="",groupId="museum",groupIds=setOf("museum"),postIds=emptySet(),dataType=0,roles=emptySet(),authorities=listOf(SimpleGrantedAuthority("COLLECTION_SELECT")),enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(reader,null,reader.authorities)
        assertThrows(BadRequestException::class.java){guard.requireReadable("/userfiles/raw","LOCAL")}
        FileDao["raw"].path="renamed";FileDao["raw"].secure=false
        assertTrue(guard.isProtected("/userfiles/raw","LOCAL"));assertTrue(guard.isProtected("/userfiles/renamed","LOCAL"))
        assertThrows(BadRequestException::class.java){guard.requireReadable("/userfiles/renamed","LOCAL")}
        SecurityContextHolder.getContext().authentication=original
    }
    @Test fun `original download is bound to this actual project and preserves stored bytes`()=fixture {
        ready()
        assertEquals("raw",service.originalFile("rep","raw").path)
        assertThrows(BadRequestException::class.java){service.originalFile("rep","processed")}
        val response=org.springframework.mock.web.MockHttpServletResponse()
        io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","raw",response)
        assertArrayEquals(blobs["raw"],response.contentAsByteArray)
        assertEquals("private, no-store",response.getHeader("Cache-Control"))
        assertEquals("Content-Disposition, X-Collection-Content-Integrity",response.getHeader("Access-Control-Expose-Headers"))
        assertEquals("sha256-verified",response.getHeader("X-Collection-Content-Integrity"))
        ReproductionDao["rep"].orgId="other"
        assertThrows(BadRequestException::class.java){service.originalFile("rep","raw")}
    }
    @Test fun `captured resources remain manageable under original museum after physical collection transfer`()=fixture {
        ready()
        val before=CollectionPermanentArchiveDao.all().map {it.id.value to (it.snapshot to it.digest)}
        val frozen=DigitalAcquisitionPlanDao.all().single().sourceSnapshot
        CollectionDao["piece"].orgId="receiving-museum";CollectionDao["piece"].name="接收馆当前名称"
        val detail=service.detail("rep")
        assertNotNull(detail["sourceIssue"]);assertNull(detail["sourceRevision"])
        assertEquals(frozen,service.formData(ReproductionDao["rep"])["sourceSnapshot"])
        assertEquals(before,CollectionPermanentArchiveDao.all().map {it.id.value to (it.snapshot to it.digest)})
        val id=stored();service.complete("rep",completion(id))
        assertEquals("raw",service.originalFile("rep","raw").path)
        assertTrue(DigitalAcquisitionPlanDao.all().single().sourceSnapshot.contains("藏品"))
    }
    @Test fun `actual submit routes photography and 3D scanning to joint digital flow`()=fixture {
        service.savePlan("rep",plan());val requests=mutableListOf<StartFlowRequest>()
        `when`(flow.startBusiness(any() ?: StartFlowRequest("unused","unused"),anyString(),anyString(), any<(FlowInstanceDao) -> Unit>() ?: {})).thenAnswer { call->
            val req=call.getArgument<StartFlowRequest>(0);requests+=req
            FlowInstanceDao.new {definitionId="def";definitionKey=req.definitionKey;definitionName="数字方案";title=req.title;initiator="operator";initiatorName="经办"}
                .also { call.getArgument<(FlowInstanceDao) -> Unit>(3)(it) }
        }
        reproduction.submit("rep")
        assertEquals(CollectionConst.FLOW_DIGITAL_ACQUISITION_2026,requests.single().definitionKey)
        assertEquals(mapOf("information" to listOf("information"),"keeper" to listOf("keeper")),requests.single().selfSelectAssignees)
        assertEquals("piece",requests.single().formData?.get("collectionId"))
    }
    @Test fun `revoked current digital duty blocks original download and repeated resource write`()=fixture {
        ready()
        val v=stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1)
        val resourceId=service.resource("rep",v)
        assertEquals("raw",service.originalFile("rep","raw").path)
        io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
        assertThrows(BadRequestException::class.java){service.originalFile("rep","raw")}
        assertThrows(BadRequestException::class.java){service.resource("rep",v)}
        assertEquals(v,DigitalAcquisitionResourceDao[resourceId].content)
        assertEquals(2L,DigitalAcquisitionResourceDao.count())
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["path","plan-org","resource-org","file-org","snapshot","archive","reference"])
    fun `digital original requires owned frozen evidence rather than access index alone`(change:String)=fixture {
        ready();assertEquals("raw",service.originalFile("rep","raw").path)
        when(change) {
            "path"->FileDao["raw"].path="processed"
            "plan-org"->DigitalAcquisitionPlanDao.all().single().orgId="other"
            "resource-org"->DigitalAcquisitionResourceDao.all().single().orgId="other"
            "file-org"->DigitalAcquisitionFileDao.all().single().orgId="other"
            "snapshot"->DigitalAcquisitionFileDao.all().single().snapshot=DigitalAcquisitionFileDao.all().single().snapshot.copy(sha256="0".repeat(64))
            "archive"->CollectionPermanentArchiveDao.all().first {it.phase.startsWith("RESOURCE_")}.snapshot="{}"
            else->CollectionPermanentArchiveFileDao.all().filter {it.fileId=="raw"}.forEach {it.delete()}
        }
        assertThrows(BadRequestException::class.java){service.originalFile("rep","raw")}
    }
    @Test fun `changed digital bytes are rejected before any download response body`()=fixture {
        ready();blobs["raw"]="BAD".toByteArray()
        val response=org.springframework.mock.web.MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","raw",response)}
        assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `forged access index cannot make an unrelated file an original`()=fixture {
        ready();val p=DigitalAcquisitionPlanDao.all().single()
        DigitalAcquisitionFileAccessDao.new {orgId="museum";planId=p.id.value;fileId="processed";pathKey="0".repeat(64)}
        assertThrows(BadRequestException::class.java){service.originalFile("rep","processed")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans=[false,true])
    fun `deleting resource rows cannot downgrade a digital original to an unchecked attachment`(deleteParent:Boolean)=fixture {
        ready();DigitalAcquisitionFileDao.all().toList().forEach {it.delete()}
        if(deleteParent)DigitalAcquisitionResourceDao.all().toList().forEach {it.delete()}
        assertThrows(BadRequestException::class.java){service.originalFile("rep","raw")}
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["permission","path"])
    fun `authorization and identity are checked again after reading bytes before output`(change:String)=fixture {
        ready()
        doAnswer {
            object:java.io.ByteArrayInputStream("RAW".toByteArray()) {
                override fun read(b:ByteArray,off:Int,len:Int):Int {
                    val n=super.read(b,off,len)
                    if(n<0) {
                        if(change=="permission")io.hizhengfu.cloud.sys.system.dao.RolePermissions.deleteAll()
                        else FileDao["raw"].path="processed"
                    }
                    return n
                }
            }
        }.`when`(storage).openStream(anyString(),anyString(),isNull(),isNull())
        val response=org.springframework.mock.web.MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","raw",response)}
        assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `download outputs the verified copy without reopening a changed source`()=fixture {
        ready();var opens=0
        doAnswer {
            opens++
            val bytes=blobs["raw"]!!.copyOf()
            blobs["raw"]="BAD".toByteArray()
            bytes.inputStream()
        }.`when`(storage).openStream(anyString(),anyString(),isNull(),isNull())
        val response=org.springframework.mock.web.MockHttpServletResponse()
        io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","raw",response)
        assertArrayEquals("RAW".toByteArray(),response.contentAsByteArray);assertEquals(1,opens)
        assertEquals("sha256-verified",response.getHeader("X-Collection-Content-Integrity"))
    }
    @Test fun `legacy signed attachment stays readable with explicit missing byte baseline`()=fixture {
        blobs["proof"]=ByteArray(100){1};service.savePlan("rep",plan())
        val before=CollectionPermanentArchiveDao.all().map {it.id.value to it.snapshot}
        val response=org.springframework.mock.web.MockHttpServletResponse()
        io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","proof",response)
        assertArrayEquals(blobs["proof"],response.contentAsByteArray)
        assertEquals("not-recorded",response.getHeader("X-Collection-Content-Integrity"))
        assertEquals(before,CollectionPermanentArchiveDao.all().map {it.id.value to it.snapshot})
    }
    @Test fun `oversized digital object stops without releasing response bytes`()=fixture {
        ready();blobs["raw"]="RAW-extra".toByteArray()
        val response=org.springframework.mock.web.MockHttpServletResponse()
        assertThrows(BadRequestException::class.java){io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(service,storage).original("rep","raw",response)}
        assertEquals(0,response.contentAsByteArray.size)
    }
    @Test fun `recorded remote version is opened explicitly and its bytes still verified`()=fixture {
        val remoteType=io.hizhengfu.cloud.common.file.EnumFileStorageType.RUSTFS
        FileDao["raw"].storageType=remoteType
        val revision=io.hizhengfu.cloud.common.file.FileObjectRevision("a".repeat(64),"retained-version","retained-etag")
        val metadata=io.hizhengfu.cloud.common.file.FileObjectMetadata("raw",remoteType,3,revision=revision)
        doReturn(metadata).`when`(storage).metadata("raw","RUSTFS")
        doReturn(metadata).`when`(storage).metadataAtRevision("raw","RUSTFS",revision)
        doAnswer {"RAW".byteInputStream()}.`when`(storage).openRevision("raw","RUSTFS",revision)
        context.beanFactory.registerSingleton("digital-test-storage",storage)
        val remoteArchives=CollectionPermanentArchiveService(binder,scope,readAccess=unprotectedArchiveReadAccessProvider(),
            objectStorage=context.getBeanProvider(FileObjectStorage::class.java))
        val remoteService=DigitalAcquisitionService(scope,users,remoteArchives,originals,locks,storage)
        val p=remoteService.savePlan("rep",plan());approve(p);remoteService.start("rep",start(p));remoteService.capture("rep",capture())
        clearInvocations(storage)
        val response=org.springframework.mock.web.MockHttpServletResponse()
        io.hizhengfu.cloud.collection.api.DigitalAcquisitionController(remoteService,storage).original("rep","raw",response)
        assertArrayEquals("RAW".toByteArray(),response.contentAsByteArray)
        // 列表核验和响应读取都必须使用同一封存版本，次数随核验入口变化。
        val reads=mockingDetails(storage).invocations.filter {it.method.name=="openRevision"}
        assertTrue(reads.isNotEmpty())
        reads.forEach {assertEquals(listOf("raw","RUSTFS",revision),it.arguments.toList())}
        verify(storage,never()).openStream(anyString(),anyString(),isNull(),isNull())
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["plan-org","resource-org","file-org","plan","resource","file","started","captured","missing-resource","missing-file","foreign-archive"])
    fun `detail and flow preview require owned complete frozen digital records`(change:String)=fixture {
        ready();val p=DigitalAcquisitionPlanDao.all().single()
        assertDoesNotThrow {service.detail("rep")};assertDoesNotThrow {service.formData(ReproductionDao["rep"])}
        when(change) {
            "plan-org"->p.orgId="other"
            "resource-org"->DigitalAcquisitionResourceDao.all().single().orgId="other"
            "file-org"->DigitalAcquisitionFileDao.all().single().orgId="other"
            "plan"->p.content=p.content.copy(workLocation="改写工作地点")
            "resource"->DigitalAcquisitionResourceDao.all().single().content=DigitalAcquisitionResourceDao.all().single().content.copy(description="改写资源说明")
            "file"->DigitalAcquisitionFileDao.all().single().snapshot=DigitalAcquisitionFileDao.all().single().snapshot.copy(name="替换成果名称")
            "started"->p.started=null
            "captured"->p.captured=null
            "missing-resource"->DigitalAcquisitionResourceDao.all().single().delete()
            "missing-file"->DigitalAcquisitionFileDao.all().single().delete()
            else->CollectionPermanentArchiveDao.new {orgId="other";bizType=DigitalAcquisitionService.BIZ;bizId=p.id.value;phase="FOREIGN";snapshot="{}";digest=CollectionCareEvidence.digest(snapshot)}
        }
        assertAll(
            {assertThrows(BadRequestException::class.java){service.detail("rep")}},
            {assertThrows(BadRequestException::class.java){service.formData(ReproductionDao["rep"])}}
        )
    }
    @Test fun `foreign malformed resource is refused before parsing its private content`()=fixture {
        ready()
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec("UPDATE col_digital_acquisition_resource SET org_id='other', content='{}'")
        val failure=assertThrows(BadRequestException::class.java){service.detail("rep")}
        assertTrue(failure.message!!.contains("所属馆"))
    }
    @Test fun `model references missing from selected raw files cannot be archived as complete data`()=fixture {
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"private-texture.png"}]}""".toByteArray()
        blobs["raw"]=bytes;FileDao["raw"].apply {name="model.gltf";ext="gltf";size=bytes.size.toLong()}
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        assertThrows(BadRequestException::class.java){service.capture("rep",capture())}
        assertNull(DigitalAcquisitionPlanDao[p].captured)
        assertEquals(0L,DigitalAcquisitionResourceDao.count())
        assertEquals(0L,DigitalAcquisitionFileDao.count())
    }

    @Test fun `complete model selection freezes and protects texture with its source`()=fixture {
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"processed"}]}""".toByteArray()
        blobs["raw"]=bytes;FileDao["raw"].apply {name="model.gltf";ext="gltf";size=bytes.size.toLong()}
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        service.capture("rep",capture().copy(rawFileIds=setOf("raw","processed")))
        val refs=DigitalAcquisitionFileDao.all().toList()
        assertEquals(setOf("raw","processed"),refs.map {it.fileId}.toSet())
        assertTrue(DigitalAcquisitionFileAccessDao.all().any {it.fileId=="processed"})
        assertTrue(CollectionPermanentArchiveFileDao.all().any {it.fileId=="processed"})
        assertThrows(BadRequestException::class.java){CollectionArchiveFileRetentionGuard().requireDeletable("processed")}
        val guard=DigitalAcquisitionFileReadGuard(CollectionFileReadAuthorization(scope,CollectionActorDirectory(scope)))
        assertTrue(guard.isProtected("/userfiles/processed","LOCAL"))
        assertDoesNotThrow{guard.requireReadable("/userfiles/processed","LOCAL")}
        val login=SecurityContextHolder.getContext().authentication
        SecurityContextHolder.clearContext()
        try {
            assertThrows(BadRequestException::class.java){guard.requireReadable("/userfiles/processed","LOCAL")}
            FileDao["processed"].path="renamed-texture"
            assertTrue(guard.isProtected("/userfiles/processed","LOCAL"))
            assertTrue(guard.isProtected("/userfiles/renamed-texture","LOCAL"))
            assertThrows(BadRequestException::class.java){guard.requireReadable("/userfiles/renamed-texture","LOCAL")}
        } finally {SecurityContextHolder.getContext().authentication=login}
    }

    @Test fun `processed model cannot omit files from its own stage`()=fixture {
        ready()
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"unselected.png"}]}""".toByteArray()
        blobs["processed"]=bytes;FileDao["processed"].apply {name="model.gltf";ext="gltf";size=bytes.size.toLong()}
        assertThrows(BadRequestException::class.java){service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1))}
        assertEquals(1L,DigitalAcquisitionResourceDao.count())
    }

    @Test fun `manifest changed during archive binding cannot report successful capture`()=fixture {
        val before="""{"asset":{"version":"2.0"},"images":[{"uri":"processed"}]}""".toByteArray()
        val after="""{"asset":{"version":"2.0"},"images":[{"uri":"organized"}]}""".toByteArray()
        assertEquals(before.size,after.size)
        blobs["raw"]=before;FileDao["raw"].apply {name="model.gltf";ext="gltf";size=before.size.toLong()}
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        `when`(binder.resolveSingle("raw")).thenAnswer {blobs["raw"]=after;"raw"}
        assertThrows(BadRequestException::class.java){service.capture("rep",capture().copy(rawFileIds=setOf("raw","processed")))}
        assertNull(DigitalAcquisitionPlanDao[p].captured)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["capture","resource","complete"])
    fun `repeat success cannot conceal changed frozen resource bytes`(kind:String)=fixture {
        ready()
        val repeat:()->Unit=when(kind) {
            "capture"->{blobs["raw"]="NEW".toByteArray();{service.capture("rep",capture());Unit}}
            "resource"->{val v=stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1);service.resource("rep",v);blobs["processed"]="CHANGED".toByteArray();{service.resource("rep",v);Unit}}
            else->{val v=completion(stored());service.complete("rep",v);blobs["stored"]="CHANGE".toByteArray();{service.complete("rep",v);Unit}}
        }
        val before=CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)}
        assertThrows(BadRequestException::class.java){repeat()}
        assertEquals(before,CollectionPermanentArchiveDao.all().associate {it.id.value to (it.snapshot to it.digest)})
    }

    @Test fun `next stage cannot use parent whose frozen bytes changed`()=fixture {
        ready();val parent=service.resource("rep",stage(EnumDigitalResourceStage.PROCESSED,raw(),"processed",1))
        blobs["processed"]="CHANGED".toByteArray()
        assertThrows(BadRequestException::class.java){service.resource("rep",stage(EnumDigitalResourceStage.ORGANIZED,parent,"organized",2))}
        assertEquals(2L,DigitalAcquisitionResourceDao.count())
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["manifest","texture"])
    fun `Spring transaction rolls back archive and access indexes after late dependency change then permits retry`(kind:String) {
        val manager=org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager(
            org.sqlite.SQLiteDataSource().apply {url="jdbc:sqlite:${originalsDir.resolve("rollback.db")}"},org.jetbrains.exposed.v1.core.DatabaseConfig {})
        val tx=org.springframework.transaction.support.TransactionTemplate(manager)
        val factory=org.springframework.aop.framework.ProxyFactory(service)
        factory.isProxyTargetClass=true
        factory.addAdvice(org.springframework.transaction.interceptor.TransactionInterceptor().apply {
            transactionManager=manager
            transactionAttributeSource=org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()
        })
        service=factory.proxy as DigitalAcquisitionService
        assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service))
        val before="""{"asset":{"version":"2.0"},"images":[{"uri":"processed"}]}""".toByteArray()
        val after="""{"asset":{"version":"2.0"},"images":[{"uri":"organized"}]}""".toByteArray()
        val planId=tx.execute {
            seedFixture();blobs["raw"]=before;FileDao["raw"].apply {name="model.gltf";ext="gltf";size=before.size.toLong()}
            val id=service.savePlan("rep",plan());approve(id);service.start("rep",start(id));id
        }
        fun recordedIds()=tx.execute {listOf(
            DigitalAcquisitionResourceDao.all().map {it.id.value}.toSet(),DigitalAcquisitionFileDao.all().map {it.id.value}.toSet(),
            CollectionPermanentArchiveDao.all().map {it.id.value}.toSet(),CollectionPermanentArchiveFileDao.all().map {it.id.value}.toSet(),
            DigitalAcquisitionFileAccessDao.all().map {it.id.value}.toSet(),CollectionOperationLockDao.all().map {it.id.value}.toSet())}
        val initial=recordedIds()
        var mutate=true
        `when`(binder.resolveSingle("raw")).thenAnswer {
            if(mutate) {if(kind=="manifest")blobs["raw"]=after else blobs["processed"]="CHANGED".toByteArray()}
            "raw"
        }
        assertThrows(BadRequestException::class.java){service.capture("rep",capture().copy(rawFileIds=setOf("raw","processed")))}
        assertEquals(initial,recordedIds())
        assertNull(tx.execute {DigitalAcquisitionPlanDao[planId].captured})
        mutate=false;blobs["raw"]=before;blobs["processed"]="PROCESS".toByteArray()
        service.capture("rep",capture().copy(rawFileIds=setOf("raw","processed")))
        tx.execute {
            assertNotNull(DigitalAcquisitionPlanDao[planId].captured)
            assertEquals(1L,DigitalAcquisitionResourceDao.count());assertEquals(2L,DigitalAcquisitionFileDao.count())
            assertTrue(DigitalAcquisitionFileAccessDao.all().any {it.fileId=="processed"})
            assertDoesNotThrow{locks.requireUnlocked("piece")}
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["capture-resource","capture-file","complete-resource","complete-file"])
    fun `repeat cannot hide missing records behind existing completion receipt`(kind:String)=fixture {
        ready()
        val done=if(kind.startsWith("complete"))completion(stored()).also {service.complete("rep",it)} else null
        val rawResource=DigitalAcquisitionResourceDao[raw()]
        if(kind.endsWith("resource"))rawResource.delete()
        else DigitalAcquisitionFileDao.all().single {it.resourceId==rawResource.id.value}.delete()
        assertThrows(BadRequestException::class.java) {
            if(done==null)service.capture("rep",capture()) else service.complete("rep",done)
        }
    }

    @Test fun `unchanged bytes newly identified as model still require complete dependencies on repeat`()=fixture {
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"unselected.png"}]}""".toByteArray()
        blobs["raw"]=bytes;FileDao["raw"].size=bytes.size.toLong()
        ready()
        // 摘要、名称和路径不变；单靠旧摘要比较不能替代当前格式的依赖检查。
        FileDao["raw"].ext="gltf"
        assertThrows(BadRequestException::class.java){service.capture("rep",capture())}
    }

    @Test fun `package mapping survives hashed upload paths capture stages and joint completion`()=fixture {
        val bytes="""{"asset":{"version":"2.0"},"images":[{"uri":"textures/t.png"}]}""".toByteArray()
        blobs["raw"]=bytes;FileDao["raw"].size=bytes.size.toLong()
        val paths=mapOf("scene.gltf" to "raw","textures/t.png" to "processed")
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        val captured=capture().copy(rawFileIds=setOf("raw","processed"),rawPackageFiles=paths)
        service.capture("rep",captured)
        service.capture("rep",captured)
        var parent=raw()
        listOf(EnumDigitalResourceStage.PROCESSED,EnumDigitalResourceStage.ORGANIZED,EnumDigitalResourceStage.STORED).forEachIndexed {index,kind->
            val v=stage(kind,parent,"raw",index+1L).copy(fileIds=setOf("raw","processed"),packageFiles=paths)
            parent=service.resource("rep",v)
        }
        val done=completion(parent);service.complete("rep",done);service.complete("rep",done)
        assertEquals(paths,DigitalAcquisitionPlanDao[p].captured?.rawPackageFiles)
        assertTrue(DigitalAcquisitionResourceDao.all().all {it.content.packageFiles==paths})
        assertTrue(DigitalAcquisitionFileDao.all().all {it.snapshot.path in setOf("raw","processed")})
        assertTrue(CollectionPermanentArchiveDao.all().filter {it.phase.startsWith("RESOURCE_")}.all {it.snapshot.contains("textures/t.png")})
        assertThrows(BadRequestException::class.java){service.capture("rep",captured.copy(rawPackageFiles=paths+mapOf("extra.png" to "processed")))}
    }

    @Test fun `explicit tileset composite external GLB and texture freeze together through every stage`()=fixture {
        val paths=mapOf("包/tileset.json" to "raw","包/models/tile.cmpt" to "processed",
            "包/models/mesh.glb" to "organized","包/textures/t.png" to "stored")
        blobs["raw"]="""{"asset":{"version":"1.1"},"root":{"content":{"uri":"models/tile.cmpt"}}}""".toByteArray()
        blobs["processed"]=DigitalTileFixture.tile("cmpt",DigitalTileFixture.tile("i3dm","mesh.glb".toByteArray(),0))
        blobs["organized"]=DigitalTileFixture.glb("../textures/t.png")
        paths.values.forEach {FileDao[it].size=blobs.getValue(it).size.toLong()}
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        val incomplete=paths.filterValues {it!="stored"}
        assertThrows(BadRequestException::class.java) {
            service.capture("rep",capture().copy(rawFileIds=incomplete.values.toSet(),rawPackageFiles=incomplete))
        }
        assertNull(DigitalAcquisitionPlanDao[p].captured);assertEquals(0L,DigitalAcquisitionResourceDao.count())
        val captured=capture().copy(rawFileIds=paths.values.toSet(),rawPackageFiles=paths)
        service.capture("rep",captured)
        var parent=raw()
        listOf(EnumDigitalResourceStage.PROCESSED,EnumDigitalResourceStage.ORGANIZED,EnumDigitalResourceStage.STORED).forEachIndexed {index,kind->
            parent=service.resource("rep",stage(kind,parent,"raw",index+1L).copy(fileIds=paths.values.toSet(),packageFiles=paths))
        }
        val done=completion(parent);service.complete("rep",done);service.complete("rep",done)
        assertEquals(4L,DigitalAcquisitionResourceDao.count());assertEquals(16L,DigitalAcquisitionFileDao.count())
        assertTrue(DigitalAcquisitionResourceDao.all().all {it.content.packageFiles==paths})
        DigitalAcquisitionFileDao.all().forEach {f->
            assertEquals(f.snapshot.fileId,f.snapshot.path)
            assertEquals(MessageDigest.getInstance("SHA-256").digest(blobs.getValue(f.snapshot.fileId)).joinToString("") {"%02x".format(it)},f.snapshot.sha256)
        }
    }

    @Test fun `legacy resource and capture serialization omit absent package mapping`()=fixture {
        val captured=capture()
        val v=stage(EnumDigitalResourceStage.PROCESSED,"parent","processed",1)
        val captureJson=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(captured)!!
        val stageJson=io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.toJson(v)!!
        assertFalse(captureJson.contains("rawPackageFiles"));assertFalse(stageJson.contains("packageFiles"))
        assertEquals(captured,io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readValue(captureJson,DigitalCaptureVo::class.java))
        assertEquals(v,io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils.mapper.readValue(stageJson,DigitalResourceStageVo::class.java))
    }

    @Test fun `package mapping cannot import an unselected file during business registration`()=fixture {
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        assertThrows(BadRequestException::class.java){service.capture("rep",capture().copy(rawPackageFiles=mapOf("raw.bin" to "raw","foreign.bin" to "organized")))}
        assertEquals(0L,DigitalAcquisitionResourceDao.count());assertNull(DigitalAcquisitionPlanDao[p].captured)
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings=["b3dm","i3dm","cmpt","external-i3dm"])
    fun `binary tile dependency closure is required through capture storage and joint completion`(kind:String)=fixture {
        val external=kind=="external-i3dm"
        val tileType=if(external) "i3dm" else kind
        val model=DigitalTileFixture.glb("textures/t.png")
        val tile=when(kind) {
            "cmpt" -> DigitalTileFixture.tile("cmpt",DigitalTileFixture.tile("b3dm",model))
            "external-i3dm" -> DigitalTileFixture.tile("i3dm","mesh.glb".toByteArray(),0)
            else -> DigitalTileFixture.tile(kind,model)
        }
        blobs["raw"]=tile;FileDao["raw"].size=tile.size.toLong()
        blobs["processed"]=model;FileDao["processed"].size=model.size.toLong()
        val paths=if(external) mapOf("model.i3dm" to "raw","mesh.glb" to "processed","textures/t.png" to "organized")
            else mapOf("model.$tileType" to "raw","textures/t.png" to "organized")
        val p=service.savePlan("rep",plan());approve(p);service.start("rep",start(p))
        val initialArchives=CollectionPermanentArchiveDao.all().map {it.id.value}.toSet()
        // 所有文件在存储中已存在，但未选择/登记的贴图不属于冻结包。
        val missing=paths.filterValues {it!="organized"}
        assertThrows(BadRequestException::class.java) {
            service.capture("rep",capture().copy(rawFileIds=missing.values.toSet(),rawPackageFiles=missing))
        }
        assertNull(DigitalAcquisitionPlanDao[p].captured)
        assertEquals(0L,DigitalAcquisitionResourceDao.count());assertEquals(0L,DigitalAcquisitionFileDao.count())
        assertEquals(initialArchives,CollectionPermanentArchiveDao.all().map {it.id.value}.toSet())
        val captured=capture().copy(rawFileIds=paths.values.toSet(),rawPackageFiles=paths)
        service.capture("rep",captured);service.capture("rep",captured)
        var parent=raw()
        listOf(EnumDigitalResourceStage.PROCESSED,EnumDigitalResourceStage.ORGANIZED,EnumDigitalResourceStage.STORED).forEachIndexed {index,stageKind->
            parent=service.resource("rep",stage(stageKind,parent,"raw",index+1L).copy(fileIds=paths.values.toSet(),packageFiles=paths))
        }
        val done=completion(parent);service.complete("rep",done);service.complete("rep",done)
        assertTrue(DigitalAcquisitionResourceDao.all().all {it.content.packageFiles==paths})
        val expected=MessageDigest.getInstance("SHA-256").digest(tile).joinToString("") {"%02x".format(it)}
        assertTrue(DigitalAcquisitionFileDao.all().filter {it.snapshot.fileId=="raw"}.all {it.snapshot.sha256==expected && it.snapshot.path=="raw"})
        assertTrue(CollectionPermanentArchiveDao.all().filter {it.phase.startsWith("RESOURCE_")}.all {it.snapshot.contains("textures/t.png")})
    }

}
