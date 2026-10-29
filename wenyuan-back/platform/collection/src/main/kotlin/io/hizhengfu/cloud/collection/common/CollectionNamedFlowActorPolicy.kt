package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.EnumCollectionTransferKind
import io.hizhengfu.cloud.collection.service.CollectionActorDirectory
import io.hizhengfu.cloud.collection.service.CollectionResponsibleUserService
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.flow.domain.AssigneeType
import io.hizhengfu.cloud.flow.engine.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.springframework.stereotype.Component

@Component
class CollectionNamedFlowActorPolicy(directory: CollectionActorDirectory, users: CollectionResponsibleUserService) : MuseumNamedFlowActorPolicy(directory, users) {
    private val contracts = CollectionFlowDefinitionConstraint()
    override fun applies(context: FlowActorContext) = CollectionFlowActorSources.applies(context)
    override fun orgId(context: FlowActorContext, node: FlowNodeSnapshot) = CollectionFlowActorSources.orgId(context, node)

    override fun responsibility(context: FlowActorContext, node: FlowNodeSnapshot): MuseumNamedResponsibility? {
        val stages = contracts.contracts[context.definitionKey]?.stages
            ?: contracts.businessKeys[context.businessType].orEmpty().flatMap { contracts.contracts[it]?.stages.orEmpty() }
        val required = stages.filter { it.assigneeType == AssigneeType.SELF_SELECT }.map { it.key }.toSet()
        if (node.nodeKey !in required) return null
        val id = context.businessKey ?: fail("缺少原业务单据")
        val key = node.nodeKey
        val head = CollectionConst.ROLE_STORAGE_HEAD
        val director = CollectionConst.ROLE_DIRECTOR
        fun person(id: String?, role: String? = null, department: String? = null, departmentHead: Boolean = false): MuseumNamedResponsibility {
            if (id.isNullOrBlank() || id != id.trim()) fail("原业务责任人尚未明确")
            return MuseumNamedResponsibility(listOf(id), role, department, departmentHead)
        }
        return when (context.businessType) {
            CollectionConst.BIZ_CHANGE -> {
                val r = CollectionChanges.selectAll().where { CollectionChanges.id eq id }.single()
                if (key == "keeper") person(r[CollectionChanges.keeperUserId], head) else person(r[CollectionChanges.leaderUserId], director)
            }
            CollectionConst.BIZ_DEREGISTER -> {
                val d = Deregistrations.select(Deregistrations.dossier).where { Deregistrations.id eq id }.single()[Deregistrations.dossier] ?: fail("缺少退出原方案")
                if (key == "keeper") person(d.keeperUserId, head) else person(d.leaderUserId, director)
            }
            CollectionConst.BIZ_LOAN -> {
                val d = Loans.select(Loans.dossier).where { Loans.id eq id }.single()[Loans.dossier] ?: fail("缺少借用原方案")
                if (key == "keeper") person(d.keeperUserId, head) else person(d.leaderUserId, director)
            }
            CollectionConst.BIZ_REQUISITION -> {
                val d = Requisitions.select(Requisitions.dossier).where { Requisitions.id eq id }.single()[Requisitions.dossier] ?: fail("缺少提用原方案")
                when (key) {
                    "department" -> person(d.departmentHeadUserId, CollectionConst.ROLE_DEPARTMENT_HEAD, d.departmentGroupId, true)
                    "keeper" -> person(d.keeperUserId, head)
                    else -> person(d.leaderUserId, director)
                }
            }
            CollectionConst.BIZ_RETURN -> person(CollectionReturns.select(CollectionReturns.keeperUserId).where { CollectionReturns.id eq id }.single()[CollectionReturns.keeperUserId], head)
            CollectionConst.BIZ_TRANSFER -> {
                val d = CollectionTransfers.select(CollectionTransfers.dossier).where { CollectionTransfers.id eq id }.single()[CollectionTransfers.dossier] ?: fail("缺少双方原方案")
                when(key) {
                    "source_keeper" -> person(d.sourceKeeperUserId, head)
                    "source_leader" -> if (d.kind == EnumCollectionTransferKind.INSTITUTION_SUCCESSION) {
                        val s = d.succession ?: fail("缺少终止合并依法授权人员依据")
                        if (s.authorizedRepresentativeUserId != d.sourceLeaderUserId || s.representativeAuthorizationFileIds.isBlank()) fail("原馆依法授权责任人的依据不完整")
                        person(d.sourceLeaderUserId)
                    } else person(d.sourceLeaderUserId, director)
                    "target_keeper" -> person(d.targetKeeperUserId, head)
                    else -> person(d.targetLeaderUserId, director)
                }
            }
            CollectionConst.BIZ_WAREHOUSE_ENTRY -> {
                val d = WarehouseEntryApplications.select(WarehouseEntryApplications.dossier).where { WarehouseEntryApplications.id eq id }.single()[WarehouseEntryApplications.dossier] ?: fail("缺少入库原方案")
                if (key == "keeper") person(d.keeperUserId, head) else person(d.leaderUserId, director)
            }
            CollectionConst.BIZ_SAMPLING -> {
                val row = CollectionSamplingPlans.select(CollectionSamplingPlans.content, CollectionSamplingPlans.orgId).where { CollectionSamplingPlans.samplingId eq id }
                    .orderBy(CollectionSamplingPlans.version, SortOrder.DESC).limit(1).singleOrNull() ?: fail("缺少原取样方案")
                if (row[CollectionSamplingPlans.orgId] != orgId(context, node)) fail("原取样方案与业务所属馆不一致")
                val p = row[CollectionSamplingPlans.content]
                if (key == "conservation") person(p.conservationUserId, CollectionConst.ROLE_CONSERVATION, p.conservationDepartmentId) else person(p.keeperUserId, head)
            }
            CollectionConst.BIZ_REPRODUCTION -> if (context.definitionKey == CollectionConst.FLOW_DIGITAL_ACQUISITION_2026) {
                val row = DigitalAcquisitionPlans.select(DigitalAcquisitionPlans.content, DigitalAcquisitionPlans.orgId).where { DigitalAcquisitionPlans.reproductionId eq id }
                    .orderBy(DigitalAcquisitionPlans.version, SortOrder.DESC).limit(1).singleOrNull() ?: fail("缺少原数字采集方案")
                if (row[DigitalAcquisitionPlans.orgId] != orgId(context, node)) fail("原数字采集方案与业务所属馆不一致")
                val p = row[DigitalAcquisitionPlans.content]
                if (key == "information") person(p.informationUserId, CollectionConst.ROLE_INFORMATION, p.informationDepartmentId) else person(p.keeperUserId, head)
            } else {
                val row = ReproductionPlans.select(ReproductionPlans.content, ReproductionPlans.orgId).where { ReproductionPlans.reproductionId eq id }
                    .orderBy(ReproductionPlans.version, SortOrder.DESC).limit(1).singleOrNull() ?: fail("缺少原复制拓印方案")
                if (row[ReproductionPlans.orgId] != orgId(context, node)) fail("原复制拓印方案与业务所属馆不一致")
                val p = row[ReproductionPlans.content]
                if (key == "keeper") person(p.keeperUserId, head) else person(p.leaderUserId, director)
            }
            CollectionConst.BIZ_APPRAISAL -> {
                val rows = AppraisalMembers.select(AppraisalMembers.userId, AppraisalMembers.orgId).where { AppraisalMembers.appraisalId eq id }
                    .orderBy(AppraisalMembers.sort to SortOrder.ASC, AppraisalMembers.memberName to SortOrder.ASC).toList()
                val museum = orgId(context, node)
                if (rows.any { it[AppraisalMembers.orgId] != museum }) fail("鉴选原成员记录与业务所属馆不一致")
                val people = rows.mapNotNull { it[AppraisalMembers.userId]?.trim()?.takeIf(String::isNotBlank) }.distinct()
                if (people.isEmpty()) fail("鉴选原成员尚未关联实际人员，请先核实原名单")
                MuseumNamedResponsibility(people, externalAllowed = true)
            }
            else -> fail("原业务的本人职责尚未明确")
        }
    }

    private fun fail(message: String): Nothing = throw BadRequestException(message)
}
