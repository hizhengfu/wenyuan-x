<template>
  <NSpin :show="loading">
    <main class="stat-page">
      <section class="stat-toolbar">
        <div class="title-block">
          <h1>藏品统计</h1>
          <div class="stat-scope">
            <CollectionFieldLabel
              :label="selectedScopeTitle"
              :help="`${selectedScopeHint}；正式登记且未退出馆藏的记录纳入统计。`"
            />
            <span>{{ stat.total }} 条在册记录</span>
          </div>
        </div>
        <div class="toolbar-actions">
          <NSpace size="small" align="center" class="scope-actions">
            <NTreeSelect
              v-model:value="selectedOrgId"
              :options="orgTreeOptions"
              clearable
              filterable
              placeholder="收藏单位"
              style="width: 230px"
              @update:value="onOrgChange"
            />
            <NRadioGroup v-model:value="scopeMode" size="small">
              <NRadioButton
                v-for="option in scopeModeOptions"
                :key="option.value"
                :value="option.value"
                :disabled="option.requiresOrg && !selectedOrgId"
              >
                {{ option.label }}
              </NRadioButton>
            </NRadioGroup>
          </NSpace>
          <NSpace size="small" align="center">
            <NRadioGroup v-model:value="viewMode" size="small" aria-label="统计展示方式">
              <NRadioButton v-for="option in viewModeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </NRadioButton>
            </NRadioGroup>
            <NButton size="small" secondary :loading="loading" @click="loadPageData">刷新</NButton>
            <CollectionActionMenu label="统计报表" :loading="printing">
              <NButton quaternary size="small" :loading="printing" @click="printStatReport">打印</NButton>
              <NButton quaternary size="small" @click="periodReportRef?.open()">期间增减与提用动态</NButton>
            </CollectionActionMenu>
            <CollectionActionMenu label="普查数据" :loading="exporting">
              <NButton quaternary size="small" @click="openSurveyImport">普查导入</NButton>
              <NButton quaternary size="small" :loading="exporting" @click="onExport">普查导出 CSV</NButton>
            </CollectionActionMenu>
          </NSpace>
        </div>
      </section>
      <section class="analysis-filters">
        <NForm label-placement="top"
          ><div class="analysis-filter-grid">
            <NFormItem label="检索藏品"
              ><NInput v-model:value="analysisFilters.keyword" clearable placeholder="名称 / 总登记号 / 原藏号"
            /></NFormItem>
            <NFormItem v-for="dimension in analysisDimensions" :key="dimension.key" :label="dimension.label">
              <NSelect
                v-model:value="analysisFilters[dimension.key]"
                :options="filterOptions(dimension.key)"
                multiple
                filterable
                clearable
                max-tag-count="responsive"
              />
            </NFormItem>
            <NFormItem label="当前使用"
              ><NSelect
                v-model:value="analysisFilters.useState"
                :options="[
                  { label: '当前外借', value: 'LOAN' },
                  { label: '当前实际展出', value: 'EXHIBITION' },
                ]"
                clearable
            /></NFormItem></div
        ></NForm>
        <NSpace align="center"
          ><NButton size="small" @click="resetAnalysisFilters">清空组合条件</NButton
          ><CollectionFieldLabel label="筛选规则" help="同一字段多选为任一匹配，不同字段同时匹配。" /></NSpace
        >
      </section>
      <NAlert v-if="loadError" type="error">{{ loadError }}<NButton text @click="loadPageData">重试</NButton></NAlert>
      <NAlert v-if="excludedRegistrations.length" type="warning"
        >本筛选中
        {{
          excludedRegistrations.length
        }}
        条记录未纳入当前藏品统计及导出，请核对下方明细。既有总登记账及历史资料仍须保留。</NAlert
      >
      <NCollapse v-if="excludedRegistrations.length"
        ><NCollapseItem title="未纳入当前统计的记录"
          ><NDataTable
            :scroll-x="610"
            :columns="[
              { ellipsis: { tooltip: true }, resizable: true, width: 150, title: '登记号', key: 'totalRegNo' },
              { ellipsis: { tooltip: true }, resizable: true, width: 200, title: '名称', key: 'name' },
              { ellipsis: { tooltip: true }, resizable: true, width: 260, title: '原因', key: 'reason' },
            ]"
            :data="excludedRegistrations"
            :pagination="{ pageSize: 10 }" /></NCollapseItem
      ></NCollapse>
      <section class="kpi-grid">
        <div v-for="item in overviewCards" :key="item.label" class="kpi-tile">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </section>

      <section class="stat-section">
        <div class="section-head">
          <strong>交叉分析</strong
          ><NSpace>
            <NSelect
              v-model:value="crossFirst"
              :options="analysisDimensions.map((d) => ({ label: d.label, value: d.key }))"
              style="width: 150px"
            />
            <NSelect
              v-model:value="crossSecond"
              :options="analysisDimensions.map((d) => ({ label: d.label, value: d.key }))"
              style="width: 150px"
            />
          </NSpace>
        </div>
        <NDataTable :columns="crossColumns" :data="crossRows" :pagination="{ pageSize: 15 }" :scroll-x="640" />
      </section>
      <section class="stat-section">
        <div class="section-head">
          <strong><CollectionFieldLabel label="登记月份分布" help="所筛选的当前在册藏品；历史增减见年度统计报送。" /></strong>
        </div>
        <HEChart v-if="registrationTrend.length" :option="registrationTrendOption" style="height: 260px" />
        <NEmpty v-else description="暂无可核实登记日期" />
      </section>
      <section class="quality-section">
        <div class="section-head">
          <strong>数据完善度</strong>
        </div>
        <div class="quality-grid">
          <div v-for="item in dataQualityItems" :key="item.label" class="quality-metric">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <small>{{ item.note }}</small>
          </div>
        </div>
      </section>

      <section class="stat-section">
        <div class="section-head">
          <strong>影像资源</strong>
        </div>
        <HEChart
          v-if="mediaChartRows.length"
          :option="buildBarOption('影像类型', mediaChartRows)"
          style="height: 300px"
        />
        <NEmpty v-else description="暂无影像资源" size="small" />
      </section>

      <section class="stat-section">
        <div class="section-head">
          <strong>分类统计</strong>
        </div>

        <template v-if="viewMode === 'chart'">
          <div class="stat-chart-grid">
            <section v-for="chart in chartOptions" :key="chart.title" class="stat-chart-panel">
              <div class="stat-chart-title">
                <span>{{ chart.title }}</span>
                <small>{{ chart.rows.length }} 项</small>
              </div>
              <HEChart v-if="chart.rows.length" :option="chart.option" style="height: 260px" />
              <NEmpty v-else description="暂无数据" size="small" />
            </section>
          </div>
        </template>

        <NDataTable
          :scroll-x="550"
          v-else
          :columns="distributionColumns"
          :data="statRows"
          size="small"
          :bordered="false"
          :pagination="{ pageSize: 20 }"
        />
      </section>

      <NModal
        v-model:show="surveyImportShow"
        preset="card"
        title="全国可移动文物普查数据导入"
        style="width: min(900px, calc(100vw - 32px))"
      >
        <NAlert type="info" :bordered="false" style="margin-bottom: 12px"
          >支持导入本系统“普查导出 CSV”文件，也支持同列结构的 Excel；导入前先进行行级预校验。</NAlert
        >
        <NSpace align="center">
          <NButton type="primary" secondary @click="surveyFileInput?.click()">选择 CSV/Excel</NButton>
          <span>{{ surveyFileName || '未选择文件' }}</span>
        </NSpace>
        <input
          ref="surveyFileInput"
          type="file"
          accept=".csv,.xlsx,.xls"
          style="display: none"
          @change="onSurveyFileChange"
        />
        <NDataTable
          :scroll-x="500"
          v-if="surveyErrors.length"
          :columns="surveyErrorColumns"
          :data="surveyErrors"
          size="small"
          :bordered="false"
          :max-height="300"
          style="margin-top: 14px"
        />
        <NAlert v-else-if="surveyRows.length" type="success" :bordered="false" style="margin-top: 14px"
          >已解析 {{ surveyRows.length }} 条，请先完成服务端预校验。</NAlert
        >
        <NAlert v-if="surveyConfirmed?.validation.confirmation" type="info" :bordered="false" style="margin-top: 14px"
          >目标馆：{{ surveyConfirmed.validation.confirmation.orgName }}（{{
            surveyConfirmed.validation.confirmation.orgId
          }}），新增 {{ surveyConfirmed.validation.createCount }} 条。请核对后确认。</NAlert
        >
        <template #footer
          ><NSpace justify="end"
            ><NButton @click="surveyImportShow = false">取消</NButton
            ><NButton :disabled="!surveyRows.length || surveyBusy" @click="validateSurveyImport">服务端预校验</NButton
            ><NButton
              type="primary"
              :disabled="!surveyConfirmed || surveyBusy"
              :loading="surveySession.operation.value === 'commit'"
              @click="commitSurveyImport"
              >确认导入</NButton
            ></NSpace
          ></template
        >
      </NModal>

      <section class="stat-section">
        <div class="section-head">
          <div>
            <span>收藏单位统计</span>
            <strong>整体与分馆</strong>
          </div>
          <NText depth="3">可直接切换到某个馆的直属统计或含下级汇总统计</NText>
        </div>
        <NDataTable
          :scroll-x="1020"
          :columns="orgStatColumns"
          :data="orgStatRows"
          size="small"
          :bordered="false"
          :pagination="{ pageSize: 12 }"
          :row-key="(row: any) => row.id"
        />
      </section>
    </main>
  </NSpin>
  <CollectionPeriodReportModal ref="periodReportRef" />
