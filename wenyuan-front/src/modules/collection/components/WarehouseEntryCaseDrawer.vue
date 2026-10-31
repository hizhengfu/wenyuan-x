<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="人员入库办理">
    <NAlert v-if="readError" type="error">
      {{ readError }}<NButton v-if="recoveryId" :disabled="reading" @click="reload">重新读取申请</NButton>
    </NAlert>
    <div v-if="!reading && !readError" class="warehouse-entry-case">
      <CollectionArchiveIssues :issues="form.evidenceErrors" />
      <div class="warehouse-entry-case__summary">
        <div class="warehouse-entry-case__heading">
          <NText strong>{{ form.applicationNo || '新建入库申请' }}</NText>
          <NSpace :size="8">
            <NTag v-if="form.flowStatus" size="small" :bordered="false"
              ><HDictText type="flow_biz_status" :value="form.flowStatus"
            /></NTag>
            <NTag v-if="form.id" size="small" :bordered="false" :type="form.actualExitAt ? 'success' : 'info'">{{
              attendanceStatus
            }}</NTag>
          </NSpace>
        </div>
        <NText v-if="context" depth="3">收藏单位：{{ context.orgName }} · 当前办理人：{{ context.actorName }}</NText>
        <NText v-if="form.applicantName" depth="3">申请人：{{ form.applicantName }}</NText>
      </div>
      <NSpace size="small" align="center">
        <NButton v-if="editable" type="primary" :loading="busy" @click="save">保存入库方案</NButton>
        <NButton v-if="context?.canSubmit" type="primary" :disabled="busy" @click="submit">提交实际负责人确认</NButton>
        <NButton
          v-if="
            canVerifyAttendance &&
            !form.evidenceErrors?.length &&
            form.flowStatus === 'PASS' &&
            form.baseline &&
            !form.actualEntryAt
          "
          @click="attend(false)"
          >登记实际共同入库</NButton
        >
        <NButton v-if="canVerifyAttendance && form.actualEntryAt && !form.actualExitAt" @click="attend(true)"
          >登记实际离库</NButton
        >
        <CollectionActionMenu label="更多" v-if="form.id || context?.canVoid">
          <NButton quaternary v-if="form.id" @click="flowRef?.open(form.id, '人员入库确认')">查看确认流程</NButton>
          <NButton quaternary v-if="context?.canVoid" :disabled="busy" @click="voidCase">作废申请</NButton>
        </CollectionActionMenu>
      </NSpace>
      <NTabs v-model:value="detailTab" type="line" animated>
        <NTabPane name="application" tab="申请与人员安排" display-directive="show">
          <div class="warehouse-entry-case__panel">
            <template v-if="editable && !busy">
              <NAlert type="info"
                >先核实实际库管和入库人员，再提交保管负责人确认。库管不足两人的例外须由主要负责人指定本馆其他人员陪同；非库管来访须由两名实际库管陪同。</NAlert
              >
              <NForm label-width="190">
                <NCard title="入库安排" size="small">
                  <NFormItem label="实际拟进入库房" required>
                    <WarehouseTreeSelect
                      v-model:value="warehouseIds"
                      :org-id="context?.orgId"
                      multiple
                      scene-type="STORAGE"
                      :allowed-types="['WAREHOUSE', 'AREA']"
                    />
                  </NFormItem>
                  <NFormItem label="本馆实际入库人员"><UserSelect v-model:value="entrantIds" multiple /></NFormItem>
                  <WarehouseRecordFields
                    :key="`draft-${windowEpoch}`"
                    :model-value="form"
                    @update:model-value="draftChanged"
                    :fields="baseFields"
                  />
                </NCard>
                <NCard title="人员与陪同方案" size="small" class="warehouse-entry-case__form-section">
                  <WarehouseRecordFields
                    :key="`dossier-${windowEpoch}`"
                    :model-value="form.dossier"
                    @update:model-value="dossierChanged"
                    :fields="warehouseEntryFields(form.dossier)"
                  />
                </NCard>
              </NForm>
            </template>
            <WarehouseEntryEvidenceView
              v-if="form.id"
              :data="form"
              :section="editable ? 'staffing' : 'application'"
              :archive-files="currentOriginalFiles" :original-url="currentOriginalUrl"
              :open-staffing="openStaffing"
            />
          </div>
        </NTabPane>
        <NTabPane v-if="form.id" name="attendance" tab="出入库记录" display-directive="show">
          <div class="warehouse-entry-case__panel">
            <WarehouseEntryEvidenceView :data="form" section="attendance" :archive-files="currentOriginalFiles" :original-url="currentOriginalUrl" />
            <NAlert v-if="form.actualExitAt" type="info"
              >补录离库后，请核对关联原件操作时间；已有操作和进出原件继续保留。</NAlert
            >
            <NSpace>
              <NButton
                v-if="form.id && form.actualExitAt"
                v-permission="'COLLECTION_ADMIN,COLLECTION_WAREHOUSE_ENTRY_VERIFY'"
                @click="timelineRef?.open(form.id)"
                >核对离库与原件操作时间</NButton
              >
            </NSpace>
          </div>
        </NTabPane>
        <NTabPane
          v-if="form.id"
          name="archives"
          :tab="`原件档案（${form.archives?.length || 0}）`"
          display-directive="show"
        >
          <NEmpty v-if="!form.archives?.length" :description="form.evidenceErrors?.length ? '归档原件当前不可查阅，请核查上方提示' : '尚无永久原件档案'" />
          <NCollapse v-else>
            <NCollapseItem v-for="a in form.archives" :key="a.id" :name="a.id" :title="archiveTitle(a)">
              <div class="warehouse-entry-case__panel">
                <NSpace>
                  <HFileList :files="a.files" :original-url="(fileId: string) => archiveOriginalUrl(a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn />
                </NSpace>
                <WarehouseEntryEvidenceView
                  v-if="snapshot(a).proposal || snapshot(a).dossier"
                  :data="snapshot(a).proposal || snapshot(a)"
                  :archive-files="a.files" :original-url="(fileId) => archiveOriginalUrl(a, fileId)"
                  :open-staffing="openStaffing"
                />
                <NAlert v-else-if="snapshot(a).kind === 'ONSITE_TIME_REVIEW'" type="info"
                  >时间冲突核查材料，原始记录继续保留；详细核查情况见时间核查入口。</NAlert
                >
                <CollectionEvidenceReadView
                  v-else
                  :fields="warehouseAttendanceFields(snapshot(a), a.phase === 'CHECKED_OUT')"
                  :data="warehouseEvidenceInitial(snapshot(a))"
                  :archive-files="a.files" :original-url="(fileId) => archiveOriginalUrl(a, fileId)"
                  label-placement="left"
                  size="small"
                />
              </div>
            </NCollapseItem>
          </NCollapse>
        </NTabPane>
      </NTabs>
    </div>
  </HPageForm>
  <WarehouseStaffingDrawer v-if="show" ref="staffingRef" read-only />
  <WarehouseAttendanceDrawer
    v-if="show"
    :key="`attendance-${windowEpoch}`"
    ref="attendanceRef"
    @saved="attendanceSaved"
  />
  <WarehouseEntryTimelineDrawer v-if="show" :key="`timeline-${windowEpoch}`" ref="timelineRef" />
  <FlowFormDrawer
    v-if="show"
    :key="`flow-${windowEpoch}`"
    ref="flowRef"
    :fetch-fn="warehouseEntryApi.flow"
    :cancel-api="cancelFlow"
    @success="flowSaved"
  />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
