<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="跨馆转移与终止合并承接">
    <NSpace vertical :size="18">
      <NAlert v-if="loadError" type="error">{{ loadError }}<NButton @click="open(openedId)">重新读取</NButton></NAlert>
      <NAlert v-if="form.dossier?.kind === 'INSTITUTION_SUCCESSION'" type="info"
        >请核实非营利组织处置依据和每件藏品权属。国家所有的可移动文物须有省级部门指定其他国有博物馆的真实决定。本单只办理逐件承接，不表示全馆清算或合并完成。</NAlert
      >
      <NAlert type="info">{{
        termination?.closed
          ? '原批准、行政材料及双方意见继续保留；该方案已终止执行，不能再据此实际交接。'
          : termination?.groupPending
            ? '批准后终止办理中；同组交换须每件的双方指定责任人本人确认全部完成，才会在同一事务中解除整组业务锁。'
            : form.executedAt
          ? form.currentNoticeComplete
            ? '实际交接、双方总账和当前版本双渠道公告已留档。'
            : '实际交接已登记，请完成原馆现场及官网结果公告。'
          : '先保存逐件草稿并核实双方方案，再登记法定行政原件。双方批准后，经办人员依据实际签署交接办理过户。'
      }}</NAlert>
      <NText v-if="form.id" depth="3"
        >申请编号：{{ form.id }}。同组交换的每件申请都应引用完整编号组和同一份交换协议。</NText
      >
      <NForm :model="form" label-placement="left" label-width="150" :disabled="!editable || busy">
        <NFormItem label="藏品" required
          ><CollectionSelect v-if="!form.id" v-model:value="form.collectionId" life-status="IN_STORE" /><NText v-else
            >{{ form.collectionName || form.baseline?.formalName }}（{{ form.collectionTotalRegNo }}）</NText
          ></NFormItem
        >
        <NFormItem label="接收收藏单位" required
          ><NSelect v-model:value="form.toOrgId" :options="parties" filterable
        /></NFormItem>
        <NFormItem label="转移类别" required
          ><NSelect v-model:value="form.dossier.kind" :options="transferKindOptions"
        /></NFormItem>
        <NFormItem label="逐件转移事由" required><NInput v-model:value="form.reason" type="textarea" /></NFormItem>
        <NFormItem label="批文号"
          ><NInput v-model:value="form.approvalDocNo" placeholder="行政原件须另行逐件登记"
        /></NFormItem>
      </NForm>
      <NSpace size="small" align="center">
        <NButton v-if="editable" type="primary" :loading="busy" @click="saveDraft">保存逐件草稿</NButton>
        <NButton v-if="collecting" :loading="busy" @click="submit">材料齐备，提交双方确认</NButton>
        <NButton
          v-if="
            form.sourceWritable !== false &&
            form.flowStatus === 'PASS' &&
            !form.executedAt &&
            !termination?.groupPending &&
            !termination?.records?.length
          "
          type="warning"
          @click="edit('execute')"
          >签署并登记实际交接</NButton
        >
        <NButton v-if="canConfirmTermination" type="warning" :disabled="busy" @click="openTermination('confirm')"
          >本人确认终止执行</NButton
        >
        <CollectionActionMenu label="交接材料" v-if="(form.id && editable) || collecting">
          <NButton quaternary v-if="form.id && editable" @click="edit('dossier')">核实双方责任人和交接方案</NButton>
          <NButton quaternary v-if="collecting" @click="edit('administrative')">登记行政原件</NButton>
        </CollectionActionMenu>
        <CollectionActionMenu label="公告办理" v-if="form.sourceWritable !== false && form.executedAt">
          <NButton
            quaternary
            v-if="form.sourceWritable !== false && form.executedAt"
            v-permission="'COLLECTION_ADMIN,COLLECTION_PUBLISH'"
            @click="openDisclosure"
            >核查结果公告公开稿</NButton
          >
          <NButton quaternary v-if="form.sourceWritable !== false && form.executedAt" @click="edit('notice')"
            >登记实际结果公告</NButton
          >
        </CollectionActionMenu>
        <CollectionActionMenu label="更多" v-if="form.id || canProposeTermination">
          <NButton quaternary v-if="form.id" @click="flowRef?.open(form.id, '交换调拨双方确认')">查看审批</NButton>
          <NButton
            quaternary
            v-if="canProposeTermination"
            type="warning"
            :disabled="busy"
            @click="openTermination('propose')"
            >提出批准后终止执行</NButton
          >
        </CollectionActionMenu>
      </NSpace>
      <NText v-if="termination?.groupTransferIds?.length > 1" depth="3">同组交换共 {{ termination.groupTransferIds.length }} 件，{{ termination.groupReadyCount }} 件已完成双方四人确认；整组关闭 {{ termination.groupClosedCount }} 件。各件共用同一撤回依据文号和原件，保管现状与本人签署分别留档。</NText>
      <NText v-if="termination?.records?.length" depth="3">终止记录：{{ termination.records.map((row: any) => row.kind).join(' → ') }}。原批准状态仍为 {{ form.flowStatus }}。</NText>
      <NText v-if="form.dossier?.kind !== 'INSTITUTION_SUCCESSION'" depth="3"
        >国有文物交换须省级批准、国家备案；省级调拨须有其区域内主管国有馆的依据；一级文物须国家备案。捐赠藏品按原协议办理，无约定协议时应事先说明并取得捐赠人书面同意。</NText
      >
      <TransferEvidenceView v-if="form.id" :key="`transfer-evidence-${revision}`" :data="form" />
    </NSpace>
  </HPageForm>
  <CollectionEvidenceForm ref="evidenceRef" :title="title" :fields="fields" :save="saveEvidence" :original-url="form.id ? originalUrl : undefined" @saved="reload" />
  <CollectionEvidenceForm ref="terminationRef" :title="terminationTitle" :fields="terminationFields" :save="saveTermination" @saved="reload" />
  <CollectionDisclosureDrawer ref="disclosureRef" @updated="reload" />
  <FlowFormDrawer ref="flowRef" :fetch-fn="transferApi.flow" :cancel-api="transferApi.cancel" @success="reload" />
