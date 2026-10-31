<template>
  <NButton :disabled="!sampleCount" @click="open">查看原始采样（{{ sampleCount }}）</NButton>
  <NModal v-model:show="show" preset="card" title="区间核验时冻结的原始采样" style="width: 850px; max-width: 95vw">
    <template v-if="show">
      <NAlert type="info" :show-icon="false"
        >以下是当时冻结的证据，不代表当前设备状态。接收或原件可用时间晚于采样时，不回填当时的监测缺口。</NAlert
      >
      <NAlert v-if="error" type="error" :show-icon="false"
        >{{ error }} <NButton @click="load">重试读取原件</NButton></NAlert
      >
      <NSpin :show="loading"
        ><CollectionEvidenceReadView v-for="(row, index) in rows" :key="index" :fields="fields" :data="row"
          :archive-files="archiveFiles?.[row.originalRecordId]?.[row.originalPhase]" :original-url="row.originalRecordId && row.originalPhase
            ? (fileId: string) => api.originalUrl(collectionId, orgId, row.originalRecordId, row.originalPhase, fileId)
            : undefined"
      /></NSpin>
      <NPagination :page="page" :page-count="Math.max(1, Math.ceil(total / 20))" @update:page="changePage" />
    </template>
  </NModal>
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { onBeforeUnmount, ref, watch } from 'vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import type { EvidenceField } from '../utils/evidenceFields';
import { fragilityApi as api } from '../api/fragility';
const props = defineProps<{ collectionId: string; orgId: string; monitorId: string; sampleCount: number; automatic?: boolean; archiveFiles?:Record<string,Record<string,CollectionReadonlyFile[]>> }>();
const show = ref(false),
  page = ref(1),
  total = ref(0),
  rows = ref<any[]>([]),
  loading = ref(false),
  error = ref('');
let request = 0;
async function load() {
  const token = ++request;
  loading.value = true;
  rows.value = [];
  error.value = '';
  try {
    const response: any = await (props.automatic ? api.automaticSamples : api.samples)(
      props.collectionId,
      props.monitorId,
      page.value,
    );
    if (token !== request) return;
    rows.value = response.data.list;
    total.value = Number(response.data.total);
  } catch (e) {
    if (token === request) error.value = e instanceof Error ? e.message : '原始采样暂时无法读取';
  } finally {
    if (token === request) loading.value = false;
  }
}
function open() {
  show.value = true;
  page.value = 1;
  total.value = props.sampleCount;
  void load();
}
function changePage(value: number) {
  page.value = value;
  void load();
}
watch([() => props.collectionId, () => props.monitorId, () => props.automatic], () => {
  ++request;
  show.value = false;
  rows.value = [];
  loading.value = false;
});
watch(show, (value) => {
  if (!value) {
    ++request;
    rows.value = [];
    loading.value = false;
  }
});
onBeforeUnmount(() => {
  ++request;
});
const fields: EvidenceField[] = [
  { key: 'metricName', label: '指标' },
  { key: 'measuredAt', label: '采样时间', type: 'datetime' },
  { key: 'receivedAt', label: '接收或原件可用时间', type: 'datetime' },
  { key: 'value', label: '原始测值' },
  { key: 'unit', label: '单位' },
  { key: 'validUntil', label: '当时依据可用截止', type: 'datetime' },
  { key: 'reasons', label: '证据问题' },
  { key: 'evidence.availabilityBasis', label: '现场原件可用时间依据' },
  { key: 'evidence.signedOnSite.signedFileIds', label: '现场测量签署原件', type: 'file' },
  { key: 'evidence.fact.id', label: '联网采集事实编号' },
];
</script>
