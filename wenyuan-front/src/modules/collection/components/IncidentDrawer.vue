<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="藏品事故报告与处置"
    ><NAlert v-if="readError" type="error">{{ readError }}</NAlert
    ><NSpin :show="loading"
      ><NSpace v-if="!readError" vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >发现事故应立即采取措施并按规定实际报告。此处登记和保留真实原件，不自动报案或向行政机关送达。</NAlert
        >
        <NText
          >{{ data.title }}；案卷编号：{{ data.id }}；{{ data.closed ? '本版已共同签署归档' : '本版仍在办理' }}</NText
        >
        <NAlert v-if="data.items?.some((i: any) => i.restricted)" type="warning"
          >受影响实物仍有安全限制。未找回、毁损或仍需限制的藏品，即使本版案卷归档也不会自动恢复使用。</NAlert
        >
        <NAlert
          v-if="
            data.facts?.items?.some(
              (i: any) =>
                !i.catalog?.nature ||
                (usesRelicRules(i.catalog.nature) && (!i.catalog.level || i.catalog.level === 'UNRATED')),
            )
          "
          type="warning"
          >存在性质或级别尚未核实的藏品。先据实报告，完成处理前再依法核实并追加有依据的事实版本。</NAlert
        >
        <NSpace size="small" align="center" v-if="!loading" v-permission="'COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE'">
          <template v-if="!data.closed"
            ><NButton @click="edit('report')">登记实际报案或报告</NButton
            ><NButton @click="edit('handling')">登记实际核查处理</NButton
            ><NButton v-if="canConfirm" @click="edit('confirm')">本人核对并签署</NButton></template
          >
          <CollectionActionMenu label="现场处置" v-if="data.id">
            <NButton quaternary v-if="data.id" @click="openWork">现场专项防护</NButton>
            <NButton quaternary v-if="data.id" @click="openRelocation">应急搬移与补记</NButton>
          </CollectionActionMenu>
          <CollectionActionMenu label="事实与来源">
            <NButton quaternary @click="edit('facts')">追加事实或订正版本</NButton>
            <NButton quaternary v-if="!data.facts?.facts?.source" @click="edit('source')">补充实际业务来源</NButton>
          </CollectionActionMenu>
        </NSpace>
        <NTabs type="line">
          <NTabPane name="facts" tab="发现事实与报告义务"
            ><NText v-if="data.facts?.facts?.source"
              >来源：{{ data.facts.facts.source.bizType }} / {{ data.facts.facts.source.bizId }}</NText
            ><CollectionEvidenceReadView
              v-if="data.facts"
              :fields="incidentFields.facts"
              :data="data.facts.facts"
              :archive-files="phaseFiles('FACTS', data.factsId)" :original-url="phaseOriginalUrl('FACTS', data.factsId)"
            /><NCard title="本版逐件报告核对"
              ><NText v-for="(r, i) in data.requirements || []" :key="i" tag="div"
                >{{ itemName(r.collectionId) }} · {{ incidentLabel(r.target) }} ·
                {{ incidentLabel(r.kind, incidentReportKinds) }}：{{
                  r.recorded ? '已登记原件' : '尚未登记原件'
                }}</NText
              ></NCard
            ></NTabPane
          >
          <NTabPane name="reports" tab="实际报告与处理"
            ><NCard v-for="r in activeRecords('REPORT')" :key="r.id" :title="`实际报告 ${r.id}`"
              ><CollectionEvidenceReadView :fields="fieldsFor('report')" :data="r.content" :archive-files="phaseFiles(r.kind, r.id)" :original-url="recordOriginalUrl(r)" /><NText
                v-if="r.content.handlingId"
                >对应处理结果：{{ r.content.handlingId }}</NText
              ></NCard
            ><NCard v-if="data.handling" title="当前实际核查处理"
              ><CollectionEvidenceReadView :fields="fieldsFor('handling')" :data="data.handling" :archive-files="phaseFiles('HANDLING', data.handlingId)" :original-url="phaseOriginalUrl('HANDLING', data.handlingId)" /></NCard
          ></NTabPane>
          <NTabPane name="signatures" tab="本人确认与历史原件"
            ><NCard
              v-for="r in data.records || []"
              :key="r.id"
              :title="`第 ${r.sequence} 份 · ${recordNames[r.kind] || r.kind}`"
              ><template v-if="r.kind === 'FACTS'"
                ><CollectionEvidenceReadView :fields="incidentFields.facts" :data="r.content.facts" :archive-files="phaseFiles(r.kind, r.id)" :original-url="recordOriginalUrl(r)" /></template
              ><template v-else-if="r.kind === 'CONFIRMATION'"
                ><NText
                  >签署人：{{ r.content.actorUserId }}；{{
                    r.content.role === 'KEEPER' ? '保管负责人' : '主要负责人'
                  }}；{{
                    r.content.request.basisRevision === data.basisRevision ? '对应当前案卷' : '对应历史案卷'
                  }}</NText
                ><CollectionEvidenceReadView :fields="incidentFields.confirm" :data="r.content.request" :archive-files="phaseFiles(r.kind, r.id)" :original-url="recordOriginalUrl(r)" /></template
              ><CollectionEvidenceReadView
                v-else
                :fields="fieldsFor(r.kind === 'REPORT' ? 'report' : 'handling')"
                :data="r.content" :archive-files="phaseFiles(r.kind, r.id)" :original-url="recordOriginalUrl(r)" /></NCard
            ><NCard v-for="a in data.archives || []" :key="a.id" :title="`${a.phase} · 永久原件`"
              ><HFileList :files="a.files || []" :original-url="(fileId: string) => originalUrl(a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCard
          ></NTabPane>
        </NTabs> </NSpace></NSpin
  ></HPageForm>
  <IncidentWorkDrawer v-if="workCaseId" :incident-id="workCaseId" :items="workItems" @close="workCaseId = ''" />
  <IncidentRelocationDrawer
    v-if="relocationCaseId"
    :incident-id="relocationCaseId"
    :items="relocationItems"
    @close="relocationCaseId = ''"
  />
  <CollectionEvidenceForm
    :key="formEpoch"
    ref="formRef"
    :title="formTitle"
    :fields="fields"
    :save="save"
    @saved="saved"
  />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
