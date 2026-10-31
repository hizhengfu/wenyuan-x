<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="数字采集与资源管理"
    ><NSpin :show="loading"
      ><NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >信息化部门会同保管部门制定操作和安全方案。依实际材料登记采集、加工、整理、储存及共同复核；实物采集结束后，归库仍在来源归还单办理。</NAlert
        >
        <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
        <NAlert v-if="data.sourceIssue" type="warning"
          >{{ data.sourceIssue }}；既有数字原件仍按当时共同方案保留。</NAlert
        >
        <NText
          >用途：{{ data.purpose || '尚未填写' }}；进度：{{
            latest?.completed
              ? '已共同复核'
              : latest?.captured
                ? '数字资源处理中'
                : latest?.started
                  ? '实际采集中'
                  : '方案办理中'
          }}</NText
        >
        <NSpace v-if="!loading && !readError && data.id && data.canOperate">
          <NButton v-if="canPlan" @click="edit('plan')">{{ latest ? '订正共同方案' : '登记共同方案' }}</NButton>
          <NButton v-if="latest && !data.flowInstanceId" @click="submit">提交共同确认</NButton>
          <template v-if="latest && data.flowStatus === 'PASS' && !latest.started && data.sourceRevision"
            ><NButton @click="requisition">办理原件提用</NButton
            ><NButton @click="edit('start')">登记实际开始</NButton></template
          >
          <NButton v-if="latest?.started && !latest.captured" @click="edit('capture')">登记采集结束与原始数据</NButton>
          <template v-if="latest?.captured && !latest.completed"
            ><NButton v-for="stage in availableStages" :key="stage" @click="edit(stage)">{{
              digitalTitles[stage]
            }}</NButton
            ><NButton v-if="hasStage('STORED')" @click="edit('complete')">登记实际共同复核</NButton></template
          >
        </NSpace>
        <NCard v-for="plan in [...(data.plans || [])].reverse()" :key="plan.id" :title="`第 ${plan.version} 版共同方案`"
          ><NTabs type="line">
            <NTabPane name="plan" tab="共同方案"
              ><DigitalEvidenceReadView :project-id="data.id" :fields="digitalFields.plan" :archive-files="stageFiles(plan, 'PLAN')" :data="plan.content"
            /></NTabPane>
            <NTabPane name="capture" tab="实际采集"
              ><DigitalEvidenceReadView
                v-if="plan.started"
                :project-id="data.id"
                :fields="digitalFields.start"
                :archive-files="stageFiles(plan, 'STARTED')" :data="plan.started" /><DigitalEvidenceReadView
                v-if="plan.captured"
                :project-id="data.id"
                :fields="digitalFields.capture"
                :archive-files="stageFiles(plan, 'CAPTURED')" :data="plan.captured"
            /></NTabPane>
            <NTabPane name="resources" tab="资源来源与储存"
              ><NCard
                v-for="r in plan.resources"
                :key="r.id"
                :title="`${digitalStageLabels[r.content.stage]} · ${r.id}`"
              >
                <DigitalEvidenceReadView
                  :project-id="data.id"
                  :fields="digitalFields[r.content.stage] || rawFields"
                  :archive-files="stageFiles(plan, `RESOURCE_${r.id}`)" :data="r.content"
                />
                <DigitalPackagePreview :project-id="data.id" :plan-id="plan.id" :resource="r" />
                <NText>来源批次：{{ r.content.parentResourceIds?.join('、') || '实际采集原始数据' }}</NText>
                <NDescriptions v-for="file in r.files" :key="file.fileId" bordered :column="1"
                  ><NDescriptionsItem label="实际成果文件"
                    ><DigitalOriginalFiles :project-id="data.id" :files="[file]" /></NDescriptionsItem
                  ><NDescriptionsItem label="实际字节数">{{ file.size }}</NDescriptionsItem
                  ><NDescriptionsItem label="原始字节 SHA-256">{{ file.sha256 }}</NDescriptionsItem></NDescriptions
                >
              </NCard></NTabPane
            >
            <NTabPane name="complete" tab="共同复核"
              ><DigitalEvidenceReadView
                v-if="plan.completed"
                :project-id="data.id"
                :fields="digitalFields.complete"
                :archive-files="stageFiles(plan, 'COMPLETED')" :data="plan.completed" /><NEmpty v-else description="尚未登记实际共同复核"
            /></NTabPane>
            <NTabPane name="archives" tab="永久原件"
              ><NCollapse
                ><NCollapseItem v-for="a in plan.archives" :key="a.id" :name="a.id" :title="a.phase"
                  ><DigitalOriginalFiles :project-id="data.id" :files="a.files" /></NCollapseItem></NCollapse
            ></NTabPane> </NTabs
        ></NCard> </NSpace></NSpin
  ></HPageForm>
  <CollectionEvidenceForm
    :key="`CollectionEvidenceForm-${formEpoch}`"
    ref="formRef"
    :title="digitalTitles[kind] || ''"
    :fields="fields"
    :save="saveForCurrentForm"
    @saved="saved"
  />
  <CollectionRequisitionCaseDrawer :key="`CollectionRequisitionCaseDrawer-${formEpoch}`" ref="requisitionRef" @saved="saved" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const stageFiles = (plan: any, phase: string) => (plan.archives || []).filter((archive: any) => archive.phase===phase).flatMap((archive: any) => archive.files || []);
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionRequisitionCaseDrawer from './CollectionRequisitionCaseDrawer.vue';
import DigitalEvidenceReadView from './DigitalEvidenceReadView.vue';
import DigitalOriginalFiles from './DigitalOriginalFiles.vue';
const DigitalPackagePreview=defineAsyncComponent(()=>import('./DigitalPackagePreview.vue'));
import { digitalAcquisitionApi as api } from '../api/digitalAcquisition';
import { reproductionApi } from '../api/reproduction';
import {
  digitalFields,
  digitalTitles,
  digitalStageLabels,
  digitalValues,
  digitalFileIds,
} from '../utils/digitalAcquisitionFields';
import type { EvidenceField } from '../utils/evidenceFields';
const auth = useAuthStore();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  readError = ref(''),
  formEpoch = ref(0),
  data = ref<any>({}),
  fields = ref<EvidenceField[]>([]),
  kind = ref(''),
  formRef = ref<any>(),
  requisitionRef = ref<any>();
