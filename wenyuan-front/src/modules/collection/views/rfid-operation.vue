<template>
  <div class="rfid-operation">
    <NAlert v-if="cardOnly && !canManageCards" type="error" show-icon class="form-tip">
      当前账号没有人员卡管理权限，请使用获授权的管理员账号登录。
    </NAlert>
    <NAlert v-else-if="cardOnly" type="info" :bordered="false" class="form-tip">
      在“人员卡”中刷卡录入卡号并绑定系统用户。发卡完成后请退出管理员账号，再从“RFID作业入口”获取工作站快捷方式，刷卡进入借还台。
    </NAlert>
    <NTabs
      v-if="!cardOnly || canManageCards"
      v-model:value="activeTab"
      type="card"
      class="rfid-tabs"
      pane-wrapper-style="display:flex;flex:1;min-height:0;overflow:hidden;"
      pane-style="display:flex;flex:1;min-height:0;overflow:hidden;"
    >
      <NTabPane v-if="!cardOnly" name="workstations" tab="工作站" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="stationTableRef"
          title="工作站"
          class="rfid-page-table"
          :columns="stationColumns"
          :fetch-data="fetchStations"
          :params="stationQuery"
          :scroll-x="1100"
          :show-query-reset-button="true"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          @add="openStationForm"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="stationQuery.keyword" clearable placeholder="编码 / 名称" />
            </NFormItem>
            <NFormItem label="业务能力" path="capability">
              <HDictSelect v-model:value="stationQuery.capability" type="col_rfid_workstation_capability" clearable />
            </NFormItem>
            <NFormItem label="站点状态" path="status">
              <HDictSelect v-model:value="stationQuery.status" type="col_rfid_workstation_status" clearable />
            </NFormItem>
          </template>

          <HPageForm
            v-model:show="stationFormShow"
            :model="stationForm"
            :rules="stationRules"
            :title="stationFormTitle"
            :ref-table="stationTableRef"
            :handle-submit="saveStation"
            label-width="110"
          >
            <NFormItem v-if="stationForm.id" label="编码">
              <NInput :value="stationForm.code" disabled />
            </NFormItem>
            <NFormItem label="名称" path="name"><NInput v-model:value="stationForm.name" /></NFormItem>
            <NFormItem label="默认能力" path="defaultCapability">
              <NSelect
                v-model:value="stationForm.defaultCapability"
                :options="selectedCapabilityOptions"
                clearable
                placeholder="可不指定，进入工作台后选择"
              />
            </NFormItem>
            <NFormItem label="业务能力" path="capabilities">
              <HDictSelect
                v-model:value="stationForm.capabilities"
                type="col_rfid_workstation_capability"
                multiple
                return-array
                clearable
                placeholder="未启用时可稍后配置"
              />
            </NFormItem>
            <NFormItem label="安装位置" path="locationId">
              <NSelect v-model:value="stationForm.locationId" :options="locationOptions" filterable clearable />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('BORROW_RETURN', 'INVENTORY')" label="RFID 读取" path="rfidReadDeviceIds">
              <NSelect
                v-model:value="stationForm.rfidReadDeviceIds"
                :options="deviceOptionsByPurpose.RFID_READ"
                multiple
                filterable
                clearable
                placeholder="可配置多台，第一台为默认设备"
              />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('TAG_ISSUE')" label="RFID 写入" path="rfidWriteDeviceIds">
              <NSelect
                v-model:value="stationForm.rfidWriteDeviceIds"
                :options="deviceOptionsByPurpose.RFID_WRITE"
                multiple
                filterable
                clearable
                placeholder="可配置多台，第一台为默认设备"
              />
            </NFormItem>
            <NAlert v-if="stationUsesSike" type="info" :bordered="false">
              斯科 C5/5A 由此工作站电脑上的浏览器连接本机 WebSocket
              服务。请先在设备登记中填写各设备的服务地址和连接参数， 再在该电脑打开 RFID
              工作台；管理页面不会连接管理员电脑上的读写器。
            </NAlert>
            <NFormItem v-if="hasAnyCapability('TAG_PRINT')" label="标签打印" path="tagPrinterDeviceIds">
              <NSelect
                v-model:value="stationForm.tagPrinterDeviceIds"
                :options="deviceOptionsByPurpose.TAG_PRINT"
                multiple
                filterable
                clearable
              />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('DOCUMENT_PRINT')" label="单据打印" path="documentPrinterDeviceIds">
              <NSelect
                v-model:value="stationForm.documentPrinterDeviceIds"
                :options="deviceOptionsByPurpose.DOCUMENT_PRINT"
                multiple
                filterable
                clearable
              />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="通道读取" path="passageDeviceIds">
              <NSelect
                v-model:value="stationForm.passageDeviceIds"
                :options="deviceOptionsByPurpose.PASSAGE_READ"
                multiple
                filterable
                clearable
                placeholder="可配置多台，第一台为默认设备"
              />
            </NFormItem>
            <NFormItem label="人员卡读取" path="cardReaderDeviceIds">
              <NSelect
                v-model:value="stationForm.cardReaderDeviceIds"
                :options="deviceOptionsByPurpose.CARD_READ"
                multiple
                filterable
                clearable
                placeholder="可选；仅显示已启用的人员卡读卡器"
              />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="进入天线" path="inAntennas">
              <NSelect v-model:value="stationForm.inAntennas" :options="antennaOptions" multiple clearable />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="离开天线" path="outAntennas">
              <NSelect v-model:value="stationForm.outAntennas" :options="antennaOptions" multiple clearable />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="去抖时间" path="passageDebounceSeconds">
              <NInputNumber v-model:value="stationForm.passageDebounceSeconds" :min="1" :max="60" :precision="0">
                <template #suffix>秒</template>
              </NInputNumber>
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="报警输出" path="alarmOutputIndex">
              <NInputNumber v-model:value="stationForm.alarmOutputIndex" :min="1" :max="32" :precision="0" />
            </NFormItem>
            <NFormItem v-if="hasAnyCapability('PASSAGE_MONITOR')" label="报警时长" path="alarmDurationMillis">
              <NInputNumber
                v-model:value="stationForm.alarmDurationMillis"
                :min="100"
                :max="60000"
                :step="100"
                :precision="0"
              >
                <template #suffix>毫秒</template>
              </NInputNumber>
            </NFormItem>
            <NFormItem label="启用" path="enabled" feedback="启用时会校验每项业务能力所需外设；停用时可先保存草稿。">
              <NSwitch v-model:value="stationForm.enabled" />
            </NFormItem>
            <NAlert v-if="hasUnsupportedRuntimeCapability" type="warning" show-icon>
              标签打印和单据打印目前仅支持设备登记与绑定，运行链路完成前工作站只能停用保存。
            </NAlert>
          </HPageForm>

          <NModal v-model:show="tokenShow" preset="card" title="工作站注册令牌" style="width: 800px">
            <NAlert type="warning" show-icon>
              注册令牌只显示一次，请在对应工作站电脑打开下方地址完成绑定：<NCode>{{ oneTimeToken }}</NCode>
            </NAlert>
            <NFormItem label="本机绑定地址" class="client-url-field">
              <NInput :value="tokenStation ? workstationClientUrl(tokenStation) : ''" readonly />
            </NFormItem>
            <NSpace>
              <NButton
                type="primary"
                :disabled="!tokenStation"
                @click="tokenStation && openWorkstationEnrollment(tokenStation)"
                >前往本机绑定</NButton
              >
              <NButton :disabled="!tokenStation" @click="tokenStation && copyEnrollmentAddress(tokenStation)"
                >复制绑定地址</NButton
              >
            </NSpace>
          </NModal>
        </HPageDataTable>
      </NTabPane>

      <NTabPane v-if="canManageCards" name="cards" tab="人员卡" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="cardTableRef"
          title="人员卡"
          class="rfid-page-table"
          :columns="cardColumns"
          :fetch-data="fetchCards"
          :params="cardQuery"
          :show-query-reset-button="true"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
          @add="openCardForm"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="cardQuery.keyword" clearable placeholder="卡号后四位 / 用户" />
            </NFormItem>
            <NFormItem label="卡片状态" path="status">
              <HDictSelect v-model:value="cardQuery.status" type="col_rfid_operator_card_status" clearable />
            </NFormItem>
            <NFormItem label="能力范围" path="capability">
              <HDictSelect v-model:value="cardQuery.capability" type="col_rfid_workstation_capability" clearable />
            </NFormItem>
          </template>

          <HPageForm
            v-model:show="cardFormShow"
            :model="cardForm"
            :rules="cardRules"
            title="发放人员卡"
            :ref-table="cardTableRef"
            :handle-submit="saveCard"
            label-width="110"
          >
            <NAlert type="info" :bordered="false" class="form-tip">
              卡号由读卡器录入；人员必须从系统用户中选择，有效期使用日期时间组件。
            </NAlert>
            <NFormItem label="卡号" path="cardUid"
              ><NInput
                ref="cardUidInputRef"
                :value="cardForm.cardUid"
                type="password"
                autocomplete="off"
                placeholder="请刷卡；HID 读卡器会自动录入"
                clearable
                @update:value="updateCardUid"
            /></NFormItem>
            <NFormItem label="用户" path="userId"
              ><UserSelect v-model:value="cardForm.userId" placeholder="选择系统用户"
            /></NFormItem>
            <NFormItem label="允许能力" path="allowedCapabilities">
              <HDictSelect
                v-model:value="cardForm.allowedCapabilities"
                type="col_rfid_workstation_capability"
                multiple
                return-array
              />
            </NFormItem>
            <NFormItem label="有效时间" path="validity">
              <NDatePicker v-model:value="cardForm.validity" type="datetimerange" clearable />
            </NFormItem>
          </HPageForm>
        </HPageDataTable>
      </NTabPane>

      <NTabPane v-if="!cardOnly" name="tags" tab="藏品标签" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="tagTableRef"
          title="藏品标签"
          class="rfid-page-table"
          :columns="tagColumns"
          :fetch-data="fetchTags"
          :params="tagQuery"
          :scroll-x="1000"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="关联藏品" path="collectionId">
              <CollectionSelect v-model:value="tagQuery.collectionId" placeholder="选择藏品" />
            </NFormItem>
            <NFormItem label="标签状态" path="status">
              <HDictSelect v-model:value="tagQuery.status" type="col_rfid_tag_status" clearable />
            </NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane v-if="!cardOnly" name="sessions" tab="扫描会话" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="sessionTableRef"
          title="扫描会话"
          class="rfid-page-table"
          :columns="sessionColumns"
          :fetch-data="fetchSessions"
          :params="sessionQuery"
          :pagination="{ pageSize: 30 }"
          :scroll-x="1300"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="sessionQuery.keyword" clearable placeholder="会话号 / 业务单据 / 设备" />
            </NFormItem>
            <NFormItem label="工作站" path="workstationId">
              <NSelect v-model:value="sessionQuery.workstationId" :options="workstationOptions" filterable clearable />
            </NFormItem>
            <NFormItem label="作业类型" path="type">
              <HDictSelect v-model:value="sessionQuery.type" type="col_rfid_scan_session_type" clearable />
            </NFormItem>
            <NFormItem label="作业状态" path="state">
              <HDictSelect v-model:value="sessionQuery.state" type="col_rfid_scan_session_state" clearable />
            </NFormItem>
            <NFormItem label="创建时间" path="createdRange">
              <NDatePicker v-model:value="sessionQuery.createdRange" type="datetimerange" clearable />
            </NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>

      <NTabPane v-if="!cardOnly" name="tagOperations" tab="标签操作记录" display-directive="show">
        <HPageDataTable auto-scroll-x
          ref="operationTableRef"
          title="标签操作记录"
          class="rfid-page-table"
          :columns="operationColumns"
          :fetch-data="fetchTagOperations"
          :params="operationQuery"
          :pagination="{ pageSize: 30 }"
          :scroll-x="1400"
          :show-query-reset-button="true"
          :is-add="false"
          :is-edit="false"
          :is-copy="false"
          :is-delete="false"
          :is-export="false"
          :show-operate="false"
        >
          <template #queryForm>
            <NFormItem label="检索内容" path="keyword">
              <NInput v-model:value="operationQuery.keyword" clearable placeholder="EPC / TID / 结果说明" />
            </NFormItem>
            <NFormItem label="操作类型" path="operationType">
              <HDictSelect v-model:value="operationQuery.operationType" type="col_rfid_tag_operation_type" clearable />
            </NFormItem>
            <NFormItem label="操作结果" path="success">
              <HDictSelect v-model:value="operationQuery.success" type="col_rfid_operation_result" clearable />
            </NFormItem>
            <NFormItem label="发生时间" path="occurredRange">
              <NDatePicker v-model:value="operationQuery.occurredRange" type="datetimerange" clearable />
            </NFormItem>
          </template>
        </HPageDataTable>
      </NTabPane>
    </NTabs>
    <RfidSensitiveAuthModal ref="sensitiveAuthRef" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { breakpointsTailwind, useBreakpoints } from '@vueuse/core';
