<template>
  <HPageForm
    v-model:show="show"
    :model="attendance"
    :title="leaving ? '实际离库登记' : '实际共同入库登记'"
    label-width="220"
  >
    <NAlert v-if="error" type="error"
      >{{ error }} <NButton :disabled="loading" @click="reload">重新核对</NButton></NAlert
    >
    <NSpin :show="loading">
      <NSpace v-if="context && !loading" vertical>
        <NText
          >原收藏单位：{{ context.orgId }} · 申请：{{ context.application.applicationNo || context.application.id }} ·
          核验账号：{{ context.actorId }}</NText
        >
        <WarehouseEntryEvidenceView :data="context.application" />
        <NAlert v-if="!allowed" type="warning">当前申请不能继续此项核验，请核对已保存记录及当前状态。</NAlert>
        <WarehouseRecordFields
          v-if="allowed && !busy"
          :key="`attendance-${epoch}`"
          :model-value="attendance"
          :fields="warehouseAttendanceFields(attendance, leaving)"
          @update:model-value="updateAttendance"
        />
      </NSpace>
    </NSpin>
    <template #form-action
      ><NButton
        v-if="context && allowed && !error"
        type="primary"
        :loading="busy"
        :disabled="loading || busy"
        @click="save"
        >核实人员及原件后登记</NButton
      ></template
    >
  </HPageForm>
</template>
<script setup lang="ts">
import { computed, ref, shallowRef, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useMessage } from 'naive-ui';
import { warehouseEntryApi as api } from '../api/warehouseEntry';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import WarehouseEntryEvidenceView from './WarehouseEntryEvidenceView.vue';
import {
  warehouseAttendanceFields,
  warehouseEvidenceInitial,
  warehouseEvidencePayload,
  warehouseFieldError,
} from '../utils/warehouseEvidenceFields';
type Context = {
  application: Record<string, any>;
  orgId: string;
  actorId: string;
  revision: string;
  canCheckIn: boolean;
  canCheckOut: boolean;
};
type AttendanceSaved = { id: string; orgId: string; actorId: string; leaving: boolean };
const emit = defineEmits<{ (e: 'saved', value: AttendanceSaved): void }>();
const message = useMessage(),
  show = ref(false),
  loading = ref(false),
  busy = ref(false),
  error = ref('');
const leaving = ref(false),
  attendance = ref<Record<string, any>>({}),
  context = shallowRef<Context>();
const epoch = ref(0),
  active = ref(true),
  updateAttendance = shallowRef<(value: Record<string, any>) => void>(() => {});
