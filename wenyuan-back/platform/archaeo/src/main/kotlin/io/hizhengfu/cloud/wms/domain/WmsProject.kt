package io.hizhengfu.cloud.wms.domain

import com.fasterxml.jackson.annotation.JsonProperty


/**
 * 文物扩展数据参数
 * */
data class WmsProjectTree(
    val id: String,
    val projectId: String,
    val plotId: String? = null,
    val name: String,
    val type: String,
    val disabled: Boolean? = null,
    val checkboxDisabled: Boolean? = null,
    val plotCount: Long? = null,
    val cellCount: Long? = null,
    val children: List<WmsProjectTree>? = null,
) {
    @JsonProperty("isLeaf")
    fun isLeaf(): Boolean {
        if (plotCount != null && plotCount > 0) return false
        if (cellCount != null && cellCount > 0) return false
        return true
    }
}

/**
 * 探方与遗迹关联
 * */
data class WmsProjectPlotCellAssociation(
    val plotId: String,
    val ids: List<String>,
)