const currentOriginalFiles = computed(() => {
  const selected = new Map<string, any>();
  for (const archive of [...(form.value.archives || [])].reverse())
    for (const file of archive.files || []) if (!selected.has(file.fileId)) selected.set(file.fileId, file);
  return [...selected.values()];
});
import { computed, ref, watch, nextTick, onBeforeUnmount, onDeactivated, onActivated, shallowRef } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { cloneDeep } from 'lodash-es';
import { useMessage, useDialog } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import WarehouseEntryEvidenceView from './WarehouseEntryEvidenceView.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import WarehouseStaffingDrawer from './WarehouseStaffingDrawer.vue';
import WarehouseEntryTimelineDrawer from './WarehouseEntryTimelineDrawer.vue';
import WarehouseAttendanceDrawer from './WarehouseAttendanceDrawer.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import { warehouseEntryApi } from '../api/warehouseEntry';
import {
  warehouseEntryFields,
  warehouseEntryBaseFields,
  warehouseAttendanceFields,
  warehouseEvidenceInitial,
  warehouseEvidencePayload,
  warehouseFieldError,
} from '../utils/warehouseEvidenceFields';
import {
  warehouseApplicationResponse,
  sameWarehouseProposal,
  type WarehouseApplicationContext,
} from '../utils/warehouseApplicationContext';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  dialog = useDialog();
const auth = useAuthStore();
const show = ref(false),
  busy = ref(false),
  reading = ref(false),
  readError = ref('');
