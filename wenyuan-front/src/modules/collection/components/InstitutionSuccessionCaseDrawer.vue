<template>
  <HPageForm content-only :enable-cancel="false" v-model:show="show" title="终止、合并全馆统筹清册">
    <NSpace vertical :size="16">
      <CollectionArchiveIssues :data="record" />
      <NAlert type="info"
        >先依据实际机构决定建案，再逐库、逐项核对在库、在借、修复、暂存及未登记对象。清册条数包括业务记录和关联辅助账，不代表实物总件数。法人手续、行政指定和实际交接应按原件另行办理。</NAlert
      >
      <NAlert v-if="error" type="error">{{ error }} <NButton @click="retry">重新读取</NButton></NAlert>
      <NSpace>
        <NButton
          v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'"
          :disabled="!canCreate || loading"
          @click="edit('case')"
          >建立全馆统筹案卷</NButton
        >
        <NButton :disabled="!contextReady || loading" @click="retry">刷新范围与办理状态</NButton>
      </NSpace>
      <NSpin :show="loading">
        <NDataTable :columns="columns" :data="cases" :row-key="(r: any) => r.id" :scroll-x="690" />
        <NPagination :page="page" :page-count="Math.max(1, Math.ceil(total / 20))" @update:page="changePage" />
      </NSpin>
      <NSpin :show="detailLoading">
        <NCard v-if="record" :title="`${record.case.sourceMuseumName} · ${record.case.eventDocumentNo}`">
          <NSpace vertical :size="14">
            <NText
              >{{ institutionScopeStates[record.state] || record.state }} · 范围 {{ record.total }} 条 · 待处理
              {{ record.pending }} 条 · 核查逾期 {{ record.overdue }} 条</NText
            >
            <NAlert v-if="!record.scopeFresh" type="warning"
              >当前系统范围尚未留档或已变化。请核查新增、状态改变及不再出现的来源，再登记本次实际清点范围。</NAlert
            >
            <NAlert v-if="!canOperate" type="info">当前仅可查阅本案，办理须核对本馆实际职责。</NAlert>
            <NSpace v-if="canOperate" v-permission="'COLLECTION_ADMIN,COLLECTION_STORAGE'">
              <NButton @click="edit('scope')">记录本次全馆清点范围</NButton>
              <NButton @click="edit('unrecorded')">登记未入系统的清点对象</NButton>
              <NButton :disabled="!reviewReady" @click="edit('keeper-review')">保管负责人本人核对</NButton>
              <NButton :disabled="!reviewReady || !record.keeperReview" @click="edit('representative-review')"
                >依法授权人员本人复核</NButton
              >
            </NSpace>
            <NDataTable :columns="itemColumns" :data="record.items" :row-key="(r: any) => r.key" :scroll-x="1240" />
            <NPagination
              :page="itemPage"
              :page-count="Math.max(1, Math.ceil(record.total / 20))"
              @update:page="changeItemPage"
            />
            <NAlert type="info"
              >完成依据须关联同一对象真实执行的业务记录。辅助账和现场发现对象先核实分类及身份，再由保管和依法负责人员确认。分批交接、来源关联或存在差异时，保留全部原件及未结责任；文物、正式藏品按适用手续办理。</NAlert
            >
            <NCollapse>
              <NCollapseItem title="机构决定与依法授权原件" name="case"
                ><CollectionEvidenceReadView :fields="institutionScopeFields('case', context)" :data="record.case" :archive-files="record.archiveFiles?.[record.id]" :original-url="originalUrl(record.id, record.id)"
              /></NCollapseItem>
              <NCollapseItem
                v-for="r in record.records"
                :key="r.id"
                :name="r.id"
                :title="`${institutionScopeTitles[phase(r.kind)] || r.kind} · ${formatDateTime(r.recordedAt)}`"
              >
                <NText v-if="(r.content.request || r.content).itemKey"
                  >对应清册对象：{{ (r.content.request || r.content).itemKey }}</NText
                >
                <NDataTable
                  v-if="r.kind === 'SCOPE'"
                  :scroll-x="590"
                  :columns="historyColumns"
                  :data="r.content.items || []"
                  :row-key="(v: any) => v.key"
                  :pagination="{ pageSize: 20 }"
                />
                <CollectionEvidenceReadView
                  :fields="
                    institutionScopeFields(
                      phase(r.kind),
                      { ...context, linkableItems: record.linkableItems },
                      r.content.request?.route,
                    )
                  "
                  :data="r.content.request || r.content"
                  :archive-files="record.archiveFiles?.[r.id]" :original-url="originalUrl(record.id, r.id)"
                />
              </NCollapseItem>
            </NCollapse>
          </NSpace>
        </NCard>
      </NSpin>
    </NSpace>
  </HPageForm>
  <HPageForm content-only :enable-cancel="false" v-model:show="sourceShow" :title="sourceTitle">
    <NSpace v-if="sourceItem" vertical :size="16">
      <NText>来源：{{ institutionSourceNames[sourceItem.kind] || sourceItem.kind }} · {{ sourceItem.id }}</NText>
      <NAlert v-if="sourceItem.currentPresent === false" type="warning"
        >该来源目前不再出现。原始记录和未结责任仍保留，须继续核实。</NAlert
      >
      <NCard title="所查看版本的来源记录"><InstitutionScopeSnapshotView :archive-files="sourceItem.archiveFiles" :snapshot="sourceItem.snapshot" :original-url="originalUrl(sourceItem.caseId, sourceItem.itemId || sourceItem.key)" /></NCard>
      <NCard v-if="sourceItem.baseline" title="首次纳入本案时的来源原记录"
        ><InstitutionScopeSnapshotView :archive-files="sourceItem.archiveFiles" :snapshot="sourceItem.baseline" :original-url="originalUrl(sourceItem.caseId, sourceItem.itemId || sourceItem.key)"
      /></NCard>
    </NSpace>
  </HPageForm>
  <CollectionEvidenceForm
    v-if="show"
    ref="formRef"
    :title="title"
    :fields="fields"
    :save="save"
    @saved="saved"
    @change="formChanged"
  >
    <template #context>
      <NSpace v-if="editorKind.startsWith('item-investigation')" vertical>
        <NAlert type="info"
          >按以下固定版本核对。保留原批次的不一致事实；调查中可以如实记录未知、不在场或不安全。确认处理结果须核实全部已交接对象、双方说明、权利及实际报告。新增批次或后续问题须重新核查。</NAlert
        >
        <NCollapse>
          <NCollapseItem v-if="editorBasis.proposal" title="原分类及承接方案" name="plan">
            <CollectionEvidenceReadView
              :fields="institutionScopeFields('item-proposal', {}, editorBasis.proposal.content.request.route)"
              :data="editorBasis.proposal.content.request"
              :archive-files="editorBasis.archiveFiles?.[editorBasis.proposal.id]" :original-url="originalUrl(editorBasis.caseId, editorBasis.proposal.id)"
            />
          </NCollapseItem>
          <NCollapseItem
            v-for="batch in editorBasis.batches"
            :key="batch.id"
            :name="batch.id"
            :title="`实际交接原件 · ${formatDateTime(batch.content.request.handedAt)}`"
          >
            <NText>交接批次编号：{{ batch.id }}</NText>
            <CollectionEvidenceReadView
              :fields="institutionScopeFields('item-handover')"
              :data="batch.content.request"
              :archive-files="editorBasis.archiveFiles?.[batch.id]" :original-url="originalUrl(editorBasis.caseId, batch.id)"
            />
          </NCollapseItem>
          <NCollapseItem
            v-for="follow in editorBasis.followUps"
            :key="follow.id"
            :name="follow.id"
            :title="`后续问题 · ${formatDateTime(follow.content.recordedAt)}`"
          >
            <CollectionEvidenceReadView :fields="institutionScopeFields('follow-up')" :data="follow.content" :archive-files="editorBasis.archiveFiles?.[follow.id]" :original-url="originalUrl(editorBasis.caseId, follow.id)" />
          </NCollapseItem>
          <NCollapseItem
            v-if="editorBasis.review?.investigation"
            title="最近一次调查处理原件（本次确认对象）"
            name="investigation"
          >
            <NText>调查原件编号：{{ editorBasis.review.investigationId }}</NText>
            <CollectionEvidenceReadView
              :fields="institutionScopeFields('item-investigation')"
              :data="editorBasis.review.investigation"
              :archive-files="editorBasis.archiveFiles?.[editorBasis.review.investigationId]" :original-url="originalUrl(editorBasis.caseId, editorBasis.review.investigationId)"
            />
          </NCollapseItem>
        </NCollapse>
      </NSpace>
    </template>
  </CollectionEvidenceForm>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import { computed, h, nextTick, onActivated, onDeactivated, onBeforeUnmount, ref, watch } from 'vue';
