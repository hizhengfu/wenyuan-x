<template>
  <NCard title="岗位变动与原项目承接">
    <NSpace vertical>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NAlert v-if="info.evidenceErrors?.length" type="error">{{ info.evidenceErrors.join('；') }}</NAlert>
      <NText v-if="info.notice">{{ info.notice }}</NText>
      <NAlert v-if="info.record" type="warning"
        >旧项目执行已终止，原批准只保留历史事实；不得继续送修、开工或使用旧专项授权。</NAlert
      >
      <NAlert v-else-if="info.originRecord" type="info"
        >本项目由岗位变动建立。请核实新项目基本材料，再登记完整技术方案、资质和适用行政批文，并重新提交指定保管负责人和主要负责人审批；新送修单仍须独立办理。</NAlert
      >
      <template v-if="record">
        <NText
          >原项目 {{ record.requisitionRetirement.sourceId }} → 新项目
          {{ record.requisitionRetirement.replacementSourceId }}</NText
        >
        <CollectionEvidenceReadView v-if="replacementArchiveId" :fields="recordFields" :data="record.requisitionRetirement"
          :original-url="fileId => replacementOriginalUrl(replacementArchiveId!, fileId)" :archive-files="replacementArchiveFiles" />
        <NAlert v-else type="error">责任承接档案缺失，暂不能核对原件。</NAlert>
        <NText
          >本次已终止的旧送修单：{{
            Object.keys(record.requisitionRetirement.requisitions || {}).join('、') || '无'
          }}；已取消旧专项授权 {{ record.canceledAuthorizationIds?.length || 0 }} 项。</NText
        >
      </template>
      <NSpace>
        <NButton :loading="loading" :disabled="editing" @click="load">重新核对承接状态</NButton>
        <NButton v-if="info.canReplace && !error" :loading="editing" @click="edit">岗位变动：重建修复责任方案</NButton>
        <NButton v-if="info.supersededById" @click="emit('open', info.supersededById)">查看承接的新修复项目</NButton>
        <NButton v-if="info.replacesId" @click="emit('open', info.replacesId)">查看原修复项目及旧送修原件</NButton>
      </NSpace>
      <NCollapse v-if="info.archives?.length">
        <NCollapseItem
          v-for="archive in info.archives"
          :key="archive.id"
          :name="archive.id"
          :title="archive.phase === 'CREATED' ? '新项目承接原件' : '原项目终止执行原件'"
        >
          <HFileList :files="archive.files" :original-url="(fileId: string) => replacementOriginalUrl(archive.id, fileId)" :revision="archive.id" show-download-btn />
        </NCollapseItem>
      </NCollapse>
    </NSpace>
  </NCard>
  <CollectionEvidenceForm :key="formKey" ref="formRef" title="未点交修复项目责任变更" :fields="fields" :save="save">
    <template #context>
      <NAlert type="warning"
        >原项目
        {{
          projectId
        }}
        的后续执行和全部未点交送修单将终止，原方案、合同、审批和签署档案保留。这里只建立新草稿，不能继承旧行政批准、审批或专项授权；已有实物点交、开工或安全处置事实时应据实办理停止及后续保管。</NAlert
      >
      <NText
        >当前登记本人：{{ info.directorUserId }}。新技术方案的保管负责人和主要负责人须另行指定、核实并本人审批。</NText
      >
    </template>
  </CollectionEvidenceForm>
