<template>
  <HPageDataTable auto-scroll-x
    class="collection-data-page"
    :title="typeof $route?.query?.title === 'string' && $route.query.title.trim() ? $route.query.title : '编目录入'"
    v-if="pageActive"
    ref="tableRef"
    :columns="columns"
    :fetch-data="catalogApi.page"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    row-key-field="collectionId"
    @rowView="openDetail"
  >
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="名称 / 登记号 / 分类号" />
      </NFormItem>
      <NFormItem label="编目状态">
        <HDictSelect v-model:value="queryParams.status" type="col_catalog_status" clearable placeholder="全部状态" />
      </NFormItem>
      <NFormItem label="藏品级别">
        <HDictSelect v-model:value="queryParams.level" type="col_relic_level" clearable placeholder="全部级别" />
      </NFormItem>
    </template>

    <template #toolbar-extra>
      <CollectionFieldLabel label="编目流程" help="登记入账后自动形成编目草稿；填写完整并提交后进入“编目审核”。" />
    </template>

    <CatalogFormModal ref="formRef" @saved="onFormSaved" />

    <HPageForm content-only :enable-cancel="false" v-model:show="detailVisible" title="编目详情">
      <CatalogDetail v-if="detailRow" :catalog="detailRow" />
    </HPageForm>

    <HPageForm content-only :enable-cancel="false" v-model:show="historyVisible" title="版本记录">
      <NTimeline v-if="versions.length">
        <NTimelineItem
          v-for="item in versions"
          :key="item.id || item.version"
          :type="versionTone(item.status)"
          :title="`第 ${item.version} 版 · ${statusLabel(item.status)}`"
          :time="formatDateTime(item.submittedAt)"
        >
          <NImage
            v-if="item.content?.coverImage"
            :src="fileUrl(item.content.coverImage)"
            :alt="`第 ${item.version} 版封面影像`"
            :width="96"
          />
          <div>提交人：{{ item.submittedBy || '--' }}</div>
          <div v-if="item.auditTime">审核：{{ item.auditBy || '--' }} · {{ formatDateTime(item.auditTime) }}</div>
          <div v-if="item.auditOpinion">意见：{{ item.auditOpinion }}</div>
        </NTimelineItem>
      </NTimeline>
      <NEmpty v-else description="尚未提交过版本" />
    </HPageForm>
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import '../styles/collection-page.css';
import { useCollectionSearchRecord } from '../composables/useCollectionSearchRecord';
import { h, reactive, ref, watch } from 'vue';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readCatalog, readCatalogVersions, catalogResult } from '../utils/catalogResponses';
import { useRouter } from 'vue-router';
import { NButton, NProgress, NSpace, useDialog, useMessage } from 'naive-ui';
import { fileUrl, formatDateTime } from '@/utils';
import { catalogApi } from '../api/catalog';
import CatalogDetail from '../components/CatalogDetail.vue';
import CatalogFormModal from '../components/CatalogFormModal.vue';
import type { CatalogStatus, CollectionCatalogVersionVo, CollectionCatalogVo } from '../typings/collection';

const tableRef = ref<any>(null);
const formRef = ref<any>(null);
const message = useMessage();
const dialog = useDialog();
const router = useRouter();
const queryParams = reactive({
  keyword: null as string | null,
  status: null as CatalogStatus | null,
  level: null as string | null,
});
const detailVisible = ref(false);
const detailRow = ref<CollectionCatalogVo | null>(null);
const historyVisible = ref(false);
const versions = ref<CollectionCatalogVersionVo[]>([]);

