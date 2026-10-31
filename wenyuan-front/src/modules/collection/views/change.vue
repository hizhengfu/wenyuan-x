<template>
  <HPageDataTable auto-scroll-x
    class="collection-data-page"
    ref="tableRef"
    :title="typeof route.query.title === 'string' && route.query.title.trim() ? route.query.title : '藏品信息变更'"
    :columns="columns"
    :fetch-data="changeApi.page"
    :params="queryParams"
    :is-edit="false"
    :is-delete="false"
    row-key-field="id"
    @add="onAdd"
    @row-view="openDetail"
  >
    <template #queryForm>
      <NFormItem label="关联藏品"><CollectionSelect v-model:value="queryParams.collectionId" /></NFormItem>
      <NFormItem label="审批状态"
        ><HDictSelect v-model:value="queryParams.flowStatus" type="flow_biz_status" clearable
      /></NFormItem>
    </template>
    <template #toolbar-extra>
      <OriginalHistoryButton business-type="COLLECTION_CHANGE" />
      <CollectionFieldLabel label="生效条件" help="批准并完成签署归档后，变更才会写入总账和正式编目。总登记号永久保留。" />
    </template>
    <HPageForm
      :key="`edit-${viewRevision}`"
      :disabled="loading || saving"
      v-model:show="editVisible"
      title="藏品信息变更申请"
      :model="form"
      label-width="170"
    >
      <NAlert v-if="loading" type="info">正在读取并核对资料，请稍候。</NAlert>
      <CollectionChangeCorrectionOrigin :archive-id="stageArchive(formArchiveRows, 'CORRECTION_ORIGIN')?.id" :archive-files="stageFiles(formArchiveRows, 'CORRECTION_ORIGIN')" :origin="form.correction" :case-id="form.id" />
      <NFormItem label="藏品" required
        ><CollectionSelect :value="form.collectionId" :disabled="!!form.id" @update:value="selectCollection"
      /></NFormItem>
      <NFormItem label="总登记号">{{ form.totalRegNo || '选择藏品后显示' }}</NFormItem>
      <NFormItem label="变更事由" required
        ><NInput v-model:value="form.reason" type="textarea" :maxlength="4000"
      /></NFormItem>
      <NFormItem label="保管部门负责人" required
        ><UserSelect v-model:value="form.keeperUserId" placeholder="选择本馆具备保管负责人岗位的人员"
      /></NFormItem>
      <NFormItem label="法定代表人或主要负责人" required
        ><UserSelect v-model:value="form.leaderUserId" placeholder="选择本馆法定代表人或主要负责人"
      /></NFormItem>
      <NFormItem label="变更依据" required
        ><HUploadInput v-model:value="form.evidenceFileIds" @upload-finish="form.evidenceFileIds = $event.id" clearable
      /></NFormItem>
      <NSpace vertical :size="12">
        <NButton :disabled="loading || saving || !form.collectionId" @click="editContent">编辑拟变更内容</NButton>
        <CollectionChangeDiff :before="form.before" :after="form.content" />
        <NButton v-if="!form.setAmendment" :disabled="loading || saving || !form.collectionId" @click="loadSetAmendment"
          >加入套件订正</NButton
        >
        <CollectionSetAmendmentPanel
          :value="form.setAmendment"
          @update:value="setHandler"
          :org-id="form.orgId"
          editable
          :disabled="loading || saving"
        />
        <NAlert type="info"
          >经办人、保管部门负责人和主要负责人分别办理。提交后保留本次申请原件；驳回或撤销后可通过“补正申请”另建草稿，保留原申请关联。</NAlert
        >
      </NSpace>
      <template #form-action>
        <NSpace
          ><NButton :disabled="loading || saving" :loading="saving" @click="save(false)">保存草稿</NButton
          ><NButton type="primary" :disabled="loading || saving" :loading="saving" @click="save(true)"
            >保存并提交</NButton
          ></NSpace
        >
      </template>
    </HPageForm>
    <HPageForm
      :key="`correction-${viewRevision}`"
      :disabled="loading || correcting"
      v-model:show="correctionVisible"
      title="依据原申请另建补正草稿"
      :model="correctionInput"
    >
      <NAlert type="info"
        >原申请及审批原件保留。新草稿按当前账目重建变更前快照，请核对拟变更内容、当前责任人和附件后重新提交审批。</NAlert
      >
      <NFormItem label="原申请">{{ correctionTarget?.id }}</NFormItem>
      <NFormItem label="补正原因" required
        ><NInput v-model:value="correctionInput.reason" type="textarea" :maxlength="4000"
      /></NFormItem>
      <NFormItem label="本次补正依据" required
        ><HUploadInput
          v-model:value="correctionInput.evidenceFileIds"
          @upload-finish="correctionInput.evidenceFileIds = $event.id"
          clearable
      /></NFormItem>
      <template #form-action
        ><NButton type="primary" :disabled="loading || correcting" :loading="correcting" @click="createCorrection"
          >建立补正草稿</NButton
        ></template
      >
    </HPageForm>
    <CatalogFormModal
      v-if="editVisible"
      :key="viewRevision"
      ref="contentRef"
      proposal-mode
      @proposal="contentHandler"
    />
    <HPageForm content-only :enable-cancel="false" v-model:show="detailVisible" title="信息变更档案">
      <NAlert v-if="loading" type="info">正在读取并核对档案，请稍候。</NAlert>
      <NSpace v-if="detail" vertical :size="16">
        <NAlert v-if="detail.collectionEvidenceIssue" type="warning"><CollectionFieldLabel label="原馆资料暂无法核实，仍可查阅本次变更记录。" :help="detail.collectionEvidenceIssue" /></NAlert>
        <NDescriptions bordered :column="2">
          <NDescriptionsItem label="藏品">{{ detail.collectionName }}（{{ detail.totalRegNo }}）</NDescriptionsItem>
          <NDescriptionsItem label="办理状态">{{ status(detail) }}</NDescriptionsItem>
          <NDescriptionsItem label="变更事由" :span="2" content-style="white-space: pre-wrap; overflow-wrap: anywhere;">{{ detail.reason }}</NDescriptionsItem>
          <NDescriptionsItem label="纸质账册及页码">{{ detail.paperLedgerRef || '待办理' }}</NDescriptionsItem>
          <NDescriptionsItem label="纸质账订正日期">{{
            detail.paperUpdatedAt ? formatLocalDateValue(detail.paperUpdatedAt) : '待办理'
          }}</NDescriptionsItem>
          <NDescriptionsItem label="变更依据"
            ><CollectionCaseOriginalFiles v-if="stageArchive(archiveRows, detail.appliedAt ? 'APPLIED' : 'SUBMITTED')" case-type="change" :archive-id="stageArchive(archiveRows, detail.appliedAt ? 'APPLIED' : 'SUBMITTED')?.id" :case-id="detail.id" :archive-files="stageFiles(archiveRows, detail.appliedAt ? 'APPLIED' : 'SUBMITTED')" :value="detail.evidenceFileIds"
          /><HFileList
            v-else-if="detail.flowStatus === 'DRAFT' && !detail.flowInstanceId && !detail.appliedAt && detail.evidenceFileIds && !archiveRows.some(archive => archive.phase === 'SUBMITTED')"
            :value="detail.evidenceFileIds"
            show-download-btn
          /><NText v-else depth="3">尚无申请归档原件</NText></NDescriptionsItem>
          <NDescriptionsItem label="已签署变更表"
            ><CollectionCaseOriginalFiles v-if="stageArchive(archiveRows, 'APPLIED')" case-type="change" :archive-id="stageArchive(archiveRows, 'APPLIED')?.id" :case-id="detail.id" :archive-files="stageFiles(archiveRows, 'APPLIED')" :value="detail.signedChangeFileIds"
          /><NText v-else depth="3">尚未签署生效归档</NText></NDescriptionsItem>
        </NDescriptions>
        <NAlert v-if="detail.appliedAt" type="info">
          本次变更已永久归档。请依据新版正式编目核对并追加藏品档案目录，保留旧版历史；如涉及公开资料，还须核对实际渠道的更新或撤下凭据。
          <NButton
            v-if="canManageArchive && detail.orgId && detail.collectionId"
            text
            type="primary"
            @click="openArchivePacket(detail)"
            >核对藏品档案目录</NButton
          >
        </NAlert>
        <CollectionChangeDiff :before="detail.before" :after="detail.content" />
        <CollectionSetAmendmentPanel :value="detail.setAmendment" />
        <CollectionChangeCorrectionOrigin :archive-id="stageArchive(archiveRows, 'CORRECTION_ORIGIN')?.id" :archive-files="stageFiles(archiveRows, 'CORRECTION_ORIGIN')" :origin="detail.correction" :case-id="detail.id" />
        <NSpace>
          <NButton @click="openFlow(detail)">查看审批记录</NButton>
          <NButton v-if="correctionLinks.previous" @click="openRelated(correctionLinks.previous)">查看原申请</NButton>
          <NButton v-for="item in correctionLinks.following" :key="item.id" @click="openRelated(item)"
            >后续补正 {{ item.id }}（{{ status(item) }}）</NButton
          >
        </NSpace>
        <NDivider>永久归档记录</NDivider>
        <NTimeline v-if="archiveRows.length">
          <NTimelineItem
            v-for="archive in archiveRows"
            :key="archive.id"
            :title="archivePhase(archive.phase)"
            :time="formatDateTime(archive.archivedAt)"
          >
            <NAlert v-if="archive.evidenceIssue" type="warning"><CollectionFieldLabel label="已保留变更快照，归档原件暂不可用。" :help="archive.evidenceIssue" /></NAlert>
            <template v-else>已保留当时的变更快照及 {{ archive.files?.length || 0 }} 份附件。</template>
            <NSpace v-if="archive.files?.length" :size="8" style="margin-top: 8px">
              <HFileList :files="archive.files" :original-url="(fileId: string) => detail ? archivedOriginalUrl(detail.id, archive.id, fileId) : undefined" :revision="[archive?.id, archive?.digest]" show-download-btn />
            </NSpace>
          </NTimelineItem>
        </NTimeline>
        <NEmpty v-else description="提交审批后建立永久档案" />
      </NSpace>
    </HPageForm>
    <HPageForm
      :key="`execute-${viewRevision}`"
      :disabled="loading || saving"
      v-model:show="executeVisible"
      title="签署归档并使变更生效"
      :model="execution"
      label-width="150"
    >
      <NAlert type="warning" style="margin-bottom: 16px"
        >请先完成纸质总登记账订正及变更表签字或盖章。确认后将更新系统登记资料和正式编目卡，并永久保留本次变更档案。生效后须依据新版编目核对并追加藏品档案目录。如已有公开稿，旧版将停止向 CMS 供稿；新版须另行核查、启用，并按实际情况核对官网、现场等公开渠道的更新或撤下凭据。</NAlert
      >
      <NFormItem label="纸质总账册号" required
        ><NSelect v-model:value="execution.paperLedgerBookId" :options="paperBookOptions" placeholder="选择本馆已登记并归档的纸质总账"
      /></NFormItem>
      <NFormItem label="实际订正页码" required
        ><NInputNumber v-model:value="execution.paperLedgerPageNo" :min="1" :max="selectedPaperBook?.book.pageCount" :precision="0" placeholder="填写实际订正页码"
      /></NFormItem>
      <NFormItem label="纸质账订正日期" required
        ><NDatePicker v-model:value="execution.paperUpdatedAt" type="date"
      /></NFormItem>
      <NFormItem label="已签署变更表" required
        ><HUploadInput
          v-model:value="execution.signedChangeFileIds"
          @upload-finish="execution.signedChangeFileIds = $event.id"
          clearable
      /></NFormItem>
      <CollectionChangeDiff :before="executeTarget?.before" :after="executeTarget?.content" />
      <CollectionSetAmendmentPanel :value="executeTarget?.setAmendment" />
      <template #form-action
        ><NButton type="primary" :disabled="loading || saving" :loading="saving" @click="execute"
          >确认归档并生效</NButton
        ></template
      >
    </HPageForm>
    <FlowFormDrawer ref="flowRef" :fetch-fn="changeApi.flow" :cancel-api="changeApi.cancel" @success="refresh" />
    <CollectionArchivePacketDrawer ref="archivePacketRef" />
  </HPageDataTable>
