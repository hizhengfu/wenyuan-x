package io.hizhengfu.cloud.collection.common

import io.hizhengfu.cloud.flow.engine.FlowActorContext
import io.hizhengfu.cloud.flow.engine.FlowFormPolicy
import org.springframework.stereotype.Component

/** 与各业务 buildFormData/方案投影共同维护；空值或旧快照缺失字段也不能由任务补写。 */
@Component
class CollectionFlowFormPolicy : FlowFormPolicy {
    private val keys = CollectionFlowDefinitionConstraint().businessKeys
    private val fields = mapOf(
        CollectionConst.BIZ_CHANGE to names("id collectionId reason before content keeperUserId leaderUserId evidenceFileIds correction"),
        CollectionConst.BIZ_ACQUISITION to names("name proposedLevel proposedPrice appraisedValue dossier experts completion source reason"),
        CollectionConst.BIZ_ACQUISITION_PLAN to names("title planType year month budget"),
        CollectionConst.BIZ_DEREGISTER to names("collectionId reason detail baseline approveLevel approvalDocNo expertOpinion approvedBy destination attachmentIds dossier experts notices administrativeEvidence"),
        CollectionConst.BIZ_LOAN to names("id orgId title collectionIds borrower protocolNo insurance attachmentIds operator reviewer loanDate returnDate dossier baseline administrativeEvidence"),
        CollectionConst.BIZ_TRANSFER to names("id collectionId fromOrgId toOrgId reason approvalDocNo attachmentIds baseline dossier createBy administrativeEvidence execution"),
        CollectionConst.BIZ_REQUISITION to names("id collectionIds documentNo sourceBizType sourceBizId purpose basis department operator reviewer attachmentIds toWarehouseId expectReturnDate dossier baseline createBy"),
        CollectionConst.BIZ_RETURN to names("id orgId sourceBizType sourceBizId sourceDocumentNo collectionIds operator keeperUserId toWarehouseId inspection sourceSnapshot"),
        CollectionConst.BIZ_APPRAISAL to names("collectionId resultName resultEra resultTexture resultLevel committee members conclusion attachmentIds"),
        CollectionConst.BIZ_SAMPLING to names("collectionId title planId plan sourceSnapshot requiredAuthority administrative"),
        CollectionConst.BIZ_WAREHOUSE_ENTRY to names("id applicationNo entrantIds externalEntrants warehouseIds purpose plannedStart plannedEnd dossier baseline requiresDirector"),
        CollectionConst.BIZ_REPRODUCTION to names("collectionId repType purpose repDate images planId plan qualification administrative sourceSnapshot digitalPlanId digitalPlan"),
        CollectionConst.BIZ_EXHIBITION to names("title exhibitionNo venueId venue organizer collectionIds placements coverImage attachmentIds startDate endDate state remark"),
        CollectionConst.BIZ_INVENTORY to names("batchName collectionId result note"),
    )

    override fun protectedFields(context: FlowActorContext): Set<String> {
        val applicable = keys.filter { (business, definitions) -> business == context.businessType || context.definitionKey in definitions }.keys
        if (applicable.isEmpty()) return emptySet()
        // Association and dispatch fields are never editable extensions, even when omitted from an old snapshot.
        return applicable.flatMap { fields.getValue(it) }.toSet() + names("orgId businessKey businessType definitionKey flowInstanceId flowStatus selfSelectAssignees")
    }

    private fun names(value: String) = value.split(' ').toSet()
}
