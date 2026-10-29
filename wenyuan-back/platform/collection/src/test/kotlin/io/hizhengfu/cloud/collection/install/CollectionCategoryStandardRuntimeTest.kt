package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.CollectionCategoryStandard
import io.hizhengfu.cloud.collection.service.CollectionCategoryPolicy
import io.hizhengfu.cloud.collection.service.CollectionCategoryService
import io.hizhengfu.cloud.common.domain.GenericUpdateSort
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockHttpServletRequest
import java.nio.file.Path

class CollectionCategoryStandardRuntimeTest {
    @TempDir lateinit var dir: Path
    private fun fixture(body: () -> Unit) {
        val db = Database.connect("jdbc:sqlite:${dir.resolve("categories.db")}", "org.sqlite.JDBC")
        try { transaction(db) { SchemaUtils.create(CollectionCategories); body() } }
        finally { TransactionManager.closeAndUnregister(db) }
    }

    @Test fun `directory follows table D1 including combined categories and exact sequence`() = fixture {
        CollectionCategoryInitializer.apply()
        val rows = CollectionCategoryService(CollectionScopeResolver("single", "museum")).findAll()
        assertEquals((1..35).map { it.toString().padStart(2, '0') }, rows.map { it.code })
        // 来自附件第19、20页表D.1，不沿用原系统的一普常用分类。
        assertEquals("玉石器、宝石|陶器|瓷器|铜器|金银器|铁器、其他金属器|漆器|雕塑、造像|石器、石刻、砖瓦|书法、绘画|文具|甲骨|玺印符牌|钱币|牙骨角器|竹木雕|家具|珐琅器|织绣|古籍善本|碑帖拓本|武器|邮品|文件、宣传品|档案文书|名人遗物|玻璃器|乐器、法器|皮革|音像制品|票据|交通、运输工具|度量衡器|标本、化石|其他".split('|'), rows.map { it.name })
        assertTrue(rows.all { it.orgId == null && it.parentId == "0" })
        assertTrue(rows.all { CollectionCategoryPolicy.valid(it.id) })
    }

    @Test fun `development upgrade replaces old directory removes extras and is idempotent`() = fixture {
        CollectionCategoryDao.new("old-ten") { code="10"; name="珐琅器"; parentId="0" }
        CollectionCategoryDao.new("custom") { code="CUSTOM"; name="革命文物"; orgId="museum" }
        CollectionCategoryDao.new("duplicate") { code="10"; name="重复分类"; orgId="museum" }
        CollectionCategoryInitializer.apply()
        assertEquals("书法、绘画", CollectionCategoryDao["old-ten"].name)
        assertNull(CollectionCategoryDao.findById("custom"))
        assertNull(CollectionCategoryDao.findById("duplicate"))
        val ids = CollectionCategoryDao.all().map { it.id.value }.toSet()
        CollectionCategoryInitializer.apply()
        assertEquals(35, CollectionCategoryDao.all().count())
        assertEquals(ids, CollectionCategoryDao.all().map { it.id.value }.toSet())
        assertTrue(CollectionCategoryDao.all().all(CollectionCategoryPolicy::isStandard))
    }

    @Test fun `directory is read only even through direct write endpoints`() = fixture {
        CollectionCategoryInitializer.apply()
        val service = CollectionCategoryService(CollectionScopeResolver("multi", "museum"))
        val id = service.findAll().first().id!!
        assertThrows(BadRequestException::class.java) { service.create(CollectionCategoryVo(name="自定义")) }
        assertThrows(BadRequestException::class.java) { service.update(id, CollectionCategoryVo(name="变更名称")) }
        assertThrows(BadRequestException::class.java) { service.updateSort(emptyList<GenericUpdateSort>()) }
        assertThrows(BadRequestException::class.java) { service.delete(id) }
        assertEquals(35, service.findAll().size)
    }

    @Test fun `input guard and listing reject arbitrary old categories and mismatched table numbers`() = fixture {
        CollectionCategoryInitializer.apply()
        CollectionCategoryDao.new("old") { code="custom"; name="书法"; parentId="0" }
        assertDoesNotThrow { CollectionCategoryPolicy.requireOptional(null) }
        assertThrows(BadRequestException::class.java) { CollectionCategoryPolicy.requireSelected(null) }
        assertThrows(BadRequestException::class.java) { CollectionCategoryPolicy.requireOptional("old") }
        assertThrows(BadRequestException::class.java) { CollectionCategoryPolicy.requireOptional("missing") }
        val service=CollectionCategoryService(CollectionScopeResolver("single", "museum"))
        assertFalse(service.findAll().any { it.id == "old" })
        val changed=CollectionCategoryDao.all().first { it.code=="14" }
        changed.name="货币"
        assertThrows(BadRequestException::class.java) { CollectionCategoryPolicy.requireSelected(changed.id.value) }
        assertEquals(34, service.findAll().size)
    }

    @Test fun `management search uses standard names and appendix order`() = fixture {
        CollectionCategoryInitializer.apply()
        val service=CollectionCategoryService(CollectionScopeResolver("single", "museum"))
        val request=MockHttpServletRequest().apply { addParameter("pageSize","0") }
        @Suppress("UNCHECKED_CAST")
        val rows=service.findPage("书法", request).list as List<CollectionCategoryVo>
        assertEquals(listOf("书法、绘画"),rows.map { it.name })
        assertEquals("10",rows.single().code)
    }
}