</template>

<script setup lang="ts">
import CollectionActionMenu from '../components/CollectionActionMenu.vue';
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import { isRegistered } from '../utils/registrationStatus';
import { useAuthStore } from '@/store';
const auth = useAuthStore();
import { useRoute } from 'vue-router';
import { computed, h, onMounted, reactive, ref, watch } from 'vue';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { NButton, NTag, useMessage } from 'naive-ui';
import { htmlPrint } from '@/components/basic/print/PrintUtils';
import CollectionPeriodReportModal from '../components/CollectionPeriodReportModal.vue';
const periodReportRef=ref<InstanceType<typeof CollectionPeriodReportModal>>();
import HEChart from '@/components/data/chart/EChart.vue';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import {
  analysisDimensions,
  analysisLabel,
  analysisValue,
  emptyAnalysisFilters,
  filterAnalysisCollections,
  buildCrossAnalysis,
  buildRegistrationTrend,
  collectionQuantities,
  quantityText,
  type AnalysisDimension,
} from '../utils/collectionAnalysis';
import type { CollectionUsageSummary } from '../typings/collection';
import { collectionApi } from '../api/collection';
import { useCollectionImportSession } from '../composables/useCollectionImportSession';
import { ledgerValidation, ledgerCommit } from '../utils/importResponses';
import type { CollectionImportValidateResultVo } from '../typings/collection';
import { orgApi } from '../api/org';
import { downloadSurveyCsv } from '../utils/surveyExport';
import { parseSurveyImportFile } from '../utils/surveyImport';
import {
  buildStatDistributionRows,
  buildStatReportHtml,
  statDistributionSections,
  statReportPrintStyle,
  type StatDistributionRow,
} from '../utils/statReport';
import {
  buildCollectionDataQualityItems,
  buildCollectionStatSummary,
  buildOrgNameMap,
  flattenOrgNodes,
  collectOrgIds,
  filterCollectionsByOrgScope,
  findOrgNode,
  statScopeLabel,
  toSurveyExportRows,
  registrationExclusions,
  type OrgStatRow,
  type StatScopeMode,
} from '../utils/statAnalysis';
import type {
  CollectionImportErrorVo,
  CollectionImportRowVo,
  CollectionMediaStatVo,
  CollectionOrgOverviewNode,
  CollectionVo,
} from '../typings/collection';

