package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.dao.CollectionOrgVo
import io.hizhengfu.cloud.collection.dao.CollectionOrgCodes
import io.hizhengfu.cloud.collection.dao.CollectionOrgCodeChanges
import io.hizhengfu.cloud.collection.dao.CollectionOrgs
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.sys.system.dao.GroupDao
import io.hizhengfu.cloud.sys.system.dao.Groups
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CollectionOrgSystemGroupMappingTest {

    @Test
    fun `collection organization persists only an existing authorization group`() {
        val database = Database.connect(
            "jdbc:sqlite:file:collection-org-system-group?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC",
        )
        val service = CollectionOrgService(CollectionScopeResolver("single", ""))

        transaction(database) {
            SchemaUtils.create(Groups, CollectionOrgs, CollectionOrgCodes, CollectionOrgCodeChanges)
            GroupDao.new("GROUP-1") {
                name = "博物馆机构"
                type = '1'
                parentId = "0"
                parentIds = "0,"
                isLeaf = true
            }

            val id = service.create(CollectionOrgVo(code = "MUSEUM", name = "博物馆", systemGroupId = "GROUP-1"))

            assertEquals("GROUP-1", CollectionOrgDao.findById(id)?.systemGroupId)
            assertThrows(BadRequestException::class.java) {
                service.create(CollectionOrgVo(code = "OTHER", name = "其他馆", systemGroupId = "MISSING"))
            }
        }
    }
}