const phaseFiles = (kind: string, recordId?: string) => {
  const id = recordId || (data.value.records || []).find((row: any) => row.kind === kind)?.id;
  return (data.value.archives || []).filter((archive: any) => archive.phase === `${kind}_${id}`).flatMap((archive: any) => archive.files || []);
};
import { usesRelicRules } from '../utils/collectionNature';
import { computed, ref, defineAsyncComponent, nextTick, watch, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useAuthStore } from '@/store/modules/auth';
import { incidentApi } from '../api/incident';
import {
  incidentFields,
  incidentValues,
  incidentFormValues,
  incidentLabel,
  incidentReportKinds,
} from '../utils/incidentFields';
import type { EvidenceField } from '../utils/evidenceFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const IncidentWorkDrawer = defineAsyncComponent(() => import('./IncidentWorkDrawer.vue'));
const IncidentRelocationDrawer = defineAsyncComponent(() => import('./IncidentRelocationDrawer.vue'));
const relocationCaseId = ref('');
const relocationItems = ref<{ value: string; label: string }[]>([]);
const workCaseId = ref('');
const workItems = ref<{ value: string; label: string }[]>([]);
const auth = useAuthStore();
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  readError = ref(''),
  formEpoch = ref(0),
  data = ref<any>({}),
  formRef = ref<any>(),
  fields = ref<EvidenceField[]>([]),
  formTitle = ref('');
