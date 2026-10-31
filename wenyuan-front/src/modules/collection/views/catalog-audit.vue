<template>
  <HPageDataTable auto-scroll-x
    class="collection-data-page"
    :title="typeof $route?.query?.title === 'string' && $route.query.title.trim() ? $route.query.title : '编目审核'"
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
      <NFormItem label="审核状态">
        <HDictSelect v-model:value="queryParams.status" type="col_catalog_status" clearable placeholder="状态" />
      </NFormItem>
      <NFormItem label="藏品级别">
        <HDictSelect v-model:value="queryParams.level" type="col_relic_level" clearable placeholder="级别" />
      </NFormItem>
      <NFormItem label="审核范围">
        <NCheckbox v-model:checked="queryParams.auditedByMe" @update:checked="onMineAuditChanged">仅本人审核</NCheckbox>
      </NFormItem>
    </template>

    <template #toolbar-extra>
      <CollectionFieldLabel label="审核规则" help="审核以提交时的版本快照为准，驳回意见会回到编目录入页面供修改重提。" />
    </template>

    <HPageForm content-only :enable-cancel="false" v-model:show="detailVisible" title="编目审核详情">
      <template v-if="detailRow">
        <NAlert
          :type="detailRow.completeness.complete ? 'success' : 'warning'"
          :title="detailRow.completeness.complete ? '必填项完整' : '编目信息不完整'"
          style="margin-bottom: 14px"
        >
          {{ detailRow.completeness.complete ? '可以进入审核决定。' : `缺少：${detailRow.completeness.missingFields.join('、')}` }}
        </NAlert>
        <CatalogDetail :catalog="detailRow" />
        <NAlert v-if="needsReceipt(detailRow)" type="warning" style="margin-top: 14px">尚未关联真实逐件核收。请完成入馆核收，并在生成待登记时关联已有台账，再办理审核通过。</NAlert>
      </template>
    </HPageForm>

    <NModal v-model:show="reviewVisible" preset="card" :title="reviewTitle" style="width: min(1000px, 94vw)">
      <NDescriptions v-if="reviewTarget" :column="2" bordered size="small" style="margin-bottom: 14px">
        <NDescriptionsItem label="收藏单位">{{ reviewTarget.orgName }}（{{ reviewTarget.orgId }}）</NDescriptionsItem>
        <NDescriptionsItem label="总登记号">{{ reviewTarget.totalRegNo }}</NDescriptionsItem>
        <NDescriptionsItem label="正式定名">{{ catalogApi.displayName(reviewTarget) }}</NDescriptionsItem>
        <NDescriptionsItem label="提交版本">第 {{ reviewTarget.submittedVersion || reviewTarget.version }} 版</NDescriptionsItem>
        <NDescriptionsItem label="完整度">{{ reviewTarget.completeness.percent }}%</NDescriptionsItem>
      </NDescriptions>
      <div v-if="reviewTarget" style="max-height: 45vh; overflow: auto; margin-bottom: 16px"><CatalogDetail :catalog="reviewTarget" /></div>
      <NForm :model="reviewForm" label-placement="top">
        <NFormItem label="审核意见" :required="reviewForm.status === 'REJECTED'">
          <NInput
            v-model:value="reviewForm.opinion"
            :disabled="reviewing"
            type="textarea"
            :rows="5"
            :placeholder="reviewForm.status === 'REJECTED' ? '请逐项说明需要修改的字段和原因' : '可填写通过意见'"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="reviewVisible = false">取消</NButton>
          <NButton :type="reviewForm.status === 'APPROVED' ? 'primary' : 'error'" :loading="reviewing" :disabled="reviewing || !reviewTarget" @click="submitReview">
            {{ reviewForm.status === 'APPROVED' ? '确认通过' : '确认驳回' }}
          </NButton>
        </NSpace>
      </template>
    </NModal>
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import '../styles/collection-page.css';
import { computed, h, reactive, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readCatalog, catalogResult } from '../utils/catalogResponses';
import { NButton, NProgress, NSpace, useMessage } from 'naive-ui';
import { formatDateTime } from '@/utils';
import { catalogApi } from '../api/catalog';
import CatalogDetail from '../components/CatalogDetail.vue';
import type { CatalogStatus, CollectionCatalogVo } from '../typings/collection';

const tableRef = ref<any>(null);
const message = useMessage();
const queryParams = reactive({
  keyword: null as string | null,
  status: 'SUBMITTED' as CatalogStatus | null,
  level: null as string | null,
  auditedByMe: false,
});
function onMineAuditChanged(checked: boolean) {
  if (checked) queryParams.status = null;
}
const detailVisible = ref(false);
const detailRow = ref<CollectionCatalogVo | null>(null);
const reviewVisible = ref(false);
const reviewing = ref(false);
const reviewTarget = ref<CollectionCatalogVo | null>(null);
const reviewForm = reactive<{ status: 'APPROVED' | 'REJECTED'; opinion: string }>({ status: 'APPROVED', opinion: '' });
const reviewTitle = computed(() => reviewForm.status === 'APPROVED' ? '编目审核通过' : '编目审核驳回');

