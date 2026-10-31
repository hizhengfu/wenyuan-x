<template>
  <div class="rfid-desk" :class="{ standalone }">
    <NCard :bordered="false" class="desk-card">
      <template #header>
        <div class="desk-title">
          <span class="brand-mark">RF</span>
          <div>
            <strong>{{ deskTitle }}</strong>
            <small v-if="activeStation"
              >{{ activeStation.code }} · 操作员：{{
                previewOnly ? '示例操作员' : authStore.userInfo?.name || authStore.userInfo?.username || '待确认'
              }}</small
            >
            <small v-else>请选择可用工作站</small>
            <small v-if="standalone && !previewOnly" class="idle-hint">无活动作业时，15 分钟未操作自动退出</small>
          </div>
        </div>
      </template>
      <template #header-extra>
        <NSpace>
          <RfidWorkstationStatus
            v-if="activeStation && activeStation.id === boundWorkstationId"
            :workstation-id="activeStation.id"
            :device-id="tab === 'scan' ? session?.deviceId : tagVerifySession?.deviceId || tidReadSession?.deviceId"
            :purpose="tab === 'scan' ? 'RFID_READ' : 'RFID_WRITE'"
            :preview-only="previewOnly"
          />
          <NTag v-if="previewOnly" type="info">界面预览</NTag>
          <NTag v-if="boundWorkstationId" :type="leaseHeld ? 'success' : 'warning'">
            操作权：{{ previewOnly ? '只读预览' : leaseHeld ? '本窗口' : leaseBusy ? '获取中' : '未取得' }}
          </NTag>
          <NTag v-for="(entry, deviceId) in sikeStatuses" :key="deviceId" :type="entry.status === '已连接' ? 'success' : 'warning'">
            {{ entry.name }}：{{ entry.status }}
          </NTag>
          <NTag :type="realtimeTone">实时通道：{{ realtimeText }}</NTag>
          <NTag :type="sessionTone">{{ stateText }}</NTag>
          <NButton secondary @click="requestLogout">{{ previewOnly ? '退出预览' : '退出登录' }}</NButton>
        </NSpace>
      </template>
      <NAlert v-if="previewOnly" type="info" show-icon class="station-error">
        <NSpace align="center" justify="space-between">
          <span>当前为开发环境只读界面预览，不连接 RFID 设备，也不会提交业务数据。</span>
          <NButton v-if="tab === 'scan'" size="small" @click="togglePreviewConfirmation">
            {{ previewConfirmation ? '收起业务确认' : '查看业务确认' }}
          </NButton>
        </NSpace>
      </NAlert>
      <NAlert v-if="!previewOnly && standalone && !activeStation" type="error" show-icon class="station-error">
        当前账号无法访问快捷方式指定的工作站，请退出后检查本机绑定或联系管理员。
      </NAlert>
      <NAlert
        v-else-if="!previewOnly && standalone && activeStation && !showScan && !showTag"
        type="info"
        show-icon
        class="station-error"
      >
        通道监控工作站由设备自动运行，不提供人工桌面作业。请在管理端查看通行记录与异常告警。
      </NAlert>
      <template v-else>
        <NAlert v-if="activeStation && leaseError" type="warning" show-icon class="station-error">
          <NSpace align="center" justify="space-between" :wrap="false">
            <span>{{ leaseError }}</span>
            <NButton size="small" type="warning" :loading="leaseBusy" @click="takeOverLease">接管操作</NButton>
          </NSpace>
        </NAlert>
        <NTabs v-model:value="tab" type="line" :class="{ 'single-tab': standalone && singleVisibleTab }">
          <NTabPane v-if="showScan" name="scan" tab="借还 / 出入库核验">
            <div v-if="isStationLocked && activeSessionTypeOptions.length > 1" class="operation-picker">
              <button
                v-for="option in activeSessionTypeOptions"
                :key="option.value"
                type="button"
                class="operation-card"
                :class="{ active: form.type === option.value }"
                :disabled="Boolean(session)"
                @click="form.type = option.value"
              >
                <span>{{ option.short }}</span>
                <strong>{{ option.label }}</strong>
                <small>{{ option.description }}</small>
              </button>
            </div>
            <NForm inline label-placement="left" :show-feedback="false" class="session-form">
              <NFormItem v-if="!isStationLocked" label="工作站">
                <NSelect
                  v-model:value="form.workstationId"
                  :options="deskOptions"
                  :disabled="Boolean(session)"
                  filterable
                />
              </NFormItem>
              <NFormItem v-if="!isStationLocked" label="业务">
                <NSelect v-model:value="form.type" :options="sessionTypeOptions" />
              </NFormItem>
              <NFormItem label="业务单据" class="business-field">
                <NSelect
                  v-if="previewOnly"
                  v-model:value="form.businessId"
                  :options="previewBusinessOptions"
                  disabled
                />
                <RfidBusinessSelect
                  v-else
                  v-model:value="form.businessId"
                  :kind="form.type"
                  purpose="SESSION"
                  @selected="selectedBusiness = $event"
                />
              </NFormItem>
              <NFormItem
                ><NButton type="primary" size="large" :disabled="Boolean(session) || !leaseHeld" @click="createSession"
                  >创建作业</NButton
                ></NFormItem
              >
            </NForm>

            <NAlert v-if="session?.errorMessage" type="error" show-icon>{{ session.errorMessage }}</NAlert>
            <NGrid :cols="6" :x-gap="12" class="summary">
              <NGridItem v-for="item in reviewSummary" :key="item.filter">
                <button
                  class="summary-item"
                  type="button"
                  :aria-label="'查看' + item.label + '明细'"
                  :disabled="!preview"
                  @click="openReadDetails(item.filter)"
                >
                  <NStatistic :label="item.label" :value="item.count" />
                </button>
              </NGridItem>
            </NGrid>

            <NAlert v-if="preview?.blockReasons?.length" type="warning" show-icon class="block-reasons">
              <div v-for="reason in preview.blockReasons" :key="reason">{{ reason }}</div>
            </NAlert>

            <NDataTable
              :columns="readColumns"
              :data="preview?.reads || []"
              :pagination="false"
              :scroll-x="1290"
              :max-height="standalone ? '32vh' : undefined"
              size="small"
            />

            <NCard v-if="showFinalizeForm" ref="finalizeCardRef" title="业务确认" size="small" class="finalize-card">
              <template v-if="previewOnly" #header-extra>
                <NButton size="small" @click="togglePreviewConfirmation">收起业务确认</NButton>
              </template>
              <NAlert v-if="(session?.type || form.type) === 'RETURN_IN'" type="info"
                >归还须先在归还单逐件查验真实性和保存状况并由双方签署。RFID
                核验不能代替点交；异常件接收后继续待核查。</NAlert
              >
              <NAlert v-if="['REQUISITION_OUT', 'LOAN_OUT'].includes(session?.type || form.type)" type="info"
                >提用或借用出库须先在来源单据登记逐件点交原件。以下为本次 RFID
                核验补充资料；不能代替责任人批准、逐件现状或双方签署。</NAlert
              >
              <NForm label-placement="left" label-width="130" :disabled="previewOnly || busy">
                <NFormItem label="完残状况"
                  ><NInput v-model:value="finalizeForm.conditionNote" type="textarea"
                /></NFormItem>
                <NFormItem label="状况照片">
                  <HUploadInput
                    v-model:value="finalizeForm.conditionPhotoIds"
                    :disabled="previewOnly || busy"
                    @upload-finish="finalizeForm.conditionPhotoIds = $event.id"
                    clearable
                    placeholder="上传正面、背面和细节照片"
                  />
                </NFormItem>
                <NFormItem label="签署凭证">
                  <HUploadInput
                    v-model:value="finalizeForm.signedVoucherAttachmentIds"
                    :disabled="previewOnly || busy"
                    @upload-finish="finalizeForm.signedVoucherAttachmentIds = $event.id"
                    clearable
                    placeholder="上传双方签署后的凭证"
                  />
                </NFormItem>
                <NFormItem v-if="overridePermitted || previewOnly" label="异常强制确认">
                  <NSwitch v-model:value="finalizeForm.force" :disabled="previewOnly || !overridePermitted" />
                </NFormItem>
                <NFormItem v-if="finalizeForm.force" label="强制确认原因"
                  ><NInput v-model:value="finalizeForm.reason"
                /></NFormItem>
              </NForm>
            </NCard>
          </NTabPane>

          <NTabPane v-if="showTag" name="tag" tab="发标 / 换标">
            <NForm label-placement="left" label-width="120" class="tag-form">
              <NFormItem v-if="!isStationLocked" label="发标工作站"
                ><NSelect v-model:value="tagForm.workstationId" :options="tagStationOptions" filterable
              /></NFormItem>
              <NFormItem label="藏品"
                ><NSelect
                  v-if="previewOnly"
                  v-model:value="tagForm.collectionId"
                  :options="previewCollectionOptions"
                  disabled />
                <CollectionSelect v-else v-model:value="tagForm.collectionId" life-status="IN_STORE"
              /></NFormItem>
              <NFormItem label="待写标签 TID"
                ><NTag>{{ tagForm.expectedTid || '尚未读取' }}</NTag></NFormItem
              >
              <NFormItem
                ><NButton
                  type="primary"
                  :disabled="Boolean(tidReadSession) || !leaseHeld"
                  :loading="busy"
                  @click="startTidDiscovery"
                  >开始读取标签 TID</NButton
                ></NFormItem
              >
              <NFormItem
                ><NButton
                  type="warning"
                  :disabled="tidReadSession?.state !== 'READING' || !leaseHeld"
                  :loading="busy"
                  @click="stopTidDiscovery"
                  >停止并采用 TID</NButton
                ></NFormItem
              >
              <NFormItem
                v-if="
                  tidReadSession && ['CREATED', 'PREVIEW_READY', 'FAILED', 'EXPIRED'].includes(tidReadSession.state)
                "
                ><NButton secondary @click="resetTidDiscovery">重新读取 TID</NButton></NFormItem
              >
              <NFormItem
                ><NButton
                  type="primary"
                  :disabled="!tagForm.expectedTid || !leaseHeld"
                  :loading="busy"
                  @click="createTagJob"
                  >生成 EPC 并写入</NButton
                ></NFormItem
              >
            </NForm>
            <NAlert type="warning" :bordered="false" class="verify-form"
              >读取 TID 和写入时，读写区只允许放置一个目标标签；目标 EPC 由服务端生成，不接受操作员手输。</NAlert
            >
            <NDescriptions v-if="tagJob" bordered :column="2" size="small">
              <NDescriptionsItem label="任务">{{ tagJob.id }}</NDescriptionsItem>
              <NDescriptionsItem label="状态">
                <HDictText type="col_rfid_tag_job_status" :value="tagJob.status" />
              </NDescriptionsItem>
              <NDescriptionsItem label="目标 EPC">{{ tagJob.targetEpc }}</NDescriptionsItem>
              <NDescriptionsItem label="锁定 TID">{{ tagJob.expectedTid }}</NDescriptionsItem>
            </NDescriptions>
            <NAlert v-if="tagJob?.errorMessage" type="error" show-icon class="verify-form">{{
              tagJob.errorMessage
            }}</NAlert>
            <NForm v-if="tagJob" inline class="verify-form">
              <NFormItem label="设备读回会话">
                <NTag v-if="tagVerifySession">
                  <HDictText type="col_rfid_scan_session_state" :value="tagVerifySession.state" />
                </NTag>
                <NTag v-else>未开始</NTag>
              </NFormItem>
              <NFormItem>
                <NButton
                  type="primary"
                  :disabled="tagJob.status !== 'VERIFY_PENDING' || Boolean(tagVerifySession) || !leaseHeld"
                  :loading="busy"
                  @click="startTagVerification"
                  >开始设备读回</NButton
                >
              </NFormItem>
              <NFormItem>
                <NButton
                  type="success"
                  :disabled="tagVerifySession?.state !== 'READING' || !leaseHeld"
                  :loading="busy"
                  @click="stopAndVerifyTag"
                  >停止读回并校验激活</NButton
                >
              </NFormItem>
              <NFormItem
                v-if="
                  tagVerifySession &&
                  ['CREATED', 'PREVIEW_READY', 'FINALIZED', 'FAILED', 'EXPIRED'].includes(tagVerifySession.state)
                "
              >
                <NButton secondary @click="resetTagVerification">重新读回</NButton>
              </NFormItem>
              <NFormItem><NButton secondary @click="refreshTagJob">刷新写入结果</NButton></NFormItem>
            </NForm>
            <NAlert v-if="tagVerifyPreview" type="info" :bordered="false" class="verify-form">
              设备读到 {{ tagVerifyPreview.reads.length }} 个 EPC；只有读回目标 EPC 且 TID
              与写入前锁定值一致时，服务端才会激活标签。
            </NAlert>
          </NTabPane>
        </NTabs>
      </template>
      <template v-if="tab === 'scan' && showScan" #footer>
        <NSpace class="actions sticky-actions">
          <NButton type="success" size="large" :disabled="!canStart" :loading="busy" @click="start">开始读卡</NButton>
          <NButton type="warning" size="large" :disabled="!canStop" :loading="busy" @click="stop">停止读卡</NButton>
          <NButton size="large" :disabled="!session" @click="refreshPreview">刷新结果</NButton>
          <NButton type="error" ghost size="large" :disabled="!canCancel" @click="cancel">取消会话</NButton>
          <NButton
            v-if="showFinalizeForm"
            type="primary"
            size="large"
            :disabled="!canFinalize"
            :loading="busy"
            @click="finalizeSession"
            >确认并执行业务</NButton
          >
          <NButton
            v-if="session?.state === 'FINALIZED' || session?.state === 'CANCELLED' || session?.state === 'FAILED'"
            @click="resetSession"
            >新会话</NButton
          >
        </NSpace>
      </template>
    </NCard>
    <HPageForm content-only :enable-cancel="false" v-model:show="readDetailsVisible" :title="readDetailsTitle">
      <NDataTable
        :columns="readDetailsFilter === 'MISSING' ? missingColumns : readColumns"
        :data="readDetailsRows"
        :pagination="false"
        :scroll-x="readDetailsFilter === 'MISSING' ? 710 : 1290"
        :max-height="'65vh'"
        size="small"
      />
      <template #form-action>
        <NButton @click="readDetailsVisible = false">关闭</NButton>
      </template>
    </HPageForm>
    <RfidSensitiveAuthModal ref="sensitiveAuthRef" />
  </div>
