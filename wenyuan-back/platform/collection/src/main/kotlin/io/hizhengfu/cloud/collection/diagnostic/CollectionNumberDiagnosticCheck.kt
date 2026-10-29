package io.hizhengfu.cloud.collection.diagnostic

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionStatus
import io.hizhengfu.cloud.collection.domain.EnumRegistrationNoState
import io.hizhengfu.cloud.sys.diagnostic.domain.*
import io.hizhengfu.cloud.sys.diagnostic.service.DeploymentDiagnosticCheck
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 只读取结构化身份列；不推定账外号码从未使用，不解析或修补历史档案。 */
@Component
// SQLite 不支持建连后切换 readOnly；仅 SELECT 的边界由持久化测试核对。
@Transactional(timeout = 25)
class CollectionNumberDiagnosticCheck : DeploymentDiagnosticCheck {
    override val descriptor = DeploymentCheckDescriptor(
        "collection.regulation.number-identities", "DATABASE", "藏品总登记号", "主档、占号与原馆留存一致性",
        EnumDeploymentCheckSeverity.CRITICAL, timeoutMillis = 30_000, order = 125,
    )

    override fun check() = CollectionNumberInspection.inspect()
}

internal object CollectionNumberInspection {
    private data class Rule(val code: String, val table: OrgAuditUIDTable, val problem: Op<Boolean>)