</template>
<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { computed, nextTick, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue';
import { useRestorationAccountWindow } from '../composables/useRestorationAccountWindow';
import { cloneDeep } from 'lodash-es';
import { restorationApi } from '../api/restoration';
import { restorationOrgApi } from '../api/org';
import CollectionEvidenceForm from '@/modules/collection/components/CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from '@/modules/collection/components/CollectionEvidenceReadView.vue';
import type { EvidenceField } from '@/modules/collection/utils/evidenceFields';
const props = defineProps<{ projectId: string }>();
const emit = defineEmits<{ (e: 'open', id: string): void; (e: 'created', original: string, child: string): void }>();
const info = ref<any>({}),
  error = ref(''),
  loading = ref(false),
  editing = ref(false),
  formRef = ref<any>();
const organizations = ref<{ label: string; value: string }[]>([]);
const record = computed(() => info.value.record || info.value.originRecord);
const replacementArchiveId = computed<string | undefined>(() =>
  info.value.archives?.find((archive: any) => archive.phase === (info.value.record ? 'REPLACED' : 'CREATED'))?.id,
);
const replacementArchiveFiles = computed(() => info.value.archives?.find((archive: any) => archive.id === replacementArchiveId.value)?.files || []);
const replacementOriginalUrl = (archiveId: string, fileId: string) =>
  `/collection/restoration/${encodeURIComponent(props.projectId)}/responsibility-replacement/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
const recordFields: EvidenceField[] = [
  { key: 'reason', label: '实际变动及终止执行原因' },
  { key: 'authorizedBy', label: '登记本人', type: 'user' },
  { key: 'authorizedAt', label: '实际登记时间', type: 'datetime' },
  { key: 'appointmentFileIds', label: '任职或岗位变动依据', type: 'file' },
  { key: 'withdrawalFileIds', label: '旧项目终止执行签署原件', type: 'file' },
];
const fields = computed<EvidenceField[]>(() => [
  { key: 'reason', label: '实际变动及终止执行原因', type: 'textarea', required: true },
  { key: 'appointmentFileIds', label: '任职或岗位变动依据', type: 'file', required: true },
  { key: 'withdrawalFileIds', label: '旧项目终止执行签署原件', type: 'file', required: true },
  { key: 'operatorUserId', label: '新项目经办人', type: 'user', required: true },
  { key: 'reviewerUserId', label: '一级藏品复核人', type: 'user', required: info.value.collectionLevel === 'FIRST' },
  { key: 'restoreOrgId', label: '新项目拟承担机构', type: 'select', options: organizations.value, required: true },
]);
let generation = 0, active = true;
const formKey = ref(0);
const save = ref<(value: Record<string, any>) => Promise<unknown>>(async () => false);
function invalidate() {
  ++generation; ++formKey.value; formRef.value?.close?.();
  info.value = {}; organizations.value = []; error.value = ''; loading.value = false; editing.value = false;
  save.value = async () => false;
}
const current = (token: number, id: string) => active && token === generation && props.projectId === id;
useRestorationAccountWindow(invalidate);
watch(() => props.projectId, () => { invalidate(); if (active) void load(); }, { flush: 'sync' });
onBeforeUnmount(() => { active = false; invalidate(); });
onDeactivated(() => { active = false; invalidate(); });
onActivated(() => { if (!active) { active = true; void load(); } });
async function load() {
  if (!active) return false;
  invalidate(); const token = generation, id = props.projectId;
  loading.value = true;
  try {
    const result: any = await restorationApi.replacementInfo(id);
    if (!current(token, id)) return false;
    const value = result?.data;
    const text = (v: unknown) => typeof v === 'string' && !!v.trim();
    if ((result?.status !== undefined && result.status !== 200) || !value || value.projectId !== id ||
        !text(value.orgId) || typeof value.canReplace !== 'boolean' || (value.canReplace && !text(value.expectedRevision))) {
      throw new Error('责任承接响应缺少原馆、项目或版本，请重新核对');
    }
    info.value = cloneDeep(value); return true;
  } catch (e: any) {
    if (current(token, id)) error.value = `${errorText(e, '承接状态读取失败')}；请重新核对，不能据空记录继续办理`;
    return false;
  } finally { if (current(token, id)) loading.value = false; }
}
async function edit() {
  if (!active || editing.value || loading.value || !await load()) return;
  const token = generation, id = props.projectId, basis = cloneDeep(info.value);
  editing.value = true;
  try {
    if (!basis.canReplace || error.value) return;
    const response: any = await restorationOrgApi.page({ pageSize: 0, enabled: 'true', orgId: basis.orgId });
    if (!current(token, id)) return;
    const result = response?.data?.data ?? response?.data ?? response;
    const rows = Array.isArray(result?.list) ? result.list : Array.isArray(result) ? result : null;
    if ((response?.status !== undefined && response.status !== 200) || !rows) throw new Error('未取得本馆有效修复机构清单');
    organizations.value = rows.filter((x: any) => x.orgId === basis.orgId && x.enabled !== false)
      .map((x: any) => ({ label: x.name || x.id, value: x.id }));
    let saving = false;
    save.value = async (form) => {
      if (!current(token, id) || !formRef.value?.isOpen?.() || saving) return false;
      saving = true;
      try {
        const v = cloneDeep(form);
        const result: any = await restorationApi.replaceResponsibility(id, {
          expectedRevision: basis.expectedRevision,
          reason: v.reason, appointmentFileIds: v.appointmentFileIds, withdrawalFileIds: v.withdrawalFileIds,
          operatorUserId: v.operatorUserId, reviewerUserId: v.reviewerUserId || undefined, restoreOrgId: v.restoreOrgId,
        });
        if (!current(token, id) || !formRef.value?.isOpen?.()) return false;
        if ((result?.status !== undefined && result.status !== 200) || typeof result?.data !== 'string' ||
            !result.data.trim() || result.data === id) throw new Error('未返回有效新项目编号，请重新核对原项目承接状态');
        emit('created', id, result.data); return result;
      } catch (e) { if (current(token, id) && formRef.value?.isOpen?.()) throw e; return false; }
      finally { saving = false; }
    };
    await nextTick(); if (current(token, id)) formRef.value?.open({});
  } catch (e: any) { if (current(token, id)) error.value = errorText(e, '办理材料读取失败，请重试'); }
  finally { if (current(token, id)) editing.value = false; }
}

onMounted(load);
</script>
