<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="藏品退出办理">
    <NSpace vertical :size="20">
      <CollectionApprovalProgress v-if="show && form.id" :business-id="form.id" business-type="COLLECTION_DEREGISTER" :fetch-flow="deregistrationApi.flow" :revision="form" />
      <CollectionArchiveIssues :data="form" />
      <NAlert v-if="readError" type="error">{{ readError }}</NAlert>
      <NAlert v-if="form.sourceIssue" type="warning">{{ form.sourceIssue }}</NAlert>
      <NButton :loading="loading" @click="reload">刷新资料</NButton>
      <NAlert v-if="form.vetoUntil" type="error"
        >专家不同意退出，程序已终止。{{ formatLocalDateValue(form.vetoUntil) }} 前不能就同一件藏品重新启动退出。</NAlert
      >
      <NAlert v-else type="info">{{
        form.executedAt
          ? form.currentNoticeComplete
            ? '退出及当前版本双渠道结果公告已留档。'
            : '退出已执行，请登记博物馆现场及官方网站的账物处置公告。'
          : '先固定退出方案，再逐件登记专家意见、公示和行政凭证。馆内批准后仍须签署并办理实际退出。'
      }}</NAlert>
      <NForm :model="form" label-placement="left" label-width="165" :disabled="!editable">
        <NFormItem label="藏品" required
          ><CollectionSelect v-model:value="form.collectionId" :disabled="!!form.id || !editable"
        /></NFormItem>
        <NFormItem label="法定退出情形" required
          ><NSelect v-model:value="form.reason" :options="reasonOptions"
        /></NFormItem>
        <NFormItem label="逐件退出理由" required><NInput v-model:value="form.detail" type="textarea" /></NFormItem>
        <NFormItem label="方案实际形成日期" required
          ><NDatePicker v-model:value="form.dossier.proposedOn" type="date"
        /></NFormItem>
        <NFormItem label="处置方案原件" required
          ><CollectionCaseOriginalFiles v-if="!editable" case-type="deregistration" :case-id="form.id" :archive-files="proposalFiles" :value="form.dossier.dispositionPlanFileIds" /><HUploadInput v-else
            v-model:value="form.dossier.dispositionPlanFileIds"
            :disabled="!editable"
            :original-url="form.id ? originalUrl : undefined"
            @upload-finish="form.dossier.dispositionPlanFileIds = $event.id"
        /></NFormItem>
        <NFormItem label="法律法规依据"
          ><CollectionCaseOriginalFiles v-if="!editable" case-type="deregistration" :case-id="form.id" :archive-files="proposalFiles" :value="form.dossier.legalBasisFileIds" /><HUploadInput v-else
            v-model:value="form.dossier.legalBasisFileIds"
            :disabled="!editable"
            :original-url="form.id ? originalUrl : undefined"
            @upload-finish="form.dossier.legalBasisFileIds = $event.id"
            placeholder="其他法定情形或非国有馆退出必填"
        /></NFormItem>
        <NFormItem label="退出处置去向" required
          ><NSelect v-model:value="form.dossier.destinationType" :options="destinationOptions"
        /></NFormItem>
        <NFormItem v-if="form.dossier.destinationType === 'AUXILIARY_ACCOUNT'" label="辅助账编号" required
          ><NInput v-model:value="form.dossier.auxiliaryRecordNo"
        /></NFormItem>
        <NFormItem v-if="form.dossier.destinationType === 'AUXILIARY_ACCOUNT'" label="辅助账保管位置" required
          ><NInput v-model:value="form.dossier.auxiliaryLocation"
        /></NFormItem>
        <NFormItem label="接收单位"><NInput v-model:value="form.dossier.recipient" /></NFormItem>
        <NFormItem label="接收单位国有性质依据"
          ><CollectionCaseOriginalFiles v-if="!editable" case-type="deregistration" :case-id="form.id" :archive-files="proposalFiles" :value="form.dossier.recipientStateEvidenceFileIds" /><HUploadInput v-else
            v-model:value="form.dossier.recipientStateEvidenceFileIds"
            :disabled="!editable"
            :original-url="form.id ? originalUrl : undefined"
            @upload-finish="form.dossier.recipientStateEvidenceFileIds = $event.id"
        /></NFormItem>
        <NFormItem label="保管部门负责人" required
          ><UserSelect v-model:value="form.dossier.keeperUserId" :disabled="!editable"
        /></NFormItem>
        <NFormItem label="法定代表人或主要负责人" required
          ><UserSelect v-model:value="form.dossier.leaderUserId" :disabled="!editable"
        /></NFormItem>
        <NFormItem label="其他原始资料"
          ><CollectionCaseOriginalFiles v-if="!editable" case-type="deregistration" :case-id="form.id" :archive-files="proposalFiles" :value="form.attachmentIds" /><HUploadInput v-else
            v-model:value="form.attachmentIds"
            :disabled="!editable"
            :original-url="form.id ? originalUrl : undefined"
            @upload-finish="form.attachmentIds = $event.id"
        /></NFormItem>
      </NForm>
      <NSpace>
        <NButton v-if="editable" type="primary" :loading="saving" @click="saveDraft">保存退出方案</NButton>
        <NButton v-if="collecting" :loading="saving" @click="submit">材料齐备，提交馆内确认</NButton>
        <NButton v-if="form.id" @click="flowRef?.open(form.id, '藏品退出执行确认')">查看审批</NButton>
        <NButton
          v-permission="'COLLECTION_LEDGER_CORRECT'"
          v-if="canOperate && form.flowStatus === 'PASS' && !form.executedAt"
          type="warning"
          @click="openEvidence('execute')"
          >签署并办理实际退出</NButton
        >
        <NTooltip v-if="showVoidBlocked" trigger="hover">
          <template #trigger>
            <NButton type="error" disabled>作废</NButton>
          </template>
          {{ voidBlockedHint }}
        </NTooltip>
      </NSpace>
      <NText depth="3">国有馆间交换、调拨请从跨馆调拨办理。历史记录未核实藏品性质时，请先办理信息变更。</NText>
      <NTabs v-if="form.id" type="line">
        <NTabPane name="experts" tab="逐件专家评估">
          <NButton
            v-if="collecting && evaluated && !form.notices?.length && !form.administrativeEvidence?.length"
            @click="openEvidence('expert')"
            >登记一名馆外专家的独立意见</NButton
          >
          <DeregistrationEvidenceList :archives="form.archives" :case-id="form.id" :experts="form.experts" style="margin-top: 14px" />
        </NTabPane>
        <NTabPane name="notices" tab="公示与结果公告">
          <NButton v-permission="'COLLECTION_ADMIN,COLLECTION_PUBLISH'" @click="openDisclosure"
            >核查公示或公告公开稿</NButton
          >
          <NButton v-if="canOperate && (collecting || form.executedAt)" @click="openEvidence('notice')"
            >登记已实际发布的公示或公告</NButton
          >
          <NText depth="3" style="display: block; margin: 12px 0"
            >公示须涵盖藏品基本情况、每名专家意见和处置方案，在主管文物部门官网、博物馆现场及官网同时公示至少三十个完整工作日。各渠道须关联同一核查版本；公开稿订正后须用新版重新核对三渠道共同期限，不混用旧版记录；进入行政办理前可追加同渠道的结束凭据，原记录仍保留。</NText
          >
          <DeregistrationEvidenceList :archives="form.archives" :case-id="form.id" :notices="form.notices" />
        </NTabPane>
        <NTabPane name="administrative" tab="行政批准备案">
          <NButton v-if="collecting" @click="openEvidence('administrative')">登记行政批复或备案凭证</NButton>
          <DeregistrationEvidenceList :archives="form.archives" :case-id="form.id" :administrative-evidence="form.administrativeEvidence" style="margin-top: 14px" />
        </NTabPane>
      </NTabs>
    </NSpace>
  </HPageForm>
  <CollectionEvidenceForm
    :key="`CollectionEvidenceForm-${evidenceEpoch}`"
    ref="evidenceRef"
    :title="evidenceTitle"
    :fields="evidenceFields"
    :save="evidenceSave"
    @saved="reload"
  />
  <CollectionDisclosureDrawer :key="`CollectionDisclosureDrawer-${detailEpoch}`" ref="disclosureRef" @updated="reload" />
  <FlowFormDrawer
    :key="`FlowFormDrawer-${detailEpoch}`"
    ref="flowRef"
    :fetch-fn="deregistrationApi.flow"
    :cancel-api="deregistrationApi.cancel"
    :void-api="deregistrationApi.voidRecord"
    @success="reload"
  />
