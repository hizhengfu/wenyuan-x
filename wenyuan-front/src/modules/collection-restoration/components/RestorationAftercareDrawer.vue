<template>
  <HPageView v-model:show="show" :trap-focus="!formRef?.isOpen?.()" title="修复后养护与实际复核" width="1060px">
    <NAlert v-if="readError" type="error">{{ readError }} <NButton @click="reload">重新读取</NButton></NAlert>
    <NSpin v-else :show="loading"
      ><NSpace v-if="!loading" vertical :size="16">
        <CollectionArchiveIssues :data="data" />
        <NText>原收藏单位：{{ data.orgName || data.orgId }} · 修复项目：{{ data.restorationId }}</NText>
        <NAlert v-if="data.issue" type="warning">{{ data.issue }}</NAlert>
        <NAlert type="info"
          >按修复方案和实际验收要求持续检查保存状况、病害及环境。新的检查和复核原件会使关联告警重新进入待核查，原关闭记录保留。实物归库仍在来源归还单办理。</NAlert
        >
        <NAlert v-if="!data.completed" type="warning">须先登记实际修复竣工，再记录后续养护。</NAlert>
        <NAlert v-if="data.overdue" type="warning">后续检查已到期，请组织实际检查并留存原件。</NAlert>
        <NText v-if="data.nextCheckAt"
          >下次检查期限：{{ formatLocalDateValue(data.nextCheckAt, 'YYYY-MM-DD HH:mm:ss') }}</NText
        >
        <NButton
          v-if="data.completed && data.canOperate"
          v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,COLLECTION_STORAGE'"
          @click="edit()"
          >登记实际养护检查</NButton
        >
        <NCard
          v-for="row in [...(data.checks || [])].reverse()"
          :key="row.id"
          :title="`第 ${row.version} 次检查 · ${row.id}`"
        >
          <CollectionEvidenceReadView :original-url="originalUrl" :fields="aftercareFields" :data="display(row.content)" :archive-files="stageFiles(row.archives, 'CHECKED')" />
          <NText v-if="row.content.resolvesCheckIds?.length"
            >关联已处理问题：{{ row.content.resolvesCheckIds.join('、') }}</NText
          >
          <CollectionEvidenceReadView :original-url="originalUrl" v-if="row.review" :fields="aftercareReviewFields" :data="row.review" :archive-files="stageFiles(row.archives, 'REVIEWED')" />
          <NButton
            v-else-if="row.id === data.previousCheckId && data.canOperate"
            v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,COLLECTION_STORAGE'"
            @click="edit(row.id)"
            >登记实际签署复核</NButton
          >
          <NCollapse
            ><NCollapseItem v-for="a in row.archives || []" :key="a.id" :name="a.id" :title="`永久原件 · ${a.phase}`"
              ><CollectionCaseOriginalFiles v-for="f in a.files" :key="f.fileId" case-type="restoration" :case-id="projectId" :value="f.fileId" :archive-files="a.files" /></NCollapseItem
          ></NCollapse>
        </NCard> </NSpace
    ></NSpin>
  </HPageView>
  <CollectionEvidenceForm :trap-focus="true"
    v-if="formContext && show && active" :key="`aftercare-${formContext.token}`"
    ref="formRef"
    :title="`${editingCheck ? '登记实际签署复核' : '登记实际养护检查'} · ${formContext.orgName || formContext.orgId}`"
    :fields="fields"
    :save="save"
  />
