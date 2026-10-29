package io.hizhengfu.cloud.collection.dao

import io.hizhengfu.cloud.autoconfigure.exposed.shortCheckConstraintName

import io.hizhengfu.cloud.autoconfigure.exposed.shortIndexName
import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.domain.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.json.json

object DigitalAcquisitionPlans : OrgAuditUIDTable("col_digital_acquisition_plan") {
    val reproductionId=varchar("reproduction_id",64).index(shortIndexName("reproduction_id"))
    val version=integer("version", checkConstraintName = shortCheckConstraintName("version"))
    val sourceSnapshot=text("source_snapshot")
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalAcquisitionPlanVo::class.java)!!})
    val started=json("started",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalAcquisitionStartVo::class.java)!!}).nullable()
    val captured=json("captured",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalCaptureVo::class.java)!!}).nullable()
    val completed=json("completed",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalAcquisitionCompletionVo::class.java)!!}).nullable()
    init { uniqueIndex(shortIndexName("reproduction_version",unique=true),reproductionId,version) }
}

class DigitalAcquisitionPlanDao(id: EntityID<String>) : OrgAuditUIDEntity(id,DigitalAcquisitionPlans) {
    companion object : OrgAuditUIDEntityClass<DigitalAcquisitionPlanDao>(DigitalAcquisitionPlans)
    var reproductionId by DigitalAcquisitionPlans.reproductionId
    var version by DigitalAcquisitionPlans.version
    var sourceSnapshot by DigitalAcquisitionPlans.sourceSnapshot
    var content by DigitalAcquisitionPlans.content
    var started by DigitalAcquisitionPlans.started
    var captured by DigitalAcquisitionPlans.captured
    var completed by DigitalAcquisitionPlans.completed
}

object DigitalAcquisitionResources : OrgAuditUIDTable("col_digital_acquisition_resource") {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val content=json("content",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalResourceStageVo::class.java)!!})
}
class DigitalAcquisitionResourceDao(id: EntityID<String>) : OrgAuditUIDEntity(id,DigitalAcquisitionResources) {
    companion object : OrgAuditUIDEntityClass<DigitalAcquisitionResourceDao>(DigitalAcquisitionResources)
    var planId by DigitalAcquisitionResources.planId
    var content by DigitalAcquisitionResources.content
}

/** 每一版阶段产物的字节校验快照，可由文件访问层据此定位数字资源所属馆和方案。 */
object DigitalAcquisitionFiles : OrgAuditUIDTable("col_digital_acquisition_file") {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val resourceId=varchar("resource_id",64).index(shortIndexName("resource_id"))
    val fileId=varchar("file_id",64).index(shortIndexName("file_id"))
    val snapshot=json("snapshot",{JsonUtils.toJson(it)!!},{JsonUtils.fromJson(it,DigitalFileSnapshot::class.java)!!})
    init { uniqueIndex(shortIndexName("resource_file",unique=true),resourceId,fileId) }
}
class DigitalAcquisitionFileDao(id: EntityID<String>) : OrgAuditUIDEntity(id,DigitalAcquisitionFiles) {
    companion object : OrgAuditUIDEntityClass<DigitalAcquisitionFileDao>(DigitalAcquisitionFiles)
    var planId by DigitalAcquisitionFiles.planId
    var resourceId by DigitalAcquisitionFiles.resourceId
    var fileId by DigitalAcquisitionFiles.fileId
    var snapshot by DigitalAcquisitionFiles.snapshot
}

/** 独立保留访问边界；来源文件元数据或普通 secure 开关变化不会使旧路径失去保护。 */
object DigitalAcquisitionFileAccesses : OrgAuditUIDTable("col_digital_file_access") {
    val planId=varchar("plan_id",64).index(shortIndexName("plan_id"))
    val fileId=varchar("file_id",64).index(shortIndexName("file_id"))
    val pathKey=varchar("path_key",64).index(shortIndexName("path_key"))
    init { uniqueIndex(shortIndexName("plan_file_path",unique=true),planId,fileId,pathKey) }
}
class DigitalAcquisitionFileAccessDao(id: EntityID<String>) : OrgAuditUIDEntity(id,DigitalAcquisitionFileAccesses) {
    companion object : OrgAuditUIDEntityClass<DigitalAcquisitionFileAccessDao>(DigitalAcquisitionFileAccesses)
    var planId by DigitalAcquisitionFileAccesses.planId
    var fileId by DigitalAcquisitionFileAccesses.fileId
    var pathKey by DigitalAcquisitionFileAccesses.pathKey
}