</template>
<script setup lang="ts">
import CollectionApprovalProgress from './CollectionApprovalProgress.vue';
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import CollectionCaseOriginalFiles from './CollectionCaseOriginalFiles.vue';
const proposalFiles = computed(() => (form.value.archives || []).filter((archive: any) => archive.phase === 'SUBMITTED').flatMap((archive: any) => archive.files || []));
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { formatLocalDateValue } from '@/utils';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import CollectionSelect from './CollectionSelect.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import DeregistrationEvidenceList from './DeregistrationEvidenceList.vue';
import CollectionDisclosureDrawer from './CollectionDisclosureDrawer.vue';
import { deregistrationApi } from '../api/deregistration';
import { useAuthStore } from '@/store';
import {
  DEREGISTRATION_VOID_BLOCKED_HINT,
  DEREGISTRATION_WRITE_CODES,
  deregistrationVoidBlockedReason,
} from '../utils/deregistrationPermission';
import { orgApi } from '../api/org';
import type { EvidenceField } from '../utils/evidenceFields';
const emit = defineEmits<{ (e: 'saved'): void }>();
const message = useMessage(),
  dialog = useDialog();
const show = ref(false),
  saving = ref(false),
  form = ref<any>({ dossier: {} });
function originalUrl(fileId: string) {
  return `/collection/deregistration/${encodeURIComponent(form.value.id || '')}/original/${encodeURIComponent(fileId)}`;
}
const flowRef = ref<any>(),
  evidenceRef = ref<any>(),
  disclosureRef = ref<any>(),
  publicReviewOptions = ref<any[]>([]),
  paperBooks = ref<{ id: string; bookNo: string; book: { pageCount: number } }[]>([]),
  noticeResult = ref(false);
