package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.file.FileContentBaseline
import io.hizhengfu.cloud.common.file.FileReadAccess
import io.hizhengfu.cloud.sys.file.dao.FileDao
import io.hizhengfu.cloud.sys.file.service.FileService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class CollectionExchangePhotoVo(
    val mediaId: String, val fileId: String?, val originalName: String?, val exchangeName: String?,
    val capture: CollectionMediaCaptureVo?, val issues: List<String>,
    val sha256: String? = null, val bytes: Long? = null,
)
data class CollectionExchangeItemVo(
    val collectionId: String, val data: CollectionSurveyExportVo, val catalog: CollectionCatalogContentVo,
    val photos: List<CollectionExchangePhotoVo>, val issues: List<String>,
)
data class CollectionExchangeCheckVo(val items: List<CollectionExchangeItemVo>) {
    val valid: Boolean get() = items.isNotEmpty() && items.all { it.issues.isEmpty() && it.photos.all { photo -> photo.issues.isEmpty() } }
    val issueCount: Int get() = items.sumOf { it.issues.size + it.photos.sumOf { photo -> photo.issues.size } }
}
data class CollectionExchangePackage(val path: Path, val name: String) : AutoCloseable {
    override fun close() { Files.deleteIfExists(path) }
}

/** 交换前逐项核查；缺项仍可查看，但不能在交换包中伪造默认值或省略未通过的照片。 */
@Component
@Transactional(readOnly = true)
class CollectionExchangeService(
    private val collections: CollectionService,
    private val media: CollectionFileService,
    private val files: FileService,
    private val access: FileReadAccess,
) {
    fun check(query: CollectionQueryVo): CollectionExchangeCheckVo {
        val selected = collections.exchangeCollections(query)
        if (selected.size > 2000) throw BadRequestException("单次交换包最多2000条，请缩小检索范围分批导出")
        val result = selected.map { c ->
            val org = c.orgId?.let(CollectionOrgDao::findById) ?: throw BadRequestException("收藏单位缺失")
            val catalog = CollectionCatalogDao.find { CollectionCatalogs.collectionId eq c.id!! }.singleOrNull()
            if(catalog != null && catalog.orgId != c.orgId) throw BadRequestException("编目与藏品所属馆不一致")
            val approved = catalog?.let { row -> CollectionCatalogVersionDao.find {
                (CollectionCatalogVersions.catalogId eq row.id.value) and (CollectionCatalogVersions.catalogStatus eq EnumCatalogStatus.APPROVED)
            }.maxByOrNull { it.version } }
            if(approved != null && (approved.orgId != c.orgId || approved.collectionId != c.id)) throw BadRequestException("已审核编目版本归属不一致")
            val content = approved?.let { CollectionCatalogMapper.mergeCollection(it.content,c) } ?: CollectionCatalogMapper.fromCollection(c)
            val issues = CollectionCatalogPolicy.completeness(content).missingFields.toMutableList()
            if (!CollectionCategoryPolicy.valid(content.categoryId)) issues += "文物分类不属于WW/T 0017—2013附录D的35类"
            if(approved == null) issues += "缺少已审核编目版本"
            if (!CollectionMediaStandard.validCode(org.exchangeCode) || org.exchangeCodeBasis.isNullOrBlank()) issues += "收藏单位14位交换代码及赋码依据待核实"
            val photos = media.listMedia(c.id!!).filter { it.mediaType !in setOf(EnumCollectionMediaType.VIDEO, EnumCollectionMediaType.THREE_D) }.map { m ->
                val photoIssues = CollectionMediaStandard.missing(m.capture).toMutableList()
                val f = try { m.fileId?.let(files::resolveFileId)?.let(FileDao::findById) }
                    catch (e:BadRequestException) { photoIssues += e.message ?: "原照片引用无法解析"; null }
                if (f == null) photoIssues += "原照片文件缺失"
                else access.requireMetadataReadable(f.path, f.storageType.name)
                val name = try {
                    CollectionMediaStandard.validate(m.capture, m.mediaType ?: EnumCollectionMediaType.OTHER)
                    f?.let { CollectionMediaStandard.fileName(org.exchangeCode, c.totalRegNo.orEmpty(), m.capture, it.ext) }
                } catch (e: BadRequestException) { photoIssues += e.message ?: "照片命名不符合规范"; null }
                if (m.capture?.partNo != null && content.registrationDetails?.parts.orEmpty().none { it.partNo == m.capture.partNo })
                    photoIssues += "照片分件号未在本件分件测量记录中登记"
                CollectionExchangePhotoVo(m.id!!, f?.id?.value, f?.name, name, m.capture, photoIssues.distinct())
            }
            if (photos.none { it.capture?.view == EnumCollectionPhotoView.A }) issues += "缺少标准正面照片"
            if (photos.mapNotNull { it.exchangeName }.groupingBy { it }.eachCount().any { it.value > 1 }) issues += "标准照片文件名重复"
            if (photos.mapNotNull { it.capture?.let { v -> v.view?.let { view -> view to v.sequence } } }.groupingBy { it }.eachCount().any { it.value > 1 }) issues += "标准视图顺序号重复"
            CollectionExchangeItemVo(c.id!!, CollectionSurveyExportBuilder.build(c, org.name, org.exchangeCode), content, photos, issues.distinct())
        }
        val names = result.flatMap { it.photos }.mapNotNull { it.exchangeName }
        if (names.distinct().size != names.size) throw BadRequestException("所选收藏单位的交换代码和总登记号形成重复文件名，请先核实赋码及原账")
        return CollectionExchangeCheckVo(result)
    }

    fun build(query: CollectionQueryVo): CollectionExchangePackage {
        val check = check(query)
        if (!check.valid) throw BadRequestException("交换检查未通过（${check.issueCount}项，${check.items.size}条记录），请先查看并补齐缺项")
        val path = Files.createTempFile("collection-exchange-", ".zip")
        try {
            ZipOutputStream(Files.newOutputStream(path), Charsets.UTF_8).use { zip ->
                fun entry(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
                val exported = check.items.map { item -> item.copy(photos = item.photos.map { p ->
                    val f = FileDao.findById(p.fileId!!) ?: throw BadRequestException("照片文件记录已变化")
                    val original = listOf(f.path, f.storageType.name, f.name, f.ext)
                    val evidence = access.readEvidence(f.path, f.storageType.name)
                    if (!evidence.location.protected) throw BadRequestException("照片缺少可核实的藏品文件归属")
                    val storage = files.fileObjectStorage
                    val metadata = evidence.revision?.let { storage.metadataAtRevision(f.path, f.storageType.name, it) }
                        ?: storage.metadata(f.path, f.storageType.name)
                    zip.putNextEntry(ZipEntry("photos/${p.exchangeName}"))
                    val input = evidence.revision?.let { storage.openRevision(f.path, f.storageType.name, it) }
                        ?: storage.openStream(f.path, f.storageType.name)
                    val actual = input.use { FileContentBaseline.read(it, zip, metadata.size) }
                    zip.closeEntry()
                    if (actual.size != metadata.size) throw BadRequestException("照片读取期间字节数变化")
                    evidence.baseline?.requireMatch(actual)
                    f.refresh()
                    if (original != listOf(f.path, f.storageType.name, f.name, f.ext) || access.readEvidence(f.path, f.storageType.name) != evidence)
                        throw BadRequestException("交换期间照片来源或访问资格变化，请重新检查")
                    // 没有历史内容基线的旧照片亦须确认本次输出前后为同一内容。
                    if (evidence.revision == null && CollectionBatchFileContent.read(files, p.fileId).content != actual)
                        throw BadRequestException("交换期间照片内容变化，请重新检查")
                    p.copy(sha256=actual.sha256,bytes=actual.size)
                }) }
                entry("collections.json", JsonUtils.toJson(exported)!!)
                entry("photos.json", JsonUtils.toJson(exported.flatMap { it.photos })!!)
                entry("collections.csv", CollectionExchangeCsv.render(exported))
                entry("quality.json", JsonUtils.toJson(mapOf("checkedAt" to LocalDateTime.now().toString(), "valid" to true,
                    "recordCount" to exported.size, "photoCount" to exported.sumOf { it.photos.size }, "issues" to emptyList<String>()))!!)
                entry("README.txt", "馆藏文物交换包\n字段依据：WW/T 0017—2013；管理程序以《博物馆藏品管理办法》为依据。\n" +
                    "collections.json保存完整著录、分件及影像采集信息；collections.csv供表格核对；photos.json列出原始名、标准名、SHA-256与字节数。\n" +
                    "photos目录保存经授权读取的原照片字节，仅按附录C命名，不修改原系统文件及总登记号。\n" +
                    "取得日期、实收入馆日期、核收完成时间、正式登记时间分别记录；登记件套数与实际数量分列。\n" +
                    "尺寸和质量保留原值及单位，规范显示另列；JSON中的业务日期采用本系统日期序列化格式，CSV采用ISO日期文本。\n" +
                    "本包为本系统交换输出结构，接收平台专有接口需另行映射；不代表主管部门已受理。\n")
            }
            TransactionManager.current().entityCache.clear()
            if (check(query) != check) throw BadRequestException("交换期间藏品或影像资料变化，请重新检查后导出")
            return CollectionExchangePackage(path, "馆藏文物交换包_${LocalDateTime.now().toLocalDate()}.zip")
        } catch (failure: Throwable) { Files.deleteIfExists(path); throw failure }
    }
}

internal object CollectionExchangeCsv {
    fun render(items: List<CollectionExchangeItemVo>): String {
        val header = listOf("机构代码","收藏单位","总登记号","分类号","名称","原名","原编号","年代","质地","级别","登记件套数","登记单位","实际数量","实际单位","原尺寸","尺寸单位","规范尺寸","原质量","质量单位","规范质量","完残程度","现状","保存状态","来源","来源说明","取得日期","实收入馆日期","核收完成时间","正式登记时间","入馆凭证号","结构化著录","照片数")
        val rows = items.map { item -> val d=item.data; listOf(d.exchangeCode,d.orgName,d.totalRegNo,d.classNo,d.name,d.originalName,d.originalNo,d.era,d.texture,d.level,d.quantity,d.unit,d.actualQuantity,d.actualQuantityUnit,d.dimensions,d.dimensionUnit,d.standardDimensions,d.weight,d.weightUnit,d.standardWeight,d.completeness,d.completeStatus,d.preserveState,d.source,d.sourceDesc,d.acquireDate,d.entryDate,d.receiptCompletedAt,d.registeredAt,d.entryVoucherNo,d.registrationDetails?.let(JsonUtils::toJson),item.photos.size) }
        fun cell(value: Any?): String { val raw=value?.toString().orEmpty(); val safe=if(raw.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'$raw" else raw; return "\"${safe.replace("\"","\"\"")}\"" }
        return "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { it.joinToString(",", transform=::cell) } + "\r\n"
    }
}
