<template>
  <NModal v-model:show="visible" v-bind="scrollableModalLayout" preset="card" title="迁移批次复核" style="width: min(1120px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); overflow: hidden" :mask-closable="!session.busy.value" :closable="!session.busy.value">
    <div ref="batchBodyRef" class="batch-body">
      <NAlert type="info" :show-icon="false">选中同批可办理记录，逐件核对后统一提交或复核。异常件取消选择后单独补充；本次任一件校验失败，整批均不办理。</NAlert>
      <NSelect v-model:value="batchId" :options="options" placeholder="选择本馆迁移批次" filterable :disabled="session.busy.value" @update:value="loadBatch" />
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <NSpin :show="session.busy.value">
        <NSpace justify="space-between" align="center" style="margin-bottom: 12px">
          <NRadioGroup v-model:value="mode" :disabled="session.busy.value"><NRadioButton value="submit">提交材料</NRadioButton><NRadioButton value="review">独立复核</NRadioButton></NRadioGroup>
          <NText>共 {{ rows.length }} 件 · 已选 {{ selected.length }} 件（每次最多 200 件）</NText>
        </NSpace>
        <NDataTable :columns="columns" :data="rows" :row-key="rowKey" :checked-row-keys="selected" @update:checked-row-keys="select" :pagination="{ pageSize: 20, simple: compactTable }" :max-height="300" table-layout="fixed" size="small" />
      </NSpin>
      <NForm v-if="mode === 'submit' && rows.length" label-placement="top" :disabled="session.busy.value">
        <NFormItem label="共用原账依据"><template #label><CollectionFieldLabel label="共用原账依据" help="仅为所选记录补充尚未关联的依据文件；已有逐件依据保持原样。共用原件应能识别所选每件的原登记事实和页码。" /></template><HUploadInput v-model:value="evidence" clearable /></NFormItem>
        <NFormItem label="本批核验说明" required><NInput v-model:value="reason" type="textarea" :rows="2" maxlength="500" placeholder="写明所选记录的核对范围、原账位置和结论" /></NFormItem>
        <div class="reason-grid">
          <NFormItem v-for="field in missingReasons" :key="field.key" :label="field.label"><template #label><CollectionFieldLabel :label="field.label" help="只补充该字段确实为空且尚无缺项说明的记录；各件情况不同，请取消选择并逐件办理。不得为通过校验虚构日期、人员或编号。" /></template><NInput v-model:value="reasons[field.key]" type="textarea" :rows="2" maxlength="500" /></NFormItem>
        </div>
      </NForm>
      <NForm v-else-if="mode === 'review' && rows.length" label-placement="top" :disabled="session.busy.value"><NFormItem label="本批复核意见" required><NInput v-model:value="opinion" type="textarea" :rows="2" maxlength="500" placeholder="写明查阅依据、核对结论或退回原因" /></NFormItem></NForm>
      <NCheckbox v-model:checked="confirmed" :disabled="session.busy.value">{{ mode === 'submit' ? '已逐件核对所选藏品身份、性质、等级、数量及原账依据，缺项说明属实' : '已查阅所选各件原件和提交快照，确认其原有登记事实，缺项继续保留' }}</NCheckbox>
    </div>
    <template #footer><NSpace justify="end">
      <NButton :disabled="session.busy.value" @click="visible = false">关闭</NButton>
      <NButton :disabled="session.busy.value" @click="batchId ? loadBatch() : open()">重新加载</NButton>
      <NButton v-if="mode === 'review'" type="warning" :disabled="!canProcess || !opinion.trim()" @click="process(false)">退回所选</NButton>
      <NButton type="primary" :disabled="!canProcess || !confirmed || !(mode === 'submit' ? reason.trim() : opinion.trim())" @click="process(true)">{{ mode === 'submit' ? '提交所选复核' : '所选复核通过' }}</NButton>
    </NSpace></template>
  </NModal>
  <NModal v-model:show="inspectVisible" v-bind="scrollableModalLayout" preset="card" title="逐件核对" style="width: min(880px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); overflow: hidden">
    <div v-if="inspected" class="batch-body">
      <NText strong>总登记号：{{ inspected.totalRegNo }}</NText>
      <CatalogDetail :catalog="{ collectionId: inspected.collectionId, content: inspected.record?.collectionSnapshot || inspected.collectionSnapshot || {}, completeness: { percent: 0, complete: false, missingFields: [] } }" historical />
      <NDescriptions bordered :column="1" size="small">
        <NDescriptionsItem label="原登记日期">{{ date(verificationForm(inspected).originalRegisteredAt) }}</NDescriptionsItem>
        <NDescriptionsItem label="原登记人">{{ verificationForm(inspected).originalRegisteredBy || '原账未载／待考' }}</NDescriptionsItem>
        <NDescriptionsItem v-for="field in missingReasons" :key="field.key" :label="field.label">{{ verificationForm(inspected)[field.key] || '尚无说明' }}</NDescriptionsItem>
        <NDescriptionsItem label="提交说明">{{ inspected.record?.content.reason || '尚未提交' }}</NDescriptionsItem>
        <NDescriptionsItem label="提交人"><UserNameText v-if="inspected.record?.submittedBy" :id="inspected.record.submittedBy" /></NDescriptionsItem>
        <NDescriptionsItem label="逐件原账依据"><HFileList v-if="verificationForm(inspected).registrationEvidence" :value="verificationForm(inspected).registrationEvidence" show-download-btn /><span v-else>尚未关联</span></NDescriptionsItem>
      </NDescriptions>
    </div>
    <template #footer><NSpace justify="end"><NButton @click="inspectVisible = false">关闭</NButton><NButton v-if="inspected" @click="viewOne">到登记账逐件办理</NButton></NSpace></template>
  </NModal>
