<template>
  <HPageForm v-model:show="show" :title="historyOnly ? '本事件复核记录' : '逐件检查与复核关闭'" content-only cancel-text="关闭" :footer-style="{ paddingRight: '36px' }">
    <NAlert v-if="error" type="error">{{ error }} <NButton @click="reload">重新读取</NButton></NAlert>
    <NText v-if="context">原收藏单位：{{ context.orgName }}（{{ context.orgId }}）</NText>
    <NSpin :show="loading"
      ><NSpace v-if="!loading && !error" vertical :size="16">
        <CollectionArchiveIssues :data="records" />
        <NAlert v-if="!historyOnly" type="info"
          >逐件登记实际检查后，由保管负责人依据签署原件复核。关联修复须逐个完成当前验收及有效养护复核。业务关闭保留设备来源状态；信号未恢复或仅因规则调整而关闭时，须提供实际隔离、迁移等控制依据。</NAlert
        >
        <NCard v-for="item in historyOnly ? [] : objects" :key="item.id" :title="`${item.totalRegNo || ''} ${item.name}`">
          <CollectionEvidenceReadView v-if="checks[item.id]" :fields="itemFields" :data="checks[item.id]" />
          <NButton
            v-if="!historyOnly && canProcess && !closed && !loading && !error"
            v-permission="'PRESERVATION_ADMIN,PRESERVATION_ALARM_PROCESS'"
            @click="edit(item.id)"
            >登记本件实际检查</NButton
          >
        </NCard>
        <NAlert v-if="!historyOnly && !objects.length" type="warning">未查到可核实的受影响藏品，请先核对原告警关联范围。</NAlert>
        <NEmpty v-if="historyOnly && !records.length" description="暂无已归档复核记录" />
        <NCollapse
          ><NCollapseItem v-for="a in records" :key="a.id" :name="a.id" :title="`历次关闭原件 · ${a.phase}`">
            <CollectionEvidenceReadView :fields="reviewFields" :data="snapshot(a).review || {}" :archive-files="a.files" :original-url="(file) => originalUrl(a, file)" />
            <NCard
              v-for="item in snapshot(a).review?.items || []"
              :key="item.collectionId"
              :title="`藏品 ${item.collectionId} 实际检查`"
              ><CollectionEvidenceReadView
                :fields="itemFields" :archive-files="a.files"
                :original-url="(file) => originalUrl(a, file)"
                :data="{
                  ...item,
                  conditionSafe: item.conditionSafe ? 'YES' : 'NO',
                  environmentSafe: item.environmentSafe ? 'YES' : 'NO',
                }"
            /></NCard>
            <NText v-for="proof in snapshot(a).restorationProofs || []" :key="proof.restorationId"
              >关联修复项目：{{ proof.restorationId }}；实际养护检查：{{ proof.checkId }}</NText
            >
            <NText
              >当时来源状态：{{ snapshot(a).source?.lifecycleStatus }}；终止原因：{{
                snapshot(a).source?.terminationReason || '无'
              }}</NText
            >
            <HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(a, fileId)" :revision="a.id" show-download-btn /> </NCollapseItem
        ></NCollapse> </NSpace
    ></NSpin>
    <template #form-action>
        <NButton
          v-if="!historyOnly && canProcess && !closed && !loading && !error"
          v-permission="'PRESERVATION_ADMIN,PRESERVATION_ALARM_PROCESS'"
          :disabled="!objects.length || objects.length !== affectedCount || objects.some((x) => !checks[x.id])"
          type="primary"
          style="margin-right: 12px"
          @click="edit()"
          >登记实际签署复核并关闭</NButton
        >
    </template>
  </HPageForm>
  <CollectionEvidenceForm
    v-if="formSession"
    :key="`closure-form:${formSession}`"
    ref="formRef"
    :title="editingObject ? '登记本件实际检查' : '登记实际签署复核并关闭'"
    :fields="fields"
    :save="saveHandler"
    @saved="savedHandler"
  />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { ref, nextTick, onBeforeUnmount, onActivated, onDeactivated, watch } from 'vue';