const message = useMessage();
const route = useRoute();
const loading = ref(false);
const exporting = ref(false);
const loadError = ref('');
const usage = ref<CollectionUsageSummary | null>(null);
const analysisFilters = reactive(emptyAnalysisFilters());
const crossFirst = ref<AnalysisDimension>('categoryId'),
  crossSecond = ref<AnalysisDimension>('level');
function resetAnalysisFilters() {
  Object.assign(analysisFilters, emptyAnalysisFilters());
}

const printing = ref(false);
const viewMode = ref<'chart' | 'list'>('chart');
const scopeMode = ref<StatScopeMode>('all');
const selectedOrgId = ref<string | null>(typeof route.query.orgId === 'string' ? route.query.orgId : null);
if (route.query.scopeMode === 'direct' || route.query.scopeMode === 'aggregate')
  scopeMode.value = route.query.scopeMode;
watch(
  () => [route.query.orgId, route.query.scopeMode],
  () => {
    if (route.path !== '/collection/stat') return;
    selectedOrgId.value = typeof route.query.orgId === 'string' ? route.query.orgId : null;
    scopeMode.value =
      route.query.scopeMode === 'direct' || route.query.scopeMode === 'aggregate' ? route.query.scopeMode : 'all';
  },
  { flush: 'post' },
);
const allCollections = ref<CollectionVo[]>([]);
const orgOverview = ref<CollectionOrgOverviewNode[]>([]);
const dictLabels = ref<Record<string, Record<string, string>>>({});
const mediaStat = ref<CollectionMediaStatVo>({ total: 0, collectionCount: 0, byType: {} });
const surveyImportShow = ref(false);
const surveyFileInput = ref<HTMLInputElement | null>(null);
const surveyFileName = ref('');
const surveyRows = ref<CollectionImportRowVo[]>([]);
const surveyErrors = ref<CollectionImportErrorVo[]>([]);
const surveyConfirmed = ref<{ validation: CollectionImportValidateResultVo; rows: CollectionImportRowVo[] } | null>(
  null,
);
const surveySession = useCollectionImportSession(() => {
  surveyImportShow.value = false;
  clearSurveyInput();
});
const surveyBusy = surveySession.busy;
function clearSurveyInput() {
  surveyRows.value = [];
  surveyErrors.value = [];
  surveyFileName.value = '';
  surveyConfirmed.value = null;
}
watch(
  surveyRows,
  () => {
    surveySession.invalidate();
    surveyConfirmed.value = null;
  },
  { deep: true, flush: 'sync' },
);
watch(
  surveyImportShow,
  (show) => {
    if (!show) {
      surveySession.invalidate();
      clearSurveyInput();
    }
  },
  { flush: 'sync' },
);