import { NButton, NSpace } from 'naive-ui';
import { cloneDeep } from 'lodash-es';
import { formatDateTime } from '@/utils';
import { institutionSuccessionApi as api } from '../api/institutionSuccession';
import {
  institutionScopeFields,
  institutionScopeTitles,
  institutionScopeStates,
  institutionSourceNames,
  institutionItemStates,
  institutionScopeValues,
  institutionReviewInitial,
} from '../utils/institutionScopeFields';
import type { EvidenceField } from '../utils/evidenceFields';
import {
  institutionInvestigationInitial,
  institutionInvestigationStates,
} from '../utils/institutionInvestigationFields';
import { institutionItemInitial, institutionItemPayload } from '../utils/institutionItemFields';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import InstitutionScopeSnapshotView from './InstitutionScopeSnapshotView.vue';
const sourceShow = ref(false),
  sourceItem = ref<any>(),
  sourceTitle = ref('清册对象来源');
function readItem(item: any) {
  if (!active.value || !show.value || !record.value) return;
  sourceItem.value = { ...cloneDeep(item), caseId: record.value.id, archiveFiles: record.value.archiveFiles?.[item.itemId || item.key] || [] };
  sourceTitle.value = `来源原记录 · ${record.value?.case.eventDocumentNo || ''} · ${item.snapshot.name || item.id}`;
  sourceShow.value = true;
}
const emit = defineEmits(['saved', 'transfer']);
const active = ref(true);
const show = ref(false),
  loading = ref(false),
  detailLoading = ref(false),
  contextReady = ref(false),
  context = ref<any>({}),
  cases = ref<any[]>([]),
  record = ref<any>(),
  page = ref(1),
  itemPage = ref(1),
  total = ref(0),
  error = ref(''),
  fields = ref<EvidenceField[]>([]),
  title = ref(''),
  formRef = ref<any>();
