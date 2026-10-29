package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.common.CollectionScopeResolver
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.AnnualReportArchiveDao
import io.hizhengfu.cloud.collection.dao.AnnualReportArchiveVo
import io.hizhengfu.cloud.collection.dao.AnnualReportArchives
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
@Transactional
class AnnualReportArchiveService(
    private val scope: CollectionScopeResolver,
    private val attachmentBinder: BusinessAttachmentBinder,
) {
    fun get(year: Int): AnnualReportArchiveVo? {
        val orgId = CollectionSearchReadAccess.require(scope, additionalPermissions=setOf(CollectionConst.STAT), allowHistoricalOrgs=true).currentOrgId(scope)
        return AnnualReportArchiveDao.find { (AnnualReportArchives.orgId eq orgId) and (AnnualReportArchives.year eq year) }
            .firstOrNull()?.toModel()
    }

    fun save(vo: AnnualReportArchiveVo): String {
        throw BadRequestException("旧签章归档入口不能登记实际报送；请在年度报告的原件留档中分别登记签署、报送及备案回执")
    }
}