</template>
<script setup lang="ts">
import '../styles/collection-page.css';
import OriginalHistoryButton from '@/modules/collection/components/OriginalHistoryButton.vue';
import { computed, h, reactive, ref, watch, onBeforeUnmount, onDeactivated } from 'vue';
import { cloneDeep } from 'lodash-es';
import type { CollectionCatalogContentVo } from '../typings/collection';
import { useRoute } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { NButton, NSpace, NText, useDialog, useMessage } from 'naive-ui';
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import { formatLocalDateValue, formatDateTime } from '@/utils';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import FlowFormDrawer from '@/modules/flow/components/FlowFormDrawer.vue';
import { FLOW_STATUS_MAP } from '@/modules/flow/composables/useFlowApproval';
import CollectionSelect from '../components/CollectionSelect.vue';
import CatalogFormModal from '../components/CatalogFormModal.vue';
import CollectionChangeDiff from '../components/CollectionChangeDiff.vue';
import CollectionChangeCorrectionOrigin from '../components/CollectionChangeCorrectionOrigin.vue';
import CollectionArchivePacketDrawer from '../components/CollectionArchivePacketDrawer.vue';
import CollectionCaseOriginalFiles from '../components/CollectionCaseOriginalFiles.vue';
import { changeApi, type CollectionChangeVo, type SetAmendment } from '../api/change';
import { orgApi } from '../api/org';
import { changedFields } from '../utils/collectionChange';
import CollectionSetAmendmentPanel from '../components/CollectionSetAmendmentPanel.vue';
import { readSetAmendment, hasSetAmendmentChanges, setAmendmentError } from '../utils/collectionSetAmendment';
const route = useRoute();
const message = useMessage();
const dialog = useDialog();
const authStore = useAuthStore();
const canManageArchive = computed(() => authStore.hasPermission('COLLECTION_ADMIN,COLLECTION_ARCHIVE_MANAGE'));
const tableRef = ref<any>(null),
  contentRef = ref<any>(null),
  flowRef = ref<any>(null),
  archivePacketRef = ref<any>(null);