</template>

<script setup lang="ts">
import { computed, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import {
  NButton,
  NCard,
  NDataTable,
  NDescriptions,
  NDescriptionsItem,
  NForm,
  NFormItem,
  NGrid,
  NGridItem,
  NInput,
  NSelect,
  NSpace,
  NStatistic,
  NSwitch,
  NTabPane,
  NTabs,
  NTag,
  NTime,
  NAlert,
  useDialog,
  useMessage,
  type DataTableColumns,
} from 'naive-ui';
import { useAuthStore } from '@/store';
import { moveTokenToSession } from '@/utils/auth';
import { getDictLabel, useDict } from '@/utils/dict';
import { rfidApi, rfidRequestId } from '../api/rfid';
import { SikeWorkstationRuntime } from '../utils/sikeRfidWorkstation';
import {
  canFinalizeRfidScan,
  rfidReviewRows,
  type RfidReviewFilter,
  type RfidReviewRow,
} from '../utils/rfidScanReview';
import CollectionSelect from '../components/CollectionSelect.vue';
import RfidBusinessSelect from '../components/rfid/RfidBusinessSelect.vue';
import RfidSensitiveAuthModal from '../components/rfid/RfidSensitiveAuthModal.vue';
import RfidWorkstationStatus from '../components/rfid/RfidWorkstationStatus.vue';
import { useRfidRealtime } from '../composables/useRfidRealtime';
import type { CollectionReturnVo, InventoryBatchVo, LoanVo, RequisitionVo } from '../typings/collection';
import type {
  RfidRealtimeEvent,
  RfidScanPreview,
  RfidScanSession,
  RfidSessionType,
  RfidWorkstation,
} from '../typings/rfid';

const props = withDefaults(defineProps<{ standalone?: boolean; previewOnly?: boolean }>(), {
  standalone: false,
  previewOnly: false,
});
const emit = defineEmits<{ exitPreview: [] }>();
const { standalone, previewOnly } = props;

const authStore = useAuthStore();
const route = useRoute();
const router = useRouter();
const message = useMessage();
const dialog = useDialog();
const tab = ref('scan');
const busy = ref(false);
const workstations = ref<RfidWorkstation[]>([]);
const session = ref<RfidScanSession | null>(null);
const preview = ref<RfidScanPreview | null>(null);
const previewConfirmation = ref(false);
const finalizeCardRef = ref<InstanceType<typeof NCard>>();
const readDetailsVisible = ref(false);
const readDetailsFilter = ref<RfidReviewFilter>('MISSING');
const tagJob = ref<any>(null);
const tidReadSession = ref<RfidScanSession | null>(null);
const tagVerifySession = ref<RfidScanSession | null>(null);
const tagVerifyPreview = ref<RfidScanPreview | null>(null);
let pollTimer: number | undefined;
let tagPollTimer: number | undefined;
let tidReadPollTimer: number | undefined;
let tagVerifyPollTimer: number | undefined;
let leaseHeartbeatTimer: number | undefined;
let idleCheckTimer: number | undefined;
let lastActivityAt = Date.now();
let refreshInFlight = false;
let refreshPending = false;
const leaseHeld = ref(false);
const leaseBusy = ref(false);
const leaseError = ref('');
const sikeStatuses = reactive<Record<string, { name: string; status: string }>>({});
let sikeRuntime: SikeWorkstationRuntime | null = null;
const KIOSK_SESSION_KEY = 'collection-rfid-kiosk-session';
const KIOSK_IDLE_TIMEOUT_MS = 15 * 60 * 1000;
const KIOSK_ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'touchstart'] as const;