const surveyErrorColumns = [
  { title: '行号', key: 'rowNo', width: 80 },
  { ellipsis: { tooltip: true }, resizable: true, title: '字段', key: 'field', width: 140 },
  { ellipsis: { tooltip: true }, resizable: true, width: 280, title: '错误', key: 'message', minWidth: 280 },
];

const viewModeOptions = [
  { label: '图形', value: 'chart' },
  { label: '列表', value: 'list' },
];
const scopeModeOptions: { label: string; value: StatScopeMode; requiresOrg?: boolean }[] = [
  { label: '全馆汇总', value: 'all' },
  { label: '直属藏品', value: 'direct', requiresOrg: true },
  { label: '含分馆汇总', value: 'aggregate', requiresOrg: true },
];

onMounted(loadPageData);

const orgNameById = computed(() => buildOrgNameMap(orgOverview.value));
const exhibitionKeys = computed(
  () =>
    new Set(
      usage.value?.items.filter((item) => item.onExhibition).map((item) => JSON.stringify([item.orgId, item.id])) || [],
    ),
);
const enrichedCollections = computed(() =>
  allCollections.value.map((row) => ({
    ...row,
    orgName: row.orgId ? orgNameById.value[row.orgId] || row.orgId : undefined,
    onExhibition: exhibitionKeys.value.has(JSON.stringify([row.orgId, row.id])),
  })),
);
const selectedOrgNode = computed(() => findOrgNode(orgOverview.value, selectedOrgId.value));
const orgScopedCollections = computed(() =>
  filterCollectionsByOrgScope(enrichedCollections.value, orgOverview.value, {
    mode: scopeMode.value,
    orgId: selectedOrgId.value,
  }),
);
const scopedCollections = computed(() => filterAnalysisCollections(orgScopedCollections.value, analysisFilters));
function filterOptions(key: AnalysisDimension) {
  const options = new Map<string, string>();
  for (const row of orgScopedCollections.value) {
    const value = analysisValue(row, key);
    if (value) options.set(value, analysisLabel(row, key, statLabelResolver));
  }
  return [...options]
    .map(([value, label]) => ({ value, label }))
    .sort((a, b) => a.label.localeCompare(b.label, 'zh-Hans-CN'));
}
const crossRows = computed(() =>
  buildCrossAnalysis(scopedCollections.value, crossFirst.value, crossSecond.value, statLabelResolver),
);
const crossColumns = computed(() => [
  {
    title: analysisDimensions.find((d) => d.key === crossFirst.value)?.label,
    key: 'first',
    minWidth: 160,
    ellipsis: { tooltip: true },
  },
  {
    title: analysisDimensions.find((d) => d.key === crossSecond.value)?.label,
    key: 'second',
    minWidth: 160,
    ellipsis: { tooltip: true },
  },
  { title: '登记记录数', key: 'count', width: 120 },
  { title: '占比', key: 'percent', width: 80, render: (r: any) => `${r.percent}%` },
  { title: '实物数量', key: 'quantity', minWidth: 130 },
  { title: '数量待核实', key: 'unknown', width: 110 },
]);
const registrationTrend = computed(() => buildRegistrationTrend(scopedCollections.value));
const registrationTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 45, right: 20, bottom: 45, top: 20 },
  xAxis: { type: 'category', data: registrationTrend.value.map((r) => r.month) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ name: '当前在册登记记录数', type: 'bar', data: registrationTrend.value.map((r) => r.count) }],
}));
const filterDescription = computed(() => {
  const parts = analysisDimensions
    .filter((d) => analysisFilters[d.key].length)
    .map(
      (d) =>
        `${d.label}：${filterOptions(d.key)
          .filter((o) => analysisFilters[d.key].includes(o.value))
          .map((o) => o.label)
          .join('、')}`,
    );
  if (analysisFilters.keyword.trim()) parts.push(`检索：${analysisFilters.keyword.trim()}`);
  if (analysisFilters.useState) parts.push(analysisFilters.useState === 'EXHIBITION' ? '当前实际展出' : '当前外借');
  return parts.join('；') || '未设置组合筛选';
});
const excludedRegistrations = computed(() => registrationExclusions(scopedCollections.value));
const stat = computed(() => buildCollectionStatSummary(scopedCollections.value, new Date(), orgNameById.value));
const selectedScopeTitle = computed(() => statScopeLabel(selectedOrgNode.value, scopeMode.value));
const selectedScopeHint = computed(() => {
  if (scopeMode.value === 'all' || !selectedOrgId.value) return '统计当前账号可见的全部收藏单位数据';
  return scopeMode.value === 'direct' ? '仅统计所选收藏单位直属藏品' : '统计所选收藏单位及全部下级分馆藏品';
});
const orgTreeOptions = computed(() => mapOrgTreeOptions(orgOverview.value));
const orgStatRows = computed<OrgStatRow[]>(() =>
  flattenOrgNodes(orgOverview.value).map((node) => {
    const direct = scopedCollections.value.filter((row) => row.orgId === node.id),
      ids = new Set(collectOrgIds(node));
    const aggregate = scopedCollections.value.filter((row) => row.orgId && ids.has(row.orgId)),
      summary = buildCollectionStatSummary(aggregate);
    return {
      id: node.id,
      name: node.name,
      code: node.code,
      depth: node.depth,
      childCount: node.children?.length || 0,
      directTotal: buildCollectionStatSummary(direct).total,
      aggregateTotal: summary.total,
      inStoreCount: summary.byStatus.IN_STORE || 0,
      flowCount: ['PRE_OUT', 'IN_USE', 'RESTORING', 'ON_LOAN'].reduce(
        (sum, key) => sum + (summary.byStatus[key] || 0),
        0,
      ),
      imageRate: summary.imageRate,
      attachmentRate: summary.attachmentRate,
    };
  }),
);
const statRows = computed(() => buildStatDistributionRows(stat.value, statLabelResolver));
const overviewCards = computed(() => {
  const formal = scopedCollections.value.filter((row) => isRegistered(row) && row.lifeStatus !== 'DEREGISTERED');
  const loan = formal.filter((row) => row.lifeStatus === 'ON_LOAN'),
    exhibition = formal.filter((row) => row.onExhibition);
  return [
    { label: '藏品总量', rows: formal },
    { label: '当前外借', rows: loan },
    { label: '当前展出', rows: exhibition },
  ].map(({ label, rows }) => {
    const q = collectionQuantities(rows);
    return {
      label,
      value: quantityText(q.byUnit, q.unknown ? '待核实' : '0'),
      note: `登记记录 ${rows.length} 条${q.unknown ? `；${q.unknown} 条数量待核实` : ''}`,
    };
  });
});
const dataQualityItems = computed(() => {
  return buildCollectionDataQualityItems(scopedCollections.value);
});
const chartOptions = computed(() =>
  statDistributionSections.map((section, index) => {
    const rows = statRows.value.filter((row) => row.section === section.title);
    return {
      title: section.title,
      rows,
      option: index < 4 ? buildPieOption(section.title, rows) : buildBarOption(section.title, rows),
    };
  }),
);
const mediaChartRows = computed<StatDistributionRow[]>(() =>
  Object.entries(
    scopedCollections.value
      .filter((row) => isRegistered(row) && row.lifeStatus !== 'DEREGISTERED')
      .reduce<Record<string, number>>((total, row) => {
        for (const [type, count] of Object.entries(mediaStat.value.byCollection?.[row.id || ''] || {}))
          total[type] = (total[type] || 0) + count;
        return total;
      }, {}),
  ).map(([name, value]) => ({
    name: statLabelResolver('col_media_type', name),
    rawName: name,
    value,
    rate: 0,
    section: '影像类型',
    dictType: 'col_media_type',
  })),
);

