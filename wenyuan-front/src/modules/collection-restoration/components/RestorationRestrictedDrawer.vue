<template>
  <HPageView v-model:show="show" title="受限藏品修复与送修核查" width="1120px">
    <NAlert v-if="readError" type="error">{{ readError }} <NButton @click="reload">重新读取</NButton></NAlert>
    <NSpin v-else :show="loading"
      ><NSpace v-if="!loading && data.restorationId" vertical :size="16">
        <CollectionArchiveIssues :data="data" />
        <NText>原收藏单位：{{ data.orgName || data.orgId }} · 修复项目：{{ data.restorationId }}</NText>
        <NAlert v-if="data.issue" type="warning">{{ data.issue }}</NAlert>
        <NAlert type="info"
          >本入口保留原事故和清点限制，先核实在库实物、技术方案及适用行政批文，再由保护修复人员、提用部门负责人、保管负责人和主要负责人依次本人核查专项风险。四方专项核查是本系统对受限作业的内部风险控制，不能替代行政许可。此后仍须完成修复方案审批、指定送修提用的三方审批及真实点交。</NAlert
        >
        <NAlert v-if="!data.planId" type="warning"
          >请先在修复法定原件中保存本次完整技术方案及实际适用的行政批准原件。</NAlert
        >
        <NSpace>
          <NButton
            v-if="data.canCreate"
            @click="edit('plan')"
            >登记本版专项风险核查</NButton
          >
          <NButton @click="reload">刷新办理状态</NButton>
          <NButton @click="navigate('incident-create')"
            >先登记已知事故事实</NButton
          >
          <NButton v-for="id in data.incidentIds || []" :key="id" @click="navigate('incident', id)"
            >查阅事故 {{ id }}</NButton
          >
          <NButton @click="navigate('flow')">办理本项目馆内审批</NButton>
        </NSpace>
        <NCollapse
          ><NCollapseItem
            v-for="record in [...(data.records || [])].reverse()"
            :key="record.id"
            :name="record.id"
            :title="`专项核查第 ${record.version} 版 · ${record.planId}`"
          >
            <NSpace vertical :size="14">
              <NAlert v-if="record.canceled" type="warning">本版授权已取消，原核查与本人确认记录继续保留。</NAlert>
              <NAlert v-else-if="record.issue" type="warning">{{ record.issue }}</NAlert>
              <NAlert v-else :type="record.approved ? 'success' : 'info'">{{
                record.approved
                  ? '本版已完成专项风险确认；馆内审批和实际点交继续分别办理。'
                  : '请由四方指定人员依次以本人账号核对本版完整原件。'
              }}</NAlert>
              <CollectionEvidenceReadView :original-url="restrictedOriginalUrl" :fields="restrictedPlanFields" :data="record.plan.request" :archive-files="stageFiles(record.archives, 'PLAN')" />
              <NCollapse
                ><NCollapseItem title="本版冻结的技术方案与行政原件" name="technical">
                  <RestorationPlanEvidenceView
                    :project-id="projectId"
                    restricted-originals
                    :plan-evidence="technical(record).plan?.content || {}"
                    :archive-files="stageFiles(record.technicalArchives, 'PLAN')" :archives="record.technicalArchives"
                    :source-snapshot="technical(record).plan?.sourceSnapshot"
                    :administrative-evidence="(technical(record).administrative || []).map((v: any) => ({ ...v.content, id: v.id }))"
                  /> </NCollapseItem
              ></NCollapse>
              <NDescriptions :column="1" bordered
                ><NDescriptionsItem
                  v-for="(factsId, caseId) in record.plan.caseFacts || {}"
                  :key="caseId"
                  :label="`事故 ${caseId}`"
                  >本次依据的事实版本：{{ factsId }}</NDescriptionsItem
                ></NDescriptions
              >
              <NText>指定送修提用编号：{{ record.plan.requisitionId }}。仅此单可依据本版授权办理。</NText>
              <NSpace>
                <NButton
                  v-if="record.canConfirm"
                  @click="edit('confirm', record)"
                  >本人核查确认本版</NButton
                >
                <NButton v-if="record.canCancel" @click="edit('cancel', record)"
                  >登记未实施取消</NButton
                >
                <NButton
                  v-if="record.canRequisition"
                  @click="navigate('requisition', record.id)"
                  >办理指定送修提用</NButton
                >
              </NSpace>
              <NCard
                v-for="confirmation in record.confirmations || []"
                :key="confirmation.role"
                :title="`${roleLabel(confirmation.role)} · ${confirmation.actorUserId}`"
              >
                <CollectionEvidenceReadView :original-url="restrictedOriginalUrl" :fields="restrictedConfirmFields" :data="confirmation.content" :archive-files="stageFiles(record.archives, `CONFIRM_${confirmation.role}`)" />
              </NCard>
              <CollectionEvidenceReadView :original-url="restrictedOriginalUrl"
                v-if="record.canceled"
                :archive-files="stageFiles(record.archives, 'CANCELED')"
                :fields="restrictedCancelFields"
                :data="record.canceled"
              />
              <NCollapse
                ><NCollapseItem
                  v-for="a in record.archives || []"
                  :key="a.id"
                  :name="a.id"
                  :title="`永久原件 · ${a.phase}`"
                  ><CollectionCaseOriginalFiles case-type="restoration-restricted" :case-id="projectId"
                    v-for="file in a.files"
                    :key="file.fileId"
                    :value="file.fileId" :archive-files="a.files" /></NCollapseItem
              ></NCollapse>
            </NSpace> </NCollapseItem
        ></NCollapse> </NSpace
    ></NSpin>
  </HPageView>
  <CollectionEvidenceForm v-if="formContext && show && active" :key="`restricted-form-${formContext.token}`"
    ref="formRef" :title="title" :fields="fields" :save="save" />
  <CollectionRequisitionCaseDrawer v-if="show && active && data.restorationId" :key="`restricted-requisition-${generation}`" ref="requisitionRef" @saved="childSaved" />
  <IncidentDrawer v-if="show && active && data.restorationId" :key="`restricted-incident-${generation}`" ref="incidentRef" @saved="childSaved" />