    fun inspect(sampleLimit: Int = 10): DeploymentCheckOutcome {
        require(sampleLimit in 1..10)
        fun interrupted() {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Collection number inspection interrupted")
        }
        interrupted()
        val c = Collections
        val n = CollectionRegistrationNumbers
        val r = CollectionRetainedLedgers
        val c2 = c.alias("other_number_collection")
        val n2 = n.alias("other_number_reservation")
        val r2 = r.alias("other_number_retained")
        val tables = listOf(c, n, r)
        fun counts() = tables.map { it.select(it.id).count() }
        val before = counts()
        val numberForMaster = n.select(n.id).where { (n.orgId eq c.orgId) and (n.totalRegNo eq c.totalRegNo) }
        val ownedNumberForMaster = numberForMaster.copy().andWhere { n.collectionId eq c.id }
        val numberForRetained = n.select(n.id).where { (n.orgId eq r.orgId) and (n.totalRegNo eq r.totalRegNo) }
        val ownedNumberForRetained = numberForRetained.copy().andWhere { n.collectionId eq r.collectionId }
        val currentOwner = c.select(c.id).where {
            (c.orgId eq n.orgId) and (c.totalRegNo eq n.totalRegNo) and (c.id eq n.collectionId)
        }
        val retainedOwner = r.select(r.id).where {
            (r.orgId eq n.orgId) and (r.totalRegNo eq n.totalRegNo) and (r.collectionId eq n.collectionId)
        }
        val rules = listOf(
            Rule("MASTER_IDENTITY_EMPTY", c, (c.orgId eq "") or (c.totalRegNo eq "")),
            Rule("RESERVATION_IDENTITY_EMPTY", n, (n.orgId eq "") or (n.totalRegNo eq "")),
            Rule("RETAINED_IDENTITY_EMPTY", r, (r.orgId eq "") or (r.totalRegNo eq "")),
            Rule("MASTER_RESERVATION_MISSING", c, notExists(numberForMaster)),
            Rule("MASTER_RESERVATION_OWNER_CONFLICT", c, exists(numberForMaster) and notExists(ownedNumberForMaster)),
            Rule("MASTER_NUMBER_DUPLICATE", c, exists(c2.select(c2[c.id]).where {
                (c2[c.orgId] eq c.orgId) and (c2[c.totalRegNo] eq c.totalRegNo) and (c2[c.id] neq c.id)
            })),
            Rule("MASTER_RETAINED_OWNER_CONFLICT", c, exists(r.select(r.id).where {
                (r.orgId eq c.orgId) and (r.totalRegNo eq c.totalRegNo) and (r.collectionId neq c.id)
            })),
            Rule("RESERVATION_DUPLICATE", n, exists(n2.select(n2[n.id]).where {
                (n2[n.orgId] eq n.orgId) and (n2[n.totalRegNo] eq n.totalRegNo) and (n2[n.id] neq n.id)
            })),
            Rule("RESERVATION_OWNER_UNRESOLVED", n, notExists(currentOwner) and notExists(retainedOwner)),
            Rule("RESERVATION_STATE_CONFLICT", n,
                ((n.state eq EnumRegistrationNoState.USED) and notExists(currentOwner)) or
                    ((n.state eq EnumRegistrationNoState.RETIRED) and exists(currentOwner.copy().andWhere {
                        c.lifeStatus neq EnumCollectionStatus.DEREGISTERED
                    }))),
            Rule("RETAINED_RESERVATION_MISSING", r, notExists(numberForRetained)),
            Rule("RETAINED_RESERVATION_OWNER_CONFLICT", r, exists(numberForRetained) and notExists(ownedNumberForRetained)),
            Rule("RETAINED_OWNER_CONFLICT", r, exists(r2.select(r2[r.id]).where {
                (r2[r.orgId] eq r.orgId) and (r2[r.totalRegNo] eq r.totalRegNo) and (r2[r.collectionId] neq r.collectionId)
            })),
        )
        val facts = mutableListOf<DeploymentSafeFact>()
        val samples = mutableListOf<DeploymentSafeFact>()
        var issues = false
        for (rule in rules) {
            interrupted()
            val query = rule.table.select(rule.table.id, rule.table.orgId).where { rule.problem }
            val count = query.count()
            facts += DeploymentSafeFact(rule.code, count.toString())
            if (count > 0) {
                issues = true
                val remaining = sampleLimit - samples.size
                if (remaining > 0) query.orderBy(rule.table.id).limit(remaining).forEach { row ->
                    samples += DeploymentSafeFact("issueSample${samples.size + 1}", JsonUtils.toJson(mapOf(
                        "table" to rule.table.tableName, "id" to row[rule.table.id].value,
                        "orgId" to row[rule.table.orgId], "issue" to rule.code,
                    ))!!)
                }
            }
        }
        interrupted()
        val after = counts()
        facts += tables.mapIndexed { i, table -> DeploymentSafeFact(table.tableName + "Rows", before[i].toString()) }
        facts += DeploymentSafeFact("inspectionScope", "stored identity columns only; counts overlap; no original bytes or paper ledger verification")
        facts += samples
        val remediation = listOf(DeploymentRemediation(EnumDeploymentRemediationType.DATABASE,
            "在暂停相关写入的隔离副本中核对原账和历史归属", listOf(
                "按样本的表、记录ID和馆别定位，核对主档、占号、原馆留存及真实纸质账。",
                "同一藏品多次依法流转可形成多条留存，不能只按留存行数去重。",
                "保留原号、原档案和审批记录；不得自动删行、改号、合并藏品或补造过去的占号事实。",
                "扫描计数一致不能排除并发等量修改；请在暂停相关写入后复核，另验真实原件和升级恢复。",
            )))
        return when {
            before != after -> DeploymentCheckOutcome(EnumDeploymentCheckState.ERROR,
                "号码核查期间记录数量发生变化，请暂停相关写入后复查", "COLLECTION_NUMBER_SCAN_CHANGED", facts, remediation)
            issues -> DeploymentCheckOutcome(EnumDeploymentCheckState.FAIL,
                "主档、占号或原馆留存存在缺失、重复或归属状态冲突，须核对原账", "COLLECTION_NUMBER_IDENTITIES_UNRESOLVED", facts, remediation)
            else -> DeploymentCheckOutcome(EnumDeploymentCheckState.PASS,
                "本次结构化号码核查未发现上述冲突，不代表真实历史号码、纸账和原件已经验收", safeFacts = facts)
        }
    }
}
