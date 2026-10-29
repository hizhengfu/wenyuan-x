package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.OrgAuditUIDTable
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntity
import io.hizhengfu.cloud.collection.common.OrgAuditUIDEntityClass
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.javatime.date

object AcquisitionComplianceCases : OrgAuditUIDTable("col_acq_compliance") {
    val acquisitionId=varchar("acquisition_id",64).uniqueIndex(shortIndexName("acquisition_id",unique=true))
    val subject=text("subject_snapshot")
    val dossier=json("dossier",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AcquisitionDossierVo::class.java)!!})
    val completion=json("completion",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AcquisitionCompletionVo::class.java)!!}).nullable()
    val vetoOn=date("veto_on").nullable()
}
class AcquisitionComplianceCaseDao(id: EntityID<String>) : OrgAuditUIDEntity(id,AcquisitionComplianceCases) {
    companion object : OrgAuditUIDEntityClass<AcquisitionComplianceCaseDao>(AcquisitionComplianceCases)
    var acquisitionId by AcquisitionComplianceCases.acquisitionId
    var subject by AcquisitionComplianceCases.subject
    var dossier by AcquisitionComplianceCases.dossier
    var completion by AcquisitionComplianceCases.completion
    var vetoOn by AcquisitionComplianceCases.vetoOn
}
object AcquisitionExpertOpinions : OrgAuditUIDTable("col_acq_expert") {
    val acquisitionId=varchar("acquisition_id",64)
    val expertKey=varchar("expert_key",100)
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,AcquisitionExpertOpinionVo::class.java)!!})
    init { uniqueIndex(shortIndexName("acquisition_id_expert_key",unique=true),acquisitionId,expertKey) }
}
class AcquisitionExpertOpinionDao(id: EntityID<String>) : OrgAuditUIDEntity(id,AcquisitionExpertOpinions) {
    companion object : OrgAuditUIDEntityClass<AcquisitionExpertOpinionDao>(AcquisitionExpertOpinions)
    var acquisitionId by AcquisitionExpertOpinions.acquisitionId
    var expertKey by AcquisitionExpertOpinions.expertKey
    var content by AcquisitionExpertOpinions.content
}