const save = ref<(v: Record<string, any>) => Promise<unknown>>(async () => false);
const canCreate = computed(
  () => contextReady.value && Array.isArray(context.value.orgs) && context.value.orgs.length > 0,
);
const canOperate = computed(() => contextReady.value && record.value?.canOperate === true);
let completion: { view: number; token: number; id: string } | undefined;
let editorTargetId = '';
const reviewReady = computed(() => record.value?.scopeFresh && record.value?.pending === 0);
let session = 0,
  listRequest = 0,
  detailRequest = 0,
  editRequest = 0,
  selected = '';
const phase = (kind: string) => kind.toLowerCase().replaceAll('_', '-');
const columns = [
  { title: '原收藏单位', key: 'museum', width: 220, minWidth: 150, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.case.sourceMuseumName },
  { title: '机构事项', key: 'event', width: 100, minWidth: 80, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => (r.case.eventKind === 'MERGER' ? '合并' : '终止') },
  { title: '实际决定文号', key: 'document', width: 220, minWidth: 150, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.case.eventDocumentNo },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: (r: any) => h(NButton, { text: true, onClick: () => select(r.id) }, () => '查看统筹清册'),
  },
];
function sourceAction(item: any) {
  const owner = record.value,
    view = session;
  return () => {
    if (active.value && show.value && view === session && record.value === owner) readItem(item);
  };
}
function transferAction(id: string) {
  const owner = record.value,
    view = session;
  return () => {
    if (active.value && show.value && view === session && record.value === owner) emit('transfer', id);
  };
}
const historyColumns = [
  {
    title: '当时来源',
    key: 'name',
    width: 260,
    minWidth: 180,
    resizable: true,
    ellipsis: { tooltip: true },
    render: (r: any) => `${institutionSourceNames[r.kind] || r.kind} · ${r.snapshot.name || r.id}`,
  },
  { title: '原编号', key: 'no', width: 180, minWidth: 130, resizable: true, ellipsis: { tooltip: true }, render: (r: any) => r.snapshot.recordNo || '未登记' },
  {
    title: '当时清点记录',
    key: 'read',
    width: 150,
    render: (r: any) => h(NButton, { text: true, onClick: sourceAction(r) }, () => '查看当时来源'),
  },
];
let editorContext: Record<string, any> = {};
const editorKind = ref('');
const editorBasis = ref<any>({ batches: [], followUps: [] });
const originalUrl = (caseId?: string, sourceId?: string) => (fileId: string) =>
  caseId && sourceId && fileId
    ? `/collection/institution-succession/${encodeURIComponent(caseId)}/original/${encodeURIComponent(sourceId)}?fileId=${encodeURIComponent(fileId)}`
    : '';