</template>
<script setup lang="ts">
import { computed, h, ref, watch } from 'vue';
import { useElementSize } from '@vueuse/core';
import { NButton, useMessage, type DataTableColumns } from 'naive-ui';
import dayjs from 'dayjs';
import { collectionApi } from '../api/collection';
import { useAuthStore } from '@/store/modules/auth';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readMigrationVerification, verificationForm, verificationStatuses } from '../utils/migrationVerification';
import type { MigrationVerification, MigrationVerificationContent } from '../utils/migrationVerification';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import CatalogDetail from './CatalogDetail.vue';
import UserNameText from './UserNameText.vue';
const scrollableModalLayout = {
  contentStyle: { minWidth: '0', minHeight: '0', overflowY: 'auto', overflowX: 'hidden', overscrollBehavior: 'contain', scrollbarGutter: 'stable' },
  headerStyle: { flexShrink: 0 },
  footerStyle: { flexShrink: 0, paddingTop: '16px' },
} as const;
const emit = defineEmits<{ changed: []; view: [id: string] }>();
const auth = useAuthStore(), message = useMessage();
const visible = ref(false), inspectVisible = ref(false), inspected = ref<MigrationVerification | null>(null);
const batchBodyRef = ref<HTMLElement | null>(null);
const { width: contentWidth } = useElementSize(batchBodyRef);
const compactTable = computed(() => contentWidth.value < 900);
const batchId = ref<string | null>(null), options = ref<{ label: string; value: string }[]>([]), rows = ref<MigrationVerification[]>([]), selected = ref<string[]>([]);
const error = ref(''), mode = ref<'submit' | 'review'>('submit'), evidence = ref<string | null>(null), reason = ref(''), opinion = ref(''), confirmed = ref(false);
const reasons = ref({ originalDateMissingReason: '', originalRegistrarMissingReason: '', classificationMissingReason: '' });
const missingReasons = [{ key: 'originalDateMissingReason', label: '原登记日期缺项说明' }, { key: 'originalRegistrarMissingReason', label: '原登记人缺项说明' }, { key: 'classificationMissingReason', label: '分类号缺项说明' }] as const;
const session = useCatalogOperation(() => { visible.value = false; inspectVisible.value = false; inspected.value = null; rows.value = []; options.value = []; selected.value = []; batchId.value = null; evidence.value = null; reason.value = ''; opinion.value = ''; confirmed.value = false; error.value = ''; reasons.value = { originalDateMissingReason: '', originalRegistrarMissingReason: '', classificationMissingReason: '' }; });
const rowKey = (r: MigrationVerification) => r.collectionId;
const eligible = (r: MigrationVerification) => mode.value === 'submit' ? r.canEdit : r.canReview;
const canProcess = computed(() => !session.busy.value && !!batchId.value && selected.value.length > 0 && selected.value.length <= 200 && selected.value.every(id => rows.value.some(r => r.collectionId === id && eligible(r))));
const date = (v?: number | null) => v == null ? '原账未载／待考' : dayjs(v).format('YYYY-MM-DD');
const recordName = (r: MigrationVerification) => (r.record?.collectionSnapshot || r.collectionSnapshot)?.formalName || '--';
const recordStatus = (r: MigrationVerification) => r.record ? verificationStatuses[r.record.status] : '待补充材料';
const missingDescription = (r: MigrationVerification) => r.missingFields?.join('、') || '无上述缺项';
const columns = computed<DataTableColumns<MigrationVerification>>(() => {
  const selection: DataTableColumns<MigrationVerification>[number] = { type: 'selection', width: 40, disabled: r => session.busy.value || !eligible(r) };
  const actions: DataTableColumns<MigrationVerification>[number] = { title: '操作', key: 'actions', width: 96, render: r => h(NButton, { size: 'small', disabled: session.busy.value, onClick: () => { inspected.value = r; inspectVisible.value = true; } }, () => '逐件核对') };
  if (compactTable.value) return [
    selection,
    { title: '藏品', key: 'identity', render: r => h('div', { class: 'batch-record' }, [h('strong', r.totalRegNo), h('span', recordName(r))]) },
    { title: '核验资料', key: 'verification', render: r => {
      const content = verificationForm(r);
      return h('div', { class: 'batch-record' }, [
        h('span', recordStatus(r)),
        h('span', `原登记日期：${date(content.originalRegisteredAt)}`),
        h('span', `原登记人：${content.originalRegisteredBy || '原账未载／待考'}`),
        h('span', `待完善：${missingDescription(r)}`),
      ]);
    } },
    actions,
  ];
  return [
    selection,
    { title: '总登记号', key: 'totalRegNo', width: 110 },
    { title: '名称', key: 'name', render: recordName },
    { title: '核验状态', key: 'status', width: 120, render: recordStatus },
    { title: '原登记日期', key: 'date', width: 140, render: r => date(verificationForm(r).originalRegisteredAt) },
    { title: '原登记人', key: 'registrar', width: 110, render: r => verificationForm(r).originalRegisteredBy || '原账未载／待考' },
    { title: '待完善资料', key: 'missing', render: missingDescription },
    actions,
  ];
});
function select(keys: (string | number)[]) { selected.value = keys.map(String).filter(id => rows.value.some(r => r.collectionId === id && eligible(r))); }
watch(mode, () => { selected.value = []; confirmed.value = false; });
watch([selected, evidence, reason, opinion, reasons], () => { confirmed.value = false; }, { deep: true, flush: 'sync' });
watch(visible, v => { if (!v) session.reset(); });
watch(() => [auth.userInfo?.id, auth.identityRevision, auth.identitySyncing], () => session.reset(), { flush: 'sync' });
async function open() {
  session.reset(); if (auth.identitySyncing) return;
  visible.value = true; const ticket = session.begin('load'); if (ticket == null) return;
  try {
    const response: any = await collectionApi.migrationVerificationBatches(); if (!session.current(ticket)) return;
    if (!Array.isArray(response?.data) || response.data.some((r: any) => !r.batchId || !r.sourceSystem || !Number.isSafeInteger(r.total))) throw Error('批次回执不完整');
    options.value = response.data.map((r: any) => ({ value: r.batchId, label: `${r.sourceSystem} · ${r.migratedAt} · ${r.total} 件` }));
    if (!options.value.length) error.value = '当前收藏单位暂无迁入批次';
  } catch (e: any) { if (session.current(ticket)) error.value = e.message || '批次加载失败'; }
  finally { session.finish(ticket); }
}
async function loadBatch() {
  if (!batchId.value || session.busy.value) return;
  selected.value = []; confirmed.value = false; rows.value = []; error.value = ''; inspected.value = null; inspectVisible.value = false;
  const batch = batchId.value, ticket = session.begin('load'); if (ticket == null) return;
  try {
    const response: any = await collectionApi.migrationVerificationBatch(batch); if (!session.current(ticket)) return;
    if (!Array.isArray(response?.data)) throw Error('批次材料回执不完整');
    rows.value = response.data.map((r: any) => { const value = readMigrationVerification({ data: r }, r.collectionId); if (value.source.batchId !== batch) throw Error('材料与所选批次不一致'); return value; });
  } catch (e: any) { if (session.current(ticket)) error.value = e.message || '材料加载失败'; }
  finally { session.finish(ticket); }
}
function payload(r: MigrationVerification): MigrationVerificationContent {
  const c = verificationForm(r);
  return { ...c, registrationEvidence: c.registrationEvidence || evidence.value, reason: reason.value.trim(), matchesOriginalLedger: true,
    originalDateMissingReason: c.originalRegisteredAt == null ? c.originalDateMissingReason || reasons.value.originalDateMissingReason.trim() : null,
    originalRegistrarMissingReason: !c.originalRegisteredBy.trim() ? c.originalRegistrarMissingReason || reasons.value.originalRegistrarMissingReason.trim() : null,
    classificationMissingReason: r.missingFields?.includes('分类号') ? c.classificationMissingReason || reasons.value.classificationMissingReason.trim() : null };
}
async function process(approved: boolean) {
  if (!canProcess.value || (approved && !confirmed.value) || (mode.value === 'review' ? !opinion.value.trim() : !reason.value.trim())) return;
  const reviewing = mode.value === 'review', targets = rows.value.filter(r => selected.value.includes(r.collectionId));
  const request = { batchId: batchId.value, items: targets.map(r => ({ collectionId: r.collectionId, expectedStateHash: r.stateHash, ...(reviewing ? {} : { content: payload(r) }) })), ...(reviewing ? { review: { approved, opinion: opinion.value.trim() } } : {}) };
  const ticket = session.begin('write'); if (ticket == null) return;
  try {
    const response: any = await collectionApi.processMigrationVerificationBatch(request); if (!session.current(ticket)) return;
    if (!Array.isArray(response?.data) || response.data.length !== targets.length || new Set(response.data.map((r: any) => r.collectionId)).size !== targets.length) throw Error('办理回执不完整，请重新加载核对结果');
    const expected = reviewing ? (approved ? 'APPROVED' : 'REJECTED') : 'SUBMITTED';
    const updated = response.data.map((r: any) => { if (!targets.some(t => t.collectionId === r.collectionId)) throw Error('办理回执藏品不匹配'); const value = readMigrationVerification({ data: r }, r.collectionId); if (value.record?.status !== expected) throw Error('办理结果与操作不一致，请重新加载'); return value; });
    rows.value = rows.value.map(r => updated.find((u: MigrationVerification) => u.collectionId === r.collectionId) || r);
    selected.value = []; confirmed.value = false; error.value = ''; emit('changed'); message.success(`已办理 ${updated.length} 件`);
  } catch (e: any) { if (session.current(ticket)) { error.value = e.message || '办理失败，请核对后重试'; confirmed.value = false; } }
  finally { session.finish(ticket); }
}
function viewOne() { const id = inspected.value?.collectionId; if (id) { session.reset(); emit('view', id); } }
defineExpose({ open });
</script>
<style scoped>
.batch-body { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 16px; overflow-wrap: anywhere; }
.batch-body > *, .reason-grid > * { min-width: 0; max-width: 100%; }
.batch-body :deep(.n-data-table-th), .batch-body :deep(.n-data-table-td) { white-space: normal; overflow-wrap: anywhere; }
.batch-body :deep(.batch-record) { display: grid; gap: 4px; min-width: 0; }
.reason-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 700px) { .reason-grid { grid-template-columns: 1fr; } }
</style>