const canConfirm = computed(() => {
  const actor = auth.userInfo?.id, handling = data.value.handling;
  if (!show.value || loading.value || readError.value || data.value.closed || !data.value.basisRevision ||
      !actor || !handling || !auth.hasPermission('COLLECTION_ADMIN,COLLECTION_INCIDENT_MANAGE')) return false;
  if (actor === handling.keeperUserId) return true;
  return actor === handling.leaderUserId && (data.value.records || []).some((record: any) =>
    record.kind === 'CONFIRMATION' && record.content?.role === 'KEEPER' &&
    record.content.actorUserId === handling.keeperUserId &&
    record.content.request?.basisRevision === data.value.basisRevision);
});
const recordNames: Record<string, string> = {
  FACTS: '事实版本',
  REPORT: '实际报告',
  HANDLING: '核查处理',
  CONFIRMATION: '本人确认',
};
const originalUrl = (archive: any, fileId: string) =>
  `/collection/incident/${data.value.id}/original/${archive.id}/${fileId}`;
const phaseOriginalUrl = (kind: string, recordId: string) => (fileId: string) => {
  const archive = (data.value.archives || []).find((a: any) =>
    a.phase === `${kind}_${recordId}` && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(archive, fileId) : '';
};
const recordOriginalUrl = (record: any) => phaseOriginalUrl(record.kind, record.id);
let request = 0,
  target: any;
const save = ref<(value: Record<string, any>) => Promise<any>>(async () => false);
function bindFormSave() {
  const frozen = cloneDeep(target), epoch = formEpoch.value, actor = auth.userInfo?.id;
  save.value = async (value) => {
    if (formEpoch.value !== epoch || auth.userInfo?.id !== actor || readError.value) return false;
    return submit(frozen, cloneDeep(value));
  };
}
async function open(id: string) {
  const token = ++request;
  show.value = true;
  loading.value = true;
  data.value = {};
  workCaseId.value = '';
  relocationCaseId.value = '';
  readError.value = '';
  try {
    const r: any = await incidentApi.detail(id);
    if (token !== request || !show.value) return false;
    if (!r?.data) throw new Error('事故案卷不存在或当前不可读取');
    data.value = r.data;
    return true;
  } catch (error: any) {
    if (token === request && show.value) {
      readError.value = error?.message || '事故读取失败，请核对当前授权和原件';
      target = undefined;
      formEpoch.value++;
    }
    return false;
  } finally {
    if (token === request) loading.value = false;
  }
}
watch(
  show,
  (value) => {
    if (!value) {
      request++;
      target = undefined;
      formEpoch.value++;
      workCaseId.value = '';
      relocationCaseId.value = '';
    }
  },
  { flush: 'sync' },
);
watch(() => auth.userInfo?.id, () => {
  request++;
  target = undefined;
  formEpoch.value++;
  show.value = false;
  loading.value = false;
  data.value = {};
  readError.value = '';
  fields.value = [];
  workCaseId.value = '';
  relocationCaseId.value = '';
}, { flush: 'sync' });
onBeforeUnmount(() => {
  request++;
  formEpoch.value++;
  target = undefined;
});
function itemName(id: string) {
  const i = data.value.facts?.items?.find((i: any) => i.item.collectionId === id);
  return i ? `${i.catalog.totalRegNo || id} ${i.catalog.name || ''}` : id;
}
function openRelocation() {
  relocationItems.value = (data.value.items || [])
    .filter((i: any) => i.restricted)
    .map((i: any) => ({ value: i.collectionId, label: itemName(i.collectionId) }));
  relocationCaseId.value = data.value.id;
}
function openWork() {
  workItems.value = (data.value.items || [])
    .filter((i: any) => i.restricted)
    .map((i: any) => ({ value: i.collectionId, label: itemName(i.collectionId) }));
  workCaseId.value = data.value.id;
}
function activeRecords(kind: string) {
  return (data.value.records || []).filter((r: any) => r.kind === kind && r.content.factsId === data.value.factsId);
}
function fieldsFor(kind: string): EvidenceField[] {
  const items = (data.value.facts?.items || []).map((i: any) => ({
    value: i.item.collectionId,
    label: itemName(i.item.collectionId),
  }));
  const reports = activeRecords('REPORT')
    .filter((r: any) => r.content.kind === 'INCIDENT')
    .map((r: any) => ({ value: r.id, label: `${incidentLabel(r.content.target)} · ${r.content.reportNo}` }));
  return (incidentFields[kind] || []).map((f) => ({
    ...f,
    ...(['collectionIds', 'collectionId'].includes(f.key) ? { options: items } : {}),
    ...(f.children
      ? {
          children: f.children.map((c) => ({
            ...c,
            ...(['collectionId', 'collectionIds'].includes(c.key)
              ? { options: items }
              : c.key === 'reportIds'
                ? { options: reports }
                : {}),
          })),
        }
      : {}),
  }));
}
async function create(initial: Record<string, any> = {}) {
  readError.value = '';
  formEpoch.value++;
  const token = ++request;
  target = { kind: 'create', token, source: cloneDeep(initial.source) };
  bindFormSave();
  fields.value = cloneDeep(incidentFields.facts);
  formTitle.value = '登记已知事故发现事实';
  await nextTick();
  if (token !== request) return;
  formRef.value?.open(incidentFormValues(cloneDeep(initial)));
}
async function edit(kind: string) {
  if (readError.value || loading.value || !data.value.id || (kind === 'confirm' && !canConfirm.value)) return;
  const epoch = ++formEpoch.value;
  target = {
    kind,
    id: data.value.id,
    token: request,
    factsId: data.value.factsId,
    handlingId: data.value.handlingId,
    basisRevision: data.value.basisRevision,
    source: cloneDeep(data.value.facts?.facts?.source),
  };
  bindFormSave();
  fields.value = cloneDeep(fieldsFor(kind));
  formTitle.value = (
    {
      source: '核对并补充实际业务来源',
      facts: '追加事故事实版本',
      report: '登记真实报案、报告或结果备案',
      handling: '登记实际核查处理结果',
      confirm: '本人核对当前完整案卷并签署',
    } as any
  )[kind];
  const initial =
    kind === 'facts'
      ? incidentFormValues({
          ...cloneDeep(data.value.facts.facts),
          replacementReason: undefined,
          replacementFileIds: undefined,
        })
      : {};
  await nextTick();
  if (epoch === formEpoch.value && !readError.value) formRef.value?.open(initial);
}
async function submit(frozen: any, v: Record<string, any>) {
  const value = incidentValues(v);
  let result: any;
  if (frozen.kind === 'create') {
    result = await incidentApi.create({
      ...value,
      ...(frozen.source ? { source: frozen.source } : { source: undefined }),
    });
    if (request === frozen.token && !(await open(result.data))) return false;
  } else {
    const body =
      frozen.kind === 'source'
        ? {
            expectedFactsId: frozen.factsId,
            source: {
              bizType: value.sourceBizType,
              bizId: value.sourceBizId,
              recordId: value.sourceRecordId || undefined,
            },
            reason: value.reason,
            fileIds: value.fileIds,
          }
        : frozen.kind === 'facts'
          ? { expectedFactsId: frozen.factsId, facts: { ...value, source: frozen.source } }
          : frozen.kind === 'confirm'
            ? { ...value, basisRevision: frozen.basisRevision }
            : {
                ...value,
                factsId: frozen.factsId,
                ...(frozen.kind === 'report'
                  ? { handlingId: value.kind === 'RESULT_FILING' ? frozen.handlingId : undefined }
                  : {}),
              };
    result = await incidentApi.record(frozen.id, frozen.kind, body);
    if (data.value.id === frozen.id && request === frozen.token && !(await open(frozen.id))) return false;
  }
  return result;
}
function saved() {
  if (readError.value) return;
  emit('saved');
}
defineExpose({ open, create });
</script>