const distributionColumns = [
  { ellipsis: { tooltip: true }, resizable: true, title: '统计口径', key: 'section', width: 130 },
  { ellipsis: { tooltip: true }, resizable: true, width: 180, title: '项目', key: 'name', minWidth: 180 },
  { title: '记录数', key: 'value', width: 120 },
  { title: '占比', key: 'rate', width: 120, render: (row: StatDistributionRow) => `${row.rate}%` },
];

const orgStatColumns = [
  {
    ellipsis: { tooltip: true },
    resizable: true,
    width: 220,
    title: '收藏单位',
    key: 'name',
    minWidth: 220,
    render: (row: OrgStatRow) =>
      h('div', { class: 'org-name-cell', style: { paddingLeft: `${row.depth * 16}px` } }, [
        h('strong', row.name),
        row.code ? h('small', row.code) : null,
        row.childCount
          ? h(NTag, { size: 'small', type: 'info', bordered: false }, () => `${row.childCount} 个分馆`)
          : null,
      ]),
  },
  { title: '直属记录数', key: 'directTotal', width: 110 },
  { title: '汇总记录数', key: 'aggregateTotal', width: 110 },
  { title: '在库记录数', key: 'inStoreCount', width: 100 },
  { title: '提用/借出/修复', key: 'flowCount', width: 130 },
  { title: '图像率', key: 'imageRate', width: 90, render: (row: OrgStatRow) => `${row.imageRate}%` },
  { title: '资料附件率', key: 'attachmentRate', width: 110, render: (row: OrgStatRow) => `${row.attachmentRate}%` },
  {
    title: '筛选',
    key: 'actions',
    width: 150,
    render: (row: OrgStatRow) =>
      h('div', { class: 'org-actions' }, [
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => selectOrgScope(row.id, 'direct') },
          () => '直属',
        ),
        h(
          NButton,
          { text: true, type: 'primary', size: 'small', onClick: () => selectOrgScope(row.id, 'aggregate') },
          () => '汇总',
        ),
      ]),
  },
];

