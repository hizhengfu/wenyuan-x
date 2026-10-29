package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.support.StaticApplicationContext
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.util.ReflectionTestUtils

class CollectionMainAccessPersistenceTest {
    private class Fixture(val service: CollectionService, val scope: CollectionScopeResolver, val disclosure: CollectionDisclosureService) {
        fun grant(vararg permissions: String) { RolePermissions.deleteAll(); actorRole("reader", "CURRENT_MAIN", permissions.toSet()) }
        fun request(keyword: String? = null) = MockHttpServletRequest().apply {
            setParameter("pageSize", "0"); keyword?.let { setParameter("keyword", it) }
        }
        fun damage(kind: String) { when (kind) {
            "revoked" -> grant()
            "disabled" -> UserDao["reader"].enabled = false
            "moved" -> { UserDao["reader"].groupId = "other"; actorRole("reader", "CROSS", setOf(CollectionConst.CROSS_ORG)) }
            "configured" -> ReflectionTestUtils.setField(scope, "defaultOrgId", "other")
            "museum_disabled" -> CollectionOrgDao["museum"].orgStatus = EnumOrgStatus.DISABLED
            "storage" -> actorRole("reader", "STORAGE", setOf(CollectionConst.STORAGE))
            "keeper" -> actorRole("reader", CollectionConst.ROLE_KEEPER)
            else -> error(kind)
        } }
        fun snapshot(): List<List<List<Any?>>> {
            TransactionManager.current().commit(); TransactionManager.current().entityCache.clear()
            return listOf(Collections, CollectionRegistrationNumbers, CollectionCatalogs, CollectionLedgerLogs).map { table ->
                table.selectAll().orderBy(table.id).map { row -> table.columns.map { row[it] } }
            }
        }
        fun read(entry: String): Any? = when (entry) {
            "get" -> service.get("item-museum")
            "page" -> service.findPage(CollectionQueryVo(), request())
            "rfid" -> service.findByRfid("tag-museum")
            "batch" -> service.batchByRfid(listOf("tag-museum", "tag-other"))
            "empty_batch" -> service.batchByRfid(emptyList())
            "summary" -> service.summary()
            "logs" -> service.ledgerLogs("item-museum")
            "log_page" -> service.ledgerLogPage(request())
            "survey" -> service.surveyStatus(CollectionQueryVo())
            "export" -> service.exportSurvey(CollectionQueryVo())
            else -> error(entry)
        }
        fun newItem() = CollectionVo(name = "新藏品", categoryId = "category", level = EnumRelicLevel.GENERAL,
            source = EnumSource.OLD, acquireDate = java.time.LocalDate.of(2020, 1, 1), quantity = 1, unit = "件")
        fun write(entry: String): Any? = when (entry) {
            "create" -> service.create(newItem())
            "update" -> service.update("item-museum", CollectionDao["item-museum"].toModel().copy(remark = "本次补录"))
            "import" -> service.updateFromImport("item-museum", CollectionDao["item-museum"].toModel().copy(remark = "回导"), "修订")
            "bind" -> service.bindRfid("item-museum", "new-tag")
            "unbind" -> service.unbindRfid("item-museum")
            else -> error(entry)
        }
    }

    @Test fun `main list uses unique descending identity for equal creation times across pages`() = fixture { f ->
        val older = java.time.LocalDateTime.of(2025, 1, 1, 0, 0)
        val newer = older.plusDays(1)
        for (number in listOf(2, 1, 3, 4, 5)) CollectionDao.new("tie-$number") {
            orgId = "museum"; totalRegNo = "TIE-$number"; name = "分页同刻样本-$number"
            createTime = if (number >= 4) newer else older
        }
        val query = CollectionQueryVo().apply { keyword = "分页同刻样本" }
        fun page(number: Int, size: Int): List<String?> {
            val response = f.service.findPage(query, MockHttpServletRequest().apply {
                setParameter("pageNum", number.toString()); setParameter("pageSize", size.toString())
            })
            assertEquals(5L, response.total)
            @Suppress("UNCHECKED_CAST")
            return (response.list as List<CollectionVo>).map { it.id }
        }
        val expected = listOf("tie-5", "tie-4", "tie-3", "tie-2", "tie-1")
        assertEquals(expected, (1..3).flatMap { page(it, 2) })
        assertEquals(emptyList<String>(), page(4, 2))
        assertEquals(expected, page(1, 0))
        assertEquals(expected.take(2), page(1, 2))
    }

