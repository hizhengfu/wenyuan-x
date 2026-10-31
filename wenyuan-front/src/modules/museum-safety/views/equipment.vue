<template>
  <SafetyPageGuard>
    <div class="equipment-page">
      <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
      <HPageDataTable
        ref="tableRef"
        title="安防消防与防雷设备"
        class="safety-page-table"
        :columns="columns"
        :fetch-data="fetchProfiles"
        :params="tableQuery"
        :scroll-x="1480"
        row-key-field="id"
        :is-add="false"
        :is-edit="false"
        :is-copy="false"
        :is-delete="false"
        :is-export="false"
        :show-operate="false"
      >
        <template #queryForm>
          <NFormItem label="所属场馆">
            <VenueSelect
              v-model:value="orgId"
              clearable
              placeholder="全部场馆"
              style="width: 240px"
              @update:value="venueChanged"
            />
          </NFormItem>
          <NFormItem label="设备类别">
            <NSelect
              v-model:value="equipmentClass"
              :options="classOptions"
              clearable
              placeholder="全部类别"
              style="width: 150px"
            />
          </NFormItem>
        </template>
        <template #toolbar-extra>
          <NSpace>
            <NButton @click="() => openSpatial()">空间定位</NButton>
            <NButton :loading="exporting" @click="exportProfiles">导出 Excel</NButton>
            <NButton v-if="canManage" type="primary" :disabled="!orgId" @click="openCreate">登记设备档案</NButton>
          </NSpace>
        </template>
      </HPageDataTable>

      <HPageForm
        v-model:show="editVisible"
        :title="editing ? (editing.profiled ? '编辑安全防护设备档案' : '完善安全防护设备档案') : '登记安全防护设备档案'"
        :model="form"
        :auto-close="false"
        :handle-submit="save"
        label-placement="left"
        label-width="110"
        submit-text="保存"
      >
        <NAlert v-if="error" type="error" closable class="state-alert" @close="error = ''">{{ error }}</NAlert>
        <NFormItem label="所属场馆" required
          ><VenueSelect
            v-model:value="form.orgId"
            placeholder="请选择场馆"
            :disabled="!!editing"
            @update:value="loadCatalog"
        /></NFormItem>
        <NFormItem label="采集设备" required
          ><NSelect
            v-model:value="form.deviceId"
            :options="deviceOptions"
            filterable
            :disabled="!!editing"
            placeholder="选择采集系统中已登记的设备"
        /></NFormItem>
        <NFormItem v-if="form.deviceId" label="采集信息">
          <div class="source-panel">
            <div class="source-panel__header">
              <span>以下信息由博物馆采集系统统一维护，本页仅展示。</span>
              <NButton text type="primary" @click="openDeviceManagement">前往采集系统维护</NButton>
            </div>
            <div class="source-grid">
              <div v-for="item in sourceInfoRows" :key="item.label" class="source-item">
                <span class="source-item__label">{{ item.label }}</span>
                <span class="source-item__value">{{ item.value }}</span>
              </div>
            </div>
            <div v-if="sourceIdentifierRows.length" class="source-property-group">
              <div class="source-property-group__title">设备标识</div>
              <div class="source-property-list">
                <div v-for="item in sourceIdentifierRows" :key="item.key" class="source-property-row">
                  <span>{{ item.label }}</span
                  ><span>{{ item.value }}</span>
                </div>
              </div>
            </div>
            <div v-if="sourceTechnicalRows.length" class="source-property-group">
              <div class="source-property-group__title">技术参数</div>
              <div class="source-property-list">
                <div v-for="item in sourceTechnicalRows" :key="item.key" class="source-property-row">
                  <span>{{ item.label }}</span
                  ><span>{{ item.value }}</span>
                </div>
              </div>
            </div>
          </div>
        </NFormItem>
        <NFormItem label="设备类别"><NSelect v-model:value="form.equipmentClass" :options="classOptions" /></NFormItem>
        <NFormItem label="安装时间"
          ><NDatePicker v-model:value="form.installedAt" type="datetime" clearable /></NFormItem
        ><NFormItem label="投运时间"
          ><NDatePicker v-model:value="form.commissionedAt" type="datetime" clearable
        /></NFormItem>
        <NFormItem label="负责人" required
          ><UserSelect v-model:value="form.ownerUserId" clearable placeholder="请选择负责人" /></NFormItem
        ><NFormItem label="资产状态"
          ><NSelect v-model:value="form.assetStatus" :options="assetStatusOptions"
        /></NFormItem>
        <NFormItem label="标准依据"
          ><NInput v-model:value="basisText" placeholder="编码|名称|条款，多个用分号分隔"
        /></NFormItem>
      </HPageForm>
    </div>
  </SafetyPageGuard>
