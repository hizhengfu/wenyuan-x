package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.service.CollectionFragilityEvidence as E
import io.hizhengfu.cloud.collection.service.CollectionFragileMonitoringEvidence as M
import io.hizhengfu.cloud.collection.service.CollectionFragileAutomaticEvidence as A
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/** 每次调用独立事务，按藏品行锁串行。旧实际记录在调度中补建运行状态。 */
@Component
@Transactional
class CollectionFragileAutomaticMonitoringService(
    private val archives:CollectionPermanentArchiveService,
    private val providers:List<CollectionFragileEnvironmentProvider>,
    private val clock:Clock=Clock.systemDefaultZone(),
) {
    private fun now()=LocalDateTime.now(clock)
    @Transactional(readOnly=true)
    fun nextWakeAt():LocalDateTime? {
        val r=CollectionFragileMonitorRuntimes;val next=r.nextAttemptAt.min()
        val at=r.select(next).where {r.completed eq false}.single()[next]
        val missing=!CollectionFragileUseRecords.select(CollectionFragileUseRecords.id).where {
            CollectionFragileUseRecords.started.isNotNull() and CollectionFragileUseRecords.ended.isNull() and
                (CollectionFragileUseRecords.id notInSubQuery r.select(r.id))
        }.limit(1).empty()
        return if(missing)now() else at
    }
    @Transactional(readOnly=true)
    fun due():List<String> {
        val at=now()
        val running=CollectionFragileMonitorRuntimeDao.find {(CollectionFragileMonitorRuntimes.completed eq false) and (CollectionFragileMonitorRuntimes.nextAttemptAt lessEq at)}
            .orderBy(CollectionFragileMonitorRuntimes.nextAttemptAt to SortOrder.ASC).limit(100).map {it.id.value}
        val missing=CollectionFragileUseRecordDao.find {CollectionFragileUseRecords.started.isNotNull() and CollectionFragileUseRecords.ended.isNull() and
            (CollectionFragileUseRecords.id notInSubQuery CollectionFragileMonitorRuntimes.select(CollectionFragileMonitorRuntimes.id))}
            .orderBy(CollectionFragileUseRecords.id to SortOrder.ASC).limit(100).map {it.id.value}
        return (running+missing).distinct()
    }
    private fun locked(useId:String):Pair<CollectionFragileUseRecordDao,CollectionFragileMonitorRuntimeDao> {
        // 先只读取定位列，再按与人工结束相同的藏品锁顺序读取实际使用行。
        // 避免等锁之前缓存的 ended 导致把真实结束后的时间误当成仍在使用。
        val collectionId=CollectionFragileUseRecords.select(CollectionFragileUseRecords.collectionId)
            .where {CollectionFragileUseRecords.id eq useId}.singleOrNull()?.get(CollectionFragileUseRecords.collectionId) ?: E.fail("自动核验的实际使用不存在")
        val c=CollectionDao.find {Collections.id eq collectionId}.forUpdate().singleOrNull() ?: E.fail("自动核验的藏品不存在")
        val use=CollectionFragileUseRecordDao.find {CollectionFragileUseRecords.id eq useId}.forUpdate().singleOrNull() ?: E.fail("自动核验的实际使用不存在")
        if(c.orgId!=use.orgId || use.started==null)E.fail("自动核验的本馆实际使用来源需核实")
        val state=CollectionFragileMonitorRuntimeDao.findById(useId) ?: CollectionFragileMonitorRuntimeDao.new(useId) {orgId=use.orgId;this.collectionId=use.collectionId;nextAttemptAt=now()}
        if(state.orgId!=use.orgId || state.collectionId!=use.collectionId)E.fail("自动核验运行状态归属需核实")
        return use to state
    }
    fun scan(useId:String):String? {
        val (use,state)=locked(useId);val at=now()
        if(state.completed || state.nextAttemptAt>at)return null
        E.record(use)
        val prior=A.forUse(useId).lastOrNull()?.also(A::verify)
        val previousTo=prior?.toAt ?: use.started!!.startedAt
        val target=minOf(at,use.ended?.endedAt ?: at)
        if(target==previousTo) {state.lastAttemptAt=at;state.lastError=null;state.nextAttemptAt=at.plusSeconds(60);state.completed=use.ended!=null;return null}
        // 五分钟仅为技术分批大小；数值限值和采样有效期全部来自本件已确认评估。
        val to=minOf(target,previousTo.plusMinutes(5));val from=minOf(previousTo,to)
        val reports=M.all(use.collectionId).filter {it.useRecordId==useId}.also {it.forEach(M::verify)}
        val originals=reports.flatMap {r->r.request.readings.map {it to r.request.checkedAt}}
        val (metrics,baseIssues)=CollectionFragilePeriodEvidence.evaluate(use,from,to,originals,providers)
        val issues=baseIssues+if(target<previousTo)listOf("后来登记的真实结束早于已保存自动核验区间，须保留原件并核实矛盾") else emptyList()
        val problem=issues.isNotEmpty() || metrics.any {it.needsHandling}
        val body=mapOf("previousAutomaticId" to prior?.id?.value,"from" to from,"to" to to,"metrics" to metrics,"issues" to issues,"needsHandling" to problem,
            "scope" to "自动按已知采样及接收或原件形成时间核验；不证明未采样时刻实物环境持续合格，不代替现场检查、停止、报告和实际处置。")
        val row=CollectionFragileAutomaticCheckDao.new {orgId=use.orgId;collectionId=use.collectionId;useRecordId=useId;observedAt=at;fromAt=from;toAt=to;needsHandling=problem;evidenceSnapshot=E.json(body)}
        archives.seal(use.orgId,E.BIZ,row.id.value,"AUTOMATIC_MONITORED",A.value(row),null)
        state.lastAttemptAt=at;state.lastError=null;state.completed=use.ended!=null && to==target
        state.nextAttemptAt=if(to<target)at else at.plusSeconds(60)
        return row.id.value
    }
    /** 在失败事务回滚后另行保存，保留原游标，重试不能跳过失败区间。 */
    fun failed(useId:String) {
        val (_,state)=locked(useId)
        state.lastAttemptAt=now();state.lastError="自动采样证据核验失败，尚未覆盖的区间须核实；系统将重试，请检查来源与服务。"
        state.nextAttemptAt=now().plusSeconds(60);state.completed=false
    }
}
