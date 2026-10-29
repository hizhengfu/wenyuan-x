package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.museum.dao.MuDevices
import io.hizhengfu.cloud.museum.dao.MuHistoryData
import io.hizhengfu.cloud.museum.dao.MuHistoryDataDao
import io.hizhengfu.cloud.museum.dao.MuHistoryDatas
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.cache.annotation.CacheConfig
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Component
@Transactional
@CacheConfig(cacheNames = ["MUSEUM"], keyGenerator = "keyGenerator")
class MuHistoryDataService : CrudService<MuHistoryDataDao>(MuHistoryDataDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muHistoryData: MuHistoryData): MuHistoryDataDao {
        return dao.new {
            deviceId = muHistoryData.deviceId
            dataValue0 = muHistoryData.dataValue0
            dataValue1 = muHistoryData.dataValue1
            dataValue2 = muHistoryData.dataValue2
            dateTime = muHistoryData.dateTime
            createTime = LocalDateTime.now()
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muHistoryData: MuHistoryData): MuHistoryDataDao {
        val id = muHistoryData.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muHistoryDataDao = findById(id) ?: throw BadRequestException("无有效数据")

        if (!muHistoryData.deviceId.isNullOrBlank()) {
            muHistoryDataDao.deviceId = muHistoryData.deviceId
        }
        muHistoryData.dataValue0?.let {
            muHistoryDataDao.dataValue0 = it
        }
        muHistoryData.dataValue1?.let {
            muHistoryDataDao.dataValue1 = it
        }
        muHistoryData.dataValue2?.let {
            muHistoryDataDao.dataValue2 = it
        }
        muHistoryData.dateTime?.let {
            muHistoryDataDao.dateTime = it
        }
        return muHistoryDataDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuHistoryDataDao {
        return super.delete(id)
    }

    /**
     * 批量删除
     * */
    @CacheEvict(allEntries = true)
    fun delete(ids: List<String>): Int {
        return super.deleteByIds(ids)
    }


    @CacheEvict(allEntries = true)
    fun copy(id: String): MuHistoryDataDao {
        val muHistoryData = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muHistoryData.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muHistoryData: MuHistoryData): GenericPage<ResultRow>? {
        val query = MuHistoryDatas.join(MuDevices, JoinType.LEFT) {
            MuHistoryDatas.deviceId eq MuDevices.id
        }.select(MuHistoryDatas.columns + MuDevices.name)

        if (!muHistoryData.deviceId.isNullOrBlank()) {
            query.andWhere {
                MuHistoryDatas.deviceId.eq(muHistoryData.deviceId)
            }
        }

        if (!muHistoryData.dateTimeRange.isNullOrEmpty()) {
            query.andWhere {
                MuHistoryDatas.dateTime inList muHistoryData.dateTimeRange
            }
        }

        val page = GenericPage<ResultRow>().build(request)
        //默认倒序排列
        if (page.sortBy.isNullOrEmpty()) {
            query.orderBy(MuHistoryDatas.dateTime, SortOrder.DESC)
        } else {
            buildPageDslSort(query, page, MuHistoryDatas)
        }

        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuHistoryDataDao? {
        return super.findById(id)
    }
}