let active = true,
  readVersion = 0,
  signingVersion = 0,
  requestedId: string | undefined;
const loading = ref(false),
  readError = ref(''),
  evidenceEpoch = ref(0),
  detailEpoch = ref(0);
const evidenceSave = ref<(value: Record<string, any>) => Promise<any>>(async () => {
  throw new Error('请重新打开登记表');
});
const canOperate = computed(
  () => show.value && !loading.value && !readError.value && !form.value.sourceIssue && form.value.canOperate === true,
);
const voidBlockedHint = computed(
  () => deregistrationVoidBlockedReason(form.value.flowStatus, form.value.executedAt) || DEREGISTRATION_VOID_BLOCKED_HINT,
);
/** PASS / 已通过且未 execute：展示禁用「作废」+ tooltip，避免误点打到后端 400。 */
const showVoidBlocked = computed(
  () =>
    canOperate.value &&
    !!form.value.id &&
    String(form.value.flowStatus || '') === 'PASS' &&
    (form.value.executedAt == null || String(form.value.executedAt).trim() === ''),
);
function invalidateEvidence() {
  ++signingVersion;
  ++evidenceEpoch.value;
  publicReviewOptions.value = [];
  paperBooks.value = [];
}
function clearData() {
  form.value = { dossier: {} };
  ++detailEpoch.value;
}
function failed(e: unknown) {
  readError.value = e instanceof Error ? e.message : '退出资料读取失败';
  clearData();
  invalidateEvidence();
}
watch(show, (value) => {
  if (!value) {
    ++readVersion;
    clearData();
    invalidateEvidence();
  }
});
onBeforeUnmount(() => {
  active = false;
  ++readVersion;
  clearData();
  invalidateEvidence();
});
const collecting = computed(
  () =>
    canOperate.value &&
    !!form.value.id &&
    form.value.flowStatus === 'DRAFT' &&
    !form.value.flowInstanceId &&
    !form.value.vetoUntil,
);
const editable = computed(
  () =>
    canOperate.value &&
    (!form.value.id ||
      (collecting.value &&
        !form.value.experts?.length &&
        !form.value.notices?.length &&
        !form.value.administrativeEvidence?.length)),
);
const evaluated = computed(() => ['DAMAGE_NO_VALUE', 'OTHER_BELOW_STANDARD'].includes(form.value.reason));
const reasonOptions = [
  { label: '老化、腐蚀等毁损且无保存价值', value: 'DAMAGE_NO_VALUE' },
  { label: '其他藏品不够入藏标准', value: 'OTHER_BELOW_STANDARD' },
  { label: '不可抗力灭失', value: 'FORCE_MAJEURE' },
  { label: '法律法规规定的其他情形', value: 'OTHER_LEGAL' },
];
const destinationOptions = [
  { label: '纳入辅助账册', value: 'AUXILIARY_ACCOUNT' },
  { label: '交国有教学及科研单位保管使用', value: 'STATE_TEACHING_RESEARCH' },
  { label: '已灭失', value: 'LOST' },
  { label: '其他法律法规允许的处置', value: 'OTHER_LEGAL' },
];
const channelOptions = [
  { label: '主管文物部门官方网站', value: 'AUTHORITY_WEBSITE' },
  { label: '博物馆现场', value: 'MUSEUM_ONSITE' },
  { label: '博物馆官方网站', value: 'MUSEUM_WEBSITE' },
];
const authorityOptions = [
  { label: '主管文物行政部门', value: 'COMPETENT' },
  { label: '省级文物行政部门', value: 'PROVINCE' },
  { label: '国务院文物行政部门', value: 'NATIONAL' },
];
const evidenceKind = ref<'expert' | 'notice' | 'administrative' | 'execute'>('expert');
const evidenceTitle = computed(
  () =>
    ({
      expert: '登记逐件独立专家意见',
      notice: '登记实际公示或公告',
      administrative: '登记行政批准备案',
      execute: '签署并实际退出',
    })[evidenceKind.value],
);
const field = (key: string, label: string, type: EvidenceField['type'] = 'text', required = true): EvidenceField => ({
  key,
  label,
  type,
  required,
});
const evidenceFields = computed<EvidenceField[]>(() => {
  if (evidenceKind.value === 'expert')
    return [
      field('expertKey', '专家稳定编号'),
      field('expertName', '专家姓名'),
      field('organization', '任职机构'),
      field('userId', '关联系统账号（如有）', 'user', false),
      field('professionalTitle', '文物博物高级职称'),
      field('specialty', '研究方向'),
      field('seniorQualificationFileIds', '高级职称证明', 'file'),
      field('independenceStatementFileIds', '馆外专家独立声明', 'file'),
      field('assessedAt', '实际评估日期', 'date'),
      {
        ...field('verdict', '是否同意退出', 'select'),
        options: [
          { label: '同意退出', value: 'AGREE' },
          { label: '不同意退出', value: 'DISAGREE' },
        ],
      },
      field('opinion', '逐件评估说明', 'textarea'),
      field('signedOpinionFileIds', '专家本人签署的书面意见', 'file'),
    ];
  if (evidenceKind.value === 'notice')
    return [
      { ...field('disclosureReviewId', '已核查的公开稿版本', 'select'), options: publicReviewOptions.value },
      {
        ...field('kind', '公示或公告类型', 'select'),
        options: noticeResult.value
          ? [{ label: '退出结果公告', value: 'RESULT' }]
          : [{ label: '退出前公示', value: 'CONSULTATION' }],
      },
      {
        ...field('channel', '发布渠道', 'select'),
        options: noticeResult.value ? channelOptions.filter((c) => c.value !== 'AUTHORITY_WEBSITE') : channelOptions,
      },
      field('startedOn', '实际开始日期', 'date'),
      field('endedOn', '已完整经过的结束日期', 'date', false),
      field('urlOrLocation', '发布网址或现场地点'),
      field('publicContentFileIds', '对外公示内容原件', 'file'),
      field('publicationEvidenceFileIds', '实发凭据或现场照片', 'file'),
    ];
  if (evidenceKind.value === 'administrative')
    return [
      { ...field('level', '行政机关层级', 'select'), options: authorityOptions },
      {
        ...field('action', '行政行为', 'select'),
        options: [
          { label: '批准', value: 'APPROVAL' },
          { label: '备案', value: 'FILING' },
        ],
      },
      field('authorityName', '行政机关全称'),
      field('documentNo', '批准或备案文号'),
      field('issuedAt', '批复或备案日期', 'date'),
      field('fileIds', '行政文书原件', 'file'),
    ];
  return [
    { ...field('paperLedgerBookId', '本馆纸质总账', 'select'), options: paperBooks.value.map((book) => ({ label: `${book.bookNo}（${book.book.pageCount} 页）`, value: book.id })) },
    { ...field('paperLedgerPageNo', '实际订正页码', 'number'), min: 1 },
    field('paperUpdatedAt', '纸质账订正日期', 'date'),
    field('signedChangeFileIds', '已签署信息变更表', 'file'),
    field('dispositionReceiptFileIds', '辅助账登记或接收处置凭据', 'file'),
  ];
});
async function open(id?: string) {
  if (!active) return;
  requestedId = id;
  show.value = true;
  if (!id) {
    ++readVersion;
    readError.value = '';
    loading.value = false;
    clearData();
    invalidateEvidence();
    // 新建草稿没有后端 canOperate，按注销写权限码判断，避免无权限账号填完整张表才 403。
    form.value = { dossier: {}, flowStatus: 'DRAFT', canOperate: useAuthStore().hasPermission(DEREGISTRATION_WRITE_CODES) };
    return;
  }
  await load(id);
}
async function load(id: string) {
  const version = ++readVersion;
  readError.value = '';
  clearData();
  loading.value = true;
  try {
    const [response, archived]: any[] = await Promise.all([deregistrationApi.get(id), deregistrationApi.archive(id)]);
    if (!active || !show.value || version !== readVersion) return;
    if (!response.data || response.data.id !== id) throw new Error('退出资料身份不一致或记录不存在');
    if (!Array.isArray(archived.data)) throw new Error('退出档案返回格式不正确');
    form.value = { ...response.data, archives: archived.data, dossier: response.data.dossier || {} };
  } catch (e) {
    if (active && show.value && version === readVersion) failed(e);
  } finally {
    if (version === readVersion) loading.value = false;
  }
}
async function reload() {
  if (!active || !show.value || !requestedId) return;
  await load(requestedId);
  if (!readError.value && active && show.value) emit('saved');
}
async function saveDraft() {
  if (!canOperate.value || !editable.value || saving.value) return;
  if (!form.value.collectionId || !form.value.reason || !form.value.detail?.trim()) {
    message.warning('请填写藏品、法定情形和逐件理由');
    return;
  }
  const version = readVersion;
  saving.value = true;
  try {
    const response: any = await deregistrationApi.save(form.value);
    if (!active || !show.value || version !== readVersion) return;
    await open(response.data);
    if (!readError.value) {
      message.success('退出方案已保存');
      emit('saved');
    }
  } finally {
    saving.value = false;
  }
}
async function submit() {
  if (!collecting.value || saving.value) return;
  const id = form.value.id,
    version = readVersion;
  saving.value = true;
  try {
    await deregistrationApi.submit(id);
    if (!active || !show.value || version !== readVersion) return;
    message.success('已提交退出执行确认');
    await reload();
  } finally {
    saving.value = false;
  }
}
async function openDisclosure() {
  if (!active || !show.value || loading.value || readError.value || !form.value.id) return;
  await disclosureRef.value?.open({
    orgId: form.value.orgId,
    subject: form.value.executedAt ? 'EXIT_RESULT' : 'EXIT_CONSULTATION',
    subjectId: form.value.id,
  });
}
async function openEvidence(kind: typeof evidenceKind.value) {
  if (!canOperate.value || !form.value.id) return;
  const target = form.value.id,
    version = readVersion,
    signature = ++signingVersion;
  evidenceKind.value = kind;
  evidenceSave.value = (value) => saveEvidence(value, target, kind, signature);
  if (kind === 'execute') {
    paperBooks.value = [];
    try {
      if (!form.value.orgId) throw new Error('退出申请缺少收藏单位，请刷新核对');
      const response: any = await orgApi.paperBooks(form.value.orgId);
      if (!active || !show.value || version !== readVersion || signature !== signingVersion || form.value.id !== target) return;
      if (!Array.isArray(response?.data)) throw new Error('未取得本馆纸质总账登记资料，请重新读取');
      const books = response.data as typeof paperBooks.value;
      if (books.some((book) => !book || !book.id || !book.bookNo || !Number.isInteger(book.book?.pageCount) || book.book.pageCount < 1))
        throw new Error('纸质总账登记资料不完整，请先核对账册原件');
      if (!books.length) throw new Error('本馆尚未登记纸质总账及保管原件，请先完成账册登记');
      paperBooks.value = books;
    } catch (e) {
      if (active && show.value && version === readVersion && signature === signingVersion) failed(e);
      return;
    }
  }
  if (kind === 'notice') {
    const id = form.value.id;
    const result = !!form.value.executedAt;
    let response: any;
    try {
      response = await deregistrationApi.publicReviews(id);
    } catch (e) {
      if (active && show.value && version === readVersion && signature === signingVersion) failed(e);
      return;
    }
    if (!active || !show.value || version !== readVersion || signature !== signingVersion || form.value.id !== id)
      return;
    publicReviewOptions.value = response.data || [];
    if (!publicReviewOptions.value.length) {
      message.warning('请先完成当前公示或公告稿的内容核查');
      return;
    }
    noticeResult.value = result;
    await nextTick();
    if (!active || !show.value || version !== readVersion || signature !== signingVersion) return;
    const selected = publicReviewOptions.value[0];
    evidenceRef.value?.open({
      kind: result ? 'RESULT' : 'CONSULTATION',
      disclosureReviewId: selected.value,
      publicContentFileIds: selected.publicContentFileIds,
    });
  } else {
    await nextTick();
    if (!active || !show.value || version !== readVersion || signature !== signingVersion) return;
    evidenceRef.value?.open({});
  }
}