</template>
<script setup lang="ts">
import CollectionActionMenu from './CollectionActionMenu.vue';
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useDialog, useMessage } from 'naive-ui';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import CollectionSelect from './CollectionSelect.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import TransferEvidenceView from './TransferEvidenceView.vue';
import CollectionDisclosureDrawer from './CollectionDisclosureDrawer.vue';
import { transferApi } from '../api/transfer';
import type { EvidenceField } from '../utils/evidenceFields';
import {
  transferKindOptions,
  transferDossierFields,
  transferAdministrativeFields,
  transferExecutionFields,
  transferNoticeFields,
  transferEvidencePayload,
  transferEvidenceInitial,
} from '../utils/transferEvidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>(),
  message = useMessage(),
  dialog = useDialog();
const show = ref(false),
  busy = ref(false),
  revision = ref(0),
  form = ref<any>({ dossier: {} }),
  flowRef = ref<any>(),
  evidenceRef = ref<any>(),
  terminationRef = ref<any>(),
  termination = ref<any>(null),
  terminationStage = ref<'propose' | 'confirm'>('propose');
const loadError = ref('');
let openedId: string | undefined,
  viewRequest = 0;
const editingForm = ref<any>({ dossier: {} });
const parties = ref<{ value: string; label: string }[]>([]),
  locations = ref<{ value: string; label: string }[]>([]);
const disclosureRef = ref<any>(),
  publicReviewOptions = ref<any[]>([]);
let noticeSubjectId = '';
const kind = ref<'dossier' | 'administrative' | 'execute' | 'notice'>('dossier');
const canProposeTermination = computed(() =>
  !loadError.value && form.value.id && form.value.sourceWritable !== false &&
  form.value.flowStatus === 'PASS' && !form.value.executedAt &&
  !termination.value?.records?.length,
);
const canConfirmTermination = computed(() => {
  const next = termination.value?.nextNode;
  return !loadError.value && next && !termination.value?.closed &&
    termination.value?.actors?.[next] === termination.value?.currentUserId;
});
const terminationTitle = computed(() => terminationStage.value === 'propose'
  ? '提出已批准方案终止执行' : '本人确认已批准方案终止执行');
