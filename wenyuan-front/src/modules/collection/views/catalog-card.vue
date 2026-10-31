<template>
  <HPageDataTable auto-scroll-x
    class="collection-data-page"
    :title="typeof $route?.query?.title === 'string' && $route.query.title.trim() ? $route.query.title : '编目卡片'"
    v-if="pageActive"
    ref="tableRef"
    :columns="columns"
    :fetch-data="catalogApi.page"
    :params="queryParams"
    :is-add="false"
    :is-edit="false"
    :is-delete="false"
    row-key-field="collectionId"
    @rowView="openCard"
  >
    <template #queryForm>
      <NFormItem label="检索内容">
        <NInput v-model:value="queryParams.keyword" clearable placeholder="名称 / 登记号 / 分类号" />
      </NFormItem>
      <NFormItem label="藏品级别">
        <HDictSelect v-model:value="queryParams.level" type="col_relic_level" clearable placeholder="级别" />
      </NFormItem>
    </template>
    <template #toolbar-extra>
      <CollectionFieldLabel label="卡片版本" help="仅展示至少有一个审核通过版本的藏品；修订中的草稿不会改变已通过卡片。" />
    </template>

    <NModal
      v-model:show="previewVisible"
      preset="card"
      title="编目卡片预览"
      class="catalog-card-preview"
      :segmented="{ footer: true }"
      :header-style="{ flexShrink: 0 }"
      :content-style="{ minHeight: 0, overflow: 'auto', overscrollBehavior: 'contain' }"
      :footer-style="{ flexShrink: 0 }"
    >
      <div v-if="catalog" id="catalog-card-print" ref="paperRef" class="catalog-card-paper">
        <header class="catalog-card-header">
          <h1>藏品编目卡</h1>
          <p>审核通过版本 V{{ catalog.approvedVersion || catalog.submittedVersion || catalog.version }}</p>
        </header>
        <table class="catalog-card-table">
          <thead><tr><td colspan="5">总登记号：{{ value(catalog.totalRegNo) }}　审核版本：V{{ catalog.approvedVersion || catalog.version }}</td></tr></thead>
          <tbody>
            <tr>
              <th>总登记号</th>
              <td>{{ value(catalog.totalRegNo) }}</td>
              <th>登记号分号</th>
              <td>{{ value(content.registrationSubNo) }}</td>
              <td v-if="coverSrc" rowspan="6" class="cover-cell"><img :src="coverSrc" alt="藏品影像" /></td>
              <td v-else rowspan="6" class="cover-cell cover-empty">藏品影像</td>
            </tr>
            <tr><th>分类号</th><td>{{ value(content.classificationNo) }}</td><th>原藏号</th><td>{{ value(content.originalNo) }}</td></tr>
            <tr><th>总品名</th><td>{{ value(content.totalName) }}</td><th>分件名称</th><td>{{ value(content.componentName) }}</td></tr>
            <tr><th>正式定名</th><td colspan="3" class="emphasis">{{ value(content.formalName) }}</td></tr>
            <tr><th>文物分类</th><td>{{ value(catalog.categoryName || content.categoryId) }}</td><th>藏品级别</th><td><CollectionCatalogDictText type="col_relic_level" :value="content.level" /></td></tr>
            <tr><th>质地</th><td><CollectionCatalogDictText type="col_texture" :value="content.texture" /></td><th>色泽</th><td>{{ value(content.color) }}</td></tr>
            <tr>
              <th>年代</th>
              <td colspan="4">{{ eraText }}</td>
            </tr>
            <tr><th>地域国别</th><td colspan="2">{{ regionText }}</td><th>人文类型</th><td>{{ value(content.culture || content.cultureType) }}</td></tr>
            <tr><th>账面数量</th><td>{{ quantity(content.quantity, content.unit) }}</td><th>实际数量</th><td colspan="2">{{ quantity(content.actualQuantity, content.actualQuantityUnit) }}</td></tr>
            <tr><th>尺寸</th><td colspan="4">{{ dimensionText }}</td></tr>
            <tr><th>质量</th><td>{{ massText }}</td><th>容积</th><td colspan="2">{{ quantity(content.volume, content.volumeUnit) }}</td></tr>
            <tr><th>完残程度</th><td><CollectionCatalogDictText type="col_completeness" :value="content.completeness" /></td><th>保存状态</th><td colspan="2"><CollectionCatalogDictText type="col_preserve" :value="content.preserveState" /></td></tr>
            <tr><th>来源方式</th><td><CollectionCatalogDictText type="col_source" :value="content.source" /></td><th>取得日期</th><td colspan="2">{{ formatLocalDateValue(content.acquireDate) }}</td></tr>
            <tr><th>入馆凭证号</th><td>{{ value(card?.entryVoucherNo) }}</td><th>实收入馆日期</th><td colspan="2">{{ formatLocalDateValue(content.entryDate) }}</td></tr>
            <tr><th>来源说明</th><td colspan="4">{{ value(content.sourceDesc) }}</td></tr>
            <tr><th>基本特征</th><td colspan="4" class="multiline">{{ value(content.feature) }}</td></tr>
            <tr><th>铭文款识</th><td colspan="4" class="multiline">{{ value(content.inscription) }}</td></tr>
            <tr><th>题跋著录</th><td colspan="4" class="multiline">{{ joined(content.colophon, content.bibliography) }}</td></tr>
            <tr><th>附属物件</th><td colspan="4" class="multiline">{{ accessoryText }}</td></tr>
            <tr><th>当前库位</th><td>{{ value(catalog.warehouseName || content.warehouseId) }}</td><th>审核信息</th><td colspan="2">{{ auditText }}</td></tr>
            <tr v-for="field in additionalFields" :key="field.label"><th>{{ field.label }}</th><td colspan="4" class="multiline"><CollectionCatalogDictText v-if="field.dictType" :type="field.dictType" :value="field.value" /><template v-else>{{ value(field.value) }}</template></td></tr>
            <tr><th>制卡人签字</th><td>________________</td><th>制卡日期</th><td colspan="2">________________</td></tr>
            <tr><th>备注</th><td colspan="4" class="multiline">{{ value(content.remark) }}</td></tr>
          </tbody>
        </table>
      </div>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="previewVisible = false">关闭</NButton>
          <NButton :disabled="!catalog || busy" @click="catalog && historyRef?.open(catalog)">编目版本正文</NButton>
          <NButton type="primary" :disabled="!catalog || busy" :loading="busy" @click="printCard">打印卡片</NButton>
        </NSpace>
      </template>
    </NModal>
    <CatalogVersionHistoryDrawer ref="historyRef" />
  </HPageDataTable>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import '../styles/collection-page.css';