import {
  NAlert,
  NButton,
  NCode,
  NDatePicker,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTabPane,
  NTabs,
  useDialog,
  useMessage,
} from 'naive-ui';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import { ingestApi } from '@/modules/museum-device/api/ingest';
import { museumRfidApi } from '@/modules/museum-device/api/rfid';
import { getDictLabel, useDict } from '@/utils/dict';
import { useAuthStore } from '@/store';
import { rfidApi } from '../api/rfid';
import CollectionSelect from '../components/CollectionSelect.vue';
import RfidSensitiveAuthModal from '../components/rfid/RfidSensitiveAuthModal.vue';
import { canonicalRfidWorkstationUrl } from '../utils/rfidWorkstationUrl';
import type {
  RfidTag,
  RfidScanSession,
  RfidTagOperation,
  RfidWorkstation,
  RfidWorkstationCapability,
  RfidWorkstationDeviceBinding,
  RfidWorkstationDevicePurpose,
} from '../typings/rfid';

const message = useMessage();
const dialog = useDialog();
const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const desktopTable = useBreakpoints(breakpointsTailwind).greaterOrEqual('md');
const operationTabs = new Set(['workstations', 'cards', 'tags', 'sessions', 'tagOperations']);
const props = withDefaults(defineProps<{ initialTab?: string }>(), { initialTab: 'workstations' });
const cardOnly = computed(() => route.query.mode === 'cards');
const canManageCards = computed(() => authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_RFID_CARD_MANAGE'));
const activeTab = ref(cardOnly.value ? 'cards' : operationTabs.has(props.initialTab) ? props.initialTab : 'workstations');
const stationTableRef = ref<any>(null);
const cardTableRef = ref<any>(null);
const tagTableRef = ref<any>(null);
const sessionTableRef = ref<any>(null);
const operationTableRef = ref<any>(null);
const stationFormShow = ref(false);
const stationFormTitle = ref('新增工作站');
const cardFormShow = ref(false);
const cardUidInputRef = ref<any>(null);
const tokenShow = ref(false);
const rfidProfiles = ref<any[]>([]);
const locations = ref<any[]>([]);
const workstationRows = ref<RfidWorkstation[]>([]);
const oneTimeToken = ref('');
const tokenStation = ref<RfidWorkstation | null>(null);
const sensitiveAuthRef = ref<InstanceType<typeof RfidSensitiveAuthModal>>();
const { options: capabilityOptions } = useDict('col_rfid_workstation_capability');
const { options: purposeOptions } = useDict('col_rfid_workstation_device_purpose');
const { options: directionOptions } = useDict('col_rfid_direction');
const stationForm = reactive({
  id: null as string | null,
  code: '',
  name: '',
  defaultCapability: 'BORROW_RETURN' as RfidWorkstationCapability | null,
  capabilities: ['BORROW_RETURN'] as RfidWorkstationCapability[],
  locationId: null as string | null,
  rfidReadDeviceIds: [] as string[],
  rfidWriteDeviceIds: [] as string[],
  cardReaderDeviceIds: [] as string[],
  tagPrinterDeviceIds: [] as string[],
  documentPrinterDeviceIds: [] as string[],
  passageDeviceIds: [] as string[],
  inAntennas: [] as number[],
  outAntennas: [] as number[],
  passageDebounceSeconds: 3,
  alarmOutputIndex: 1,
  alarmDurationMillis: 3000,
  enabled: false,
});
const cardForm = reactive({
  cardUid: '',
  userId: null as string | null,
  allowedCapabilities: ['BORROW_RETURN'] as RfidWorkstationCapability[],
  validity: defaultCardValidity() as [number, number] | null,
});
const stationQuery = reactive({
  keyword: null as string | null,
  capability: null as RfidWorkstationCapability | null,
  status: null as 'ENABLED' | 'DISABLED' | null,
});
const cardQuery = reactive({
  keyword: null as string | null,
  status: null as string | null,
  capability: null as RfidWorkstationCapability | null,
});
const tagQuery = reactive({ collectionId: null as string | null, status: null as string | null });
const sessionQuery = reactive({
  keyword: null as string | null,
  workstationId: null as string | null,
  type: null as string | null,
  state: null as string | null,
  createdRange: null as [number, number] | null,
});
const operationQuery = reactive({
  keyword: null as string | null,
  operationType: null as string | null,
  success: null as string | null,
  occurredRange: null as [number, number] | null,
});
const stationRules = {
  name: { required: true, message: '请输入工作站名称' },
  capabilities: {
    validator: () => !stationForm.enabled || stationForm.capabilities.length > 0,
    message: '启用工作站前请至少选择一项业务能力',
  },
  rfidReadDeviceIds: requiredDevicesFor(['BORROW_RETURN', 'INVENTORY'], '请选择 RFID 读取设备'),
  rfidWriteDeviceIds: requiredDevicesFor(['TAG_ISSUE'], '请选择 RFID 写入设备'),
  tagPrinterDeviceIds: requiredDevicesFor(['TAG_PRINT'], '请选择标签打印机'),
  documentPrinterDeviceIds: requiredDevicesFor(['DOCUMENT_PRINT'], '请选择单据打印机'),
  passageDeviceIds: requiredDevicesFor(['PASSAGE_MONITOR'], '请选择通道读取设备'),
  inAntennas: {
    validator: () => !stationForm.enabled || !hasAnyCapability('PASSAGE_MONITOR') || stationForm.inAntennas.length > 0,
    message: '请选择进入天线',
  },
  outAntennas: {
    validator: () =>
      !stationForm.enabled ||
      !hasAnyCapability('PASSAGE_MONITOR') ||
      (stationForm.outAntennas.length > 0 &&
        !stationForm.inAntennas.some((antenna) => stationForm.outAntennas.includes(antenna))),
    message: '请选择离开天线，且不能与进入天线重复',
  },
};
const cardRules = {
  cardUid: { required: true, message: '请刷卡录入卡号' },
  userId: { required: true, message: '请选择系统用户' },
  allowedCapabilities: { type: 'array', required: true, message: '请选择允许使用的业务能力' },
  validity: {
    validator: () => Boolean(cardForm.validity && cardForm.validity[1] > cardForm.validity[0]),
    message: '请选择有效时间，且失效时间必须晚于生效时间',
  },
};

const compatibleRoles: Record<RfidWorkstationDevicePurpose, string[]> = {
  RFID_READ: ['DESK_READER', 'HANDHELD', 'TAG_WRITER'],
  RFID_WRITE: ['TAG_WRITER'],
  CARD_READ: ['OPERATOR_CARD_READER'],
  TAG_PRINT: ['TAG_PRINTER'],
  DOCUMENT_PRINT: ['DOCUMENT_PRINTER'],
  PASSAGE_READ: ['WAREHOUSE_GATE', 'MUSEUM_GATE'],
};
const selectedCapabilityOptions = computed(() =>
  capabilityOptions.value.filter((option: any) => stationForm.capabilities.includes(option.value)),
);
const hasUnsupportedRuntimeCapability = computed(() =>
  stationForm.capabilities.some((item) => ['TAG_PRINT', 'DOCUMENT_PRINT'].includes(item)),
);
const stationUsesSike = computed(() =>
  [...stationForm.rfidReadDeviceIds, ...stationForm.rfidWriteDeviceIds].some((id) =>
    rfidProfiles.value.some((profile) => profile.deviceId === id && profile.protocolCode === 'SIKE_RFID_WS_V1'),
  ),
);
const deviceOptionsByPurpose = computed(
  () =>
    Object.fromEntries(
      (Object.keys(compatibleRoles) as RfidWorkstationDevicePurpose[]).map((purpose) => [
        purpose,
        rfidProfiles.value
          .filter(
            (profile) =>
              profile.enabled && profileRoles(profile).some((role) => compatibleRoles[purpose].includes(role)),
          )
          .map((profile) => ({ label: profileLabel(profile.deviceId), value: profile.deviceId })),
      ]),
    ) as Record<RfidWorkstationDevicePurpose, Array<{ label: string; value: string }>>,
);
const locationOptions = computed(() =>
  locations.value
    .filter((item) => item.id)
    .map((item) => ({
      label: `${item.path || item.name || item.code}${item.code ? `（${item.code}）` : ''}`,
      value: item.id,
    })),
);
const workstationOptions = computed(() =>
  workstationRows.value.map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);
const antennaOptions = computed(() => {
  const count = Number(
    rfidProfiles.value.find((profile) => profile.deviceId === stationForm.passageDeviceIds[0])?.antennaCount || 0,
  );
  return Array.from({ length: count }, (_, index) => ({ label: `天线 ${index + 1}`, value: index + 1 }));
});

function antennaDirections() {
  const result: Record<number, 'IN' | 'OUT'> = {};
  stationForm.inAntennas.forEach((n) => {
    result[n] = 'IN';
  });
  stationForm.outAntennas.forEach((n) => {
    result[n] = 'OUT';
  });
  return result;
}
function hasAnyCapability(...capabilities: RfidWorkstationCapability[]) {
  return capabilities.some((capability) => stationForm.capabilities.includes(capability));
}
function requiredDevicesFor(capabilities: RfidWorkstationCapability[], messageText: string) {
  return {
    validator: (_rule: unknown, value: string[]) =>
      !stationForm.enabled || !capabilities.some((capability) => hasAnyCapability(capability)) || value.length > 0,
    message: messageText,
  };
}
const purposeFormFields: Record<RfidWorkstationDevicePurpose, keyof typeof stationForm> = {
  RFID_READ: 'rfidReadDeviceIds',
  RFID_WRITE: 'rfidWriteDeviceIds',
  CARD_READ: 'cardReaderDeviceIds',
  TAG_PRINT: 'tagPrinterDeviceIds',
  DOCUMENT_PRINT: 'documentPrinterDeviceIds',
  PASSAGE_READ: 'passageDeviceIds',
};
function buildDeviceBindings(data: typeof stationForm): RfidWorkstationDeviceBinding[] {
  return (Object.entries(purposeFormFields) as Array<[RfidWorkstationDevicePurpose, keyof typeof stationForm]>).flatMap(
    ([purpose, field]) =>
      ((data[field] as string[]) || []).map((deviceId, index) => ({
        deviceId,
        purpose,
        isDefault: index === 0,
        priority: index,
        enabled: true,
      })),
  );
}
function bindingDeviceIds(row: RfidWorkstation, purpose: RfidWorkstationDevicePurpose) {
  return (row.deviceBindings || [])
    .filter((binding) => binding.purpose === purpose && binding.enabled)
    .sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.priority - b.priority)
    .map((binding) => binding.deviceId);
}
function capabilityLabel(capability: RfidWorkstationCapability) {
  return dictLabel(capabilityOptions.value, capability);
}
function purposeLabel(purpose: RfidWorkstationDevicePurpose) {
  return dictLabel(purposeOptions.value, purpose);
}
function directionLabel(direction: string) {
  return dictLabel(directionOptions.value, direction);
}
function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}
async function fetchStations(params: any) {
  const keyword = params.keyword?.trim().toLowerCase();
  const rows = responseRows(await rfidApi.workstations())
    .filter((row: RfidWorkstation) => {
      if (params.capability && !row.capabilities?.includes(params.capability)) return false;
      if (params.status === 'ENABLED' && !row.enabled) return false;
      if (params.status === 'DISABLED' && row.enabled) return false;
      return !keyword || [row.code, row.name].some((value) => value?.toLowerCase().includes(keyword));
    })
    .map((row: RfidWorkstation) => ({ ...row, enabledStatus: row.enabled ? 'ENABLED' : 'DISABLED' }));
  workstationRows.value = rows;
  return pageResponse(rows, params);
}
async function fetchCards(params: any) {
  const keyword = params.keyword?.trim().toLowerCase();
  const rows = responseRows(await rfidApi.cards()).filter((row: any) => {
    if (params.status && row.status !== params.status) return false;
    if (params.capability && !row.allowedCapabilities?.includes(params.capability)) return false;
    return (
      !keyword || [row.cardLast4, row.userId, row.userName].some((value) => value?.toLowerCase().includes(keyword))
    );
  });
  return pageResponse(rows, params);
}
async function fetchTags(params: any) {
  return rfidApi.tags(params);
}
async function fetchSessions(params: any) {
  return rfidApi.sessions({
    ...params,
    createdRange: undefined,
    createdAtStart: params.createdRange?.[0],
    createdAtEnd: params.createdRange?.[1],
  });
}
async function fetchTagOperations(params: any) {
  return rfidApi.tagOperations({
    ...params,
    occurredRange: undefined,
    occurredAtStart: params.occurredRange?.[0],
    occurredAtEnd: params.occurredRange?.[1],
  });
}
async function loadReferences() {
  const [profileResp, locationResp, stationResp]: any[] = await Promise.all([
    museumRfidApi.profiles(),
    ingestApi.locationCatalog({}),
    rfidApi.workstations(),
  ]);
  rfidProfiles.value = profileResp?.data || [];
  locations.value = locationResp?.data || [];
  workstationRows.value = responseRows(stationResp);
}
function profileRoles(profile: any): string[] {
  return Array.isArray(profile.roles) && profile.roles.length ? profile.roles : profile.role ? [profile.role] : [];
}
function profileLabel(deviceId: string) {
  const profile = rfidProfiles.value.find((item) => item.deviceId === deviceId);
  if (!profile) return deviceId;
  const access =
    profile.protocolCode === 'SIKE_RFID_WS_V1'
      ? '斯科本机 WebSocket'
      : profile.protocolCode === 'CK_RFID_C5_V1'
        ? 'C5 直连 TCP'
        : profile.protocolCode || '其他设备';
  return `${profile.deviceName || profile.deviceCode || '未命名 RFID 设备'}（${profile.deviceCode || profile.hardwareSerial} · ${access}）`;
}
async function saveStation(data: any) {
  if (data.enabled && data.capabilities?.some((item: string) => ['TAG_PRINT', 'DOCUMENT_PRINT'].includes(item))) {
    message.warning('标签打印和单据打印运行链路尚未实现，请停用工作站后保存配置');
    throw new Error('RFID 打印运行链路尚未实现');
  }
  const deviceBindings = buildDeviceBindings(data);
  const resp: any = await rfidApi.saveWorkstation({
    id: data.id,
    name: data.name,
    defaultCapability: data.defaultCapability,
    capabilities: data.capabilities,
    locationId: data.locationId,
    deviceBindings,
    antennaDirections: antennaDirections(),
    passageDebounceSeconds: data.passageDebounceSeconds,
    alarmOutputIndex: data.alarmOutputIndex,
    alarmDurationMillis: data.alarmDurationMillis,
    enabled: data.enabled,
  });
  message.success(data.id ? '工作站已更新' : '工作站已创建');
  oneTimeToken.value = resp?.data?.enrollmentToken || '';
  if (oneTimeToken.value) {
    tokenStation.value = {
      id: resp?.data?.id,
      orgId: '',
      code: resp?.data?.code,
      name: data.name,
      defaultCapability: data.defaultCapability,
      locationId: data.locationId,
      capabilities: data.capabilities,
      deviceBindings,
      antennaDirections: antennaDirections(),
      passageDebounceSeconds: data.passageDebounceSeconds,
      alarmOutputIndex: data.alarmOutputIndex,
      alarmDurationMillis: data.alarmDurationMillis,
      enabled: data.enabled,
    };
    tokenShow.value = true;
  }
  return resp;
}
let stationFormSyncing = false;
function assignStationForm(values: Partial<typeof stationForm>) {
  stationFormSyncing = true;
  try {
    Object.assign(stationForm, values);
  } finally {
    stationFormSyncing = false;
  }
}
function resetStationForm() {
  assignStationForm({
    id: null,
    code: '',
    name: '',
    defaultCapability: 'BORROW_RETURN',
    capabilities: ['BORROW_RETURN'],
    locationId: null,
    rfidReadDeviceIds: [],
    rfidWriteDeviceIds: [],
    cardReaderDeviceIds: [],
    tagPrinterDeviceIds: [],
    documentPrinterDeviceIds: [],
    passageDeviceIds: [],
    inAntennas: [],
    outAntennas: [],
    passageDebounceSeconds: 3,
    alarmOutputIndex: 1,
    alarmDurationMillis: 3000,
    enabled: false,
  });
}
function openStationForm() {
  resetStationForm();
  stationFormTitle.value = '新增工作站';
  stationFormShow.value = true;
}
function editStation(row: RfidWorkstation) {
  const directions = row.antennaDirections || {};
  assignStationForm({
    id: row.id,
    code: row.code,
    name: row.name,
    defaultCapability: row.defaultCapability || null,
    capabilities: [...(row.capabilities || [])],
    locationId: row.locationId || null,
    rfidReadDeviceIds: bindingDeviceIds(row, 'RFID_READ'),
    rfidWriteDeviceIds: bindingDeviceIds(row, 'RFID_WRITE'),
    cardReaderDeviceIds: bindingDeviceIds(row, 'CARD_READ'),
    tagPrinterDeviceIds: bindingDeviceIds(row, 'TAG_PRINT'),
    documentPrinterDeviceIds: bindingDeviceIds(row, 'DOCUMENT_PRINT'),
    passageDeviceIds: bindingDeviceIds(row, 'PASSAGE_READ'),
    passageDebounceSeconds: row.passageDebounceSeconds ?? 3,
    alarmOutputIndex: row.alarmOutputIndex ?? 1,
    alarmDurationMillis: row.alarmDurationMillis ?? 3000,
    enabled: row.enabled,
    inAntennas: Object.entries(directions)
      .filter(([, direction]) => direction === 'IN')
      .map(([antenna]) => Number(antenna)),
    outAntennas: Object.entries(directions)
      .filter(([, direction]) => direction === 'OUT')
      .map(([antenna]) => Number(antenna)),
  });
  stationFormTitle.value = `编辑工作站「${row.name}」`;
  stationFormShow.value = true;
}
async function resetToken(row: RfidWorkstation) {
  const resp: any = await rfidApi.resetToken(row.id);
  oneTimeToken.value = resp?.data?.enrollmentToken || '';
  tokenStation.value = row;
  tokenShow.value = true;
  message.warning('旧工作站浏览器凭证已失效');
}