const terminationFields = computed<EvidenceField[]>(() => terminationStage.value === 'propose' ? [
  { key: 'proposedAt', label: '提出时间', type: 'datetime', required: true },
  { key: 'reason', label: '不再执行的原因', type: 'textarea', required: true },
  { key: 'authorityDocumentNo', label: '撤回或不再执行依据文号', required: true },
  { key: 'authorityFileIds', label: '依据原件', type: 'file', required: true },
  { key: 'custodyStatement', label: '当前在库和未交接核对', type: 'textarea', required: true },
  { key: 'custodyFileIds', label: '保管核对原件', type: 'file', required: true },
] : [
  { key: 'confirmedAt', label: '本人确认时间', type: 'datetime', required: true },
  { key: 'opinion', label: '本人确认意见', type: 'textarea', required: true },
  { key: 'signedFileIds', label: '本人签署原件', type: 'file', required: true },
]);
const collecting = computed(
  () =>
    form.value.sourceWritable !== false &&
    !!form.value.id &&
    form.value.flowStatus === 'DRAFT' &&
    !form.value.flowInstanceId,
);
const editable = computed(
  () =>
    !loadError.value &&
    (!form.value.id ||
      (form.value.proposalEditable !== false && collecting.value && !form.value.administrativeEvidence?.length)),
);
const title = computed(
  () =>
    ({
      dossier: '核实双方账与交接方案',
      administrative: '登记实际行政批准或备案',
      execute: '登记双方已签署的实际交接',
      notice: '登记原馆退出结果公告',
    })[kind.value] +
    (editingForm.value.collectionName || editingForm.value.id
      ? ` · ${editingForm.value.collectionName || editingForm.value.id}`
      : ''),
);
const fields = computed(() =>
  kind.value === 'dossier'
    ? transferDossierFields(editingForm.value.dossier?.kind, editingForm.value.baseline?.source, locations.value)
    : kind.value === 'administrative'
      ? transferAdministrativeFields
      : kind.value === 'execute'
        ? transferExecutionFields
        : [
            {
              key: 'disclosureReviewId',
              label: '已核查的结果公告稿版本',
              type: 'select' as const,
              required: true,
              options: publicReviewOptions.value,
            },
            ...transferNoticeFields,
          ],
);
const persistedOriginalIds = computed(() => {
  const ids = new Set<string>();
  function visit(value: unknown) {
    if (Array.isArray(value)) { value.forEach(visit); return; }
    if (!value || typeof value !== 'object') return;
    for (const [key, child] of Object.entries(value)) {
      if (typeof child === 'string' && (key.endsWith('FileIds') || key.endsWith('FileId') || key === 'attachmentIds'))
        child.split(/[,，;；\r\n]/).map((id) => id.trim()).filter(Boolean).forEach((id) => ids.add(id));
      else visit(child);
    }
  }
  visit(form.value);
  return ids;
});
function originalUrl(fileId: string): string | undefined {
  if (!form.value.id || !persistedOriginalIds.value.has(fileId)) return undefined;
  return `/collection/transfer/${encodeURIComponent(form.value.id)}/original/${encodeURIComponent(fileId)}`;
}
async function open(id?: string) {
  const token = ++viewRequest;
  openedId = id;
  show.value = true;
  busy.value = true;
  loadError.value = '';
  form.value = { dossier: {} };
  termination.value = null;
  try {
    const [response, detail, terminationDetail]: any[] = await Promise.all([
      transferApi.parties(),
      id
        ? transferApi.get(id)
        : Promise.resolve({ data: { dossier: { kind: 'STATE_ALLOCATION' }, flowStatus: 'DRAFT' } }),
      id ? transferApi.termination(id) : Promise.resolve({ data: null }),
    ]);
    if (token !== viewRequest || !show.value) return;
    parties.value = response.data || [];
    form.value = detail.data;
    termination.value = terminationDetail.data;
    form.value.dossier ||= {};
    revision.value++;
  } catch (e) {
    if (token === viewRequest) loadError.value = e instanceof Error ? e.message : '逐件承接原件读取失败';
  } finally {
    if (token === viewRequest) busy.value = false;
  }
}
async function reload() {
  await open(form.value.id);
  emit('saved');
}
async function saveDraft() {
  if (!form.value.collectionId || !form.value.toOrgId || !form.value.reason?.trim() || !form.value.dossier?.kind) {
    message.warning('请填写藏品、接收单位、类别和逐件事由');
    return;
  }
  busy.value = true;
  const view = viewRequest;
  try {
    const value = { ...form.value, dossier: form.value.dossier.sourceKeeperUserId ? form.value.dossier : undefined };
    const selectedKind = form.value.dossier.kind;
    const response: any = await transferApi.save(value);
    if (view !== viewRequest || !show.value) {
      emit('saved');
      return;
    }
    await open(response.data);
    if (form.value.id !== response.data) return;
    form.value.dossier.kind ||= selectedKind;
    message.success('逐件草稿已保存，请核实完整交接方案');
    emit('saved');
  } finally {
    busy.value = false;
  }
}
async function openDisclosure() {
  await disclosureRef.value?.open({ orgId: form.value.orgId, subject: 'TRANSFER_RESULT', subjectId: form.value.id });
}
async function edit(value: typeof kind.value) {
  const token = viewRequest;
  const selected = cloneDeep(form.value);
  kind.value = value;
  editingForm.value = selected;
  if (value === 'dossier') {
    const places: any = await transferApi.receivingLocations(selected.id);
    if (token !== viewRequest) return;
    locations.value = places.data || [];
  }
  if (value === 'notice') {
    const id = form.value.id;
    const response: any = await transferApi.publicReviews(id);
    if (token !== viewRequest || form.value.id !== id) return;
    publicReviewOptions.value = response.data || [];
    if (!publicReviewOptions.value.length) {
      message.warning('请先完成当前结果公告稿的内容核查');
      return;
    }
    noticeSubjectId = id;
    await nextTick();
    const selected = publicReviewOptions.value[0];
    evidenceRef.value?.open({
      disclosureReviewId: selected.value,
      publicContentFileIds: selected.publicContentFileIds,
    });
  } else {
    await nextTick();
    if (token !== viewRequest) return;
    evidenceRef.value?.open(value === 'dossier' ? transferEvidenceInitial(selected.dossier) : {});
  }
}