async function saveEvidence(
  value: Record<string, any>,
  id: string,
  kind: typeof evidenceKind.value,
  signature: number,
) {
  const requireOpen = () => {
    if (!active || !show.value || loading.value || readError.value || signature !== signingVersion)
      throw new Error('登记状态已失效，请刷新后重新打开');
  };
  requireOpen();
  if (kind === 'expert') {
    const { verdict, ...data } = value;
    await deregistrationApi.expert(id, { ...data, agree: verdict === 'AGREE' });
  } else if (kind === 'notice') await deregistrationApi.notice(id, value);
  else if (kind === 'administrative') await deregistrationApi.administrative(id, value);
  else {
    const book = paperBooks.value.find((item) => item.id === value.paperLedgerBookId);
    if (!book || !Number.isInteger(value.paperLedgerPageNo) || value.paperLedgerPageNo < 1 || value.paperLedgerPageNo > book.book.pageCount)
      throw new Error('请选择本馆已登记纸质总账及有效订正页码');
    const confirmed = await new Promise<boolean>((resolve) =>
      dialog.warning({
        title: '确认实际退出馆藏',
        content: `此操作将办理馆藏退出，永久保留总登记号及原始档案。请核对${book.bookNo}第${value.paperLedgerPageNo}页已订正，且处置交接已完成。`,
        positiveText: '确认退出',
        negativeText: '返回核对',
        onPositiveClick: () => resolve(true),
        onNegativeClick: () => resolve(false),
        onClose: () => resolve(false),
        onMaskClick: () => resolve(false),
      }),
    );
    if (!confirmed) return false;
    requireOpen();
    await deregistrationApi.execute(id, value);
  }
  requireOpen();
  message.success('原始材料已登记并归档');
}
defineExpose({ open });
</script>