const requestedType = String(route.query.type || '');
const initialType: RfidSessionType = ['LOAN_OUT', 'REQUISITION_OUT', 'RETURN_IN', 'INVENTORY'].includes(requestedType)
  ? (requestedType as RfidSessionType)
  : 'LOAN_OUT';
const form = reactive<{ workstationId: string | null; type: RfidSessionType; businessId: string | null }>({
  workstationId: (route.query.workstationId as string) || null,
  type: initialType,
  businessId: (route.query.businessId as string) || null,
});
const selectedBusiness = ref<LoanVo | RequisitionVo | CollectionReturnVo | InventoryBatchVo | null>(null);
const finalizeForm = reactive({
  force: false,
  reason: '',
  conditionNote: '',
  conditionPhotoIds: '',
  signedVoucherAttachmentIds: '',
});
const tagForm = reactive({
  workstationId: null as string | null,
  collectionId: null as string | null,
  expectedTid: '',
});
const previewBusinessOptions = computed(() => [
  {
    label: `${dictLabel(sessionTypeDictOptions.value, form.type)}示例单据（只读预览）`,
    value: 'preview-business',
  },
]);
const previewCollectionOptions = [{ label: '示例藏品（只读预览）', value: 'preview-collection' }];
const sensitiveAuthRef = ref<InstanceType<typeof RfidSensitiveAuthModal>>();
const lockedWorkstationId = computed(() => (standalone ? String(route.query.workstationId || '') : ''));
const boundWorkstationId = ref('');
const isStationLocked = computed(() => standalone);
const activeStation = computed(() => workstations.value.find((item) => item.id === form.workstationId) || null);
const showScan = computed(
  () =>
    !standalone ||
    activeStation.value?.capabilities?.some((capability) => ['BORROW_RETURN', 'INVENTORY'].includes(capability)),
);
const showTag = computed(() => !standalone || activeStation.value?.capabilities?.includes('TAG_ISSUE'));
const singleVisibleTab = computed(() => Number(showScan.value) + Number(showTag.value) <= 1);
const deskTitle = computed(() => `${APP_TITLE} - ${activeStation.value?.name || 'RFID 工作台'}`);

