package io.hizhengfu.cloud.collection.domain

import io.hizhengfu.cloud.common.exception.BadRequestException
import java.time.LocalDate

/** WW/T 0017 附录 C 的视图编码，不用“背面”替代“底视图”。 */
enum class EnumCollectionPhotoView { A, B, C, D, E, F }
data class CollectionMediaCaptureVo(
    val view: EnumCollectionPhotoView? = null,
    val sequence: Int? = null,
    val partNo: String? = null,
    val institution: String? = null,
    val location: String? = null,
    val photographedOn: LocalDate? = null,
    val photographer: String? = null,
    val accessoriesIncluded: Boolean? = null,
    val scaleDescription: String? = null,
)
data class CollectionStandardPhotoName(val institutionCode: String, val totalRegNo: String, val view: EnumCollectionPhotoView, val sequence: Int, val extension: String)
object CollectionMediaStandard {
    private val pattern=Regex("^([0-9]{14})-(.+)-([A-F])-([0-9]+)\\.([A-Za-z0-9]+)$")
    fun validCode(code: String?): Boolean = code?.matches(Regex("[0-9]{14}")) == true
    private fun validNumber(value: String) = value.isNotBlank() && value.all { (it.code in 33..126 && it !in "\\/:,*?\"<>|()[]{}") || it in "：，／＼＊？＂＜＞｜（）［］｛｝【】〔〕〈〉《》「」『』" }
    fun parse(fileName: String?): CollectionStandardPhotoName? {
        val match=fileName?.substringAfterLast('/')?.substringAfterLast('\\')?.let(pattern::matchEntire) ?: return null
        val (code,regNo,view,index,extension)=match.destructured
        val sequence=index.toIntOrNull()?.takeIf { it>0 } ?: return null
        if(!validNumber(regNo)) return null
        return CollectionStandardPhotoName(code,regNo,enumValueOf(view),sequence,extension.lowercase())
    }
    fun fileName(code: String?, regNo: String, capture: CollectionMediaCaptureVo?, extension: String): String {
        if(!validCode(code))throw BadRequestException("收藏单位须填写经核实的14位规范交换代码，内部代码不能代替")
        if(!validNumber(regNo))throw BadRequestException("总登记号不满足规范文件名字符要求，请核对原号；系统不会改写总登记号")
        val view=capture?.view ?: throw BadRequestException("照片缺少 A-F 标准视图")
        val sequence=capture.sequence?.takeIf {it>0} ?: throw BadRequestException("照片缺少正整数顺序号")
        val ext=extension.lowercase().removePrefix(".")
        if(ext !in setOf("jpg","jpeg","png","tif","tiff","bmp")) throw BadRequestException("规范照片交换须为图像格式")
        return "$code-$regNo-${view.name}-$sequence.$ext"
    }
    fun mediaType(view: EnumCollectionPhotoView) = when(view) {
        EnumCollectionPhotoView.A -> EnumCollectionMediaType.FRONT
        EnumCollectionPhotoView.B -> EnumCollectionMediaType.TOP
        EnumCollectionPhotoView.C -> EnumCollectionMediaType.SIDE
        EnumCollectionPhotoView.D -> EnumCollectionMediaType.PANORAMIC
        EnumCollectionPhotoView.E -> EnumCollectionMediaType.DETAIL
        EnumCollectionPhotoView.F -> EnumCollectionMediaType.BOTTOM
    }
    fun missing(capture: CollectionMediaCaptureVo?): List<String> = buildList {
        if(capture?.view==null)add("标准视图")
        if((capture?.sequence ?: 0)<=0)add("图片顺序号")
        if(capture?.institution.isNullOrBlank())add("拍摄单位")
        if(capture?.location.isNullOrBlank())add("拍摄地点")
        if(capture?.photographedOn==null)add("拍摄日期")
        if(capture?.photographer.isNullOrBlank())add("拍摄人")
    }
    fun validate(capture: CollectionMediaCaptureVo?, type: EnumCollectionMediaType) {
        if(capture==null)return
        if(capture.sequence!=null && capture.sequence<=0)throw BadRequestException("图片顺序号须为正整数")
        if(capture.photographedOn?.isAfter(LocalDate.now())==true)throw BadRequestException("拍摄日期不能晚于今天")
        if(capture.view!=null && mediaType(capture.view)!=type)throw BadRequestException("标准视图与影像类型不一致")
        if((capture.partNo?.length ?: 0) >100)throw BadRequestException("分件号过长")
    }
}
