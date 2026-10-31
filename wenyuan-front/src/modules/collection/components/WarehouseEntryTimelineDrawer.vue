<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="离库与原件操作时间核查">
    <NSpace vertical :size="16">
      <NAlert type="info"
        >时间冲突是待核实线索。请对照进出登记、日记和原件操作签署记录；核查材料登记不会覆盖原记录，也不会自动标记为已解决。</NAlert
      >
      <NButton :disabled="busy" :loading="loading" @click="reload">刷新核对</NButton>
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <template v-if="data && !loading && !error">
        <NAlert v-for="item in data.evidenceErrors" :key="item" type="error">{{ item }}</NAlert>
        <NAlert v-if="!data.conflicts.length && !data.evidenceErrors.length" type="info"
          >当前已登记原件中未发现关联时间冲突；仍须核对实际原件和现场事实。</NAlert
        >
        <NCard v-for="item in data.conflicts" :key="item.archiveId" size="small" title="待核实时间冲突">
          <NText
            >{{ labels[item.businessType] || '原件操作' }} ·
            {{ formatLocalDateValue(item.startedAt, 'YYYY-MM-DD HH:mm:ss') }} · 人员编号
            {{ item.operatorUserId }}</NText
          >
          <NText depth="3">操作原件编号：{{ item.archiveId }}；关联日记编号：{{ item.diaryId }}</NText>
        </NCard>
        <NButton v-if="data.conflicts.length && !data.evidenceErrors.length" :disabled="busy" @click="begin"
          >登记实际核查材料</NButton
        >
        <NForm v-if="editing" label-width="190">
          <WarehouseRecordFields v-model="form" :fields="fields" />
          <NButton type="primary" :loading="busy" @click="save">核实后保存核查材料</NButton>
        </NForm>
        <NCard v-for="review in data.reviews" :key="review.id" title="已登记核查材料（原冲突保留）" size="small">
          <NAlert v-if="review.baselineCurrent === false" type="warning"
            >核查后关联原件或时间已变化，请对照新材料重新核实；本次旧核查原件继续保留。</NAlert
          >
          <CollectionEvidenceReadView :fields="fields" :data="snapshot(review)" :archive-files="review.files" :original-url="originalUrl(review)" />
          <HFileList :files="review.files" :original-url="(fileId: string) => originalUrl(review)(fileId)" :revision="[review?.id, review?.digest]" show-download-btn />
        </NCard>
      </template>
    </NSpace>
  </HPageForm>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { warehouseEntryApi } from '../api/warehouseEntry';
import type { EvidenceField } from '../utils/evidenceFields';
import { warehouseFieldError, warehouseEvidencePayload } from '../utils/warehouseEvidenceFields';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
const show = ref(false),
  loading = ref(false),
  busy = ref(false),
  editing = ref(false),
  error = ref('');
const entryId = ref(''),
  data = ref<any>(),
  form = ref<any>({});
const message = useMessage();
let sequence = 0;
const labels: Record<string, string> = {
  COLLECTION_CARE: '日常养护',
  REPRODUCTION_COMPLIANCE_2026: '复制拓印',
  COLLECTION_DIGITAL_ACQUISITION: '数字采集',
  COLLECTION_SAMPLING: '有损取样',
};
const fields: EvidenceField[] = [
  { key: 'reviewedAt', label: '实际核查时间', type: 'datetime', required: true },
  {
    key: 'comparedOriginals',
    label: '已逐项对照原件',
    type: 'select',
    options: [
      { label: '已实际对照', value: 'YES' },
      { label: '尚未对照', value: 'NO' },
    ],
    required: true,
  },
  { key: 'statement', label: '实际核查情况', type: 'textarea', required: true },
  { key: 'followUp', label: '后续核实与处置', type: 'textarea', required: true },
  { key: 'fileIds', label: '实际签署核查材料', type: 'file', required: true },
];
function snapshot(review: any) {
  try {
    const content = JSON.parse(review.snapshot).content || {};
    return { ...content, comparedOriginals: content.comparedOriginals === true ? 'YES' : 'NO' };
  } catch {
    return {};
  }
}
const originalUrl = (review: any) => (fileId: string) =>
  review.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/warehouse-entry/${entryId.value}/timeline/review/${review.id}/original/${fileId}`
    : '';
async function open(id: string) {
  entryId.value = id;
  show.value = true;
  await reload();
}
async function reload() {
  const current = ++sequence,
    id = entryId.value;
  loading.value = true;
  error.value = '';
  data.value = undefined;
  editing.value = false;
  form.value = {};
  try {
    const response = await warehouseEntryApi.timeline(id);
    if (current === sequence) data.value = (response as any).data;
  } catch {
    if (current === sequence) error.value = '时间核查未完成，请刷新重试；不能据此认为没有冲突。';
  } finally {
    if (current === sequence) loading.value = false;
  }
}
function begin() {
  if (!data.value || data.value.evidenceErrors.length || !data.value.conflicts.length) return;
  form.value = { requestKey: crypto.randomUUID(), baseline: data.value.baseline };
  editing.value = true;
}
async function save() {
  const invalid = warehouseFieldError(fields, form.value);
  if (invalid || form.value.comparedOriginals !== 'YES') {
    message.warning(invalid || '请先实际对照原件');
    return;
  }
  const id = entryId.value,
    current = sequence;
  busy.value = true;
  try {
    await warehouseEntryApi.reviewTimeline(id, {
      ...warehouseEvidencePayload(form.value),
      comparedOriginals: form.value.comparedOriginals === 'YES',
    });
    if (current === sequence) {
      message.success('核查材料已登记，原冲突继续保留');
      await reload();
    }
  } finally {
    busy.value = false;
  }
}
defineExpose({ open });
</script>
