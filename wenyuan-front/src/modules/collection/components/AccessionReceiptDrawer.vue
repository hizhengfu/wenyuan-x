<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="实际核收及正式登记进度">
    <NSpace vertical :size="18">
      <CollectionArchiveIssues :data="data" />
      <NText>{{ data.voucher?.voucherNo }} · {{ receiptStatus(data.voucher?.voucherStatus) }}</NText>
      <NAlert type="info"
        >实际逐件核收原件齐备后生成待登记记录。文物须补齐行政定级备案，再经初始编目审核正式登账；其他藏品完成核收后办理初始编目审核。</NAlert
      >
      <NAlert v-if="data.legacyUnverified" type="warning"
        >这是历史已登记凭证，尚无新版逐件核收原件。现有状态保留，须核实纸质资料并完成历史补录验收。</NAlert
      >
      <NSelect
        v-model:value="acquisitionId"
        :options="
          items.map((v: any) => ({ label: `${v.name} · ${v.quantity}${v.unit || ''}`, value: v.acquisitionId }))
        "
      />
      <template v-if="current">
        <CollectionApprovalProgress v-if="show && acquisitionId" title="关联征集审批" :business-id="acquisitionId" business-type="COLLECTION_ACQUISITION" :fetch-flow="acquisitionApi.flow" :revision="data" />
        <NDescriptions bordered :column="2">
          <NDescriptionsItem label="实际取得日">{{ date(current.actualAcquiredOn) }}</NDescriptionsItem>
          <NDescriptionsItem label="三个月办理期限">{{ date(current.deadline) }}</NDescriptionsItem>
          <NDescriptionsItem label="本次核收状态">{{
            current.current
              ? current.current.content.accepted
                ? '核收完成'
                : '实际收到部分或差异待处理'
              : '尚无实际核收记录'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="登记号">{{ current.totalRegNo || '尚未预留' }}</NDescriptionsItem>
          <NDescriptionsItem label="正式登账">{{
            current.registeredAt ? date(current.registeredAt) : '尚未正式登账'
          }}</NDescriptionsItem>
        </NDescriptions>
        <NAlert v-if="current.overdue" type="warning">核收办理已超过取得后三个月，迟办事实继续保留。</NAlert>
        <NButton v-if="data.voucher?.voucherStatus === 'DRAFT'" type="primary" @click="edit">{{
          current.current ? '补充实收或订正清册' : '登记实际逐件核收'
        }}</NButton>
        <NButton
          v-if="
            data.voucher?.voucherStatus === 'RECEIVED' &&
            current.collectionId &&
            !current.registeredAt &&
            ['DRAFT', 'REJECTED'].includes(current.catalogStatus) &&
            current.current?.content?.accepted
          "
          type="primary"
          @click="editSignature"
          >追加核收照片及签署原件订正</NButton
        >
        <NCollapse>
          <NCollapseItem
            v-for="record in [...(current.history || [])].reverse()"
            :key="record.id"
            :name="record.id"
            :title="`第 ${record.version} 版 · ${date(record.content.checkedAt)} · ${record.content.accepted ? '已核收' : '未完成'}`"
          >
            <NSpace vertical>
              <NAlert v-if="record.legacyFileContentUnverified" type="warning"
                >历史附件尚未留存实际内容核验依据，文件一致性须核实原件及备份，不能仅凭核收状态认定完成。</NAlert
              >
              <CollectionEvidenceReadView :fields="receiptReadFields" :data="record.content" :archive-files="(record.archives || []).filter((archive: any) => archive.phase === 'RECORDED').flatMap((archive: any) => archive.files || [])" :original-url="recordedOriginalUrl(record)" />
              <NText>移交人员：{{ record.content.handoverNames?.join('、') }}</NText>
              <NFormItem label="实际接收保管库位"
                ><template #label><CollectionFieldLabel label="实际接收保管库位" :help="WAREHOUSE_FIXED_AT_RECEIPT_HINT" /></template
                ><WarehouseTreeSelect :value="record.content.warehouseId" disabled
              /></NFormItem>
              <NCard v-for="piece in record.content.pieces" :key="piece.pieceNo" :title="piece.pieceNo"
                ><CollectionEvidenceReadView :fields="pieceFields" :data="piece" :archive-files="(record.archives || []).filter((archive: any) => archive.phase === 'RECORDED').flatMap((archive: any) => archive.files || [])" :original-url="recordedOriginalUrl(record)"
              /></NCard>
              <CollectionEvidenceReadView
                v-if="!record.content.accepted"
                :fields="pendingReceiptFields"
                :data="record.content"
                :archive-files="(record.archives || []).filter((archive: any) => archive.phase === 'RECORDED').flatMap((archive: any) => archive.files || [])" :original-url="recordedOriginalUrl(record)"
              />
              <CollectionEvidenceReadView
                v-if="record.content.lateReason"
                :fields="lateReceiptFields"
                :data="record.content"
                :archive-files="(record.archives || []).filter((archive: any) => archive.phase === 'RECORDED').flatMap((archive: any) => archive.files || [])" :original-url="recordedOriginalUrl(record)"
              />
              <CollectionEvidenceReadView
                v-if="record.version > 1"
                :fields="correctionReceiptFields"
                :data="record.content"
                :archive-files="(record.archives || []).filter((archive: any) => archive.phase === 'RECORDED').flatMap((archive: any) => archive.files || [])" :original-url="recordedOriginalUrl(record)"
              />
              <NText v-if="record.collectionId">已关联登记号：{{ current.totalRegNo }}</NText>
              <NCollapse
                ><NCollapseItem
                  v-for="a in record.archives || []"
                  :key="a.id"
                  :name="a.id"
                  :title="`永久原件 · ${date(a.archivedAt)}`"
                  ><HFileList :files="a.files" :original-url="(fileId: string) => originalUrl(record, a, fileId)" :revision="[a?.id, a?.digest]" show-download-btn /></NCollapseItem
              ></NCollapse>
            </NSpace>
          </NCollapseItem>
        </NCollapse>
      </template>
      <NButton v-if="data.voucher?.voucherStatus === 'DRAFT'" :disabled="!canGenerate || generating" :loading="registrationLoading" type="primary" @click="generate"
        >依据核收原件生成待登记记录</NButton
      >
    </NSpace>
  </HPageForm>
  <AccessionReceiptForm ref="formRef" :save="save" @saved="reload" />
  <NModal v-model:show="registrationShow" preset="card" title="依据核收原件生成待登记记录" style="width: min(760px, 94vw)" :mask-closable="!generating" :closable="!generating">
    <NSpace vertical :size="16">
      <NAlert type="info">已通过新增、复制或普通导入建立台账的，可选择对应待登记藏品，保留原登记号和资料。候选记录的名称、性质、来源、件套数须与核收对象一致；未选择时新建记录。正式登账仍需定级及初始编目审核。</NAlert>
      <NFormItem v-for="item in registrationItems" :key="item.acquisitionId" :label="item.name">
        <NSelect v-model:value="targetSelections[item.acquisitionId]" clearable filterable :disabled="generating"
          :options="(registrationTargets[item.acquisitionId] || []).map(target => ({label: `${target.totalRegNo} · ${target.name}`, value: target.collectionId}))"
          placeholder="新建待登记记录；已有台账时请选择对应记录" />
      </NFormItem>
    </NSpace>
    <template #footer><NSpace justify="end">
      <NButton :disabled="generating" @click="registrationShow = false">取消</NButton>
      <NButton type="primary" :loading="generating" :disabled="generating" @click="confirmRegistration">确认生成或关联</NButton>
    </NSpace></template>
  </NModal>
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import { acquisitionApi } from '../api/acquisition';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import { entryVoucherApi, type EntryVoucherRegistrationTarget, type EntryVoucherExecuteRequest } from '../api/entryVoucher';
import AccessionReceiptForm from './AccessionReceiptForm.vue';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { WAREHOUSE_FIXED_AT_RECEIPT_HINT } from '../utils/accessionReceiptHints';
import {
  receiptStatus,
  receiptFields,
  pieceFields,
  pendingReceiptFields,
  lateReceiptFields,
  correctionReceiptFields,
} from '../utils/accessionReceiptFields';
/** 核收记录只读展示：保管人员标签旁提示库房与保管员已在核收时确定。 */
const receiptReadFields = receiptFields.map((field) =>
  field.key === 'keeperUserIds' ? { ...field, labelHelp: WAREHOUSE_FIXED_AT_RECEIPT_HINT } : field,
);
const emit = defineEmits<{ (e: 'saved'): void }>();
const message = useMessage(),
  show = ref(false),
  data = ref<any>({}),
  voucherId = ref(''),
  acquisitionId = ref<string>(),
  formRef = ref<any>();
const items = computed<any[]>(() => data.value.items || []),
  current = computed(() => items.value.find((v) => v.acquisitionId === acquisitionId.value));
const canGenerate = computed(
  () => items.value.length > 0 && items.value.every((v) => v.current?.content?.accepted === true),
);
const date = (v: any) => (v == null ? '未登记' : formatLocalDateValue(v, 'YYYY-MM-DD HH:mm:ss'));
const registrationShow = ref(false), registrationLoading = ref(false), generating = ref(false);
const registrationTargets = ref<Record<string, EntryVoucherRegistrationTarget[]>>({});
const targetSelections = ref<Record<string, string | null>>({});
const registrationItems = ref<{acquisitionId: string; name: string}[]>([]);
let registrationVoucherId = '', registrationRequest = 0;
watch(show, visible => { if (!visible) { registrationRequest++; registrationShow.value = false; } });
const originalUrl = (record: any, archive: any, fileId: string) =>
  `/collection/entry-voucher/${voucherId.value}/receipts/${record.id}/original/${archive.id}/${fileId}`;
const recordedOriginalUrl = (record: any) => (fileId: string) => {
  const archive = (record.archives || []).find((a: any) => a.phase === 'RECORDED' && a.files?.some((f: any) => f.fileId === fileId));
  return archive ? originalUrl(record, archive, fileId) : '';
};
let selection = 0,
  editingVoucherId = '';
async function open(id: string) {
  registrationRequest++;
  registrationShow.value = false;
  registrationLoading.value = false;
  show.value = true;
  voucherId.value = id;
  data.value = {};
  acquisitionId.value = undefined;
  await load();
}
async function load() {
  const version = ++selection,
    id = voucherId.value;
  if (!id) return;
  data.value = {};
  try {
    const response: any = await entryVoucherApi.receipts(id);
    if (version !== selection) return;
    data.value = response.data;
    acquisitionId.value = items.value.some((v) => v.acquisitionId === acquisitionId.value)
      ? acquisitionId.value
      : items.value[0]?.acquisitionId;
    return true;
  } catch (error: any) {
    if (version !== selection) return;
    acquisitionId.value = undefined;
    message.error(error?.message || '核收原件读取失败，请核对当前权限与原件完整性');
  }
}
function edit() {
  if (!current.value) return;
  editingVoucherId = voucherId.value;
  formRef.value?.open(current.value);
}
function editSignature() {
  if (!current.value) return;
  editingVoucherId = voucherId.value;
  formRef.value?.open(current.value, true);
}
async function save(value: any) {
  return entryVoucherApi.recordReceipt(editingVoucherId, value);
}
async function reload() {
  if (await load()) emit('saved');
}
async function generate() {
  if (registrationLoading.value || generating.value || !canGenerate.value) return;
  const id = voucherId.value;
  const request = ++registrationRequest;
  registrationLoading.value = true;
  try {
    const response: any = await entryVoucherApi.registrationTargets(id);
    if (request !== registrationRequest || id !== voucherId.value || !show.value) return;
    registrationVoucherId = id;
    registrationTargets.value = response.data || {};
    targetSelections.value = {};
    registrationItems.value = items.value.map(item => ({acquisitionId: item.acquisitionId, name: item.name}));
    registrationShow.value = true;
  } finally {
    if (request === registrationRequest) registrationLoading.value = false;
  }
}
async function confirmRegistration() {
  if (generating.value || registrationVoucherId !== voucherId.value || !show.value) return;
  const existingCollections: EntryVoucherExecuteRequest['existingCollections'] = {};
  const selected = new Set<string>();
  for (const item of registrationItems.value) {
    const collectionId = targetSelections.value[item.acquisitionId];
    if (!collectionId) continue;
    const target = registrationTargets.value[item.acquisitionId]?.find(value => value.collectionId === collectionId);
    if (!target || selected.has(collectionId)) { message.error('请逐项选择不同的有效待登记藏品'); return; }
    selected.add(collectionId);
    existingCollections[item.acquisitionId] = {collectionId, stateHash: target.stateHash};
  }
  const id = registrationVoucherId;
  generating.value = true;
  try {
    await entryVoucherApi.execute(id, {existingCollections});
    registrationShow.value = false;
    message.success('已生成或关联待登记记录，请继续办理定级及初始编目审核');
    if (id === voucherId.value && show.value) await reload(); else emit('saved');
  } finally { generating.value = false; }
}
defineExpose({ open });
</script>
