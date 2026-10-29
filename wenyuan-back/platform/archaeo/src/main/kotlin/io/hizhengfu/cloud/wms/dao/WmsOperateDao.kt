package io.hizhengfu.cloud.wms.dao

import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntity
import io.hizhengfu.cloud.autoconfigure.exposed.UIDEntityClass
import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable

import io.hizhengfu.cloud.sys.system.dao.Users
import io.hizhengfu.cloud.wms.domain.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.javatime.datetime
import java.time.LocalDateTime


/**
 * 库存操作表
 * */
object WmsOperateRecords : UIDTable("wms_operate_record") {
    val type = enumeration<EnumStorageOperationType>("type").comment("操作类型")//操作类型 0 入库 1 出库 2 移库
    val code = varchar("code", 50).nullable().comment("出库编号")
    val sumQuantity = integer("sum_quantity").default(1).comment("总数量")
    val remarks = text("remarks").nullable().comment("备注")//备注
    val operator = varchar("operator", 50).comment("操作人")//操作人
    val operationTime = datetime("operation_time").comment("操作时间")//操作时间

    val flowStatus = enumeration<EnumOperationFlowStatus>("flow_status").default(EnumOperationFlowStatus.DOING)
        .comment("流程状态")


    fun toModel(row: ResultRow): WmsOperateRecordVo {
        return WmsOperateRecordVo(
            id = row[id]?.value,
            type = row[type],
            code = row[code],
            sumQuantity = row[sumQuantity],
            remarks = row[remarks],
            flowStatus = row[flowStatus],
            operatorBy = "${row[Users.name]}(${row[Users.loginName]})",
            operationTime = row[operationTime]
        )
    }

    fun toModelHistory(row: ResultRow): WmsOperateRecordVo {
        return WmsOperateRecordVo(
            id = row[id]?.value,
            type = row[WmsArtifactOperateRecords.type],
            code = row[code],
            sumQuantity = row[sumQuantity],
            remarks = row[remarks],
            flowStatus = row[flowStatus],
            operatorBy = "${row[Users.name]}(${row[Users.loginName]})",
            operationTime = row[operationTime]
        )
    }


    fun toModelList(row: ResultRow): WmsOperateRecordVo {
        return when (row[type]) {
            EnumStorageOperationType.INBOUND -> {
                toModelInbound(row)
            }

            EnumStorageOperationType.OUTBOUND -> {
                toModelOutbound(row)
            }

            EnumStorageOperationType.TRANSFER -> {
                toModelTransfer(row)
            }

            EnumStorageOperationType.RETURN -> {
                toModelReturn(row)
            }

            else -> {
                toModel(row)
            }
        }
    }

    fun toModelInbound(row: ResultRow): WmsOperateRecordVo {
        return toModel(row).copy(
            operationDetail = OperationInbound(
                locationId = row[WmsOperateInbounds.locationId],
                receiver = row[WmsOperateInbounds.receiver],
                transfer = row[WmsOperateInbounds.transfer],
            )
        )
    }

    fun toModelOutbound(row: ResultRow): WmsOperateRecordVo {
        return toModel(row).copy(
            operationDetail = OperationOutbound(
                artifactDest = row[WmsOperateOutbounds.artifactDest],
                outPurpose = row[WmsOperateOutbounds.outPurpose],
                receiver = row[WmsOperateOutbounds.receiver],
                needReturn = row[WmsOperateOutbounds.needReturn],
                expectedReturn = row[WmsOperateOutbounds.expectedReturn],
            )
        )
    }

    fun toModelTransfer(row: ResultRow): WmsOperateRecordVo {
        return toModel(row).copy(
            operationDetail = OperationTransfer(
                fromLocationId = row[WmsOperateTransfers.fromLocationId],
                toLocationId = row[WmsOperateTransfers.toLocationId],
                reason = row[WmsOperateTransfers.reason],
            )
        )
    }

    fun toModelReturn(row: ResultRow): WmsOperateRecordVo {
        return toModel(row).copy(
            operationDetail = OperationReturn(
                locationId = row[WmsOperateReturns.locationId],
                checkBy = row[WmsOperateReturns.checkBy],
                returnBy = row[WmsOperateReturns.returnBy],
                receiver = row[WmsOperateReturns.receiver],
                actualReturn = row[WmsOperateReturns.actualReturn],
            )
        )
    }
}


class WmsOperateRecordDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<WmsOperateRecordDao>(WmsOperateRecords)

    var type by WmsOperateRecords.type
    var code by WmsOperateRecords.code

    var sumQuantity by WmsOperateRecords.sumQuantity
    var remarks by WmsOperateRecords.remarks
    var flowStatus by WmsOperateRecords.flowStatus
    var operatorBy by WmsOperateRecords.operator
    var operationTime by WmsOperateRecords.operationTime

    fun toModel(): WmsOperateRecordVo {
        return WmsOperateRecordVo(
            id = id.value,
            type = type,
            code = code,
            sumQuantity = sumQuantity,
            remarks = remarks,
            flowStatus = flowStatus,
            operatorBy = operatorBy,
            operationTime = operationTime
        )
    }
}

data class WmsOperateRecordVo(
    val id: String? = null,
    @field:NotNull
    val type: EnumStorageOperationType? = null,
    val code: String? = null,
    @field:NotBlank
    val operatorBy: String? = null,
    val sumQuantity: Int? = null,
    val remarks: String? = null,
    val flowStatus: EnumOperationFlowStatus? = null,
    val operationTime: LocalDateTime? = null,

    //操作详情
    val operationDetail: IOperation? = null,

    val files: List<String>? = null
)

/**
 *  文物库存操作记录
 * */
object WmsArtifactOperateRecords : UIDTable("wms_artifact_operate_record") {
    val type = enumeration<EnumStorageOperationType>("type").comment("操作类型")//操作类型 0 入库 1 出库 2 移库
    val recordId = varchar("record_id", 64).comment("操作记录")
    val artifactId = varchar("artifact_id", 64).comment("文物id")
    val saveStatus = varchar("save_status", 255).nullable().comment("出库保存状态")
    val quantity = integer("quantity").nullable().comment("出库数量")
    val createTime = datetime("create_time").nullable().clientDefault { LocalDateTime.now() }

    val flowStatus = enumeration<EnumOperationFlowStatus>("flow_status").default(EnumOperationFlowStatus.DOING)
        .comment("流程状态")

    init {
        index(false, recordId, artifactId)
    }
}

class WmsArtifactOperateRecordDao(id: EntityID<String>) : UIDEntity(id) {
    companion object : UIDEntityClass<WmsArtifactOperateRecordDao>(WmsArtifactOperateRecords)

    var type by WmsArtifactOperateRecords.type
    var recordId by WmsArtifactOperateRecords.recordId
    var artifactId by WmsArtifactOperateRecords.artifactId
    var saveStatus by WmsArtifactOperateRecords.saveStatus
    var quantity by WmsArtifactOperateRecords.quantity
    var flowStatus by WmsArtifactOperateRecords.flowStatus
    var createTime by WmsArtifactOperateRecords.createTime
}


/**
 * 入库明细表
 * */
object WmsOperateInbounds : UIDTable("wms_operate_inbound") {
    val recordId = varchar("record_id", 64).comment("操作记录")
    val locationId = varchar("location_id", 64).comment("库房位置")
    val receiver = varchar("receiver", 255).comment("接收人")
    val transfer = varchar("transfer", 255).comment("移交人")
}

/**
 * 出库明细表
 * */
object WmsOperateOutbounds : UIDTable("wms_operate_outbound") {
    val recordId = varchar("record_id", 64).comment("操作记录")
    val artifactDest = varchar("artifact_dest", 255).nullable().comment("文物去向")
    val outPurpose = varchar("out_purpose", 100).comment("出库原因")//参考 EnumArtifactOutboundType 类
    val receiver = varchar("receiver", 100).comment("接收方")//接收方
    val needReturn = bool("need_return").default(true).comment("是否归还")
    val expectedReturn = datetime("expected_return").nullable().comment("预计归还日期")//预计归还日期
}

/**
 * 归库明细表
 * */
object WmsOperateReturns : UIDTable("wms_operate_return") {
    val recordId = varchar("record_id", 64).comment("操作记录")
    val locationId = varchar("location_id", 64).comment("库房位置")
    val checkBy = varchar("check_by", 100).comment("核对人")
    val returnBy = varchar("return_by", 100).comment("归还人")
    val receiver = varchar("receiver", 100).comment("接收方")
    val actualReturn = datetime("actual_return").comment("实际归还日期")//实际归还日期
}

/**
 * 移库明细表
 * */
object WmsOperateTransfers : UIDTable("wms_operate_transfer") {
    val recordId = varchar("record_id", 64).comment("操作记录")
    val fromLocationId = varchar("from_location_id", 64).comment("原库房位置")
    val toLocationId = varchar("to_location_id", 64).comment("目标库房位置")
    val reason = varchar("reason", 100).comment("转移原因")//转移原因 '整理','修复','布展','安全转移'
}
