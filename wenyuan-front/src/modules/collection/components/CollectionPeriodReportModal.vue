<template>
  <NModal v-model:show="visible" preset="card" title="期间馆藏增减与提用动态" style="width:min(1180px,96vw)">
    <NSpace align="center" class="mb-3">
      <NDatePicker v-model:value="dates" type="daterange" clearable :disabled="saving" />
      <NButton :loading="loading" :disabled="saving" @click="load">核查期间</NButton>
      <NButton :disabled="!report || loading || saving" @click="printCurrent">打印核对表</NButton>
      <NButton v-if="canWrite" :disabled="!report?.ledger.completePeriod || loading || saving" @click="signing=!signing">登记实际签署</NButton>
    </NSpace>
    <NAlert v-if="error" type="error" class="mb-3">{{ error }}</NAlert>
    <NAlert type="info" class="mb-3">馆藏增减按正式总账事实计算，临时出入库另列为提用动态。历史缺口保留提示，未知数量不计为零。</NAlert>
    <div v-if="report" class="period-preview" v-html="html" />
    <NCard v-if="canWrite && signing && report" title="实际签署原件" class="mt-3">
      <NAlert type="info">请先核对打印表与已签署报表一致，再上传完整签署原件。存在历史缺口时，须附纸质总账、逐件明细及核对说明。</NAlert>
      <NForm label-placement="top"><WarehouseRecordFields v-model="sign" :fields="annualSignedFields" /></NForm>
      <NButton type="primary" :loading="saving" @click="save">保存签署原件</NButton>
    </NCard>
    <NCard v-if="history.length" title="已归档版本" class="mt-3">
      <CollectionArchiveIssues :data="history" class="mb-3" />
      <NSpace v-for="row in history" :key="row.id" align="center" class="mb-3">
        <span>第 {{ row.version }} 版</span><NButton size="small" @click="printSaved(row.id)">打印归档数据</NButton>
        <HFileList v-if="!row.evidenceIssue" :value="row.signed.signedReportFileIds" show-download-btn download-only />
      </NSpace>
    </NCard>
  </NModal>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { useAuthStore } from '@/store/modules/auth';
import { NAlert,NButton,NCard,NDatePicker,NForm,NModal,NSpace } from 'naive-ui';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import HFileList from '@/components/form/field/upload/fileList.vue';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import { annualSignedFields } from '../utils/annualReportFields';
import { periodReportApi } from '../api/periodReport';
import { periodReportHtml,periodReportStyle } from '../utils/periodReport';
const auth=useAuthStore();
const canWrite=computed(()=>!!auth.userInfo?.id && auth.userInfo.enabled===true && auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_STAT'));
const visible=ref(false),loading=ref(false),saving=ref(false),signing=ref(false),error=ref('');
const dates=ref<[number,number] | null>(null), report=ref<any>(), history=ref<any[]>([]), sign=ref<any>({});
const html=computed(()=>report.value ? periodReportHtml(report.value) : '');
let controller:AbortController | undefined, params:Record<string,unknown>={};
function reset() { controller?.abort();saving.value=false;report.value=undefined;history.value=[];sign.value={};signing.value=false;error.value='';loading.value=false; }
watch(dates,reset);watch(visible,v=>{if(!v)reset()});onBeforeUnmount(reset);
onDeactivated(()=>{visible.value=false;reset()});
watch(()=>JSON.stringify([auth.userInfo,canWrite.value]),()=>{visible.value=false;reset()},{flush:'sync'});
async function load() {
  reset(); if(!dates.value) {error.value='请选择起止日期';return;}
  const signal=(controller=new AbortController()).signal;loading.value=true;
  params={startDate:dayjs(dates.value[0]).format('YYYY-MM-DD'),endDate:dayjs(dates.value[1]).format('YYYY-MM-DD')};
  try { const current=await periodReportApi.preview(params,signal); if(signal.aborted)return;
    params={...params,orgId:current.data.ledger.orgId};const saved=await periodReportApi.history(params,signal);if(signal.aborted)return;
    report.value=current.data;history.value=saved.data || [];
  } catch(e) {if(!signal.aborted)error.value=e instanceof Error?e.message:'期间核查失败';}
  finally {if(!signal.aborted)loading.value=false;}
}
async function printCurrent() {
  if(!report.value || loading.value || saving.value)return;
  const before=report.value, signal=controller?.signal;
  loading.value=true;error.value='';
  try {
    const response=await periodReportApi.preview({...params},signal);
    if(signal?.aborted || !visible.value || report.value!==before)return;
    if(response.data.digest!==before.digest) {error.value='统计依据已变化，请重新核查后打印';report.value=undefined;return;}
    htmlPrint(periodReportHtml(response.data),'期间馆藏增减与提用动态',periodReportStyle);
  } catch(e) {if(!signal?.aborted && report.value===before)error.value=e instanceof Error?e.message:'打印核查失败';}
  finally {if(!signal?.aborted)loading.value=false;}
}
async function printSaved(id:string) {
  if(!report.value || loading.value || saving.value)return;
  const before=report.value,signal=controller?.signal;loading.value=true;error.value='';
  try {
    const saved=await periodReportApi.history({...params},signal);
    if(signal?.aborted || !visible.value || report.value!==before)return;
    const row=saved.data?.find(x=>x.id===id);if(!row){error.value='归档版本不可读，请刷新核查';return;}
    htmlPrint(periodReportHtml(row.calculated,row.signed),'期间统计归档数据',periodReportStyle);
  } catch(e) {if(!signal?.aborted && report.value===before)error.value=e instanceof Error?e.message:'归档读取失败';}
  finally {if(!signal?.aborted)loading.value=false;}
}
async function save() {
  if(!canWrite.value || saving.value || loading.value || !report.value || !dates.value)return;
  const before=report.value,signal=controller?.signal;saving.value=true;error.value='';
  try { await periodReportApi.signed({...sign.value,orgId:before.ledger.orgId,startDate:dayjs(String(params.startDate)).startOf('day').valueOf(),endDate:dayjs(String(params.endDate)).startOf('day').valueOf(),digest:before.digest,previousId:history.value.at(-1)?.id},signal);
    if(!signal?.aborted && visible.value && report.value===before)await load();
  } catch(e) {if(!signal?.aborted && report.value===before)error.value=e instanceof Error?e.message:'归档失败，请核对实际结果';}
  finally {if(!signal?.aborted)saving.value=false;}
}
function open() {reset();visible.value=true;}
defineExpose({open});
</script>
<style scoped>
.period-preview{max-height:560px;overflow:auto;font-size:12px}.period-preview :deep(table){width:100%;border-collapse:collapse;margin:8px 0}.period-preview :deep(th),.period-preview :deep(td){border:1px solid #aaa;padding:6px}.period-preview :deep(h1){font-size:20px}.period-preview :deep(h2){font-size:15px;margin-top:15px}.period-preview :deep(.signatures){display:flex;gap:20px;margin:20px 0}
.period-preview :deep(.category-reason-table){overflow-x:auto}.period-preview :deep(.category-reason-table table){min-width:1120px;font-size:11px}.period-preview :deep(.category-reason-table th),.period-preview :deep(.category-reason-table td){white-space:nowrap;text-align:right}.period-preview :deep(.category-reason-table td:first-child){text-align:left}
</style>