const pageSession = useCatalogOperation(() => {
  allCollections.value = [];
  orgOverview.value = [];
  usage.value = null;
  loading.value = false;
  loadError.value = '';
  mediaStat.value = { total: 0, collectionCount: 0, byType: {} };
});
watch(pageSession.active, (active, previous) => {
  if (active && !previous) void loadPageData();
});
async function loadPageData() {
  pageSession.reset();
  const ticket = pageSession.begin('load');
  if (ticket === null) return;
  loading.value = true;
  loadError.value = '';
  allCollections.value = [];
  usage.value = null;
  try {
    const [collectionResp, orgResp, mediaResp, usageResp] = await Promise.all([
      collectionApi.page({ pageNum: 1, pageSize: 0 }),
      orgApi.overview(),
      collectionApi.mediaStat(),
      collectionApi.usageSummary(),
      loadStatDictLabels(),
    ]);
    if (!pageSession.current(ticket)) return;
    const value = (usageResp as any)?.data;
    if (!Array.isArray(value?.items) || !Number.isSafeInteger(value.total) || value.total !== value.items.length)
      throw new Error('实际使用统计数据不完整，请重新读取');
    const rows = listFromPage(collectionResp);
    const payload = (collectionResp as any)?.data;
    if (
      !Array.isArray(rows) ||
      (payload?.list && payload.total !== rows.length) ||
      !Array.isArray((orgResp as any)?.data) ||
      !(mediaResp as any)?.data?.byCollection
    )
      throw new Error('统计资料读取不完整，请核对配套版本后重试');
    usage.value = value;
    allCollections.value = rows;
    orgOverview.value = (orgResp as any)?.data ?? [];
    mediaStat.value = (mediaResp as any)?.data ?? { total: 0, collectionCount: 0, byType: {} };
  } catch (error: any) {
    if (!pageSession.current(ticket)) return;
    loadError.value = error?.message || '统计资料读取失败，请重试';
    allCollections.value = [];
    usage.value = null;
    orgOverview.value = [];
  } finally {
    if (pageSession.current(ticket)) loading.value = false;
    pageSession.finish(ticket);
  }
}

async function loadStatDictLabels() {
  const types = Array.from(
    new Set(
      [...statDistributionSections.map((section) => section.dictType), 'col_media_type'].filter(
        (type): type is string => Boolean(type),
      ),
    ),
  );
  const entries = await Promise.all(
    types.map(async (type) => {
      try {
        const resp: any = await fetchDictType(type, true);
        return [
          type,
          Object.fromEntries(
            (resp?.data ?? []).map((item: any) => [
              String(item.value ?? ''),
              String(item.label ?? item.name ?? item.value ?? ''),
            ]),
          ),
        ] as const;
      } catch {
        return [type, {}] as const;
      }
    }),
  );
  dictLabels.value = Object.fromEntries(entries);
}

function listFromPage(resp: any): CollectionVo[] {
  return resp?.data?.list ?? resp?.data ?? [];
}

function mapOrgTreeOptions(nodes: CollectionOrgOverviewNode[]): any[] {
  return nodes.map((node) => ({
    label: node.name,
    key: node.id,
    children: node.children?.length ? mapOrgTreeOptions(node.children) : undefined,
  }));
}

