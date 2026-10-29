package io.hizhengfu.cloud.museum.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.common.domain.EnumContentStatus
import io.hizhengfu.cloud.common.domain.GenericPage
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.service.CrudService
import io.hizhengfu.cloud.common.utils.HttpClientUtils
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.museum.dao.MuRooms
import io.hizhengfu.cloud.museum.dao.MuTour
import io.hizhengfu.cloud.museum.dao.MuTourDao
import io.hizhengfu.cloud.museum.dao.MuTours
import io.hizhengfu.cloud.museum.domain.TouristSpotToken
import io.hizhengfu.cloud.museum.domain.TouristSpotWrap
import jakarta.servlet.http.HttpServletRequest
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteAll
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
class MuTourService : CrudService<MuTourDao>(MuTourDao, "MUSEUM") {

    @CacheEvict(allEntries = true)
    fun create(muTour: MuTour): MuTourDao {
        val userId = SecurityUtils.getUserId()
        return dao.new {
            name = muTour.name!!
            image = muTour.image
            serialNo = muTour.serialNo
            roomId = muTour.roomId!!
            x = muTour.x
            y = muTour.y
            z = muTour.z
            createTime = LocalDateTime.now()
            createBy = userId ?: "anonymous"
            preHint = muTour.preHint
            finalHint = muTour.finalHint
            commentary = muTour.commentary!!
            wait = muTour.wait
            status = muTour.status ?: EnumContentStatus.AUDIT
            inTheWay = muTour.inTheWay
            audioPath = muTour.audioPath
            videoPath = muTour.videoPath
        }
    }

    @CacheEvict(allEntries = true)
    fun update(muTour: MuTour): MuTourDao {
        val id = muTour.id
        if (id.isNullOrBlank()) {
            throw BadRequestException("ID不能为空")
        }
        val muTourDao = findById(id) ?: throw BadRequestException("无有效数据")
        val userId = SecurityUtils.getUserId()

        if (!muTour.name.isNullOrBlank()) {
            muTourDao.name = muTour.name
        }
        if (!muTour.image.isNullOrBlank()) {
            muTourDao.image = muTour.image
        }
        if (!muTour.serialNo.isNullOrBlank()) {
            muTourDao.serialNo = muTour.serialNo
        }
        if (!muTour.roomId.isNullOrBlank()) {
            muTourDao.roomId = muTour.roomId
        }
        muTour.x?.let {
            muTourDao.x = it
        }
        muTour.y?.let {
            muTourDao.y = it
        }
        muTour.z?.let {
            muTourDao.z = it
        }
        if (!muTour.preHint.isNullOrBlank()) {
            muTourDao.preHint = muTour.preHint
        }
        if (!muTour.finalHint.isNullOrBlank()) {
            muTourDao.finalHint = muTour.finalHint
        }
        if (!muTour.commentary.isNullOrBlank()) {
            muTourDao.commentary = muTour.commentary
        }
        muTour.wait?.let {
            muTourDao.wait = it
        }
        muTour.status?.let {
            muTourDao.status = it
        }
        if (!muTour.inTheWay.isNullOrBlank()) {
            muTourDao.inTheWay = muTour.inTheWay
        }
        if (!muTour.audioPath.isNullOrBlank()) {
            muTourDao.audioPath = muTour.audioPath
        }
        if (!muTour.videoPath.isNullOrBlank()) {
            muTourDao.videoPath = muTour.videoPath
        }
        return muTourDao
    }

    @CacheEvict(allEntries = true)
    override fun delete(id: String): MuTourDao {
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
    fun copy(id: String): MuTourDao {
        val muTour = findById(id)?.toModel() ?: throw BadRequestException("无效数据")
        return create(muTour.copy(id = null))
    }

    @Cacheable
    fun findPage(request: HttpServletRequest, muTour: MuTour): GenericPage<ResultRow>? {
        val query = MuTours.join(MuRooms, JoinType.LEFT) {
            MuTours.roomId eq MuRooms.id
        }.select(MuTours.columns + MuRooms.name)
        if (!muTour.name.isNullOrBlank()) {
            query.andWhere {
                MuTours.name eq muTour.name
            }
        }

        if (!muTour.roomId.isNullOrBlank()) {
            query.andWhere {
                MuTours.roomId eq muTour.roomId
            }
        }

        val page = GenericPage<ResultRow>().build(request)
        buildPageDslSort(query, page, MuTours)
        return super.findPageDsl(query, page)
    }

    @Cacheable
    fun get(id: String): MuTourDao? {
        return super.findById(id)
    }


    /**
     * 同步一期导览点数据
     * */
    fun syncData() {
        val data = mutableMapOf(Pair("username", "test"), Pair("password", "password123"))
        var token = ""
        HttpClientUtils.post("http://47.100.46.95:25586/api-token-auth/", data = data) { json ->
            val resp = JsonUtils.fromJson<TouristSpotToken>(json)
            if (resp != null) {
                token = resp.token
            }
        }

        var page = 1
        val headers = mutableMapOf(Pair("Authorization", "token $token"))
        MuTours.deleteAll()
        while (page != -1) {
            HttpClientUtils.get("http://47.100.46.95:25586/tourist_spots/",
                    headers = headers,
                    params = mutableMapOf(Pair("page", page))
            ) { json ->
                val resp = JsonUtils.fromJson<TouristSpotWrap>(json)
                val dateTime = LocalDateTime.now()

                if (resp?.results != null) {
                    if (resp.next != null) {
                        page++
                    } else {
                        page = -1
                    }

                    MuTours.batchInsert(resp.results) {
                        this[MuTours.name] = it.name
                        this[MuTours.serialNo] = it.id.toString()
                        this[MuTours.roomId] = "1554111392098672640"
                        this[MuTours.preHint] = it.final_hint
                        this[MuTours.finalHint] = it.final_hint
                        this[MuTours.commentary] = it.commentary
                        if (it.wait.isNotBlank()) {
                            this[MuTours.wait] = it.wait.toInt()
                        }
                        this[MuTours.inTheWay] = it.in_the_way
                        this[MuTours.audioPath] = it.mp3
                        this[MuTours.videoPath] = it.mp4

                        this[MuTours.x] = it.selected.x_position
                        this[MuTours.y] = it.selected.y_position
                        this[MuTours.z] = it.selected.z_position

                        this[MuTours.createTime] = dateTime
                        this[MuTours.status] = EnumContentStatus.NORMAL
                        this[MuTours.fromFirst] = true
                    }
                } else {
                    page = -1
                }
            }
        }
    }
}