import { cloneDeep, isEqual } from 'lodash-es';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
import { alarmApi as api } from '../api/alarm';
const yn = [
  { label: '安全', value: 'YES' },
  { label: '存在不安全因素', value: 'NO' },
];
const itemFields: EvidenceField[] = [
  { key: 'checkedAt', label: '实际检查时间', type: 'datetime', required: true },
  { key: 'checkerUserId', label: '实际检查人员', type: 'user', required: true },
  { key: 'actualLocation', label: '实际存放地点', required: true },
  { key: 'conditionSafe', label: '保存状况核查', type: 'select', options: yn, required: true },
  { key: 'environmentSafe', label: '环境安全核查', type: 'select', options: yn, required: true },
  { key: 'findingsAndMeasures', label: '现状、环境及处置措施', type: 'textarea', required: true },
  { key: 'inspectionFileIds', label: '实际检查原件', type: 'file', required: true },
];
const reviewFields: EvidenceField[] = [
  { key: 'reviewedAt', label: '实际复核时间', type: 'datetime', required: true },
  { key: 'keeperUserId', label: '保管负责人', type: 'user', required: true },
  { key: 'reviewerReason', label: '实际复核意见', type: 'textarea', required: true },
  { key: 'signedReviewFileIds', label: '签署复核原件', type: 'file', required: true },
  { key: 'activeSignalControlReason', label: '来源未恢复时的控制依据', type: 'textarea' },
  { key: 'activeSignalControlFileIds', label: '实际迁移或隔离控制原件', type: 'file' },
];
const emit = defineEmits<{ (e: 'saved', id: string): void }>();
const historyOnly = ref(false);
const show = ref(false),
  closed = ref(false),
  loading = ref(false),
  objects = ref<any[]>([]),
  checks = ref<Record<string, any>>({}),
  records = ref<any[]>([]),
  formRef = ref<any>(),
  fields = ref<EvidenceField[]>([]),
  editingObject = ref('');
let id = '',
  request = 0,
  disposed = false,
  active = true,
  serial = 0;
const context = ref<any>(null);
const error = ref(''),
  canProcess = ref(false),
  affectedCount = ref(0),
  formSession = ref(0);
const saveHandler = ref<(v: Record<string, any>) => Promise<unknown>>(async () => false);
const savedHandler = ref<() => Promise<unknown>>(async () => false);
const valid = (token: number, target: string) =>
  !disposed && active && show.value && request === token && id === target;