const deskOptions = computed(() => {
  const capability = form.type === 'INVENTORY' ? 'INVENTORY' : 'BORROW_RETURN';
  return workstations.value
    .filter((item) => item.capabilities?.includes(capability))
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id }));
});
const tagStationOptions = computed(() =>
  workstations.value
    .filter((item) => item.capabilities?.includes('TAG_ISSUE'))
    .map((item) => ({ label: `${item.name}（${item.code}）`, value: item.id })),
);
const { options: sessionTypeDictOptions } = useDict('col_rfid_scan_session_type');
const { options: sessionStateOptions } = useDict('col_rfid_scan_session_state');
const { options: classificationOptions } = useDict('col_rfid_read_classification');
const reviewSummary = computed<Array<{ label: string; count: number; filter: RfidReviewFilter }>>(() => [
  { label: '应读', count: preview.value?.expected ?? session.value?.expectedCount ?? 0, filter: 'EXPECTED_ITEMS' },
  { label: '已匹配', count: preview.value?.matched ?? 0, filter: 'EXPECTED' },
  { label: '漏读', count: preview.value?.missing ?? 0, filter: 'MISSING' },
  { label: '范围外', count: preview.value?.extra ?? 0, filter: 'EXTRA' },
  { label: '未知', count: preview.value?.unknown ?? 0, filter: 'UNKNOWN' },
  { label: '冲突', count: preview.value?.conflict ?? 0, filter: 'CONFLICT' },
]);
const readDetailsRows = computed(() => rfidReviewRows(preview.value, readDetailsFilter.value));
const readDetailsTitle = computed(() =>
  readDetailsFilter.value === 'EXPECTED_ITEMS'
    ? '应读藏品明细'
    : dictLabel(classificationOptions.value, readDetailsFilter.value) + '明细',
);
function openReadDetails(filter: RfidReviewFilter) {
  readDetailsFilter.value = filter;
  readDetailsVisible.value = true;
}
async function togglePreviewConfirmation() {
  previewConfirmation.value = !previewConfirmation.value;
  if (previewConfirmation.value) {
    await nextTick();
    finalizeCardRef.value?.$el.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }
}
const sessionTypeMeta: Array<{ value: RfidSessionType; short: string; description: string }> = [
  { value: 'LOAN_OUT', short: '借', description: '按借展单核验并执行出库' },
  { value: 'REQUISITION_OUT', short: '提', description: '按提用单核验并执行出库' },
  { value: 'RETURN_IN', short: '还', description: '按归还单核验藏品入库' },
  { value: 'INVENTORY', short: '盘', description: '按盘点批次核验在库藏品' },
];
const sessionTypeOptions = computed(() =>
  sessionTypeMeta.map((option) => ({
    ...option,
    label: dictLabel(sessionTypeDictOptions.value, option.value),
  })),
);
const activeSessionTypeOptions = computed(() => {
  if (!standalone || !activeStation.value) return sessionTypeOptions.value;
  return sessionTypeOptions.value.filter((option) =>
    option.value === 'INVENTORY'
      ? activeStation.value?.capabilities?.includes('INVENTORY')
      : activeStation.value?.capabilities?.includes('BORROW_RETURN'),
  );
});
const stateText = computed(() =>
  session.value?.state ? dictLabel(sessionStateOptions.value, session.value.state) : '未创建会话',
);
const sessionTone = computed(() =>
  session.value?.state === 'READING' ? 'success' : session.value?.state === 'FAILED' ? 'error' : 'info',
);
const canStart = computed(
  () => leaseHeld.value && (session.value?.state === 'CREATED' || session.value?.state === 'PREVIEW_READY'),
);
const canStop = computed(() => leaseHeld.value && session.value?.state === 'READING');
const canCancel = computed(
  () => leaseHeld.value && (session.value?.state === 'CREATED' || session.value?.state === 'PREVIEW_READY'),
);
const showFinalizeForm = computed(
  () => session.value?.state === 'PREVIEW_READY' || (previewOnly && previewConfirmation.value),
);
// 与服务端异常确认策略一致：管理员角色，或明确授予的异常确认权限。
const overridePermitted = computed(
  () => authStore.hasRoleId('1') || Boolean(authStore.permissions?.includes('COLLECTION_RFID_OVERRIDE')),
);
const canFinalize = computed(() =>
  canFinalizeRfidScan({
    session: session.value,
    preview: preview.value,
    previewOnly,
    leaseHeld: leaseHeld.value,
    busy: busy.value,
    permitted: authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_RFID_FINALIZE'),
    overridePermitted: overridePermitted.value,
    force: finalizeForm.force,
    reason: finalizeForm.reason,
  }),
);
const hasActiveSession = computed(() =>
  [session.value, tidReadSession.value, tagVerifySession.value].some((item) =>
    Boolean(item && !['FINALIZED', 'CANCELLED', 'FAILED', 'EXPIRED'].includes(item.state)),
  ),
);
const {
  status: realtimeStatus,
  connect: connectRealtime,
  disconnect: disconnectRealtime,
} = useRfidRealtime(handleRealtimeEvent);
const realtimeText = computed(() =>
  previewOnly
    ? '只读预览'
    : { CLOSED: '未连接', CONNECTING: '连接中', OPEN: '已连接', RETRYING: '重连中' }[realtimeStatus.value],
);
const realtimeTone = computed(() =>
  realtimeStatus.value === 'OPEN' ? 'success' : realtimeStatus.value === 'RETRYING' ? 'warning' : 'default',
);

const readColumns: DataTableColumns<RfidReviewRow> = [
  {
    title: '结果',
    key: 'classification',
    width: 100,
    render: (row: RfidReviewRow) =>
      h(NTag, { type: row.classification === 'EXPECTED' ? 'success' : 'error', bordered: false }, () =>
        dictLabel(classificationOptions.value, row.classification),
      ),
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'collectionNo', width: 150 },
  { resizable: true, width: 180, title: '藏品名称', key: 'collectionName', minWidth: 180, ellipsis: { tooltip: true } },
  { resizable: true, title: 'EPC', key: 'epc', width: 220, ellipsis: { tooltip: true } },
  { resizable: true, title: 'TID', key: 'tid', width: 220, ellipsis: { tooltip: true } },
  {
    title: '最近读取时间',
    key: 'lastSeenAt',
    width: 180,
    render: (row: RfidReviewRow) =>
      row.lastSeenAt == null ? '—' : h(NTime, { time: row.lastSeenAt, format: 'yyyy-MM-dd HH:mm:ss' }),
  },
  { title: '天线', key: 'antenna', width: 70 },
  { title: '次数', key: 'readCount', width: 70 },
  { title: '峰值 RSSI', key: 'peakRssi', width: 100 },
];
const missingColumns: DataTableColumns<RfidReviewRow> = [
  readColumns[0],
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'collectionNo', width: 150 },
  { resizable: true, width: 180, title: '藏品名称', key: 'collectionName', minWidth: 180, ellipsis: { tooltip: true } },
  { resizable: true, width: 280, title: '应读 EPC', key: 'epc', minWidth: 280, ellipsis: { tooltip: true } },
];

async function loadWorkstations() {
  const [resp, contextResp]: any[] = await Promise.all([rfidApi.workstations(), rfidApi.workstationContext()]);
  workstations.value = resp?.data || [];
  boundWorkstationId.value = contextResp?.data?.workstationId || '';
  if (standalone) {
    const validBinding =
      boundWorkstationId.value === lockedWorkstationId.value &&
      workstations.value.some((item) => item.id === boundWorkstationId.value);
    form.workstationId = validBinding ? boundWorkstationId.value : null;
    const station = activeStation.value;
    const defaultCapability = station?.defaultCapability;
    if (station && (defaultCapability === 'TAG_ISSUE' || (!showScan.value && showTag.value))) {
      tagForm.workstationId = station.id;
      tab.value = 'tag';
    } else if (defaultCapability === 'INVENTORY') {
      form.type = 'INVENTORY';
      tab.value = 'scan';
    } else if (station?.capabilities?.includes('BORROW_RETURN')) {
      if (form.type === 'INVENTORY') form.type = 'LOAN_OUT';
      tab.value = 'scan';
    } else if (station?.capabilities?.includes('INVENTORY')) {
      form.type = 'INVENTORY';
      tab.value = 'scan';
    }
  } else {
    if (!form.workstationId) {
      form.workstationId =
        deskOptions.value.find((option) => option.value === boundWorkstationId.value)?.value ||
        deskOptions.value[0]?.value ||
        null;
    }
    if (!tagForm.workstationId) {
      tagForm.workstationId =
        tagStationOptions.value.find((option) => option.value === boundWorkstationId.value)?.value ||
        tagStationOptions.value[0]?.value ||
        null;
    }
  }
  if (form.workstationId) await switchRealtimeStation(form.workstationId);
}

