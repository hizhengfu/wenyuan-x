<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="行政定级与备案原件">
    <NSpace vertical :size="18">
      <CollectionArchiveIssues :data="[data, historical]" />
      <NAlert type="info"
        >馆内鉴定意见作为初步鉴选依据。请记录实际向省级部门申请、正式定级和备案原件；一级文物另须省级部门报国家备案。材料登记后，正式级别仍通过初始编目审核或信息变更生效。</NAlert
      >
      <CollectionSelect :value="collectionId" clearable @update:value="select" />
      <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
      <NText v-if="data.collectionId">{{ data.totalRegNo }} · {{ data.name }}</NText>
      <NButton v-if="canRecord" :disabled="loading || !!readError || !data.collectionId" type="primary" @click="edit('application')">登记已提交的省级定级申请</NButton>
      <NSelect
        v-if="records.length"
        v-model:value="recordId"
        :options="
          records.map((v: any) => ({ label: `第${v.version}次 · ${v.application.applicationNo}`, value: v.id }))
        "
      />
      <template v-if="current">
        <NAlert :type="current.ready ? 'success' : 'warning'">{{ gradingStatus(current) }}</NAlert>
        <CollectionEvidenceReadView :fields="gradingApplicationFields" :data="current.application" :archive-files="(current.archives || []).filter((archive: any) => archive.phase === 'APPLICATION').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(current,'APPLICATION')" />
        <CollectionEvidenceReadView v-if="current.result" :fields="gradingResultFields" :data="current.result" :archive-files="(current.archives || []).filter((archive: any) => archive.phase === 'PROVINCIAL_RESULT').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(current,'PROVINCIAL_RESULT')" />
        <CollectionEvidenceReadView
          v-if="current.nationalFiling"
          :fields="gradingNationalFields"
          :data="current.nationalFiling"
          :archive-files="(current.archives || []).filter((archive: any) => archive.phase === 'NATIONAL_FILING').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(current,'NATIONAL_FILING')"
        />
        <NSpace v-if="canRecord">
          <NButton v-if="!current.result" @click="edit('result')">登记正式定级与省级备案</NButton>
          <NButton v-if="current.result?.level === 'FIRST' && !current.nationalFiling" @click="edit('national')"
            >登记省级报国家备案</NButton
          >
        </NSpace>
        <NCollapse
          ><NCollapseItem
            v-for="a in current.archives || []"
            :key="a.id"
            :name="a.id"
            :title="`原件归档 · ${archivePhaseTitle(a.phase)} · ${formatLocalDateValue(a.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
          >
            <HFileList :files="a.files" :original-url="(fileId: string) => archivedOriginalUrl(data.collectionId,current.id,a.id,fileId)" :revision="[a?.id, a?.digest]" show-download-btn />
            <NText v-if="a.phase.startsWith('USED_')"
              >已作为正式登记或信息变更的定级依据，关联记录：{{ snapshot(a).operationId }}</NText
            >
            <CollectionEvidenceReadView
              v-if="a.phase === 'APPLICATION'"
              :fields="gradingApplicationFields"
              :data="snapshot(a).application"
              :archive-files="a.files" :original-url="originalFor(current,a.phase)"
            />
            <CollectionEvidenceReadView
              v-else-if="a.phase === 'PROVINCIAL_RESULT'"
              :fields="gradingResultFields"
              :data="snapshot(a)"
              :archive-files="a.files" :original-url="originalFor(current,a.phase)"
            />
            <CollectionEvidenceReadView
              v-else-if="a.phase === 'NATIONAL_FILING'"
              :fields="gradingNationalFields"
              :data="snapshot(a)"
              :archive-files="a.files" :original-url="originalFor(current,a.phase)"
            />
            <template v-else
              ><CollectionEvidenceReadView
                :fields="gradingResultFields"
                :data="snapshot(a).result || {}" :archive-files="a.files" :original-url="originalFor(current,a.phase)" /><CollectionEvidenceReadView
                v-if="snapshot(a).nationalFiling"
                :fields="gradingNationalFields"
                :data="snapshot(a).nationalFiling"
                :archive-files="a.files" :original-url="originalFor(current,a.phase)"
            /></template> </NCollapseItem
        ></NCollapse>
      </template>
      <NDivider>原馆留存的行政定级历史</NDivider>
      <NSpace>
        <NInput v-model:value="historyKeyword" placeholder="按原总登记号或藏品 ID 查找" clearable @keyup.enter="loadHistory" />
        <NButton :loading="historyLoading" @click="loadHistory">查询历史定级</NButton>
      </NSpace>
      <NAlert v-if="historyError" type="error">{{ historyError }}</NAlert>
      <NSelect v-if="historyRows.length" v-model:value="historicalId"
        :options="historyRows.map((row:any)=>({label:`${row.totalRegNo} · 第${row.version}次 · ${row.applicationNo}`,value:row.id}))"
        clearable filterable placeholder="选择原馆行政定级记录" @update:value="loadHistoricalDetail" />
      <NText v-else-if="!historyLoading && !historyError" depth="3">当前查询范围没有历史定级记录</NText>
      <template v-if="historical?.id">
        <NText strong>原总登记号 {{ historical.totalRegNo }} · 第 {{ historical.version }} 次申请</NText>
        <CollectionEvidenceReadView :fields="gradingApplicationFields" :data="historical.application" :archive-files="(historical.archives || []).filter((archive: any) => archive.phase === 'APPLICATION').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(historical,'APPLICATION',true)" />
        <CollectionEvidenceReadView v-if="historical.result" :fields="gradingResultFields" :data="historical.result" :archive-files="(historical.archives || []).filter((archive: any) => archive.phase === 'PROVINCIAL_RESULT').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(historical,'PROVINCIAL_RESULT',true)" />
        <CollectionEvidenceReadView v-if="historical.nationalFiling" :fields="gradingNationalFields" :data="historical.nationalFiling" :archive-files="(historical.archives || []).filter((archive: any) => archive.phase === 'NATIONAL_FILING').flatMap((archive: any) => archive.files || [])" :original-url="originalFor(historical,'NATIONAL_FILING',true)" />
        <NCollapse><NCollapseItem v-for="a in historical.archives || []" :key="a.id" :name="a.id"
          :title="`原馆永久档案 · ${archivePhaseTitle(a.phase)} · ${formatLocalDateValue(a.archivedAt,'YYYY-MM-DD HH:mm:ss')}`">
          <HFileList :files="a.files" :original-url="(fileId: string) => historicalOriginalUrl(historical.id,a.id,fileId)" :revision="[a?.id, a?.digest]" show-download-btn />
        </NCollapseItem></NCollapse>
      </template>
    </NSpace>
  </HPageForm>
  <CollectionEvidenceForm ref="formRef" :title="formTitle" :fields="fields" :save="save" @saved="reload" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, ref, nextTick } from 'vue';
