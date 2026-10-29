package io.hizhengfu.cloud.collection.service

import io.hizhengfu.cloud.autoconfigure.jackson.JsonUtils
import io.hizhengfu.cloud.collection.common.*
import io.hizhengfu.cloud.collection.dao.*
import io.hizhengfu.cloud.collection.domain.*
import io.hizhengfu.cloud.common.domain.SecurityUser
import io.hizhengfu.cloud.common.exception.BadRequestException
import io.hizhengfu.cloud.common.utils.IdGenerate
import io.hizhengfu.cloud.common.utils.SecurityUtils
import io.hizhengfu.cloud.sys.system.dao.Users
import io.hizhengfu.cloud.sys.system.dao.Roles
import io.hizhengfu.cloud.sys.file.dao.FileDao
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.dao.entityCache
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Component
@Transactional
class CollectionStaffEducationService(
    private val access:StaffEducationAccess,
    private val scope:CollectionScopeResolver,
    private val directory:CollectionActorDirectory,
    private val archives:CollectionPermanentArchiveService,
) {
    companion object {const val BIZ="COLLECTION_STAFF_EDUCATION"}
    private fun fail(message:String):Nothing=throw BadRequestException(message)
    private fun now()=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    /** 等馆和记录行锁后、实际归档及文件绑定后，都按当前目录核对同一本人。 */
    private fun <T> write(orgId:String,educationId:String?=null,action:(SecurityUser,((()->Unit)->Unit))->T):T {
        val actorId=access.actor().id
        access.require(orgId,write=true)
        val org=CollectionOrgDao.find {CollectionOrgs.id eq orgId}.forUpdate().single()
        val education=educationId?.let {id -> CollectionStaffEducationDao.find {CollectionStaffEducations.id eq id}.forUpdate().single()}
        fun current():SecurityUser {
            TransactionManager.current().entityCache.flush()
            org.refresh(flush=false)
            education?.refresh(flush=false)
            if(education!=null && education.orgId!=orgId)fail("办理期间教育记录所属馆已变化，请重新核实")
            if(SecurityUtils.getNullUserDetail()?.id!=actorId)fail("办理期间岗位教育账号已变化，请重新登录核实")
            return access.require(orgId,write=true)
        }
        val actor=current()
        val checks=mutableListOf<()->Unit>()
        val result=action(actor) {checks.add(it)}
        current()
        checks.forEach {it()}
        return result
    }
    /** 重新读取实际 SQL 后的正文、版本、归档与保护索引，不用事务实体缓存代替核验。 */
    private fun recheckStored(id:String,orgId:String,userId:String,key:String) {
        TransactionManager.current().entityCache.flush()
        TransactionManager.current().entityCache.clear()
        val e=CollectionStaffEducationDao.findById(id) ?: fail("办理期间教育记录已缺失")
        if(e.orgId!=orgId || e.userId!=userId || e.requestKey!=key)fail("办理期间教育记录人员或身份已变化，请重新核实")
        verify(e)
    }
    fun context():Map<String,Any?> {
        val actor=access.actor();val orgIds=scope.currentReadableOrgIds(actor,CollectionConst.CROSS_ORG)
        return mapOf("orgs" to CollectionOrgDao.all().filter {(orgIds==null || it.id.value in orgIds) && access.configured(it.id.value)}.map {mapOf("id" to it.id.value,"name" to it.name)})
    }
    private fun rows(id:String)=CollectionStaffEducationVersionDao.find {CollectionStaffEducationVersions.educationId eq id}.sortedBy {it.version}
    private fun phase(id:String,phase:String)=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq id) and (CollectionPermanentArchives.phase eq phase)}.singleOrNull()
    private fun files(v:StaffEducationVersionRecord)=listOfNotNull(v.content.plan.needsFileIds,v.content.plan.dueBasisFileIds,v.content.participation?.evidenceFileIds,v.content.withdrawal?.fileIds,v.fileIds).joinToString(",")
    private fun seal(e:CollectionStaffEducationDao,phase:String,value:Any,files:String?) {
        val archiveId=archives.seal(e.orgId,BIZ,e.id.value,phase,value,files)
        CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq archiveId}.forEach {ref ->
            val file=FileDao.findById(ref.fileId) ?: fail("教育原件不存在")
            archives.fileReadKeys(file).forEach {key ->
                if(CollectionStaffEducationFileAccessDao.find {(CollectionStaffEducationFileAccesses.educationId eq e.id.value) and (CollectionStaffEducationFileAccesses.fileId eq ref.fileId) and (CollectionStaffEducationFileAccesses.pathKey eq key)}.empty())
                    CollectionStaffEducationFileAccessDao.new {orgId=e.orgId;educationId=e.id.value;fileId=ref.fileId;pathKey=key}
            }
        }
    }
    private fun exact(e:CollectionStaffEducationDao,p:String,value:Any,files:String?):CollectionPermanentArchiveDao {
        val a=phase(e.id.value,p) ?: fail("教育记录缺少永久原件：$p")
        if(a.orgId!=e.orgId || a.digest!=CollectionCareEvidence.digest(a.snapshot) || JsonUtils.mapper.readTree(a.snapshot)!=JsonUtils.mapper.readTree(JsonUtils.toJson(value)!!))fail("教育记录与归档原件不一致")
        archives.verifyExisting(a,e.orgId,BIZ,e.id.value,p,value,files)
        CollectionPermanentArchiveFileDao.find {CollectionPermanentArchiveFiles.archiveId eq a.id.value}.forEach {f ->
            if(CollectionStaffEducationFileAccessDao.find {(CollectionStaffEducationFileAccesses.educationId eq e.id.value) and (CollectionStaffEducationFileAccesses.fileId eq f.fileId) and (CollectionStaffEducationFileAccesses.orgId eq e.orgId)}.empty())fail("教育原件读取保护记录缺失")
        }
        return a
    }
    private fun verify(e:CollectionStaffEducationDao):List<CollectionStaffEducationVersionDao> = try {
        val o=e.original
        if(o.id!=e.id.value || o.orgId!=e.orgId || o.request.userId!=e.userId || o.request.requestKey!=e.requestKey)fail("教育记录人员或归属与原件不一致")
        exact(e,"ORIGINAL",o,o.request.employmentFileIds)
        val versions=rows(e.id.value)
        if(versions.isEmpty() || versions.map {it.version}!=(1..versions.size).toList())fail("教育记录版本不连续")
        var previous:String?=null
        versions.forEach {v ->
            val c=v.content
            if(v.orgId!=e.orgId || c.educationId!=e.id.value || c.version!=v.version || c.previousDigest!=previous)fail("教育记录版本链不一致")
            if(v.version==1 && c.content!=StaffEducationContent(o.request.plan))fail("首次教育需求与原登记不一致")
            val a=exact(e,"V_${v.version}",c,files(c));previous=a.digest
            val confirmation=phase(e.id.value,"REVIEW_${v.version}")
            if((confirmation==null)!=(v.confirmation==null))fail("教育核实记录或签署原件缺失")
            v.confirmation?.let {r ->
                if(c.content.participation==null || c.content.withdrawal!=null || r.confirmedAt.isBefore(c.registeredAt))fail("教育核实与参加记录或版本顺序不一致")
                exact(e,"REVIEW_${v.version}",r,r.content.fileIds)
            }
        }
        val expectedPhases=setOf("ORIGINAL")+versions.map {"V_${it.version}"}+versions.filter {it.confirmation!=null}.map {"REVIEW_${it.version}"}
        val archivedPhases=CollectionPermanentArchiveDao.find {(CollectionPermanentArchives.bizType eq BIZ) and (CollectionPermanentArchives.bizId eq e.id.value)}.map {it.phase}.toSet()
        if(expectedPhases!=archivedPhases)fail("教育当前记录遗漏已归档版本或核实意见")
        versions
    } catch (_:tools.jackson.core.JacksonException){fail("教育原件无法解析，请核对历史材料")}
    private fun load(id:String):Pair<CollectionStaffEducationDao,SecurityUser> {
        val e=CollectionStaffEducationDao.findById(id) ?: fail("岗位教育记录不存在")
        return e to access.require(e.orgId,e.userId)
    }
    private fun validate(v:StaffEducationContent) {
        val p=v.plan
        if(listOf(p.title,p.dutyScope,p.skillsNeeded,p.needsFileIds).any(String::isBlank))fail("请明确实际岗位、技能需求及依据原件")
        if(p.identifiedAt.isAfter(now()))fail("不能预填岗位需求已核实的未来时间")
        if(p.dueAt!=null && (p.dueBasisFileIds.isNullOrBlank() || p.dueAt.isBefore(p.identifiedAt)))fail("馆方指定期限须有真实依据且不得早于需求确认")
        if(p.dueAt==null && !p.dueBasisFileIds.isNullOrBlank())fail("请同时核实所填期限依据及具体期限")
        v.participation?.let {a ->
            if(listOf(a.provider,a.courseTitle,a.skillsCovered,a.evidenceFileIds).any(String::isBlank))fail("请填写实际培训机构、课程、技能内容和参加原件")
            if(a.endedAt.isBefore(a.startedAt) || a.endedAt.isAfter(now()))fail("实际参加时间须先开始后结束，不能填写未来完成事实")
            if(a.actualMinutes!=null && a.actualMinutes<=0)fail("实际培训时长应大于零；没有原件依据时可不填写")
        }
        v.withdrawal?.let {if(it.reason.isBlank() || it.fileIds.isBlank())fail("需求终止须说明真实原因并保留依据，不能记作培训已完成")}
    }
    fun create(orgId:String,v:StaffEducationCreate):String=write(orgId) {actor,recheck ->
        val existing=CollectionStaffEducationDao.find {(CollectionStaffEducations.orgId eq orgId) and (CollectionStaffEducations.requestKey eq v.requestKey)}.singleOrNull()
        if(existing!=null){
            val existingId=existing.id.value;val userId=existing.userId;val key=existing.requestKey
            recheck {recheckStored(existingId,orgId,userId,key)}
            verify(existing)
            if(existing.original.request==v && existing.original.registeredBy==actor.id)return@write existingId
            fail("该登记请求已使用，请重新核对原记录")
        }
        if(v.requestKey.isBlank() || v.requestKey.length>64 || v.employmentFileIds.isBlank())fail("缺少登记请求标识或真实任职依据")
        val person=Users.select(Users.id,Users.name).where {Users.id eq v.userId}.singleOrNull() ?: fail("人员账号不存在，请核对实际人员标识")
        if(!v.historicalPerson) {
            val a=directory.requireActive(v.userId)
            if(!scope.isMuseumMember(a,orgId))fail("当前人员须实际属于本馆；历史人员应依据真实任职原件单独补录")
            recheck {
                if(!scope.isMuseumMember(directory.requireActive(v.userId),orgId))fail("登记期间所选人员已不属于本馆，请重新核实任职依据")
            }
        } else if(!access.responsible(actor,orgId))fail("历史人员补录须由本馆现任保管负责人或主要负责人依据任职原件办理")
        if(v.historicalPerson)recheck {if(!access.responsible(access.actor(),orgId))fail("登记期间历史人员补录职责已变化，请由本馆现任负责人核实")}
        validate(StaffEducationContent(v.plan))
        val id=IdGenerate.nextId();val o=StaffEducationOriginal(id,orgId,person[Users.name],v,actor.id,now())
        val e=CollectionStaffEducationDao.new(id){this.orgId=orgId;userId=v.userId;requestKey=v.requestKey;original=o}
        recheck {recheckStored(id,orgId,v.userId,v.requestKey)}
        seal(e,"ORIGINAL",o,v.employmentFileIds)
        append(e,StaffEducationVersionRecord(id,1,StaffEducationContent(v.plan),null,null,null,actor.id,now()))
        id
    }
    private fun append(e:CollectionStaffEducationDao,c:StaffEducationVersionRecord) {
        CollectionStaffEducationVersionDao.new {orgId=e.orgId;educationId=e.id.value;version=c.version;content=c}
        seal(e,"V_${c.version}",c,files(c))
    }
    fun revise(id:String,v:StaffEducationRevision):String {
        val orgId=CollectionStaffEducationDao.findById(id)?.orgId ?: fail("岗位教育记录不存在")
        return write(orgId,id) {actor,recheck ->
            val e=CollectionStaffEducationDao[id];val userId=e.userId;val key=e.requestKey
            recheck {recheckStored(id,orgId,userId,key)}
            val versions=verify(e);val last=versions.last()
            if(last.version==v.expectedVersion+1 && last.content.content==v.content && last.content.reason==v.reason && last.content.fileIds==v.fileIds && last.content.registeredBy==actor.id)return@write id
            if(last.version!=v.expectedVersion)fail("记录已更新，请重新读取后补充或更正")
            if(v.reason.isBlank() || v.fileIds.isBlank())fail("补充或更正须说明原因并保留依据，旧记录和核实意见不会覆盖")
            if(v.content.withdrawal!=null && !access.responsible(actor,e.orgId))fail("终止岗位教育需求须由本馆现任保管负责人或主要负责人办理")
            if(v.content.withdrawal!=null)recheck {if(!access.responsible(access.actor(),orgId))fail("办理期间终止教育需求的本人职责已变化，请重新核实")}
            validate(v.content)
            append(e,StaffEducationVersionRecord(id,last.version+1,v.content,phase(id,"V_${last.version}")!!.digest,v.reason,v.fileIds,actor.id,now()))
            id
        }
    }
    fun confirm(id:String,version:Int,v:StaffEducationConfirmationVo):String {
        val orgId=CollectionStaffEducationDao.findById(id)?.orgId ?: fail("岗位教育记录不存在")
        return write(orgId,id) {actor,recheck ->
            val e=CollectionStaffEducationDao[id];val userId=e.userId;val key=e.requestKey
            recheck {recheckStored(id,orgId,userId,key)}
            val latest=verify(e).last()
            if(!access.responsible(actor,e.orgId))fail("须由本馆现任保管负责人或主要负责人本人核实，管理员不能代签")
            recheck {if(!access.responsible(access.actor(),orgId))fail("办理期间教育需求核实的本人职责已变化，请重新核实")}
            if(latest.version!=version)fail("该版本已有后续补充，请核实最新记录")
            if(latest.content.content.withdrawal!=null)fail("需求已终止，不能记作已培训")
            val activity=latest.content.content.participation ?: fail("尚未登记实际参加情况，不能确认已经完成需求")
            if(v.opinion.isBlank() || v.fileIds.isBlank())fail("请填写本人核实意见和实际签署依据")
            if(v.meetsNeed && activity.outcome!=EnumStaffEducationOutcome.COMPLETED)fail("仅参加或未完成课程不能核实为已满足岗位需求")
            latest.confirmation?.let {if(it.actorUserId==actor.id && it.content==v)return@write id;fail("核实意见已留存；更正须追加版本，不能覆盖原签署")}
            val c=StaffEducationConfirmation(actor.id,now(),v);latest.confirmation=c;seal(e,"REVIEW_$version",c,v.fileIds)
            id
        }
    }
    private fun state(v:CollectionStaffEducationVersionDao):String=when {
        v.content.content.withdrawal!=null->"WITHDRAWN"
        v.content.content.participation==null->"PLANNED"
        v.confirmation?.content?.meetsNeed==true->"VERIFIED"
        v.confirmation?.content?.meetsNeed==false->"NEEDS_SUPPLEMENT"
        else->"RECORDED"
    }
    private fun item(e:CollectionStaffEducationDao):Map<String,Any?> {
        return try {
            val v=verify(e).last();val p=v.content.content.plan
            val participation=v.content.content.participation
            val courseCompleted=participation?.outcome==EnumStaffEducationOutcome.COMPLETED
            val completedLate=courseCompleted && p.dueAt!=null && participation.endedAt.isAfter(p.dueAt)
            mapOf("id" to e.id.value,"userId" to e.userId,"personName" to e.original.personName,"version" to v.version,"content" to v.content.content,"state" to state(v),
                "overdue" to (v.content.content.withdrawal==null && p.dueAt?.isBefore(now())==true && v.confirmation?.content?.meetsNeed!=true && !courseCompleted),
                "completedLate" to completedLate,"completionInTimePendingReview" to (courseCompleted && p.dueAt!=null && !completedLate && v.confirmation?.content?.meetsNeed!=true),
                "historicalPerson" to e.original.request.historicalPerson)
        }catch(ex:BadRequestException){mapOf("id" to e.id.value,"userId" to e.userId,"state" to "EVIDENCE_REVIEW_REQUIRED","evidenceErrors" to listOf(ex.message))}
    }
    fun detail(id:String):Map<String,Any?> {
        val (e,actor)=load(id)
        if(!StaffEducationReadIdentity.valid(e)) return mapOf("id" to id,"orgId" to e.orgId,"userId" to e.userId,
            "state" to "EVIDENCE_REVIEW_REQUIRED","evidenceErrors" to listOf("教育记录人员或版本归属与原件不一致，不能返回原始材料"),
            "versions" to emptyList<Any>(),"archives" to emptyList<Any>(),"canEdit" to false,"canConfirm" to false)
        var summary=item(e)
        val originalArchives=try { archives.listForAuthorizedOrg(BIZ,id,e.orgId) } catch(ex:BadRequestException) {
            summary=summary+mapOf("state" to "EVIDENCE_REVIEW_REQUIRED","evidenceErrors" to listOf(ex.message))
            emptyList()
        }
        val writable=summary["evidenceErrors"]==null && access.canWrite(actor,e.orgId)
        return summary+mapOf("orgId" to e.orgId,"original" to e.original,"versions" to rows(id).map {mapOf("record" to it.content,"confirmation" to it.confirmation)},
            "archives" to originalArchives,"canEdit" to writable,
            "canConfirm" to (writable && access.responsible(actor,e.orgId)))
    }
    fun requireArchivedOriginal(id:String,archiveId:String,fileId:String) {
        if (listOf(id,archiveId,fileId).any { it.isBlank() || it.length>100 }) fail("岗位教育归档原件标识不正确")
        @Suppress("UNCHECKED_CAST")
        val originals=detail(id)["archives"] as List<Map<String,Any?>>
        val archive=originals.singleOrNull { it["id"]==archiveId } ?: fail("该永久档案不属于当前岗位教育记录")
        @Suppress("UNCHECKED_CAST")
        val files=archive["files"] as? List<Map<String,Any?>> ?: fail("岗位教育归档文件清单无效")
        if (files.none { it["fileId"]==fileId }) fail("该文件不是所选岗位教育永久档案的原件")
    }
    fun list(orgId:String,userId:String?=null,page:Int=1,pageSize:Int=20):Map<String,Any?> {
        val current=access.actor()
        val actor=access.require(orgId,userId ?: current.id)
        val allAllowed=access.manages(actor) || CollectionConst.STAFF_EDUCATION_VIEW in actor.permissions
        val selected=if(allAllowed)userId else actor.id
        if(page<1 || pageSize<0 || pageSize>200)fail("分页参数无效，pageSize为0时查询全部")
        val q=CollectionStaffEducations.selectAll().where {CollectionStaffEducations.orgId eq orgId}
        selected?.let {q.andWhere {CollectionStaffEducations.userId eq it}}
        val total=q.count();q.orderBy(CollectionStaffEducations.createTime to SortOrder.DESC,CollectionStaffEducations.id to SortOrder.DESC)
        if(pageSize>0)q.limit(pageSize).offset((page.toLong()-1)*pageSize)
        return mapOf("list" to CollectionStaffEducationDao.wrapRows(q).map(::item),"total" to total,"pageNum" to page,"pageSize" to pageSize,
            "canViewAll" to allAllowed,"canCreate" to access.canWrite(actor,orgId))
    }
    fun people(orgId:String):Map<String,Any?> {
        val actor=access.actor();access.require(orgId,actor.id)
        val allAllowed=access.manages(actor) || CollectionConst.STAFF_EDUCATION_VIEW in actor.permissions
        val roleIds=Roles.select(Roles.id).where {Roles.code inList listOf(CollectionConst.ROLE_KEEPER,CollectionConst.ROLE_STORAGE_HEAD)}.map {it[Roles.id].value}
        val people=directory.roleMembers(roleIds,orgId).toMap().toMutableMap()
        WarehouseDao.find {Warehouses.orgId eq orgId}.forEach {w->w.staffing?.keeperUserIds.orEmpty().forEach {id->directory.findActive(id)?.takeIf {scope.isMuseumMember(it,orgId)}?.let {people[id]=it.name}}}
        val cases=CollectionStaffEducationDao.find {CollectionStaffEducations.orgId eq orgId}.toList().filter {allAllowed || it.userId==actor.id}
        cases.forEach {people.putIfAbsent(it.userId,if(StaffEducationReadIdentity.valid(it))it.original.personName else it.userId)}
        return mapOf("people" to people.filterKeys {allAllowed || it==actor.id}.toSortedMap().map {(id,name)->
            val entries=cases.filter {it.userId==id}.map(::item)
            mapOf("id" to id,"name" to name,"recordCount" to entries.size,"hasVerifiedPostTraining" to entries.any {it["state"]=="VERIFIED" && (it["content"] as? StaffEducationContent)?.plan?.kind==EnumStaffEducationKind.POST_TRAINING},
                "continuingEducationNeeds" to entries.count {(it["content"] as? StaffEducationContent)?.plan?.kind==EnumStaffEducationKind.CONTINUING_EDUCATION && it["state"]!="WITHDRAWN"},"evidenceIssueCount" to entries.count {it["evidenceErrors"]!=null})
        },"canViewAll" to allAllowed,"notice" to "名单来自当前保管职责、库管名单及已登记教育人员，须与真实完整保管人员名册核对；已核实培训只覆盖记录列明的岗位和技能，不自动授予任职或入库权限。")
    }
}
