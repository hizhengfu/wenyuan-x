<template>
  <HPageView v-model:show="show" :trap-focus="!formRef?.isOpen?.()" title="修复实际验收与归档原件" width="1120px">
    <NAlert v-if="readError" type="error">{{ readError }} <NButton @click="reload">重新读取</NButton></NAlert>
    <NSpin v-else :show="loading"
      ><NSpace v-if="!loading" vertical :size="16">
        <CollectionArchiveIssues :data="data" />
        <NText>原收藏单位：{{ data.orgName || data.orgId }} · 修复项目：{{ data.restorationId }}</NText>
        <NAlert type="info"
          >先核实实际竣工及全过程资料，再登记实际验收。一级文物修复须由省级文物行政部门组织验收，结果报国务院文物行政部门备案。归档与归库分别办理，归档不会自动确认实物已归库。</NAlert
        >
        <NAlert v-if="!data.completed" type="warning">尚缺本次实际竣工原件；旧完成或通过状态不能代替。</NAlert>
        <NAlert v-if="data.acceptanceOverdue" type="warning"
          >已超过竣工后三个月，请保留实际迟办原因及相关处理依据，按真实验收时间登记。</NAlert
        >
        <NText v-if="data.acceptanceDueAt"
          >文物修复验收期限：{{ formatLocalDateValue(data.acceptanceDueAt, 'YYYY-MM-DD HH:mm:ss') }}</NText
        >
        <NSpace v-if="data.completed && !data.archived && !loading && !readError">
          <NButton
            v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,RESTORATION_ACCEPTANCE,COLLECTION_RESTORATION_ACCEPTANCE'"
            v-if="data.canAccept" @click="edit('acceptance')"
            >{{ latest ? '登记复验或订正原件' : '登记实际验收原件' }}</NButton
          >
          <NButton
            v-if="latest && data.canAccept"
            v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,RESTORATION_ACCEPTANCE,COLLECTION_RESTORATION_ACCEPTANCE'"
            @click="edit('filing')"
            >登记本次验收结果实际备案</NButton
          >
          <NButton
            v-if="latest?.content.conclusion === 'PASS' && data.archiveId && data.canArchive"
            v-permission="'COLLECTION_ADMIN,RESTORATION_ADMIN,RESTORATION_ARCHIVE,COLLECTION_RESTORATION_ARCHIVE'"
            @click="edit('archive')"
            >登记实际归档及养护交接</NButton
          >
        </NSpace>
        <NAlert v-if="data.completed && !data.archiveId" type="info"
          >请在修复档案页面保存档案编号及前后状况等主件；验收和备案原件可先登记。</NAlert
        >
        <NAlert v-if="latest && latest.content.conclusion !== 'PASS'" type="warning"
          >最新验收尚未通过，应先处理问题并保留真实复验记录，不能直接归档。</NAlert
        >
        <NCollapse
          ><NCollapseItem
            v-for="record in [...(data.records || [])].reverse()"
            :key="record.id"
            :name="record.id"
            :title="`第 ${record.version} 次实际验收 · ${record.id}`"
          >
            <NSpace vertical :size="14">
              <CollectionEvidenceReadView :original-url="originalUrl"
                :archive-files="stageFiles(record.archives, 'ACCEPTED')"
                :fields="restorationAcceptanceFields(data.firstRelic, !!record.content.previousRecordId)"
                :data="record.content"
              />
              <DeregistrationEvidenceList case-type="restoration-closeout" :case-id="projectId" :administrative-evidence="record.filings || []" :archives="record.archives" administrative-phase-prefix="FILED" />
              <NCollapse
                ><NCollapseItem
                  v-for="a in record.archives || []"
                  :key="a.id"
                  :name="a.id"
                  :title="`永久原件 · ${a.phase}`"
                  ><CollectionCaseOriginalFiles case-type="restoration-closeout" :case-id="projectId"
                    v-for="file in a.files"
                    :key="file.fileId"
                    :value="file.fileId" :archive-files="a.files" /></NCollapseItem
              ></NCollapse>
            </NSpace> </NCollapseItem
        ></NCollapse>
        <NCard v-if="data.archiveSummary" title="已保存的档案主件（归档依据）"
          ><CollectionEvidenceReadView :original-url="originalUrl" :fields="archiveSummaryFields" :data="data.archiveSummary" :archive-files="data.archiveFiles || []"
        /></NCard>
        <NCard v-if="data.archiveEvidence" title="实际归档与后续养护交接"
          ><CollectionEvidenceReadView :original-url="originalUrl" :fields="restorationArchiveFields" :data="data.archiveEvidence" :archive-files="data.archiveFiles || []"
        /></NCard>
        <NAlert v-else-if="data.archived" type="warning"
          >现有历史归档标记尚未关联上述实际原件；原记录保留，须在历史资料核查时单独确认。</NAlert
        >
      </NSpace></NSpin
    >
  </HPageView>
  <CollectionEvidenceForm :trap-focus="true" v-if="formContext && show && active" :key="formContext.token" ref="formRef" :title="title" :fields="fields" :save="save" />