function initializePreviewDesk() {
  const station: RfidWorkstation = {
    id: 'preview-workstation',
    orgId: 'preview-org',
    code: 'RFID-WS-PREVIEW',
    name: '示例借还工作站',
    defaultCapability: 'BORROW_RETURN',
    capabilities: ['BORROW_RETURN', 'INVENTORY', 'TAG_ISSUE'],
    deviceBindings: [],
    antennaDirections: { 1: 'IN', 2: 'OUT' },
    passageDebounceSeconds: 3,
    alarmOutputIndex: 1,
    alarmDurationMillis: 3000,
    enabled: true,
  };
  workstations.value = [station];
  boundWorkstationId.value = station.id;
  form.workstationId = station.id;
  form.businessId = 'preview-business';
  tagForm.workstationId = station.id;
  tagForm.collectionId = 'preview-collection';
  preview.value = {
    sessionId: 'preview-session',
    state: 'PREVIEW_READY',
    expected: 3,
    matched: 2,
    missing: 1,
    extra: 0,
    unknown: 0,
    conflict: 0,
    stopAcknowledged: true,
    settled: true,
    canFinalize: false,
    blockReasons: ['只读预览不会提交业务确认'],
    reads: [
      {
        epc: 'E28068940000501A00000001',
        tid: 'E2801100200076250A510001',
        antenna: 1,
        direction: 'IN',
        readCount: 8,
        firstSeenAt: 1788850000000,
        lastSeenAt: 1788850010000,
        peakRssi: -42,
        collectionId: 'preview-collection-1',
        collectionNo: 'LSBWG-0001',
        collectionName: '示例藏品一',
        classification: 'EXPECTED',
      },
      {
        epc: 'E28068940000501A00000002',
        tid: 'E2801100200076250A510002',
        antenna: 2,
        direction: 'OUT',
        readCount: 5,
        firstSeenAt: 1788850005000,
        lastSeenAt: 1788850012000,
        peakRssi: -49,
        collectionId: 'preview-collection-2',
        collectionNo: 'LSBWG-0002',
        collectionName: '示例藏品二',
        classification: 'EXPECTED',
      },
    ],
    missingItems: [
      {
        collectionId: 'preview-collection-3',
        collectionNo: 'LSBWG-0003',
        collectionName: '示例漏读藏品',
        expectedEpc: 'E28068940000501A00000003',
      },
    ],
  };
}

async function initializeDesk() {
  if (standalone) {
    try {
      await rfidApi.authorizeWorkstationAccess();
    } catch (error: any) {
      const status = Number(error?.response?.status || 0);
      if (![400, 401, 403].includes(status)) {
        if (status) message.error('RFID 工作站授权校验失败，请检查服务状态后重试');
        return;
      }
      sessionStorage.removeItem(KIOSK_SESSION_KEY);
      authStore.clearUserInfo();
      message.error('当前账号无权使用本机绑定的 RFID 工作站，请联系管理员配置对应权限');
      await router.replace({ path: '/rfid-kiosk' });
      return;
    }
  }
  try {
    await loadWorkstations();
  } catch {
    message.error('RFID 工作台加载失败，请检查网络连接后重试');
  }
}

function stopLeaseHeartbeat() {
  if (leaseHeartbeatTimer) window.clearInterval(leaseHeartbeatTimer);
  leaseHeartbeatTimer = undefined;
}

function startLeaseHeartbeat() {
  stopLeaseHeartbeat();
  leaseHeartbeatTimer = window.setInterval(async () => {
    if (document.hidden || !leaseHeld.value) return;
    try {
      await rfidApi.heartbeatWorkstationLease();
    } catch {
      leaseHeld.value = false;
      leaseError.value = '本窗口的工作站操作权已失效，请重新取得操作权。';
      stopLeaseHeartbeat();
      void stopSikeRuntime();
    }
  }, 15_000);
}

async function acquireLeaseForStation(workstationId: string, force = false): Promise<boolean> {
  if (!boundWorkstationId.value) {
    leaseHeld.value = false;
    leaseError.value = '当前浏览器尚未绑定 RFID 工作站，请从工作站快捷方式完成首次部署。';
    return false;
  }
  if (boundWorkstationId.value !== workstationId) {
    leaseHeld.value = false;
    leaseError.value = '当前电脑绑定的工作站与所选工作站不一致。';
    return false;
  }
  leaseBusy.value = true;
  try {
    const response: any = await rfidApi.acquireWorkstationLease(force);
    if (response?.data?.workstationId !== workstationId) throw new Error('工作站租约与当前工作站不一致');
    leaseHeld.value = true;
    leaseError.value = '';
    startLeaseHeartbeat();
    await startSikeRuntime();
    return true;
  } catch {
    leaseHeld.value = false;
    leaseError.value = force
      ? '未能接管工作站操作权，请检查网络或重新登录。'
      : '工作站正在另一窗口操作；确认旧窗口已停用后，可选择接管操作。';
    stopLeaseHeartbeat();
    void stopSikeRuntime();
    return false;
  } finally {
    leaseBusy.value = false;
  }
}

async function ensureLeaseForStation(workstationId: string | null): Promise<boolean> {
  if (!workstationId) {
    message.warning('请选择工作站');
    return false;
  }
  if (leaseHeld.value && workstationId === boundWorkstationId.value) return true;
  return acquireLeaseForStation(workstationId);
}

async function releaseWorkstationLease() {
  stopLeaseHeartbeat();
  const held = leaseHeld.value;
  if (held && sikeRuntime) {
    const active = [session.value, tidReadSession.value, tagVerifySession.value]
      .filter((item): item is RfidScanSession => Boolean(item && sikeRuntime?.handles(item.deviceId)));
    for (const current of active) {
      if (current.state === 'READING') {
        try {
          const stopped = ((await rfidApi.stopSession(current.id)) as any)?.data as RfidScanSession;
          if (session.value?.id === current.id) session.value = stopped;
          if (tidReadSession.value?.id === current.id) tidReadSession.value = stopped;
          if (tagVerifySession.value?.id === current.id) tagVerifySession.value = stopped;
        } catch {
          leaseError.value = '本机读写器停止请求未被服务器确认，请返回工作台核对会话。';
        }
      }
    }
    if (active.some((item) => item.state === 'READING' || item.state === 'STOPPING')) {
      try { await sikeRuntime.drainCommands(); } catch { /* 本机停止帧仍在连接关闭时尽力发送 */ }
    }
  }
  leaseHeld.value = false;
  await stopSikeRuntime();
  if (!held) return;
  try {
    await rfidApi.releaseWorkstationLease();
  } catch {
    // 页面关闭或断网时由服务端短租约自动过期，不能阻塞页面退出。
  }
}

async function startSikeRuntime() {
  await stopSikeRuntime();
  const runtime = new SikeWorkstationRuntime((deviceId, deviceName, status) => {
    if (sikeRuntime === runtime) sikeStatuses[deviceId] = { name: deviceName, status };
  });
  sikeRuntime = runtime;
  try {
    await runtime.start();
  } catch {
    leaseError.value = '斯科本机服务配置读取失败；请检查设备配置及工作站权限。';
  }
}

async function stopSikeRuntime() {
  const runtime = sikeRuntime;
  sikeRuntime = null;
  if (runtime) await runtime.stop();
  Object.keys(sikeStatuses).forEach((key) => { delete sikeStatuses[key]; });
}

async function takeOverLease() {
  const workstationId = form.workstationId || tagForm.workstationId;
  if (workstationId) await acquireLeaseForStation(workstationId, true);
}

function handleVisibilityChange() {
  if (document.hidden) {
    void releaseWorkstationLease();
  } else {
    const workstationId = form.workstationId || tagForm.workstationId;
    if (workstationId) void acquireLeaseForStation(workstationId);
  }
}

async function switchRealtimeStation(workstationId: string) {
  await acquireLeaseForStation(workstationId);
  const cursor = Number(sessionStorage.getItem(`rfid:sse:${workstationId}`) || 0);
  connectRealtime(workstationId, Number.isSafeInteger(cursor) ? cursor : 0);
  await recoverActiveSession(workstationId);
}

async function recoverActiveSession(workstationId: string) {
  const resp: any = await rfidApi.activeSession(workstationId);
  const active = (resp?.data || null) as RfidScanSession | null;
  if (!active) return;
  session.value = active;
  sikeRuntime?.setSession(active);
  form.type = active.type;
  form.businessId = active.businessId || null;
  await refreshPreview();
  startFallbackPolling();
  message.info(`已恢复进行中的会话 ${active.sessionNo}`);
}