    @Test fun `appraisal picker filters ungraded active collections before pagination`() = fixture { f ->
        CollectionDao.new("eligible-store") {
            orgId = "museum"; totalRegNo = "ELIGIBLE"; name = "在库未定级"; lifeStatus = EnumCollectionStatus.IN_STORE
        }
        CollectionDao.new("retired") {
            orgId = "museum"; totalRegNo = "RETIRED"; name = "已注销未定级"; lifeStatus = EnumCollectionStatus.DEREGISTERED
        }
        CollectionDao.new("graded") {
            orgId = "museum"; totalRegNo = "GRADED"; name = "在库已定级"
            lifeStatus = EnumCollectionStatus.IN_STORE; level = EnumRelicLevel.GENERAL
        }
        val query = CollectionQueryVo().apply {
            level = EnumRelicLevel.UNRATED
            lifeStatuses = listOf(EnumCollectionStatus.IN_STORE, EnumCollectionStatus.PENDING)
        }
        val page = f.service.findPage(query, MockHttpServletRequest().apply {
            setParameter("pageNum", "1"); setParameter("pageSize", "30")
        })
        assertEquals(2L, page.total)
        assertEquals(setOf("eligible-store", "item-museum"), (page.list as List<CollectionVo>).mapNotNull { it.id }.toSet())
    }

    @Test fun `status only initial draft resave is included in main ledger correction`() = fixture { f ->
        f.grant(CollectionConst.LEDGER_CREATE)
        val collection = CollectionDao["item-museum"]
        collection.catalogStatus = EnumCatalogStatus.REJECTED
        CollectionCatalogDao.new {
            orgId = collection.orgId
            collectionId = collection.id.value
            catalogStatus = EnumCatalogStatus.REJECTED
            content = CollectionCatalogMapper.fromCollection(collection.toModel())
        }
        f.service.update(collection.id.value, collection.toModel())
        val correction = CollectionLedgerLogDao.all().single { it.field == "initialDraft" }
        assertTrue(correction.before!!.contains("\"catalogStatus\":\"REJECTED\""))
        assertTrue(correction.after!!.contains("\"catalogStatus\":\"DRAFT\""))
        assertEquals(EnumCatalogStatus.DRAFT, collection.catalogStatus)
    }

