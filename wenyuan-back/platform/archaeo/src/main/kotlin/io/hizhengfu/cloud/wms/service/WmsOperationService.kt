package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.utils.UniqueCodeGeneratorUtils
import io.hizhengfu.cloud.sys.system.dao.Users
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.*
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional(rollbackFor = [BadRequestException::class, Exception::class])
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsOperationService() : CrudService<WmsOperateRecordDao>(WmsOperateRecordDao, "WMS") {


    /**
     * 库房操作默认操作
     * */
    protected fun defaultOperation(
        operateRecordDao: WmsOperateRecordDao,
        operation: IOperation,
        type: EnumStorageOperationType,
        dateTime: LocalDateTime = LocalDateTime.now()
    ) {

        operation.artifactList?.forEach { artifact ->
            val artifactStockDao =
                WmsArtifactStockDao.findById(artifact.artifactId)
                    ?: throw BadRequestException("${type.description}文物无效")

            var flowStatus = EnumOperationFlowStatus.COMPLETE
            when (type) {
                EnumStorageOperationType.INBOUND -> {
                    if (artifactStockDao.status != EnumArtifactStatus.REGISTER) {
                        throw BadRequestException("非法操作，请立即停止非法操作！")
                    }

                    val artifactDao = WmsArtifactDao.findById(artifact.artifactId)

                    artifactStockDao.status = EnumArtifactStatus.INSIDE
                    artifactStockDao.locationId = operation.locationId
                    artifactStockDao.storageTime = dateTime
                    //修改为在库
                    artifactStockDao.storageType = EnumArtifactStatus.INSIDE.description

                    //入库的时候复制出土状态到保存状态
                    artifactStockDao.saveStatus = artifactDao?.preservation
                    //入库成功后更新文物状态为完成入库
                    artifactDao?.status = EnumArtifactStatus.INSIDE
                }

                EnumStorageOperationType.OUTBOUND -> {
                    if (artifactStockDao.status != EnumArtifactStatus.INSIDE) {
                        throw BadRequestException("文物不在库，请立即停止非法操作！")
                    }

                    if (artifact.quantity > artifactStockDao.stockQuantity) {
                        throw BadRequestException("出库文物不能超过库存！")
                    }
                    if (artifact.quantity < 1) {
                        throw BadRequestException("出库文物至少要选择一件！")
                    }

                    //全部出库后设置状态为不在库
                    if (artifact.quantity == artifactStockDao.stockQuantity) {
                        artifactStockDao.status = EnumArtifactStatus.OUTSIDE
                    }
                    //对库存数据进行修改
                    artifactStockDao.stockQuantity -= artifact.quantity
                    //更新操作类型
                    artifactStockDao.storageType = operation.storageType

                    //出库时候设置状态为进行中
                    flowStatus = EnumOperationFlowStatus.DOING

                    //不移除库房位置，方便后续操作
                    //artifactDao.locationId = operation.locationId
                }

                EnumStorageOperationType.TRANSFER -> {
                    if (artifactStockDao.status != EnumArtifactStatus.INSIDE) {
                        throw BadRequestException("文物不在库，请立即停止非法操作！")
                    }
                    artifactStockDao.status = EnumArtifactStatus.INSIDE
                    artifactStockDao.locationId = operation.locationId
                    artifactStockDao.storageTime = LocalDateTime.now()
                }

                EnumStorageOperationType.RETURN -> {
                    if (artifactStockDao.status != EnumArtifactStatus.OUTSIDE) {
                        throw BadRequestException("文物全部在库，不能进行归库操作！")
                    }

                    if (artifact.quantity < 1) {
                        throw BadRequestException("归库文物至少要选择一件！")
                    }
                    if ((artifact.quantity + artifactStockDao.stockQuantity) > artifactStockDao.quantity) {
                        throw BadRequestException("归还的文物数量不能超过总的文物数量！")
                    }

                    //查询未处理完成关联的出库信息
                    val artifactOperateRecordDao = WmsArtifactOperateRecordDao.find {
                        WmsArtifactOperateRecords.recordId.eq(operateRecordDao.id.value) and
                                WmsArtifactOperateRecords.artifactId.eq(artifact.artifactId) and
                                WmsArtifactOperateRecords.type.eq(EnumStorageOperationType.OUTBOUND)
                    }.singleOrNull() ?: throw BadRequestException("文物出库记录不存在")

                    if (artifactOperateRecordDao.flowStatus == EnumOperationFlowStatus.COMPLETE) {
                        throw BadRequestException("该文物已经完成操作，不需要重复操作")
                    }

                    //出库单对应的出库数量
                    val outQuantity =
                        artifactOperateRecordDao.quantity ?: throw BadRequestException("出库单数量异常，数据无效")

                    if (artifact.quantity > outQuantity) {
                        throw BadRequestException("归还的文物数量超过借出的文物数量！")
                    }

                    //更新库存值
                    artifactStockDao.stockQuantity += artifact.quantity
                    artifactStockDao.storageType = EnumArtifactStatus.INSIDE.description


                    //当入库数量和出库一致时设置状态为已完成
                    if (artifact.quantity == outQuantity) {
                        artifactOperateRecordDao.flowStatus = EnumOperationFlowStatus.COMPLETE
                    }

                    //当本次归库数据和出库一致时，设置库存为在库
                    if (artifact.quantity == artifactStockDao.quantity) {
                        artifactStockDao.status = EnumArtifactStatus.INSIDE
                    }

                    artifactStockDao.locationId = operation.locationId
                    artifactStockDao.storageTime = LocalDateTime.now()
                }

                else -> {

                }
            }



            WmsArtifactOperateRecordDao.new {
                this.type = type
                artifactId = artifact.artifactId
                recordId = operateRecordDao.id.value
                saveStatus = artifact.saveStatus
                quantity = artifact.quantity
                this.flowStatus = flowStatus
            }
        }

        if (type == EnumStorageOperationType.RETURN) {
            //当所有数据都已经归还，设置出库单为已完成
            if (countArtifactOperateDoing(recordId = operateRecordDao.id.value) == 0L) {
                operateRecordDao.flowStatus = EnumOperationFlowStatus.COMPLETE
            }
        }
    }


    /**
     * 统计还没完成的文物与操作记录的数量
     * */
    protected fun countArtifactOperateDoing(recordId: String): Long {
        return WmsArtifactOperateRecords.selectAll().where(
            WmsArtifactOperateRecords.recordId.eq(recordId) and
                    WmsArtifactOperateRecords.flowStatus.eq(EnumOperationFlowStatus.DOING)
        ).count()
    }

    /**
     * 入库操作
     * */
    @CacheEvict(allEntries = true)
    fun inboundOperation(operation: OperationInbound): WmsOperateRecordDao {
        val userDetail = SecurityUtils.getUserDetail()
        val type = EnumStorageOperationType.INBOUND
        val dateTime = LocalDateTime.now()

        val operateRecordDao = dao.new {
            this.type = type
            code = UniqueCodeGeneratorUtils.generate("RK")
            operatorBy = userDetail.id
            sumQuantity = operation.sumQuantity()
            remarks = operation.remarks
            operationTime = dateTime
            flowStatus = EnumOperationFlowStatus.COMPLETE
        }

        //入库时，先增加库存数据
        val artifactIds = operation.artifactList!!.map { it.artifactId }
        WmsArtifactDao.find { WmsArtifacts.id.inList(artifactIds) }.map { artifactDao ->
            WmsArtifactStockDao.new(artifactDao.id.value) {
                quantity = artifactDao.quantity
                stockQuantity = artifactDao.quantity
                status = EnumArtifactStatus.REGISTER
            }
        }

        defaultOperation(
            operateRecordDao = operateRecordDao,
            operation = operation,
            type = type,
            dateTime = dateTime
        )

        WmsOperateInbounds.insert {
            it[recordId] = operateRecordDao.id.value
            it[locationId] = operation.locationId!!
            it[receiver] = operation.receiver
            it[transfer] = operation.transfer
        }

        return operateRecordDao
    }


    /**
     * 出库操作
     * */
    @CacheEvict(allEntries = true)
    fun outboundOperation(operation: OperationOutbound): WmsOperateRecordDao {
        val userDetail = SecurityUtils.getUserDetail()

        val type = EnumStorageOperationType.OUTBOUND

        val dateTime = LocalDateTime.now()

        val operateRecordDao = dao.new {
            this.type = type
            code = UniqueCodeGeneratorUtils.generate("CK")
            operatorBy = userDetail.id
            sumQuantity = operation.sumQuantity()
            remarks = operation.remarks
            operationTime = dateTime
            flowStatus = EnumOperationFlowStatus.DOING
        }

        defaultOperation(
            operateRecordDao = operateRecordDao,
            operation = operation.copy(storageType = operation.outPurpose),
            type = type,
            dateTime = dateTime
        )

        if (operation.needReturn == true && operation.expectedReturn == null) {
            throw BadRequestException("需要归还时，需设置归还日期。")
        }

        WmsOperateOutbounds.insert {
            it[recordId] = operateRecordDao.id.value
            it[outPurpose] = operation.outPurpose
            it[artifactDest] = operation.artifactDest
            it[receiver] = operation.receiver
            it[needReturn] = operation.needReturn ?: false
            it[expectedReturn] = operation.expectedReturn
        }
        return operateRecordDao
    }

    /**
     * 归库操作
     * */
    @CacheEvict(allEntries = true)
    fun returnOperation(operation: OperationReturn): WmsOperateRecordDao {
        val operateRecordDao = get(operation.recordId!!) ?: throw BadRequestException("本出库单无效，请重新选择")
        if (operateRecordDao.flowStatus == EnumOperationFlowStatus.COMPLETE) throw BadRequestException("本出库单已经办结，无需重复办理")

        defaultOperation(
            operateRecordDao = operateRecordDao,
            operation = operation,
            type = EnumStorageOperationType.RETURN
        )

        WmsOperateReturns.insert {
            it[recordId] = operateRecordDao.id.value
            it[actualReturn] = operation.actualReturn
            it[locationId] = operation.locationId!!
            it[receiver] = operation.receiver
            it[checkBy] = operation.checkBy
            it[returnBy] = operation.returnBy
        }
        return operateRecordDao
    }

    /**
     *  移库操作
     * */
    @CacheEvict(allEntries = true)
    fun transferOperation(operation: OperationTransfer): WmsOperateRecordDao {
        val type = EnumStorageOperationType.TRANSFER
        val userDetail = SecurityUtils.getUserDetail()

        val dateTime = LocalDateTime.now()

        val operateRecordDao = dao.new {
            this.type = type
            code = UniqueCodeGeneratorUtils.generate("YK")
            operatorBy = userDetail.id
            sumQuantity = operation.sumQuantity()
            remarks = operation.remarks
            operationTime = dateTime
            flowStatus = EnumOperationFlowStatus.DOING
        }

        defaultOperation(
            operateRecordDao = operateRecordDao,
            operation = operation.copy(locationId = operation.toLocationId),
            type = type,
            dateTime = dateTime
        )
        WmsOperateTransfers.insert {
            it[recordId] = operateRecordDao.id.value
            it[fromLocationId] = operation.fromLocationId
            it[toLocationId] = operation.toLocationId
            it[reason] = operation.reason
        }
        return operateRecordDao
    }


    @CacheEvict(allEntries = true)
    override fun delete(id: String): WmsOperateRecordDao {
        return super.delete(id)
    }

    /**
     * 批量删除
     * */
    @CacheEvict(allEntries = true)
    fun delete(ids: List<String>): Int {
        return super.deleteByIds(ids)
    }


    /**
     * 根据文物id获取流转历史
     * */
    @Cacheable
    fun findHistory(artifactId: String): List<ResultRow> {
        val query = WmsOperateRecords.join(WmsArtifactOperateRecords, JoinType.LEFT) {
            WmsArtifactOperateRecords.recordId.eq(WmsOperateRecords.id)
        }.join(Users, JoinType.LEFT) {
            WmsOperateRecords.operator.eq(Users.id)
        }.select(WmsOperateRecords.columns + WmsArtifactOperateRecords.type + Users.name + Users.loginName)

        query.andWhere {
            WmsArtifactOperateRecords.artifactId eq artifactId
        }
        //按照操作时间倒排
        query.orderBy(WmsArtifactOperateRecords.createTime, SortOrder.DESC)

        return query.toList()
    }


    /**
     * 获取操作关联的文物列表
     * @param recordId 记录id
     * */
    @Cacheable
    fun findArtifactList(recordId: String, type: EnumStorageOperationType? = null): List<ResultRow> {
        val query = WmsArtifactOperateRecords.join(WmsArtifactStocks, JoinType.LEFT) {
            WmsArtifactOperateRecords.artifactId.eq(WmsArtifactStocks.id)
        }.join(WmsArtifacts, JoinType.LEFT) {
            WmsArtifactStocks.id.eq(WmsArtifacts.id)
        }.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsStorageLocations, JoinType.LEFT) {
            WmsArtifactStocks.locationId eq WmsStorageLocations.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsArtifactOperateRecords.saveStatus,
                WmsArtifactOperateRecords.flowStatus,
                WmsArtifactOperateRecords.quantity,
                WmsArtifactStocks.stockQuantity,
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
                WmsArtifactStocks.locationId,
                WmsStorageLocations.name
            )
        )

        type?.let {
            query.andWhere {
                WmsArtifactOperateRecords.type eq it
            }
        }

        query.andWhere {
            WmsArtifactOperateRecords.recordId eq recordId
        }
        query.orderBy(WmsArtifacts.name, SortOrder.ASC)

        return query.toList()
    }

    @Cacheable
    fun get(id: String): WmsOperateRecordDao? {
        return findById(id)
    }

    /**
     * 库存信息表
     * */
    @Cacheable
    fun findPage(
        request: HttpServletRequest, operationRecordVo: OperationRecordVo
    ): GenericPage<ResultRow> {
        val type = operationRecordVo.type

        val joinQuery = WmsOperateRecords.join(Users, JoinType.LEFT) {
            WmsOperateRecords.operator.eq(Users.id)
        }

        val query = when (type) {
            EnumStorageOperationType.INBOUND -> {
                joinQuery.join(WmsOperateInbounds, JoinType.LEFT) {
                    WmsOperateRecords.id.eq(WmsOperateInbounds.recordId)
                }.select(WmsOperateRecords.columns + WmsOperateInbounds.columns + Users.name + Users.loginName)
            }

            EnumStorageOperationType.OUTBOUND -> {
                joinQuery.join(WmsOperateOutbounds, JoinType.LEFT) {
                    WmsOperateRecords.id.eq(WmsOperateOutbounds.recordId)
                }.select(WmsOperateRecords.columns + WmsOperateOutbounds.columns + Users.name + Users.loginName)
            }

            EnumStorageOperationType.TRANSFER -> {
                joinQuery.join(WmsOperateTransfers, JoinType.LEFT) {
                    WmsOperateRecords.id.eq(WmsOperateTransfers.recordId)
                }.select(WmsOperateRecords.columns + WmsOperateTransfers.columns + Users.name + Users.loginName)
            }

            EnumStorageOperationType.RETURN -> {
                joinQuery.join(WmsOperateReturns, JoinType.LEFT) {
                    WmsOperateRecords.id.eq(WmsOperateReturns.recordId)
                }.select(WmsOperateRecords.columns + WmsOperateReturns.columns + Users.name + Users.loginName)
            }

            else -> {
                joinQuery.select(WmsOperateRecords.columns + Users.name + Users.loginName)
            }
        }

        operationRecordVo.type?.let {
            query.andWhere {
                WmsOperateRecords.type.eq(it)
            }
        }

        if (!operationRecordVo.id.isNullOrEmpty()) {
            query.andWhere {
                WmsOperateRecords.id.eq(operationRecordVo.id)
            }
        }

        if (!operationRecordVo.code.isNullOrEmpty()) {
            query.andWhere {
                WmsOperateRecords.code.like("%${operationRecordVo.code}%")
            }
        }

        if (!operationRecordVo.operationTimeList.isNullOrEmpty()) {
            operationRecordVo.operationTimeList.let {
                query.andWhere {
                    WmsOperateRecords.operationTime.between(it.first(), it.last())
                }
            }
        }

        //出库查询增加支持条件
        if (type == EnumStorageOperationType.OUTBOUND) {
            if (!operationRecordVo.outPurpose.isNullOrEmpty()) {
                query.andWhere {
                    WmsOperateOutbounds.outPurpose.eq(operationRecordVo.outPurpose)
                }
            }
        }

        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(WmsOperateRecords.operationTime, SortOrder.DESC)
        } else {
            buildPageDslSort(query, page, WmsOperateRecords)
        }
        return super.findPageDsl(query, page)
    }
}