async function handleRealtimeEvent(event: RfidRealtimeEvent) {
  if (event.workstationId && event.sequence > 0) {
    sessionStorage.setItem(`rfid:sse:${event.workstationId}`, String(event.sequence));
  }
  if (event.type === 'RESYNC_REQUIRED' || event.type === 'AUTH_STATE') {
    if (form.workstationId) await syncCurrentSession(form.workstationId);
    if (tidReadSession.value) await refreshTidDiscovery();
    if (tagVerifySession.value) await refreshTagVerifySession();
    return;
  }
  if (!['SESSION_STATE', 'TAG_BATCH', 'COMMAND_RESULT'].includes(event.type)) return;
  if (tidReadSession.value && event.sessionId === tidReadSession.value.id) {
    await refreshTidDiscovery();
    return;
  }
  if (tagVerifySession.value && event.sessionId === tagVerifySession.value.id) {
    await refreshTagVerifySession();
    return;
  }
  if (!session.value && event.sessionId && form.workstationId) {
    // 手持机可在借还台之外领取直传任务；首个会话事件到达时主动恢复，不能只刷新空会话。
    await syncCurrentSession(form.workstationId);
    return;
  }
  if (!session.value || !event.sessionId || event.sessionId === session.value.id) await queueRefresh();
}

async function syncCurrentSession(workstationId: string) {
  const resp: any = await rfidApi.activeSession(workstationId);
  const active = (resp?.data || null) as RfidScanSession | null;
  if (active) {
    session.value = active;
    await queueRefresh();
  } else if (session.value && hasActiveSession.value) {
    await queueRefresh();
  }
}

async function queueRefresh() {
  if (refreshInFlight) {
    refreshPending = true;
    return;
  }
  refreshInFlight = true;
  try {
    do {
      refreshPending = false;
      await refreshPreview();
    } while (refreshPending);
  } finally {
    refreshInFlight = false;
  }
}

watch(
  () => form.type,
  () => {
    if (previewOnly) {
      form.businessId = 'preview-business';
      return;
    }
    if (session.value) return;
    form.businessId = null;
    selectedBusiness.value = null;
    if (!deskOptions.value.some((option) => option.value === form.workstationId)) {
      form.workstationId = deskOptions.value[0]?.value || null;
    }
  },
);

watch(
  () => form.workstationId,
  (value, previous) => {
    if (previewOnly) return;
    if (value && value !== previous && !session.value) void switchRealtimeStation(value);
  },
);

async function createSession() {
  if (!form.workstationId || !form.businessId) return message.warning('请选择可执行业务单据或盘点批次');
  if (!(await ensureLeaseForStation(form.workstationId))) return;
  busy.value = true;
  try {
    const inventoryCollectionIds =
      form.type === 'INVENTORY'
        ? (selectedBusiness.value as InventoryBatchVo | null)?.collectionIds
            ?.split(',')
            .map((id) => id.trim())
            .filter(Boolean)
        : undefined;
    const resp: any = await rfidApi.createSession({
      workstationId: form.workstationId,
      type: form.type,
      businessId: form.businessId || undefined,
      collectionIds: inventoryCollectionIds,
      clientRequestId: rfidRequestId(),
    });
    session.value = resp?.data;
    await refreshPreview();
  } finally {
    busy.value = false;
  }
}

async function start() {
  if (!session.value) return;
  if (!(await ensureLeaseForStation(form.workstationId))) return;
  busy.value = true;
  try {
    session.value = ((await rfidApi.startSession(session.value.id)) as any)?.data;
    startFallbackPolling();
  } finally {
    busy.value = false;
  }
}
async function stop() {
  if (!session.value) return;
  if (!(await ensureLeaseForStation(form.workstationId))) return;
  busy.value = true;
  try {
    session.value = ((await rfidApi.stopSession(session.value.id)) as any)?.data;
    startFallbackPolling();
  } finally {
    busy.value = false;
  }
}
async function refreshPreview() {
  const sessionId = session.value?.id;
  if (!sessionId) return;
  const sessionResp: any = await rfidApi.session(sessionId);
  session.value = sessionResp?.data;
  const resp: any = await rfidApi.preview(sessionId);
  preview.value = resp?.data;
  if (['PREVIEW_READY', 'FINALIZED', 'CANCELLED', 'FAILED'].includes(session.value?.state || '')) stopPolling();
}
function startFallbackPolling() {
  stopPolling();
  pollTimer = window.setInterval(() => {
    if (realtimeStatus.value !== 'OPEN') void queueRefresh();
  }, 3_000);
}
function stopPolling() {
  if (pollTimer) window.clearInterval(pollTimer);
  pollTimer = undefined;
}
async function cancel() {
  if (!session.value) return;
  if (!(await ensureLeaseForStation(form.workstationId))) return;
  session.value = ((await rfidApi.cancelSession(session.value.id)) as any)?.data;
  stopPolling();
}
function resetSession() {
  stopPolling();
  session.value = null;
  preview.value = null;
  Object.assign(finalizeForm, {
    force: false,
    reason: '',
    conditionNote: '',
    conditionPhotoIds: '',
    signedVoucherAttachmentIds: '',
  });
}

function requestLogout() {
  if (previewOnly) {
    emit('exitPreview');
    return;
  }
  if (hasActiveSession.value) {
    message.warning('请先停止读卡，并确认业务或取消会话后再退出登录');
    return;
  }
  void releaseWorkstationLease();
  if (standalone) {
    const stationCode = activeStation.value?.code;
    stopIdleLogout();
    sessionStorage.removeItem(KIOSK_SESSION_KEY);
    authStore.clearUserInfo();
    void router.replace({ path: '/rfid-kiosk', query: stationCode ? { stationCode } : undefined });
    return;
  }
  authStore.logout();
}

function recordOperatorActivity() {
  lastActivityAt = Date.now();
}

function stopIdleLogout() {
  if (idleCheckTimer) window.clearInterval(idleCheckTimer);
  idleCheckTimer = undefined;
  KIOSK_ACTIVITY_EVENTS.forEach((eventName) => window.removeEventListener(eventName, recordOperatorActivity));
}

function startIdleLogout() {
  if (!standalone) return;
  stopIdleLogout();
  lastActivityAt = Date.now();
  KIOSK_ACTIVITY_EVENTS.forEach((eventName) => window.addEventListener(eventName, recordOperatorActivity));
  idleCheckTimer = window.setInterval(async () => {
    if (Date.now() - lastActivityAt < KIOSK_IDLE_TIMEOUT_MS) return;
    if (hasActiveSession.value) {
      lastActivityAt = Date.now();
      return;
    }
    stopIdleLogout();
    await releaseWorkstationLease();
    const stationCode = activeStation.value?.code;
    sessionStorage.removeItem(KIOSK_SESSION_KEY);
    authStore.clearUserInfo();
    message.warning('工作台已空闲 15 分钟，请重新登录');
    await router.replace({ path: '/rfid-kiosk', query: stationCode ? { stationCode } : undefined });
  }, 30_000);
}

function dictLabel(options: any[], value: string) {
  const label = getDictLabel(options, value);
  return label === '--' ? value : label;
}

function confirmLeave(): Promise<boolean> {
  if (!hasActiveSession.value) return Promise.resolve(true);
  return new Promise((resolve) =>
    dialog.warning({
      title: '会话仍在进行',
      content: '离开页面不会停止 RFID 设备。建议返回并停止读卡；确需离开时，会话可在刷新后恢复。',
      positiveText: '仍然离开',
      negativeText: '返回处理',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    }),
  );
}