    private fun fixture(mode: String = "single", body: (Fixture) -> Unit) {
        val context = StaticApplicationContext()
        context.beanFactory.registerSingleton("cacheManager", ConcurrentMapCacheManager()); context.refresh()
        SpringContextHolder().setApplicationContext(context)
        val old = SecurityUser(id = "reader", name = "旧登录", username = "reader", password = "", groupId = "museum",
            groupIds = setOf("museum"), postIds = emptySet(), dataType = 0, roles = setOf(EnumRole.ADMIN.value),
            authorities = listOf("ADMIN", CollectionConst.LEDGER_CREATE, CollectionConst.CROSS_ORG).map(::SimpleGrantedAuthority), enabled = true)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(old, null, old.authorities)
        val path = java.nio.file.Files.createTempFile("collection-main-access-", ".db")
        val database = Database.connect("jdbc:sqlite:$path", driver = "org.sqlite.JDBC")
        try {
            val fixture = transaction(database) {
                originalReaderSchema()
                SchemaUtils.create(CollectionOrgs, Collections, CollectionCategories, Warehouses, CollectionMedias,
                    CollectionAttachments, CollectionOperationLocks, CollectionCatalogs, CollectionCatalogVersions,
                    CollectionRegistrationNumbers, CollectionRetainedLedgers, CollectionLedgerLogs)
                for (org in listOf("museum", "other")) {
                    CollectionOrgDao.new(org) { name = org; code = org; systemGroupId = org }
                    CollectionDao.new("item-$org") {
                        orgId = org; totalRegNo = "REG-$org"; name = "藏品-$org"; rfidTag = "tag-$org"
                        lifeStatus = EnumCollectionStatus.PENDING; catalogStatus = EnumCatalogStatus.DRAFT
                        acquireDate = java.time.LocalDate.of(2020, 1, 1)
                    }
                    CollectionLedgerLogDao.new("log-$org") {
                        orgId = org; collectionId = "item-$org"; totalRegNo = "REG-$org"
                        action = EnumLedgerAction.RESERVE_REG_NO; reason = "原馆记录-$org"; refBizId = "business-$org"
                    }
                }
                originalReader("reader", CollectionConst.SELECT)
                val scope = CollectionScopeResolver(mode, "museum")
                val binder = mock(BusinessAttachmentBinder::class.java)
                val locks = CollectionOperationLockService()
                val catalog = CollectionCatalogService(scope, locks, mock(CollectionGradingService::class.java), mock(AccessionReceiptService::class.java), binder)
                val disclosure = mock(CollectionDisclosureService::class.java)
                Fixture(CollectionService(scope, binder, CollectionRegistrationNumberService(), locks, catalog,
                    mock(CollectionChangeService::class.java), mock(CollectionRetainedLedgerService::class.java), disclosure), scope, disclosure)
            }
            transaction(database) { body(fixture) }
        } finally { SecurityContextHolder.clearContext(); context.close(); java.nio.file.Files.deleteIfExists(path) }
    }

    @ParameterizedTest @ValueSource(strings = ["single", "multi"])
    fun `main detail and list do not supplement foreign category or location names`(mode: String) = fixture(mode) { r ->
        CollectionCategoryDao.new("foreign-category") { orgId = "other"; name = "外馆分类"; parentId = "0"; parentIds = "0," }
        WarehouseDao.new("foreign-location") { orgId = "other"; name = "外馆库位"; parentId = "0"; parentIds = "0," }
        val item = CollectionDao["item-museum"]
        item.categoryId = "foreign-category"; item.warehouseId = "foreign-location"
        if (mode == "multi") r.grant(CollectionConst.SELECT, CollectionConst.CROSS_ORG)
        val detail = r.service.get("item-museum")!!
        val row = (r.service.findPage(CollectionQueryVo(), r.request()).list as List<CollectionVo>).single { it.id == "item-museum" }
        for (value in listOf(detail, row)) {
            assertNull(value.categoryName); assertNull(value.warehouseName)
            assertEquals("foreign-category", value.categoryId); assertEquals("foreign-location", value.warehouseId)
        }
    }

    @ParameterizedTest @ValueSource(strings = ["local", "standard", "missing"])
    fun `main names retain local and national classification without inventing missing references`(kind: String) = fixture { r ->
        if (kind != "missing") {
            CollectionCategoryDao.new("category") { orgId = if (kind == "standard") null else "museum"; name = "本件分类"; parentId = "0"; parentIds = "0," }
            WarehouseDao.new("location") { orgId = "museum"; name = "本馆库位"; parentId = "0"; parentIds = "0," }
        }
        CollectionDao["item-museum"].apply { categoryId = "category"; warehouseId = "location" }
        val detail = r.service.get("item-museum")!!
        val row = (r.service.findPage(CollectionQueryVo(), r.request()).list as List<CollectionVo>).single()
        for (value in listOf(detail, row)) {
            assertEquals(if (kind == "missing") null else "本件分类", value.categoryName)
            assertEquals(if (kind == "missing") null else "本馆库位", value.warehouseName)
        }
    }

    @ParameterizedTest @ValueSource(strings = ["get", "page", "rfid", "batch", "empty_batch", "summary", "logs", "log_page", "survey", "export"])
    fun `all main reads reject revoked current permissions and disabled accounts despite old admin login`(entry: String) = fixture { r ->
        r.read(entry); r.grant(); assertThrows(BadRequestException::class.java) { r.read(entry) }
        r.grant(CollectionConst.SELECT); UserDao["reader"].enabled = false
        assertThrows(BadRequestException::class.java) { r.read(entry) }
    }

