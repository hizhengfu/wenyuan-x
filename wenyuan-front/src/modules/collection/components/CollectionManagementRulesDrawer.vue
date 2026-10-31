<template>
  <HPageForm v-model:show="show" :title="`${org.name || '收藏单位'} · 藏品管理制度与备案`" content-only :enable-cancel="false">
      <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="data" />
        <NAlert type="info"
          >按已实际印发的本馆制度汇编和内部流程留档，并注明十二类管理事项在原件中的位置。未覆盖事项如实留空；收到备案回执不代表制度内容已完整。未来施行版本提前留档时，保留当前已生效版本。</NAlert
        >
        <NButton secondary :loading="loading" @click="reload">重新读取制度原件</NButton>
        <NButton type="primary" :disabled="loading || !!readError" @click="editIssued">登记实际印发的制度版本</NButton>
        <NSpin :show="loading"
          ><NAlert v-if="readError" type="error">{{ readError }}</NAlert>
          <NAlert v-if="!loading && !readError && !data.currentId" type="warning">尚无已生效制度版本原件，请核实馆方制度。</NAlert>
          <NCollapse>
            <NCollapseItem
              v-for="row in [...records].reverse()"
              :key="row.id"
              :name="row.id"
              :title="`第 ${row.version} 版 · ${row.rules.title} · ${managementRulesStatus(row)}`"
            >
              <NSpace vertical :size="12">
                <NTag :type="row.id === data.currentId ? 'success' : 'default'">{{
                  row.id === data.currentId ? '当前已生效版本' : '非当前版本'
                }}</NTag>
                <NAlert v-if="row.missingTopics?.length" type="warning"
                  >制度内容待核对：{{ row.missingTopics.map(topicLabel).join('、') }}。这些事项尚无覆盖依据。</NAlert
                >
                <CollectionEvidenceReadView :fields="readRulesFields" :data="readRules(row.rules)" :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'ISSUED').flatMap((archive: any) => archive.files || [])" :original-url="fileId => originalUrl(row, 'ISSUED', fileId)" />
                <CollectionEvidenceReadView
                  v-if="row.submission"
                  :fields="rulesSubmissionFields"
                  :data="row.submission"
                  :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'SUBMITTED').flatMap((archive: any) => archive.files || [])" :original-url="fileId => originalUrl(row, 'SUBMITTED', fileId)"
                />
                <CollectionEvidenceReadView v-if="row.filing" :fields="rulesFilingFields" :data="row.filing" :archive-files="(row.archives || []).filter((archive: any) => archive.phase === 'FILED').flatMap((archive: any) => archive.files || [])" :original-url="fileId => originalUrl(row, 'FILED', fileId)" />
                <NButton v-if="!row.submission" @click="editStage(row, 'submission')">登记实际制度报送凭据</NButton>
                <NButton v-else-if="!row.filing" @click="editStage(row, 'filing')">登记实际制度备案回执</NButton>
                <NCollapse
                  ><NCollapseItem
                    v-for="a in row.archives || []"
                    :key="a.id"
                    :name="a.id"
                    :title="`永久原件 · ${formatLocalDateValue(a.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`"
                    ><HFileList :files="a.files" :original-url="(fileId: string) => archiveOriginalUrl(row.id, a.id, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
                ></NCollapse>
              </NSpace>
            </NCollapseItem>
          </NCollapse>
        </NSpin>
      </NSpace>
  </HPageForm>
  <CollectionEvidenceForm ref="formRef" :title="formTitle" :fields="formFields" :save="save" @saved="reload" />
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, ref, nextTick } from 'vue';
import { formatLocalDateValue } from '@/utils';
import { orgApi } from '../api/org';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {
  managementRuleTopics,
  managementRulesFields,
  rulesSubmissionFields,
  rulesFilingFields,
  managementRulesStatus,
  rulesPayload,
} from '../utils/managementRulesFields';
const show = ref(false),
  org = ref<{ id: string; name?: string }>({ id: '' }),
  data = ref<any>({}),
  loading = ref(false),
  readError = ref(''),
  formRef = ref<any>();
const records = computed<any[]>(() => data.value.records || []);
const stage = ref<'issued' | 'submission' | 'filing'>('issued'),
  revision = ref(false);
let editingOrg = '',
  editingId = '',
  request = 0;
const formFields = computed(() =>
  stage.value === 'issued'
    ? managementRulesFields(revision.value)
    : stage.value === 'submission'
      ? rulesSubmissionFields
      : rulesFilingFields,
);
const formTitle = computed(() =>
  stage.value === 'issued'
    ? '实际制度版本原件'
    : stage.value === 'submission'
      ? '实际制度报送凭据'
      : '实际制度备案回执',
);
const readRulesFields = managementRulesFields();
const topicLabel = (value: string) => managementRuleTopics.find(([key]) => key === value)?.[1] || value;
function archiveOriginalUrl(recordId: string, archiveId: string, fileId: string) {
  return `/collection/org/${encodeURIComponent(org.value.id)}/management-rules/${encodeURIComponent(recordId)}/archive/${encodeURIComponent(archiveId)}/original/${encodeURIComponent(fileId)}`;
}
function originalUrl(row: any, phase: string, fileId: string) {
  const archive = (row.archives || []).find((item: any) => item.phase === phase && item.files?.some((file: any) => file.fileId === fileId));
  return archive ? archiveOriginalUrl(row.id, archive.id, fileId) : '';
}
function readRules(value: any) {
  return {
    ...value,
    ...Object.fromEntries(Object.entries(value.topicReferences || {}).map(([key, v]) => [`topic_${key}`, v])),
  };
}
async function open(value: { id: string; name?: string }) {
  org.value = { ...value };
  data.value = {};
  show.value = true;
  await reload();
}
async function reload() {
  if (!org.value.id) return;
  const token = ++request;
  loading.value = true;
  data.value = {};
  readError.value = '';
  try {
    const response: any = await orgApi.managementRules(org.value.id);
    if (token === request) data.value = response.data;
  } catch {
    if (token === request) readError.value = '制度原件读取失败，请核对当前权限后重试。';
  } finally {
    if (token === request) loading.value = false;
  }
}
async function editIssued() {
  stage.value = 'issued';
  editingOrg = org.value.id;
  editingId = '';
  revision.value = records.value.length > 0;
  await nextTick();
  formRef.value?.open({});
}
async function editStage(row: any, type: 'submission' | 'filing') {
  stage.value = type;
  editingOrg = org.value.id;
  editingId = row.id;
  await nextTick();
  formRef.value?.open(type === 'filing' ? { authorityName: row.submission.authorityName } : {});
}
const save = (value: any) =>
  stage.value === 'issued'
    ? orgApi.registerManagementRules(editingOrg, rulesPayload(value))
    : stage.value === 'submission'
      ? orgApi.submitManagementRules(editingOrg, editingId, value)
      : orgApi.fileManagementRules(editingOrg, editingId, value);
defineExpose({ open });
</script>