</template>

<script setup lang="ts">
import { computed, h, reactive, ref } from 'vue';
import * as XLSX from 'xlsx';
import { useRoute, useRouter } from 'vue-router';
import {
  NAlert,
  NButton,
  NDatePicker,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  NTag,
  type DataTableColumns,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { useAuthStore } from '@/store/modules/auth';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { ingestApi } from '../../museum-device/api/ingest';
import VenueSelect from '../../museum-device/components/VenueSelect.vue';
import type { RecordFilterOptionsVo } from '../../museum-device/typings';
import { safetyApi, unwrapResponse } from '../api';
import type { SafetyAssetStatus, SafetyComplianceBasis, SafetyDeviceProfileVo, SafetyEquipmentClass } from '../typings';
import SafetyPageGuard from '../components/SafetyPageGuard.vue';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const authStore = useAuthStore();
const canManage = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_ASSET'));
const exporting = ref(false);
const tableRef = ref<any>(null);
const error = ref('');
const orgId = ref<string | null>(null);
const routeClass = route.query.equipmentClass as SafetyEquipmentClass | undefined;
const equipmentClass = ref<SafetyEquipmentClass | null>(
  ['SECURITY', 'FIRE', 'LIGHTNING'].includes(routeClass || '') ? routeClass! : null,
);
const tableQuery = computed(() => ({
  orgId: orgId.value || undefined,
  equipmentClass: equipmentClass.value || undefined,
}));
const catalog = ref<RecordFilterOptionsVo>({ devices: [], locations: [] });
const editVisible = ref(false);
const editing = ref<SafetyDeviceProfileVo | null>(null);
const classOptions = [
  { label: '安防设备', value: 'SECURITY' },
  { label: '消防设备', value: 'FIRE' },
  { label: '防雷设备', value: 'LIGHTNING' },
];
const assetStatusOptions = [
  { label: '在用', value: 'IN_SERVICE' },
  { label: '停用', value: 'OUT_OF_SERVICE' },
  { label: '维护中', value: 'MAINTENANCE' },
  { label: '已退役', value: 'RETIRED' },
];
const identifierLabels: Record<string, string> = {
  deviceCode: '设备编码',
  externalDeviceId: '外部设备标识',
  protocolCode: '协议编码',
};
const technicalAttributeLabels: Record<string, string> = {
  modelCode: '设备型号编码',
  modelVersion: '型号版本',
  codecCode: '采集适配器',
  supportedProtocols: '支持协议',
  source: '数据来源',
  communicationProtocol: '通信协议',
};
const deviceOptions = computed(() =>
  catalog.value.devices.map((item) => ({
    label: `${item.name}（${item.code}） · ${item.locationPath || '未配置位置'}`,
    value: item.id,
  })),
);
const form = reactive({
  orgId: null as string | null,
  deviceId: '',
  equipmentClass: 'SECURITY' as SafetyEquipmentClass,
  installedAt: null as number | null,
  commissionedAt: null as number | null,
  ownerUserId: '',
  assetStatus: 'IN_SERVICE' as SafetyAssetStatus,
});
const selectedCatalogDevice = computed(() => catalog.value.devices.find((item) => item.id === form.deviceId));
const selectedProfile = computed(() => (editing.value?.deviceId === form.deviceId ? editing.value : null));
const sourceInfoRows = computed(() => {
  const profile = selectedProfile.value;
  const device = selectedCatalogDevice.value;
  return [
    { label: '设备名称', value: profile?.deviceName || device?.name || '--' },
    { label: '安装位置', value: profile?.locationPath || device?.locationPath || '--' },
    { label: '设备类型', value: profile?.equipmentType || '--' },
    { label: '生产厂家', value: profile?.manufacturer || '--' },
    { label: '设备型号', value: profile?.model || '--' },
    { label: '通信状态', value: profile ? (profile.online ? '在线' : '离线') : '--' },
    { label: '最近通信', value: profile?.lastSeenAt ? formatDateTime(profile.lastSeenAt) : '--' },
  ];
});
const sourceIdentifierRows = computed(() => {
  const values = selectedProfile.value?.identifiers || {
    deviceCode: selectedCatalogDevice.value?.code,
    externalDeviceId: selectedCatalogDevice.value?.externalDeviceId,
  };
  return propertyRows(values, identifierLabels);
});
const sourceTechnicalRows = computed(() =>
  propertyRows(selectedProfile.value?.technicalAttributes || {}, technicalAttributeLabels),
);
const basisText = ref('GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求');
const columns: DataTableColumns<SafetyDeviceProfileVo> = [
  { title: '设备', key: 'deviceName', minWidth: 180, render: (r) => `${r.deviceName}（${r.deviceCode}）` },
  { title: '类别', key: 'equipmentClass', width: 100, render: (r) => classLabel(r.equipmentClass) },
  {
    title: '业务档案',
    key: 'profiled',
    width: 100,
    render: (r) =>
      h(
        NTag,
        { type: r.profiled ? 'success' : 'default', bordered: false },
        { default: () => (r.profiled ? '已完善' : '待完善') },
      ),
  },
  { title: '设备类型', key: 'equipmentType', minWidth: 150 },
  {
    title: '位置',
    key: 'locationPath',
    minWidth: 200,
    ellipsis: { tooltip: true },
    render: (r) => r.locationPath || '--',
  },
  {
    title: '通信状态',
    key: 'online',
    width: 100,
    render: (r) =>
      h(
        NTag,
        { type: r.online ? 'success' : 'warning', bordered: false },
        { default: () => (r.online ? '在线' : '离线') },
      ),
  },
  {
    title: '最近通信',
    key: 'lastSeenAt',
    width: 180,
    render: (r) => (r.lastSeenAt ? formatDateTime(r.lastSeenAt) : '--'),
  },
  { title: '负责人', key: 'ownerUserId', width: 130, render: (r) => r.ownerUserId || '--' },
  {
    title: '资产状态',
    key: 'assetStatus',
    width: 100,
    render: (r) => assetStatusOptions.find((i) => i.value === r.assetStatus)?.label || r.assetStatus,
  },
  {
    title: '厂家/型号',
    key: 'model',
    minWidth: 180,
    render: (r) => [r.manufacturer, r.model].filter(Boolean).join(' / ') || '--',
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    fixed: 'right',
    render: (r) =>
      h(NSpace, null, {
        default: () => [
          h(NButton, { text: true, onClick: () => openSpatial(r.locationId || undefined) }, { default: () => '定位' }),
          canManage.value
            ? h(
                NButton,
                { text: true, type: 'primary', onClick: () => openEdit(r) },
                { default: () => (r.profiled ? '编辑' : '完善') },
              )
            : null,
        ],
      }),
  },
];
function classLabel(value: SafetyEquipmentClass) {
  return classOptions.find((i) => i.value === value)?.label || value;
}
function formatSourceValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '--';
  if (Array.isArray(value)) return value.map(formatSourceValue).join('、');
  if (typeof value === 'object') {
    return Object.entries(value)
      .map(([key, item]) => `${key}：${formatSourceValue(item)}`)
      .join('；');
  }
  if (typeof value === 'boolean') return value ? '是' : '否';
  return String(value);
}
function propertyRows(values: Record<string, unknown>, labels: Record<string, string>) {
  return Object.entries(values)
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({ key, label: labels[key] || key, value: formatSourceValue(value) }));
}
function formatPropertyRecord(values: Record<string, unknown>, labels: Record<string, string>): string {
  return propertyRows(values, labels)
    .map((item) => `${item.label}：${item.value}`)
    .join('；');
}
function parseBasis(): SafetyComplianceBasis[] {
  return basisText.value
    .split(/[;；]/)
    .map((v) => v.trim())
    .filter(Boolean)
    .map((item) => {
      const [code, title = code, clause] = item.split('|').map((v) => v.trim());
      return { code, title, clause: clause || null };
    });
}
function openSpatial(locationId?: string) {
  void router.push({
    path: '/museum-device/spatial',
    query: {
      ...(orgId.value ? { orgId: orgId.value } : {}),
      ...(locationId ? { locationId } : {}),
    },
  });
}
function openDeviceManagement() {
  void router.push('/museum-device/device');
}
async function exportProfiles() {
  exporting.value = true;
  error.value = '';
  try {
    const profiles =
      unwrapResponse<SafetyDeviceProfileVo[]>(
        await safetyApi.safetyDeviceProfiles({
          orgId: orgId.value || undefined,
          equipmentClass: equipmentClass.value || undefined,
        }),
      ) || [];
    const values = profiles.map((row) => ({
      场馆ID: row.orgId,
      采集设备ID: row.deviceId,
      设备编码: row.deviceCode,
      设备名称: row.deviceName,
      设备类别: row.equipmentClass,
      设备类型: row.equipmentType,
      生产厂家: row.manufacturer || '',
      设备型号: row.model || '',
      安装位置: row.locationPath || '',
      通信状态: row.online ? '在线' : '离线',
      最近通信: row.lastSeenAt ? formatDateTime(row.lastSeenAt) : '',
      负责人ID: row.ownerUserId,
      资产状态: row.assetStatus,
      设备标识: formatPropertyRecord(row.identifiers || {}, identifierLabels),
      技术参数: formatPropertyRecord(row.technicalAttributes || {}, technicalAttributeLabels),
    }));
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(values), '安全防护设备');
    XLSX.writeFile(workbook, '安全防护设备台账.xlsx');
  } catch (cause: any) {
    error.value = cause?.message || '安全防护设备台账导出失败。';
  } finally {
    exporting.value = false;
  }
}
async function loadCatalog(value: string | null = form.orgId) {
  try {
    catalog.value = value
      ? unwrapResponse<RecordFilterOptionsVo>(await ingestApi.recordFilterOptions({ orgId: value }))
      : { devices: [], locations: [] };
  } catch {
    catalog.value = { devices: [], locations: [] };
  }
}
async function venueChanged() {
  await loadCatalog(orgId.value);
  tableRef.value?.handleQuery?.();
}
async function fetchProfiles(params: Record<string, unknown>) {
  error.value = '';
  try {
    return await safetyApi.safetyDeviceProfilePage(params);
  } catch (cause: any) {
    error.value = cause?.message || '安全防护设备档案加载失败。';
    throw cause;
  }
}
function refreshTable() {
  tableRef.value?.handleRefresh?.();
}
function openCreate() {
  editing.value = null;
  Object.assign(form, {
    orgId: orgId.value,
    deviceId: '',
    equipmentClass: equipmentClass.value || 'SECURITY',
    installedAt: null,
    commissionedAt: null,
    ownerUserId: '',
    assetStatus: 'IN_SERVICE',
  });
  basisText.value = 'GB/T 16571-2012|博物馆和文物保护单位安全防范系统要求';
  void loadCatalog(form.orgId);
  editVisible.value = true;
}
function openEdit(row: SafetyDeviceProfileVo) {
  editing.value = row;
  Object.assign(form, {
    orgId: row.orgId,
    deviceId: row.deviceId,
    equipmentClass: row.equipmentClass,
    installedAt: row.installedAt || null,
    commissionedAt: row.commissionedAt || null,
    ownerUserId: row.ownerUserId,
    assetStatus: row.assetStatus,
  });
  basisText.value = row.complianceBasis.map((item) => `${item.code}|${item.title}|${item.clause || ''}`).join('；');
  void loadCatalog(row.orgId);
  editVisible.value = true;
}
async function save() {
  if (!form.orgId || !form.deviceId || !form.ownerUserId) {
    error.value = '请完整填写场馆、采集设备和负责人。';
    return;
  }
  try {
    await safetyApi.upsertSafetyDeviceProfile(form.deviceId, {
      ...form,
      complianceBasis: parseBasis(),
    });
    message.success('设备档案已保存');
    editVisible.value = false;
    refreshTable();
  } catch (cause: any) {
    error.value = cause?.message || '设备档案保存失败。';
  }
}
</script>

<style scoped>
.equipment-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  overflow: hidden;
}
.safety-page-table {
  min-height: 0;
  flex: 1;
}
.state-alert {
  margin-bottom: 12px;
}
.source-panel {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--body-color);
}
.source-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--text-color-2);
  font-size: 13px;
}
.source-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}
.source-item,
.source-property-row {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 8px;
}
.source-item__label,
.source-property-row > span:first-child {
  color: var(--text-color-3);
}
.source-item__value,
.source-property-row > span:last-child {
  min-width: 0;
  overflow-wrap: anywhere;
  color: var(--text-color-1);
}
.source-property-group {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--border-color);
}
.source-property-group__title {
  margin-bottom: 8px;
  color: var(--text-color-1);
  font-weight: 500;
}
.source-property-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}
@media (max-width: 720px) {
  .equipment-page {
    padding: 8px;
  }
  :deep(.n-card-header) {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
  }
  :deep(.n-card-header__extra .n-space) {
    flex-wrap: wrap;
  }
  .source-panel__header {
    align-items: flex-start;
    flex-direction: column;
  }
  .source-grid,
  .source-property-list {
    grid-template-columns: 1fr;
  }
}
</style>
