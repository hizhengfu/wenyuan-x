package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.core.eq
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.javatime.datetime

/** 当前和历次代码归属于同一个稳定的收藏单位 ID，旧代码不得借给其他单位。 */
object CollectionOrgCodes : OrgAuditUIDTable("col_org_code") {
    val code = varchar("code", 32).uniqueIndex(shortIndexName("code", unique = true)).comment("当前或历史收藏单位代码")
}

/** 仅追加，不覆写旧代码记录或业务快照。 */
object CollectionOrgCodeChanges : OrgAuditUIDTable("col_org_code_change") {
    val oldCode = varchar("old_code", 32).comment("原收藏单位代码")
    val newCode = varchar("new_code", 32).comment("新收藏单位代码")
    val reason = varchar("reason", 500).comment("修改或更正原因")
    val correction = bool("correction").comment("是否有业务后的留痕更正")
    val operatorId = varchar("operator_id", 64).comment("实际操作人")
    val operatedAt = datetime("operated_at").comment("实际操作时间")
}

object CollectionOrgCodeRegistry {
    fun normalize(code: String): String = code.trim().also {
        if (it.isBlank() || it.length > 32 || it.any(Char::isISOControl))
            throw BadRequestException("收藏单位代码必填、不超过32个字符，且不能包含控制字符")
    }

    fun claim(orgId: String, code: String) {
        val existing = CollectionOrgCodes.selectAll().where { CollectionOrgCodes.code eq code }.singleOrNull()
        if (existing != null) {
            if (existing[CollectionOrgCodes.orgId] != orgId) throw BadRequestException("该代码已属于其他收藏单位（包括历史代码），不能重复使用")
            return
        }
        if (CollectionOrgDao.find { CollectionOrgs.code eq code }.any { it.id.value != orgId })
            throw BadRequestException("收藏单位代码已存在")
        CollectionOrgCodes.insert {
            it[id] = IdGenerate.nextId(); it[this.orgId] = orgId; it[this.code] = code
        }
    }

    fun acceptedCodes(orgId: String): Set<String> = buildSet {
        CollectionOrgDao.findById(orgId)?.code?.let(::add)
        CollectionOrgCodes.selectAll().where { CollectionOrgCodes.orgId eq orgId }.forEach { add(it[CollectionOrgCodes.code]) }
    }

    fun matches(orgId: String, code: String): Boolean =
        CollectionOrgDao.findById(orgId)?.code == code || code in acceptedCodes(orgId)

    /** 由现有安装/升级流程调用；不改写已有代码或更正历史。 */
    fun seedCurrentCodes() { CollectionOrgDao.all().forEach { claim(it.id.value, it.code) } }
}

data class CollectionOrgCodeChangeVo(
    val id: String, val oldCode: String, val newCode: String, val reason: String,
    val correction: Boolean, val operatorId: String, val operatedAt: LocalDateTime,
)

data class CollectionOrgCodeContextVo(
    val orgId: String, val currentCode: String, val used: Boolean, val canEdit: Boolean,
    val stateHash: String, val history: List<CollectionOrgCodeChangeVo>,
)

data class CollectionOrgCodeCorrectionVo(val newCode: String, val reason: String)