function beforeUnload(event: BeforeUnloadEvent) {
  sikeRuntime?.stopInventoryBestEffort();
  void releaseWorkstationLease();
  if (!hasActiveSession.value) return;
  event.preventDefault();
  event.returnValue = '';
}

async function finalizeSession() {
  if (!canFinalize.value || !session.value) return;
  const sessionId = session.value.id;
  dialog.warning({
    title: '确认执行业务',
    content: '确认后将调用原有出入库/盘点业务，RFID 读数不能再修改。',
    positiveText: '确认执行',
    negativeText: '返回核对',
    onPositiveClick: async () => {
      if (!canFinalize.value || session.value?.id !== sessionId) return;
      if (!(await ensureLeaseForStation(form.workstationId))) return;
      const id = sessionId;
      const grant = await sensitiveAuthRef.value?.authorize('RFID_FINALIZE', `RFID_SESSION:${id}`);
      if (!grant) return;
      busy.value = true;
      try {
        session.value = ((await rfidApi.finalize(id, finalizeForm, grant)) as any)?.data;
        message.success('RFID 核验与业务执行完成');
      } finally {
        busy.value = false;
      }
    },
  });
}

async function createTagJob() {
  if (!tagForm.workstationId || !tagForm.collectionId || !tagForm.expectedTid)
    return message.warning('请选择发标工作站，并先读取藏品标签 TID');
  if (!(await ensureLeaseForStation(tagForm.workstationId))) return;
  busy.value = true;
  try {
    const resp: any = await rfidApi.createTagJob({ ...tagForm, clientRequestId: rfidRequestId() });
    tagJob.value = resp?.data;
    tagVerifySession.value = null;
    tagVerifyPreview.value = null;
    startTagPolling();
  } finally {
    busy.value = false;
  }
}
async function refreshTagJob() {
  const jobId = tagJob.value?.id;
  if (!jobId) return;
  const resp: any = await rfidApi.tagJob(jobId);
  tagJob.value = resp?.data;
  if (['VERIFY_PENDING', 'FAILED', 'ACTIVATED', 'CANCELLED'].includes(tagJob.value?.status || '')) stopTagPolling();
}
function startTagPolling() {
  stopTagPolling();
  void refreshTagJob();
  tagPollTimer = window.setInterval(() => void refreshTagJob(), 1200);
}
function stopTagPolling() {
  if (tagPollTimer) window.clearInterval(tagPollTimer);
  tagPollTimer = undefined;
}
async function startTidDiscovery() {
  if (!tagForm.workstationId) return message.warning('请选择发标工作站');
  if (!(await ensureLeaseForStation(tagForm.workstationId))) return;
  tagForm.expectedTid = '';
  busy.value = true;
  try {
    const response: any = await rfidApi.createSession({
      workstationId: tagForm.workstationId,
      type: 'TAG_VERIFY',
      businessType: 'RFID_TAG_DISCOVERY',
      clientRequestId: rfidRequestId(),
    });
    tidReadSession.value = response?.data;
    tidReadSession.value = ((await rfidApi.startSession(tidReadSession.value!.id)) as any)?.data;
    startTidReadPolling();
  } finally {
    busy.value = false;
  }
}
async function refreshTidDiscovery() {
  const id = tidReadSession.value?.id;
  if (!id) return;
  tidReadSession.value = ((await rfidApi.session(id)) as any)?.data;
  if (['CANCELLED', 'FAILED', 'EXPIRED'].includes(tidReadSession.value?.state || '')) stopTidReadPolling();
}
function startTidReadPolling() {
  stopTidReadPolling();
  tidReadPollTimer = window.setInterval(() => void refreshTidDiscovery(), 800);
}
function stopTidReadPolling() {
  if (tidReadPollTimer) window.clearInterval(tidReadPollTimer);
  tidReadPollTimer = undefined;
}
async function stopTidDiscovery() {
  const current = tidReadSession.value;
  if (!current) return;
  if (!(await ensureLeaseForStation(tagForm.workstationId))) return;
  busy.value = true;
  try {
    tidReadSession.value = ((await rfidApi.stopSession(current.id)) as any)?.data;
    const evidence = await waitForSessionEvidence(current.id, () => refreshTidDiscovery());
    const candidates = evidence.reads.filter((read) => Boolean(read.tid));
    if (candidates.length !== 1) throw new Error(`读写区必须只有一个带 TID 的标签，当前读取到 ${candidates.length} 个`);
    tagForm.expectedTid = candidates[0].tid!;
    await rfidApi.cancelSession(current.id);
    stopTidReadPolling();
    tidReadSession.value = null;
    message.success(`已读取并锁定标签 TID：${tagForm.expectedTid}`);
  } finally {
    busy.value = false;
  }
}
async function resetTidDiscovery() {
  const current = tidReadSession.value;
  if (current && !(await ensureLeaseForStation(tagForm.workstationId))) return;
  if (current && ['CREATED', 'PREVIEW_READY'].includes(current.state)) await rfidApi.cancelSession(current.id);
  stopTidReadPolling();
  tidReadSession.value = null;
  tagForm.expectedTid = '';
}
async function startTagVerification() {
  if (!tagJob.value || !tagForm.workstationId) return message.warning('发标任务或工作站不存在');
  if (!(await ensureLeaseForStation(tagForm.workstationId))) return;
  busy.value = true;
  try {
    const response: any = await rfidApi.createSession({
      workstationId: tagForm.workstationId,
      type: 'TAG_VERIFY',
      businessType: 'RFID_TAG',
      businessId: tagJob.value.id,
      clientRequestId: rfidRequestId(),
    });
    tagVerifySession.value = response?.data;
    tagVerifyPreview.value = null;
    tagVerifySession.value = ((await rfidApi.startSession(tagVerifySession.value!.id)) as any)?.data;
    startTagVerifyPolling();
  } finally {
    busy.value = false;
  }
}
async function refreshTagVerifySession() {
  const id = tagVerifySession.value?.id;
  if (!id) return;
  tagVerifySession.value = ((await rfidApi.session(id)) as any)?.data;
  if (tagVerifySession.value?.state === 'PREVIEW_READY') {
    tagVerifyPreview.value = ((await rfidApi.preview(id)) as any)?.data;
  }
  if (['FINALIZED', 'CANCELLED', 'FAILED', 'EXPIRED'].includes(tagVerifySession.value?.state || ''))
    stopTagVerifyPolling();
}
function startTagVerifyPolling() {
  stopTagVerifyPolling();
  tagVerifyPollTimer = window.setInterval(() => void refreshTagVerifySession(), 800);
}
function stopTagVerifyPolling() {
  if (tagVerifyPollTimer) window.clearInterval(tagVerifyPollTimer);
  tagVerifyPollTimer = undefined;
}
async function resetTagVerification() {
  const current = tagVerifySession.value;
  if (current && !(await ensureLeaseForStation(tagForm.workstationId))) return;
  if (current && ['CREATED', 'PREVIEW_READY'].includes(current.state)) await rfidApi.cancelSession(current.id);
  stopTagVerifyPolling();
  tagVerifySession.value = null;
  tagVerifyPreview.value = null;
}
async function stopAndVerifyTag() {
  if (!tagJob.value || !tagVerifySession.value) return;
  if (!(await ensureLeaseForStation(tagForm.workstationId))) return;
  const id = tagJob.value.id;
  const scanSessionId = tagVerifySession.value.id;
  busy.value = true;
  try {
    tagVerifySession.value = ((await rfidApi.stopSession(scanSessionId)) as any)?.data;
    const evidence = await waitForSessionEvidence(scanSessionId, () => refreshTagVerifySession());
    const target = evidence.reads.find(
      (read) => read.epc === tagJob.value.targetEpc && read.tid === tagJob.value.expectedTid,
    );
    if (!target) throw new Error('设备未读到目标 EPC/TID，请隔离标签后重新读取');
    const grant = await sensitiveAuthRef.value?.authorize('RFID_TAG_ACTIVATE', `RFID_TAG_JOB:${id}`);
    if (!grant) return;
    tagJob.value = ((await rfidApi.verifyTag(id, scanSessionId, grant)) as any)?.data;
    await refreshTagVerifySession();
    stopTagPolling();
    message.success('设备读回 EPC/TID 一致，标签已激活绑定');
  } finally {
    busy.value = false;
  }
}
async function waitForSessionEvidence(sessionId: string, refresh: () => Promise<void>): Promise<RfidScanPreview> {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    await refresh();
    const current = tidReadSession.value?.id === sessionId ? tidReadSession.value : tagVerifySession.value;
    if (current?.state === 'FAILED') throw new Error(current.errorMessage || '设备停止失败');
    if (current?.state === 'PREVIEW_READY') {
      const evidence = ((await rfidApi.preview(sessionId)) as any)?.data as RfidScanPreview;
      if (tagVerifySession.value?.id === sessionId) tagVerifyPreview.value = evidence;
      if (evidence.settled) return evidence;
    }
    await new Promise((resolve) => window.setTimeout(resolve, 500));
  }
  throw new Error('等待设备停止和读数收敛超时');
}