import { eraText as describeEra } from '../utils/collectionDescription';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import { catalogCardPrintStyle } from '../utils/catalogCardPrint';
import CollectionCatalogDictText from '../components/CollectionCatalogDictText.vue';
import { normalizeRepeatedMeasureUnit } from '../utils/collectionDictFallback';
import CatalogVersionHistoryDrawer from '../components/CatalogVersionHistoryDrawer.vue';
import { computed, h, reactive, ref, watch, nextTick } from 'vue';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { readCatalog } from '../utils/catalogResponses';
import { NButton, NSpace, useMessage } from 'naive-ui';
import { fileUrl, formatDateTime, formatLocalDateValue } from '@/utils';
import { catalogApi } from '../api/catalog';
import type { CollectionCatalogCardVo, CollectionCatalogContentVo, CollectionCatalogVo } from '../typings/collection';

const paperRef = ref<HTMLElement | null>(null);
const tableRef = ref<any>(null);
const historyRef = ref<any>(null);
const queryParams = reactive({
  keyword: null as string | null,
  level: null as string | null,
  approvedOnly: true,
});
const previewVisible = ref(false);
const card = ref<CollectionCatalogCardVo | null>(null);
const catalog = computed(() => card.value?.catalog || null);
const content = computed<CollectionCatalogContentVo>(() => catalog.value?.content || {});
const coverSrc = computed(() => content.value.coverImage ? fileUrl(content.value.coverImage) : '');
const eraText = computed(() => describeEra(content.value));
const regionText = computed(() => joined(content.value.region, content.value.country));
const dimensionText = computed(() => normalizeRepeatedMeasureUnit(card.value?.registrationFields?.find(field => field.label === '尺寸')?.value || '待核实'));
const shownFields = new Set(['原藏号','分类号','正式名称','具体年代','年代类型','年代表示方式','质地','级别','登记件套数','实际数量','尺寸','质量','完残程度','保存状态','来源方式','来源说明','取得日期','实收入馆日期','基本特征','铭文款识']);
const additionalFields = computed(() => card.value?.registrationFields?.filter(field => !shownFields.has(field.label)) || []);
const massText = computed(() => normalizeRepeatedMeasureUnit(card.value?.registrationFields?.find(field => field.label === '质量')?.value || '待核实'));
const accessoryText = computed(() =>
  content.value.accessories?.map((item) => {
    const name = joined(item.kind, item.name);
    const count = item.quantity ? `${item.quantity}${item.unit || ''}` : '';
    return joined(name, count, item.condition, item.remark);
  }).join('；') || '--',
);
const auditText = computed(() => {
  if (!catalog.value) return '--';
  return joined(
    `V${catalog.value.approvedVersion || catalog.value.submittedVersion || catalog.value.version || 0}`,
    catalog.value.auditBy,
    formatDateTime(catalog.value.auditTime),
  );
});

