package io.hizhengfu.cloud.collection.service

import com.aspose.words.Document
import com.aspose.words.DocumentBuilder
import com.aspose.words.SaveFormat
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.*
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.event.FileUploadEvent
import io.hizhengfu.cloud.sys.system.dao.*
import io.hizhengfu.cloud.sys.file.dao.*
import io.hizhengfu.cloud.sys.file.service.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.MapPropertySource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayOutputStream
import java.nio.file.Files as NioFiles
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Real parser, sys upload service, SQL file metadata and local bytes in an isolated current PostgreSQL schema. */
@Tag("postgres-concurrency")
class CollectionWordStorageCurrentIntegrationTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement(proxyTargetClass = true) class Transactions

    private class Runtime(val c: AnnotationConfigApplicationContext, val tx: TransactionTemplate,
        val ds: PGSimpleDataSource, val base: Path) {
        val imports = c.getBean(CollectionImportService::class.java)
        val word = c.getBean(CollectionWordImportService::class.java)
        val objects = c.getBean(FileObjectStorage::class.java)
        val uploads = mutableListOf<FileVo>()
        fun <T> db(block: () -> T): T = tx.execute { block() }
        fun grant(vararg permissions: String) = db {
            RolePermissions.deleteAll(); actorRole("reader", "CURRENT_IMPORT", permissions.toSet())
        }
        fun row(reg: String = "EXISTING") = CollectionImportRowVo(totalRegNo=reg, name="开发合成Word当前链",
            categoryId="category", level="GENERAL", source="OLD", acquireDate=LocalDate.of(2020,1,1), quantity=1, unit="件")
        fun snapshot(): List<List<String>> = db {
            listOf(CollectionOrgs, Collections, CollectionRegistrationNumbers, CollectionCatalogs, CollectionCatalogVersions,
                CollectionLedgerLogs, CollectionWordImports, CollectionTrackingEvents, StorageMovements,
                CollectionAttachments, Files, FileUsers, FileContents).map { table ->
                table.selectAll().map { row -> table.columns.joinToString("|") { row[it].toString() } }.sorted()
            }
        }
        fun externalUserChange(kind: String) {
            ds.connection.use { connection ->
                connection.prepareStatement(if(kind=="revoked") "DELETE FROM sys_role_permission"
                    else "UPDATE sys_user SET group_id='other' WHERE id='reader'").use { assertTrue(it.executeUpdate()>0) }
            }
        }
        fun evidence(case: String, values: Map<String,Any?>) {
            System.getProperty("collection.word.storage.evidence.dir")?.let {
                val path=Path.of(it); NioFiles.createDirectories(path)
                NioFiles.writeString(path.resolve(case+".json"), JsonUtils.toJson(values)+"\n")
            }
        }
    }

    private fun fixture(onUpload: ((Runtime)->Unit)?=null, body:(Runtime)->Unit) {
        val config=System.getProperty("collection.test.import.pg.config") ?: System.getProperty("collection.test.pg.config")
            ?: error("Requires existing PostgreSQL private connection properties; no SQLite substitute")
        val properties=Properties().apply { NioFiles.newInputStream(Path.of(config)).use { load(it) } }
        val ds=PGSimpleDataSource().apply {
            setURL(properties.getProperty("url")); user=properties.getProperty("user"); password=properties.getProperty("password")
            currentSchema="word_current_"+UUID.randomUUID().toString().replace("-",""); applicationName=currentSchema
        }
        ds.connection.use { connection -> connection.createStatement().use { it.execute("CREATE SCHEMA "+ds.currentSchema) } }
        val old=SecurityUser(id="reader", name="普通开发导入员", username="reader", password="", groupId="museum",
            groupIds=setOf("museum"), postIds=emptySet(), dataType=0, roles=setOf("CURRENT_IMPORT"), enabled=true,
            authorities=listOf(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT,CollectionConst.STORAGE).map(::SimpleGrantedAuthority))
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(old,null,old.authorities)
        val manager=SpringTransactionManager(ds,DatabaseConfig {})
        try {
            AnnotationConfigApplicationContext().use { c ->
                lateinit var r: Runtime
                c.environment.propertySources.addFirst(MapPropertySource("test-file-root",mapOf("file.basedir" to root.resolve("objects").toString())))
                c.beanFactory.registerSingleton("transactionManager",manager)
                c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
                c.beanFactory.registerSingleton("scope",CollectionScopeResolver("multi","museum"))
                c.beanFactory.registerSingleton("objects",LocalFileObjectStorage(root.resolve("objects").toString()))
                c.beanFactory.registerSingleton("folders",mock(FileFolderService::class.java))
                c.beanFactory.registerSingleton("readAccess",mock(FileReadAccess::class.java))
                for(type in listOf(CollectionGradingService::class.java,AccessionReceiptService::class.java,
                    CollectionChangeService::class.java,CollectionRetainedLedgerService::class.java,CollectionDisclosureService::class.java))
                    c.beanFactory.registerSingleton(type.simpleName,mock(type))
                c.addApplicationListener { event -> if(event is FileUploadEvent) {
                    r.uploads.add(event.fileInfo); onUpload?.invoke(r)
                } }
                c.register(Transactions::class.java,FileService::class.java,FileSystemStorageService::class.java,
                    BusinessAttachmentBinder::class.java,CollectionWordDocumentParser::class.java,
                    CollectionFileService::class.java,CollectionOperationLockService::class.java,CollectionRegistrationNumberService::class.java,
                    CollectionCatalogService::class.java,CollectionService::class.java,CollectionImportService::class.java,
                    CollectionWordImportService::class.java,StorageMovementService::class.java)
                c.refresh(); SpringContextHolder().setApplicationContext(c)
                r=Runtime(c,TransactionTemplate(manager),ds,root.resolve("objects"))
                r.db {
                    originalReaderSchema()
                    SchemaUtils.create(Files,FileUsers,FileContents,CollectionMedias,CollectionMediaFolders,CollectionAttachments,
                        CollectionOrgs,Collections,CollectionCategories,Warehouses,CollectionOperationLocks,CollectionRegistrationNumbers,
                        CollectionRetainedLedgers,CollectionCatalogs,CollectionCatalogVersions,CollectionLedgerLogs,
                        CollectionWordImports,CollectionTrackingEvents,StorageMovements)
                    originalReader("reader",CollectionConst.LEDGER_CREATE)
                    CollectionCategoryDao.new("category") { name="标准分类";parentId="0";parentIds="0," }
                    for(org in listOf("museum","other")) {
                        CollectionOrgDao.new(org) {name=org;code=org;systemGroupId=org}
                        WarehouseDao.new("warehouse-"+org) {orgId=org;name="2号库";parentId="0";parentIds="0,"}
                        CollectionDao.new("item-"+org) {orgId=org;name="开发合成Word当前链";totalRegNo="EXISTING";categoryId="category"
                            acquireDate=LocalDate.of(2020,1,1);lifeStatus=EnumCollectionStatus.PENDING;catalogStatus=EnumCatalogStatus.DRAFT}
                    }
                }
                body(r)
            }
        } finally {
            SecurityContextHolder.clearContext()
            ds.connection.use { connection -> connection.createStatement().use { it.execute("DROP SCHEMA "+ds.currentSchema+" CASCADE") } }
        }
    }

    @ParameterizedTest @ValueSource(strings=["registration","dynamic"])
    fun `real Word upload after museum reassignment preserves exact original and same museum history`(kind:String)=fixture { r ->
        r.grant(if(kind=="registration") CollectionConst.LEDGER_CREATE else CollectionConst.STORAGE, CollectionConst.SELECT)
        r.db { UserDao["reader"].groupId="other" }
        val file=document(kind)
        val check=r.word.validate(file)
        assertTrue(check.valid,check.errors.toString()); assertEquals("other",check.confirmation!!.orgId)
        val result=r.word.commit(file,expectedOrgId=check.confirmation!!.orgId,expectedValidationHash=check.confirmation!!.validationHash)
        assertTrue(result.valid,result.errors.toString()); assertEquals(1,r.uploads.size)
        val uploaded=r.uploads.single(); val restored=r.objects.openStream(uploaded.path!!,uploaded.storageType).use { it.readBytes() }
        assertArrayEquals(file.bytes,restored)
        val records=r.db {
            val record=CollectionWordImportDao[result.importId!!]; val coll=CollectionDao[result.collectionId!!]
            assertEquals("other",record.orgId);assertEquals("other",coll.orgId);assertEquals(uploaded.id,record.sourceFileId)
            assertEquals(sha(file.bytes),record.sourceHash)
            assertEquals(if(kind=="registration")EnumCollectionWordDocumentType.REGISTRATION else EnumCollectionWordDocumentType.DYNAMIC_TRACKING,record.documentType)
            val attachment=CollectionAttachmentDao.all().single()
            assertEquals("other",attachment.orgId);assertEquals(result.importId,attachment.bizId)
            assertEquals(result.collectionId,attachment.collectionId);assertEquals(uploaded.id,attachment.fileId)
            assertEquals(1L,FileDao.count());assertEquals(1L,FileContents.selectAll().count());assertEquals(1L,FileUsers.selectAll().count())
            val events=CollectionTrackingEventDao.all().toList();val moves=StorageMovementDao.all().toList()
            assertEquals(result.eventCount,events.size);assertEquals(result.movementCount,moves.size)
            events.forEach {assertEquals("other",it.orgId);assertEquals(result.importId,it.importId);assertEquals(result.collectionId,it.collectionId)}
            moves.forEach {assertEquals("other",it.orgId);assertEquals(result.collectionId,it.collectionId)}
            mapOf("importId" to record.id.value,"collectionId" to coll.id.value,"orgId" to coll.orgId,"fileId" to uploaded.id,
                "documentType" to record.documentType.name,"eventCount" to events.size,"movementCount" to moves.size)
        }
        val currentHistory=r.word.history(result.collectionId!!)
        assertEquals(listOf(result.importId),currentHistory.imports.map {it.id})
        assertEquals(result.eventCount,currentHistory.events.size)
        r.externalUserChange("revoked")
        assertThrows(BadRequestException::class.java) {r.word.history(result.collectionId!!)}
        r.evidence("success-"+kind,records+mapOf("sourceSha256" to sha(file.bytes),"restoredSha256" to sha(restored),"sourceSize" to file.size,
            "actualUploadCalls" to r.uploads.size,"currentHistoryMatches" to true,"revokedHistoryRejected" to true,"realFileServiceAndLocalStorage" to true))
    }

    @ParameterizedTest @CsvSource("registration,revoked","registration,moved","dynamic,revoked","dynamic,moved")
    fun `real upload current privilege change rolls back SQL and records exact unbound byte residue`(kind:String,change:String)=fixture(onUpload={r->r.externalUserChange(change)}) { r ->
        r.grant(if(kind=="registration")CollectionConst.LEDGER_CREATE else CollectionConst.STORAGE)
        val file=document(kind);val check=r.word.validate(file);assertTrue(check.valid,check.errors.toString())
        val before=r.snapshot()
        val failure=assertThrows(BadRequestException::class.java) {
            r.word.commit(file,expectedOrgId=check.confirmation!!.orgId,expectedValidationHash=check.confirmation!!.validationHash)
        }
        assertEquals(1,r.uploads.size,"Must reach real completed upload before refusal, not a preview rejection")
        assertEquals(before,r.snapshot(),"All declared business/file SQL must roll back; independent authority change is preserved")
        val uploaded=r.uploads.single()
        val residue=r.objects.openStream(uploaded.path!!,uploaded.storageType).use {it.readBytes()}
        assertArrayEquals(file.bytes,residue,"Current local storage is not transactional: accurately record unbound bytes")
        r.db {
            assertEquals(0L,CollectionWordImportDao.count());assertEquals(0L,CollectionTrackingEventDao.count())
            assertEquals(0L,StorageMovementDao.count());assertEquals(0L,CollectionAttachmentDao.count())
            assertEquals(0L,FileDao.count());assertEquals(0L,FileUsers.selectAll().count());assertEquals(0L,FileContents.selectAll().count())
        }
        r.evidence("rollback-"+kind+"-"+change,mapOf("refusalType" to failure.javaClass.simpleName,"refusalMessage" to failure.message,
            "actualUploadCalls" to 1,"declaredSqlRowsUnchanged" to true,"sqlFileOrAttachmentAssociations" to 0,
            "storedByteResidueObjects" to 1,"residueSourceSha256" to sha(residue),"sourceSha256" to sha(file.bytes),
            "residueSize" to residue.size,"residueIsUnbound" to true,"notClaimedZeroResidue" to true,
            "testCleanup" to "Isolated schema dropped; JUnit temporary storage directory removed after test"))
    }

    @ParameterizedTest @ValueSource(strings=["survey","word"])
    fun `two PostgreSQL connections reject a changed fixed match without updating later object`(kind:String)=fixture { r ->
        if(kind=="word") r.grant(CollectionConst.STORAGE)
        else r.grant(CollectionConst.LEDGER_CREATE,CollectionConst.LEDGER_CORRECT)
        val file=document("dynamic");val rows=listOf(r.row())
        val confirmation=if(kind=="word")r.word.validate(file).confirmation!!
            else r.imports.validate(rows,EnumCollectionImportMode.UPSERT,"独立合成普查匹配核对").confirmation!!
        val authentication=SecurityContextHolder.getContext().authentication
        val pool=Executors.newSingleThreadExecutor()
        try {
            r.ds.connection.use { held ->
                held.autoCommit=false
                held.createStatement().use {it.executeQuery("SELECT id FROM col_collection WHERE id='item-museum' FOR UPDATE").close()}
                val future=pool.submit(Callable {
                    SecurityContextHolder.getContext().authentication=authentication
                    try {runCatching {
                        if(kind=="word")r.word.commit(file,expectedOrgId=confirmation.orgId,expectedValidationHash=confirmation.validationHash)
                        else r.imports.commit(rows,EnumCollectionImportMode.UPSERT,"独立合成普查匹配核对",confirmation.orgId,confirmation.validationHash)
                    }} finally {SecurityContextHolder.clearContext()}
                })
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15)
                var lockWaitObserved=false
                while(System.nanoTime()<deadline && !lockWaitObserved) {
                    r.ds.connection.use { observer -> observer.prepareStatement("SELECT count(*) FROM pg_stat_activity WHERE application_name=? AND wait_event_type='Lock'").use {
                        it.setString(1,r.ds.applicationName);it.executeQuery().use {rs->rs.next();lockWaitObserved=rs.getInt(1)>0}
                    } }
                    if(!lockWaitObserved)Thread.sleep(30)
                }
                assertTrue(lockWaitObserved,"The second actual PG connection must wait on the exact original row lock")
                held.createStatement().use { statement ->
                    statement.executeUpdate("UPDATE col_collection SET total_reg_no='MOVED' WHERE id='item-museum'")
                    val columns=Collections.columns.joinToString(",") {"\""+it.name+"\""}
                    val values=Collections.columns.joinToString(",") {
                        when(it) {Collections.id -> "'later-object'";Collections.totalRegNo -> "'EXISTING'";else -> "\""+it.name+"\""}
                    }
                    assertEquals(1,statement.executeUpdate("INSERT INTO col_collection ("+columns+") SELECT "+values+" FROM col_collection WHERE id='item-museum'"))
                }
                held.commit()
                val result=future.get(30,TimeUnit.SECONDS)
                assertTrue(result.isFailure,"Original confirmation must not silently switch to the later row")
                assertTrue(result.exceptionOrNull() is BadRequestException,result.exceptionOrNull().toString())
                r.db {
                    assertEquals("MOVED",CollectionDao["item-museum"].totalRegNo)
                    assertEquals("EXISTING",CollectionDao["later-object"].totalRegNo);assertEquals("开发合成Word当前链",CollectionDao["later-object"].name)
                    assertEquals(0L,CollectionLedgerLogDao.count());assertEquals(0L,CollectionWordImportDao.count())
                    assertEquals(0L,CollectionTrackingEventDao.count());assertEquals(0L,StorageMovementDao.count());assertEquals(0L,FileDao.count())
                }
                r.evidence("concurrency-"+kind,mapOf("actualPostgresConnections" to 2,"originalRowLockWaitObserved" to true,
                    "oldTarget" to "item-museum","laterObject" to "later-object","fixedConfirmationRejected" to true,
                    "laterObjectUnmodified" to true,"newImportsOrEventsOrFiles" to 0,
                    "surveyAndOrdinaryShareFormalImportCommit" to (kind=="survey"),"wordParserAndStorageReal" to (kind=="word")))
            }
        } finally {pool.shutdownNow()}
    }

    private fun document(kind:String):MockMultipartFile {
        val doc=Document();val b=DocumentBuilder(doc)
        fun table(rows:List<List<String>>) {for(row in rows) {for(value in row){b.insertCell();b.write(value)};b.endRow()};b.endTable()}
        if(kind=="registration") {
            b.writeln("文物藏品登记表")
            table(listOf(listOf("名 称","开发合成Word当前链"),listOf("曾 用 名","开发合成原名"),
                listOf("总登记号","NEW-WORD","入藏日期","1956年"),listOf("类 别","标准分类","级 别","二级"),
                listOf("年代类型","清(1616~1911)","年 代","清代"),listOf("质地","瓷"),
                listOf("尺寸","口径46.7，底径26.5，高7.5(cm)cm"),listOf("传统数量","1件","实际数量","1件"),listOf("质量","3.700kg"),
                listOf("完残程度","残缺"),listOf("完残状况","开发合成局部损伤"),listOf("来源方式","拨交"),
                listOf("当前状况","基本稳定"),listOf("保存条件","温度15℃，湿度55%。"),listOf("保护优先等级","日常维护")))
        } else {
            b.writeln("文物藏品动态跟踪记录汇总表");b.writeln("2026 年至 2027 年")
            table(listOf(listOf("藏品名称","开发合成Word当前链","总登记号","EXISTING"),
                listOf("提取退还记录","提取:2026年8月26日 11：12退还: 2026年8月26日 12：00"),
                listOf("观察监测记录",""),listOf("保护修复记录","数字化拍摄高清拍照；三维扫描"),listOf("鉴定变更记录",""),
                listOf("研究利用情况",""),listOf("事故情况记录",""),listOf("藏品注销记录",""),listOf("备注","开发合成独立软件验收")))
            b.writeln("附页")
            table(listOf(listOf("时间","出库","入库","事由","经手人","备注"),
                listOf("2026.8.26 11：12","2号库","","文物高清图像数字化采集工作","开发合成经办人",""),
                listOf("2026.8.26 12：00","","2号库","文物高清图像数字化采集工作","开发合成经办人","")))
        }
        val bytes=ByteArrayOutputStream().use {doc.save(it,SaveFormat.DOCX);it.toByteArray()}
        return MockMultipartFile("file",kind+".docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes)
    }
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
}
