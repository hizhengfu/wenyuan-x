package io.hizhengfu.cloud.collection.install

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.sys.system.dao.Roles
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert

internal val flowRoleCodes = listOf(CollectionConst.ROLE_APPRAISAL_COMMITTEE, CollectionConst.ROLE_STORAGE_HEAD, CollectionConst.ROLE_VICE_DIRECTOR)
internal fun seedFlowRoles() {
    SchemaUtils.create(Roles)
    flowRoleCodes.forEachIndexed { index, code ->
        Roles.insert { it[id] = EntityID("museum-role-$index", Roles); it[Roles.code] = code; it[name] = code }
    }
}