const queryParams = reactive({ collectionId: null as string | null, flowStatus: null as string | null });
const form = ref<CollectionChangeVo>({});
const detail = ref<CollectionChangeVo>();
const archiveRows = ref<any[]>([]);
const formArchiveRows = ref<any[]>([]);
const stageArchive = (rows: any[], phase: string) => { const selected = rows.filter(archive => archive.phase === phase); return selected.length === 1 ? selected[0] : undefined; };
const stageFiles = (rows: any[], phase: string) => stageArchive(rows, phase)?.files || [];
let viewAbort: AbortController | undefined;
function checkedArchives(value: unknown, id: string): any[] {
  if (!Array.isArray(value) || value.some(archive => !archive || typeof archive.id !== 'string' || typeof archive.phase !== 'string' || (archive.bizId && archive.bizId !== id) || (archive.bizType && archive.bizType !== 'COLLECTION_CHANGE')))
    throw new Error('档案所属申请不一致，请核对原件');
  return value;
}
async function readCorrectionArchives(id: string, version: number) {
  const response: any = await changeApi.archive(id, viewAbort?.signal);
  if (!current(version)) return;
  formArchiveRows.value = checkedArchives(response?.data, id);
}
const correctionLinks = ref<{ previous?: CollectionChangeVo; following: CollectionChangeVo[] }>({ following: [] });
const correctionVisible = ref(false),
  correcting = ref(false);
