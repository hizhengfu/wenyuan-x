package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.CollectionCarePlanDao
import java.time.LocalDateTime

/** 新方案不能抹去已经漏做的旧周期；按真实检查/完成时间计算，不按页面刷新时间滚动。 */
internal object CollectionCareSchedule {
    fun next(plans:List<CollectionCarePlanDao>,actual:List<LocalDateTime>,interval:(CollectionCarePlanDao)->Int):LocalDateTime? {
        val approved=plans.filter {it.keeper!=null};val current=approved.lastOrNull() ?: return null
        val first=approved.first().keeper!!.confirmedAt
        val last=actual.maxOrNull()
        var due=(last ?: first).plusDays(interval(current).toLong())
        approved.zipWithNext().forEach {(old,next)->
            val change=next.keeper!!.confirmedAt
            val prior=actual.filter {it<=change}.maxOrNull() ?: first
            val missed=prior.plusDays(interval(old).toLong())
            if(missed<=change && (last==null || last<missed))due=minOf(due,missed)
        }
        return due
    }
}
