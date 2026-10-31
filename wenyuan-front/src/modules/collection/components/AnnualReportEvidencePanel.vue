<template>
  <NCard title="实际签署、报送和备案原件">
    <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
      <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
      <NButton v-if="readError" :disabled="loading" @click="reload">重新读取报送原件</NButton>
      <NAlert v-if="data.legacyArchive" type="warning"
        >存在旧版签章归档。旧归档时间
        {{ date(data.legacyArchive.reportedAt) }} 不是实际报送日期；尚不能据此认定完成备案。<HFileList :value="data.legacyArchive.reportAttachmentIds" show-download-btn /></NAlert>
      <NButton v-if="canWrite" type="primary" :disabled="loading || !!readError || !completeYear" @click="editSigned"
        >登记已实际签署的年度报表</NButton
      >
      <NText v-if="!completeYear">本年度未结束，可先核对阶段预览，完整年报在年度结束后留档。</NText>
      <NCollapse>
        <NCollapseItem
          v-for="row in [...records].reverse()"
          :key="row.id"
          :name="row.id"
          :title="`第 ${row.version} 版 · ${annualEvidenceStatus(row)}`"
        >
          <NSpace vertical :size="14">
            <CollectionEvidenceReadView
              :fields="annualSignedFields"
              :data="row.signed"
              :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'SIGNED').flatMap((archive: any) => archive.files || [])" :original-url="stageOriginalUrl(row, 'SIGNED')"
            />
            <AnnualLedgerFigures :data="row.signed.figures" />
            <NAlert v-if="row.calculated?.issues?.length" type="warning"
              >留档时系统有
              {{ row.calculated.issues.length }} 条历史缺口；正式数值以所附签署报表和核对明细为依据。</NAlert
            >
            <CollectionEvidenceReadView
              v-if="row.submission"
              :fields="annualSubmissionFields"
              :data="row.submission"
              :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'SUBMITTED').flatMap((archive: any) => archive.files || [])" :original-url="stageOriginalUrl(row, 'SUBMITTED')"
            />
            <CollectionEvidenceReadView
              v-if="row.filing"
              :fields="annualFilingFields"
              :data="row.filing"
              :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'FILED').flatMap((archive: any) => archive.files || [])" :original-url="stageOriginalUrl(row, 'FILED')"
            />
            <NSpace v-if="canWrite"
              ><NButton v-if="!row.submission" @click="editStage(row, 'submission')">登记实际报送凭据</NButton
              ><NButton v-else-if="!row.filing" @click="editStage(row, 'filing')">登记实际备案回执</NButton></NSpace
            >
            <NCollapse
              ><NCollapseItem title="签署留档时的系统计算与历史缺口"
                ><AnnualLedgerFigures :data="row.calculated" /><NText
                  v-for="issue in row.calculated?.issues || []"
                  :key="issue.collectionId"
                  >{{ issue.totalRegNo }}：{{ issue.description }}</NText
                ></NCollapseItem
              ></NCollapse
            >
            <NCollapse
              ><NCollapseItem
                v-for="a in row.archives || []"
                :key="a.id"
                :name="a.id"
                :title="`永久原件 · ${date(a.archivedAt)}`"
                ><HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(row, a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
              ></NCollapse
            >
          </NSpace>
        </NCollapseItem>
      </NCollapse>
    </NSpace>
  </NCard>
  <AnnualReportSignedForm ref="signedRef" :save="saveSigned" @saved="signedSaved" />
  <CollectionEvidenceForm
    ref="stageRef"
    :title="stageTitle"
    :fields="stage === 'submission' ? annualSubmissionFields : annualFilingFields"
    :save="saveStage"
    @saved="stageSaved"
  />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, ref, shallowRef, watch, nextTick, onActivated, onDeactivated, onBeforeUnmount } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { formatLocalDateValue } from '@/utils';
import { collectionApi } from '../api/collection';
import AnnualReportSignedForm from './AnnualReportSignedForm.vue';
import AnnualLedgerFigures from './AnnualLedgerFigures.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import {
  annualSignedFields,
  annualSubmissionFields,
  annualFilingFields,
  annualEvidenceStatus,
} from '../utils/annualReportFields';
const props = defineProps<{ year: number; completeYear: boolean; hasIssues: boolean }>();
const auth = useAuthStore();
const canWrite = computed(() =>
  !!auth.userInfo?.id && auth.userInfo.enabled === true &&
  auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_STAT'),
);
const data = ref<any>({}),
  loading = ref(false),
  readError = ref(''),
  signedRef = ref<any>(),
  stageRef = ref<any>();
const stage = ref<'submission' | 'filing'>('submission'),
  stageTitle = ref('实际报送凭据');
const records = computed<any[]>(() => data.value.records || []);
const saveSigned = shallowRef<(value: any) => Promise<any>>(async () => false),
  saveStage = shallowRef<(value: any) => Promise<any>>(async () => false);
const signedSaved = shallowRef<() => unknown>(reload),
  stageSaved = shallowRef<() => unknown>(reload);
let request = 0,
  formRevision = 0,
  active = true,
  wasInactive = false;
const date = (v: any) => (v == null ? '未登记' : formatLocalDateValue(v, 'YYYY-MM-DD HH:mm:ss'));
const originalUrl = (row: any, archive: any, fileId: string) =>
  `/collection/stat/annual/reports/${props.year}/${row.id}/original/${archive.id}/${fileId}`;