const latest = computed(() => data.value.plans?.at(-1));
const canPlan = computed(
  () =>
    !!data.value.sourceRevision &&
    data.value.flowStatus === 'DRAFT' &&
    !data.value.flowInstanceId &&
    !latest.value?.started,
);
const previous: Record<string, string> = { PROCESSED: 'RAW', ORGANIZED: 'PROCESSED', STORED: 'ORGANIZED' };
const hasStage = (stage: string) => (latest.value?.resources || []).some((r: any) => r.content.stage === stage);
const availableStages = computed(() => Object.keys(previous).filter((stage) => hasStage(previous[stage])));
const rawFields = digitalFields.PROCESSED.filter((f) => f.key !== 'parentResourceIds');
let id = '',
  request = 0,
  editing = { id: '', plan: '', source: '', kind: '' };
const saveForCurrentForm = computed(() => {
  const epoch = formEpoch.value, actor = auth.userInfo?.id, target = cloneDeep(editing);
  return (value: Record<string, any>) => epoch === formEpoch.value && actor === auth.userInfo?.id
    ? save(value, target) : Promise.resolve(false);
});
async function open(value: string) {
  id = value;
  data.value = {};
  show.value = true;
  await reload();
}
function clearForms() {
  editing = { id: '', plan: '', source: '', kind: '' };
  kind.value = '';
  fields.value = [];
  formEpoch.value++;
}
function close() {
  request++;
  data.value = {};
  readError.value = '';
  loading.value = false;
  clearForms();
}
watch(
  show,
  (visible) => {
    if (!visible) close();
  },
  { flush: 'sync' },
);
onBeforeUnmount(close);
watch(() => auth.userInfo?.id, () => {
  show.value = false;
  id = '';
  close();
}, { flush: 'sync' });
async function reload() {
  const token = ++request,
    target = id;
  loading.value = true;
  data.value = {};
  readError.value = '';
  try {
    const r: any = await api.detail(target);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('missing detail');
    data.value = r.data;
    return true;
  } catch {
    if (token === request && show.value) {
      data.value = {};
      readError.value = '数字采集资料读取失败，请核对当前授权和原件。';
      clearForms();
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
async function edit(action: string) {
  if (loading.value || readError.value || !data.value.id || !data.value.canOperate || !show.value) return;
  const token = request;
  formEpoch.value++;
  editing = { id, plan: latest.value?.id, source: data.value.sourceRevision, kind: action };
  kind.value = action;
  fields.value = cloneDeep(digitalFields[action]);
  fields.value = fields.value.map((f) =>
    ['parentResourceIds', 'storedResourceIds'].includes(f.key)
      ? {
          ...f,
          options: (latest.value?.resources || [])
            .filter((r: any) => r.content.stage === (f.key === 'storedResourceIds' ? 'STORED' : previous[action]))
            .map((r: any) => ({ value: r.id, label: `${r.id} · ${r.content.description}` })),
        }
      : f,
  );
  const target = editing;
  await nextTick();
  if (token !== request || target !== editing || !show.value) return;
  formRef.value?.open({});
}
async function save(value: Record<string, any>, target: typeof editing) {
  if (!show.value || loading.value || readError.value || !target.id || !target.kind) return false;
  const v = digitalValues(value);
  if (target.kind === 'plan') return api.record(target.id, 'plan', { ...v, sourceRevision: target.source });
  if (target.kind === 'start') return api.record(target.id, 'start', { ...v, planId: target.plan });
  if (target.kind === 'capture')
    return api.record(target.id, 'capture', { ...v, rawFileIds: digitalFileIds(v.rawFileIds) });
  if (previous[target.kind])
    return api.record(target.id, 'resource', {
      ...v,
      stage: target.kind,
      fileIds: digitalFileIds(v.fileIds),
      parentResourceIds: v.parentResourceIds || [],
    });
  return api.record(target.id, 'complete', { ...v, storedResourceIds: v.storedResourceIds || [] });
}
async function submit() {
  if (!show.value || loading.value || readError.value || !data.value.id || !data.value.canOperate) return;
  const target = id,
    token = request;
  await reproductionApi.submit(target);
  if (token !== request || target !== id || !show.value) return;
  if (await reload()) emit('saved');
}
async function requisition() {
  if (!show.value || loading.value || readError.value || !data.value.id || !data.value.canOperate) return;
  await requisitionRef.value?.open(undefined, {
    collectionIds: data.value.collectionId,
    purpose: undefined,
    sourceBizType: 'COLLECTION_REPRODUCTION',
    sourceBizId: id,
  });
}
async function saved() {
  if (!show.value || readError.value) return;
  if (await reload()) emit('saved');
}
defineExpose({ open });
</script>
