package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.LoanDao
import io.hizhengfu.cloud.collection.dao.Loans
import io.hizhengfu.cloud.collection.domain.CollectionSafetyAssignee
import io.hizhengfu.cloud.collection.domain.CollectionSafetyTask
import io.hizhengfu.cloud.collection.domain.EnumCollectionSafetyDomain
import io.hizhengfu.cloud.common.exception.BadRequestException
import org.jetbrains.exposed.v1.core.eq
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 借用法定义务来自借用原件与实际履行记录；待办本身不代替催还、检查或备案。 */
@Component
class CollectionLoanSafetyTaskSource(private val statutory:LoanStatutoryService):CollectionSafetyTaskSource {
    override val domain=EnumCollectionSafetyDomain.LOAN

    override fun tasks(orgId:String,at:LocalDateTime):List<CollectionSafetyTask> =
        LoanDao.find {Loans.orgId eq orgId}.filter {it.outExecuted}.flatMap {loan->
            try {tasks(loan,at)} catch(e:BadRequestException) {
                listOf(safetyTask(orgId,domain,"ORIGINAL_ERROR","借用履行原件需核查",loan.id.value,
                    name=loan.title,at=at,basis="ORIGINAL_ERROR",status="ORIGINAL_ERROR",error=e.message))
            }
        }

    internal fun tasks(loan:LoanDao,at:LocalDateTime):List<CollectionSafetyTask> {
        val org=loan.orgId;val source=loan.id.value;val dossier=loan.dossier
        if(dossier==null || loan.baseline==null)return listOf(safetyTask(org,domain,"MISSING_DOSSIER",
            "已借出藏品缺少法定义务原件",source,name=loan.title,at=at,basis="MISSING_DOSSIER",status="ORIGINAL_ERROR"))
        val obligations=statutory.obligations(loan,at)
        val keeper=CollectionSafetyAssignee(dossier.keeperUserId)
        val supervisor=CollectionSafetyAssignee(dossier.supervisionUserId)
        val leader=CollectionSafetyAssignee(dossier.leaderUserId)
        val operator=loan.operator?.takeIf {it.isNotBlank()}?.let(::CollectionSafetyAssignee)
        val managers=listOfNotNull(operator,keeper,leader).distinctBy {it.userId}
        val inspectors=listOf(keeper,supervisor).distinctBy {it.userId}
        fun rows(key:String)=((obligations[key] as? List<*>) ?: emptyList<Any>()).mapNotNull {it as? Map<*,*>}
        fun ids(key:String)=((obligations[key] as? List<*>) ?: emptyList<Any>()).filterIsInstance<String>()
        fun due(date:Any?)=when(date) {
            is LocalDateTime -> date
            is LocalDate -> date.atTime(LocalTime.MAX)
            else -> null
        }
        fun task(kind:String,title:String,basis:Any,people:List<CollectionSafetyAssignee>,record:String?=null,
                 collection:String?=null,date:Any?=null)=safetyTask(org,domain,kind,title,source,record,collection,
            collection ?: loan.title,due(date),at,basis,people)
        return buildList {
            rows("returnDeadlines").forEach {row->
                val cid=row["collectionId"] as? String ?: return@forEach
                add(task("RETURN_DUE","借出藏品催还与归还期限",row,managers,collection=cid,date=row["dueAt"]))
            }
            rows("inspections").forEach {row->
                val cid=row["collectionId"] as? String ?: return@forEach
                add(task("INSPECTION_DUE","借出藏品定期安全检查",row,inspectors,collection=cid,date=row["nextDueOn"]))
            }
            rows("filings").filter {it["submittedOn"]==null}.forEach {row->
                val level=row["level"]?.toString() ?: return@forEach
                add(task("ADMIN_FILING","借出后行政备案",row,managers,record=level,date=row["dueOn"]))
            }
            rows("overseasFilings").filter {it["submittedOn"]==null}.forEach {row->
                val kind=row["kind"]?.toString() ?: return@forEach
                add(task("OVERSEAS_FILING","境外展览备案或期限核实",row,managers,record=kind,date=row["dueOn"]))
            }
            ids("unresolvedIssueIds").forEach {id->
                add(task("ISSUE_RESOLUTION","借出期间发现问题待处理",id,inspectors,record=id))
            }
            ids("pendingMajorReportIds").forEach {id->
                add(task("MAJOR_REPORT","借出期间重大情况待报告",id,listOf(leader,keeper),record=id))
            }
            ids("heldForReviewCollectionIds").forEach {cid->
                add(task("RETURN_HELD","返还点交后藏品待复核",cid,inspectors,collection=cid))
            }
        }
    }
}