const columns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150, fixed: 'left' as const, columnType: 'title' },
  { ellipsis: { tooltip: true }, resizable: true, width: 190,
    title: '正式定名',
    key: 'formalName',
    minWidth: 190,
    render: (row: CollectionCatalogVo) => catalogApi.displayName(row),
  },
  { resizable: true, title: '文物分类', key: 'categoryName', width: 140, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '年代', key: 'era', width: 110, render: (row: CollectionCatalogVo) => row.content.era || '--' },
  { ellipsis: { tooltip: true }, resizable: true, title: '质地', key: 'texture', width: 90, render: (row: CollectionCatalogVo) => row.content.texture || '--' },
  { ellipsis: { tooltip: true }, resizable: true, title: '级别', key: 'level', width: 90, render: (row: CollectionCatalogVo) => row.content.level || '--', dictType: 'col_relic_level' },
  {
    title: '完整度',
    key: 'completeness',
    width: 150,
    render: (row: CollectionCatalogVo) =>
      h(NProgress, {
        type: 'line',
        percentage: row.completeness?.percent || 0,
        status: row.completeness?.complete ? 'success' : 'warning',
        height: 8,
        indicatorPlacement: 'inside',
      }),
  },
  {
    title: '版本',
    key: 'version',
    width: 74,
    render: (row: CollectionCatalogVo) => `V${row.version || 0}`,
  },
  { ellipsis: { tooltip: true }, resizable: true, title: '编目状态', key: 'status', width: 110, dictType: 'col_catalog_status' },
  { resizable: true, width: 190,
    title: '待补字段',
    key: 'missingFields',
    minWidth: 190,
    ellipsis: { tooltip: true },
    render: (row: CollectionCatalogVo) => row.completeness?.missingFields?.join('、') || '已完整',
  },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 250,
    fixed: 'right' as const,
    render: (row: CollectionCatalogVo) =>
      h(NSpace, { size: 10, wrap: false }, () => [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openDetail(row) }, () => '详情'),
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            size: 'small',
            disabled: !isEditable(row.status) || !row.canEdit,
            onClick: () => openEdit(row),
          },
          () => row.status === 'REJECTED' ? '修改重提' : '录入',
        ),
        h(
          NButton,
          {
            text: true,
            type: 'primary',
            size: 'small',
            disabled: !isEditable(row.status) || !row.canEdit || !row.completeness?.complete || row.migrationVerificationRequired,
            onClick: () => confirmSubmit(row),
          },
          () => '提交',
        ),
        row.migrationVerificationRequired ? h(NButton, { text: true, type: 'warning', size: 'small', onClick: () => router.push({ path: '/collection/ledger', query: { collectionId: row.collectionId } }) }, () => '核实登记依据') : null,
        row.status === 'APPROVED'
          ? h(NButton, { text: true, type: 'warning', size: 'small', onClick: () => confirmRevision(row) }, () => '修订')
          : null,
        h(NButton, { text: true, size: 'small', onClick: () => openHistory(row) }, () => '版本'),
      ]),
  },
];

function isEditable(status?: CatalogStatus) {
  return status === 'DRAFT' || status === 'REJECTED';
}

