package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.CollectionConst
import io.hizhengfu.cloud.collection.dao.CollectionReturnDao
import org.springframework.stereotype.Component

/** 调用方已核对当前业务权限；跨模块保管来源也须使用同一接收原件和文件清单。 */
@Component
class CollectionReturnOriginalEvidence(private val archives: CollectionPermanentArchiveService) {
    fun requireReceived(row: CollectionReturnDao) {
        val original=ReturnResponsibilityEvidence.received(row)
        archives.verifyExisting(original,row.orgId,CollectionConst.BIZ_RETURN,row.id.value,"RECEIVED",
            JsonUtils.mapper.readTree(original.snapshot),returnFiles(row))
    }
}
