<template>
  <NCard title="系统自动监测">
    <NText v-if="status?.legacy">既有已结束记录未启动新增自动核验，请按原人工核验和处置档案查证。</NText>
    <NText>已核验至：{{ time(status?.checkedThrough) }}；最近运行：{{ time(status?.lastAttemptAt) }}</NText>
    <NAlert v-if="status?.lastError || status?.overdue" type="warning" :show-icon="false">
      {{ status.lastError || '自动监测尚未覆盖应核验区间，请核实调度和采样来源。' }}
    </NAlert>
    <NAlert v-if="status?.problemCount" type="warning" :show-icon="false">
      已保存 {{ status.problemCount }} 份含超限或证据缺口的自动原件，{{ status.unclearedCount }} 份尚无处置核对记录。
      已有关联解限仍须核实当前处置依据；后续正常采样不删除历史异常。
    </NAlert>
    <NText depth="3">系统核验已知采样证据，不代表人员已检查、已停止实物使用或已完成处置。</NText>
    <NSpace>
      <NButton @click="open">查看自动核验原件（{{ status?.total || 0 }}）</NButton>
      <NButton v-if="canOperate && status?.problemCount" @click="$emit('incident')">办理使用期间异常</NButton>
      <NButton v-if="canOperate && status?.problemCount && clearanceAvailable" @click="$emit('clear')">核对自动监测异常处置</NButton>
    </NSpace>
  </NCard>
  <NModal v-model:show="show" preset="card" title="自动核验原件" style="width: 950px; max-width: 95vw">
    <template v-if="show">
      <NAlert v-if="error" type="error">{{ error }} <NButton @click="load">重试读取</NButton></NAlert>
      <NSpin :show="loading">
        <NCard v-for="row in rows" :key="row.id" :title="`系统核验 · ${time(row.observedAt)}`">
          <NText>{{ row.needsHandling ? '发现问题，须据实核实处置' : '本区间已有有效采样证据' }}</NText>
          <CollectionEvidenceReadView :fields="fragileMonitoringEvidenceFields" :data="row.evidence" />
          <FragileMonitoringSamples :archive-files="archiveFiles"
            :collection-id="collectionId"
            :org-id="orgId"
            :monitor-id="row.id"
            :automatic="true"
            :sample-count="
              (row.evidence.metrics || []).reduce((n: number, m: any) => n + Number(m.sampleCount || 0), 0)
            "
          />
          <NCard
            v-for="v in row.clearances || (row.clearance ? [row.clearance] : [])"
            :key="v.version"
            :title="`第 ${v.version} 版处置核对 · ${v.version === row.clearance?.version ? '当前版本' : '历史版本'}`"
          >
            <CollectionEvidenceReadView :fields="careFields.clear" :data="v.record"
              :archive-files="archiveFiles?.[row.id]?.[`AUTO_CLEARED_${v.version}`]" :original-url="(fileId: string) => api.originalUrl(collectionId, orgId, row.id, `AUTO_CLEARED_${v.version}`, fileId)" />
            <NButton @click="$emit('openIncident', v.record.incidentId)">查看本版关联事故</NButton>
          </NCard>
        </NCard>
        <NText v-if="!loading && !error && !rows.length">尚无自动核验原件。</NText>
      </NSpin>
      <NPagination :page="page" :page-count="Math.max(1, Math.ceil(total / 20))" @update:page="changePage" />
    </template>
  </NModal>
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { onBeforeUnmount, ref, watch } from 'vue';
import { formatDateTime } from '@/utils';
import { fragilityApi as api } from '../api/fragility';
import { fragileMonitoringEvidenceFields } from '../utils/fragilityFields';
import { careFields } from '../utils/careFields';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import FragileMonitoringSamples from './FragileMonitoringSamples.vue';
const props = defineProps<{
  collectionId: string;
  orgId: string;
  useId: string;
  status?: Record<string, any>;
  clearanceAvailable: boolean;
  canOperate?: boolean;
  archiveFiles?:Record<string,Record<string,CollectionReadonlyFile[]>>;
}>();
defineEmits(['incident', 'clear', 'openIncident']);
const show = ref(false),
  loading = ref(false),
  page = ref(1),
  total = ref(0),
  rows = ref<any[]>([]),
  error = ref('');
let request = 0;
const time = (v: any) => (v == null ? '尚未形成记录' : formatDateTime(v));
async function load() {
  const token = ++request;
  loading.value = true;
  rows.value = [];
  error.value = '';
  try {
    const r: any = await api.automaticChecks(props.collectionId, props.useId, page.value);
    if (token !== request) return;
    rows.value = r.data.list;
    total.value = Number(r.data.total);
  } catch (e) {
    if (token === request) error.value = e instanceof Error ? e.message : '自动核验原件读取失败';
  } finally {
    if (token === request) loading.value = false;
  }
}
function open() {
  show.value = true;
  page.value = 1;
  total.value = Number(props.status?.total || 0);
  void load();
}
function changePage(value: number) {
  page.value = value;
  void load();
}
function invalidate() {
  ++request;
  rows.value = [];
  loading.value = false;
}
watch([() => props.collectionId, () => props.orgId, () => props.useId], () => {
  invalidate();
  show.value = false;
});
watch(show, (v) => {
  if (!v) invalidate();
});
onBeforeUnmount(invalidate);
</script>