</template>
<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import CollectionArchiveIssues from '@/modules/collection/components/CollectionArchiveIssues.vue';
import { useRestorationAccountWindow } from '../composables/useRestorationAccountWindow';
import { computed, nextTick, ref, shallowRef, watch, onBeforeUnmount, onActivated, onDeactivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import { formatLocalDateValue } from '@/utils';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import CollectionCaseOriginalFiles from '@/modules/collection/components/CollectionCaseOriginalFiles.vue';
import DeregistrationEvidenceList from '@/modules/collection/components/DeregistrationEvidenceList.vue';
import { restorationCloseoutApi } from '../api/closeout';
import { restorationArchiveApi } from '../api/archive';
import {
  restorationAcceptanceFields,
  restorationAcceptanceFilingFields,
  restorationArchiveFields,
} from '../utils/restorationCloseoutFields';
const archiveSummaryFields = [
  { key: 'archiveNo', label: '档案编号' },
  { key: 'beforeDescription', label: '修复前状况' },
  { key: 'afterDescription', label: '修复后状况' },
  { key: 'remarks', label: '档案备注' },
];
const emit = defineEmits<{ (e: 'saved'): void }>();
const show = ref(false),
  loading = ref(false),
  data = ref<any>({}),
  formRef = ref<any>();
const stage = ref<'acceptance' | 'filing' | 'archive'>('acceptance');
const first = ref(false),
  revision = ref(false);
let projectId = '', request = 0, editRequest = 0;
function stageFiles(archives: any[] | undefined, phase: string): any[] {
  const matching = Array.isArray(archives) ? archives.filter((archive) => archive.phase === phase) : [];
  return matching.length === 1 && Array.isArray(matching[0].files) ? matching[0].files : [];
}
function originalUrl(fileId: string) { return '/collection/restoration-closeout/' + encodeURIComponent(projectId) + '/original/' + encodeURIComponent(fileId); }
const active = ref(true), readError = ref('');
const formContext = shallowRef<{ token: number; orgId: string; orgName?: string; id: string }>();
const save = shallowRef<(value: Record<string, any>) => Promise<any>>(async () => false);
function invalidate() {
  ++request; ++editRequest; formRef.value?.close?.(); formContext.value = undefined;
  data.value = {}; loading.value = false;
}
watch(show, value => { if (!value) invalidate(); }, { flush: 'sync' });
useRestorationAccountWindow(() => {
  show.value = false; invalidate(); projectId = ''; readError.value = ''; save.value = async () => false;
});
onBeforeUnmount(() => { active.value = false; invalidate(); });
onDeactivated(() => { active.value = false; show.value = false; invalidate(); });
onActivated(() => { active.value = true; });
const latest = computed(() => data.value.records?.at(-1));
const fields = computed(() =>
  stage.value === 'acceptance'
    ? restorationAcceptanceFields(first.value, revision.value)
    : stage.value === 'filing'
      ? restorationAcceptanceFilingFields
      : restorationArchiveFields,
);
const title = computed(
  () =>
    ({ acceptance: '登记实际验收或复验原件', filing: '登记本次验收结果实际备案', archive: '登记实际归档与养护交接' })[
      stage.value
    ] + ` · ${formContext.value?.orgName || formContext.value?.orgId || ''} · ${formContext.value?.id || ''}`,
);
function readResponse(response: any, id: string) {
  const d = response?.data;
  const text = (v: any) => typeof v === 'string' && !!v.trim();
  if ((response?.status !== undefined && response.status !== 200) || !d || d.restorationId !== id ||
      !text(d.orgId) || !text(d.revision) || typeof d.canAccept !== 'boolean' || typeof d.canArchive !== 'boolean' ||
      typeof d.archived !== 'boolean' || !Array.isArray(d.records) || (d.completed && !text(d.planId)) ||
      (d.archiveId && (!text(d.archiveId) || !text(d.archiveRevision))) ||
      (d.archiveSummary && (d.archiveSummary.restorationId !== id || d.archiveSummary.archiveId !== d.archiveId))) {
    throw new Error('验收归档响应缺少原馆、版本或对应项目，请重新读取');
  }
  const ids = new Set<string>();
  for (const [index, row] of d.records.entries()) {
    if (!row || !text(row.id) || ids.has(row.id) || row.version !== index + 1 || !row.content ||
        row.content.planId !== d.planId || (row.content.previousRecordId || null) !== (d.records[index - 1]?.id || null) ||
        !Array.isArray(row.filings) || !Array.isArray(row.archives)) {
      throw new Error('验收原件清单或方案关联不完整，请重新核对');
    }
    ids.add(row.id);
  }
  return cloneDeep(d);
}
async function open(id: string) {
  if (!active.value || !id) return false;
  invalidate(); projectId = id; readError.value = ''; show.value = true; loading.value = true;
  const token = request;
  try {
    const response = await restorationCloseoutApi.detail(id);
    if (token !== request || !show.value || !active.value) return false;
    data.value = readResponse(response, id);
    return true;
  } catch (error: any) {
    if (token === request && show.value && active.value) readError.value = errorText(error, '无法读取验收归档原件，请核对当前权限后重试');
    return false;
  } finally { if (token === request) loading.value = false; }
}
async function reload() { return open(projectId); }
async function edit(kind: typeof stage.value) {
  if (!show.value || !active.value || loading.value || readError.value) return;
  const id = projectId, orgId = data.value.orgId;
  if (!await open(id)) return;
  if (data.value.orgId !== orgId) {
    data.value = {}; readError.value = '项目原收藏单位已变化，请关闭后重新核对'; return;
  }
  const d = cloneDeep(data.value), previous = d.records.at(-1)?.id || null;
  if (!d.completed || d.archived || (kind === 'archive' ? !d.canArchive : !d.canAccept) ||
      (kind !== 'acceptance' && !previous) || (kind === 'archive' && (!d.archiveId || d.records.at(-1)?.content.conclusion !== 'PASS'))) return;
  stage.value = kind; first.value = !!d.firstRelic; revision.value = !!previous;
  const token = ++editRequest, epoch = request;
  const owned = () => active.value && show.value && request === epoch && editRequest === token &&
    projectId === id && formContext.value?.token === token;
  formContext.value = { token, orgId, orgName: d.orgName, id };
  let saving = false;
  save.value = async (input) => {
    if (!owned() || !formRef.value?.isOpen?.() || saving) return false;
    saving = true;
    const value = cloneDeep(input);
    try {
      const response: any = kind === 'acceptance'
        ? await restorationCloseoutApi.acceptance(id, { ...value, planId: d.planId, previousRecordId: previous }, orgId, d.revision)
        : kind === 'filing'
          ? await restorationCloseoutApi.filing(id, { acceptanceRecordId: previous, evidence: { ...value, action: 'FILING' } }, orgId, d.revision)
          : await restorationArchiveApi.approve(d.archiveId, { ...value, archiveRevision: d.archiveRevision, planId: d.planId, acceptanceRecordId: previous }, orgId, d.revision);
      if (!owned() || !formRef.value?.isOpen?.()) return false;
      if ((response?.status !== undefined && response.status !== 200) || typeof response?.data !== 'string' || !response.data.trim() ||
          (kind === 'archive' && response.data !== d.archiveId)) throw new Error('登记响应无法确认，请重新读取核对实际结果');
      if (await open(id)) emit('saved');
      return false;
    } catch (error) { if (owned() && formRef.value?.isOpen?.()) throw error; return false; }
    finally { saving = false; }
  };
  await nextTick();
  if (owned()) formRef.value?.open({});
}
defineExpose({ open });
</script>
