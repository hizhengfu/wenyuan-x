package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import org.sqlite.SQLiteDataSource
import java.nio.file.Path

/** Exercise the actual transaction boundary: failed metadata updates must roll back code and reservations together. */
class CollectionOrgCodeRuntimeTest {
    @TempDir lateinit var root: Path
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    private class Runtime(val context: AnnotationConfigApplicationContext, val tx: TransactionTemplate) {
        val codes = context.getBean(CollectionOrgCodeService::class.java)
        val orgs = context.getBean(CollectionOrgService::class.java)
        fun <T> db(body: () -> T): T = tx.execute { body() }
        fun change(code: String, hash: String = codes.context("museum").stateHash) = codes.correct("museum",
            CollectionOrgCodeCorrectionVo(code, "核对机构资料后更正"), hash)
        fun use() = db { WarehouseDao.new("warehouse") { orgId="museum"; name="保管库" } }
        fun snapshot() = db { listOf(CollectionOrgs, CollectionOrgCodes, CollectionOrgCodeChanges).map { table ->
            table.selectAll().map { row -> table.columns.map { row[it] } }
        } }
    }

    private fun fixture(body: (Runtime) -> Unit) {
        val user = SecurityUser(id="reader", name="登录快照", username="reader", password="", groupId="museum",
            groupIds=setOf("museum"), postIds=emptySet(), dataType=0, roles=emptySet(), authorities=emptyList(), enabled=true)
        SecurityContextHolder.getContext().authentication=UsernamePasswordAuthenticationToken(user,null,user.authorities)
        val manager=SpringTransactionManager(SQLiteDataSource().apply { url="jdbc:sqlite:${root.resolve("org.db")}" }, DatabaseConfig {})
        AnnotationConfigApplicationContext().use { c ->
            c.beanFactory.registerSingleton("transactionManager",manager)
            c.beanFactory.registerSingleton("scope",CollectionScopeResolver("single","museum"))
            c.register(Transactions::class.java,CollectionOrgCodeService::class.java,CollectionOrgService::class.java)
            c.refresh()
            val r=Runtime(c,TransactionTemplate(manager))
            try {
                r.db {
                    originalReaderSchema(); originalReader("reader",CollectionConst.ORG_MANAGE)
                    SchemaUtils.create(CollectionOrgs,CollectionOrgCodes,CollectionOrgCodeChanges,Warehouses,Collections)
                    listOf("museum","other").forEach { id -> CollectionOrgDao.new(id) { name=id;code=id;systemGroupId=id } }
                }
                body(r)
            } finally { SecurityContextHolder.clearContext() }
        }
    }

    @Test fun `unused code edits persist with history and cannot release an old code to another institution`() = fixture { r ->
        val initial=r.codes.context("museum")
        assertFalse(initial.used); assertTrue(initial.canEdit)
        r.orgs.update("museum",CollectionOrgVo(code=" NEW ",name="新名称",systemGroupId="museum",codeStateHash=initial.stateHash))
        val saved=r.codes.context("museum")
        assertEquals("NEW",saved.currentCode); assertEquals("museum",saved.history.single().oldCode)
        assertFalse(saved.history.single().correction); assertEquals("reader",saved.history.single().operatorId)
        r.db { assertEquals("新名称",CollectionOrgDao["museum"].name) }
        assertThrows(BadRequestException::class.java) { r.orgs.create(CollectionOrgVo(code="museum",name="另一单位")) }
        r.db {
            assertEquals(setOf("museum","NEW"),CollectionOrgCodeRegistry.acceptedCodes("museum"))
            assertFalse(CollectionOrgCodeRegistry.matches("other","museum"))
            CollectionOrgCodeRegistry.seedCurrentCodes(); CollectionOrgCodeRegistry.seedCurrentCodes()
            assertEquals(3,CollectionOrgCodes.selectAll().count())
        }
    }

    @Test fun `business use requires a reasoned correction and preserves registration and exchange codes`() = fixture { r ->
        r.use()
        r.db {
            CollectionOrgDao["museum"].exchangeCode="12345678901234"
            CollectionDao.new("item") {orgId="museum";name="原藏品";totalRegNo="1：2"}
        }
        val initial=r.codes.context("museum"); assertTrue(initial.used)
        assertThrows(BadRequestException::class.java) {r.orgs.update("museum",CollectionOrgVo(code="NEW",codeStateHash=initial.stateHash))}
        assertThrows(BadRequestException::class.java) {r.codes.correct("museum",CollectionOrgCodeCorrectionVo("NEW","  "),initial.stateHash)}
        val changed=r.change("NEW")
        assertTrue(changed.history.single().correction)
        r.db {
            assertEquals("1：2",CollectionDao["item"].totalRegNo); assertEquals("museum",CollectionDao["item"].orgId)
            assertEquals("12345678901234",CollectionOrgDao["museum"].exchangeCode)
        }
        assertThrows(BadRequestException::class.java) {r.change("AGAIN",initial.stateHash)}
        r.change("museum")
        assertThrows(BadRequestException::class.java) {r.change("NEW",initial.stateHash)}
        assertEquals(2,r.codes.context("museum").history.size)
    }

    @Test fun `code and audit reservations roll back if later metadata validation fails`() = fixture { r ->
        val before=r.snapshot(); val hash=r.codes.context("museum").stateHash
        assertThrows(BadRequestException::class.java) {
            r.orgs.update("museum",CollectionOrgVo(code="NEW",name="不应保存",systemGroupId="missing",codeStateHash=hash))
        }
        assertEquals(before,r.snapshot())
        assertThrows(BadRequestException::class.java) {r.change("other")}
        assertEquals(before,r.snapshot())
    }

    @Test fun `new business invalidates the unused editing receipt`() = fixture { r ->
        val hash=r.codes.context("museum").stateHash
        r.use()
        assertThrows(BadRequestException::class.java) {r.orgs.update("museum",CollectionOrgVo(code="NEW",codeStateHash=hash))}
        assertThrows(BadRequestException::class.java) {r.change("NEW",hash)}
        assertEquals("museum",r.codes.context("museum").currentCode)
    }

    @ParameterizedTest @ValueSource(strings=["revoked","disabled","moved"])
    fun `current duties and organization are rechecked before writing`(damage:String) = fixture { r ->
        val hash=r.codes.context("museum").stateHash
        r.db { when(damage) {
            "revoked" -> RolePermissions.deleteAll()
            "disabled" -> UserDao["reader"].enabled=false
            "moved" -> {UserDao["reader"].groupId="other";actorRole("reader","CROSS",setOf(CollectionConst.CROSS_ORG))}
        } }
        assertThrows(BadRequestException::class.java) {r.change("NEW",hash)}
        if(damage=="moved") assertFalse(r.codes.context("museum").canEdit)
        r.db {assertEquals("museum",CollectionOrgDao["museum"].code);assertEquals(0,CollectionOrgCodeChanges.selectAll().count())}
    }
}
