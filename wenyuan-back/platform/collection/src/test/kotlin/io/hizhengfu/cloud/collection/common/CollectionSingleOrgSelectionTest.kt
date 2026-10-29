package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CollectionSingleOrgSelectionTest {
    private fun fixture(body: () -> Unit) {
        val db = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(CollectionOrgs)
            body()
        }
    }

    private fun museum(id: String) = CollectionOrgDao.new(id) { code = id; name = id }

    @Test fun `single museum remains usable without explicit configuration`() = fixture {
        museum("only")
        assertEquals("only", CollectionScopeResolver("single", "").currentOrgId())
    }

    @Test fun `multiple museums require an explicit choice independent of insertion order`() = fixture {
        museum("second"); museum("first")
        val failure = assertThrows(BadRequestException::class.java) {
            CollectionScopeResolver("single", "").currentOrgId()
        }
        assertTrue(failure.message!!.contains("后端系统设置"))
        assertEquals(2L, CollectionOrgDao.count())
        assertEquals(setOf("first", "second"), CollectionOrgDao.all().map { it.id.value }.toSet())
    }

    @Test fun `explicit default selects the intended museum among multiple records`() = fixture {
        museum("first"); museum("configured")
        assertEquals("configured", CollectionScopeResolver("single", "configured").currentOrgId())
    }

    @Test fun `disabled historic museum is not silently ignored to invent a default`() = fixture {
        museum("history").orgStatus = EnumOrgStatus.DISABLED
        museum("active")
        assertThrows(BadRequestException::class.java) { CollectionScopeResolver("single", " ").currentOrgId() }
    }

    @Test fun `empty installation keeps its initialization error`() = fixture {
        val failure = assertThrows(BadRequestException::class.java) { CollectionScopeResolver("single", "").currentOrgId() }
        assertEquals("尚未初始化收藏单位", failure.message)
    }
}