function openTermination(stage: 'propose' | 'confirm') {
  if(stage === 'propose' && !canProposeTermination.value) return;
  if(stage === 'confirm' && !canConfirmTermination.value) return;
  terminationStage.value = stage;
  terminationRef.value?.open(stage === 'propose' ? { proposedAt: Date.now() } : { confirmedAt: Date.now() });
}
async function saveTermination(value: Record<string, any>) {
  const id = form.value.id;
  const detail = termination.value;
  if(!id || !detail) return false;
  const proposal = detail.records?.find((row: any) => row.kind === 'PROPOSAL');
  if(terminationStage.value === 'propose' && !canProposeTermination.value) return false;
  if(terminationStage.value === 'confirm' && !canConfirmTermination.value) return false;
  const confirmed = await new Promise<boolean>((resolve) => dialog.warning({
    title: terminationStage.value === 'propose' ? '确认提出终止执行' : '确认本人签署终止执行',
    content: terminationStage.value === 'propose'
      ? (form.value.dossier?.kind === 'STATE_EXCHANGE'
        ? '原批准及行政原件会永久保留。同组每件均须引用同一撤回依据，分别完成双方四人确认后才会整组停止执行并释放业务锁。请核对藏品仍在各自原馆。'
        : '原批准及行政原件会永久保留。四名指定责任人依次确认后，本申请才停止执行并释放业务锁。请核对行政依据和藏品仍在原馆。')
      : (form.value.dossier?.kind === 'STATE_EXCHANGE'
        ? '请核对共同撤回依据、当前保管状态及本人签署原件。同组所有申请均完成双方确认后才会整组停止执行并释放业务锁。'
        : '请核对同一撤回依据、当前保管状态及本人签署原件。最后一名责任人确认后将停止本方案执行并释放业务锁。'),
    positiveText: '确认登记', negativeText: '返回核对',
    onPositiveClick: () => resolve(true), onNegativeClick: () => resolve(false),
    onClose: () => resolve(false), onMaskClick: () => resolve(false),
  }));
  if(!confirmed) return false;
  if(terminationStage.value === 'propose') {
    await transferApi.proposeTermination(id, { ...value, proposerUserId: detail.currentUserId });
  } else {
    if(!proposal?.id || !detail.nextNode) return false;
    await transferApi.confirmTermination(id, { ...value, proposalRecordId: proposal.id,
      nodeKey: detail.nextNode, confirmerUserId: detail.currentUserId });
  }
  message.success('终止办理原件已登记');
}
async function saveEvidence(value: Record<string, any>) {
  const data = transferEvidencePayload(value),
    id = kind.value === 'notice' ? noticeSubjectId : editingForm.value.id;
  if (kind.value === 'dossier')
    await transferApi.save({ ...editingForm.value, dossier: { ...editingForm.value.dossier, ...data } });
  else if (kind.value === 'administrative') await transferApi.administrative(id, data);
  else if (kind.value === 'notice') await transferApi.notice(id, { ...data, kind: 'RESULT' });
  else {
    const confirmed = await new Promise<boolean>((resolve) =>
      dialog.warning({
        title: '确认登记实际交接并过户',
        content: '请核实包装运输已按批准的标准和方案逐件签署，双馆纸质账、档案和实物交接均已签署。此操作将同步接收馆归属和库位，永久保留原馆总账与本次原件。',
        positiveText: '确认登记实际交接',
        negativeText: '返回核对',
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onMaskClick: () => resolve(false),
      }),
    );
    if (!confirmed) return false;
    await transferApi.execute(id, data);
  }
  message.success('资料已保存');
}
async function submit() {
  busy.value = true;
  try {
    await transferApi.submit(form.value.id);
    message.success('已提交双方确认');
    await reload();
  } finally {
    busy.value = false;
  }
}
watch(show, (v) => {
  if (!v) {
    ++viewRequest;
    busy.value = false;
  }
});
onBeforeUnmount(() => {
  ++viewRequest;
});
defineExpose({ open });
</script>