function workstationClientUrl(row: RfidWorkstation) {
  const href = router.resolve({ path: '/rfid-kiosk', query: { stationCode: row.code } }).href;
  return canonicalRfidWorkstationUrl(href, window.location.href);
}

function openWorkstationEnrollment(row: RfidWorkstation) {
  window.open(workstationClientUrl(row), '_blank', 'noopener,noreferrer');
}

async function copyEnrollmentAddress(row: RfidWorkstation) {
  await navigator.clipboard.writeText(workstationClientUrl(row));
  message.success('本机绑定地址已复制');
}

async function openCardForm() {
  Object.assign(cardForm, {
    cardUid: '',
    userId: null,
    allowedCapabilities: ['BORROW_RETURN'],
    validity: defaultCardValidity(),
  });
  cardFormShow.value = true;
  await nextTick();
  cardUidInputRef.value?.focus?.();
}
function updateCardUid(value: string) {
  cardForm.cardUid = value.replace(/[\s:-]/g, '').toUpperCase();
}
async function saveCard(data: any) {
  const validity = data.validity as [number, number];
  const resp = await rfidApi.saveCard({
    cardUid: data.cardUid,
    userId: data.userId,
    allowedCapabilities: data.allowedCapabilities,
    validFrom: validity[0],
    validUntil: validity[1],
  });
  message.success('人员卡已发放');
  return resp;
}
async function revokeCard(row: any) {
  await rfidApi.updateCardStatus(row.id, 'REVOKED', '管理端注销');
  cardTableRef.value?.handleRefresh?.();
}
async function retireTag(row: RfidTag) {
  dialog.warning({
    title: '报废标签',
    content: `确认报废 EPC ${row.epc}？该操作会解除藏品当前标签投影。`,
    positiveText: '继续认证',
    negativeText: '取消',
    onPositiveClick: async () => {
      const grant = await sensitiveAuthRef.value?.authorize('RFID_TAG_RETIRE', `RFID_TAG:${row.id}`);
      if (!grant) return;
      await rfidApi.retireTag(row.id, '管理端报废', grant);
      tagTableRef.value?.handleRefresh?.();
    },
  });
}
function terminateSession(row: RfidScanSession) {
  let reason = '';
  dialog.warning({
    title: '强制终止扫描会话',
    content: () =>
      h(NInput, {
        type: 'textarea',
        placeholder: '请输入终止原因',
        autosize: { minRows: 3, maxRows: 5 },
        'onUpdate:value': (value: string) => {
          reason = value;
        },
      }),
    positiveText: '确认终止',
    negativeText: '取消',
    onPositiveClick: async () => {
      if (!reason.trim()) {
        message.warning('请输入终止原因');
        return false;
      }
      await rfidApi.forceTerminateSession(row.id, reason.trim());
      message.success('扫描会话已终止');
      sessionTableRef.value?.handleRefresh?.();
      return true;
    },
  });
}