const columns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150, fixed: 'left' as const, columnType: 'title' },
  { ellipsis: { tooltip: true }, resizable: true, width: 190, title: '正式定名', key: 'formalName', minWidth: 190, render: (row: CollectionCatalogVo) => catalogApi.displayName(row) },
  { resizable: true, title: '文物分类', key: 'categoryName', width: 140, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '年代', key: 'era', width: 110, render: (row: CollectionCatalogVo) => row.content.era || '--' },
  { ellipsis: { tooltip: true }, resizable: true, title: '质地', key: 'texture', width: 90, render: (row: CollectionCatalogVo) => row.content.texture || '--' },
  {
    title: '完整度',
    key: 'completeness',
    width: 145,
    render: (row: CollectionCatalogVo) =>
      h(NProgress, {
        type: 'line',
        percentage: row.completeness?.percent || 0,
        status: row.completeness?.complete ? 'success' : 'warning',
        height: 8,
        indicatorPlacement: 'inside',
      }),
  },
  { title: '提交版本', key: 'submittedVersion', width: 92, render: (row: CollectionCatalogVo) => `V${row.submittedVersion || row.version || 0}` },
  { title: '提交时间', key: 'submittedAt', width: 165, render: (row: CollectionCatalogVo) => formatDateTime(row.submittedAt) },
  { ellipsis: { tooltip: true }, resizable: true, title: '审核状态', key: 'status', width: 110, dictType: 'col_catalog_status' },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 190,
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
            disabled: !isSubmitted(row) || !row.canReview || !row.completeness?.complete || needsReceipt(row),
            onClick: () => openReview(row, 'APPROVED'),
          },
          () => '通过',
        ),
        h(
          NButton,
          { text: true, type: 'error', size: 'small', disabled: !isSubmitted(row) || !row.canReview, onClick: () => openReview(row, 'REJECTED') },
          () => '驳回',
        ),
      ]),
  },
];

function isSubmitted(row: CollectionCatalogVo) {
  return row.status === 'SUBMITTED' || row.status === 'PENDING';
}
function needsReceipt(row: CollectionCatalogVo) {
  return row.receiptLinked === false && !row.historicalMigration && !row.migrationVerificationRequired;
}

const session = useCatalogOperation(clearViews);
const pageActive = session.active;
function clearViews() {
  detailVisible.value = false; detailRow.value = null; reviewVisible.value = false; reviewTarget.value = null;
  reviewing.value = false; reviewForm.opinion = '';
}
watch(queryParams, () => session.reset(), { deep: true, flush: 'sync' });
watch(detailVisible, show => { if (!show) session.reset(); }, { flush: 'sync' });
watch(reviewVisible, show => { if (!show) session.reset(); }, { flush: 'sync' });
async function openDetail(row: CollectionCatalogVo) {
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
async function openReview(row: CollectionCatalogVo, status: 'APPROVED' | 'REJECTED') {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); reviewVisible.value = true; reviewForm.status = status;
  const ticket = session.begin('review-load'); if (ticket === null) return;
  try {
    const response = await catalogApi.get(collectionId);
    if (!session.current(ticket) || !reviewVisible.value) return;
    const target = readCatalog(response, collectionId);
    if (!target.canReview || !isSubmitted(target) || (status === 'APPROVED' && (!target.completeness.complete || needsReceipt(target)))) throw new Error('当前编目不能执行本次审核，请核对职责、提交状态及真实核收关联');
    reviewTarget.value = target;
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '审核依据读取失败'); }
  } finally { session.finish(ticket); }
}
async function submitReview() {
  if (!pageActive.value || !reviewVisible.value || !reviewTarget.value || reviewing.value || session.busy.value) return;
  const target = cloneDeep(reviewTarget.value), status = reviewForm.status, opinion = reviewForm.opinion.trim() || undefined;
  if (status === 'REJECTED' && !opinion) { message.warning('驳回时必须填写审核意见'); return; }
  const ticket = session.begin('review'); if (ticket === null) return;
  reviewing.value = true;
  try {
    const response = await catalogApi.review(target.collectionId!, status, opinion, target.stateHash!);
    if (!session.current(ticket) || !reviewVisible.value) return;
    catalogResult(response, target, status, target.version!);
    reviewVisible.value = false; message.success(status === 'APPROVED' ? '编目审核已通过' : '编目已驳回修改');
    tableRef.value?.handleRefresh?.();
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '审核失败，请核对实际结果'); }
  } finally { if (session.current(ticket)) { reviewing.value = false; session.finish(ticket); } }
}

</script>
