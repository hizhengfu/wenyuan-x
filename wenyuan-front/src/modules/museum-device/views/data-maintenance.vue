<template>
  <div class="maintenance-page">
    <NAlert v-if="!canManage" type="warning">需要采集管理权限才能进行补录和清洗。</NAlert>
    <template v-else>
      <NCard size="small" :bordered="false">
        <NForm inline :show-feedback="false" label-placement="left">
          <NFormItem label="所属场馆">
            <NSelect
              v-model:value="orgId"
              :options="venueOptions"
              :disabled="busy"
              filterable
              placeholder="请选择场馆"
              style="width: 280px"
            />
          </NFormItem>
        </NForm>
      </NCard>
      <NAlert v-if="error" type="error" closable @close="error = ''">{{ error }}</NAlert>
      <NTabs
        v-model:value="tab"
        type="line"
        class="maintenance-tabs"
        pane-wrapper-style="display:flex;flex:1;min-height:0;overflow:hidden"
        pane-style="display:flex;flex:1;min-height:0;flex-direction:column;overflow:auto;gap:12px"
      >
        <NTabPane name="import" tab="模板补录" display-directive="show:lazy">
          <MuseumBackfillImportPanel
            v-if="orgId"
            :org-id="orgId"
            @busy="importBusy = $event"
            @completed="historyTable?.handleRefresh?.()"
          />
          <NEmpty v-else description="请先选择场馆，再下载补录模板或查看后台任务" />
        </NTabPane>
        <NTabPane name="clean" tab="重复数据清洗" display-directive="show:lazy">
          <NAlert type="info" :bordered="false">
            按设备、来源、指标和采样时间识别重复，仅清理值与质量一致的遥测副本。最新值、预警引用记录和冲突数据保留；清理后同步重算日统计。
          </NAlert>
          <NCard size="small" :bordered="false">
            <NSpace vertical :size="14">
              <NForm inline :show-feedback="false" label-placement="left">
                <NFormItem label="设备">
                  <NSelect
                    v-model:value="deviceId"
                    :options="deviceOptions"
                    filterable
                    :disabled="!orgId || busy"
                    placeholder="请选择设备"
                    style="width: 270px"
                  />
                </NFormItem>
                <NFormItem label="指标">
                  <NSelect
                    v-model:value="metricCode"
                    :options="metricOptions"
                    filterable
                    :disabled="busy"
                    placeholder="请选择指标"
                    style="width: 240px"
                  />
                </NFormItem>
                <NFormItem label="采样时间">
                  <NDatePicker v-model:value="range" type="datetimerange" clearable :disabled="busy" />
                </NFormItem>
              </NForm>
              <NText depth="3">范围包含开始时间，不含结束时间。每次最多 31 天、10000 条，请按需缩小范围。</NText>
              <NInput v-model:value="cleanReason" :disabled="busy" maxlength="500" placeholder="填写清洗原因" />
              <NSpace>
                <NButton
                  :disabled="!orgId || !deviceId || !metricCode || !range || busy"
                  :loading="busyAction === 'previewClean'"
                  @click="previewClean"
                  >预览重复数据</NButton
                >
                <NButton
                  type="error"
                  :disabled="!duplicatePreview?.removable || !cleanReason.trim() || busy"
                  :loading="busyAction === 'clean'"
                  @click="confirmClean"
                  >清理预览中的重复数据</NButton
                >
              </NSpace>
            </NSpace>
          </NCard>
          <template v-if="duplicatePreview">
            <NAlert :type="duplicatePreview.conflicts ? 'warning' : 'info'" :bordered="false">
              扫描 {{ duplicatePreview.scanned }} 条，可清理 {{ duplicatePreview.removable }} 条，受引用保护
              {{ duplicatePreview.protected }} 条，冲突 {{ duplicatePreview.conflicts }} 组。
            </NAlert>
            <NDataTable
              :columns="duplicateColumns"
              :data="duplicatePreview.groups"
              :pagination="{ pageSize: 20 }"
              :scroll-x="1100"
              size="small"
            />
          </template>
        </NTabPane>
        <NTabPane name="history" tab="操作记录" display-directive="show:lazy">
          <HPageDataTable
            v-if="orgId"
            ref="historyTable"
            :key="orgId"
            :columns="historyColumns"
            :fetch-data="fetchOperations"
            :is-add="false"
            :is-edit="false"
            :is-copy="false"
            :is-delete="false"
            :is-export="false"
            :show-operate="false"
            :scroll-x="1200"
            row-key-field="id"
          />
          <NEmpty v-else description="请先选择场馆" />
        </NTabPane>
      </NTabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui';
