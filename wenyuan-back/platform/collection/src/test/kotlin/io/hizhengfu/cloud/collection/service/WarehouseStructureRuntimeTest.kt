package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.collection.install.CollectionInstall
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.SpringContextHolder
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.*
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path
import java.time.LocalDateTime

class WarehouseStructureRuntimeTest {
    @Test fun `location saves reject duplicate sibling codes and invalid capacities without partial writes`() = fixture { r ->
        val first = r.service.create(WarehouseVo(name="柜一",parentId="old",type=EnumWarehouseType.CABINET,code="1.1.1",capacity=0))
        val before = r.state()
        assertThrows(BadRequestException::class.java) { r.service.create(WarehouseVo(name="柜二",parentId="old",type=EnumWarehouseType.CABINET,code=" 1.1.1 ")) }
        assertThrows(BadRequestException::class.java) { r.service.create(WarehouseVo(name="负容量",capacity=-5)) }
        assertThrows(BadRequestException::class.java) { r.service.update(first,WarehouseVo(name="负容量",code="1.1.1",capacity=-1)) }
        assertThrows(BadRequestException::class.java) { r.service.create(WarehouseVo(name="长".repeat(101))) }
        assertEquals(before,r.state())
        val other = r.service.create(WarehouseVo(name="另一库房的柜",parentId="target",type=EnumWarehouseType.CABINET,code="1.1.1"))
        assertNotEquals(first,other)
        assertThrows(BadRequestException::class.java) { r.service.update(other,WarehouseVo(parentId="old",code="1.1.1")) }
    }
    @TempDir lateinit var dir: Path
    @Configuration(proxyBeanMethods=false) @EnableCaching @EnableTransactionManagement(proxyTargetClass=true) class Transactions
    private class Runtime(val service: WarehouseService,val tx: TransactionTemplate) {
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun state()=db { WarehouseDao.all().map { it.toModel() }.sortedBy { it.id } }
    }
    private fun fixture(body: (Runtime) -> Unit) {
        val manager=SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${dir.resolve("structure.db")}" },DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("cacheManager",ConcurrentMapCacheManager())
            c.beanFactory.registerSingleton("keyGenerator",org.springframework.cache.interceptor.SimpleKeyGenerator())
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("single","museum"))
            c.beanFactory.registerSingleton("attachmentBinder",mock(BusinessAttachmentBinder::class.java))
            c.register(Transactions::class.java,WarehouseService::class.java,CollectionLocationReferenceGuard::class.java)
            c.refresh();SpringContextHolder().setApplicationContext(c)
            val r=Runtime(c.getBean(WarehouseService::class.java),TransactionTemplate(manager))
            r.db {
                SchemaUtils.create(*CollectionInstall("single").initTables().toTypedArray())
            CollectionOrgDao.new("museum") { code="museum";name="测试馆" }
                listOf("old","target").forEach { key -> WarehouseDao.new(key) { orgId="museum";name=key;type=EnumWarehouseType.WAREHOUSE;sceneType=EnumLocationScene.STORAGE;parentId="0";parentIds="0,";isLeaf=key!="old" } }
                WarehouseDao.new("branch") { orgId="museum";name="库区";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId="old";parentIds="0,old,";isLeaf=false }
                WarehouseDao.new("leaf") { orgId="museum";name="下级位置";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId="branch";parentIds="0,old,branch,";isLeaf=true }
            }
            body(r)
        }
    }
    private fun reference(r: Runtime, source: String, location: String) = r.db {
        when(source) {
            "collection" -> CollectionDao.new("item") { orgId="museum";totalRegNo="A1";name="藏品";warehouseId=location }
            "history" -> CollectionPermanentArchiveDao.new("archive") { orgId="other";bizType="HISTORICAL_TEST";bizId="case";phase="RECORDED";snapshot=JsonUtils.toJson(mapOf("warehouseId" to location))!!;digest=CollectionCareEvidence.digest(snapshot) }
            "staffing" -> WarehouseDao[location].staffing=WarehouseStaffingVo(setOf("keeper"),"head","director",LocalDateTime.of(2026,1,1,8,0),"evidence")
        }
    }
    @ParameterizedTest @CsvSource("collection,branch,move","history,branch,move","staffing,branch,move",
        "collection,leaf,move","history,leaf,move","staffing,leaf,move",
        "collection,leaf,type","history,leaf,type","staffing,leaf,type")
    fun `structural changes cannot relocate or reclassify a used subtree`(source: String, location: String, operation: String) = fixture { r ->
        reference(r,source,location);val before=r.state()
        assertThrows(BadRequestException::class.java) {
            r.service.update("branch",WarehouseVo(name="不应保存",parentId=if(operation=="move")"target" else "old",type=if(operation=="type")EnumWarehouseType.WAREHOUSE else EnumWarehouseType.AREA))
        }
        assertEquals(before,r.state())
    }
    @Test fun `moving an unused subtree updates every path and both parents leaf flags`() = fixture { r ->
        r.service.update("branch",WarehouseVo(name="新库区",parentId="target"))
        r.db {
            assertEquals("target",WarehouseDao["branch"].parentId)
            assertEquals("0,target,",WarehouseDao["branch"].parentIds)
            assertEquals("0,target,branch,",WarehouseDao["leaf"].parentIds)
            assertTrue(WarehouseDao["old"].isLeaf)
            assertFalse(WarehouseDao["target"].isLeaf)
        }
    }
    @ParameterizedTest @ValueSource(strings=["collection","history"])
    fun `cleared descendant cache cannot hide actual parent relationships`(source: String) = fixture { r ->
        reference(r,source,"leaf")
        r.db { WarehouseDao["leaf"].parentIds="0," }
        val before=r.state()
        assertThrows(BadRequestException::class.java) { r.service.update("branch",WarehouseVo(parentId="target")) }
        assertEquals(before,r.state())
    }
    @Test fun `parent deletion sees direct children even when their cached paths are missing`() = fixture { r ->
        r.db { WarehouseDao["leaf"].parentIds="0," }
        val before=r.state()
        assertThrows(BadRequestException::class.java) { r.service.delete("branch") }
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["self","descendant","cycle","foreign-child","mixed-child","foreign-ancestor","mixed-ancestor","stale-ancestor","missing-ancestor"])
    fun `invalid structural target or branch rolls back every field`(failure: String) = fixture { r ->
        r.db {
            when(failure) {
                "descendant" -> WarehouseDao["target"].apply { parentId="leaf";parentIds="0," }
                "cycle" -> { WarehouseDao["branch"].parentId="leaf";WarehouseDao["leaf"].parentIds="0," }
                "foreign-child" -> WarehouseDao["leaf"].orgId="other"
                "mixed-child" -> WarehouseDao["leaf"].apply { sceneType=EnumLocationScene.EXHIBITION;type=EnumWarehouseType.EXHIBITION_HALL }
                "foreign-ancestor" -> { WarehouseDao["target"].parentId="old";WarehouseDao["target"].parentIds="0,old,";WarehouseDao["old"].orgId="other" }
                "mixed-ancestor" -> { WarehouseDao["target"].parentId="old";WarehouseDao["target"].parentIds="0,old,";WarehouseDao["old"].sceneType=EnumLocationScene.EXHIBITION }
                "stale-ancestor" -> WarehouseDao["target"].parentIds="0,missing,"
                "missing-ancestor" -> WarehouseDao["target"].parentId="missing"
            }
        }
        val before=r.state()
        assertThrows(BadRequestException::class.java) { r.service.update("branch",WarehouseVo(name="不应保存",parentId=if(failure=="self")"branch" else "target",capacity=100)) }
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["foreign","cycle","missing","stale"])
    fun `creation validates the full existing parent chain before writing`(failure: String) = fixture { r ->
        r.db {
            when(failure) {
                "foreign" -> { WarehouseDao["target"].parentId="old";WarehouseDao["target"].parentIds="0,old,";WarehouseDao["old"].orgId="other" }
                "cycle" -> { WarehouseDao["target"].parentId="old";WarehouseDao["old"].parentId="target" }
                "missing" -> WarehouseDao["target"].parentId="missing"
                "stale" -> WarehouseDao["target"].parentIds="0,missing,"
            }
        }
        val before=r.state()
        assertThrows(BadRequestException::class.java) { r.service.create(WarehouseVo(name="新位置",parentId="target",type=EnumWarehouseType.AREA)) }
        assertEquals(before,r.state())
    }
    @ParameterizedTest @ValueSource(strings=["collection","history"])
    fun `wide subtrees protect references beyond one database parameter batch`(source: String) = fixture { r ->
        r.db { repeat(205) { i -> WarehouseDao.new("wide-${i.toString().padStart(4,'0')}") { orgId="museum";name="空位置$i";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId="branch";parentIds="0,old,branch," } } }
        reference(r,source,"wide-0204");val before=r.state()
        assertThrows(BadRequestException::class.java) { r.service.update("branch",WarehouseVo(parentId="target")) }
        assertEquals(before,r.state())
    }
    @Test fun `unused branch move can rebuild stale descendant paths from actual parent links`() = fixture { r ->
        r.db { WarehouseDao["leaf"].parentIds="0,unrelated," }
        r.service.update("branch",WarehouseVo(parentId="target"))
        r.db { assertEquals("0,target,branch,",WarehouseDao["leaf"].parentIds);assertEquals("branch",WarehouseDao["leaf"].parentId) }
    }
    @Test fun `similar legacy ids are not mistaken for ancestor membership`() = fixture { r ->
        r.db {
            WarehouseDao.new("1") { orgId="museum";name="待移位置";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId="0";parentIds="0," }
            WarehouseDao.new("110") { orgId="museum";name="目标上级";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId="0";parentIds="0," }
            WarehouseDao["target"].apply { parentId="110";parentIds="0,110," }
        }
        r.service.update("1",WarehouseVo(parentId="target"))
        r.db { assertEquals("0,110,target,",WarehouseDao["1"].parentIds) }
    }
    @Test fun `ordinary metadata and empty sibling moves remain possible beside a used location`() = fixture { r ->
        reference(r,"collection","leaf")
        r.service.update("branch",WarehouseVo(name="核实名称",parentId="old",type=EnumWarehouseType.AREA))
        val empty=r.service.create(WarehouseVo(name="未使用兄弟位置",parentId="old",type=EnumWarehouseType.AREA))
        r.service.update(empty,WarehouseVo(parentId="target"))
        r.db { assertEquals("核实名称",WarehouseDao["branch"].name);assertEquals("0,target,",WarehouseDao[empty].parentIds);assertFalse(WarehouseDao["old"].isLeaf) }
    }
    @Test fun `moving an unused subtree to the root retains exact descendant membership`() = fixture { r ->
        r.service.update("branch",WarehouseVo(parentId="0"))
        r.db { assertEquals("0,",WarehouseDao["branch"].parentIds);assertEquals("0,branch,",WarehouseDao["leaf"].parentIds);assertTrue(WarehouseDao["old"].isLeaf) }
    }
    @ParameterizedTest @ValueSource(strings=["create","move"])
    fun `overlong derived paths fail before any tree changes`(operation: String) = fixture { r ->
        val target=r.db {
            var previous="0";var path="0,"
            repeat(32) { i ->
                val key="deep-${i.toString().padStart(2,'0')}-"+"x".repeat(55)
                WarehouseDao.new(key) { orgId="museum";name="深层位置$i";type=EnumWarehouseType.AREA;sceneType=EnumLocationScene.STORAGE;parentId=previous;parentIds=path }
                previous=key;path+=key+","
            };previous
        }
        val before=r.state()
        assertThrows(BadRequestException::class.java) {
            if(operation=="create")r.service.create(WarehouseVo(name="超长位置",parentId=target,type=EnumWarehouseType.AREA))
            else r.service.update("branch",WarehouseVo(parentId=target))
        }
        assertEquals(before,r.state())
    }
}
