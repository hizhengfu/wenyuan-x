<template>
  <NSpace vertical :size="16">
    <NAlert type="info"
      >任职名单变更后，请核对被移除库管的离任清点移交。完整责任范围须以岗位责任和签署原件核实，不能只按藏品当前位置推定；录入更正须说明错误及依据。{{
        data.historyNotice
      }}</NAlert
    >
    <NButton :loading="loading" :disabled="busy" @click="load">刷新岗位交接核对</NButton>
    <NAlert v-for="error in data.evidenceErrors || []" :key="error" type="error">{{ error }}</NAlert>
    <NCollapse>
      <NCollapseItem v-for="item in data.items || []" :key="key(item)" :name="key(item)" :title="status(item.status)">
        <CollectionEvidenceReadView :fields="sourceFields" :data="item" />
        <NCard v-for="source in assignmentSources(item)" :key="source.archiveId" :title="source.label" size="small">
          <NText v-if="!source.archive" type="warning">任职签署原件待核对</NText>
          <HFileList :files="source.archive?.files || []" :original-url="(fileId: string) => assignmentOriginalUrl(source.archiveId, fileId)" :revision="[source.archive?.id, source.archive?.digest]" show-download-btn />
        </NCard>
        <NAlert v-if="item.departureScopeErrors?.length" type="warning"
          >{{
            item.departureScopeErrors.join('；')
          }}。旧签署保留，请另建完整清点，再补充核对记录关联新批次并重新由负责人确认。</NAlert
        >
        <CollectionEvidenceReadView v-if="item.record" :fields="recordFields" :data="item.record.request" :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `R_${item.record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(`R_${item.record.id}`)" />
        <p v-if="item.lateBatchIds?.length">
          迟办清点批次：{{ item.lateBatchIds.map(batchName).join('、') }}。本次确认保留实际迟办事实。
        </p>
        <CollectionEvidenceReadView v-if="item.keeper" :fields="signatureReadFields" :data="item.keeper" :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `K_${item.record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(`K_${item.record.id}`)" />
        <CollectionEvidenceReadView v-if="item.leader" :fields="signatureReadFields" :data="item.leader" :archive-files="(data.archives || []).filter((archive: any) => archive.phase === `L_${item.record.id}`).flatMap((archive: any) => archive.files || [])" :original-url="phaseOriginalUrl(`L_${item.record.id}`)" />
        <NSpace>
          <NButton
            v-if="allowPrepareInventory && ['PENDING', 'SCOPE_REVIEW_REQUIRED'].includes(item.status)"
            :disabled="busy || loading || !!data.evidenceErrors?.length"
            @click="prepare(item)"
            >以本次任职依据填写离任清点</NButton
          >
          <NButton :disabled="busy || loading" @click="edit(item)">{{
            item.record ? '补充或更正核对' : '登记交接核对'
          }}</NButton>
          <NButton
            v-if="canSign(item, 'keeper')"
            :disabled="busy || loading"
            @click="sign(item, 'keeper')"
            >保管部门负责人本人确认</NButton
          >
          <NButton
            v-if="canSign(item, 'leader')"
            :disabled="busy || loading"
            @click="sign(item, 'leader')"
            >主要负责人本人确认</NButton
          >
        </NSpace>
      </NCollapseItem>
    </NCollapse>
    <NAlert v-if="ready && !data.items?.length" type="info"
      >连续任职原件中未发现库管移除记录。首次登记前的离任交接仍须核对。</NAlert
    >
    <NForm v-if="target" label-width="200">
      <CollectionEvidenceReadView :fields="sourceFields" :data="target.item" />
      <NCard v-for="source in assignmentSources(target.item)" :key="source.archiveId" :title="source.label" size="small">
        <NText v-if="!source.archive" type="warning">任职签署原件待核对</NText>
        <HFileList :files="source.archive?.files || []" :original-url="(fileId: string) => assignmentOriginalUrl(source.archiveId, fileId)" :revision="[source.archive?.id, source.archive?.digest]" show-download-btn />
      </NCard>
      <WarehouseRecordFields v-model="form" :fields="target.role ? signatureFields : recordFields" />
      <NCheckbox v-if="!target.role" v-model:checked="form.fullResponsibilityScopeConfirmed"
        >已依据原件核对完整责任范围；如属录入更正，已核对原错误及更正依据</NCheckbox
      >
      <NSpace
        ><NButton :loading="busy" @click="submit">{{ target.role ? '提交本人确认' : '保存核对记录' }}</NButton
        ><NButton :disabled="busy" @click="target = undefined">取消本次填写</NButton></NSpace
      >
    </NForm>
    <NCollapse
      ><NCollapseItem v-for="a in data.archives || []" :key="a.id" :name="a.id" :title="`交接历史原件 · ${a.phase}`">
        <CollectionEvidenceReadView :fields="archiveFields" :data="a" />
        <HFileList :files="a.files || []" :original-url="(fileId: string) => archiveOriginalUrl(a.id, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /> </NCollapseItem
    ></NCollapse>
  </NSpace>
</template>
<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { useMessage } from 'naive-ui';
import { warehouseApi } from '../api/warehouse';
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import type { EvidenceField } from '../utils/evidenceFields';
import { warehouseFieldError } from '../utils/warehouseEvidenceFields';
import type { KeeperInventorySource } from '../utils/inventoryEvidenceFields';
const props = defineProps<{ warehouseId: string; staffing?: any; allowPrepareInventory?: boolean }>();
const emit = defineEmits<{ (e: 'prepareInventory', value: Omit<KeeperInventorySource, 'orgId'>): void }>();
const message = useMessage();
const auth = useAuthStore();
const readActor = ref<string>();
let alive = true, writeVersion = 0;
const data = ref<any>({}),
  form = ref<any>({}),
  loading = ref(false),
  busy = ref(false);
const batches = computed<any[]>(() => data.value.inventoryBatches || []);
const target = ref<{ warehouseId: string; item: any; role?: string }>();
let sequence = 0;
const sourceFields: EvidenceField[] = [
  { key: 'leavingUserId', label: '本次名单移除人员', type: 'user' },
  { key: 'assignedAt', label: '任职原件签署时间', type: 'datetime' },
  { key: 'successorUserIds', label: '该次任职名单中的库管', type: 'user', multiple: true },
];
const signatureFields: EvidenceField[] = [
  { key: 'opinion', label: '本人核对意见', type: 'textarea', required: true },
  { key: 'fileIds', label: '本人实际签署原件', type: 'file', required: true },
];
const signatureReadFields: EvidenceField[] = [
  { key: 'actorUserId', label: '实际确认人', type: 'user' },
  { key: 'confirmedAt', label: '系统确认时间', type: 'datetime' },
  ...signatureFields.map((f) => ({ ...f, key: `record.${f.key}` })),
];
const archiveFields: EvidenceField[] = [
  { key: 'archivedAt', label: '归档时间', type: 'datetime' },
  { key: 'snapshot', label: '完整留存内容', type: 'textarea' },
];
const recordFields = computed<EvidenceField[]>(() => [
  {
    key: 'kind',
    label: '核对事项',
    type: 'select',
    required: true,
    options: [
      { label: '实际离任清点移交', value: 'DUTY_DEPARTURE' },
      { label: '任职名单录入更正', value: 'RECORD_CORRECTION' },
    ],
  },
  {
    key: 'inventoryBatchIds',
    label: '已完成的离任清点批次',
    type: 'select',
    multiple: true,
    required: !target.value?.role && form.value.kind === 'DUTY_DEPARTURE',
    options: batches.value.map((b) => ({ value: b.id, label: b.batchName })),
  },
  { key: 'scopeStatement', label: '完整责任范围及交接依据／录入错误及更正依据', type: 'textarea', required: true },
  { key: 'scopeFileIds', label: '范围核对或更正签署依据', type: 'file', required: true },
  { key: 'keeperUserId', label: '现任保管部门负责人', type: 'user', required: true },
  { key: 'leaderUserId', label: '现任主要负责人', type: 'user', required: true },
  { key: 'replacementReason', label: '补充或更正原因', type: 'textarea', required: !!target.value?.item.record },
]);
const key = (item: any) => `${item.assignmentArchiveId}/${item.leavingUserId}`;
const batchName = (id: string) => batches.value.find((b) => b.id === id)?.batchName || id;
const statuses: Record<string, string> = {
  PENDING: '待登记交接核对',
  SCOPE_REVIEW_REQUIRED: '旧离任范围待补核，原签署保留',
  PENDING_KEEPER: '待保管部门负责人核对',
  PENDING_LEADER: '待主要负责人核对',
  CORRECTION_CONFIRMED: '录入更正已确认',
  DEPARTURE_CONFIRMED: '离任清点移交核对已确认',
  DEPARTURE_CONFIRMED_LATE: '离任清点移交核对已确认（迟办）',
};
const status = (v: string) => statuses[v] || '状态待核对';
const archiveOriginalUrl = (archiveId: string, fileId: string) =>
  warehouseApi.staffingHandoverOriginalUrl(props.warehouseId, archiveId, fileId);
const assignmentOriginalUrl = (archiveId: string, fileId: string) =>
  warehouseApi.staffingHandoverAssignmentOriginalUrl(props.warehouseId, archiveId, fileId);
const assignmentSources = (item: any) => [
  { label: '离任前任职签署原件', archiveId: item.previousAssignmentArchiveId },
  { label: '移除人员后的任职签署原件', archiveId: item.assignmentArchiveId },
].map(source => ({ ...source, archive: (data.value.assignmentArchives || []).find((archive: any) => archive.id === source.archiveId) }));
const phaseOriginalUrl = (phase: string) => (fileId: string) => {
  const archive = (data.value.archives || []).find((row: any) =>
    row.phase === phase && row.files?.some((file: any) => file.fileId === fileId));
  return archive ? archiveOriginalUrl(archive.id, fileId) : '';
};
function invalidate() {
  ++sequence;
  ++writeVersion;
  data.value = {};
  form.value = {};
  target.value = undefined;
  readActor.value = undefined;
  loading.value = false;
  busy.value = false;
}
const ready = computed(() => alive && !!readActor.value && readActor.value === auth.userInfo?.id &&
  !loading.value && !busy.value && !data.value.evidenceErrors?.length);
function currentItem(item: any) {
  return (data.value.items || []).find((row: any) => key(row) === key(item) && row.record?.id === item.record?.id);
}
function canSign(item: any, role: string): boolean {
  if (!ready.value || !item?.record || item.departureScopeErrors?.length) return false;
  const row = currentItem(item);
  if (!row || row.departureScopeErrors?.length) return false;
  if (role === 'keeper') return row.canConfirmKeeper === true && !row.keeper &&
    row.record.request.keeperUserId === auth.userInfo?.id &&
    (!props.staffing?.keeperUserId || props.staffing.keeperUserId === auth.userInfo?.id);
  if (role === 'leader') return row.canConfirmLeader === true && !!row.keeper && !row.leader &&
    row.record.request.leaderUserId === auth.userInfo?.id &&
    (!props.staffing?.leaderUserId || props.staffing.leaderUserId === auth.userInfo?.id);
  return false;
}
async function load() {
  invalidate();
  const token = sequence, id = props.warehouseId, actor = auth.userInfo?.id;
  if (!alive || !id || !actor) return false;
  loading.value = true;
  const current = () => alive && token === sequence && id === props.warehouseId && actor === auth.userInfo?.id;
  try {
    const result = (await warehouseApi.staffingHandovers(id)) as any;
    if (!current()) return false;
    data.value = result.data;
    readActor.value = actor;
    return !data.value.evidenceErrors?.length;
  } catch (error: any) {
    if (current()) data.value = { evidenceErrors: [error?.message || '岗位交接读取失败，请核对当前权限后重试'] };
    return false;
  } finally {
    if (current()) loading.value = false;
  }
}
watch(() => props.warehouseId, () => { void load(); }, { immediate: true, flush: 'sync' });
watch(() => auth.userInfo?.id, invalidate, { flush: 'sync' });
onBeforeUnmount(() => { alive = false; invalidate(); });
function prepare(item: any) {
  if (
    !ready.value || !currentItem(item) || !props.allowPrepareInventory ||
    busy.value ||
    loading.value ||
    data.value.evidenceErrors?.length ||
    !['PENDING', 'SCOPE_REVIEW_REQUIRED'].includes(item.status)
  )
    return;
  emit('prepareInventory', {
    warehouseId: props.warehouseId,
    assignmentArchiveId: item.assignmentArchiveId,
    leavingUserId: item.leavingUserId,
    successorUserIds: [...(item.successorUserIds || [])],
    keeperUserId: props.staffing?.keeperUserId,
    leaderUserId: props.staffing?.leaderUserId,
  });
}
function edit(item: any) {
  if (!ready.value || !currentItem(item)) return;
  target.value = { warehouseId: props.warehouseId, item: JSON.parse(JSON.stringify(item)) };
  form.value = {
    kind: item.record?.request.kind,
    inventoryBatchIds: [...(item.record?.request.inventoryBatchIds || [])],
    scopeStatement: item.record?.request.scopeStatement,
    scopeFileIds: undefined,
    keeperUserId: props.staffing?.keeperUserId,
    leaderUserId: props.staffing?.leaderUserId,
    fullResponsibilityScopeConfirmed: false,
  };
}
function sign(item: any, role: string) {
  if (!canSign(item, role)) return;
  target.value = { warehouseId: props.warehouseId, item: JSON.parse(JSON.stringify(item)), role };
  form.value = {};
}
async function submit() {
  if (!target.value || !ready.value || target.value.warehouseId !== props.warehouseId ||
    !currentItem(target.value.item) || (target.value.role && !canSign(target.value.item, target.value.role))) return;
  const selected = target.value, token = sequence, actor = auth.userInfo?.id, operation = ++writeVersion;
  const error = warehouseFieldError(selected.role ? signatureFields : recordFields.value, form.value);
  if (error) {
    message.warning(error);
    return;
  }
  if (!selected.role && form.value.fullResponsibilityScopeConfirmed !== true) {
    message.warning('请核对完整责任范围及签署依据');
    return;
  }
  if (!selected.role && form.value.kind === 'RECORD_CORRECTION' && form.value.inventoryBatchIds?.length) {
    message.warning('录入更正须清除离任清点批次，不能冒充实际移交');
    return;
  }
  const payload = JSON.parse(JSON.stringify(form.value));
  busy.value = true;
  try {
    if (selected.role)
      await warehouseApi.confirmStaffingHandover(selected.warehouseId, selected.item.record.id, selected.role, payload);
    else
      await warehouseApi.registerStaffingHandover(selected.warehouseId, {
        ...payload,
        assignmentArchiveId: selected.item.assignmentArchiveId,
        leavingUserId: selected.item.leavingUserId,
        previousId: selected.item.record?.id,
      });
    if (alive && token === sequence && actor === auth.userInfo?.id && props.warehouseId === selected.warehouseId) await load();
  } finally {
    if (operation === writeVersion) busy.value = false;
  }
}
</script>