import MuseumBackfillImportPanel from '../components/MuseumBackfillImportPanel.vue';
import { useAuthStore } from '@/store';
import UserText from '@/modules/sys/components/UserText.vue';
import { formatDateTime } from '@/utils';
import { gatewayApi } from '../api/gateway';
import { ingestApi } from '../api/ingest';
import {
  maintenanceApi,
  type DuplicateGroup,
  type DuplicatePreview,
  type DuplicateQuery,
  type MaintenanceOperation,
} from '../api/dataMaintenance';
import type { MuseumVenueVo, StandardMetricDefinition } from '../typings';

const message = useMessage();
const dialog = useDialog();
const auth = useAuthStore();
const canManage = computed(() => auth.hasPermission(['ADMIN', 'MUSEUM_DEVICE_ADMIN', 'MUSEUM_DEVICE_MANAGE']));
const tab = ref('import');
const orgId = ref<string | null>(null);
const venueOptions = ref<Array<{ label: string; value: string }>>([]);
const deviceOptions = ref<Array<{ label: string; value: string }>>([]);
const metricOptions = ref<Array<{ label: string; value: string }>>([]);
const cleanReason = ref('');
const duplicatePreview = ref<DuplicatePreview | null>(null);
const previewQuery = ref<DuplicateQuery | null>(null);
const deviceId = ref<string | null>(null);
const metricCode = ref<string | null>(null);
const range = ref<[number, number] | null>([Date.now() - 86400000, Date.now()]);
const busyAction = ref('');
const importBusy = ref(false);
const busy = computed(() => !!busyAction.value || importBusy.value);
const error = ref('');
const historyTable = ref<{ handleRefresh?: () => void } | null>(null);
const time = (value: number | null) => (value == null ? '--' : formatDateTime(value));
const duplicateColumns: DataTableColumns<DuplicateGroup> = [
  { title: '指标', key: 'metricCode', width: 150 },
  { title: '来源', key: 'sourceCode', width: 120, render: (row) => row.sourceCode || '设备采集' },
  { title: '采样时间', key: 'occurredAt', width: 175, render: (row) => time(row.occurredAt) },
  { title: '条数', key: 'count', width: 65 },
  { title: '可清理', key: 'removable', width: 80 },
  { title: '受保护', key: 'protected', width: 80 },
  { title: '判定', key: 'conflict', width: 130, render: (row) => (row.conflict ? '冲突，全部保留' : '相同采样') },
  { title: '值与质量', key: 'value', minWidth: 250 },
];
const historyColumns: DataTableColumns<MaintenanceOperation> = [
  { title: '操作时间', key: 'createdAt', width: 180, render: (row) => time(row.createdAt) },
  {
    title: '操作',
    key: 'operation',
    width: 110,
    render: (row) => (row.operation === 'IMPORT' ? '模板补录' : '重复清洗'),
  },
  { title: '新增 / 删除', key: 'affectedCount', width: 100 },
  { title: '跳过 / 保护', key: 'skippedCount', width: 100 },
  { title: '原因', key: 'reason', minWidth: 240 },
  { title: '文件', key: 'fileName', width: 240 },
  { title: '操作用户', key: 'operatorId', width: 170, render: (row) => h(UserText, { value: row.operatorId }) },
  { title: '操作编号', key: 'id', width: 190 },
];

