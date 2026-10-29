package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.exposed.UIDTable
import io.hizhengfu.cloud.sys.file.service.FileUploadTargetSchema
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.install.AbstractInstall
import io.hizhengfu.cloud.sys.file.service.FileUploadTargetGuard
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.unionAll
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 识别当前主记录及附件子记录，不以读取资格、管理员或拥有文件替代业务办理确认。 */
@Component
@Transactional
class CollectionPublicUploadTargetGuard(
    private val sources: ObjectProvider<FileUploadTargetSchema>,
    private val installs: ObjectProvider<AbstractInstall>,
) : FileUploadTargetGuard {
    override fun requirePublicBindingAllowed(contentId: String) {
        val tables = sources.orderedStream().toList().flatMap { it.publicUploadBlockedTables() }.distinct().map {
            it as? UIDTable ?: throw BadRequestException("业务附件目标表尚未配置字符串编号检查，请核对模块结构")
        }
        if (contains(tables, contentId))
            throw BadRequestException("藏品业务材料须先上传文件，再从原业务办理入口核对并关联，不能通过公共上传直接挂接")

        // 未出现的编号不是普通业务身份：否则提前写入的关联会在后来建档时变成其材料。
        // 仅接受当前安装器已声明且确实存在的非受限字符串主键记录；不按编号外观或请求参数猜测模块。
        val blockedNames = tables.map { it.tableName }.toSet()
        val ordinary = installs.orderedStream().toList().flatMap { it.initTables().orEmpty() }
            .filterIsInstance<UIDTable>().filter { it.tableName !in blockedNames }.distinctBy { it.tableName }
        if (!contains(ordinary, contentId))
            throw BadRequestException("无法确认待关联的业务记录，请先上传文件，再从原业务办理入口核对并关联")
    }

    /** 不缓存记录存在性；关联前后均从当前事务核对，批量主键查询避免逐表往返。 */
    private fun contains(tables: List<UIDTable>, contentId: String): Boolean {
        val queries = tables.map { table -> table.select(table.id).where { table.id eq contentId } }
        return when (queries.size) {
            0 -> false
            1 -> queries.single().limit(1).any()
            else -> queries.drop(2).fold(queries[0].unionAll(queries[1])) { combined, query -> combined.unionAll(query) }.limit(1).any()
        }
    }
}