const columns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '总登记号', key: 'totalRegNo', width: 150, fixed: 'left' as const, columnType: 'title' },
  { ellipsis: { tooltip: true }, resizable: true, width: 210, title: '正式定名', key: 'formalName', minWidth: 210, render: (row: CollectionCatalogVo) => catalogApi.displayName(row) },
  { resizable: true, title: '文物分类', key: 'categoryName', width: 150, ellipsis: { tooltip: true } },
  { ellipsis: { tooltip: true }, resizable: true, title: '年代', key: 'era', width: 120, render: (row: CollectionCatalogVo) => row.content.era || '--' },
  { ellipsis: { tooltip: true }, resizable: true, title: '质地', key: 'texture', width: 100, render: (row: CollectionCatalogVo) => row.content.texture || '--' },
  { ellipsis: { tooltip: true }, resizable: true, title: '级别', key: 'level', width: 100, render: (row: CollectionCatalogVo) => h(CollectionCatalogDictText, { type: 'col_relic_level', value: row.content.level }) },
  { title: '通过版本', key: 'approvedVersion', width: 100, render: (row: CollectionCatalogVo) => `V${row.approvedVersion || 0}` },
  { title: '审核时间', key: 'auditTime', width: 170, render: (row: CollectionCatalogVo) => formatDateTime(row.auditTime) },
  {
    title: '操作',
    key: 'operate',
    type: 'operate',
    width: 100,
    fixed: 'right' as const,
    render: (row: CollectionCatalogVo) =>
      h(NSpace, { size: 8 }, () => [
        h(NButton, { text: true, type: 'primary', size: 'small', onClick: () => openCard(row) }, () => '预览打印'),
      ]),
  },
];

const message = useMessage();
const session = useCatalogOperation(() => { previewVisible.value = false; card.value = null; historyRef.value?.close(); });
const pageActive = session.active, busy = session.busy;
watch(queryParams, () => session.reset(), { deep: true, flush: 'sync' });
watch(previewVisible, show => { if (!show) session.reset(); }, { flush: 'sync' });
function readCard(response: any, id: string): CollectionCatalogCardVo {
  const current = readCatalog({ data: response?.data?.catalog }, id);
  if (current.status !== 'APPROVED' || !current.approvedVersion) throw new Error('正式编目卡资料不完整，请重新核对');
  return { ...response.data, catalog: current };
}
async function openCard(row: CollectionCatalogVo) {
  if (!row.collectionId || !pageActive.value) return;
  const collectionId = row.collectionId;
  session.reset(); previewVisible.value = true;
  const ticket = session.begin('card'); if (ticket === null) return;
  try {
    const response = await catalogApi.card(collectionId);
    if (session.current(ticket) && previewVisible.value) card.value = readCard(response, collectionId);
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '正式编目卡读取失败'); }
  } finally { session.finish(ticket); }
}
async function printCard() {
  if (!pageActive.value || !previewVisible.value || !catalog.value || busy.value) return;
  const target = catalog.value;
  const ticket = session.begin('print'); if (ticket === null) return;
  try {
    const response = await catalogApi.card(target.collectionId!);
    if (!session.current(ticket) || !previewVisible.value) return;
    const result = readCard(response, target.collectionId!); card.value = result;
    if (result.catalog.orgId !== target.orgId || result.catalog.stateHash !== target.stateHash || result.catalog.approvedVersion !== target.approvedVersion) {
      message.warning('卡片依据已变化，请核对当前显示的版本后再打印'); return;
    }
    await nextTick();
    if (session.current(ticket) && previewVisible.value && paperRef.value)
      htmlPrint(paperRef.value.outerHTML, '馆藏文物编目卡', catalogCardPrintStyle);
  } catch (error: any) {
    if (session.current(ticket)) { session.reset(); message.error(error?.message || '打印前核对失败'); }
  } finally { session.finish(ticket); }
}

function value(input: unknown) {
  return input === undefined || input === null || input === '' || input === '--' ? '--' : String(input);
}

function quantity(input: unknown, unit?: string) {
  if (input === undefined || input === null || input === '') return '--';
  return `${input}${unit || ''}`;
}

function joined(...values: Array<unknown>) {
  return values.map(value).filter((item) => item !== '--').join('；') || '--';
}
</script>

<style scoped>
:global(.catalog-card-preview) {
  width: min(1060px, 94vw);
  max-height: calc(100vh - 32px);
  max-height: calc(100dvh - 32px);
  overflow: hidden;
}
.catalog-card-paper {
  width: 100%;
  max-width: 920px;
  margin: 0 auto;
  padding: 22px 26px 30px;
  background: #fff;
  color: #1f2329;
  box-sizing: border-box;
}
.catalog-card-header {
  margin-bottom: 16px;
  text-align: center;
}
.catalog-card-header h1 {
  margin: 0;
  font-family: serif;
  font-size: 28px;
  letter-spacing: 8px;
}
.catalog-card-header p {
  margin: 7px 0 0;
  color: #606266;
}
.catalog-card-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 13px;
}
.catalog-card-table th,
.catalog-card-table td {
  border: 1px solid #333;
  padding: 7px 8px;
  vertical-align: middle;
  word-break: break-word;
}
.catalog-card-table th {
  width: 92px;
  background: #f4f1ea;
  text-align: center;
  font-weight: 600;
}
.catalog-card-table td {
  min-height: 34px;
}
.catalog-card-table .emphasis {
  font-size: 16px;
  font-weight: 600;
}
.cover-cell {
  width: 190px;
  padding: 6px !important;
  text-align: center;
}
.cover-cell img {
  width: 100%;
  height: 190px;
  object-fit: contain;
}
.cover-empty {
  color: #999;
}
.multiline {
  min-height: 46px;
  white-space: pre-wrap;
}

</style>