    @ParameterizedTest @ValueSource(strings = ["single", "multi"])
    fun `page count tags statistics and survey share the current museum boundary`(mode: String) = fixture(mode) { r ->
        val page = r.service.findPage(CollectionQueryVo(), r.request())
        assertEquals(1L, page.total); assertEquals(listOf("item-museum"), (page.list as List<CollectionVo>).map { it.id })
        assertEquals(0, r.service.summary().total)
        assertEquals(1, r.service.summary().pendingRegistrationTotal)
        val batch = r.service.batchByRfid(listOf("tag-museum", "tag-other"))
        assertEquals(listOf("item-museum"), batch.matched.map { it.id }); assertEquals(listOf("tag-other"), batch.unknown)
        assertThrows(BadRequestException::class.java) { r.service.get("item-other") }
        assertThrows(BadRequestException::class.java) { r.service.findByRfid("tag-other") }
        assertThrows(BadRequestException::class.java) { r.service.findPage(CollectionQueryVo().apply { orgId = "other" }, r.request()) }
        val excluded = r.service.surveyStatus(CollectionQueryVo())["excluded"] as List<*>
        assertEquals(1, excluded.size)
        CollectionDao["item-museum"].registeredAt = java.time.LocalDateTime.now()
        CollectionDao["item-other"].registeredAt = java.time.LocalDateTime.now()
        assertEquals(1, r.service.exportSurvey(CollectionQueryVo()).size)
    }