let applicationId = '';
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
const time = (v: unknown) => typeof v === 'number' && Number.isSafeInteger(v);
const allowed = computed(
  () => !!context.value && (leaving.value ? context.value.canCheckOut : context.value.canCheckIn),
);
function invalidate() {
  ++epoch.value;
  context.value = undefined;
  attendance.value = {};
  loading.value = false;
  busy.value = false;
  error.value = '';
  updateAttendance.value = () => {};
}
function owned(token: number) {
  return token === epoch.value && show.value && active.value;
}
function close() {
  show.value = false;
  invalidate();
}
watch(
  show,
  (value) => {
    if (!value) invalidate();
  },
  { flush: 'sync' },
);
onBeforeUnmount(() => {
  active.value = false;
  close();
});
onDeactivated(() => {
  active.value = false;
  close();
});
onActivated(() => {
  active.value = true;
});
function responseContext(response: any, id: string): Context {
  const d = response?.data,
    a = d?.application;
  if (
    (response?.status !== undefined && response.status !== 200) ||
    !d ||
    !a ||
    a.id !== id ||
    !text(d.orgId) ||
    a.orgId !== d.orgId ||
    !text(d.actorId) ||
    !text(d.revision) ||
    !/^[0-9a-f]{64}$/.test(d.revision) ||
    typeof d.canCheckIn !== 'boolean' ||
    typeof d.canCheckOut !== 'boolean' ||
    !text(a.warehouseIds) ||
    typeof a.entrantIds !== 'string' ||
    (a.archives != null && !Array.isArray(a.archives)) ||
    (a.actualEntryAt != null && !time(a.actualEntryAt)) ||
    (a.actualExitAt != null && !time(a.actualExitAt)) ||
    (d.canCheckIn && (a.flowStatus !== 'PASS' || !a.baseline || !a.dossier || a.actualEntryAt != null)) ||
    (d.canCheckOut && (a.actualEntryAt == null || a.actualExitAt != null || !a.dossier))
  )
    throw Error('核验响应缺少一致的申请、原馆、账号或版本，请重新核对');
  return cloneDeep(d);
}
async function open(id: string, exit = false) {
  if (!active.value) return false;
  invalidate();
  applicationId = id;
  leaving.value = exit;
  show.value = true;
  loading.value = true;
  const token = epoch.value;
  try {
    if (!text(id)) throw Error('人员入库申请编号缺失');
    const response = await api.attendanceContext(id);
    if (!owned(token)) return false;
    const d = responseContext(response, id);
    context.value = d;
    attendance.value = warehouseEvidenceInitial({
      entrantUserIds: d.application.entrantIds.split(',').filter(Boolean),
      escortUserIds: cloneDeep(d.application.dossier?.escortUserIds || []),
      externalNames: cloneDeep(d.application.dossier?.externalNames || []),
    });
    updateAttendance.value = (value) => {
      if (owned(token) && !busy.value) attendance.value = cloneDeep(value);
    };
    return true;
  } catch (e) {
    if (owned(token)) error.value = e instanceof Error ? e.message : '核验依据读取失败';
    return false;
  } finally {
    if (owned(token)) loading.value = false;
  }
}
async function reload() {
  if (!loading.value && !busy.value) return open(applicationId, leaving.value);
}
function sameRecord(actual: any, expected: Record<string, any>): boolean {
  if (
    !actual ||
    actual.occurredAt !== expected.occurredAt ||
    actual.identityChecked !== expected.identityChecked ||
    actual.continuousAccompaniment !== expected.continuousAccompaniment ||
    actual.signedRecordFileIds !== expected.signedRecordFileIds
  )
    return false;
  for (const k of ['entrantUserIds', 'escortUserIds', 'externalNames']) {
    const a = actual[k] ?? [],
      b = expected[k] ?? [];
    if (
      !Array.isArray(a) ||
      !Array.isArray(b) ||
      a.some((x) => typeof x !== 'string') ||
      b.some((x) => typeof x !== 'string') ||
      JSON.stringify([...a].sort()) !== JSON.stringify([...b].sort())
    )
      return false;
  }
  return ['remark', 'exceptionMeasures', 'exceptionReportFileIds'].every(
    (k) => (actual[k] ?? '') === (expected[k] ?? ''),
  );
}
async function save() {
  if (!show.value || !active.value || !context.value || !allowed.value || loading.value || busy.value || error.value)
    return false;
  const missing = warehouseFieldError(warehouseAttendanceFields(attendance.value, leaving.value), attendance.value);
  if (missing) {
    message.warning(missing);
    return false;
  }
  const token = epoch.value,
    c = context.value,
    id = c.application.id,
    exit = leaving.value;
  const value = warehouseEvidencePayload(cloneDeep(attendance.value));
  if (!time(value.occurredAt)) {
    message.warning('请填写有效的实际核验时间');
    return false;
  }
  busy.value = true;
  try {
    const receipt: any = await (exit ? api.checkOut : api.checkIn)(id, value, c.orgId, c.revision);
    if (!owned(token)) return false;
    const a = receipt?.data,
      key = exit ? 'exitRecord' : 'entryRecord',
      actorKey = exit ? 'checkOutBy' : 'checkInBy',
      atKey = exit ? 'actualExitAt' : 'actualEntryAt';
    if (
      (receipt?.status !== undefined && receipt.status !== 200) ||
      a?.id !== id ||
      a.orgId !== c.orgId ||
      a[actorKey] !== c.actorId ||
      a[atKey] !== value.occurredAt ||
      !sameRecord(a[key], value)
    )
      throw Error('核验回执与原申请或本次登记不一致，请重新核对实际保存结果');
    const response = await api.attendanceContext(id);
    if (!owned(token)) return false;
    const fresh = responseContext(response, id),
      saved = fresh.application;
    if (
      fresh.orgId !== c.orgId ||
      fresh.actorId !== c.actorId ||
      fresh.revision === c.revision ||
      saved[actorKey] !== c.actorId ||
      saved[atKey] !== value.occurredAt ||
      !sameRecord(saved[key], value)
    )
      throw Error('未能从原申请回查本次核验结果，请重新核对');
    close();
    emit('saved', { id, orgId: c.orgId, actorId: c.actorId, leaving: exit });
    return true;
  } catch (e) {
    if (owned(token)) {
      context.value = undefined;
      attendance.value = {};
      error.value = e instanceof Error ? e.message : '核验未完成，请重新核对实际结果';
    }
    return false;
  } finally {
    if (owned(token)) busy.value = false;
  }
}
defineExpose({ open, close });
</script>
