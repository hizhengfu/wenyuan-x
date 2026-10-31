<template>
  <div>
    <div style="display: flex; justify-content: flex-end; gap: 8px; margin-bottom: 8px">
      <NButton size="small" :loading="loading" @click="load">重新汇总</NButton>
      <NButton size="small" type="primary" :disabled="loading || !html" @click="onPrint">打印</NButton>
    </div>
    <NAlert v-if="error" type="error" style="margin-bottom: 8px">{{ error }}</NAlert>
    <div class="restoration-summary-report" v-html="html" />
  </div>
</template>

<script setup lang="ts">
import { errorText } from '../utils/restorationError';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { NAlert, NButton } from 'naive-ui';
import { restorationProcessApi } from '../api/process';
import type {
  RestorationAcceptanceVo, RestorationArchiveVo, RestorationContractVo, RestorationProcessVo, RestorationVo,
} from '../typings';
import { formatDateTime, formatDateValue } from '@/utils/datetime';
import { useDictLabel } from '@/utils/dict';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { buildSummaryReportHtml, projectStateText, summaryReportStyle, summaryReportTitle, type ProcessSummary } from '../utils/restorationReport';

const props = defineProps<{
  project: RestorationVo;
  processes: RestorationProcessVo[];
  archive?: RestorationArchiveVo | null;
  acceptance?: RestorationAcceptanceVo | null;
  contract?: RestorationContractVo | null;
}>();
const summary = ref<ProcessSummary | null>(null);
const loading = ref(false), error = ref('');
const { label: stateLabel } = useDictLabel('col_process_state', computed(() => props.project.state) as any);
let token = 0, alive = true;
onBeforeUnmount(() => { alive = false; token++; });

async function load() {
  const id = props.project.id, current = ++token;
  summary.value = null; error.value = '';
  if (!id) return;
  loading.value = true;
  try {
    const response: any = await restorationProcessApi.summarize(id);
    if (!alive || current !== token) return;
    const data = response?.data;
    if (response?.status !== undefined && response.status !== 200) throw Error('工序汇总读取失败');
    if (data != null && (typeof data !== 'object' || Array.isArray(data))) throw Error('工序汇总格式不正确');
    summary.value = data ?? null;
  } catch (e) {
    if (alive && current === token) error.value = errorText(e, '工序汇总读取失败');
  } finally {
    if (alive && current === token) loading.value = false;
  }
}
watch(() => props.project.id, () => { void load(); }, { immediate: true });

const html = computed(() => props.project.id ? buildSummaryReportHtml({
  project: props.project,
  processes: props.processes,
  summary: summary.value,
  archive: props.archive,
  acceptance: props.acceptance,
  contract: props.contract,
  formatDate: formatDateValue,
  formatDateTime: (v) => formatDateTime(v, 'YYYY-MM-DD HH:mm'),
  stateText: projectStateText(props.project, stateLabel.value),
}) : '');

function onPrint() {
  if (!html.value) return;
  // 标题传原始文本，由 PrintUtils.htmlPrint → printDocumentTitle 统一只转义一次；此处不要自行转义。
  htmlPrint(html.value, summaryReportTitle(props.project), summaryReportStyle);
}
</script>

<style scoped>
.restoration-summary-report :deep(.report) { width: 100%; border-collapse: collapse; margin: 6px 0 12px; font-size: 12px; }
.restoration-summary-report :deep(.report th),
.restoration-summary-report :deep(.report td) { border: 1px solid var(--n-border-color, #ddd); padding: 4px 6px; vertical-align: top; }
.restoration-summary-report :deep(.report th) { font-weight: normal; white-space: nowrap; }
.restoration-summary-report :deep(.pre) { white-space: pre-wrap; }
.restoration-summary-report :deep(.text-center) { text-align: center; }
.restoration-summary-report :deep(h3) { font-size: 14px; margin: 12px 0 4px; }
</style>