</template>
<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { useRestorationAccountWindow } from '../composables/useRestorationAccountWindow';
import { usesRelicRules } from '@/modules/collection/utils/collectionNature';
import { ref, shallowRef, computed, nextTick, defineAsyncComponent, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import { restorationRestrictedApi as api } from '../api/restricted';
import RestorationPlanEvidenceView from './RestorationPlanEvidenceView.vue';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import CollectionCaseOriginalFiles from '@/modules/collection/components/CollectionCaseOriginalFiles.vue';
import { restrictedPlanFields, restrictedConfirmFields, restrictedCancelFields, restrictedPayload } from '../utils/restorationRestrictedFields';
const CollectionRequisitionCaseDrawer = defineAsyncComponent(() => import('@/modules/collection/components/CollectionRequisitionCaseDrawer.vue'));
const IncidentDrawer = defineAsyncComponent(() => import('@/modules/collection/components/IncidentDrawer.vue'));
const emit = defineEmits<{ (e: 'saved'): void; (e: 'flow', id: string): void }>();
type Stage = 'plan' | 'confirm' | 'cancel';
const show=ref(false), loading=ref(false), active=ref(true), readError=ref(''), data=ref<any>({});
const formRef=ref<any>(), requisitionRef=ref<any>(), incidentRef=ref<any>(), generation=ref(0);
const stage=ref<Stage>('plan'), formContext=shallowRef<{token:number;orgId:string;orgName?:string}>();
const save=shallowRef<(v:Record<string,any>)=>Promise<any>>(async()=>false);
let projectId='', request=0, editRequest=0;
function stageFiles(archives: any[] | undefined, phase: string): any[] {
  const matching = Array.isArray(archives) ? archives.filter((archive) => archive.phase === phase) : [];
  return matching.length === 1 && Array.isArray(matching[0].files) ? matching[0].files : [];
}
function restrictedOriginalUrl(fileId:string){return '/collection/restoration/'+encodeURIComponent(projectId)+'/restricted/original/'+encodeURIComponent(fileId);}
const text=(v:unknown):v is string=>typeof v==='string' && !!v.trim();
const fields=computed(()=>stage.value==='plan'?restrictedPlanFields:stage.value==='confirm'?restrictedConfirmFields:restrictedCancelFields);
const title=computed(()=>({plan:'登记专项修复风险核查',confirm:'本人确认本版完整原件',cancel:'登记尚未实施取消'})[stage.value]+' · '+(formContext.value?.orgName || formContext.value?.orgId || ''));
function roleLabel(role:string){return({CONSERVATION:'保护修复人员',DEPARTMENT:'提用部门负责人',KEEPER:'保管负责人',DIRECTOR:'主要负责人'} as Record<string,string>)[role] || role;}
function technical(record:any){
  const original=JSON.parse(record.plan.technicalEvidence);
  if(original && typeof original==='object' && !Object.hasOwn(original,'administrative')){
    let snapshot:any;
    try{snapshot=JSON.parse(original.plan?.sourceSnapshot);}catch{return original;}
    const catalog=snapshot?.catalog;
    if(catalog?.nature==='OTHER' || (usesRelicRules(catalog?.nature) && catalog?.level==='GENERAL'))return {...original,administrative:[]};
  }
  return original;
}
function invalidate(){++request;++editRequest;generation.value=request;formRef.value?.close?.();formContext.value=undefined;save.value=async()=>false;data.value={};loading.value=false;readError.value='';}
const current=(token:number)=>active.value && show.value && token===request;
watch(show,v=>{if(!v)invalidate();},{flush:'sync'});
useRestorationAccountWindow(() => { show.value = false; invalidate(); projectId = ''; });
onBeforeUnmount(()=>{active.value=false;invalidate();});
onDeactivated(()=>{active.value=false;show.value=false;invalidate();});
onActivated(()=>{active.value=true;});
function responseData(response:any,id:string){
  const d=response?.data;
  if((response?.status!==undefined && response.status!==200) || !d || d.restorationId!==id || !text(d.orgId) || !text(d.collectionId) || !text(d.revision) || !text(d.actorUserId)
    || typeof d.canOperate!=='boolean' || typeof d.canCreate!=='boolean' || !Array.isArray(d.records) || !Array.isArray(d.incidentIds) || d.incidentIds.some((v:any)=>!text(v))
    || new Set(d.incidentIds).size!==d.incidentIds.length || (d.planId!=null && !text(d.planId)) || (d.canCreate && (!d.canOperate || !text(d.planId))))throw Error('专项核查响应缺少原馆、版本或项目依据，请重新读取');
  const roles=['CONSERVATION','DEPARTMENT','KEEPER','DIRECTOR'];
  for(const r of d.records){
    if(!r || !text(r.id) || r.orgId!==d.orgId || r.restorationId!==id || !text(r.planId) || !text(r.basisRevision) || !Number.isSafeInteger(r.version) || r.version<1
      || !r.plan?.request || r.plan.request.planId!==r.planId || !text(r.plan.requisitionId) || !text(r.plan.technicalEvidence) || !r.plan.caseFacts
      || typeof r.approved!=='boolean' || typeof r.outExecuted!=='boolean' || typeof r.canConfirm!=='boolean' || typeof r.canCancel!=='boolean' || typeof r.canRequisition!=='boolean'
      || !Array.isArray(r.confirmations) || !Array.isArray(r.archives) || (r.canConfirm && !roles.includes(r.confirmRole)))throw Error('专项核查记录来源或状态不一致，请重新核对');
    let original:any;try{original=technical(r);}catch{throw Error('专项核查技术原件无法读取');}
    if(!original?.plan?.content || !Array.isArray(original.administrative))throw Error('专项核查技术原件不完整');
    if(r.confirmations.some((v:any)=>!v || !roles.includes(v.role) || !text(v.actorUserId) || !v.content || v.content.basisRevision!==r.basisRevision)
      || new Set(r.confirmations.map((v:any)=>v.role)).size!==r.confirmations.length)throw Error('本人确认与本版核查依据不一致');
  }
  if(new Set(d.records.map((r:any)=>r.id)).size!==d.records.length)throw Error('专项核查编号重复，请重新核对');
  return d;
}
async function open(id:string){
  if(!active.value)return false;
  invalidate();projectId=id;show.value=true;loading.value=true;const token=request;
  try{if(!text(id))throw Error('修复项目编号缺失');const response=await api.detail(id);if(!current(token))return false;data.value=responseData(response,id);return true;}
  catch(e){if(current(token))readError.value=errorText(e, '无法读取专项核查资料');return false;}
  finally{if(token===request)loading.value=false;}
}
async function reload(){if(show.value && active.value)return open(projectId);return false;}
async function refreshOwned(){
  if(!show.value || !active.value || loading.value || readError.value || !text(data.value.orgId))return false;
  const id=projectId,org=data.value.orgId;
  if(!await open(id))return false;
  if(data.value.orgId!==org){data.value={};readError.value='项目原收藏单位已变化，请关闭后重新核对';return false;}
  return true;
}
async function edit(kind:Stage,record?:any){
  const recordId=record?.id,oldBasis=record?.basisRevision,oldPlan=data.value.planId;
  if(!await refreshOwned())return;
  const d=cloneDeep(data.value),id=projectId,org=d.orgId;
  const selected=d.records.find((r:any)=>r.id===recordId);
  if(!d.canOperate || (kind==='plan'&&!d.canCreate))return;
  if(kind==='plan'?d.planId!==oldPlan:!selected || selected.basisRevision!==oldBasis || !selected[kind==='confirm'?'canConfirm':'canCancel']){
    readError.value='专项核查版本或可办状态已变化，请重新选择';return;
  }
  stage.value=kind;const epoch=request,token=++editRequest;formContext.value={token,orgId:org,orgName:d.orgName};
  const owned=()=>current(epoch) && token===editRequest && projectId===id && formContext.value?.token===token;
  let saving=false;
  save.value=async(input)=>{
    if(!owned() || !formRef.value?.isOpen?.() || saving)return false;
    saving=true;
    try{
      const value=restrictedPayload(cloneDeep(input));
      const sent=kind==='plan'?{...value,planId:d.planId}:kind==='confirm'?{...value,basisRevision:selected.basisRevision}:value;
      const response:any=kind==='plan'?await api.create(id,sent,org,d.revision):kind==='confirm'?await api.confirm(id,recordId,sent,org,d.revision):await api.cancel(id,recordId,sent,org,d.revision);
      if(!owned() || !formRef.value?.isOpen?.())return false;
      if((response?.status!==undefined && response.status!==200) || !text(response?.data) || (kind!=='plan'&&response.data!==recordId))throw Error('未收到有效专项核查编号，请回原项目核实保存结果');
      if(await open(id)){
        if(data.value.orgId!==org || data.value.actorUserId!==d.actorUserId){data.value={};readError.value='保存后原馆或当前账号已变化，请重新核对';return false;}
        const saved=data.value.records.find((r:any)=>r.id===response.data);
        const actual=kind==='plan'?saved?.plan?.request:kind==='cancel'?saved?.canceled:saved?.confirmations.find((v:any)=>v.role===selected.confirmRole && v.actorUserId===d.actorUserId)?.content;
        if(!actual || Object.entries(sent).some(([k,v])=>JSON.stringify(actual[k])!==JSON.stringify(v))){data.value={};readError.value='回执原件与本项目本次登记不一致，请核实实际保存结果';return false;}
        emit('saved');
      }
      return false;
    }catch(e){if(owned() && formRef.value?.isOpen?.())throw e;return false;}
    finally{saving=false;}
  };
  await nextTick();if(owned())formRef.value?.open(kind==='plan'?{incidentIds:d.incidentIds.join('\n')}:{});
}
async function navigate(kind:'requisition'|'incident'|'incident-create'|'flow',key?:string){
  if(!await refreshOwned())return;
  const token=request,d=data.value;await nextTick();if(!current(token))return;
  if(kind==='requisition'){
    const row=d.records.find((r:any)=>r.id===key);
    if(!row?.canRequisition){readError.value='指定送修提用的当前依据已变化';return;}
    requisitionRef.value?.open(row.plan.requisitionId);
  }else if(kind==='incident'){
    if(!d.incidentIds.includes(key)){readError.value='当前事故关联已变化，请重新核对';return;}
    incidentRef.value?.open(key);
  }else if(kind==='incident-create')incidentRef.value?.create({items:[{collectionId:d.collectionId}]});
  else emit('flow',projectId);
}
const childSaved=computed(()=>{
  const token=generation.value,id=projectId,org=data.value.orgId,actor=data.value.actorUserId;
  return async()=>{
    if(!current(token) || !await open(id))return;
    if(data.value.orgId!==org || data.value.actorUserId!==actor){data.value={};readError.value='子窗口保存后原馆或当前账号已变化，请重新核对';return;}
    emit('saved');
  };
});
defineExpose({open});
</script>
