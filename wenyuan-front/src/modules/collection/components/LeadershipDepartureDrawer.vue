<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="法定代表人、主要负责人离任交接">
    <NSpace vertical :size="16">
      <NAlert type="info"
        >先核对原任职、离任和接任依据，再关联全馆清点。停用账号可按原账号精确登记，历史材料不恢复当前权限。国有馆须保留主管文物行政部门实际派员监交原件；系统不代填签署和移交事实。</NAlert
      >
      <NSelect
        :value="orgId ?? null"
        :options="orgOptions"
        :disabled="busy"
        placeholder="选择收藏单位"
        @update:value="select"
      />
      <NSpace
        ><NButton :disabled="!orgId || loading || busy || !!data.evidenceErrors?.length" @click="edit()"
          >登记负责人离任交接案</NButton
        ><NButton :disabled="!orgId || busy" :loading="loading" @click="select(orgId)">刷新交接案</NButton></NSpace
      >
      <NAlert v-for="error in data.evidenceErrors || []" :key="error" type="error">{{ error }}</NAlert>
      <NCollapse
        ><NCollapseItem
          v-for="item in data.items || []"
          :key="item.record.id"
          :name="item.record.id"
          :title="`${item.record.leavingUserName} → ${item.record.successorUserName} · ${status(item.status)}`"
        >
          <CollectionEvidenceReadView :fields="recordReadFields" :data="item.record" />
          <CollectionEvidenceReadView :fields="requestFields" :data="item.record.request" :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `CASE_${item.record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(`CASE_${item.record.id}`)" />
          <NAlert v-if="item.evidenceError" type="error">{{ item.evidenceError }}</NAlert>
          <p v-if="item.inventoryBatchName">当前清点：{{ item.inventoryBatchName }}</p>
          <p v-for="history in item.inventoryHistory || []" :key="history.id">
            {{ history.current ? '当前批次' : '原批次保留' }}：{{ history.batchName || history.id }}
          </p>
          <NAlert v-if="item.status === 'COMPLETED_LATE'" type="warning"
            >本案已核对移交结果，实际迟办事实仍保留。</NAlert
          >
          <CollectionEvidenceReadView
            v-for="(signature, phase) in item.preparations || {}"
            :key="phase"
            :fields="signatureReadFields"
            :data="signature"
            :archive-files="(data.archives || []).filter((archive: any) => archive.phase === String(phase)).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(String(phase))"
          />
          <CollectionEvidenceReadView
            v-for="(review, phase) in item.completionReviews || {}"
            :key="phase"
            :fields="signatureReadFields"
            :data="review.signature"
            :archive-files="(data.archives || []).filter((archive: any) => archive.phase === String(phase)).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(String(phase))"
          />
          <NSpace>
            <NButton v-if="ready && item.canCorrect" :disabled="busy || loading" @click="edit(item.record)"
              >更正任职或离任依据</NButton
            >
            <NButton
              v-for="stage in (item.actions || []).filter((stage: string) => canSign(item.record, stage))"
              :key="stage"
              :disabled="busy || loading"
              @click="sign(item.record, stage)"
              >{{ stages[stage] }}</NButton
            >
            <NButton
              v-if="item.status === 'PENDING_INVENTORY'"
              :disabled="busy || loading"
              @click="prepareInventory(item.record)"
              >为当前批次填写离任清点方案</NButton
            >
            <NButton
              v-if="item.canReplan"
              :disabled="busy || loading"
              @click="prepareInventory(item.record, item.inventoryBatchId)"
              >为当前新批次重新组织全馆清点</NButton
            >
          </NSpace>
        </NCollapseItem></NCollapse
      >
      <NForm v-if="target" label-width="200">
        <NAlert type="info">{{
          target.stage ? stages[target.stage] : '按真实原件登记任职与责任范围；离任人员填写原账号，支持已停用账号。'
        }}</NAlert>
        <WarehouseRecordFields v-model="form" :fields="target.stage ? signatureFields : requestFields" />
        <NSpace
          ><NButton :loading="busy" @click="submit">{{ target.stage ? '提交本人核对' : '保存离任交接案' }}</NButton
          ><NButton :disabled="busy" @click="target = undefined">取消本次填写</NButton></NSpace
        >
      </NForm>
      <NCollapse
        ><NCollapseItem
          v-for="archive in data.archives || []"
          :key="archive.id"
          :name="archive.id"
          :title="`交接原件 · ${archive.phase}`"
        >
          <CollectionEvidenceReadView :fields="archiveFields" :data="archive" />
          <HFileList :files="archive.files || []" :original-url="(fileId: string) => originalUrl(archive.id, fileId)" :revision="[archive?.id, archive?.digest]" show-download-btn /> </NCollapseItem
      ></NCollapse>
    </NSpace>
  </HPageForm>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useMessage } from 'naive-ui';
import { leadershipDepartureApi } from '../api/leadershipDeparture';
import { orgApi } from '../api/org';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import { warehouseFieldError } from '../utils/warehouseEvidenceFields';
import type { EvidenceField } from '../utils/evidenceFields';
const emit = defineEmits<{
  (e: 'prepareInventory', value: { orgId: string; record: any; replacesInventoryBatchId?: string }): void;
  (e: 'saved'): void;
}>();
const message = useMessage();
const auth = useAuthStore();
const readActor = ref<string>();
let alive = true, writeVersion = 0;
const show = ref(false),
  orgId = ref<string>(),
  organizations = ref<any[]>([]),
  data = ref<any>({}),
  form = ref<any>({}),
  loading = ref(false),
  busy = ref(false);
const target = ref<{ orgId: string; recordId?: string; stage?: string }>();
let requestVersion = 0;
const orgOptions = computed(() => organizations.value.map((o) => ({ value: o.id, label: o.name })));
const stages: Record<string, string> = {
  PK: '保管负责人核对任职依据',
  PS: '接任负责人核对任职依据',
  HK: '保管负责人核对清点移交结果',
  HS: '接任负责人核对清点移交结果',
};
const statuses: Record<string, string> = {
  PENDING_KEEPER: '待保管负责人核对任职依据',
  PENDING_SUCCESSOR: '待接任人核对任职依据',
  PENDING_INVENTORY: '待全馆清点',
  INVENTORY_IN_PROGRESS: '全馆清点进行中',
  PENDING_HANDOVER_REVIEW: '待本人核对移交结果',
  COMPLETED: '移交结果已核对',
  COMPLETED_LATE: '移交结果已核对（迟办）',
  SUPERSEDED: '原版本已保留',
  EVIDENCE_REQUIRED: '原件待核对',
};
const status = (v: string) => statuses[v] || '状态待核对';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const requestFields = computed<EvidenceField[]>(() => [
  {
    ...f('post', '实际离任职务', 'select'),
    options: [
      { value: 'LEGAL_REPRESENTATIVE', label: '法定代表人' },
      { value: 'PRINCIPAL', label: '主要负责人' },
      { value: 'BOTH', label: '同时为法定代表人和主要负责人' },
    ],
  },
  f('leavingUserId', '离任人员原账号或已存身份编号'),
  f('appointedAt', '原实际任职开始时间', 'datetime'),
  f('leavingAt', '正式离任时间', 'datetime'),
  f('appointmentFileIds', '原任职依据', 'file'),
  f('departureFileIds', '正式离任依据', 'file'),
  f('successorUserId', '实际接任负责人', 'user'),
  f('successorAppointmentFileIds', '接任及任职依据', 'file'),
  f('keeperUserId', '保管部门核对负责人', 'user'),
  f('responsibilityStatement', '全部责任范围及任职、离任、接任关系', 'textarea'),
  f('responsibilityFileIds', '完整责任范围原件', 'file'),
  f('replacementReason', '本次更正原因', 'textarea', !!target.value?.recordId && !target.value?.stage),
]);
const recordReadFields: EvidenceField[] = [
  f('id', '交接案编号'),
  f('leavingUserName', '离任人登记时姓名'),
  f('successorUserName', '接任人登记时姓名'),
  f('registeredAt', '系统登记时间', 'datetime'),
];
const signatureFields: EvidenceField[] = [
  f('opinion', '本人核对意见', 'textarea'),
  f('fileIds', '本人实际签署原件', 'file'),
];
const signatureReadFields: EvidenceField[] = [
  f('actorUserId', '实际核对人', 'user'),
  f('confirmedAt', '系统核对时间', 'datetime'),
  f('record.opinion', '本人核对意见', 'textarea'),
  f('record.fileIds', '本人签署原件', 'file'),
];
const archiveFields: EvidenceField[] = [
  f('archivedAt', '归档时间', 'datetime'),
  f('snapshot', '完整原始内容', 'textarea'),
];
const originalUrl = (archiveId: string, fileId: string) =>
  leadershipDepartureApi.originalUrl(orgId.value!, archiveId, fileId);
const phaseOriginalUrl = (phase: string) => (fileId: string) => {
  const archive = (data.value.archives || []).find((row: any) =>
    row.phase === phase && row.files?.some((file: any) => file.fileId === fileId));
  return archive ? originalUrl(archive.id, fileId) : '';
};
function invalidate() {
  ++requestVersion;
  ++writeVersion;
  loading.value = false;
  busy.value = false;
  readActor.value = undefined;
  target.value = undefined;
  form.value = {};
  data.value = {};
}
function clear() {
  invalidate();
  orgId.value = undefined;
  organizations.value = [];
}
watch(show, value => { if (!value) clear(); }, { flush: 'sync' });
watch(() => auth.userInfo?.id, () => { show.value = false; clear(); }, { flush: 'sync' });
onBeforeUnmount(() => { alive = false; show.value = false; clear(); });
const ready = computed(() => alive && show.value && !!orgId.value && !!readActor.value &&
  readActor.value === auth.userInfo?.id && !loading.value && !busy.value && !data.value.evidenceErrors?.length);
function currentItem(record: any) {
  return (data.value.items || []).find((item: any) => item.record.id === record?.id);
}
function canSign(record: any, stage: string): boolean {
  if (!ready.value || !stages[stage]) return false;
  const item = currentItem(record);
  if (!item || item.evidenceError || !item.actions?.includes(stage)) return false;
  const expected = stage.endsWith('K') ? item.record.request.keeperUserId : item.record.request.successorUserId;
  return expected === auth.userInfo?.id;
}
async function open(selected?: string) {
  if (!alive || !auth.userInfo?.id) return;
  clear();
  const token = requestVersion, actor = auth.userInfo.id;
  show.value = true;
  loading.value = true;
  const current = () => alive && show.value && token === requestVersion && actor === auth.userInfo?.id;
  try {
    const result = (await orgApi.list()) as any;
    if (!current()) return;
    organizations.value = result.data || [];
    await select(selected || organizations.value[0]?.id);
  } catch (error: any) {
    if (current()) {
      data.value = { evidenceErrors: [error?.message || '收藏单位读取失败，请重试'] };
      loading.value = false;
    }
  }
}
async function select(id?: string) {
  if (!alive || !show.value) return false;
  invalidate();
  const token = requestVersion, actor = auth.userInfo?.id;
  orgId.value = id;
  if (!id || !actor) return false;
  loading.value = true;
  const current = () => alive && show.value && token === requestVersion && orgId.value === id && actor === auth.userInfo?.id;
  try {
    const result = (await leadershipDepartureApi.list(id)) as any;
    if (!current()) return false;
    data.value = result.data;
    readActor.value = actor;
    return !data.value.evidenceErrors?.length;
  } catch (error: any) {
    if (current()) data.value = { evidenceErrors: [error?.message || '离任交接案读取失败，请重试'] };
    return false;
  } finally {
    if (current()) loading.value = false;
  }
}
function edit(record?: any) {
  if (!ready.value || (record && !currentItem(record)?.canCorrect)) return;
  target.value = { orgId: orgId.value!, recordId: record?.id };
  form.value = record
    ? { ...JSON.parse(JSON.stringify(currentItem(record).record.request)), previousId: record.id, replacementReason: undefined }
    : {};
}
function sign(record: any, stage: string) {
  if (!canSign(record, stage)) return;
  target.value = { orgId: orgId.value!, recordId: record.id, stage };
  form.value = {};
}
function prepareInventory(record: any, replacesInventoryBatchId?: string) {
  const item = currentItem(record);
  if (!ready.value || !item || item.evidenceError ||
    (replacesInventoryBatchId ? !item.canReplan || item.inventoryBatchId !== replacesInventoryBatchId : item.status !== 'PENDING_INVENTORY')) return;
  emit('prepareInventory', {
    orgId: orgId.value!,
    record: JSON.parse(JSON.stringify(item.record)),
    ...(replacesInventoryBatchId ? { replacesInventoryBatchId } : {}),
  });
}
async function submit() {
  if (!target.value || !ready.value || target.value.orgId !== orgId.value) return;
  const selected = { ...target.value }, token = requestVersion, actor = auth.userInfo?.id;
  const item = selected.recordId ? currentItem({ id: selected.recordId }) : undefined;
  if (selected.stage ? !canSign(item?.record, selected.stage) : selected.recordId && !item?.canCorrect) return;
  const payload = JSON.parse(JSON.stringify(form.value));
  const error = warehouseFieldError(selected.stage ? signatureFields : requestFields.value, payload);
  if (error) { message.warning(error); return; }
  const operation = ++writeVersion;
  busy.value = true;
  try {
    if (selected.stage) await leadershipDepartureApi.confirm(selected.orgId, selected.recordId!, selected.stage, payload);
    else await leadershipDepartureApi.register(selected.orgId, { ...payload, previousId: selected.recordId });
    if (!alive || !show.value || token !== requestVersion || actor !== auth.userInfo?.id || orgId.value !== selected.orgId) return;
    if (await select(selected.orgId) && alive && show.value && actor === auth.userInfo?.id && orgId.value === selected.orgId) emit('saved');
  } finally {
    if (operation === writeVersion) busy.value = false;
  }
}
function close() { show.value = false; }
defineExpose({ open, close });
</script>