const stationColumns = computed(() => [
  // 窄窗口不同时固定左右两侧，避免名称列遮住编辑等操作。
  { ellipsis: { tooltip: true }, width: 150, title: '名称', key: 'name', minWidth: 150, resizable: true, fixed: desktopTable.value ? 'left' : undefined },
  { title: '编码', key: 'code', width: 160, ellipsis: { tooltip: true }, resizable: true },
  { ellipsis: { tooltip: true }, resizable: true, width: 240,
    title: '业务能力',
    key: 'capabilities',
    minWidth: 240,
    render: (row: RfidWorkstation) => row.capabilities?.map(capabilityLabel).join('，') || '--',
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 260,
    title: '外设配置',
    key: 'deviceBindings',
    minWidth: 260,
    render: (row: RfidWorkstation) => {
      const bindings = row.deviceBindings?.filter((binding) => binding.enabled) || [];
      const labels = bindings.map((binding) => `${purposeLabel(binding.purpose)}：${profileLabel(binding.deviceId)}`);
      return labels.join('；') || '未配置';
    },
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 160,
    title: '天线方向',
    key: 'antennaDirections',
    minWidth: 160,
    render: (row: RfidWorkstation) =>
      Object.entries(row.antennaDirections || {})
        .map(([a, d]) => `${a}:${directionLabel(d)}`)
        .join('，') || '--',
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'enabledStatus', width: 90, dictType: 'col_rfid_workstation_status' },
  {
    title: '操作',
    key: 'actions',
    fixed: 'right',
    width: 180,
    render: (row: RfidWorkstation) => [
      h(NButton, { text: true, type: 'primary', onClick: () => editStation(row) }, () => '编辑'),
      h(
        NButton,
        { text: true, type: 'warning', style: 'margin-left:12px', onClick: () => resetToken(row) },
        () => '重置令牌',
      ),
    ],
  },
]);
const cardColumns = [
  { width: 130, title: '卡号', key: 'cardLast4', render: (row: any) => `**** ${row.cardLast4}` },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '用户', key: 'userId' },
  { ellipsis: { tooltip: true }, resizable: true, width: 260,
    title: '能力范围',
    key: 'allowedCapabilities',
    render: (row: any) => row.allowedCapabilities?.map(capabilityLabel).join('，'),
  },
  { ellipsis: { tooltip: true }, resizable: true, width: 110, title: '状态', key: 'status', dictType: 'col_rfid_operator_card_status' },
  { title: '生效时间', key: 'validFrom', width: 180, render: (row: any) => time(row.validFrom) },
  {
    title: '失效时间',
    key: 'validUntil',
    width: 180,
    render: (row: any) => (row.validUntil ? time(row.validUntil) : '长期有效'),
  },
  { width: 100,
    title: '操作',
    key: 'actions',
    render: (row: any) =>
      h(
        NButton,
        { text: true, type: 'error', disabled: row.status !== 'ACTIVE', onClick: () => revokeCard(row) },
        () => '注销',
      ),
  },
];
const tagColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'collectionNo', width: 150 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品名称', key: 'collectionName', minWidth: 180 },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: 'EPC', key: 'epc', minWidth: 220 },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: 'TID', key: 'tid', minWidth: 220 },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'status', width: 140, dictType: 'col_rfid_tag_status' },
  {
    title: '操作',
    key: 'actions',
    width: 90,
    render: (row: RfidTag) =>
      h(
        NButton,
        { text: true, type: 'error', disabled: row.status !== 'ACTIVE', onClick: () => retireTag(row) },
        () => '报废',
      ),
  },
];
const sessionColumns = [
  { title: '创建时间', key: 'createdAt', width: 180, render: (row: RfidScanSession) => time(row.createdAt) },
  { ellipsis: { tooltip: true }, resizable: true, width: 190, title: '会话号', key: 'sessionNo', minWidth: 190 },
  { ellipsis: { tooltip: true }, resizable: true, title: '作业类型', key: 'type', width: 130, dictType: 'col_rfid_scan_session_type' },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '业务单据', key: 'businessId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '工作站', key: 'workstationId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '设备', key: 'deviceId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true,
    title: '读取方式',
    key: 'directInput',
    width: 100,
    render: (row: RfidScanSession) => (row.directInput ? '手持机' : '固定设备'),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '状态', key: 'state', width: 120, dictType: 'col_rfid_scan_session_state' },
  { title: '应读', key: 'expectedCount', width: 80 },
  { title: '实读', key: 'readCount', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: '失败摘要', key: 'errorMessage', minWidth: 220 },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    render: (row: RfidScanSession) =>
      ['CREATED', 'STARTING', 'READING', 'STOPPING', 'PREVIEW_READY', 'FINALIZING'].includes(row.state)
        ? h(NButton, { text: true, type: 'error', onClick: () => terminateSession(row) }, () => '强制终止')
        : '--',
  },
];
const operationColumns = [
  { title: '发生时间', key: 'occurredAt', width: 180, render: (row: RfidTagOperation) => time(row.occurredAt) },
  { ellipsis: { tooltip: true }, resizable: true, title: '操作类型', key: 'operationType', width: 140, dictType: 'col_rfid_tag_operation_type' },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '标签 ID', key: 'tagId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: '操作前 EPC', key: 'beforeEpc', minWidth: 210 },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: '操作后 EPC', key: 'afterEpc', minWidth: 210 },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: 'TID', key: 'tid', minWidth: 210 },
  { ellipsis: { tooltip: true }, resizable: true, width: 170, title: '工作站', key: 'workstationId', minWidth: 170 },
  { ellipsis: { tooltip: true }, resizable: true, width: 150, title: '操作人', key: 'operatorId', minWidth: 150 },
  {
    title: '结果',
    key: 'success',
    width: 90,
    dictType: 'col_rfid_operation_result',
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '结果码', key: 'resultCode', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 220, title: '说明', key: 'detail', minWidth: 220 },
];

