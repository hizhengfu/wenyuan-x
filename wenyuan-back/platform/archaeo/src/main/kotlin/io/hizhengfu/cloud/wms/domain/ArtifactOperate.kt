package io.hizhengfu.cloud.wms.domain

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDateTime


/**
 * 库存查询
 * */
data class OperationRecordVo(
    val type: EnumStorageOperationType? = null,
    val status: EnumArtifactStatus? = null,
    val code: String? = null,
    val id: String? = null,
    val outPurpose: String? = null,
    val needReturn: String? = null,
    val operationTimeList: List<LocalDateTime>? = null,
)

/**
 * 文物详细数据
 * */
data class ArtifactOutboundField(
    @field:NotEmpty(message = "请选择文物")
    val artifactId: String,//文物id
    val saveStatus: String?,//保存状态
    val quantity: Int,//数量
)

interface IOperation {
    //    val artifactId: String//文物id
    val artifactList: List<ArtifactOutboundField>?//文物id
    val locationId: String? //库房位置
    val remarks: String?//入库描述
    var storageType: String?//库存操作子类型

    /**
     * 计算总的数量
     * */
    fun sumQuantity() = artifactList?.sumOf { it.quantity ?: 0 } ?: 0
}


/**
 * 入库操作
 * */
data class OperationInbound(
    @field:NotEmpty(message = "请至少选择一个文物")
    override val artifactList: List<ArtifactOutboundField>? = null,//文物id
    @field:NotBlank
    override val locationId: String? = null,
    val receiver: String,//接收方
    val transfer: String,//移交人
    override val remarks: String? = null,//入库描述
    override var storageType: String? = null,//库存子类型
) : IOperation


/**
 * 出库操作
 * */
data class OperationOutbound(
    @field:NotEmpty(message = "请至少选择一个文物")
    override val artifactList: List<ArtifactOutboundField>? = null,//文物id
    override val locationId: String? = null,
    override val remarks: String? = null,//出库描述
    override var storageType: String? = null,//库存子类型
    val outPurpose: String,//出库用途
    val receiver: String,//接收方
    val artifactDest: String?,//文物去向
    val needReturn: Boolean?,//是否归还
    val expectedReturn: LocalDateTime?,//预计归还日期
) : IOperation


/**
 * 归库操作
 * */
data class OperationReturn(
    @field:NotEmpty(message = "请至少选择一个文物")
    override val artifactList: List<ArtifactOutboundField>? = null,//文物id
    override var storageType: String? = null,//库存子类型
    @field:NotEmpty(message = "请选择待处理的出库单")
    val recordId: String? = null,//归库关联的记录
    val receiver: String,//接收方
    val checkBy: String,
    val returnBy: String,
    @field:NotBlank
    override val locationId: String?,
    val actualReturn: LocalDateTime,//实际归还日期
    override val remarks: String? = null,//归库描述
) : IOperation

/**
 * 移库操作
 * */
data class OperationTransfer(
    @field:NotEmpty(message = "请至少选择一个文物")
    override val artifactList: List<ArtifactOutboundField>? = null,//文物id
    override var storageType: String? = null,//库存子类型
    @field:NotBlank
    val fromLocationId: String,//原存储ID
    @field:NotBlank
    val toLocationId: String,//目标存储ID
    override val locationId: String? = null,
    val reason: String,//移库原因
    override val remarks: String? = null,//移库描述
) : IOperation
