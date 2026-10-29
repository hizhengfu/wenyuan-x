package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.collection.domain.EnumOrgStatus
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.Configs
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class CollectionDefaultOrgSettingsTest {
    private fun fixture(body: (CollectionScopeResolver) -> Unit) {
        val db = Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.create(Configs, CollectionOrgs)
            CollectionDefaultOrgSettings.seed()
            AnnotationConfigApplicationContext().use { context ->
                context.environment.propertySources.addFirst(MapPropertySource("legacy", mapOf(
                    "collection.mode" to "single", "collection.default-org-id" to "legacy-file-choice",
                )))
                context.register(CollectionScopeResolver::class.java, CollectionDefaultOrgSettings::class.java)
                context.refresh()
                body(context.getBean(CollectionScopeResolver::class.java))
            }
        }
    }

    private fun museum(id: String) = CollectionOrgDao.new(id) { code = id; name = id }
    private fun select(id: String) = Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[content] = id }

    @Test fun `Spring selects the only backend museum and ignores old application property`() = fixture { scope ->
        museum("only")
        assertEquals("only", scope.currentOrgId())
    }

    @Test fun `backend change applies without restarting the Spring resolver`() = fixture { scope ->
        museum("first"); museum("second")
        assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
        select("first"); assertEquals("first", scope.currentOrgId())
        select("second"); assertEquals("second", scope.currentOrgId())
    }

    @Test fun `missing or disabled selected museum cannot silently fall back`() = fixture { scope ->
        museum("active"); museum("disabled").orgStatus = EnumOrgStatus.DISABLED
        select("missing"); assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
        select("disabled"); assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
        select("active"); assertEquals("active", scope.currentOrgId())
        Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[enable] = false }
        assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
    }

    @Test fun `automatic default rejects empty or disabled installation`() = fixture { scope ->
        assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
        museum("disabled").orgStatus = EnumOrgStatus.DISABLED
        assertThrows(BadRequestException::class.java) { scope.currentOrgId() }
    }

    @Test fun `seeding preserves administrators saved default and switch`() = fixture { scope ->
        museum("saved"); select("saved")
        CollectionDefaultOrgSettings.seed()
        assertEquals("saved", scope.currentOrgId())
        Configs.update({ Configs.name eq CollectionDefaultOrgSettings.KEY }) { it[enable] = false }
        CollectionDefaultOrgSettings.seed()
        val setting = Configs.selectAll().single()
        assertEquals("saved", setting[Configs.content]); assertFalse(setting[Configs.enable])
        assertEquals("STRING", setting[Configs.fieldType]); assertFalse(setting[Configs.required]!!)
    }
}