const stageOriginalUrl = (row: any, phase: string) => (fileId: string) => {
  const archive = (row.archives || []).find(
    (a: any) => a.phase === phase && a.files?.some((f: any) => f.fileId === fileId),
  );
  return archive ? originalUrl(row, archive, fileId) : '';
};
function closeForms() {
  ++formRevision;
  signedRef.value?.close();
  stageRef.value?.close();
  saveSigned.value = async () => false;
  saveStage.value = async () => false;
}
function clear() {
  ++request;
  closeForms();
  data.value = {};
  loading.value = false;
  readError.value = '';
}
const currentForm = (token: number, actor: string | undefined, form: any) =>
  active && canWrite.value && token === formRevision && actor === auth.userInfo?.id && form?.isOpen?.() !== false;
function receipt(result: any, expected?: string) {
  if (
    (result?.status !== undefined && result.status !== 200) ||
    typeof result?.data !== 'string' ||
    !result.data.trim() ||
    (expected && result.data !== expected)
  )
    throw Error('登记回执无法确认，请回原年度报表核对实际结果');
  return result;
}
async function reload() {
  if (!active) return false;
  const token = ++request,
    year = props.year;
  data.value = {};
  readError.value = '';
  loading.value = true;
  try {
    const response: any = await collectionApi.annualReports(year);
    if (!active || token !== request) return false;
    const value = response?.data;
    if (
      (response?.status !== undefined && response.status !== 200) ||
      !value ||
      value.year !== year ||
      typeof value.orgId !== 'string' ||
      !value.orgId.trim() ||
      !Array.isArray(value.records) ||
      value.records.some((r: any) => !r || typeof r.id !== 'string' || !r.id || !r.signed || !r.calculated) ||
      new Set(value.records.map((r: any) => r.id)).size !== value.records.length
    )
      throw Error('年度报送原件身份或版本清单不完整');
    data.value = value;
    return true;
  } catch {
    if (active && token === request) {
      closeForms();
      readError.value = '年度报送原件读取失败，请核对当前权限和收藏单位后重试。';
    }
  } finally {
    if (token === request) loading.value = false;
  }
  return false;
}
watch(
  () => props.year,
  () => {
    void reload();
  },
  { immediate: true },
);
watch(
  () => [auth.userInfo?.id, auth.userInfo?.groupId, auth.userInfo?.enabled, auth.permissions],
  () => {
    clear();
    if (active) void reload();
  },
  { deep: true, flush: 'sync' },
);
onActivated(() => {
  active = true;
  if (wasInactive) {
    wasInactive = false;
    void reload();
  }
});
onDeactivated(() => {
  active = false;
  wasInactive = true;
  clear();
});
onBeforeUnmount(() => {
  active = false;
  clear();
});
function savedFor(year: number, orgId: string, token: number, actor: string | undefined) {
  return () => {
    if (active && canWrite.value && token === formRevision && actor === auth.userInfo?.id && props.year === year && data.value.orgId === orgId) return reload();
  };
}
function editSigned() {
  if (!active || !canWrite.value || loading.value || readError.value || !props.completeYear || !data.value.orgId) return;
  closeForms();
  const token = formRevision,
    actor = auth.userInfo?.id,
    year = props.year,
    orgId = data.value.orgId;
  let pending = false;
  saveSigned.value = async (value) => {
    if (!currentForm(token, actor, signedRef.value) || pending) return false;
    if (value.year !== year) throw Error('签署年度已变化，请重新打开原年度表单');
    pending = true;
    try {
      const result = await collectionApi.signAnnualReport(value, orgId);
      if (!currentForm(token, actor, signedRef.value)) return false;
      return receipt(result);
    } finally {
      pending = false;
    }
  };
  signedSaved.value = savedFor(year, orgId, token, actor);
  signedRef.value?.open({ year, hasIssues: props.hasIssues, replacement: records.value.length > 0 });
}
async function editStage(row: any, type: 'submission' | 'filing') {
  if (!active || !canWrite.value || loading.value || readError.value || !records.value.some((r) => r.id === row.id)) return;
  closeForms();
  const token = formRevision,
    actor = auth.userInfo?.id,
    id = row.id,
    year = props.year,
    orgId = data.value.orgId;
  let pending = false;
  stage.value = type;
  stageTitle.value = `${year}年度 · 第 ${row.version} 版 · ${type === 'submission' ? '实际报送凭据' : '实际备案回执'}`;
  saveStage.value = async (value) => {
    if (!currentForm(token, actor, stageRef.value) || pending) return false;
    pending = true;
    try {
      const result = await (type === 'submission'
        ? collectionApi.submitAnnualReport(id, value)
        : collectionApi.fileAnnualReport(id, value));
      if (!currentForm(token, actor, stageRef.value)) return false;
      return receipt(result, id);
    } finally {
      pending = false;
    }
  };
  stageSaved.value = savedFor(year, orgId, token, actor);
  await nextTick();
  if (active && canWrite.value && actor === auth.userInfo?.id && token === formRevision)
    stageRef.value?.open(type === 'filing' ? { authorityName: row.submission.authorityName } : {});
}
</script>