function onOrgChange(value: string | null) {
  selectedOrgId.value = value;
  if (!value) {
    scopeMode.value = 'all';
  } else if (scopeMode.value === 'all') {
    scopeMode.value = 'aggregate';
  }
}

function selectOrgScope(orgId: string, mode: StatScopeMode) {
  selectedOrgId.value = orgId;
  scopeMode.value = mode;
}

function statLabelResolver(dictType: string | undefined, value: string) {
  if (!dictType) return value;
  return dictLabels.value[dictType]?.[value] || value;
}

function rateText(value: number, total: number) {
  return `${total ? Math.round((value / total) * 100) : 0}%`;
}

function buildPieOption(title: string, rows: StatDistributionRow[]) {
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['44%', '68%'],
        center: ['50%', '42%'],
        avoidLabelOverlap: true,
        data: rows.map((row) => ({ name: row.name, value: row.value })),
      },
    ],
  };
}

function buildBarOption(title: string, rows: StatDistributionRow[]) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 16, top: 28, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((row) => row.name),
      axisLabel: { interval: 0, rotate: rows.length > 4 ? 30 : 0 },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: title,
        type: 'bar',
        barMaxWidth: 34,
        data: rows.map((row) => row.value),
      },
    ],
  };
}

async function onExport() {
  exporting.value = true;
  try {
    const rows = toSurveyExportRows(scopedCollections.value, orgNameById.value);
    if (!rows.length) {
      message.warning('当前筛选暂无可导出的藏品记录');
      return;
    }
    await downloadSurveyCsv(rows, `${selectedScopeTitle.value}藏品普查导出.csv`);
    message.success(`已导出 ${rows.length} 条`);
  } finally {
    exporting.value = false;
  }
}

function openSurveyImport() {
  surveySession.invalidate();
  clearSurveyInput();
  surveyImportShow.value = true;
}
async function onSurveyFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !surveyImportShow.value) return;
  surveySession.invalidate();
  clearSurveyInput();
  surveyFileName.value = file.name;
  input.value = '';
  const ticket = surveySession.begin('parse');
  if (ticket === null) return;
  try {
    const parsed = await parseSurveyImportFile(file);
    if (!surveySession.current(ticket) || !surveyImportShow.value) return;
    surveyRows.value = parsed.rows;
    surveyErrors.value = parsed.errors;
  } catch (error: any) {
    if (surveySession.current(ticket)) {
      clearSurveyInput();
      message.error(error?.message || '普查文件解析失败');
    }
  } finally {
    surveySession.finish(ticket);
  }
}
async function validateSurveyImport() {
  if (!surveyImportShow.value || !surveyRows.value.length || surveyBusy.value) return;
  const rows = JSON.parse(JSON.stringify(surveyRows.value)) as CollectionImportRowVo[];
  surveyConfirmed.value = null;
  surveyErrors.value = [];
  const ticket = surveySession.begin('validate');
  if (ticket === null) return;
  try {
    const response = await collectionApi.validateImport(rows);
    if (!surveySession.current(ticket) || !surveyImportShow.value) return;
    const result = ledgerValidation(response, rows.length);
    surveyErrors.value = result.errors;
    if (result.valid) {
      surveyConfirmed.value = { validation: result, rows };
      message.success('服务端预校验通过，请核对目标馆后确认');
    } else message.warning('预校验未通过，未导入');
  } catch (error: any) {
    if (surveySession.current(ticket)) {
      surveyConfirmed.value = null;
      surveyErrors.value = [];
      message.error(error?.message || '普查导入预校验失败');
    }
  } finally {
    surveySession.finish(ticket);
  }
}
async function commitSurveyImport() {
  const confirmed = surveyConfirmed.value;
  if (!confirmed?.validation.confirmation || !surveyImportShow.value || surveyBusy.value) return;
  const ticket = surveySession.begin('commit');
  if (ticket === null) return;
  try {
    const response = await collectionApi.commitImport(
      JSON.parse(JSON.stringify(confirmed.rows)),
      'CREATE_ONLY',
      undefined,
      confirmed.validation.confirmation,
    );
    if (!surveySession.current(ticket) || !surveyImportShow.value) return;
    const result = ledgerCommit(response, confirmed.validation);
    surveyErrors.value = result.errors;
    if (!result.valid) {
      surveyConfirmed.value = null;
      message.warning('重新校验未通过，未导入');
      return;
    }
    message.success('普查数据导入完成，共 ' + result.createdIds.length + ' 条');
    surveyImportShow.value = false;
    await loadPageData();
  } catch (error: any) {
    if (surveySession.current(ticket)) {
      surveyConfirmed.value = null;
      surveyErrors.value = [];
      message.error(error?.message || '普查导入提交失败，请核对处理结果');
    }
  } finally {
    surveySession.finish(ticket);
  }
}