const correctionTarget = ref<CollectionChangeVo>();
const correctionInput = reactive({ requestKey: '', expectedInstanceId: '', reason: '', evidenceFileIds: '' });
type View = 'edit' | 'detail' | 'correction' | 'execute' | 'route';
let activeView: View | null = null,
  disposed = false;
const viewRevision = ref(0),
  loading = ref(false);
const setHandler = ref<(value: SetAmendment) => void>(() => {});
const contentHandler = ref<(value: CollectionCatalogContentVo) => void>(() => {});
function archivePhase(phase: string) {
  if (phase.startsWith('APPROVED_')) return '审批通过归档';
  return (
    (
      {
        CORRECTION_ORIGIN: '补正来由及原申请',
        SUBMITTED: '申请原件',
        APPROVED: '审批通过归档',
        APPLIED: '签署生效归档',
        SET_APPLIED: '套件关系生效原件',
      } as Record<string, string>
    )[phase] || phase
  );
}
function archivedOriginalUrl(caseId: string | undefined, archiveId: string, fileId: string) {
  return `/collection/change/${encodeURIComponent(caseId || '')}/original/${encodeURIComponent(archiveId)}/${encodeURIComponent(fileId)}`;
}
const executeTarget = ref<CollectionChangeVo>();
type PaperBook = { id: string; bookNo: string; book: { pageCount: number } };
const paperBooks = ref<PaperBook[]>([]);
const paperBookOptions = computed(() => paperBooks.value.map((book) => ({ label: `${book.bookNo}（${book.book.pageCount} 页）`, value: book.id })));
const execution = reactive({ paperLedgerBookId: null as string | null, paperLedgerPageNo: null as number | null, paperUpdatedAt: null as number | null, signedChangeFileIds: '' });
const selectedPaperBook = computed(() => paperBooks.value.find((book) => book.id === execution.paperLedgerBookId));
const editVisible = ref(false),
  detailVisible = ref(false),
  executeVisible = ref(false),
  saving = ref(false);