let confirmationDialog: ReturnType<typeof dialog.warning> | null = null;
const session = useCatalogOperation(clearViews);
const pageActive = session.active;
function clearViews() {
  detailVisible.value = false; detailRow.value = null; historyVisible.value = false; versions.value = [];
  formRef.value?.close?.();
  const previous = confirmationDialog; confirmationDialog = null; previous?.destroy();
}
watch(queryParams, () => session.reset(), { deep: true, flush: 'sync' });
watch(detailVisible, show => { if (!show) session.reset(); }, { flush: 'sync' });
watch(historyVisible, show => { if (!show) session.reset(); }, { flush: 'sync' });
async function openDetail(row: Pick<CollectionCatalogVo, 'collectionId'>) {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); detailVisible.value = true;
  const ticket = session.begin('detail'); if (ticket === null) return;
  try {
    const response = await catalogApi.get(collectionId);
    if (session.current(ticket) && detailVisible.value) detailRow.value = readCatalog(response, collectionId);
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '编目详情读取失败'); }
  } finally { session.finish(ticket); }
}
async function openEdit(row: CollectionCatalogVo) {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); const ticket = session.begin('edit'); if (ticket === null) return;
  try {
    const response = await catalogApi.get(collectionId);
    if (!session.current(ticket)) return;
    const current = readCatalog(response, collectionId);
    if (!current.canEdit || !isEditable(current.status)) throw new Error('当前编目不可编辑，请重新核对资格和状态');
    await formRef.value?.open(current);
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '编目读取失败'); }
  } finally { session.finish(ticket); }
}
async function confirmSubmit(row: CollectionCatalogVo) {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); const ticket = session.begin('confirm'); if (ticket === null) return;
  try {
    const response = await catalogApi.get(collectionId);
    if (!session.current(ticket)) return;
    const target = readCatalog(response, collectionId);
    if (!target.canEdit || !isEditable(target.status) || !target.completeness.complete) throw new Error('当前编目不具备提交条件，请重新核对');
    let posting = false;
    const cancel = () => { if (session.current(ticket)) session.reset(); };
    confirmationDialog = dialog.warning({
      title: '核对本次提交的编目', positiveText: '确认提交', negativeText: '取消',
      showIcon: false,
      style: { width: 'min(1120px, calc(100vw - 32px))', maxWidth: 'calc(100vw - 32px)' },
      contentStyle: { maxHeight: 'calc(100dvh - 180px)', overflowY: 'auto', overflowX: 'hidden', paddingRight: '8px', overscrollBehavior: 'contain' },
      content: () => h('div', [h('p', `${target.orgName}（${target.orgId}） · ${target.totalRegNo || target.collectionId} · 第 ${target.version! + 1} 版`), h(CatalogDetail, { catalog: target })]),
      onNegativeClick: cancel, onClose: cancel, onMaskClick: cancel,
      onPositiveClick: async () => {
        if (!session.current(ticket) || posting) return false;
        posting = true; session.operation.value = 'submit';
        try {
          const response = await catalogApi.submit(target.collectionId!, target.stateHash!);
          if (!session.current(ticket)) return false;
          catalogResult(response, target, 'SUBMITTED', target.version! + 1);
          message.success('已提交编目审核'); refresh();
        } catch (error: any) {
          if (session.current(ticket)) { session.reset(); message.error(error?.message || '提交失败，请核对实际结果'); }
          return false;
        } finally { if (session.current(ticket)) { posting = false; session.finish(ticket); } }
      },
    });
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '提交依据读取失败'); }
  } finally { session.finish(ticket); }
}
function confirmRevision(row: CollectionCatalogVo) {
  if (row.collectionId && pageActive.value) { session.reset(); void router.push({ path: '/collection/change', query: { collectionId: row.collectionId } }); }
}
async function openHistory(row: CollectionCatalogVo) {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); historyVisible.value = true;
  const ticket = session.begin('history'); if (ticket === null) return;
  try {
    const response = await catalogApi.versions(collectionId);
    if (session.current(ticket) && historyVisible.value) versions.value = readCatalogVersions(response, collectionId);
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '编目版本读取失败'); }
  } finally { session.finish(ticket); }
}

function statusLabel(status?: CatalogStatus) {
  const labels: Record<CatalogStatus, string> = {
    DRAFT: '草稿',
    SUBMITTED: '已提交',
    PENDING: '待审核（旧）',
    APPROVED: '已通过',
    REJECTED: '已驳回',
  };
  return status ? labels[status] : '--';
}

function versionTone(status?: CatalogStatus): 'default' | 'info' | 'success' | 'warning' | 'error' {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'error';
  if (status === 'SUBMITTED' || status === 'PENDING') return 'info';
  return 'default';
}

function refresh() { if (pageActive.value) { session.reset(); tableRef.value?.handleRefresh?.(); } }
function onFormSaved() { if (pageActive.value) tableRef.value?.handleRefresh?.(); }
useCollectionSearchRecord('/collection/catalog-entry', 'CATALOG', id => openDetail({ collectionId: id }));
</script>