function escapeAnalysisHtml(value: unknown) {
  return String(value ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] || c,
  );
}
async function printStatReport() {
  printing.value = true;
  try {
    if (!statRows.value.length) message.warning('当前统计暂无分布明细');
    htmlPrint(
      buildStatReportHtml(stat.value, statLabelResolver, {
        scopeLabel: `${selectedScopeTitle.value}；${filterDescription.value}`,
      }) +
        `<section class="collection-stat-print"><h2>交叉分析</h2><table class="distribution"><thead><tr><th>${escapeAnalysisHtml(crossColumns.value[0].title)}</th><th>${escapeAnalysisHtml(crossColumns.value[1].title)}</th><th>记录数</th><th>实物数量</th><th>数量待核实</th></tr></thead><tbody>${crossRows.value.map((r) => `<tr><td>${escapeAnalysisHtml(r.first)}</td><td>${escapeAnalysisHtml(r.second)}</td><td>${r.count}</td><td>${escapeAnalysisHtml(r.quantity)}</td><td>${r.unknown}</td></tr>`).join('')}</tbody></table><h2>登记月份分布</h2><p>仅统计本次筛选的当前在册记录，不代表历史馆藏增减。</p><table class="distribution"><tbody>${registrationTrend.value.map((r) => `<tr><td>${r.month}</td><td>${r.count}</td></tr>`).join('')}</tbody></table></section>`,
      '藏品统计分析报表',
      statReportPrintStyle,
    );
  } finally {
    printing.value = false;
  }
}
watch(
  () => [auth.token, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, auth.roles, auth.permissions],
  () => {
    pageSession.reset();
    if (auth.token && !auth.identitySyncing) void loadPageData();
  },
  { deep: true, flush: 'sync' },
);
</script>

<style scoped>
.stat-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  color: #172033;
}

.stat-toolbar,
.quality-section,
.stat-section {
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
}

.stat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
}

.title-block {
  min-width: 0;
}

.section-head span {
  color: #08756f;
  font-size: 13px;
  font-weight: 700;
}

.title-block h1 {
  margin: 4px 0;
  font-size: 24px;
  line-height: 1.25;
}

.stat-scope {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 14px;
  margin-top: 8px;
  color: #6b778c;
  font-size: 13px;
}

.toolbar-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  min-width: 0;
  max-width: 100%;
}
.scope-actions {
  padding-bottom: 12px;
  border-bottom: 1px solid #e8ebef;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.kpi-tile {
  min-width: 0;
  padding: 14px;
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
}

.kpi-tile span {
  color: #6b778c;
  font-size: 13px;
}

.kpi-tile strong {
  display: block;
  margin-top: 6px;
  font-size: 26px;
  line-height: 1.2;
}

.kpi-tile small {
  display: block;
  margin-top: 5px;
  color: #6b778c;
}

.stat-section {
  min-width: 0;
  padding: 14px;
}

.quality-section {
  min-width: 0;
  padding: 14px;
}

.quality-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 16px;
  border-top: 1px solid #e3e9f2;
  padding-top: 12px;
}

.quality-metric {
  min-width: 0;
}

.quality-metric span {
  color: #6b778c;
  font-size: 13px;
}

.quality-metric strong {
  display: block;
  margin-top: 4px;
  font-size: 22px;
  line-height: 1.2;
}

.quality-metric small {
  display: block;
  margin-top: 4px;
  color: #6b778c;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
}

.section-head div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.section-head strong {
  font-size: 16px;
}

.stat-chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}

.stat-chart-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e3e9f2;
  border-radius: 6px;
  background: #fbfdff;
}

.stat-chart-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  color: #1f2937;
}

.stat-chart-title span {
  font-size: 14px;
  font-weight: 700;
}

.stat-chart-title small {
  color: #6b778c;
}

.org-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.org-name-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-name-cell small {
  color: #6b778c;
}

.org-actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 1200px) {
  .stat-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-actions {
    align-items: flex-start;
  }

  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .stat-page {
    padding: 12px;
  }

  .kpi-grid,
  .stat-chart-grid {
    grid-template-columns: 1fr;
  }

  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
.analysis-filters {
  padding: 16px;
  border: 1px solid var(--n-border-color, #e5e7eb);
  border-radius: 8px;
}
.analysis-filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0 16px;
}
@media (max-width: 1000px) {
  .analysis-filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .analysis-filter-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