const form = ref<any>({ dossier: { kind: 'KEEPERS' } }),
  warehouseIds = ref<string[]>([]),
  entrantIds = ref<string[]>([]);
const context = shallowRef<WarehouseApplicationContext>(),
  flowRef = ref<any>(),
  timelineRef = ref<any>(),
  attendanceRef = ref<any>(),
  staffingRef = ref<{ open: (id?: string) => void }>();
const detailTab = ref('application');
const windowEpoch = ref(0),
  active = ref(true),
  flowSaved = shallowRef<() => void>(() => {}),
  cancelFlow = shallowRef<(id: string) => Promise<any>>();
let opening = false,
  readRequest = 0;
const recoveryId = ref<string>();
function openStaffing(id: string) {
  staffingRef.value?.open(id);
}
const baseFields = warehouseEntryBaseFields.filter((f) => !['entrantIds', 'externalEntrants'].includes(f.key));
const canVerifyAttendance = computed(
  () =>
    !!auth.userInfo?.id &&
    auth.userInfo.enabled === true &&
    context.value?.actorId === auth.userInfo.id &&
    auth.hasPermission('ADMIN,COLLECTION_ADMIN,COLLECTION_WAREHOUSE_ENTRY_VERIFY'),
);
const editable = computed(() => context.value?.canSave === true);
const snapshot = (a: any) => JSON.parse(a.snapshot);
const attendanceStatus = computed(() =>
  form.value.actualExitAt
    ? '已离库'
    : form.value.actualEntryAt
      ? '已入库'
      : form.value.flowStatus === 'PASS'
        ? form.value.baseline
          ? '待入库'
          : '旧材料待核实'
        : '尚未入库',
);
function archiveTitle(archive: any) {
  const phase = String(archive.phase || '');
  const label =
    phase === 'CHECKED_IN'
      ? '入库登记原件'
      : phase === 'CHECKED_OUT'
        ? '离库登记原件'
        : phase.startsWith('APPROVED_')
          ? '批准方案与确认意见'
          : phase.startsWith('ONSITE_TIME_REVIEW_')
            ? '操作时间核查原件'
            : '永久原件';
  return `${label} · ${formatLocalDateValue(archive.archivedAt, 'YYYY-MM-DD HH:mm:ss')}`;
}
const archiveOriginalUrl = (archive: any, fileId: string): string =>
  form.value.id && archive?.files?.some((file: any) => file.fileId === fileId)
    ? `/collection/warehouse-entry/${encodeURIComponent(form.value.id)}/original/${encodeURIComponent(archive.id)}/${encodeURIComponent(fileId)}`
    : '';
const currentOriginalUrl = (fileId: string): string => {
  const archive = [...(form.value.archives || [])]
    .reverse()
    .find((a: any) => a.files?.some((file: any) => file.fileId === fileId));
  return archive ? archiveOriginalUrl(archive, fileId) : '';
};
const fieldChanged = (dossier: boolean) =>
  computed(() => {
    const request = readRequest,
      c = context.value,
      epoch = windowEpoch.value;
    return (value: Record<string, any>) => {
      if (!c || !current(request, c) || epoch !== windowEpoch.value || busy.value || reading.value || !c.canSave)
        return;
      if (dossier) form.value.dossier = cloneDeep(value);
      else form.value = cloneDeep(value);
    };
  });
const draftChanged = fieldChanged(false),
  dossierChanged = fieldChanged(true);
