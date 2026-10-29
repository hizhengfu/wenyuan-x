package io.hizhengfu.cloud.wms.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.wms.dao.*
import io.hizhengfu.cloud.wms.domain.EnumArtifactStatus
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
@Transactional
@CacheConfig(cacheNames = ["WMS"], keyGenerator = "keyGenerator")
class WmsArtifactStockService() : CrudService<WmsArtifactDao>(WmsArtifactDao, "WMS") {

    /**
     * 库存详情：以 WmsArtifactStocks 为驱动表 —— 仅返回已入库（有库存记录）的文物。
     * 未入库文物请走 [getRegister]（登记信息回退），保持「库存接口只含已入库」的语义。
     */
    @Cacheable
    fun get(id: String): ResultRow? {
        val query = WmsArtifactStocks.join(WmsArtifacts, JoinType.LEFT) {
            WmsArtifactStocks.id eq WmsArtifacts.id
        }.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.join(WmsStorageLocations, JoinType.LEFT) {
            WmsArtifactStocks.locationId eq WmsStorageLocations.id
        }.join(WmsStorageRooms, JoinType.LEFT) {
            WmsStorageLocations.roomId eq WmsStorageRooms.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsArtifactStocks.locationId,
                WmsArtifactStocks.stockQuantity,
                WmsArtifactStocks.storageTime,
                WmsArtifactStocks.saveStatus,
                WmsStorageRooms.id,
                WmsStorageRooms.name,
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
                WmsStorageLocations.name
            )
        ).where { WmsArtifacts.id.eq(id) }.singleOrNull()
        return query
    }

    /**
     * 登记信息（无库存回退）：仅文物 + 遗址/单位，库存相关字段为空。
     * 供详情页在文物「未入库」时展示登记信息，不污染「库存接口只含已入库」的语义。
     */
    @Cacheable
    fun getRegister(id: String): ResultRow? =
        WmsArtifacts.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
            )
        ).where { WmsArtifacts.id.eq(id) }.singleOrNull()

    @Cacheable
    fun findPage(request: HttpServletRequest, wmsArtifact: WmsArtifactVo): GenericPage<ResultRow> {
        val query = WmsArtifactStocks.join(WmsArtifacts, JoinType.LEFT) {
            WmsArtifactStocks.id.eq(WmsArtifacts.id)
        }.join(WmsArtifactExtends, JoinType.LEFT) {
            WmsArtifacts.id eq WmsArtifactExtends.id
        }.join(WmsProjects, JoinType.LEFT) {
            WmsArtifacts.projectId eq WmsProjects.id
        }.join(WmsStorageLocations, JoinType.LEFT) {
            WmsArtifactStocks.locationId eq WmsStorageLocations.id
        }.join(WmsStorageRooms, JoinType.LEFT) {
            WmsStorageLocations.roomId eq WmsStorageRooms.id
        }.join(WmsProjectCells, JoinType.LEFT) {
            WmsArtifacts.projectCellId eq WmsProjectCells.id
        }.select(
            listOf(
                *WmsArtifacts.columns.toTypedArray(),
                WmsArtifactStocks.locationId,
                WmsArtifactStocks.stockQuantity,
                WmsArtifactStocks.storageTime,
                WmsArtifactStocks.saveStatus,
                WmsStorageRooms.id,
                WmsStorageRooms.name,
                WmsStorageLocations.name,
                WmsProjects.id,
                WmsProjects.name,
                WmsProjectCells.name,
                WmsStorageLocations.name
            )
        )

        if (!wmsArtifact.name.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.name like "%${wmsArtifact.name}%"
            }
        }
        if (!wmsArtifact.code.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.code.eq(wmsArtifact.code)
            }
        }
        if (!wmsArtifact.material.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.material.inList(wmsArtifact.material.split(","))
            }
        }
        if (!wmsArtifact.storageType.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactStocks.storageType.inList(wmsArtifact.storageType.split(","))
            }
        }

        if (!wmsArtifact.projectId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectId.eq(wmsArtifact.projectId)
            }
        }

        if (!wmsArtifact.projectPlotId.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.plotId.inList(wmsArtifact.projectPlotId.split(","))
            }
        }

        if (!wmsArtifact.projectCellType.isNullOrBlank()) {
            query.andWhere {
                WmsProjectCells.type.inList(wmsArtifact.projectCellType.split(","))
            }
        }

        if (!wmsArtifact.projectCellId.isNullOrBlank()) {
            query.andWhere {
                WmsArtifacts.projectCellId.inList(wmsArtifact.projectCellId.split(","))
            }
        }
        if (!wmsArtifact.locationId.isNullOrBlank()) {
            query.andWhere {
                WmsStorageLocations.id.eq(wmsArtifact.locationId)
            }
        }
        if (!wmsArtifact.roomId.isNullOrBlank()) {
            query.andWhere {
                WmsStorageLocations.roomId.eq(wmsArtifact.roomId)
            }
        }

        if (!wmsArtifact.storageType.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactStocks.storageType.inList(wmsArtifact.storageType.split(","))
            }
        }

        if (!wmsArtifact.saveStatus.isNullOrBlank()) {
            query.andWhere {
                WmsArtifactStocks.saveStatus.inList(wmsArtifact.saveStatus.split(","))
            }
        }
        wmsArtifact.status?.let {
            query.andWhere {
                WmsArtifactStocks.status.eq(it)
            }
        }
        if (wmsArtifact.unearthDateList?.isNotEmpty() == true) {
            wmsArtifact.unearthDateList.let {
                query.andWhere {
                    WmsArtifacts.unearthDate.between(it.first(), it.last())
                }
            }
        }
        //更新时间
        if (wmsArtifact.storageTimeList?.isNotEmpty() == true) {
            wmsArtifact.storageTimeList.let {
                query.andWhere {
                    WmsArtifactStocks.storageTime.between(it.first(), it.last())
                }
            }
        }
        //更新时间
        if (wmsArtifact.storageRemarks?.isNotEmpty() == true) {
            wmsArtifact.storageRemarks.let {
                query.andWhere {
                    WmsArtifactStocks.storageRemarks.like("%${wmsArtifact.storageRemarks}%")
                }
            }
        }

        val page = GenericPage<ResultRow>().build(request)
        if (page.sortBy.isNullOrEmpty()) {
            if (wmsArtifact.status == null || wmsArtifact.status == EnumArtifactStatus.REGISTER) {
                query.orderBy(WmsArtifacts.createTime, SortOrder.DESC)
            } else {
                query.orderBy(WmsArtifactStocks.storageTime, SortOrder.DESC)
            }
        } else {
            buildPageDslSort(query, page, WmsArtifacts)
        }
        return super.findPageDsl(query, page)
    }
}
