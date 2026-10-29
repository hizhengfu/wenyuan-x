package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.CollectionOrgDao
import io.hizhengfu.cloud.collection.domain.CollectionImportConfirmationVo
import io.hizhengfu.cloud.common.exception.BadRequestException
import java.security.MessageDigest

/** 核对操作者预览过的业务依据；不替代当前权限，也不表示正式登记或签署。 */
internal object CollectionImportConfirmation {
    fun hash(value: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(JsonUtils.toJson(value)!!.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun create(orgId: String, basis: Any): CollectionImportConfirmationVo = CollectionImportConfirmationVo(
        orgId, CollectionOrgDao.findById(orgId)?.name ?: throw BadRequestException("导入收藏单位不存在"), hash(basis),
    )

    fun requireSame(current: CollectionImportConfirmationVo?, expectedHash: String?) {
        if (expectedHash != null && (expectedHash.isBlank() || current?.validationHash != expectedHash))
            throw BadRequestException("导入资料或匹配结果已变化，请重新预校验并核对后提交")
    }
}