watch(
  () => form.value.dossier?.kind,
  (kind, old) => {
    if (opening || !old || kind === old || form.value.flowInstanceId) return;
    if (kind === 'KEEPERS') {
      form.value.dossier.escortUserIds = [];
      form.value.dossier.externalNames = '';
    }
    if (kind !== 'STAFF_SHORTAGE') {
      form.value.dossier.leaderUserId = undefined;
      form.value.dossier.shortageReason = undefined;
      form.value.dossier.shortageFileIds = undefined;
    }
    if (kind === 'STAFF_SHORTAGE') form.value.dossier.externalNames = '';
  },
);
function current(request: number, c?: WarehouseApplicationContext) {
  return request === readRequest && active.value && show.value && (!c || context.value === c);
}
function invalidate() {
  detailTab.value = 'application';
  ++readRequest;
  ++windowEpoch.value;
  attendanceRef.value?.close();
  context.value = undefined;
  form.value = { dossier: { kind: 'KEEPERS' } };
  warehouseIds.value = [];
  entrantIds.value = [];
  reading.value = false;
  busy.value = false;
  readError.value = '';
  opening = false;
  flowSaved.value = () => {};
  cancelFlow.value = undefined;
}
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
watch(
  () => [auth.userInfo?.id, auth.userInfo?.groupId, auth.userInfo?.enabled, auth.permissions],
  () => {
    if (show.value) invalidate();
  },
  { deep: true, flush: 'sync' },
);
onBeforeUnmount(() => {
  active.value = false;
  show.value = false;
  invalidate();
});
onDeactivated(() => {
  active.value = false;
  show.value = false;
  invalidate();
});
onActivated(() => {
  active.value = true;
});
function accept(c: WarehouseApplicationContext, request: number) {
  opening = true;
  ++windowEpoch.value;
  attendanceRef.value?.close();
  context.value = c;
  form.value = cloneDeep(c.application || { orgId: c.orgId, flowStatus: 'DRAFT', dossier: { kind: 'KEEPERS' } });
  form.value.dossier = warehouseEvidenceInitial(form.value.dossier || { kind: 'KEEPERS' });
  warehouseIds.value = String(form.value.warehouseIds || '')
    .split(',')
    .filter(Boolean);
  entrantIds.value = String(form.value.entrantIds || '')
    .split(',')
    .filter(Boolean);
  recoveryId.value = c.application?.id;
  flowSaved.value = () => {
    if (current(request, c)) void open(c.application?.id);
  };
  cancelFlow.value = c.canCancel
    ? async (id) => {
        if (!current(request, c) || id !== c.application?.id || busy.value || reading.value)
          throw Error('原撤销窗口已失效，请重新打开申请');
        return runOperation('cancel', request, c);
      }
    : undefined;
}
function failed(request: number, c: WarehouseApplicationContext, error: unknown) {
  if (!current(request, c)) return;
  readError.value =
    (error instanceof Error ? error.message : '办理结果未能确认') +
    '。请重新核对实际保存结果；新建回执不明时请先检查申请列表。';
  context.value = undefined;
  cancelFlow.value = undefined;
  flowSaved.value = () => {};
}
async function open(id?: string, mode?: 'in' | 'out') {
  if (!active.value) return false;
  invalidate();
  recoveryId.value = id;
  const request = readRequest;
  reading.value = true;
  show.value = true;
  opening = true;
  try {
    const response = await warehouseEntryApi.applicationContext(id);
    if (!current(request)) return false;
    accept(warehouseApplicationResponse(response, id), request);
    await nextTick();
    if (!current(request)) return false;
    if (canVerifyAttendance.value && (mode === 'out' || (mode === 'in' && form.value.baseline && !form.value.evidenceErrors?.length)))
      await attendanceRef.value?.open(form.value.id, mode === 'out');
    return current(request);
  } catch (error) {
    if (current(request)) readError.value = error instanceof Error ? error.message : '人员入库资料读取失败';
    return false;
  } finally {
    if (current(request)) {
      reading.value = false;
      opening = false;
    }
  }
}
async function readBack(
  response: any,
  c: WarehouseApplicationContext,
  request: number,
  id: string,
  payload?: Record<string, any>,
  operation?: 'submit' | 'cancel' | 'void',
) {
  const receipt = warehouseApplicationResponse(response, id);
  if (
    receipt.orgId !== c.orgId ||
    receipt.actorId !== c.actorId ||
    (payload && !sameWarehouseProposal(receipt.application, payload))
  )
    throw Error('办理回执与原申请、原馆、账号或填写内容不一致');
  if (operation) {
    const state = receipt.application?.flowStatus;
    if (
      operation === 'submit'
        ? !['AUDIT', 'PASS'].includes(state)
        : state !== (operation === 'cancel' ? 'CANCEL' : 'VOID')
    )
      throw Error('办理回执状态不符');
  }
  recoveryId.value = id;
  const response2 = await warehouseEntryApi.applicationContext(id);
  if (!current(request, c)) return;
  const fresh = warehouseApplicationResponse(response2, id);
  if (
    fresh.orgId !== c.orgId ||
    fresh.actorId !== c.actorId ||
    fresh.revision !== receipt.revision ||
    (payload && !sameWarehouseProposal(fresh.application, payload)) ||
    fresh.application?.flowStatus !== receipt.application?.flowStatus
  )
    throw Error('保存后的原申请回查不一致，请重新核对');
  return fresh;
}
async function save() {
  const c = context.value,
    request = readRequest;
  if (!c || !current(request, c) || !c.canSave || busy.value || reading.value || readError.value) return;
  const error =
    warehouseFieldError(baseFields, form.value) ||
    warehouseFieldError(warehouseEntryFields(form.value.dossier), form.value.dossier);
  if (error || !warehouseIds.value.length) {
    message.warning(error || '请选择实际库房');
    return;
  }
  const payload = warehouseEvidencePayload(
    cloneDeep({ ...form.value, warehouseIds: warehouseIds.value.join(','), entrantIds: entrantIds.value.join(',') }),
  );
  busy.value = true;
  try {
    const response: any = await warehouseEntryApi.save(payload, c.orgId, c.revision);
    if (!current(request, c)) return;
    const id = response?.data?.application?.id;
    if (typeof id !== 'string' || !id.trim() || (c.application && id !== c.application.id))
      throw Error('保存回执的申请编号不符');
    const fresh = await readBack(response, c, request, id, payload);
    if (!fresh || !current(request, c)) return;
    accept(fresh, request);
    await nextTick();
    if (!current(request, fresh)) return;
    opening = false;
    emit('saved');
    if (current(request, fresh)) return fresh;
  } catch (error) {
    failed(request, c, error);
  } finally {
    if (current(request)) busy.value = false;
  }
}
async function runOperation(operation: 'submit' | 'cancel' | 'void', request: number, c: WarehouseApplicationContext) {
  const flag = operation === 'submit' ? 'canSubmit' : operation === 'cancel' ? 'canCancel' : 'canVoid';
  if (!current(request, c) || !c[flag] || busy.value || reading.value || !c.application?.id)
    throw Error('原办理窗口已失效，请重新核对');
  busy.value = true;
  try {
    const response = await warehouseEntryApi[operation](c.application.id, c.orgId, c.revision);
    if (!current(request, c)) throw Error('原办理窗口已关闭或切换');
    const fresh = await readBack(response, c, request, c.application.id, undefined, operation);
    if (!fresh || !current(request, c)) throw Error('原办理窗口已关闭或切换');
    accept(fresh, request);
    await nextTick();
    if (!current(request, fresh)) throw Error('原办理窗口已关闭或切换');
    opening = false;
    emit('saved');
    return response;
  } catch (error) {
    failed(request, c, error);
    throw error;
  } finally {
    if (current(request)) busy.value = false;
  }
}
async function submit() {
  const saved = await save();
  if (!saved || !current(readRequest, saved)) return;
  try {
    await runOperation('submit', readRequest, saved);
  } catch {
    /* 当前窗口已显示可恢复错误；失效窗口不处理回调。 */
  }
}
async function attend(exit: boolean) {
  if (
    canVerifyAttendance.value &&
    (exit || !form.value.evidenceErrors?.length) &&
    active.value &&
    show.value &&
    context.value &&
    !busy.value &&
    !reading.value &&
    form.value.id
  )
    return attendanceRef.value?.open(form.value.id, exit);
}
async function attendanceSaved(saved: { id: string; orgId: string; leaving: boolean }) {
  if (!active.value || !show.value || form.value.id !== saved.id || form.value.orgId !== saved.orgId) return;
  if (!(await open(saved.id)) || form.value.orgId !== saved.orgId) return;
  emit('saved');
  if (saved.leaving) await timelineRef.value?.open(saved.id);
}
async function voidCase() {
  const c = context.value,
    request = readRequest;
  if (!c || !current(request, c) || !c.canVoid || busy.value || reading.value) return;
  const ok = await new Promise<boolean>((resolve) =>
    dialog.warning({
      title: '作废尚未入库的申请',
      content: '已登记的方案和原件继续保留。',
      positiveText: '确认作废',
      negativeText: '取消',
      onPositiveClick: () => resolve(true),
      onNegativeClick: () => resolve(false),
      onClose: () => resolve(false),
    }),
  );
  if (!ok || !current(request, c) || busy.value) return;
  try {
    await runOperation('void', request, c);
  } catch {
    /* 显示当前窗口错误，不报告成功。 */
  }
}
async function reload() {
  if (show.value && active.value && recoveryId.value) await open(recoveryId.value);
}
defineExpose({ open });
</script>

<style scoped>
.warehouse-entry-case,
.warehouse-entry-case__panel {
  display: grid;
  gap: 16px;
  min-width: 0;
}
.warehouse-entry-case__summary {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--n-border-color);
  border-radius: 4px;
  overflow-wrap: anywhere;
}
.warehouse-entry-case__heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 18px;
}
.warehouse-entry-case__form-section {
  margin-top: 16px;
}
</style>