function fail(cause: unknown) {
  const e = cause as { response?: { data?: { message?: string } }; message?: string };
  error.value = e.response?.data?.message || e.message || '操作失败，请重试';
}
async function run(action: string, callback: () => Promise<void>) {
  if (busy.value) return;
  busyAction.value = action;
  error.value = '';
  try {
    await callback();
  } catch (cause) {
    fail(cause);
  } finally {
    busyAction.value = '';
  }
}
function previewClean() {
  duplicatePreview.value = null;
  previewQuery.value = null;
  if (!orgId.value || !deviceId.value || !metricCode.value || !range.value) return;
  const query: DuplicateQuery = {
    orgId: orgId.value,
    deviceId: deviceId.value,
    from: range.value[0],
    to: range.value[1],
    metricCode: metricCode.value,
  };
  if (query.to <= query.from || query.to - query.from > 31 * 86400000) {
    error.value = '请选择不超过 31 天的有效时间范围';
    return;
  }
  void run('previewClean', async () => {
    duplicatePreview.value = (await maintenanceApi.previewDuplicates(query)).data;
    previewQuery.value = query;
  });
}
function confirmClean() {
  const preview = duplicatePreview.value;
  const query = previewQuery.value;
  if (!preview?.removable || !query) return;
  dialog.warning({
    title: '确认清理重复数据',
    content: `本次将删除 ${preview.removable} 条重复副本，并重算该设备受影响日期的统计。冲突数据与受引用保护的记录会保留。`,
    positiveText: '确认清理',
    negativeText: '取消',
    onPositiveClick: () =>
      run('clean', async () => {
        if (duplicatePreview.value !== preview) throw new Error('预览已失效，请重新检查');
        const result = (await maintenanceApi.cleanDuplicates(query, preview.previewHash, cleanReason.value)).data;
        duplicatePreview.value = null;
        previewQuery.value = null;
        message.success(`已清理 ${result.affected} 条重复数据。操作编号：${result.operationId}`);
        historyTable.value?.handleRefresh?.();
      }),
  });
}
function fetchOperations(params: Record<string, unknown>) {
  return maintenanceApi.operations({ ...params, orgId: orgId.value });
}
watch([deviceId, metricCode, range], () => {
  duplicatePreview.value = null;
  previewQuery.value = null;
});
watch(orgId, async (selected) => {
  duplicatePreview.value = null;
  previewQuery.value = null;
  deviceId.value = null;
  deviceOptions.value = [];
  if (!selected) return;
  try {
    const response = await ingestApi.recordFilterOptions({ orgId: selected });
    if (orgId.value !== selected) return;
    const devices = response.data?.devices || [];
    deviceOptions.value = devices.map((item: { id: string; name: string; code: string }) => ({
      label: `${item.name}（${item.code}）`,
      value: item.id,
    }));
  } catch (cause) {
    if (orgId.value === selected) fail(cause);
  }
});
onMounted(async () => {
  if (!canManage.value) return;
  const results = await Promise.allSettled([gatewayApi.venues(), ingestApi.standardMetrics(true)]);
  if (results[0].status === 'fulfilled') {
    const venues = results[0].value.data as MuseumVenueVo[];
    venueOptions.value = venues.map((item) => ({ label: item.name, value: item.id }));
    if (venues.length === 1) orgId.value = venues[0].id;
  } else fail(results[0].reason);
  if (results[1].status === 'fulfilled') {
    metricOptions.value = (results[1].value.data as StandardMetricDefinition[])
      .filter((item) => item.recordType === 'TELEMETRY')
      .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.code }));
  } else fail(results[1].reason);
});
</script>

<style scoped>
.maintenance-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
}
.maintenance-tabs {
  flex: 1;
  min-height: 0;
}
</style>