function defaultCardValidity(): [number, number] {
  const from = Date.now();
  return [from, from + 365 * 24 * 60 * 60 * 1000];
}
function time(value?: number) {
  return value ? new Date(value).toLocaleString() : '--';
}

function responseRows(response: any): any[] {
  const data = response?.data?.data ?? response?.data ?? response;
  return Array.isArray(data) ? data : Array.isArray(data?.list) ? data.list : [];
}
function pageResponse<T>(rows: T[], params: any) {
  const pageSize = Math.max(1, Number(params?.pageSize || 30));
  const pageNum = Math.max(1, Number(params?.pageNum || 1));
  return {
    data: {
      list: rows.slice((pageNum - 1) * pageSize, pageNum * pageSize),
      total: rows.length,
      pages: Math.max(1, Math.ceil(rows.length / pageSize)),
    },
  };
}

watch(
  () => [...stationForm.capabilities],
  () => {
    if (stationFormSyncing) return;
    if (stationForm.defaultCapability && !stationForm.capabilities.includes(stationForm.defaultCapability)) {
      stationForm.defaultCapability = stationForm.capabilities[0] || null;
    }
  },
  { flush: 'sync' },
);
watch(
  () => stationForm.passageDeviceIds[0],
  () => {
    if (stationFormSyncing) return;
    const allowed = new Set(antennaOptions.value.map((option) => option.value));
    stationForm.inAntennas = stationForm.inAntennas.filter((antenna) => allowed.has(antenna));
    stationForm.outAntennas = stationForm.outAntennas.filter((antenna) => allowed.has(antenna));
  },
  { flush: 'sync' },
);

onMounted(async () => {
  if (!cardOnly.value) await loadReferences();
});
watch(cardOnly, (only) => {
  activeTab.value = only ? 'cards' : operationTabs.has(props.initialTab) ? props.initialTab : 'workstations';
  if (!only) void loadReferences();
});
</script>

<style scoped>
.rfid-operation {
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px;
}
.rfid-tabs {
  flex: 1;
  min-height: 0;
}
.rfid-page-table {
  min-height: 0;
}
.form-tip {
  margin-bottom: 16px;
}
.client-url-field {
  margin: 18px 0 4px;
}
</style>