    @ParameterizedTest @ValueSource(strings = ["moved", "configured", "museum_disabled"])
    fun `single museum reads follow current membership configuration and museum status`(damage: String) = fixture { r ->
        r.damage(damage)
        if (damage == "moved") r.grant(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { r.service.get("item-museum") }
        // A configured different museum is still outside this reader's current membership.
        if (damage != "moved") assertEquals(0L, r.service.findPage(CollectionQueryVo(), r.request()).total)
    }

    @Test fun `multi museum follows current membership and explicit historical cross access`() = fixture("multi") { r ->
        UserDao["reader"].groupId = "other"
        assertEquals("item-other", (r.service.findPage(CollectionQueryVo(), r.request()).list as List<CollectionVo>).single().id)
        CollectionOrgDao["museum"].orgStatus = EnumOrgStatus.DISABLED
        r.grant(CollectionConst.SELECT, CollectionConst.CROSS_ORG)
        assertEquals(2L, r.service.findPage(CollectionQueryVo(), r.request()).total)
        assertEquals("item-museum", r.service.get("item-museum")!!.id)
        assertThrows(BadRequestException::class.java) { r.write("update") }
    }

    @Test fun `ledger history retains original museum records without leaking current foreign names or foreign logs`() = fixture { r ->
        CollectionLedgerLogDao["log-other"].collectionId = "item-museum"
        val detailLogs = r.service.ledgerLogs("item-museum")
        assertEquals(listOf("log-museum"), detailLogs.map { it.id })
        assertEquals("business-museum", detailLogs.single().refBizId)
        CollectionLedgerLogDao["log-museum"].collectionId = "item-other"
        val page = r.service.ledgerLogPage(r.request())
        assertEquals(1L, page.total); assertEquals("REG-museum", (page.list as List<LedgerLogVo>).single().totalRegNo)
        assertEquals("business-museum", (page.list as List<LedgerLogVo>).single().refBizId)
        assertNull((page.list as List<LedgerLogVo>).single().collectionName)
        assertEquals(0L, r.service.ledgerLogPage(r.request("藏品-other")).total)
        assertEquals(1L, r.service.ledgerLogPage(r.request("原馆记录-museum")).total)
        assertEquals(1L, r.service.ledgerLogPage(r.request("business-museum")).total)
        assertEquals(0L, r.service.ledgerLogPage(r.request("business-other")).total)
        assertEquals(1L, r.service.ledgerLogPage(r.request().apply { setParameter("refBizId", "business-museum") }).total)
        assertEquals(0L, r.service.ledgerLogPage(r.request().apply { setParameter("refBizId", "business-other") }).total)
        r.grant(CollectionConst.SELECT, CollectionConst.CROSS_ORG)
        // Single museum remains fixed even after granting a cross-museum read permission.
        assertNull((r.service.ledgerLogPage(r.request()).list as List<LedgerLogVo>).single().collectionName)
    }

    @Test fun `ledger page uses stable identity order when log times are equal`() = fixture { r ->
        val createdAt = java.time.LocalDateTime.of(2026, 1, 1, 0, 0)
        for (number in 1..5) CollectionLedgerLogDao.new("log-tie-$number") {
            orgId = "museum"; collectionId = "item-museum"; action = EnumLedgerAction.CORRECT
            refBizId = "same-reference"; createTime = createdAt
        }
        fun page(number: Int, size: Int): List<String?> {
            val response = r.service.ledgerLogPage(r.request().apply {
                setParameter("refBizId", "same-reference")
                setParameter("pageNum", number.toString())
                setParameter("pageSize", size.toString())
            })
            assertEquals(5L, response.total)
            @Suppress("UNCHECKED_CAST")
            return (response.list as List<LedgerLogVo>).map { it.id }
        }
        val expected = (5 downTo 1).map { "log-tie-$it" }
        assertEquals(expected, (1..3).flatMap { page(it, 2) })
        assertEquals(expected, page(1, 0))
    }

    @ParameterizedTest @CsvSource("revoked,create", "revoked,update", "revoked,import", "revoked,bind", "revoked,unbind",
        "disabled,create", "disabled,update", "disabled,import", "disabled,bind", "disabled,unbind",
        "moved,create", "moved,update", "moved,import", "moved,bind", "moved,unbind",
        "configured,create", "configured,update", "configured,import", "configured,bind", "configured,unbind",
        "museum_disabled,create", "museum_disabled,update", "museum_disabled,import", "museum_disabled,bind", "museum_disabled,unbind",
        "storage,create", "storage,update", "storage,import", "storage,bind", "storage,unbind",
        "keeper,create", "keeper,update", "keeper,import", "keeper,bind", "keeper,unbind")
    fun `current ledger authorization and separation are required before any mutation`(damage: String, entry: String) = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT)
        r.damage(damage); val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { r.write(entry) }; assertEquals(before, r.snapshot())
    }

    @ParameterizedTest @ValueSource(strings = ["create", "createForOrg"])
    fun `new manual number uses the same identity in collection reservation and ledger`(entry: String) = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val historicalNumber = " OLD-0002 "
        CollectionDao["item-other"].totalRegNo = historicalNumber
        // 首尾空白与既有占号规则一致；号码内部空格、前导零和馆方前缀保留。
        val number = "馆 甲-0001"
        fun create(input: String): String {
            val value = r.newItem().copy(totalRegNo = input)
            return if (entry == "create") r.service.create(value) else r.service.createForOrg("museum", value)
        }
        val id = create(" \t$number\n ")
        r.snapshot()
        assertEquals(number, CollectionDao[id].totalRegNo)
        val reservation = CollectionRegistrationNumberDao.find { CollectionRegistrationNumbers.collectionId eq id }.single()
        assertEquals(number, reservation.totalRegNo)
        assertEquals(EnumRegistrationNoState.USED, reservation.state)
        val log = CollectionLedgerLogDao.find { CollectionLedgerLogs.collectionId eq id }.single()
        assertEquals(number, log.totalRegNo)
        assertEquals(number, log.after)
        assertEquals(EnumLedgerAction.RESERVE_REG_NO, log.action)
        assertEquals(EnumCatalogStatus.DRAFT, CollectionDao[id].catalogStatus)
        assertNull(CollectionDao[id].registeredAt)
        val before = r.snapshot()
        assertThrows(BadRequestException::class.java) { create(number) }
        assertThrows(BadRequestException::class.java) { create(" $number ") }
        assertEquals(before, r.snapshot())
        assertEquals("REG-museum", CollectionDao["item-museum"].totalRegNo)
        assertEquals(historicalNumber, CollectionDao["item-other"].totalRegNo)
    }

    @ParameterizedTest @ValueSource(strings = ["update", "import"])
    fun `historical exact number allows editing without changing its identity`(entry: String) = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val number=" REG-museum "
        CollectionDao["item-museum"].totalRegNo=number
        r.snapshot()
        r.write(entry)
        r.snapshot()
        assertEquals(number,CollectionDao["item-museum"].totalRegNo)
        assertEquals(if(entry=="update")"本次补录" else "回导",CollectionDao["item-museum"].remark)
        val before=r.snapshot()
        val request=CollectionDao["item-museum"].toModel().copy(totalRegNo=number.trim(),remark="不得应用")
        assertThrows(BadRequestException::class.java) {
            if(entry=="update")r.service.update("item-museum",request) else r.service.updateFromImport("item-museum",request,"改号")
        }
        assertEquals(before,r.snapshot())
    }

    @Test fun `current registrar can create update import and bind tags without stale login duties`() = fixture { r ->
        r.grant(CollectionConst.LEDGER_CREATE)
        val id = r.service.create(r.newItem()); assertEquals("museum", CollectionDao[id].orgId)
        r.write("update"); assertEquals("本次补录", CollectionDao["item-museum"].remark)
        r.write("import"); assertEquals("回导", CollectionDao["item-museum"].remark)
        CollectionDao["item-museum"].lifeStatus = EnumCollectionStatus.IN_STORE
        r.write("bind"); assertEquals("new-tag", CollectionDao["item-museum"].rfidTag)
        r.write("unbind"); assertNull(CollectionDao["item-museum"].rfidTag)
    }

    @Test fun `create uses current primary museum and internal workflow entry does not require manual ledger duty`() = fixture("multi") { r ->
        r.grant(CollectionConst.LEDGER_CREATE); UserDao["reader"].groupId = "other"
        assertEquals("other", CollectionDao[r.service.create(r.newItem())].orgId)
        r.grant()
        assertEquals("museum", CollectionDao[r.service.createForOrg("museum", r.newItem())].orgId)
    }

    @Test fun `actual administrator still needs current ledger duty and cannot write outside configured museum`() = fixture { r ->
        Roles.insert { it[Roles.id] = EnumRole.ADMIN.value; it[code] = "ACTUAL_ADMIN"; it[name] = "实际管理员" }
        RoleUsers.insert { it[userId] = "reader"; it[roleId] = EnumRole.ADMIN.value }
        r.grant()
        assertThrows(BadRequestException::class.java) { r.write("create") }
        actorRole("reader", CollectionConst.ROLE_LEDGER_CUSTODIAN)
        val id = r.service.create(r.newItem()); assertEquals("museum", CollectionDao[id].orgId)
        assertThrows(BadRequestException::class.java) { r.service.update("item-other", CollectionDao["item-other"].toModel()) }
        actorRole("reader", CollectionConst.ROLE_KEEPER)
        assertThrows(BadRequestException::class.java) { r.write("update") }
    }

    @Test fun `stat correction and publication permissions keep their distinct endpoint contracts`() = fixture { r ->
        r.grant(CollectionConst.STAT)
        r.service.summary(); r.service.surveyStatus(CollectionQueryVo()); r.service.exportSurvey(CollectionQueryVo())
        assertThrows(BadRequestException::class.java) { r.service.get("item-museum") }
        r.grant(CollectionConst.LEDGER_CORRECT)
        assertEquals(1L, r.service.ledgerLogPage(r.request()).total)
        assertThrows(BadRequestException::class.java) { r.write("update") }
        r.grant(CollectionConst.PUBLISH)
        CollectionDao["item-museum"].catalogStatus = EnumCatalogStatus.APPROVED
        CollectionDao["item-museum"].lifeStatus = EnumCollectionStatus.IN_STORE
        `when`(r.disclosure.enableLatestResource("item-museum")).thenReturn("published")
        assertEquals("published", r.service.publishCms("item-museum"))
        r.grant(CollectionConst.SELECT)
        assertThrows(BadRequestException::class.java) { r.service.publishCms("item-museum") }
        verify(r.disclosure, times(1)).enableLatestResource("item-museum")
    }
}