import { formatLocalDateValue } from '@/utils';
import { gradingApi } from '../api/grading';
import { useAuthStore } from '@/store/modules/auth';
import CollectionSelect from './CollectionSelect.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  gradingApplicationFields,
  gradingResultFields,
  gradingNationalFields,
  gradingStatus,
} from '../utils/gradingEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>();
const auth=useAuthStore();
const canRecord=computed(()=>auth.hasPermission('COLLECTION_ADMIN,COLLECTION_APPRAISE'));
const show = ref(false),
  collectionId = ref<string>(),
  recordId = ref<string>(),
  data = ref<any>({}),
  loading = ref(false),
  readError = ref(''),
  formRef = ref<any>();
const historyKeyword=ref(''),historyRows=ref<any[]>([]),historicalId=ref<string>(),historical=ref<any>({}),historyError=ref(''),historyLoading=ref(false);
let historyGeneration=0;
const operation = ref<'application' | 'result' | 'national'>('application');
let selection = 0;
const records = computed(() => data.value.records || []),
  current = computed(() => records.value.find((v: any) => v.id === recordId.value));
const fields = computed(() =>
  operation.value === 'application'
    ? gradingApplicationFields
    : operation.value === 'result'
      ? gradingResultFields
      : gradingNationalFields,
);
const formTitle = computed(() =>
  operation.value === 'application'
    ? '实际提交省级申请'
    : operation.value === 'result'
      ? '正式定级及省级备案'
      : '省级报国家备案',
);
const snapshot = (a: any) => JSON.parse(a.snapshot);
const archivePhaseTitle=(phase:string)=>({APPLICATION:'省级定级申请',PROVINCIAL_RESULT:'省级正式定级及备案',NATIONAL_FILING:'省级报国家备案'} as Record<string,string>)[phase] || (phase.startsWith('USED_')?'正式登记或信息变更采用':phase);
const archivedOriginalUrl = (collectionId: string, gradingId: string, archiveId: string, fileId: string) =>
  `/collection/grading/collection/${encodeURIComponent(collectionId)}/record/${encodeURIComponent(gradingId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const historicalOriginalUrl = (gradingId: string, archiveId: string, fileId: string) =>
  `/collection/grading/history/${encodeURIComponent(gradingId)}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const originalFor=(record:any,phase:string,historicalRecord=false)=>(fileId:string)=>{
  const archive=(record?.archives||[]).find((item:any)=>item.phase===phase && item.files?.some((file:any)=>file.fileId===fileId));
  if(!archive)return '';
  return historicalRecord?historicalOriginalUrl(record.id,archive.id,fileId):archivedOriginalUrl(data.value.collectionId,record.id,archive.id,fileId);
};
async function open(id?: string) {
  show.value = true;
  historyKeyword.value='';historyRows.value=[];historicalId.value=undefined;historical.value={};historyError.value='';
  ++historyGeneration;
  await Promise.all([select(id),loadHistory()]);
}
async function loadHistory(){
  const ticket=++historyGeneration;historyLoading.value=true;historyError.value='';historicalId.value=undefined;historical.value={};
  try {const response:any=await gradingApi.historyPage(historyKeyword.value.trim() || undefined);
    if(ticket===historyGeneration && show.value)historyRows.value=response.data?.list || [];
  } catch {if(ticket===historyGeneration && show.value){historyRows.value=[];historyError.value='历史定级记录读取失败，请核对原馆权限和档案。';}}
  finally {if(ticket===historyGeneration)historyLoading.value=false;}
}
async function loadHistoricalDetail(id?:string){
  const ticket=++historyGeneration;historical.value={};historyError.value='';if(!id)return;
  try {const response:any=await gradingApi.history(id);
    if(ticket===historyGeneration && show.value){if(response.data?.id!==id)throw new Error('历史记录不一致');historical.value=response.data;}
  } catch {if(ticket===historyGeneration && show.value)historyError.value='历史定级档案读取失败，请重新核对。';}
}
async function select(id?: string) {
  const request = ++selection;
  collectionId.value = id;
  data.value = {};
  recordId.value = undefined;
  readError.value = '';
  loading.value = false;
  if (!id) return false;
  loading.value = true;
  try {
    const response: any = await gradingApi.detail(id);
    if (request !== selection) return false;
    data.value = response.data;
    recordId.value = records.value.at(-1)?.id;
    return true;
  } catch {
    if (request === selection) readError.value = '定级原件读取失败，请核对当前权限、收藏单位及登记身份后重试。';
  } finally {
    if (request === selection) loading.value = false;
  }
  return false;
}
async function edit(type: 'application' | 'result' | 'national') {
  operation.value = type;
  editingTarget = type === 'application' ? collectionId.value : recordId.value;
  await nextTick();
  const initial =
    type === 'result'
      ? { provincialAuthority: current.value?.application.provincialAuthority }
      : type === 'national'
        ? { submittingProvincialAuthority: current.value?.result.provincialAuthority }
        : {};
  formRef.value?.open(initial);
}
async function save(value: Record<string, any>) {
  // Capture the subject when opening the form; a later selection cannot redirect the submitted evidence.
  const target = editingTarget;
  if (!target) throw new Error('请先选择对应藏品或定级申请');
  return gradingApi[operation.value](target, value);
}
let editingTarget: string | undefined;
async function reload() {
  const keep = recordId.value;
  if (!(await select(collectionId.value))) return;
  if (records.value.some((r: any) => r.id === keep)) recordId.value = keep;
  emit('saved');
}
defineExpose({ open });
</script>