onBeforeRouteLeave(() => confirmLeave());
onMounted(() => {
  if (previewOnly) {
    initializePreviewDesk();
    return;
  }
  if (standalone && sessionStorage.getItem(KIOSK_SESSION_KEY) === 'pending') {
    const token = moveTokenToSession('/rfid-kiosk');
    if (token) {
      authStore.token = token;
      sessionStorage.setItem(KIOSK_SESSION_KEY, 'active');
    }
  }
  window.addEventListener('beforeunload', beforeUnload);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  startIdleLogout();
  void initializeDesk();
});
onBeforeUnmount(() => {
  if (previewOnly) return;
  window.removeEventListener('beforeunload', beforeUnload);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  void releaseWorkstationLease();
  disconnectRealtime();
  stopPolling();
  stopTagPolling();
  stopTidReadPolling();
  stopTagVerifyPolling();
  stopLeaseHeartbeat();
  stopIdleLogout();
});
</script>

<style scoped>
.rfid-desk {
  box-sizing: border-box;
  padding: 16px;
  min-height: 100%;
  background: #f4f6f8;
}
.rfid-desk.standalone {
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  padding: 0;
  background: #f3f8f9;
}
.standalone .desk-card {
  height: 100%;
  min-height: 0;
  border-radius: 0;
  background: #f3f8f9;
}
.desk-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 13px;
}
.desk-title .brand-mark {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: #fff;
  font-weight: 800;
  background: linear-gradient(145deg, #15869a, #0c566d);
  box-shadow: 0 8px 20px rgb(15 102 132 / 16%);
}
.desk-title > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}
.desk-title strong,
.desk-title small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.desk-title strong {
  font-size: 18px;
}
.desk-title small {
  color: #78909c;
  font-size: 12px;
  font-weight: 400;
}
.desk-title .idle-hint {
  color: #91a4ad;
  font-size: 11px;
}
.station-error {
  margin: 20px 0;
}
.operation-picker {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 12px;
}
.operation-card {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 4px 12px;
  min-height: 76px;
  padding: 12px;
  color: #33454f;
  text-align: left;
  border: 1px solid #d8e3e7;
  border-radius: 14px;
  background: #fff;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}
.operation-card > span {
  grid-row: 1 / 3;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  color: #0f718e;
  font-size: 18px;
  font-weight: 800;
  background: #e9f5f8;
}
.operation-card strong {
  align-self: end;
  font-size: 16px;
}
.operation-card small {
  color: #78909c;
  font-size: 12px;
}
.operation-card:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: #59a8be;
  box-shadow: 0 9px 24px rgb(31 99 119 / 12%);
}
.operation-card.active {
  border-color: #137695;
  box-shadow: 0 0 0 2px rgb(19 118 149 / 12%);
  background: #f7fcfd;
}
.operation-card.active > span {
  color: #fff;
  background: #137695;
}
.operation-card:disabled {
  cursor: not-allowed;
  opacity: 0.68;
}
.session-form,
.tag-form {
  margin-bottom: 16px;
}
.standalone .session-form {
  box-sizing: border-box;
  align-items: flex-end;
  padding: 16px 18px;
  margin-bottom: 12px;
  border: 1px solid #dfebee;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(34 82 95 / 5%);
}
.summary {
  margin: 18px 0;
}
.standalone .summary {
  box-sizing: border-box;
  margin: 12px 0;
  padding: 12px;
  border: 1px solid #dfebee;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgb(34 82 95 / 5%);
}
.summary-item {
  box-sizing: border-box;
  width: 100%;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.summary-item:hover:not(:disabled),
.summary-item:focus-visible {
  background: #e9f5f8;
  outline: 2px solid #137695;
  outline-offset: 3px;
}
.summary-item:disabled {
  cursor: default;
}
.standalone .summary-item {
  min-height: 68px;
  padding: 10px 12px;
  border-radius: 10px;
}
.actions {
  margin: 0;
}
.sticky-actions {
  padding: 12px;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid #e5e7eb;
  box-shadow: 0 -4px 14px rgba(15, 23, 42, 0.08);
}
.block-reasons,
.finalize-card,
.verify-form {
  margin-top: 18px;
}
.tag-form {
  max-width: 760px;
}
.standalone :deep(.single-tab > .n-tabs-nav) {
  display: none;
}
.desk-card > :deep(.n-card__footer) {
  padding: 0;
}
.rfid-desk:not(.standalone) .desk-card > :deep(.n-card__footer) {
  position: sticky;
  bottom: 0;
  z-index: 5;
}
.standalone .desk-card > :deep(.n-card-header),
.standalone .desk-card > :deep(.n-card__footer) {
  flex-shrink: 0;
}
.standalone .desk-card > :deep(.n-card-header) {
  box-sizing: border-box;
  gap: 16px;
  padding: 16px 24px;
  border-bottom: 1px solid #e3ecef;
  background: #fff;
}
.standalone .desk-card > :deep(.n-card-header .n-card-header__main) {
  min-width: 0;
}
.standalone .desk-card > :deep(.n-card-content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 18px 24px 24px;
  background: radial-gradient(circle at 8% 0%, rgb(26 126 154 / 7%), transparent 32%), #f3f8f9;
}
.standalone .station-error {
  margin: 0 0 8px;
}
.standalone .block-reasons {
  margin: 0 0 8px;
}
@media (max-width: 900px) {
  .operation-picker {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .standalone .desk-card > :deep(.n-card-header) {
    flex-wrap: wrap;
    padding: 12px 16px;
  }
  .standalone .desk-card > :deep(.n-card-header .n-card-header__main) {
    flex-basis: 100%;
  }
  .standalone .desk-card > :deep(.n-card-content) {
    padding: 0 16px 12px;
  }
}
@media (max-width: 540px) {
  .operation-picker {
    grid-template-columns: 1fr;
  }
}
</style>
