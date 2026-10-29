<template>
  <div class="device-page">
    <NTabs
      v-model:value="activeTab"
      type="line"
      animated
      class="device-tabs"
      pane-wrapper-style="display: flex; flex: 1; min-height: 0; overflow: hidden;"
      pane-style="display: flex; flex: 1; min-height: 0; flex-direction: column; overflow: hidden;"
    >
      <NTabPane name="devices" tab="设备实例" display-directive="show">
        <HPageDataTable
          ref="tableRef"
          class="device-data-table"
          :columns="columns"
          :fetch-data="fetchData"
          :params="query"
          :fun-api-delete="ingestApi.deleteDevice"
          :scroll-x="1960"
          row-key-field="id"
          @add="onAdd"
          @rowEdit="onEdit"
          @row-view="onView"
        >
          <template #queryForm>
            <NFormItem label="网关">
              <NSelect
                v-model:value="query.gatewayId"
                :options="gatewayOptions"
                clearable
                filterable
                style="width: 220px"
              />
            </NFormItem>
            <NFormItem label="设备型号">
              <NSelect
                v-model:value="query.modelCode"
                :options="modelOptions"
                multiple
                clearable
                filterable
                :max-tag-count="1"
                placeholder="请选择设备型号（可多选）"
                style="width: 280px"
              />
            </NFormItem>
            <NFormItem label="关键字">
              <NInput
                v-model:value="query.keyword"
                clearable
                placeholder="编号 / 名称 / 外部标识"
                style="width: 240px"
              />
            </NFormItem>
          </template>

          <HPageForm
            v-model:show="formShow"
            :title="formType === 'candidate' ? '登记待注册设备' : formType === 'add' ? '新增设备实例' : '编辑设备实例'"
            :model="form"
            :rules="isManagedSource ? sourceLocationRules : formType === 'candidate' ? candidateRules : rules"
            :ref-table="tableRef"
            :handle-submit="handleSubmit"
            label-placement="left"
            label-width="110"
            width="60%"
          >
            <template v-if="isManagedSource">
              <NAlert type="info" :bordered="false">{{ isExternalDevice ? '请核对摄像头名称并选择实际安装位置。第三方地址不会自动作为安装位置。' : '采集标识由事实接口维护；请核对名称和实际监测位置，历史事实继续保留。' }}</NAlert>
              <template v-if="isExternalDevice">
                <NFormItem label="接入型号">视频平台摄像头</NFormItem>
                <NFormItem label="所属网关">{{ gatewayName(form.gatewayId) }}</NFormItem>
                <NFormItem label="接入方式">外部系统推送</NFormItem>
              </template>
              <NFormItem label="设备名称" path="name"><NInput v-model:value="form.name" /></NFormItem>
              <NFormItem label="来源标识"><NInput :value="form.externalDeviceId" readonly /></NFormItem>
            </template>
            <template v-else>
            <NFormItem label="设备型号" path="modelCode">
              <NSelect
                :value="form.modelCode"
                :options="formModelOptions"
                filterable
                @update:value="changeFormModel"
              />
            </NFormItem>
            <NFormItem label="设备编号" path="code"
              ><NInput v-model:value="form.code" placeholder="机构内唯一"
            /></NFormItem>
            <NFormItem label="设备名称" path="name"><NInput v-model:value="form.name" /></NFormItem>
            <NFormItem label="所属网关" path="gatewayId">
              <NSelect
                v-model:value="form.gatewayId"
                :options="gatewayOptions"
                :disabled="formType === 'candidate'"
                filterable
                @update:value="changeFormGateway"
              />
              <span v-if="isSikeModel" class="sike-field-hint">用于确定场馆归属；本机读写数据不经过网关</span>
            </NFormItem>
            <NFormItem label="外部设备标识" path="externalDeviceId">
              <NInput
                v-model:value="form.externalDeviceId"
                :disabled="formType === 'candidate'"
                placeholder="DevEUI、从站地址或厂商设备编号"
              />
            </NFormItem>
            <NFormItem label="业务协议" path="protocolCode">
              <NSelect
                v-model:value="form.protocolCode"
                :options="formProtocolOptions"
                :disabled="formType === 'candidate'"
                filterable
                placeholder="请选择设备实际接入协议"
              />
            </NFormItem>
            <NFormItem label="业务角色" path="businessRoles">
              <HDictSelect
                v-model:value="form.businessRoles"
                type="museum_rfid_device_role"
                multiple
                return-array
                clearable
                placeholder="工作站外设选择实际用途；普通采集设备可留空"
              />
            </NFormItem>
            <template v-if="form.businessRoles?.length || isSikeModel">
              <NFormItem label="硬件序列号" path="rfidHardwareSerial">
                <NInput v-model:value="form.rfidHardwareSerial" clearable :placeholder="isSikeModel ? '在设备电脑上点击连接并读取' : '留空时使用外部设备标识'" />
                <NButton v-if="isSikeModel" :loading="sikeProbing" class="sike-probe-button" @click="probeReaderSerial">连接并读取</NButton>
              </NFormItem>
              <NFormItem v-if="hasRadioBusinessRole || isSikeModel" label="RFID 天线数" path="rfidAntennaCount">
                <NInputNumber v-model:value="form.rfidAntennaCount" :min="1" :max="isSikeModel ? 16 : 32" />
              </NFormItem>
              <NAlert type="info" :bordered="false">
                保存设备时会自动生成工作站外设业务档案，无需再到“RFID 设备”页面重复建档。
              </NAlert>
            </template>
            </template>
            <NFormItem label="统一安装地址" path="locationId" required>
              <NSelect
                v-model:value="form.locationId"
                :options="locationOptions"
                :loading="locationLoading"
                clearable
                filterable
                placeholder="请选择统一安装地址"
              >
                <template #empty
                  ><NEmpty size="small" description="请先在“采集位置”中同步或新增当前场馆的位置"
                /></template>
              </NSelect>
            </NFormItem>
            <NAlert v-if="locationMoved" type="warning" :bordered="false">
              设备位置修改后，历史事实和最新值仍按设备连续保留；趋势查询会提示本次位置变化。
            </NAlert>
            <NFormItem v-if="locationMoved" label="调整原因">
              <NInput
                v-model:value="form.locationChangeReason"
                type="textarea"
                :rows="2"
                placeholder="请说明馆内调整、检修或重新布点原因"
              />
            </NFormItem>
            <template v-if="isSikeModel">
              <NAlert type="info" :bordered="false" class="sike-config-tip">
                斯科 C5/5A 由绑定的工作站电脑连接本机服务。登记后在 RFID 基础管理中把设备绑定到工作站，再从工作台读取 EPC/TID、发标写 EPC 并回读核验，借还与盘点继续使用同一藏品标签。
              </NAlert>
              <NAlert v-if="sikeConfigError" type="warning" :bordered="false" class="sike-config-tip">{{ sikeConfigError }}</NAlert>
              <NFormItem
                label="本机服务地址"
                feedback="预填厂家示例地址；若现场服务修改过端口或标识，请按实际值调整"
                required
              >
                <NInput v-model:value="sikeConfig.serviceUrl" placeholder="ws://127.0.0.1:9090/rfidService/1234" />
              </NFormItem>
              <NFormItem label="连接方式" required>
                <NSelect :value="sikeConfig.connectType" :options="sikeConnectTypes" @update:value="changeSikeConnectType" />
              </NFormItem>
              <NFormItem label="读写器地址" :feedback="sikeConnectAddressFeedback" required>
                <NInput v-model:value="sikeConfig.connectAddress" :placeholder="sikeConnectAddressHint" />
                <NButton v-if="sikeConfig.connectType === 4" :loading="sikeHidScanning" class="sike-probe-button" @click="scanSikeHidAddress">扫描 HID</NButton>
              </NFormItem>
              <NFormItem label="工作天线" required>
                <NSelect v-model:value="sikeAntennas" :options="sikeAntennaOptions" multiple placeholder="选择实际接入的天线" />
              </NFormItem>
              <NFormItem label="TID 读取长度" required>
                <NInputNumber v-model:value="sikeConfig.tidWordLength" :min="1" :max="16" />
                <span class="sike-field-hint">单位：字；默认 6</span>
              </NFormItem>
              <NAlert type="warning" :bordered="false" class="sike-config-tip">
                请在设备所在电脑、停止当前扫描后读取序列号。功率、频段和基带参数目前由读写器保存；首次启用前请核对现场设置。更改连接参数后需在工作台重新取得操作权。
              </NAlert>
            </template>
            <NFormItem v-else-if="!isManagedSource" label="实例配置">
              <NInput
                v-model:value="form.instanceConfig"
                type="textarea"
                :rows="3"
                placeholder="设备差异配置 JSON，可留空"
              />
            </NFormItem>
            <NAlert v-if="selectedModel" type="info" :bordered="false">
              {{ selectedModel.vendor }} · {{ selectedModel.name }}；默认输出
              {{ selectedModel.metrics.map((item) => `${item.name}(${item.metricCode})`).join('、') || '动态指标' }}。
            </NAlert>
            <NAlert v-if="formType === 'candidate' && !isExternalDevice" type="warning" :bordered="false">
              保存后将自动重放该网关、该协议下此外部设备标识对应的全部待识别消息。
            </NAlert>
          </HPageForm>

          <HPageView v-model:show="viewShow" title="设备实例详情" width="680px" :show-action="false">
            <NDescriptions v-if="viewRow" label-placement="left" :column="1" bordered size="small">
              <NDescriptionsItem label="设备编号">{{ viewRow.code || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="设备名称">{{ viewRow.name || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="来源类型">{{ deviceKindName(viewRow.kind) }}</NDescriptionsItem>
              <NDescriptionsItem label="设备型号">{{
                viewRow.modelName || modelName(viewRow.modelCode)
              }}</NDescriptionsItem>
              <NDescriptionsItem label="所属网关">{{ viewRow.gatewayName || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="业务协议">{{ protocolName(viewRow.protocolCode) }}</NDescriptionsItem>
              <NDescriptionsItem label="业务角色">{{ businessRoleText(viewRow.businessRoles) }}</NDescriptionsItem>
              <template v-if="isSikeDeviceModel(viewRow.modelCode)">
                <NDescriptionsItem v-if="viewSikeError" label="配置检查">{{ viewSikeError }}</NDescriptionsItem>
                <NDescriptionsItem label="硬件序列号">{{ viewRow.rfidHardwareSerial || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="本机服务地址">{{ viewSikeConfig.serviceUrl || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="连接方式">{{ sikeConnectTypeName(viewSikeConfig.connectType) }}</NDescriptionsItem>
                <NDescriptionsItem label="读写器地址">{{ viewSikeConfig.connectAddress || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="工作天线">{{ sikeAntennaNumbers(viewSikeConfig.antennaMask).join('、') || '--' }}</NDescriptionsItem>
                <NDescriptionsItem label="TID 读取长度">{{ viewSikeConfig.tidWordLength }} 字</NDescriptionsItem>
              </template>
              <NDescriptionsItem label="外部设备标识">{{ viewRow.externalDeviceId || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="馆区/站点">{{ viewRow.siteId || '--' }}</NDescriptionsItem>
              <NDescriptionsItem label="统一安装地址">{{
                viewRow.locationPath || viewRow.locationId || '--'
              }}</NDescriptionsItem>
              <NDescriptionsItem label="最后接收">{{ formatDateTime(viewRow.lastSeenAt) }}</NDescriptionsItem>
            </NDescriptions>
            <NButton v-if="viewRow?.id" class="location-history-button" @click="openLocationHistory(viewRow)"
              >查看位置变更记录</NButton
            >
          </HPageView>

          <NModal
            v-model:show="bindingShow"
            preset="card"
            title="设备指标"
            style="width: min(1180px, calc(100vw - 32px))"
          >
            <NSpace vertical :size="14">
              <NAlert type="info" :bordered="false">
                以下为型号预置与设备覆盖合并后的指标；可逐项停用，停用后实时、历史和预警均不再采集该指标。
              </NAlert>
              <NForm inline label-placement="top">
                <NFormItem label="设备输出键" required
                  ><NInput v-model:value="bindingForm.externalMetricKey" style="width: 180px"
                /></NFormItem>
                <NFormItem label="统一指标编码" required
                  ><NInput v-model:value="bindingForm.metricCode" style="width: 180px"
                /></NFormItem>
                <NFormItem label="倍率"
                  ><NInputNumber v-model:value="bindingForm.scale" style="width: 110px"
                /></NFormItem>
                <NFormItem label="偏移"
                  ><NInputNumber v-model:value="bindingForm.offset" style="width: 110px"
                /></NFormItem>
                <NFormItem label="字段映射 JSON">
                  <NInput
                    v-model:value="bindingFieldMappingsText"
                    type="textarea"
                    :rows="2"
                    style="width: 260px"
                    placeholder='如 {"ua":"A","ub":"B","uc":"C"}'
                  />
                </NFormItem>
                <NFormItem label=" "><NButton type="primary" @click="saveBinding">保存覆盖</NButton></NFormItem>
              </NForm>
              <NDataTable
                :columns="bindingColumns"
                :data="effectiveMetricRows"
                :loading="bindingLoading"
                :pagination="false"
                :scroll-x="1210"
              />
            </NSpace>
          </NModal>
          <NDrawer v-model:show="locationHistoryShow" :width="620" placement="right">
            <NDrawerContent title="设备位置变更记录" closable>
              <NAlert type="info" :bordered="false" class="location-history-alert">
                历史监测数据始终归属该设备；下列记录仅用于提示各时间段的安装位置变化。
              </NAlert>
              <NDataTable
                :columns="locationHistoryColumns"
                :data="locationHistory"
                :loading="locationHistoryLoading"
                :pagination="false"
              />
              <NEmpty
                v-if="!locationHistory.length && !locationHistoryLoading"
                description="新版本上线后尚无位置变更记录"
              />
            </NDrawerContent>
          </NDrawer>
        </HPageDataTable>
      </NTabPane>

      <NTabPane name="candidates" tab="待注册设备" display-directive="show:lazy">
        <HPageDataTable
          ref="candidateTableRef"
          title="待注册设备"
          class="candidate-data-table"
          :columns="candidateColumns"
          :fetch-data="fetchCandidates"
          :params="candidateQuery"
          :pagination="false"
          :scroll-x="1280"
          row-key-field="id"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          enable-selection
        >
          <template #queryForm>
            <NFormItem label="网关">
              <NSelect
                v-model:value="candidateQuery.gatewayId"
                :options="gatewayOptions"
                clearable
                filterable
                style="width: 260px"
              />
            </NFormItem>
            <NFormItem label="设备标识">
              <NInput
                v-model:value="candidateQuery.externalDeviceId"
                clearable
                placeholder="外部设备标识"
                style="width: 220px"
              />
            </NFormItem>
            <NFormItem label="推断型号">
              <NSelect
                v-model:value="candidateQuery.modelCode"
                :options="modelOptions"
                clearable
                filterable
                style="width: 280px"
              />
            </NFormItem>
            <NFormItem label="业务协议">
              <NSelect
                v-model:value="candidateQuery.protocolCode"
                :options="protocolOptions"
                clearable
                filterable
                style="width: 280px"
              />
            </NFormItem>
            <NFormItem label="型号提示">
              <NInput
                v-model:value="candidateQuery.modelHint"
                clearable
                placeholder="协议上报的型号提示"
                style="width: 220px"
              />
            </NFormItem>
          </template>
          <template #activeBtn="{ checkedTableData = [] }">
            <NSpace>
              <NButton
                type="primary"
                size="small"
                :disabled="!checkedTableData.length || candidateDeleting"
                @click="openBatchRegistration(checkedTableData)"
              >
                批量登记{{ checkedTableData.length ? `（${checkedTableData.length}）` : '' }}
              </NButton>
              <NButton
                type="error"
                secondary
                size="small"
                :loading="candidateDeleting"
                :disabled="!checkedTableData.length"
                @click="confirmDeleteCandidates(checkedTableData)"
              >
                删除选中{{ checkedTableData.length ? `（${checkedTableData.length}）` : '' }}
              </NButton>
              <NButton
                type="error"
                secondary
                size="small"
                :loading="candidateDeleting"
                :disabled="!candidateCount"
                @click="confirmDeleteCandidates()"
              >
                {{ hasCandidateFilters ? '清空当前筛选' : '一键清空'
                }}{{ candidateCount ? `（${candidateCount}）` : '' }}
              </NButton>
            </NSpace>
          </template>
          <template #toolbar-extra>
            <NButton size="small" @click="refreshCandidates">刷新</NButton>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>

    <NModal
      v-model:show="batchShow"
      preset="card"
      title="批量登记待注册设备"
      style="width: min(640px, calc(100vw - 32px))"
      :mask-closable="false"
    >
      <NSpace vertical :size="14">
        <NAlert type="info" :bordered="false">
          将登记 {{ batchRows.length }} 台“{{
            batchModelName
          }}”设备。系统会自动使用已识别的网关、协议、外部标识和型号，并生成设备编号与名称。
        </NAlert>
        <NForm label-placement="left" label-width="100">
          <NFormItem label="统一安装地址" required>
            <NSelect
              v-model:value="batchLocationId"
              :options="batchLocationOptions"
              :loading="batchLocationLoading"
              clearable
              filterable
              placeholder="为本批设备选择统一安装位置"
            />
          </NFormItem>
          <NFormItem v-if="!isExternalBatch" label="业务角色">
            <HDictSelect
              v-model:value="batchBusinessRoles"
              type="museum_rfid_device_role"
              multiple
              return-array
              clearable
              placeholder="工作站外设选择实际用途；普通采集设备可留空"
            />
          </NFormItem>
          <NFormItem v-if="hasRadioRole(batchBusinessRoles)" label="RFID 天线数">
            <NInputNumber v-model:value="batchRfidAntennaCount" :min="1" :max="32" />
          </NFormItem>
        </NForm>
      </NSpace>
      <template #footer>
        <NSpace justify="end">
          <NButton :disabled="batchSubmitting" @click="batchShow = false">取消</NButton>
          <NButton type="primary" :loading="batchSubmitting" @click="submitBatchRegistration">
            确认登记（{{ batchRows.length }}）
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import {
  NAlert,
  NButton,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NDrawer,
  NDrawerContent,
  NEmpty,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  type DataTableColumns,
  useDialog,
  useMessage,
} from 'naive-ui';
import { formatDateTime } from '@/utils';
import { getDictLabel, useDict } from '@/utils/dict';
import { gatewayApi } from '../api/gateway';
import { ingestApi } from '../api/ingest';
import { metricDisplayName } from '../utils/metricName';
import {
  discoverSikeHidAddress,
  emptySikeDeviceConfig,
  isSikeDeviceModel,
  parseSikeDeviceConfig,
  probeSikeReader,
  serializeSikeDeviceConfig,
  sikeAntennaNumbers,
  sikeDefaultConnectAddress,
  validateSikeDeviceConfig,
  type SikeDeviceConfig,
} from '../utils/sikeDeviceConfig';
import type {
  DeviceCandidateBatchRegistrationResultVo,
  DeviceCandidateDeleteResultVo,
  DeviceCandidateRegistrationResultVo,
  DeviceCandidateVo,
  DeviceMetricBindingVo,
  DeviceVo,
  GatewayVo,
  IngestCatalog,
  MuseumDeviceLocationChangeVo,
  MuseumLocationOptionVo,
  RfidDeviceBusinessRole,
} from '../typings';

const emptyCatalog: IngestCatalog = {
  protocols: [],
  codecs: [],
  deviceModels: [],
  builtInConnectors: [],
  standardMetrics: [],
};
const message = useMessage();
const dialog = useDialog();
const activeTab = ref<'devices' | 'candidates'>('devices');
const tableRef = ref<any>(null);
const candidateTableRef = ref<any>(null);
const query = reactive({
  gatewayId: null as string | null,
  modelCode: null as string[] | null,
  keyword: null as string | null,
});
const candidateQuery = reactive({
  gatewayId: null as string | null,
  externalDeviceId: null as string | null,
  modelCode: null as string | null,
  protocolCode: null as string | null,
  modelHint: null as string | null,
});
type CandidateFilters = Partial<Record<keyof typeof candidateQuery, string>>;
const catalog = ref<IngestCatalog>(emptyCatalog);
const gateways = ref<GatewayVo[]>([]);
const formShow = ref(false);
const formType = ref<'add' | 'edit' | 'candidate'>('add');
const form = ref<DeviceVo>({});
const originalLocationId = ref<string | null>(null);
const registeringCandidate = ref<DeviceCandidateVo | null>(null);
const batchShow = ref(false);
const batchSubmitting = ref(false);
const batchRows = ref<DeviceCandidateVo[]>([]);
const candidateCount = ref(0);
const candidateAppliedFilters = ref<CandidateFilters>({});
const candidateDeleting = ref(false);
const batchLocationId = ref<string | null>(null);
const batchBusinessRoles = ref<RfidDeviceBusinessRole[]>([]);
const batchRfidAntennaCount = ref(1);
const batchLocations = ref<MuseumLocationOptionVo[]>([]);
const batchLocationLoading = ref(false);
const locations = ref<MuseumLocationOptionVo[]>([]);
const locationLoading = ref(false);
const viewShow = ref(false);
const viewRow = ref<DeviceVo | null>(null);
const sikeConfig = ref<SikeDeviceConfig>(emptySikeDeviceConfig());
const sikeConfigError = ref('');
const sikeProbing = ref(false);
const sikeHidScanning = ref(false);
const sikeAddressByType = ref<Record<number, string>>({});
const sikeConnectTypes = [
  { label: 'TCP', value: 1 },
  { label: '串口 RS232', value: 2 },
  { label: '串口 RS485', value: 3 },
  { label: 'USB-HID', value: 4 },
];
const sikeConnectAddressHint = computed(() => {
  if (sikeConfig.value.connectType === 4) return '自动扫描或手动填写设备 HID 地址';
  return sikeDefaultConnectAddress(sikeConfig.value.connectType);
});
const sikeConnectAddressFeedback = computed(() => sikeConfig.value.connectType === 4
  ? 'USB-HID 无固定地址，切换时自动扫描本机设备；也可手动填写'
  : '预填厂家示例地址，请按现场读写器的实际地址或串口调整');
const sikeAntennaOptions = computed(() =>
  Array.from({ length: Math.min(16, Math.max(1, form.value.rfidAntennaCount || 1)) }, (_, index) => ({
    label: `${index + 1} 号天线`, value: index + 1,
  })),
);
const sikeAntennas = computed<number[]>({
  get: () => sikeAntennaNumbers(sikeConfig.value.antennaMask),
  set: (values) => { sikeConfig.value.antennaMask = values.reduce((mask, antenna) => mask | (1 << (antenna - 1)), 0); },
});
const viewSikeConfig = computed(() => parseSikeDeviceConfig(viewRow.value?.instanceConfig).config);
const viewSikeError = computed(() => parseSikeDeviceConfig(viewRow.value?.instanceConfig).error);
function sikeConnectTypeName(type: number) {
  return sikeConnectTypes.find((item) => item.value === type)?.label || '--';
}
function loadSikeFormConfig(raw?: string | null) {
  const parsed = parseSikeDeviceConfig(raw);
  sikeConfig.value = parsed.config;
  sikeConfigError.value = parsed.error;
  sikeAddressByType.value = { [parsed.config.connectType]: parsed.config.connectAddress };
}
function changeSikeConnectType(connectType: number) {
  if (connectType === sikeConfig.value.connectType) return;
  sikeAddressByType.value[sikeConfig.value.connectType] = sikeConfig.value.connectAddress;
  sikeConfig.value.connectType = connectType;
  sikeConfig.value.connectAddress = sikeAddressByType.value[connectType] ?? sikeDefaultConnectAddress(connectType);
  if (connectType === 4 && !sikeConfig.value.connectAddress) void scanSikeHidAddress();
}
async function scanSikeHidAddress() {
  if (sikeHidScanning.value) return;
  const currentForm = form.value;
  const serviceUrl = sikeConfig.value.serviceUrl;
  const previousAddress = sikeConfig.value.connectAddress;
  sikeHidScanning.value = true;
  try {
    const address = await discoverSikeHidAddress(serviceUrl);
    if (form.value !== currentForm || !formShow.value || sikeConfig.value.connectType !== 4 ||
      sikeConfig.value.serviceUrl !== serviceUrl || sikeConfig.value.connectAddress !== previousAddress) return;
    sikeConfig.value.connectAddress = address;
    sikeAddressByType.value[4] = address;
    message.success('已读取 USB-HID 设备地址');
  } catch (error) {
    if (form.value === currentForm && formShow.value && sikeConfig.value.connectType === 4) {
      message.warning(error instanceof Error ? error.message : 'USB-HID 扫描失败');
    }
  } finally {
    sikeHidScanning.value = false;
  }
}
async function probeReaderSerial() {
  if (sikeProbing.value) return;
  const currentForm = form.value;
  sikeProbing.value = true;
  try {
    const serial = await probeSikeReader(sikeConfig.value, form.value.rfidAntennaCount);
    if (form.value !== currentForm || !formShow.value) return;
    form.value.rfidHardwareSerial = serial;
    message.success(`已读取设备序列号：${serial}`);
  } catch (error) {
    message.error(error instanceof Error ? error.message : '读取序列号失败');
  } finally {
    sikeProbing.value = false;
  }
}
const locationHistoryShow = ref(false);
const locationHistoryLoading = ref(false);
const locationHistory = ref<MuseumDeviceLocationChangeVo[]>([]);
const bindingShow = ref(false);
const bindingDevice = ref<DeviceVo | null>(null);
const bindings = ref<DeviceMetricBindingVo[]>([]);
const bindingLoading = ref(false);
const bindingForm = ref<DeviceMetricBindingVo>({ scale: 1, offset: 0 });
const bindingFieldMappingsText = ref('{}');
const bindingSavingKeys = ref(new Set<string>());

type EffectiveMetricSource = 'MODEL' | 'OVERRIDE' | 'DYNAMIC';
interface EffectiveMetricRow extends DeviceMetricBindingVo {
  name: string;
  unit?: string | null;
  source: EffectiveMetricSource;
  override?: DeviceMetricBindingVo;
}

const isStandardSource = computed(() => form.value.kind === 'STANDARD_SOURCE');
const isExternalDevice = computed(() => form.value.kind === 'EXTERNAL_DEVICE');
const isManagedSource = computed(() => isStandardSource.value || isExternalDevice.value);
const isExternalBatch = computed(() => batchRows.value.length > 0 && batchRows.value.every((row) => row.kind === 'EXTERNAL_DEVICE'));
const sourceLocationRules = {
  name: { required: true, message: '请输入设备名称', trigger: ['blur', 'input'] },
  locationId: { required: true, message: '请选择统一安装地址', trigger: 'change' },
};
const locationMoved = computed(
  () => formType.value === 'edit' && (form.value.locationId || null) !== originalLocationId.value,
);
const hasCandidateFilters = computed(() =>
  Object.values(candidateAppliedFilters.value).some((value) => Boolean(value)),
);

const gatewayOptions = computed(() =>
  gateways.value
    .filter((item) => item.id)
    .map((item) => ({ label: `${item.name || item.code}（${item.code || '--'}）`, value: item.id! })),
);
const modelOptions = computed(() =>
  catalog.value.deviceModels.map((item) => ({
    label: `${item.vendor} / ${item.name} / ${item.version}`,
    value: item.code,
  })),
);
const protocolOptions = computed(() =>
  catalog.value.protocols.map((item) => ({
    label: `${item.name}（${item.code}）`,
    value: item.code,
  })),
);
const { options: businessRoleOptions } = useDict('museum_rfid_device_role');
const radioBusinessRoles = new Set<RfidDeviceBusinessRole>([
  'DESK_READER',
  'TAG_WRITER',
  'HANDHELD',
  'WAREHOUSE_GATE',
  'MUSEUM_GATE',
]);
const sikeRadioBusinessRoles = new Set<RfidDeviceBusinessRole>([
  'DESK_READER', 'TAG_WRITER', 'WAREHOUSE_GATE', 'MUSEUM_GATE',
]);
const hasRadioBusinessRole = computed(() => hasRadioRole(form.value.businessRoles));
const formModelOptions = computed(() => {
  const candidate = registeringCandidate.value;
  if (formType.value !== 'candidate' || !candidate) return modelOptions.value;
  return catalog.value.deviceModels
    .filter((item) => item.protocolCodes.includes(candidate.protocolCode))
    .map((item) => ({ label: `${item.vendor} / ${item.name} / ${item.version}`, value: item.code }));
});
const locationOptions = computed(() =>
  locations.value.map((item) => ({
    label: `${item.path || item.name}（${item.code}）`,
    value: item.id,
  })),
);
const batchModelName = computed(
  () => batchRows.value[0]?.suggestedModelName || modelName(batchRows.value[0]?.suggestedModelCode || undefined),
);
const batchLocationOptions = computed(() =>
  batchLocations.value.map((item) => ({
    label: `${item.path || item.name}（${item.code}）`,
    value: item.id,
  })),
);
const selectedModel = computed(() => catalog.value.deviceModels.find((item) => item.code === form.value.modelCode));
const isSikeModel = computed(() => isSikeDeviceModel(form.value.modelCode));
const formProtocolOptions = computed(() =>
  (selectedModel.value?.protocolCodes || []).map((code) => ({
    label: protocolName(code),
    value: code,
  })),
);
const effectiveMetricRows = computed<EffectiveMetricRow[]>(() => {
  const device = bindingDevice.value;
  if (!device) return [];
  const modelMetrics = catalog.value.deviceModels.find((item) => item.code === device.modelCode)?.metrics || [];
  const overrides = new Map(bindings.value.map((item) => [normalizeMetricKey(item.externalMetricKey), item]));
  const consumedKeys = new Set<string>();
  const standards = new Map(catalog.value.standardMetrics.map((metric) => [metric.code, metric]));
  const rows: EffectiveMetricRow[] = modelMetrics.map((metric) => {
    const normalizedKey = normalizeMetricKey(metric.externalMetricKey);
    const override = overrides.get(normalizedKey);
    consumedKeys.add(normalizedKey);
    const metricCode = override?.metricCode || metric.metricCode;
    const standardMetric = standards.get(metricCode);
    return {
      id: override?.id,
      orgId: override?.orgId,
      deviceId: device.id,
      externalMetricKey: override?.externalMetricKey || metric.externalMetricKey,
      metricCode,
      scale: override?.scale ?? metric.scale,
      offset: override?.offset ?? metric.offset,
      valueFieldMappings: override?.valueFieldMappings || metric.valueFieldMappings || {},
      enabled: override?.enabled ?? true,
      name: metricDisplayName(metricCode, standardMetric?.name || metric.name, override?.sourceMetricName),
      unit: standardMetric?.unit || metric.unit,
      sourceMetricName: override?.sourceMetricName,
      sourceUnit: override?.sourceUnit,
      source: override ? 'OVERRIDE' : 'MODEL',
      override,
    } satisfies EffectiveMetricRow;
  });
  bindings.value.forEach((override) => {
    const normalizedKey = normalizeMetricKey(override.externalMetricKey);
    if (consumedKeys.has(normalizedKey)) return;
    const standardMetric = standards.get(override.metricCode || '');
    rows.push({
      ...override,
      name: metricDisplayName(override.metricCode || override.externalMetricKey || '', standardMetric?.name, override.sourceMetricName),
      unit: standardMetric?.unit,
      source: 'DYNAMIC',
      override,
    });
  });
  return rows;
});
const rules = {
  modelCode: { required: true, message: '请选择设备型号', trigger: 'change' },
  protocolCode: { required: true, message: '请选择业务协议', trigger: 'change' },
  code: { required: true, message: '请输入设备编号', trigger: ['blur', 'input'] },
  name: { required: true, message: '请输入设备名称', trigger: ['blur', 'input'] },
  gatewayId: { required: true, message: '请选择所属网关', trigger: 'change' },
  externalDeviceId: { required: true, message: '请输入外部设备标识', trigger: ['blur', 'input'] },
  locationId: { required: true, message: '请选择统一安装地址', trigger: 'change' },
  rfidAntennaCount: {
    validator: (_rule: unknown, value: number | null | undefined) =>
      !hasRadioBusinessRole.value || (Number(value) >= 1 && Number(value) <= 32),
    message: 'RFID 天线数应为 1 到 32',
    trigger: 'change',
  },
};
const candidateRules = {
  ...rules,
  locationId: { required: true, message: '请选择统一安装地址', trigger: 'change' },
};

const columns = [
  { title: '设备名称', key: 'name', minWidth: 200, fixed: 'left', columnType: 'title', ellipsis: { tooltip: true } },
  { title: '设备编号', key: 'code', width: 150 },
  { title: '来源类型', key: 'kind', width: 130, render: (row: DeviceVo) => deviceKindName(row.kind) },
  {
    title: '设备型号',
    key: 'modelName',
    minWidth: 220,
    render: (row: DeviceVo) => row.modelName || modelName(row.modelCode),
  },
  {
    title: '业务角色',
    key: 'businessRoles',
    minWidth: 220,
    render: (row: DeviceVo) => businessRoleText(row.businessRoles),
  },
  { title: '网关', key: 'gatewayName', minWidth: 180 },
  {
    title: '业务协议',
    key: 'protocolCode',
    minWidth: 230,
    ellipsis: { tooltip: true },
    render: (row: DeviceVo) => protocolName(row.protocolCode),
  },
  { title: '外部标识', key: 'externalDeviceId', minWidth: 190 },
  {
    title: '统一安装地址',
    key: 'locationPath',
    minWidth: 210,
    render: (row: DeviceVo) => row.locationPath || row.locationId || '--',
  },
  { title: '最后接收', key: 'lastSeenAt', width: 190, columnType: 'dateTime' },
  {
    title: '设备指标',
    key: 'bindings',
    width: 110,
    fixed: 'right',
    render: (row: DeviceVo) =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          size: 'small',
          disabled: row.kind === 'STANDARD_SOURCE',
          onClick: () => openBindings(row),
        },
        { default: () => '查看/配置' },
      ),
  },
  { title: '操作', key: 'operate', type: 'operate', width: 160, fixed: 'right' },
];

const bindingColumns: DataTableColumns<EffectiveMetricRow> = [
  { title: '指标名称', key: 'name', minWidth: 150 },
  { title: '厂商原名', key: 'sourceMetricName', minWidth: 150, render: (row) => row.sourceMetricName || '--' },
  { title: '设备输出键', key: 'externalMetricKey', minWidth: 180 },
  { title: '统一指标编码', key: 'metricCode', minWidth: 180 },
  { title: '单位', key: 'unit', width: 90, render: (row) => row.unit || '--' },
  {
    title: '字段映射',
    key: 'valueFieldMappings',
    minWidth: 180,
    render: (row) =>
      Object.entries(row.valueFieldMappings || {})
        .map(([source, target]) => `${source}→${target}`)
        .join('、') || '字段编码原样使用',
  },
  {
    title: '启用',
    key: 'enabled',
    width: 90,
    render: (row) => {
      const key = metricBindingKey(row);
      return h(NSwitch, {
        value: row.enabled !== false,
        loading: bindingSavingKeys.value.has(key),
        onUpdateValue: (enabled: boolean) => updateMetricBindingEnabled(row, enabled),
      });
    },
  },
  {
    title: '来源',
    key: 'source',
    width: 110,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          bordered: false,
          type: row.source === 'MODEL' ? 'success' : row.source === 'OVERRIDE' ? 'warning' : 'info',
        },
        { default: () => effectiveMetricSourceName(row.source) },
      ),
  },
  { title: '换算', key: 'scale', width: 160, render: (row) => `× ${row.scale ?? 1} + ${row.offset ?? 0}` },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    fixed: 'right',
    render: (row) =>
      h(
        NSpace,
        { size: 8 },
        {
          default: () => {
            const actions = [
              h(
                NButton,
                { text: true, type: 'primary', size: 'small', onClick: () => editMetricOverride(row) },
                { default: () => (row.override ? '编辑' : '配置覆盖') },
              ),
            ];
            if (row.override && row.source === 'OVERRIDE') {
              actions.push(
                h(
                  NButton,
                  { text: true, type: 'error', size: 'small', onClick: () => removeBinding(row.override!) },
                  { default: () => '删除' },
                ),
              );
            }
            return actions;
          },
        },
      ),
  },
];

const candidateColumns: DataTableColumns<DeviceCandidateVo> = [
  { title: '设备名称', key: 'name', minWidth: 180, render: (row) => row.name || '--' },
  { title: '外部设备标识', key: 'externalDeviceId', minWidth: 190, fixed: 'left' },
  { title: '网关', key: 'gatewayName', minWidth: 180, render: (row) => row.gatewayName || gatewayName(row.gatewayId) },
  {
    title: '推断型号',
    key: 'suggestedModelName',
    minWidth: 240,
    render: (row) => row.suggestedModelName || row.suggestedModelCode || '需人工选择',
  },
  { title: '业务协议', key: 'protocolCode', minWidth: 250, render: (row) => protocolName(row.protocolCode) },
  { title: '型号提示', key: 'modelHint', minWidth: 180, render: (row) => row.modelHint || '--' },
  { title: '发现次数', key: 'seenCount', width: 100 },
  { title: '首次发现', key: 'firstSeenAt', width: 190, render: (row) => formatDateTime(row.firstSeenAt) },
  { title: '最近发现', key: 'lastSeenAt', width: 190, render: (row) => formatDateTime(row.lastSeenAt) },
  {
    title: '操作',
    key: 'actions',
    width: 130,
    fixed: 'right',
    render: (row) =>
      h(
        NSpace,
        { size: 8 },
        {
          default: () => [
            h(
              NButton,
              { text: true, type: 'primary', size: 'small', onClick: () => openCandidateRegistration(row) },
              { default: () => '登记' },
            ),
            h(
              NButton,
              { text: true, type: 'error', size: 'small', onClick: () => ignoreCandidate(row) },
              { default: () => '忽略' },
            ),
          ],
        },
      ),
  },
];

function responsePayload(response: any): any {
  let payload = response;
  for (let depth = 0; depth < 5; depth += 1) {
    if (Array.isArray(payload) || payload === null || typeof payload !== 'object' || !('data' in payload)) {
      return payload;
    }
    payload = payload.data;
  }
  return payload;
}

function responseList<T>(response: any): T[] {
  const payload = responsePayload(response);
  if (Array.isArray(payload)) return payload;
  return Array.isArray(payload?.list) ? payload.list : [];
}
function fetchData(params: Record<string, unknown>) {
  return ingestApi.devicePage({
    ...params,
    gatewayId: query.gatewayId || undefined,
    modelCode: query.modelCode?.length ? query.modelCode : undefined,
    keyword: query.keyword || undefined,
  });
}
function modelName(code?: string) {
  return catalog.value.deviceModels.find((item) => item.code === code)?.name || code || '--';
}
function gatewayName(id?: string) {
  return gateways.value.find((item) => item.id === id)?.name || id || '--';
}
function protocolName(code?: string) {
  if (code === 'EXTERNAL_PUSH_V1') return '外部系统推送';
  return catalog.value.protocols.find((item) => item.code === code)?.name || code || '--';
}
function businessRoleText(values?: RfidDeviceBusinessRole[] | null) {
  if (!values?.length) return '通用采集设备';
  return values
    .map((value) => {
      const label = getDictLabel(businessRoleOptions.value, value);
      return label === '--' ? value : label;
    })
    .join('、');
}
function hasRadioRole(values?: RfidDeviceBusinessRole[] | null) {
  return values?.some((value) => radioBusinessRoles.has(value)) === true;
}
function normalizeMetricKey(key?: string) {
  return key?.trim().toLowerCase() || '';
}
function effectiveMetricSourceName(source: EffectiveMetricSource) {
  if (source === 'MODEL') return '型号预置';
  if (source === 'OVERRIDE') return '设备覆盖';
  return '动态指标';
}
function editMetricOverride(row: EffectiveMetricRow) {
  bindingForm.value = row.override
    ? cloneDeep(row.override)
    : {
        deviceId: bindingDevice.value?.id,
        externalMetricKey: row.externalMetricKey,
        metricCode: row.metricCode,
        scale: row.scale ?? 1,
        offset: row.offset ?? 0,
        valueFieldMappings: row.valueFieldMappings || {},
      };
  bindingFieldMappingsText.value = JSON.stringify(bindingForm.value.valueFieldMappings || {}, null, 2);
}
function changeFormModel(modelCode: string | null) {
  const wasSike = isSikeDeviceModel(form.value.modelCode);
  form.value.modelCode = modelCode || undefined;
  if (wasSike !== isSikeDeviceModel(modelCode)) {
    form.value.instanceConfig = undefined;
    loadSikeFormConfig();
  }
  const protocols = catalog.value.deviceModels.find((item) => item.code === modelCode)?.protocolCodes || [];
  if (!form.value.protocolCode || !protocols.includes(form.value.protocolCode)) {
    form.value.protocolCode = protocols.length === 1 ? protocols[0] : undefined;
  }
}
function deviceKindName(kind?: DeviceVo['kind']) {
  return kind === 'EXTERNAL_DEVICE' ? '外部平台设备' : kind === 'STANDARD_SOURCE' ? '标准业务数据源' : '物理采集设备';
}
async function onAdd() {
  registeringCandidate.value = null;
  formType.value = 'add';
  form.value = { businessRoles: [], rfidAntennaCount: 1 };
  loadSikeFormConfig();
  originalLocationId.value = null;
  locations.value = [];
  formShow.value = true;
}
async function onEdit(row: DeviceVo) {
  registeringCandidate.value = null;
  formType.value = 'edit';
  form.value = cloneDeep(row);
  loadSikeFormConfig(row.instanceConfig);
  originalLocationId.value = row.locationId || null;
  await loadLocations(row.gatewayId);
  formShow.value = true;
}
function onView(row: DeviceVo) {
  viewRow.value = row;
  viewShow.value = true;
}
async function handleSubmit() {
  if (formType.value !== 'candidate' || !registeringCandidate.value) {
    if (locationMoved.value) {
      if (!form.value.locationChangeReason?.trim()) {
        message.warning('修改设备位置时请填写调整原因');
        throw new Error('修改设备位置时请填写调整原因');
      }
      const confirmed = await confirmLocationMove();
      if (!confirmed) throw new Error('已取消位置调整');
    }
    return ingestApi.saveDevice(devicePayload());
  }
  const response = await ingestApi.registerDeviceCandidate(registeringCandidate.value.id, devicePayload());
  const result = responsePayload(response) as DeviceCandidateRegistrationResultVo | undefined;
  message.success(result?.replayPending ? '设备已登记，历史待识别消息将在后台分批重新入队' : '设备已登记');
  await Promise.all([candidateTableRef.value?.handleRefresh?.(), tableRef.value?.handleRefresh?.()]);
  return response;
}

async function changeFormGateway(gatewayId: string | null) {
  form.value.gatewayId = gatewayId || undefined;
  form.value.locationId = undefined;
  await loadLocations(gatewayId || undefined);
}

async function loadLocations(gatewayId?: string) {
  locations.value = [];
  if (!gatewayId) return;
  locationLoading.value = true;
  try {
    locations.value = responseList<MuseumLocationOptionVo>(await ingestApi.locations(gatewayId));
  } finally {
    locationLoading.value = false;
  }
}

function devicePayload(): DeviceVo {
  const payload = { ...form.value };
  if (isSikeDeviceModel(payload.modelCode)) {
    if (!payload.businessRoles?.some((role) => sikeRadioBusinessRoles.has(role))) {
      message.warning('请选择斯科读写器的实际 RFID 读写业务角色');
      throw new Error('请选择斯科读写器的实际 RFID 读写业务角色');
    }
    if (!payload.rfidAntennaCount || payload.rfidAntennaCount > 16) {
      message.warning('斯科读写器天线数必须在 1 至 16 之间');
      throw new Error('斯科读写器天线数必须在 1 至 16 之间');
    }
    const error = validateSikeDeviceConfig(sikeConfig.value, payload.rfidAntennaCount);
    if (error) {
      message.warning(error);
      throw new Error(error);
    }
    payload.instanceConfig = serializeSikeDeviceConfig(sikeConfig.value);
    sikeConfigError.value = '';
  }
  delete payload.siteId;
  delete payload.locationPath;
  return payload;
}

async function fetchCandidates(params: Record<string, unknown>) {
  const filters = normalizeCandidateFilters(params);
  const candidates = responseList<DeviceCandidateVo>(await ingestApi.deviceCandidates(filters));
  candidateAppliedFilters.value = filters;
  candidateCount.value = candidates.length;
  return { data: { list: candidates, total: candidates.length, pages: 1 } };
}

function refreshCandidates() {
  candidateTableRef.value?.handleRefresh?.();
}

function normalizeCandidateFilters(params: Record<string, unknown>): CandidateFilters {
  const normalized = (key: keyof typeof candidateQuery) => {
    const value = params[key];
    return typeof value === 'string' ? value.trim() || undefined : undefined;
  };
  return {
    gatewayId: normalized('gatewayId'),
    externalDeviceId: normalized('externalDeviceId'),
    modelCode: normalized('modelCode'),
    protocolCode: normalized('protocolCode'),
    modelHint: normalized('modelHint'),
  };
}

function confirmDeleteCandidates(selectedRows: DeviceCandidateVo[] = []) {
  const rows = Array.isArray(selectedRows) ? selectedRows : [];
  const allMatched = rows.length === 0;
  if (allMatched && candidateCount.value === 0) return;
  if (rows.length > 1000) {
    message.warning('单次最多删除 1000 台待注册设备');
    return;
  }
  const count = allMatched ? candidateCount.value : rows.length;
  dialog.warning({
    title: allMatched ? (hasCandidateFilters.value ? '清空当前筛选' : '一键清空待注册设备') : '批量删除待注册设备',
    content: allMatched
      ? `确认删除当前${hasCandidateFilters.value ? '筛选条件及' : ''}场馆权限范围内的 ${count} 台待注册设备？原始采集报文仍会保留，后续新报文可能再次生成候选。`
      : `确认删除选中的 ${count} 台待注册设备？原始采集报文仍会保留，后续新报文可能再次生成候选。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      candidateDeleting.value = true;
      try {
        const response = await ingestApi.deleteDeviceCandidates(
          allMatched
            ? { allMatched: true, ...candidateAppliedFilters.value }
            : { candidateIds: rows.map((item) => item.id) },
        );
        const result = responsePayload(response) as DeviceCandidateDeleteResultVo | undefined;
        message.success(`已删除 ${result?.deletedCount ?? count} 台待注册设备`);
        candidateTableRef.value?.clearCheckedRows?.();
        await candidateTableRef.value?.handleRefresh?.();
      } finally {
        candidateDeleting.value = false;
      }
    },
  });
}

async function openBatchRegistration(selectedRows: DeviceCandidateVo[]) {
  const rows = Array.isArray(selectedRows) ? selectedRows : [];
  if (!rows.length) return;
  if (rows.length > 100) {
    message.warning('单次最多登记 100 台设备');
    return;
  }
  if (rows.some((item) => !item.suggestedModelCode)) {
    message.warning('批量登记只支持已唯一识别型号的设备');
    return;
  }
  if (new Set(rows.map((item) => item.suggestedModelCode)).size !== 1) {
    message.warning('请只选择同一识别型号的设备');
    return;
  }
  if (new Set(rows.map((item) => item.orgId)).size !== 1) {
    message.warning('请只选择同一场馆的设备');
    return;
  }
  batchRows.value = rows;
  batchLocationId.value = null;
  batchBusinessRoles.value = [];
  batchRfidAntennaCount.value = 1;
  batchLocations.value = [];
  batchShow.value = true;
  batchLocationLoading.value = true;
  try {
    batchLocations.value = responseList<MuseumLocationOptionVo>(await ingestApi.locations(rows[0].gatewayId));
  } finally {
    batchLocationLoading.value = false;
  }
}

async function submitBatchRegistration() {
  if (!batchLocationId.value) {
    message.warning('请选择统一安装地址');
    return;
  }
  batchSubmitting.value = true;
  try {
    const response = await ingestApi.registerDeviceCandidates({
      candidateIds: batchRows.value.map((item) => item.id),
      locationId: batchLocationId.value,
      businessRoles: isExternalBatch.value ? undefined : batchBusinessRoles.value,
      rfidAntennaCount: !isExternalBatch.value && hasRadioRole(batchBusinessRoles.value) ? batchRfidAntennaCount.value : undefined,
    });
    const result = responsePayload(response) as DeviceCandidateBatchRegistrationResultVo | undefined;
    const registeredMessage = `已登记 ${result?.deviceCount || batchRows.value.length} 台设备`;
    message.success(result?.replayPending ? `${registeredMessage}，历史待识别消息将在后台分批重新入队` : registeredMessage);
    batchShow.value = false;
    candidateTableRef.value?.clearCheckedRows?.();
    await Promise.all([candidateTableRef.value?.handleRefresh?.(), tableRef.value?.handleRefresh?.()]);
  } finally {
    batchSubmitting.value = false;
  }
}

async function openCandidateRegistration(candidate: DeviceCandidateVo) {
  registeringCandidate.value = candidate;
  formType.value = 'candidate';
  form.value = {
    kind: candidate.kind,
    name: candidate.name || undefined,
    modelCode: candidate.suggestedModelCode || undefined,
    gatewayId: candidate.gatewayId,
    protocolCode: candidate.protocolCode,
    externalDeviceId: candidate.externalDeviceId,
    ...(candidate.kind === 'EXTERNAL_DEVICE' ? {} : {
      businessRoles: [],
      rfidHardwareSerial: candidate.externalDeviceId,
      rfidAntennaCount: 1,
    }),
  };
  loadSikeFormConfig();
  originalLocationId.value = null;
  await loadLocations(candidate.gatewayId);
  formShow.value = true;
}

function ignoreCandidate(candidate: DeviceCandidateVo) {
  dialog.warning({
    title: '忽略待注册设备',
    content: `确认忽略外部设备“${candidate.externalDeviceId}”？后续报文仍会保留在待识别消息中。`,
    positiveText: '忽略',
    negativeText: '取消',
    onPositiveClick: async () => {
      await ingestApi.ignoreDeviceCandidate(candidate.id);
      message.success('已忽略');
      refreshCandidates();
    },
  });
}

async function loadBaseData() {
  const [catalogResponse, gatewayResponse] = await Promise.all([ingestApi.catalog(), gatewayApi.page({ pageSize: 0 })]);
  catalog.value = catalogResponse?.data ?? emptyCatalog;
  gateways.value = responseList<GatewayVo>(gatewayResponse);
}
async function openBindings(row: DeviceVo) {
  if (!row.id) return;
  bindingDevice.value = row;
  bindingForm.value = { deviceId: row.id, scale: 1, offset: 0 };
  bindingFieldMappingsText.value = '{}';
  bindingShow.value = true;
  await loadBindings();
}
async function loadBindings() {
  if (!bindingDevice.value?.id) return;
  bindingLoading.value = true;
  try {
    bindings.value = responseList<DeviceMetricBindingVo>(await ingestApi.metricBindings(bindingDevice.value.id));
  } finally {
    bindingLoading.value = false;
  }
}
async function saveBinding() {
  if (!bindingDevice.value?.id || !bindingForm.value.externalMetricKey || !bindingForm.value.metricCode) {
    message.warning('请填写设备输出键和统一指标编码');
    return;
  }
  let valueFieldMappings: Record<string, string>;
  try {
    const parsed = JSON.parse(bindingFieldMappingsText.value || '{}');
    if (
      !parsed ||
      Array.isArray(parsed) ||
      typeof parsed !== 'object' ||
      Object.values(parsed).some((value) => typeof value !== 'string')
    ) {
      throw new Error('invalid');
    }
    valueFieldMappings = parsed as Record<string, string>;
  } catch {
    message.warning('字段映射必须是字符串到字符串的 JSON 对象');
    return;
  }
  await ingestApi.saveMetricBinding({ ...bindingForm.value, valueFieldMappings, deviceId: bindingDevice.value.id });
  message.success('指标覆盖已保存');
  bindingForm.value = { deviceId: bindingDevice.value.id, scale: 1, offset: 0 };
  bindingFieldMappingsText.value = '{}';
  await loadBindings();
}
function metricBindingKey(row: DeviceMetricBindingVo) {
  return row.id || `${row.deviceId || bindingDevice.value?.id}:${normalizeMetricKey(row.externalMetricKey)}`;
}
async function updateMetricBindingEnabled(row: EffectiveMetricRow, enabled: boolean) {
  const key = metricBindingKey(row);
  if (bindingSavingKeys.value.has(key) || enabled === (row.enabled !== false)) return;
  bindingSavingKeys.value = new Set(bindingSavingKeys.value).add(key);
  try {
    if (row.override?.id) {
      await ingestApi.updateMetricBindingEnabled(row.override.id, enabled);
    } else if (bindingDevice.value?.id && row.externalMetricKey && row.metricCode) {
      await ingestApi.saveMetricBinding({
        deviceId: bindingDevice.value.id,
        externalMetricKey: row.externalMetricKey,
        metricCode: row.metricCode,
        scale: row.scale ?? 1,
        offset: row.offset ?? 0,
        valueFieldMappings: row.valueFieldMappings || {},
        enabled,
      });
    }
    await loadBindings();
    message.success(enabled ? `已启用 ${row.name}` : `已停用 ${row.name}`);
  } finally {
    const saving = new Set(bindingSavingKeys.value);
    saving.delete(key);
    bindingSavingKeys.value = saving;
  }
}
async function removeBinding(row: DeviceMetricBindingVo) {
  if (!row.id) return;
  await ingestApi.deleteMetricBinding(row.id);
  message.success('指标覆盖已删除');
  await loadBindings();
}

function confirmLocationMove(): Promise<boolean> {
  return new Promise((resolve) => {
    dialog.warning({
      title: '确认调整设备位置',
      content: `设备将从“${effectiveLocationName(originalLocationId.value)}”调整到“${effectiveLocationName(form.value.locationId || null)}”。历史数据继续保留，并将在趋势中显示位置变化提示。`,
      positiveText: '确认调整',
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    });
  });
}

function effectiveLocationName(id: string | null) {
  if (!id) return '未设置位置';
  const location = locations.value.find((item) => item.id === id);
  return location?.path || location?.name || id;
}

async function openLocationHistory(device: DeviceVo) {
  if (!device.id) return;
  locationHistoryShow.value = true;
  locationHistoryLoading.value = true;
  try {
    locationHistory.value = responseList<MuseumDeviceLocationChangeVo>(
      await ingestApi.deviceLocationChanges(device.id),
    );
  } finally {
    locationHistoryLoading.value = false;
  }
}

const locationHistoryColumns: DataTableColumns<MuseumDeviceLocationChangeVo> = [
  { title: '变更时间', key: 'changedAt', width: 180, render: (row) => formatDateTime(row.changedAt) },
  {
    title: '原位置',
    key: 'fromLocationName',
    minWidth: 140,
    render: (row) => row.fromLocationName || row.fromLocationId || '未设置',
  },
  {
    title: '新位置',
    key: 'toLocationName',
    minWidth: 140,
    render: (row) => row.toLocationName || row.toLocationId || '未设置',
  },
  { title: '原因', key: 'reason', minWidth: 180, render: (row) => row.reason || '--' },
];

watch(activeTab, (tab) => {
  if (tab === 'candidates') refreshCandidates();
});
onMounted(loadBaseData);
</script>

<style scoped>
.device-page,
.device-tabs {
  display: flex;
  width: 100%;
  height: 100%;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.device-tabs :deep(.n-tabs-pane-wrapper),
.device-tabs :deep(.n-tab-pane) {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.pageTable.device-data-table,
.candidate-data-table {
  min-height: 0;
  flex: 1;
}

.candidate-data-table {
  width: 100%;
  height: 100%;
}

.pageTable.device-data-table {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.pageTable.device-data-table :deep(.n-card-content) {
  min-height: 0;
  overflow: hidden !important;
}

.pageTable.device-data-table :deep(.n-data-table) {
  min-height: 0;
  flex: 1 1 0;
}

.location-history-button {
  margin-top: 14px;
}

.location-history-alert {
  margin-bottom: 14px;
}

.sike-config-tip {
  margin-bottom: 14px;
}

.sike-field-hint {
  margin-left: 10px;
  opacity: 0.7;
}

.sike-probe-button {
  margin-left: 10px;
}
</style>