</template>
<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { useRestorationAccountWindow } from '../composables/useRestorationAccountWindow';
import { ref, shallowRef, nextTick, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import CollectionCaseOriginalFiles from '@/modules/collection/components/CollectionCaseOriginalFiles.vue';
import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
import { aftercareFields, aftercareReviewFields, aftercareValues } from '../utils/restorationAftercareFields';
import { restorationAftercareApi as api } from '../api/aftercare';
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false), loading = ref(false), data = ref<any>({}), formRef = ref<any>();
const fields = ref<EvidenceField[]>([]), editingCheck = ref(''), active = ref(true), readError = ref('');
const formContext = shallowRef<{token:number;orgId:string;orgName?:string}>();
const save = shallowRef<(value:Record<string,any>)=>Promise<any>>(async()=>false);
let projectId = '', request = 0, editRequest = 0;
function stageFiles(archives: any[] | undefined, phase: string): any[] {
  const matching = Array.isArray(archives) ? archives.filter((archive) => archive.phase === phase) : [];
  return matching.length === 1 && Array.isArray(matching[0].files) ? matching[0].files : [];
}
function originalUrl(fileId: string) { return '/collection/restoration/' + encodeURIComponent(projectId) + '/original/' + encodeURIComponent(fileId); }
const text = (v:unknown):v is string => typeof v === 'string' && !!v.trim();
const display = (v:any) => ({...v,conditionSafe:v.conditionSafe?'YES':'NO',environmentSafe:v.environmentSafe?'YES':'NO'});
function invalidate() {
  ++request; ++editRequest; formRef.value?.close?.(); formContext.value=undefined;
  save.value=async()=>false; data.value={}; loading.value=false; readError.value=''; fields.value=[];
}
watch(show,value=>{if(!value)invalidate();},{flush:'sync'});
useRestorationAccountWindow(() => { show.value = false; invalidate(); projectId = ''; });
onBeforeUnmount(()=>{active.value=false;invalidate();});
onDeactivated(()=>{active.value=false;show.value=false;invalidate();});
onActivated(()=>{active.value=true;});
function responseData(response:any,id:string) {
  const d=response?.data;
  if((response?.status!==undefined && response.status!==200) || !d || d.restorationId!==id || !text(d.orgId) || !text(d.revision)
    || typeof d.canOperate!=='boolean' || typeof d.completed!=='boolean' || !Array.isArray(d.checks)
    || (d.completed && !text(d.planId)) || (d.acceptanceId!=null && !text(d.acceptanceId))) throw Error('养护响应缺少原馆、版本或项目依据，请重新读取');
  if(d.checks.some((r:any)=>!r || !text(r.id) || r.orgId!==d.orgId || r.restorationId!==id || !Number.isSafeInteger(r.version) || r.version<1
    || !r.content || !text(r.content.planId) || typeof r.content.conditionSafe!=='boolean' || typeof r.content.environmentSafe!=='boolean')
    || new Set(d.checks.map((r:any)=>r.id)).size!==d.checks.length
    || (d.previousCheckId??null)!==(d.checks.at(-1)?.id??null)) throw Error('养护记录来源或最新记录不一致，请重新核对');
  return d;
}
async function open(value:string) {
  if(!active.value)return false;
  invalidate();projectId=value;show.value=true;const token=request;loading.value=true;
  try {
    if(!text(value))throw Error('修复项目编号缺失');
    const result=await api.detail(value);
    if(token!==request || !active.value || !show.value)return false;
    data.value=responseData(result,value);return true;
  } catch(e) {
    if(token===request && active.value && show.value)readError.value=errorText(e, '无法读取后续养护资料');
    return false;
  } finally {if(token===request)loading.value=false;}
}
async function reload(){if(show.value && active.value)return open(projectId);return false;}
async function edit(check='') {
  if(!active.value || !show.value || loading.value || readError.value || !data.value.canOperate)return;
  const id=projectId,orgId=data.value.orgId;
  if(!await open(id))return;
  if(data.value.orgId!==orgId){data.value={};readError.value='项目原收藏单位已变化，请关闭后重新核对';return;}
  const d=cloneDeep(data.value),latest=d.checks.at(-1);
  if(!d.canOperate || (!check && !d.completed))return;
  if(check && (check!==d.previousCheckId || latest?.review)){readError.value='养护检查或复核已变化，请重新选择最新记录';return;}
  editingCheck.value=check;
  fields.value=check?aftercareReviewFields:[...aftercareFields,{
    key:'resolvesCheckIds',label:'本次复查处理的原问题',type:'select',multiple:true,
    options:d.checks.filter((r:any)=>!r.content.conditionSafe || !r.content.environmentSafe).map((r:any)=>({value:r.id,label:`第 ${r.version} 次检查 · ${r.content.conditionAndDisease}`})),
  }];
  const token=++editRequest,epoch=request;
  formContext.value={token,orgId,orgName:d.orgName};
  const owned=()=>active.value && show.value && request===epoch && editRequest===token && projectId===id && formContext.value?.token===token;
  let saving=false;
  save.value=async(input)=>{
    if(!owned() || !formRef.value?.isOpen?.() || saving)return false;
    saving=true;
    try {
      const value=cloneDeep(input);
      const response:any=check
        ?await api.review(id,check,value,orgId,d.revision)
        :await api.check(id,{...aftercareValues(value),planId:d.planId,acceptanceId:d.acceptanceId??null,previousCheckId:d.previousCheckId??null},orgId,d.revision);
      if(!owned() || !formRef.value?.isOpen?.())return false;
      if((response?.status!==undefined && response.status!==200) || !text(response?.data) || (check && response.data!==check))throw Error('未收到有效养护编号，请回原项目核实保存结果');
      if(await open(id)) {
        if(!data.value.checks.some((r:any)=>r.id===response.data)){data.value={};readError.value='回执记录不在本项目最新资料中，请核实实际保存结果';return false;}
        emit('saved');
      }
      return false;
    } catch(e) {if(owned() && formRef.value?.isOpen?.())throw e;return false;}
    finally {saving=false;}
  };
  await nextTick();if(owned())formRef.value?.open({});
}
defineExpose({open});
</script>
