package io.hizhengfu.cloud.wms.domain


import cn.idev.excel.annotation.ExcelIgnore
import cn.idev.excel.annotation.ExcelProperty
import java.time.LocalDate

/**
 * 文物登录数据导入
 * */
class WmsArtifactModel {
    @ExcelProperty("名称")
    var name: String? = null

    @ExcelProperty("遗址名称")
    var project: String? = null

    @ExcelIgnore
    var projectId: String? = null

    @ExcelProperty("编号")
    var code: String? = null

    @ExcelProperty("质地")
    var material: String? = null

    @ExcelProperty("数量")
    var quantity: Int? = null

//    @ExcelProperty("数量单位")
//    var quantityUnit: String? = null

    @ExcelProperty("出土单位")
    var projectCell: String? = null

    @ExcelIgnore
    var projectCellId: String? = null

    @ExcelProperty("坐标")
    var coordinate: String? = null

    @ExcelProperty("出土状况")
    var preservation: String? = null

    @ExcelProperty("出土日期")
    var unearthDate: LocalDate? = null

    @ExcelProperty("记录人")
    var recorderBy: String? = null

    @ExcelProperty("备注")
    var remarks: String? = null
}