function status(row: CollectionChangeVo) {
  return row.appliedAt
    ? '已生效并归档'
    : row.flowStatus === 'PASS'
      ? '已批准，待签署归档'
      : FLOW_STATUS_MAP[row.flowStatus || 'DRAFT']?.label || row.flowStatus;
}
const columns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '藏品名称', key: 'collectionName', minWidth: 180, columnType: 'title' },
  {
    resizable: true, width: 180, title: '原馆资料核验', key: 'collectionEvidenceIssue',
    render: (row: CollectionChangeVo) => row.collectionEvidenceIssue
      ? h(NText, { type: 'warning' }, () => h(CollectionFieldLabel, {
          label: '原馆资料待核实', help: row.collectionEvidenceIssue,
        }))
      : '--',
  },
  { resizable: true, width: 240, title: '变更事由', key: 'reason', minWidth: 240, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '办理状态', key: 'flowStatus', width: 180, render: status },
  { title: '申请时间', key: 'createTime', width: 180, columnType: 'dateTime' },
  { title: '生效时间', key: 'appliedAt', width: 180, columnType: 'dateTime' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 260,
    fixed: 'right' as const,
    render: (row: CollectionChangeVo) =>
      h(NSpace, { size: 10, wrap: false }, () => [
        h(NButton, { text: true, onClick: () => openDetail(row) }, () => '档案'),
        row.flowStatus === 'DRAFT' && !row.flowInstanceId
          ? h(NButton, { text: true, type: 'primary', onClick: () => openEdit(row) }, () => '编辑提交')
          : null,
        row.flowInstanceId ? h(NButton, { text: true, onClick: () => openFlow(row) }, () => '审批') : null,
        row.flowStatus === 'PASS' && !row.appliedAt
          ? h(NButton, { text: true, type: 'warning', onClick: () => openExecute(row) }, () => '签署生效')
          : null,
        ['REJECT', 'CANCEL', 'VOID'].includes(row.flowStatus || '') && row.flowInstanceId
          ? h(NButton, { text: true, onClick: () => openCorrection(row) }, () => '补正申请')
          : null,
      ]),
  },
];
function clearView() {
  viewAbort?.abort();
  viewAbort = undefined;
  activeView = null;
  viewRevision.value++;
  editVisible.value = detailVisible.value = correctionVisible.value = executeVisible.value = false;
  form.value = {};
  detail.value = undefined;
  archiveRows.value = [];
  formArchiveRows.value = [];
  correctionLinks.value = { following: [] };
  correctionTarget.value = undefined;
  executeTarget.value = undefined;
  paperBooks.value = [];
  Object.assign(correctionInput, { requestKey: '', expectedInstanceId: '', reason: '', evidenceFileIds: '' });
  Object.assign(execution, { paperLedgerBookId: null, paperLedgerPageNo: null, paperUpdatedAt: null, signedChangeFileIds: '' });
  loading.value = saving.value = correcting.value = false;
  contentHandler.value = () => {};
  setHandler.value = () => {};
}
function begin(view: View) {
  clearView();
  activeView = view;
  viewAbort = new AbortController();
  const version = viewRevision.value;
  setHandler.value = (value) => {
    if (current(version, 'edit') && !loading.value && !saving.value) form.value.setAmendment = cloneDeep(value);
  };
  loading.value = true;
  if (view === 'edit') editVisible.value = true;
  if (view === 'detail') detailVisible.value = true;
  if (view === 'correction') correctionVisible.value = true;
  if (view === 'execute') executeVisible.value = true;
  return viewRevision.value;
}
function current(version: number, view?: View) {
  return !disposed && version === viewRevision.value && activeView !== null && (!view || view === activeView);
}
for (const [visible, view] of [
  [editVisible, 'edit'],
  [detailVisible, 'detail'],
  [correctionVisible, 'correction'],
  [executeVisible, 'execute'],
] as const) {
  watch(
    visible,
    (shown) => {
      if (!shown && activeView === view) clearView();
    },
    { flush: 'sync' },
  );
}
onDeactivated(clearView);
watch(() => [authStore.token, authStore.authorizationName, authStore.userInfo?.id, authStore.roles, authStore.permissions], clearView, { deep: true, flush: 'sync' });
onBeforeUnmount(() => {
  disposed = true;
  clearView();
});
function failure(version: number, error: unknown, clear = true) {
  if (!current(version)) return;
  if (clear) clearView();
  message.error(error instanceof Error ? error.message : '资料读取或办理失败，请重新核对原记录');
}
function requireId(value: unknown): string {
  if (typeof value !== 'string' || !value.trim() || value !== value.trim())
    throw new Error('缺少有效记录标识，请重新选择');
  return value;
}
function record(value: unknown, id: string): CollectionChangeVo {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('未取得信息变更资料，请重新读取');
  const row = value as CollectionChangeVo;
  if (row.id !== id || typeof row.flowStatus !== 'string')
    throw new Error('返回的变更记录与所选申请不一致，请重新核对');
  return { ...row, setAmendment: readSetAmendment(row.setAmendment, row.collectionId, row.orgId) };
}
async function readRecord(id: string) {
  const resp: any = await changeApi.get(id);
  return record(resp?.data, id);
}
function requireEditable(row: CollectionChangeVo) {
  if (row.flowStatus !== 'DRAFT' || row.flowInstanceId || row.appliedAt)
    throw new Error('原申请已提交或生效，请保留原记录并重新核对');
}
async function onAdd(collectionId?: unknown) {
  if (typeof collectionId === 'string') return selectCollection(collectionId);
  begin('edit');
  loading.value = false;
}
async function selectCollection(id?: string | null, includeSetRelations = false) {
  const version = begin('edit');
  if (!id) {
    loading.value = false;
    return;
  }
  try {
    const resp: any = await (includeSetRelations
      ? changeApi.proposal(requireId(id), true)
      : changeApi.proposal(requireId(id)));
    if (!current(version, 'edit')) return;
    const value = resp?.data;
    if (
      !value ||
      value.collectionId !== id ||
      value.id ||
      !value.before ||
      !value.content ||
      typeof value.before !== 'object' ||
      typeof value.content !== 'object'
    )
      throw new Error('拟稿来源与所选藏品不一致，请重新核对');
    const amendment = readSetAmendment(value.setAmendment, id, value.orgId);
    if (includeSetRelations && !amendment) throw new Error('未取得套件订正基线，请重新读取');
    form.value = { ...value, setAmendment: amendment };
  } catch (error) {
    failure(version, error);
  } finally {
    if (current(version)) loading.value = false;
  }
}
async function loadSetAmendment() {
  if (activeView !== 'edit' || loading.value || saving.value || !form.value.collectionId || form.value.setAmendment)
    return;
  const version = viewRevision.value,
    id = form.value.collectionId,
    orgId = form.value.orgId;
  loading.value = true;
  try {
    const resp: any = await changeApi.proposal(id, true);
    if (!current(version, 'edit')) return;
    if (
      resp?.data?.collectionId !== id ||
      (orgId && resp.data.orgId !== orgId) ||
      !resp.data.before ||
      changedFields(resp.data.before, form.value.before).length
    )
      throw new Error('套件来源或正式编目基线已变化，请重新打开申请核对');
    const value = readSetAmendment(resp.data.setAmendment, id, resp.data.orgId);
    if (!value) throw new Error('未取得套件原关系，请重新读取');
    form.value.setAmendment = value;
  } catch (error) {
    failure(version, error, false);
  } finally {
    if (current(version, 'edit')) loading.value = false;
  }
}
async function openEdit(row: CollectionChangeVo) {
  const version = begin('edit');
  try {
    const source = await readRecord(requireId(row.id));
    if (!current(version, 'edit')) return;
    requireEditable(source);
    if (source.correction) await readCorrectionArchives(source.id!, version);
    if (!current(version, 'edit')) return;
    form.value = source;
  } catch (error) {
    failure(version, error);
  } finally {
    if (current(version)) loading.value = false;
  }
}
function editContent() {
  if (activeView !== 'edit' || loading.value || saving.value || !form.value.collectionId) return;
  const version = viewRevision.value;
  contentHandler.value = (value) => {
    if (current(version, 'edit') && !loading.value && !saving.value) form.value.content = cloneDeep(value);
  };
  contentRef.value?.open({ collectionId: form.value.collectionId, content: form.value.content || {} });
}
async function save(submit: boolean) {
  if (activeView !== 'edit' || loading.value || saving.value) return;
  if (!form.value.collectionId || !form.value.reason?.trim()) {
    message.warning('请选择藏品并填写变更事由');
    return;
  }
  const setError = setAmendmentError(form.value.setAmendment, form.value.collectionId);
  if (setError) {
    message.warning(setError);
    return;
  }
  if (
    !changedFields(form.value.before, form.value.content).length &&
    !hasSetAmendmentChanges(form.value.setAmendment)
  ) {
    message.warning('请先编辑拟变更内容');
    return;
  }
  if (submit && (!form.value.keeperUserId || !form.value.leaderUserId || !form.value.evidenceFileIds)) {
    message.warning('请补充两名责任人和变更依据');
    return;
  }
  const version = viewRevision.value,
    payload = cloneDeep(form.value);
  saving.value = true;
  try {
    const resp: any = await changeApi.save(payload);
    if (!current(version, 'edit')) return;
    const id = requireId(resp?.data);
    if (payload.id && id !== payload.id) throw new Error('保存结果与原申请不一致，请重新读取原记录');
    form.value.id = id;
    if (payload.setAmendment) {
      const saved = await readRecord(id);
      if (!current(version, 'edit')) return;
      requireEditable(saved);
      if (saved.collectionId !== payload.collectionId || !saved.setAmendment)
        throw new Error('保存后的套件订正来源不一致，请重新读取原申请');
      form.value = saved;
      if (submit && JSON.stringify(payload.setAmendment.identities) !== JSON.stringify(saved.setAmendment.identities)) {
        message.success('已保存套件订正，请核对系统记录的关联藏品原身份后再提交');
        refresh();
        return;
      }
    }
    if (submit) await changeApi.submit(id);
    if (!current(version, 'edit')) return;
    message.success(submit ? '已提交信息变更审批' : '变更草稿已保存');
    refresh();
    if (submit) clearView();
  } catch (error) {
    failure(version, error, false);
  } finally {
    if (current(version)) saving.value = false;
  }
}
async function loadDetail(id: string, version: number, source?: CollectionChangeVo) {
  const [value, archive, links]: any[] = await Promise.all([
    source ? Promise.resolve(source) : readRecord(id),
    changeApi.archive(id, viewAbort?.signal),
    changeApi.corrections(id),
  ]);
  if (!current(version)) return;
  const checked = record(value, id);
  if (!Array.isArray(archive?.data) || !Array.isArray(links?.data?.following))
    throw new Error('档案或补正关联资料不完整，请重新读取');
  if (
    archive.data.some(
      (item: any) =>
        !item ||
        typeof item.id !== 'string' ||
        typeof item.phase !== 'string' ||
        (item.bizId && item.bizId !== id) ||
        (item.bizType && item.bizType !== 'COLLECTION_CHANGE'),
    )
  )
    throw new Error('档案所属申请不一致，请核对原件');
  detail.value = checked;
  archiveRows.value = archive.data;
  correctionLinks.value = links.data;
}
async function openDetail(row: CollectionChangeVo) {
  const version = begin('detail');
  try {
    await loadDetail(requireId(row.id), version);
  } catch (error) {
    failure(version, error);
  } finally {
    if (current(version)) loading.value = false;
  }
}
function openRelated(row: CollectionChangeVo) {
  if (row.flowStatus === 'DRAFT' && !row.flowInstanceId && !row.appliedAt) void openEdit(row);
  else void openDetail(row);
}
async function openCorrection(row: CollectionChangeVo) {
  const version = begin('correction');
  try {
    const source = await readRecord(requireId(row.id));
    if (!current(version, 'correction')) return;
    if (!source.flowInstanceId || source.appliedAt || !['REJECT', 'CANCEL', 'VOID'].includes(source.flowStatus || ''))
      throw new Error('原申请尚未结束或已生效，请先核对原业务');
    correctionTarget.value = source;
    Object.assign(correctionInput, {
      requestKey: crypto.randomUUID(),
      expectedInstanceId: source.flowInstanceId,
      reason: '',
      evidenceFileIds: '',
    });
  } catch (error) {
    failure(version, error);
  } finally {
    if (current(version)) loading.value = false;
  }
}
async function createCorrection() {
  if (activeView !== 'correction' || loading.value || correcting.value) return;
  const source = correctionTarget.value;
  if (!source?.id || !correctionInput.reason.trim() || !correctionInput.evidenceFileIds) {
    message.warning('请填写补正原因并上传本次依据');
    return;
  }
  const version = viewRevision.value,
    id = source.id,
    payload = { ...correctionInput };
  correcting.value = true;
  try {
    const resp: any = await changeApi.createCorrection(id, payload);
    if (!current(version, 'correction') || correctionInput.requestKey !== payload.requestKey) return;
    const created = requireId(resp?.data);
    if (created === id) throw new Error('补正必须另建草稿，请重新核对原申请');
    refresh();
    message.success('已建立补正草稿，请核对材料后重新提交审批');
    await openEdit({ id: created });
  } catch (error) {
    failure(version, error, false);
  } finally {
    if (current(version)) correcting.value = false;
  }
}
function openFlow(row: CollectionChangeVo) {
  clearView();
  flowRef.value?.open(row.id, `信息变更：${row.collectionName || ''}`);
}
function openArchivePacket(row: CollectionChangeVo) {
  if (!canManageArchive.value || !row.orgId || !row.collectionId) return;
  archivePacketRef.value?.open({ orgId: row.orgId, collectionId: row.collectionId });
}
async function openExecute(row: CollectionChangeVo) {
  const version = begin('execute');
  try {
    const source = await readRecord(requireId(row.id));
    if (!current(version, 'execute')) return;
    if (source.flowStatus !== 'PASS' || !source.flowInstanceId || source.appliedAt)
      throw new Error('仅已批准且尚未生效的申请可办理签署生效，请重新核对');
    const orgId = requireId(source.orgId);
    const response: any = await orgApi.paperBooks(orgId);
    if (!current(version, 'execute')) return;
    if (!Array.isArray(response?.data)) throw new Error('未取得本馆纸质总账登记资料，请重新读取');
    const books = response.data as PaperBook[];
    if (books.some((book) => !book || !book.id || !book.bookNo || !Number.isInteger(book.book?.pageCount) || book.book.pageCount < 1))
      throw new Error('纸质总账登记资料不完整，请先核对账册原件');
    if (!books.length) throw new Error('本馆尚未登记纸质总账及保管原件，请先完成账册登记');
    paperBooks.value = books;
    executeTarget.value = source;
  } catch (error) {
    failure(version, error);
  } finally {
    if (current(version)) loading.value = false;
  }
}
function execute() {
  if (activeView !== 'execute' || loading.value || saving.value || !executeTarget.value?.id) return;
  const book = selectedPaperBook.value;
  if (!book || !Number.isInteger(execution.paperLedgerPageNo) || execution.paperLedgerPageNo! < 1 || execution.paperLedgerPageNo! > book.book.pageCount ||
      !execution.paperUpdatedAt || !execution.signedChangeFileIds) {
    message.warning('请选择已登记纸账、填写有效页码与订正日期，并上传已签署的变更表');
    return;
  }
  const version = viewRevision.value,
    id = executeTarget.value.id,
    payload = { ...execution, paperLedgerBookId: book.id, paperLedgerPageNo: execution.paperLedgerPageNo!, paperUpdatedAt: execution.paperUpdatedAt };
  let confirmed = false;
  dialog.warning({
    title: '确认使信息变更生效',
    content: `确认已核对“${executeTarget.value.collectionName}”的变更内容、${book.bookNo}第${payload.paperLedgerPageNo}页和签署材料？`,
    positiveText: '确认生效',
    negativeText: '返回核对',
    onPositiveClick: async () => {
      if (!current(version, 'execute') || saving.value || confirmed) return;
      confirmed = true;
      saving.value = true;
      try {
        await changeApi.execute(id, payload);
        if (!current(version, 'execute')) return;
        message.success('信息变更已生效；请核对藏品档案目录及相关公开资料');
        refresh();
        clearView();
      } catch (error) {
        failure(version, error, false);
      } finally {
        if (current(version)) saving.value = false;
      }
    },
  });
}
function refresh() {
  tableRef.value?.handleRefresh?.();
}
watch(
  () => [route.query.changeId, route.query.collectionId, route.query.setAmendment],
  async ([changeId, collectionId, setAmendment]) => {
    if (typeof changeId !== 'string') {
      if (typeof collectionId === 'string') await selectCollection(collectionId, setAmendment === '1');
      else clearView();
      return;
    }
    const version = begin('route');
    try {
      const source = await readRecord(requireId(changeId));
      if (!current(version, 'route')) return;
      if (source.flowStatus === 'DRAFT' && !source.flowInstanceId && !source.appliedAt) {
        activeView = 'edit';
        if (source.correction) await readCorrectionArchives(source.id!, version);
        if (!current(version, 'edit')) return;
        form.value = source;
        editVisible.value = true;
      } else {
        activeView = 'detail';
        detailVisible.value = true;
        await loadDetail(changeId, version, source);
      }
    } catch (error) {
      failure(version, error);
    } finally {
      if (current(version)) loading.value = false;
    }
  },
  { immediate: true },
);
</script>
