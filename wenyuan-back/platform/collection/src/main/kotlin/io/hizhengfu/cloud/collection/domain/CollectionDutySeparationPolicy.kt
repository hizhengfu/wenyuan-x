package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.common.exception.BadRequestException

/** 《博物馆藏品管理办法》岗位不相容：总账登记人员不得同时执行库房保管操作。 */
object CollectionDutySeparationPolicy {
    fun requireLedgerOperation(permissions: Collection<String>, roles: Collection<String> = emptySet()) {
        if (CollectionConst.ROLE_LEDGER_CUSTODIAN !in roles && permissions.none { it in setOf(CollectionConst.LEDGER_CREATE, CollectionConst.LEDGER_CORRECT) }) throw BadRequestException("请明确配置总登记账岗位或登记权限，管理员身份不能代替实际职责")
        if (CollectionConst.ROLE_KEEPER in roles || permissions.any { it == CollectionConst.STORAGE || it == CollectionConst.WAREHOUSE_MANAGE }) {
            throw BadRequestException("总账登记与库房保管岗位不相容：当前账号含库房权限，不能执行总账登记/更正")
        }
    }

    fun requireStorageOperation(permissions: Collection<String>, roles: Collection<String> = emptySet()) {
        if (CollectionConst.ROLE_KEEPER !in roles && permissions.none { it in setOf(CollectionConst.STORAGE, CollectionConst.WAREHOUSE_MANAGE) }) throw BadRequestException("请明确配置库房保管岗位或操作权限，管理员身份不能代替实际职责")
        if (CollectionConst.ROLE_LEDGER_CUSTODIAN in roles || permissions.any { it == CollectionConst.LEDGER_CREATE || it == CollectionConst.LEDGER_CORRECT }) {
            throw BadRequestException("总账登记与库房保管岗位不相容：当前账号含总账权限，不能执行出入库")
        }
    }

}