function itemPlan(item: any) {
  return record.value?.records
    ?.filter((r: any) => r.kind === 'ITEM_PROPOSAL' && r.content.request.itemKey === item.key)
    .at(-1);
}
function itemBatches(item: any) {
  return record.value?.records?.filter((r: any) => r.kind === 'ITEM_HANDOVER' && r.content.itemKey === item.key) || [];
}
function itemActions(item: any) {
  if (
    !item.itemId ||
    (!['AUXILIARY', 'UNRECORDED'].includes(item.kind) &&
      !(item.kind === 'COLLECTION' && item.snapshot.state === 'DEREGISTERED'))
  )
    return [];
  const plan = itemPlan(item),
    handed = itemBatches(item),
    disabled = !canOperate.value;
  const button = (kind: string, label: string, extra = false) =>
    h(NButton, { text: true, disabled: disabled || extra, onClick: () => edit(kind, item) }, () => label);
  return [
    button('item-proposal', plan ? '追加分类方案' : '分类办理', handed.length > 0),
    ...(plan
      ? [
          button('item-keeper', '保管核对方案', handed.length > 0),
          button('item-authorization', '依法确认方案', handed.length > 0),
          ...(plan.content.request.route !== 'RECORD_LINK' ? [button('item-handover', '登记交接批次')] : []),
        ]
      : []),
    ...(handed.length
      ? [
          button('item-investigation', '登记差异调查', !item.handoverReview?.basisDigest),
          ...(item.handoverReview?.investigationId
            ? [
                button(
                  'item-investigation-keeper',
                  '保管核对差异处理',
                  !['READY_FOR_REVIEW', 'KEEPER_CONFIRMED', 'CONFIRMED'].includes(item.handoverReview.state),
                ),
                button(
                  'item-investigation-authorization',
                  '依法复核差异处理',
                  !['KEEPER_CONFIRMED', 'CONFIRMED'].includes(item.handoverReview.state),
                ),
              ]
            : []),
        ]
      : []),
  ];
}
function formChanged(value: Record<string, any>) {
  if (editorKind.value === 'item-proposal')
    fields.value = institutionScopeFields(editorKind.value, editorContext, value.route);
}
const itemColumns = [
  {
    title: '来源及对象',
    key: 'name',
    minWidth: 160,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 220,
    render: (r: any) => `${institutionSourceNames[r.kind] || r.kind} · ${r.snapshot.name || r.id}`,
  },
  {
    title: '原编号 / 本条数量',
    key: 'number',
    minWidth: 150,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 180,
    render: (r: any) =>
      `${r.snapshot.recordNo || '未登记'} / ${r.snapshot.quantity ?? '待核'} ${r.snapshot.unit || ''}`,
  },
  {
    title: '状态及关联',
    key: 'sourceState',
    minWidth: 180,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 260,
    render: (r: any) =>
      [
        institutionItemStates[r.snapshot.state] || r.snapshot.state,
        r.snapshot.originCollectionId ? `关联藏品 ${r.snapshot.originCollectionId}` : '',
        !r.currentPresent ? '当前来源不再出现，保留核查责任' : '',
      ]
        .filter(Boolean)
        .join('；'),
  },
  {
    title: '核实及后续处理',
    key: 'result',
    minWidth: 220,
    resizable: true,
    ellipsis: { tooltip: true },
    width: 330,
    render: (r: any) =>
      r.error ||
      (r.resolved
        ? '已有实际完成依据'
        : r.followUp
          ? `${r.overdue ? '核查已逾期；' : ''}${r.followUp.issue}；${r.followUp.actualMeasures}；下次 ${formatDateTime(r.followUp.nextCheckAt)}`
          : r.handoverReview?.requiresInvestigation
            ? `${r.overdue ? '核查已逾期；' : ''}${institutionInvestigationStates[r.handoverReview.state] || r.handoverReview.state}；${r.handoverReview.issue || ''}${r.handoverReview.investigation?.remainingIssues || ''}${r.handoverReview.investigation?.nextCheckAt ? `；下次 ${formatDateTime(r.handoverReview.investigation.nextCheckAt)}` : ''}`
            : '待核实并逐项办理'),
  },
  {
    title: '操作',
    key: 'actions',
    width: 250,
    render: (r: any) =>
      h(
        NSpace,
        {},
        {
          default: () => [
            h(
              NButton,
              { text: true, disabled: !r.itemId || !canOperate.value, onClick: () => edit('follow-up', r) },
              () => '追加处理',
            ),
            h(
              NButton,
              {
                text: true,
                disabled:
                  !r.itemId || !canOperate.value || (['AUXILIARY', 'UNRECORDED'].includes(r.kind) && !itemPlan(r)),
                onClick: () => edit('resolution', r),
              },
              () => '核实完成依据',
            ),
            h(NButton, { text: true, onClick: sourceAction(r) }, () => '查看来源'),
            ...itemActions(r),
            ...(r.proof?.transferId || r.snapshot.transferId
              ? [
                  h(
                    NButton,
                    { text: true, onClick: transferAction(r.proof?.transferId || r.snapshot.transferId) },
                    () => '查看逐件承接',
                  ),
                ]
              : []),
          ],
        },
      ),
  },
];
async function reload() {
  const token = ++listRequest,
    view = session;
  loading.value = true;
  cases.value = [];
  total.value = 0;
  try {
    const r: any = await api.page(page.value);
    if (view !== session || token !== listRequest || !show.value) return;
    if (
      !Array.isArray(r?.data?.list) ||
      !Number.isInteger(Number(r.data.total)) ||
      Number(r.data.total) < r.data.list.length
    )
      throw new Error('案卷列表回执不完整，请重新读取');
    cases.value = r.data.list;
    total.value = Number(r.data.total);
  } catch (e) {
    if (view === session && token === listRequest)
      error.value = e instanceof Error ? e.message : '统筹案卷列表读取失败';
  } finally {
    if (view === session && token === listRequest) loading.value = false;
  }
}
async function select(id: string, newPage = 1) {
  if (editorTargetId === id) {
    ++editRequest;
    completion = undefined;
    formRef.value?.close?.();
  }
  selected = id;
  itemPage.value = newPage;
  const token = ++detailRequest,
    view = session;
  record.value = undefined;
  detailLoading.value = true;
  error.value = '';
  try {
    const r: any = await api.detail(id, newPage);
    if (view === session && token === detailRequest && show.value) {
      if (
        r?.data?.id !== id ||
        typeof r.data.orgId !== 'string' ||
        !r.data.orgId.trim() ||
        !r.data.case ||
        !Array.isArray(r.data.records)
      )
        throw new Error('案卷详情与当前对象不一致，请重新读取');
      record.value = r.data;
    }
  } catch (e) {
    if (view === session && token === detailRequest)
      error.value = e instanceof Error ? e.message : '统筹清册及原件读取失败';
  } finally {
    if (view === session && token === detailRequest) detailLoading.value = false;
  }
}
async function open(id?: string) {
  if (!active.value) return;
  invalidate();
  const view = session;
  show.value = true;
  selected = id || '';
  page.value = 1;
  itemPage.value = 1;
  total.value = 0;
  error.value = '';
  contextReady.value = false;
  context.value = {};
  loading.value = true;
  try {
    const r: any = await api.context();
    if (view !== session || !show.value) return;
    if (!Array.isArray(r?.data?.orgs)) throw new Error('办理范围回执不完整，请重新读取');
    context.value = r.data;
    contextReady.value = true;
    await reload();
    if (id && view === session && selected === id && show.value) await select(id);
  } catch (e) {
    if (view === session) error.value = e instanceof Error ? e.message : '原馆办理范围读取失败';
  } finally {
    if (view === session) loading.value = false;
  }
}
async function retry() {
  await open(selected || undefined);
}
function changePage(v: number) {
  page.value = v;
  void reload();
}
function changeItemPage(v: number) {
  if (selected) void select(selected, v);
}
async function edit(kind: string, item?: any) {
  if (
    !active.value ||
    !show.value ||
    loading.value ||
    detailLoading.value ||
    !contextReady.value ||
    (kind === 'case' ? !canCreate.value : !canOperate.value)
  )
    return;
  if (item && !record.value.items.includes(item)) return;
  const view = session,
    token = ++editRequest;
  const target = cloneDeep({
    id: record.value?.id || '',
    orgId: record.value?.orgId,
    allowedOrgs: cloneDeep(context.value.orgs || []),
    selected,
    kind,
    itemKey: item?.key,
    scanDigest: record.value?.scanDigest,
    scopeRecordId: record.value?.scopeRecordId,
    reviewDigest: record.value?.reviewDigest,
    proposal: item ? itemPlan(item) : undefined,
    batches: item ? itemBatches(item) : [],
    handoverReview: item?.handoverReview,
    followUps: (record.value?.records || []).filter(
      (r: any) => r.kind === 'FOLLOW_UP' && r.content.itemKey === item?.key,
    ),
  });
  title.value = `${institutionScopeTitles[kind]}${kind === 'case' ? '' : ` · ${record.value.case.eventDocumentNo}${item ? ` · ${item.snapshot.name || item.id}` : ''}`}`;
  if (kind !== 'case') title.value += ` · ${record.value.case.sourceMuseumName}（${target.orgId}）`;
  editorKind.value = kind;
  editorBasis.value = cloneDeep({
    archiveFiles: record.value.archiveFiles,
    caseId: target.id,
    proposal: target.proposal,
    batches: target.batches,
    review: target.handoverReview,
    followUps: target.followUps,
  });
  editorContext = cloneDeep({
    ...context.value,
    hasPrevious: !!target.proposal,
    hasPreviousInvestigation: !!target.handoverReview?.investigationId,
    linkableItems: (record.value?.linkableItems || [])
      .filter((r: any) => r.value !== item?.key)
      .map((r: any) => ({
        ...r,
        label: `${institutionSourceNames[r.kind] || r.kind} · ${r.label}${r.recordNo ? ` · ${r.recordNo}` : ''}`,
      })),
  });
  let initial: Record<string, any> =
    kind === 'case'
      ? { orgId: context.value.currentOrgId }
      : kind.endsWith('review')
        ? institutionReviewInitial(
            kind === 'representative-review' ? cloneDeep(record.value?.keeperReview) : undefined,
            context.value.currentUserId,
          )
        : { operatorUserId: context.value.currentUserId };
  if (kind.startsWith('item-'))
    initial = institutionItemInitial(
      kind,
      cloneDeep(target.proposal?.content.request),
      context.value.currentUserId,
      target.batches.flatMap((r: any) => r.content.request.pieces.map((p: any) => p.pieceNo.trim())),
    );
  if (kind.startsWith('item-investigation'))
    initial = institutionInvestigationInitial(kind, context.value.currentUserId, target.batches);
  if (kind === 'resolution' && target.proposal) {
    if (target.proposal.content.request.route === 'RECORD_LINK')
      initial = { ...initial, kind: 'LINKED_ITEM', businessId: target.proposal.id };
    else if (target.batches.length)
      initial = { ...initial, kind: 'OTHER_ITEM_HANDOVER', businessId: target.batches.at(-1).id };
  }
  fields.value = institutionScopeFields(kind, editorContext, initial.route);
  completion = undefined;
  editorTargetId = target.id;
  let inFlight = false,
    finished = false;
  const live = () =>
    active.value && view === session && token === editRequest && show.value && formRef.value?.isOpen?.() !== false;
  save.value = async (value) => {
    if (!live() || inFlight || finished) return false;
    const v = institutionItemPayload(kind, institutionScopeValues(cloneDeep(value)));
    if (kind === 'item-proposal' && target.proposal) v.previousProposalId = target.proposal.id;
    if (['item-keeper', 'item-authorization', 'item-handover'].includes(kind)) v.proposalRecordId = target.proposal?.id;
    if (kind === 'scope') v.scanDigest = target.scanDigest;
    if (kind === 'item-investigation') {
      v.handoverRecordId = target.handoverReview?.handoverRecordId;
      v.basisDigest = target.handoverReview?.basisDigest;
      if (target.handoverReview?.investigationId) v.previousInvestigationId = target.handoverReview.investigationId;
    } else if (kind.startsWith('item-investigation-')) v.investigationRecordId = target.handoverReview?.investigationId;
    if (
      target.itemKey &&
      !kind.startsWith('item-investigation') &&
      !['item-keeper', 'item-authorization', 'item-handover'].includes(kind)
    )
      v.itemKey = target.itemKey;
    if (kind.endsWith('review')) {
      v.scopeRecordId = target.scopeRecordId;
      v.reviewDigest = target.reviewDigest;
    }
    const orgId = kind === 'case' ? v.orgId : target.orgId;
    if (
      typeof orgId !== 'string' ||
      !orgId.trim() ||
      (kind === 'case' && !target.allowedOrgs.some((o: any) => o.value === orgId))
    )
      throw new Error('请重新核对本次办理的收藏单位');
    inFlight = true;
    try {
      const result: any = kind === 'case' ? await api.create(v, orgId) : await api.record(target.id, kind, v, orgId);
      if (!live()) return false;
      if (typeof result?.data !== 'string' || !result.data.trim()) {
        finished = true;
        throw new Error('办理回执不完整，请返回原案卷核实实际状态');
      }
      finished = true;
      if (kind === 'case' && selected === target.selected) selected = result.data;
      completion = { view, token, id: kind === 'case' ? result.data : target.id };
      emit('saved');
      return live();
    } catch (e) {
      if (!live()) return false;
      throw e;
    } finally {
      inFlight = false;
    }
  };
  await nextTick();
  if (view === session && token === editRequest && show.value) formRef.value?.open(initial);
}
async function saved() {
  const done = completion;
  completion = undefined;
  if (
    !done ||
    !active.value ||
    !show.value ||
    done.view !== session ||
    done.token !== editRequest ||
    done.id !== selected
  )
    return;
  await open(done.id);
}
function invalidate() {
  editorTargetId = '';
  completion = undefined;
  formRef.value?.close?.();
  contextReady.value = false;
  context.value = {};
  total.value = 0;
  editorKind.value = '';
  editorBasis.value = { batches: [], followUps: [] };
  editorContext = {};
  sourceShow.value = false;
  sourceItem.value = undefined;
  ++session;
  ++listRequest;
  ++detailRequest;
  ++editRequest;
  loading.value = false;
  detailLoading.value = false;
  record.value = undefined;
  cases.value = [];
}
watch(
  show,
  (v) => {
    if (!v) invalidate();
  },
  { flush: 'sync' },
);
onActivated(() => {
  active.value = true;
});
onDeactivated(() => {
  active.value = false;
  show.value = false;
  invalidate();
});
onBeforeUnmount(invalidate);
defineExpose({ open });
</script>