function invalidate() {
  ++request;
  id = '';
  context.value = null;
  objects.value = [];
  records.value = [];
  checks.value = {};
  closed.value = false;
  canProcess.value = false;
  affectedCount.value = 0;
  loading.value = false;
  formSession.value = 0;
  editingObject.value = '';
  fields.value = [];
  saveHandler.value = async () => false;
  savedHandler.value = async () => false;
}
function close() {
  show.value = false;
  invalidate();
  error.value = '';
}
const snapshot = (a: any) => {
  try {
    return JSON.parse(a.snapshot);
  } catch {
    return {};
  }
};
function originalUrl(archive:any,fileId:string) {
  if (!id || !archive?.id || !archive?.files?.some((f:any)=>f.fileId===fileId)) return '';
  return `/preservation/alarm/${encodeURIComponent(id)}/closure-records/${encodeURIComponent(archive.id)}/original/${encodeURIComponent(fileId)}`;
}
const textId = (v: any) => typeof v === 'string' && !!v.trim();
function readContext(response: any, target: string) {
  const d = response?.data;
  if ((response?.status != null && response.status !== 200) || !d || d.id !== target || !textId(d.orgId) ||
      !textId(d.orgName) || !textId(d.actorUserId) || !textId(d.revision) || typeof d.canProcess !== 'boolean' ||
      !['AWAIT','PROCESSING','PROCESSED','TRANSFERRED_RESTORATION','CLOSED'].includes(d.processStatus) ||
      !Number.isInteger(d.affectedCount) || d.affectedCount < 0 || !Array.isArray(d.objects) || !Array.isArray(d.records) ||
      d.objects.some((x:any)=>!textId(x?.id) || x.orgId!==d.orgId) || new Set(d.objects.map((x:any)=>x.id)).size!==d.objects.length ||
      d.records.some((x:any)=>!textId(x?.id) || x.orgId!==d.orgId || x.alarmId!==target || !/^CLOSED_[1-9]\d*$/.test(x.phase) || !Array.isArray(x.files) || x.files.some((f:any)=>!textId(f?.fileId))) ||
      new Set(d.records.map((x:any)=>x.id)).size!==d.records.length)
    throw new Error('告警复核资料或身份不完整，请重新核对');
  for (const record of d.records) {
    let body; try { body=JSON.parse(record.snapshot); } catch { throw new Error('关闭原件无法解析，请核查原档案'); }
    if (!body || typeof body!=='object' || Array.isArray(body)) throw new Error('关闭原件格式不正确');
  }
  return cloneDeep(d);
}
function comparableReview(value:any) {
  if(!value || typeof value!=='object' || Array.isArray(value))return value;
  const result=JSON.parse(JSON.stringify(value));
  // 服务端原件省略未填写的可选控制说明；实际布尔值、原件编号和时间保持原值。
  for(const key of ['activeSignalControlReason','activeSignalControlFileIds'])
    if(result[key]==null || result[key]==='')delete result[key];
  return result;
}
function applyContext(d:any) {
  context.value=d;objects.value=d.objects;records.value=d.records;
  closed.value=d.processStatus==='CLOSED';canProcess.value=d.canProcess;affectedCount.value=d.affectedCount;
}
function rejectCurrent(message:string) {
  const target=id;invalidate();id=target;error.value=message;
}
async function open(value: string, readonly = false) {
  if (disposed || !active) return;
  invalidate();historyOnly.value=readonly;id=value;show.value=true;error.value='';loading.value=true;
  const token=request;
  try {
    const response=await api.closureContext(value);
    if(valid(token,value))applyContext(readContext(response,value));
  } catch(e:any) {
    if(valid(token,value))rejectCurrent(e?.message || '实际复核原件读取失败');
  } finally {if(valid(token,value))loading.value=false;}
}
async function reload(){if(id)await open(id,historyOnly.value);}
async function edit(object = '') {
  if (historyOnly.value || !canProcess.value || closed.value || loading.value || error.value || !valid(request,id) || !context.value) return;
  if(object && !objects.value.some(x=>x.id===object))return;
  if(!object && (!objects.value.length || objects.value.length!==affectedCount.value || objects.value.some(x=>!checks.value[x.id])))return;
  const target=id, token=request, original=cloneDeep(context.value), localChecks=cloneDeep(checks.value);
  // 重新读取用于确认原依据；来源变化时不把旧检查配上新版本继续提交。
  loading.value=true;formSession.value=0;
  try {
    const response=await api.closureContext(target);
    if(!valid(token,target))return;
    const current=readContext(response,target);
    if(current.orgId!==original.orgId || current.actorUserId!==original.actorUserId || current.revision!==original.revision)
      throw new Error('原收藏单位、当前账号或核对依据已变化，请重新读取并逐件核对');
    applyContext(current);
    if(!current.canProcess || current.processStatus==='CLOSED')return;
  } catch(e:any) {
    if(valid(token,target))rejectCurrent(e?.message || '当前复核依据读取失败');return;
  } finally {if(valid(token,target))loading.value=false;}
  if(!valid(token,target))return;
  const session=++serial;
  editingObject.value=object;fields.value=object?itemFields:reviewFields;formSession.value=session;
  const current=()=>valid(token,target) && formSession.value===session && canProcess.value && !closed.value && formRef.value?.isOpen?.()!==false;
  let saving=false;
  saveHandler.value=async(v)=>{
    if(!current() || saving)return false;
    if(object){checks.value={...checks.value,[object]:{...cloneDeep(v),collectionId:object}};return true;}
    saving=true;
    const bool=(x:any)=>x==='YES'?true:x==='NO'?false:x;
    const value={...cloneDeep(v),items:Object.values(localChecks).map((x:any)=>({...x,conditionSafe:bool(x.conditionSafe),environmentSafe:bool(x.environmentSafe)}))};
    try {
      const result=await api.close(target,value,original.orgId,original.revision);
      if(!current())return false;
      if((result?.status!=null && result.status!==200) || !textId(result?.data))throw new Error('未收到有效关闭原件编号，请重新核对');
      const response=await api.closureContext(target);
      if(!current())return false;
      const confirmed=readContext(response,target);
      const record=confirmed.records.find((x:any)=>x.id===result.data);
      if(confirmed.orgId!==original.orgId || confirmed.actorUserId!==original.actorUserId || confirmed.processStatus!=='CLOSED' ||
         !record || !isEqual(comparableReview(snapshot(record).review),comparableReview(value)))throw new Error('关闭结果与本次原件不一致，请重新核对');
      applyContext(confirmed);formSession.value=0;saveHandler.value=async()=>false;
      emit('saved',target);return result;
    } catch(e:any) {
      if(current())rejectCurrent(e?.message || '复核关闭失败，请重新核对');return false;
    } finally {saving=false;}
  };
  savedHandler.value=async()=>false;
  await nextTick();
  if(valid(token,target) && formSession.value===session)formRef.value?.open(object?cloneDeep(localChecks[object]||{}):{});
}

watch(
  show,
  (value) => {
    if (!value) {
      invalidate();
      error.value = '';
    }
  },
  { flush: 'sync' },
);
onDeactivated(() => {
  active = false;
  close();
});
onActivated(() => {
  if (!disposed) active = true;
});
onBeforeUnmount(() => {
  disposed = true;
  active = false;
  close();
});
defineExpose({ open, close });
</script>
