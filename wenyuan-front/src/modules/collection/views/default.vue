<template>
  <NSpin v-if="pageActive" :show="loading">
    <main class="collection-home">
      <NAlert v-if="homeLoadErrors.length" type="warning" title="部分数据未能更新" role="status">
        <NSpace justify="space-between" align="center">
          <span
            >{{ homeLoadErrors.map((key) => homeLoadLabels[key]).join('、') }}加载失败；其他已成功数据仍可使用。</span
          >
          <NButton size="small" :loading="loading" @click="loadHomeData">重试</NButton>
        </NSpace>
      </NAlert>

      <NTabs v-model:value="activeTab" type="line" class="home-tabs" pane-wrapper-class="home-tab-panels">
        <NTabPane name="overview" tab="工作概览">
          <div class="home-tab-content">
            <!-- 总账快照 -->
            <div class="kpi-toolbar">
              <strong>馆藏概况</strong>
              <NPopover trigger="click" placement="bottom-end" :width="360">
                <template #trigger><NButton text type="primary" size="small">统计口径</NButton></template>
                <div class="scope-popover">
                  <strong>统计口径</strong>
                  <p>藏品总量：正式在册登记记录数，按件（套）计，一套算一件；借出、提用的藏品仍计入其中。</p>
                  <p>
                    当前外借、在库也按件（套）计；待登记、登记依据待核实和退出馆藏的记录单列。
                  </p>
                  <p>年度增减：按登记、调拨、退出等历史记录计算，出入库流水另行统计。</p>
                  <NButton text type="primary" size="small" @click="go('/collection/annual-report')"
                    >查看年度统计</NButton
                  >
                </div>
              </NPopover>
            </div>
            <section class="kpi-grid">
              <button
                type="button"
                v-for="card in kpiCards"
                :key="card.label"
                class="kpi-card"
                @click="card.path && go(card.path)"
              >
                <span class="kpi-label">{{ card.label }}</span>
                <strong class="kpi-value">{{ card.value }}</strong>
              </button>
            </section>

            <NAlert v-if="annual?.issues.length" type="warning"
              >年度数据有 {{ annual.issues.length }} 条历史核对缺口，请先核对年度统计。</NAlert
            >
            <section class="overview-grid business-dynamics">
              <div class="home-main">
                <div class="chart-grid">
                  <section class="home-panel">
                    <div class="panel-head"><strong>藏品类别</strong></div>
                    <HEChart
                      v-if="categoryRows.length"
                      :option="
                        categoryRows.length <= 8 ? pieOption('类别', categoryRows) : barOption('类别', categoryRows)
                      "
                      style="height: 260px"
                    />
                    <NEmpty v-else :description="unavailable('stat') ? '数据暂不可用' : '暂无类别数据'" />
                  </section>
                  <section class="home-panel">
                    <div class="panel-head"><strong>文物藏品级别</strong></div>
                    <HEChart
                      v-if="relicLevelRows.length"
                      :option="pieOption('文物级别', relicLevelRows)"
                      style="height: 260px"
                    />
                    <NEmpty v-else :description="unavailable('stat') ? '数据暂不可用' : '暂无文物级别数据'" />
                  </section>
                </div>
                <CollectionStorageOverview :usage="usage" />
                <div class="home-panel ongoing-panel">
                  <div class="panel-head">
                    <div>
                      <span>在批单据</span>
                      <strong>{{ metric('ongoing', ongoing.length) }}</strong>
                    </div>
                  </div>
                  <div v-if="ongoing.length" class="recent-list">
                    <button
                      v-for="item in ongoing"
                      :key="item.bizType + item.id"
                      type="button"
                      class="recent-row ongoing-row"
                      @click="goOngoing(item)"
                    >
                      <NTag size="small" type="info" class="flow-kind">{{ item.bizName }}</NTag>
                      <span class="recent-name" :title="item.title || item.id">{{ item.title || item.id }}</span>
                      <span class="recent-meta">{{ item.applicantName || item.applicant || '--' }}</span>
                      <NTag size="small" type="warning" class="flow-stage" :bordered="false">{{
                        item.stage || '审核中'
                      }}</NTag>
                      <span class="recent-meta">{{ formatTime(item.createTime) }}</span>
                    </button>
                  </div>
                  <NEmpty
                    v-else
                    :description="unavailable('ongoing') ? '数据暂不可用' : '当前没有在批的出入库/业务流程'"
                    size="small"
                  />
                </div>
              </div>
              <aside class="home-side">
                <div class="home-panel work-panel">
                  <div class="panel-head">
                    <div><span>工作待办</span></div>
                    <NButton text type="primary" @click="go('/collection/stat')">统计概览</NButton>
                  </div>
                  <div class="home-work-grid">
                    <button
                      v-for="item in workItems"
                      :key="item.key"
                      type="button"
                      class="work-row"
                      @click="go(item.path)"
                    >
                      <span class="work-dot" :class="item.tone" />
                      <span class="work-title">{{ item.label }}</span>
                      <strong>{{ item.value }}</strong>
                    </button>
                  </div>
                </div>

                <CollectionWorkbenchTasks />
                <CollectionWorkbenchActions />
              </aside>
            </section>
            <section class="chart-grid">
              <div class="home-panel">
                <div class="panel-head">
                  <span>出入库记录</span>
                  <NButton text type="primary" @click="go('/collection/movement')">查看全部</NButton>
                </div>
                <NDataTable
                  v-if="!unavailable('movements') && recentMovements.length"
                  :columns="movementColumns"
                  :data="recentMovements"
                  :row-key="(row: MovementVo) => row.id!"
                  :pagination="false"
                  :scroll-x="720"
                  size="small"
                />
                <NEmpty
                  v-else
                  :description="unavailable('movements') ? '数据暂不可用' : '暂无出入库记录'"
                  size="small"
                />
              </div>
              <div class="home-panel">
                <div class="panel-head">
                  <span>编目审核记录</span>
                  <NButton text type="primary" @click="go('/collection/catalog-audit?status=ALL')">查看全部</NButton>
                </div>
                <NDataTable
                  v-if="!unavailable('auditRecords') && recentAudits.length"
                  :columns="auditColumns"
                  :data="recentAudits"
                  :row-key="(row: CollectionCatalogVo) => row.collectionId!"
                  :pagination="false"
                  :scroll-x="560"
                  size="small"
                />
                <NEmpty
                  v-else
                  :description="unavailable('auditRecords') ? '数据暂不可用' : '暂无审核记录'"
                  size="small"
                />
              </div>
            </section>
            <CollectionWorkflowNavigation />
          </div>
        </NTabPane>
        <NTabPane name="statistics" tab="统计分析">
          <div class="home-tab-content">
            <!-- 总账分类核对 -->
            <div class="statistics-toolbar">
              <CollectionFieldLabel label="在册藏品" help="以下分类以正式登账且未退出的记录为范围，每条记录计一次。" />
              <NButton text type="primary" @click="go('/collection/ledger?registrationScope=DEREGISTERED')">
                退出馆藏 {{ metric('stat', stat.retiredTotal ?? 0) }} 条
              </NButton>
            </div>
            <section class="chart-grid">
              <div class="home-panel">
                <div class="panel-head"><span>藏品类别</span></div>
                <HEChart v-if="categoryRows.length" :option="barOption('类别', categoryRows)" style="height: 240px" />
                <NEmpty v-else :description="unavailable('stat') ? '数据暂不可用' : '暂无数据'" size="small" />
              </div>
              <div class="home-panel">
                <div class="panel-head"><span>文物藏品级别</span></div>
                <HEChart
                  v-if="relicLevelRows.length"
                  :option="barOption('级别', relicLevelRows)"
                  style="height: 240px"
                />
                <NEmpty
                  v-else
                  :description="unavailable('stat') || !hasRelicBreakdown ? '数据暂不可用' : '暂无文物级别数据'"
                  size="small"
                />
              </div>
              <div class="home-panel">
                <div class="panel-head"><span>藏品来源</span></div>
                <HEChart v-if="sourceRows.length" :option="pieOption('来源', sourceRows)" style="height: 240px" />
                <NEmpty v-else :description="unavailable('stat') ? '数据暂不可用' : '暂无数据'" size="small" />
              </div>
              <div class="home-panel">
                <div class="panel-head"><span>登记的存放位置</span></div>
                <HEChart v-if="warehouseRows.length" :option="barOption('位置', warehouseRows)" style="height: 240px" />
                <NEmpty v-else :description="unavailable('stat') ? '数据暂不可用' : '暂无数据'" size="small" />
              </div>
              <div class="home-panel">
                <div class="panel-head"><span>年代分布</span></div>
                <HEChart v-if="eraRows.length" :option="barOption('年代', eraRows)" style="height: 240px" />
                <NEmpty v-else :description="unavailable('era') ? '数据暂不可用' : '暂无年代数据'" size="small" />
              </div>
              <div class="home-panel">
                <div class="panel-head"><span>出入库时间趋势（近 12 个月）</span></div>
                <HEChart v-if="movementTrend.length" :option="movementTrendOption" style="height: 240px" />
                <NEmpty
                  v-else
                  :description="unavailable('trend') ? '数据暂不可用' : '暂无出入库趋势数据'"
                  size="small"
                />
              </div>
            </section>

            <section class="home-panel annual-panel">
              <div class="panel-head">
                <div>
                  <span>{{ currentYear }} 年度藏品统计（当前馆）</span>
                </div>
                <NButton text type="primary" @click="go('/collection/annual-report')">核对年度统计</NButton>
              </div>
              <NAlert v-if="annual && annual.issues.length" type="warning" class="annual-issues">
                有 {{ annual.issues.length }} 条历史核对缺口，计算结果不能作为完整年度报送数。
              </NAlert>
              <div v-if="unavailable('annual')" class="annual-empty">年度数据暂不可用，请到年度统计页核对。</div>
              <div v-else class="annual-grid">
                <div v-for="item in annualFigures" :key="item.label" class="annual-figure">
                  <span>{{ item.label }}</span
                  ><strong>{{ item.value }}</strong>
                </div>
              </div>
            </section>
          </div>
        </NTabPane>
        <NTabPane name="organization" tab="总分馆概况" display-directive="show:lazy">
          <CollectionOrgStructurePanel embedded />
        </NTabPane>
      </NTabs>
    </main>
  </NSpin>
</template>

<script setup lang="ts">
import CollectionFieldLabel from '../components/CollectionFieldLabel.vue';
import CollectionStorageOverview from '../components/CollectionStorageOverview.vue';
import { quantityText } from '../utils/collectionAnalysis';
import type { CollectionUsageSummary } from '../typings/collection';
import { computed, h, onMounted, ref, watch } from 'vue';
import UserText from '@/modules/sys/components/UserText.vue';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { cloneDeep } from 'lodash-es';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { getNodeByParam } from '@/utils/tree';
import { NDataTable, NTag, NTabs, NTabPane, type DataTableColumns } from 'naive-ui';
import HEChart from '@/components/data/chart/EChart.vue';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { collectionApi } from '../api/collection';
import { catalogApi } from '../api/catalog';
import { formatPrintDateTime } from '../utils/printCollectionList';
import { restorationApi } from '@/modules/collection-restoration/api/restoration';
import type { RestorationWorkSummary } from '@/modules/collection-restoration/typings';
import CollectionOrgStructurePanel from '../components/CollectionOrgStructurePanel.vue';
import CollectionWorkflowNavigation from '../components/CollectionWorkflowNavigation.vue';
import CollectionWorkbenchActions from '../components/CollectionWorkbenchActions.vue';
import CollectionWorkbenchTasks from '../components/CollectionWorkbenchTasks.vue';
import type {
  AnnualStatVo,
  CatalogAuditSummaryVo,
  CollectionCatalogVo,
  MovementTrendVo,
  MovementVo,
  OngoingFlowVo,
} from '../typings/collection';

type StatSummary = {
  total: number;
  pendingRegistrationTotal?: number;
  unverifiedRegistrationTotal?: number;
  retiredTotal?: number;
  ungradedRelicTotal?: number;
  byStatus: Record<string, number>;
  bySource: Record<string, number>;
  byCategory: Record<string, number>;
  byWarehouse: Record<string, number>;
  byNature?: Record<string, number>;
  byRelicLevel?: Record<string, number>;
  quantityByUnit?: Record<string, number>;
  quantityUnverifiedTotal?: number;
};

type HomeLoadKey =
  | 'stat'
  | 'annual'
  | 'audit'
  | 'ongoing'
  | 'usage'
  | 'restorationWork'
  | 'era'
  | 'trend'
  | 'movements'
  | 'auditRecords';

const homeLoadLabels: Record<HomeLoadKey, string> = {
  stat: '藏品总登记账统计',
  annual: '年度藏品统计',
  audit: '编目审核',
  ongoing: '在批单据',
  usage: '当前外借与实际展出',
  restorationWork: '本馆修复与实际提用统计',
  era: '年代分布',
  trend: '出入库时间趋势',
  movements: '藏品出入库记录表',
  auditRecords: '藏品审核记录及结果表',
};

const router = useRouter();
const auth = useAuthStore();
const currentYear = new Date().getFullYear();
const activeTab = ref('overview');
const stat = ref<StatSummary>({
  total: 0,
  byStatus: {},
  bySource: {},
  byCategory: {},
  byWarehouse: {},
  byNature: {},
  byRelicLevel: {},
  quantityByUnit: {},
  quantityUnverifiedTotal: 0,
});
const restorationWorkSummary = ref<RestorationWorkSummary | null>(null);
const annual = ref<AnnualStatVo | null>(null);
const auditSummary = ref<CatalogAuditSummaryVo>({ draft: 0, pending: 0, approved: 0, rejected: 0, passRate: 0 });
const ongoing = ref<OngoingFlowVo[]>([]);
const usage = ref<CollectionUsageSummary | null>(null);
const byEra = ref<Record<string, number>>({});
const movementTrend = ref<MovementTrendVo[]>([]);
const recentMovements = ref<MovementVo[]>([]);
const recentAudits = ref<CollectionCatalogVo[]>([]);
const dictLabels = ref<Record<string, Record<string, string>>>({});
const homeLoadErrors = ref<HomeLoadKey[]>([]);
const session = useCatalogOperation(clearHomeData);
const pageActive = session.active;
const loading = session.busy;
function clearHomeData() {
  stat.value = {
    total: 0,
    byStatus: {},
    bySource: {},
    byCategory: {},
    byWarehouse: {},
    byNature: {},
    byRelicLevel: {},
    quantityByUnit: {},
    quantityUnverifiedTotal: 0,
  };
  restorationWorkSummary.value = null;
  annual.value = null;
  auditSummary.value = { draft: 0, pending: 0, approved: 0, rejected: 0, passRate: 0 };
  ongoing.value = [];
  usage.value = null;
  byEra.value = {};
  movementTrend.value = [];
  recentMovements.value = [];
  recentAudits.value = [];
  dictLabels.value = {};
  homeLoadErrors.value = [];
}
function unavailable(key: HomeLoadKey) {
  return homeLoadErrors.value.includes(key);
}
function metric(key: HomeLoadKey, value: string | number) {
  return unavailable(key) ? '--' : value;
}

const byStatus = computed(() => stat.value.byStatus ?? {});
const hasRelicBreakdown = computed(() => !!stat.value.byNature && !!stat.value.byRelicLevel);
const hasQuantityBreakdown = computed(() => !!stat.value.quantityByUnit && count(stat.value.quantityUnverifiedTotal));
const quantityRows = computed(() =>
  Object.entries(stat.value.quantityByUnit ?? {})
    .map(([unit, quantity]) => ({ unit, quantity }))
    .sort((a, b) => a.unit.localeCompare(b.unit, 'zh-Hans-CN')),
);
const annualFigures = computed(() => [
  { label: '期初记录数', value: metric('annual', annual.value?.openingTotal ?? '--') },
  { label: '新登记', value: metric('annual', annual.value?.newTotal ?? '--') },
  { label: '调入', value: metric('annual', annual.value?.transferInTotal ?? '--') },
  { label: '调出', value: metric('annual', annual.value?.transferOutTotal ?? '--') },
  { label: '退出馆藏', value: metric('annual', annual.value?.deregTotal ?? '--') },
  { label: '期末记录数', value: metric('annual', annual.value?.closingTotal ?? '--') },
]);

const registeredLedger = '/collection/ledger?registrationScope=REGISTERED';
function annualMetric(value: number | undefined) {
  return unavailable('annual') || !annual.value ? '--' : annual.value.issues.length ? '待核实' : (value ?? '--');
}
const kpiCards = computed(() => [
  {
    label: '藏品总量',
    value: metric('stat', `${stat.value.total} 件（套）`),
    note: unavailable('stat')
      ? '数据暂不可用'
      : `实际数量：${hasQuantityBreakdown.value ? quantityText(stat.value.quantityByUnit || {}, stat.value.quantityUnverifiedTotal ? '待核实' : '0') : '待核实'}${stat.value.quantityUnverifiedTotal ? `；${stat.value.quantityUnverifiedTotal} 条数量待核实` : ''}`,
    path: registeredLedger,
  },
  {
    label: '在库',
    value: metric('stat', `${byStatus.value.IN_STORE ?? 0} 件（套）`),
    note: unavailable('stat') ? '数据暂不可用' : '正式在册且状态为在库的藏品',
    path: registeredLedger + '&lifeStatus=IN_STORE',
  },
  {
    label: '当前外借',
    value: metric('usage', usage.value ? `${usage.value.loanCount} 件（套）` : '--'),
    note: usage.value
      ? `实际数量：${quantityText(usage.value.loanQuantityByUnit, usage.value.loanCount ? '待核实' : '0')}${usage.value.items.filter((i) => i.onLoan && (!i.unit?.trim() || Number(i.quantity) <= 0 || i.quantity == null)).length ? '；部分数量待核实' : ''}`
      : '数据暂不可用',
    path: registeredLedger + '&lifeStatus=ON_LOAN',
  },
  {
    label: '当前展出',
    value: metric(
      'usage',
      usage.value
        ? quantityText(usage.value.exhibitionQuantityByUnit, usage.value.exhibitionCount ? '待核实' : '0')
        : '--',
    ),
    note: usage.value
      ? `${usage.value.exhibitionCount} 条实际展出记录${usage.value.items.filter((i) => i.onExhibition && (!i.unit?.trim() || Number(i.quantity) <= 0 || i.quantity == null)).length ? '；部分数量待核实' : ''}`
      : '数据暂不可用',
    path: '/collection/exhibition',
  },
]);

const natureRows = computed(() => [
  {
    key: 'RELIC',
    label: '文物藏品',
    value: metric('stat', stat.value.byNature ? (stat.value.byNature.RELIC ?? 0) : '--'),
    path: registeredLedger + '&nature=RELIC',
  },
  {
    key: 'OTHER',
    label: '其他藏品',
    value: metric('stat', stat.value.byNature ? (stat.value.byNature.OTHER ?? 0) : '--'),
    path: registeredLedger + '&nature=OTHER',
  },
  {
    key: 'SCIENTIFIC_FOSSIL',
    label: '科学化石',
    value: metric('stat', stat.value.byNature ? (stat.value.byNature.SCIENTIFIC_FOSSIL ?? 0) : '--'),
    path: registeredLedger + '&nature=SCIENTIFIC_FOSSIL',
  },
]);

const workItems = computed(() => [
  {
    key: 'registration',
    label: '待办理总账登记',
    value: metric('stat', stat.value.pendingRegistrationTotal ?? '--'),
    tone: 'info',
    path: '/collection/ledger?registrationScope=PENDING',
  },
  {
    key: 'registration-unverified',
    label: '登记依据待核实',
    value: metric('stat', stat.value.unverifiedRegistrationTotal ?? '--'),
    tone: 'warning',
    path: '/collection/ledger?registrationScope=UNVERIFIED',
  },
  {
    key: 'ungraded',
    label: '未定级文物（含待登记）',
    value: metric('stat', stat.value.ungradedRelicTotal ?? '--'),
    tone: 'warning',
    path: '/collection/ledger?relicLevelScope=UNGRADED',
  },
  {
    key: 'catalog-draft',
    label: '待编目录入',
    value: metric('audit', auditSummary.value.draft),
    tone: 'info',
    path: '/collection/catalog-entry',
  },
  {
    key: 'catalog-review',
    label: '待编目审核',
    value: metric('audit', auditSummary.value.pending),
    tone: 'info',
    path: '/collection/catalog-audit',
  },
  {
    key: 'loan',
    label: '借出未归',
    value: metric('stat', byStatus.value.ON_LOAN ?? 0),
    tone: 'warning',
    path: registeredLedger + '&lifeStatus=ON_LOAN',
  },
  {
    key: 'repair',
    label: '修复中项目',
    value: metric('restorationWork', restorationWorkSummary.value?.activeRestorationProjects ?? '--'),
    tone: 'warning',
    path: '/collection-restoration/project',
  },
  {
    key: 'return-hold',
    label: '归还待核查',
    value: metric('stat', byStatus.value.RETURN_HOLD ?? 0),
    tone: 'warning',
    path: registeredLedger + '&lifeStatus=RETURN_HOLD',
  },
  {
    key: 'missing',
    label: '盘亏待处理',
    value: metric('stat', byStatus.value.MISSING ?? 0),
    tone: 'danger',
    path: registeredLedger + '&lifeStatus=MISSING',
  },
  {
    key: 'sent-repair',
    label: '送修未归',
    value: metric('restorationWork', restorationWorkSummary.value?.sentRestorationUnreturnedCollections ?? '--'),
    tone: 'warning',
    path: '/collection/requisition',
  },
  {
    key: 'inuse',
    label: '其他提用未归',
    value: metric('restorationWork', restorationWorkSummary.value?.otherRequisitionUnreturnedCollections ?? '--'),
    tone: 'primary',
    path: '/collection/requisition',
  },
]);

const categoryRows = computed(() => distRows(stat.value.byCategory));
const relicLevelRows = computed(() => distRows(stat.value.byRelicLevel, 'col_relic_level'));
const sourceRows = computed(() => distRows(stat.value.bySource, 'col_source'));
const warehouseRows = computed(() => distRows(stat.value.byWarehouse));
const eraRows = computed(() => distRows(byEra.value));
const movementTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { bottom: 0 },
  grid: { left: 36, right: 16, top: 30, bottom: 52, containLabel: true },
  xAxis: { type: 'category', data: movementTrend.value.map((row) => row.month) },
  yAxis: { type: 'value', name: '流水条数', minInterval: 1 },
  series: [
    { name: '入库', type: 'line', data: movementTrend.value.map((row) => row.inCount) },
    { name: '出库', type: 'line', data: movementTrend.value.map((row) => row.outCount) },
  ],
}));
const movementColumns: DataTableColumns<MovementVo> = [
  {
    title: '单号',
    key: 'documentNo',
    width: 140,
    ellipsis: { tooltip: true },
    render: (row) => row.documentNo || '--',
  },
  {
    title: '藏品',
    key: 'collectionName',
    width: 180,
    ellipsis: { tooltip: true },
    render: (row) => row.collectionName || row.totalRegNo || row.collectionId || '--',
  },
  { title: '类型', key: 'type', width: 70, render: (row) => (row.type === 'IN' ? '入库' : '出库') },
  {
    title: '经办人',
    key: 'operator',
    width: 160,
    ellipsis: { tooltip: true },
    render: (row) => h(UserText, { value: row.operator, name: row.operatorName }),
  },
  { title: '时间', key: 'operatedAt', width: 170, render: (row) => formatPrintDateTime(row.operatedAt) || '--' },
];
const auditColumns: DataTableColumns<CollectionCatalogVo> = [
  { title: '藏品', key: 'collectionName', width: 180, ellipsis: { tooltip: true }, render: catalogApi.displayName },
  { title: '审核人', key: 'auditBy', width: 140, ellipsis: { tooltip: true }, render: (row) => h(UserText, { value: row.auditBy }) },
  { title: '结果', key: 'status', width: 70, render: (row) => (row.status === 'APPROVED' ? '通过' : '驳回') },
  { title: '时间', key: 'auditTime', width: 170, render: (row) => formatPrintDateTime(row.auditTime ?? row.updateTime) || '--' },
];

async function loadRecentAudits() {
  const statuses = ['APPROVED', 'REJECTED'] as const;
  const responses = await Promise.all(statuses.map((status) => collectionApi.workbenchCatalogAudits(status)));
  const rows: CollectionCatalogVo[] = [];
  responses.forEach((response: any, index) => {
    if (
      (response?.status !== undefined && response.status !== 200) ||
      !validPayload('auditRecords', response?.data) ||
      response.data.list.some((row: CollectionCatalogVo) => row.status !== statuses[index])
    ) {
      throw new Error('编目审核记录数据暂不可用');
    }
    rows.push(...response.data.list);
  });
  // 后端时间为毫秒数或字符串；按审核时间倒序，缺失时用更新时间兜底。
  rows.sort((a, b) => (timeValue(b.auditTime ?? b.updateTime) ?? 0) - (timeValue(a.auditTime ?? a.updateTime) ?? 0));
  return { data: { list: rows.slice(0, 6) } };
}

const ongoingRoutes: Record<string, string> = {
  COLLECTION_ACQUISITION: '/collection/acquisition',
  COLLECTION_ACQUISITION_PLAN: '/collection/acquisition-plan',
  COLLECTION_APPRAISAL: '/collection/appraisal',
  COLLECTION_REQUISITION: '/collection/requisition',
  COLLECTION_LOAN: '/collection/loan',
  COLLECTION_RETURN: '/collection/return',
  COLLECTION_TRANSFER: '/collection/transfer',
  COLLECTION_DEREGISTER: '/collection/deregistration',
  COLLECTION_INVENTORY: '/collection/inventory',
  COLLECTION_EXHIBITION: '/collection/exhibition',
  COLLECTION_REPRODUCTION: '/collection/reproduction',
  COLLECTION_SAMPLING: '/collection/sampling',
  COLLECTION_WAREHOUSE_ENTRY: '/collection/warehouse-entry',
  COLLECTION_RESTORATION: '/collection-restoration/project',
};

onMounted(loadHomeData);
// Nested KeepAlive hooks can run before the session is active; reload after its actual return.
watch(
  pageActive,
  (active, wasActive) => {
    if (active && !wasActive) void loadHomeData();
  },
  { flush: 'post' },
);

async function loadHomeData() {
  if (!pageActive.value) return;
  session.reset();
  const ticket = session.begin('load');
  if (ticket === null) return;
  try {
    const results = await Promise.allSettled([
      collectionApi.statSummary(true),
      collectionApi.annualStat(currentYear),
      collectionApi.auditSummary(),
      collectionApi.ongoing(12),
      collectionApi.usageSummary(),
      restorationApi.workSummary(),
      collectionApi.movementTrend(12, true),
      collectionApi.workbenchMovements(),
      loadRecentAudits(),
    ]);
    if (!session.current(ticket)) return;
    const failures: HomeLoadKey[] = [];
    applySettled(results[0], 'stat', (data) => (stat.value = data), failures);
    applySettled(results[1], 'annual', (data) => (annual.value = data), failures);
    applySettled(results[2], 'audit', (data) => (auditSummary.value = data), failures);
    applySettled(results[3], 'ongoing', (data) => (ongoing.value = data), failures);
    applySettled(results[4], 'usage', (data) => (usage.value = data), failures);
    applySettled(results[5], 'restorationWork', (data) => (restorationWorkSummary.value = data), failures);
    // 年代单独校验，缺失或格式错误时不影响原有统计区块。
    applySettled(results[0], 'era', (data) => (byEra.value = data.byEra), failures);
    applySettled(
      results[6],
      'trend',
      (data) =>
        (movementTrend.value = data.sort((a: MovementTrendVo, b: MovementTrendVo) => a.month.localeCompare(b.month))),
      failures,
    );
    applySettled(results[7], 'movements', (data) =>
        (recentMovements.value = [...data.list]
          .sort((a: MovementVo, b: MovementVo) => (timeValue(b.operatedAt) ?? 0) - (timeValue(a.operatedAt) ?? 0))
          .slice(0, 6)), failures);
    applySettled(results[8], 'auditRecords', (data) => (recentAudits.value = data.list), failures);
    homeLoadErrors.value = failures;
    const labels = await loadDictLabels(['col_source', 'col_relic_level']);
    if (session.current(ticket)) dictLabels.value = labels;
  } finally {
    session.finish(ticket);
  }
}
const object = (v: any) => v != null && typeof v === 'object' && !Array.isArray(v);
const count = (v: any) => Number.isSafeInteger(v) && v >= 0;
const text = (v: any) => typeof v === 'string' && !!v.trim();
const counts = (v: any) => object(v) && Object.values(v).every(count);
const optionalText = (v: any) => v == null || typeof v === 'string';
function timeValue(v: unknown): number | null {
  if (typeof v === 'number') return Number.isFinite(v) ? v : null;
  if (typeof v !== 'string' || !v.trim()) return null;
  const t = v.trim();
  if (/^\d+$/.test(t)) return Number(t);
  const parsed = Date.parse(t.replace(' ', 'T'));
  return Number.isFinite(parsed) ? parsed : null;
}
const dateText = (v: any) => timeValue(v) !== null;
function validPayload(key: HomeLoadKey, data: any) {
  if (key === 'era') return object(data) && counts(data.byEra);
  if (key === 'trend')
    return (
      Array.isArray(data) &&
      data.length <= 12 &&
      data.every(
        (row) =>
          object(row) &&
          text(row.month) &&
          /^\d{4}-(0[1-9]|1[0-2])$/.test(row.month) &&
          count(row.inCount) &&
          count(row.outCount),
      ) &&
      new Set(data.map((row) => row.month)).size === data.length
    );
  if (key === 'movements')
    return (
      object(data) &&
      Array.isArray(data.list) &&
      data.list.every(
        (row: any) =>
          object(row) &&
          text(row.id) &&
          text(row.collectionId) &&
          ['IN', 'OUT'].includes(row.type) &&
          (row.operatedAt == null || (count(row.operatedAt) && Number.isFinite(new Date(row.operatedAt).getTime()))) &&
          ['documentNo', 'collectionName', 'totalRegNo', 'operator', 'operatorName'].every((k) => optionalText(row[k])),
      )
    );
  if (key === 'auditRecords')
    return (
      object(data) &&
      Array.isArray(data.list) &&
      data.list.every(
        (row: any) =>
          object(row) &&
          text(row.collectionId) &&
          object(row.content) &&
          ['APPROVED', 'REJECTED'].includes(row.status) &&
          ['formalName', 'totalName'].every((k) => optionalText(row.content[k])) &&
          ['collectionName', 'auditBy'].every((k) => optionalText(row[k])) &&
          dateText(row.updateTime) &&
          (row.auditTime == null || dateText(row.auditTime)),
      )
    );
  if (key === 'stat')
    return (
      object(data) &&
      ['total', 'pendingRegistrationTotal', 'unverifiedRegistrationTotal', 'retiredTotal'].every((k) =>
        count(data[k]),
      ) &&
      ['byStatus', 'bySource', 'byCategory', 'byWarehouse'].every((k) => counts(data[k])) &&
      (data.byNature === undefined || counts(data.byNature)) &&
      (data.byRelicLevel === undefined || counts(data.byRelicLevel)) &&
      (data.quantityByUnit === undefined || counts(data.quantityByUnit)) &&
      (data.quantityUnverifiedTotal === undefined || count(data.quantityUnverifiedTotal)) &&
      (data.ungradedRelicTotal === undefined || count(data.ungradedRelicTotal))
    );
  if (key === 'restorationWork')
    return (
      object(data) &&
      text(data.orgId) &&
      [
        'activeRestorationProjects',
        'sentRestorationUnreturnedCollections',
        'otherRequisitionUnreturnedCollections',
      ].every((k) => count(data[k]))
    );
  if (key === 'annual')
    return (
      object(data) &&
      data.year === currentYear &&
      typeof data.orgId === 'string' &&
      ['openingTotal', 'newTotal', 'transferInTotal', 'transferOutTotal', 'deregTotal', 'closingTotal'].every((k) =>
        count(data[k]),
      ) &&
      Array.isArray(data.issues)
    );
  if (key === 'audit')
    return (
      object(data) &&
      ['draft', 'pending', 'approved', 'rejected', 'passRate'].every((k) => count(data[k])) &&
      data.passRate <= 100
    );
  if (key === 'usage')
    return (
      object(data) &&
      ['total', 'loanCount', 'exhibitionCount', 'quantityUnverifiedTotal'].every((k) => count(data[k])) &&
      ['quantityByUnit', 'loanQuantityByUnit', 'exhibitionQuantityByUnit'].every((k) => counts(data[k])) &&
      Array.isArray(data.items) &&
      data.total === data.items.length
    );
  if (key === 'ongoing')
    return Array.isArray(data) && data.every((r) => object(r) && text(r.id) && text(r.bizType) && text(r.bizName));
  return (
    object(data) && Array.isArray(data.list) && data.list.every((r: any) => object(r) && text(r.id) && text(r.orgId))
  );
}
function applySettled(
  result: PromiseSettledResult<any>,
  key: HomeLoadKey,
  apply: (payload: any) => void,
  failures: HomeLoadKey[],
) {
  if (
    result.status === 'fulfilled' &&
    (!['stat', 'restorationWork', 'era', 'trend', 'movements', 'auditRecords'].includes(key) ||
      result.value?.status === undefined ||
      result.value.status === 200) &&
    validPayload(key, result.value?.data)
  )
    apply(cloneDeep(result.value.data));
  else failures.push(key);
}
async function loadDictLabels(types: string[]) {
  const entries = await Promise.all(
    types.map(async (type) => {
      try {
        const resp: any = await fetchDictType(type, true);
        if (!Array.isArray(resp?.data)) return [type, {}] as const;
        return [
          type,
          Object.fromEntries(resp.data.map((i: any) => [String(i.value ?? ''), String(i.label ?? i.value ?? '')])),
        ] as const;
      } catch {
        return [type, {}] as const;
      }
    }),
  );
  return Object.fromEntries(entries);
}

function distRows(map: Record<string, number> | undefined, dictType?: string) {
  const rows = Object.entries(map ?? {})
    .map(([key, value]) => ({ name: dictType ? dictLabels.value[dictType]?.[key] || key : key, value }))
    .sort((a, b) => b.value - a.value);
  if (rows.length <= 12) return rows;
  return [
    ...rows.slice(0, 11),
    { name: `其余 ${rows.length - 11} 项`, value: rows.slice(11).reduce((sum, row) => sum + row.value, 0) },
  ];
}

function pieOption(title: string, rows: { name: string; value: number }[]) {
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{ name: title, type: 'pie', radius: ['44%', '68%'], center: ['50%', '42%'], data: rows }],
  };
}

function barOption(title: string, rows: { name: string; value: number }[]) {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 36, right: 16, top: 24, bottom: 48, containLabel: true },
    xAxis: {
      type: 'category',
      data: rows.map((r) => r.name),
      axisLabel: { interval: 0, rotate: rows.length > 5 ? 30 : 0 },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ name: title, type: 'bar', barMaxWidth: 32, data: rows.map((r) => r.value) }],
  };
}

function formatTime(value?: string) {
  if (!value) return '--';
  return String(value).replace('T', ' ').slice(0, 16);
}

function goOngoing(item: OngoingFlowVo) {
  go(ongoingRoutes[item.bizType] || '/collection/ledger');
}

function canNavigate(path: string) {
  return Boolean(getNodeByParam(auth.menus, router.resolve(path).path, 'path'));
}

function go(path: string) {
  if (!pageActive.value) return;
  const target = router.resolve(path);
  const menu = getNodeByParam(auth.menus, target.path, 'path');
  router.push(
    menu
      ? { path: target.path, query: { ...target.query, menuId: menu.id, title: menu.name }, hash: target.hash }
      : path,
  );
}
watch(
  () => [auth.token, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, auth.roles, auth.permissions],
  () => {
    session.reset();
    if (auth.token && !auth.identitySyncing) void loadHomeData();
  },
  { deep: true, flush: 'sync' },
);
</script>

<style scoped>
.collection-home {
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  padding: 16px;
  color: #172033;
  container: collection-workbench / inline-size;
}

.home-tabs {
  min-width: 0;
}
.home-tab-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(240px, 1fr);
  align-items: start;
  gap: 14px;
}
.work-panel {
  container: collection-work-items / inline-size;
}
.home-work-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.business-dynamics .recent-list {
  max-height: 240px;
  overflow-y: auto;
  scrollbar-gutter: stable;
}

.home-header,
.home-panel,
.kpi-card {
  border: 1px solid #dbe5ef;
  border-radius: 8px;
  background: #fff;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
}

.home-kicker {
  color: #08756f;
  font-size: 13px;
  font-weight: 700;
}

.home-title-block h1 {
  margin: 5px 0 0;
  font-size: 26px;
  font-weight: 750;
  line-height: 1.25;
}

.home-title-block p {
  margin: 8px 0 0;
  color: #6b778c;
  font-size: 13px;
}

.kpi-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.kpi-toolbar strong {
  font-size: 15px;
}
.scope-popover {
  max-width: calc(100vw - 48px);
  font-size: 13px;
  line-height: 1.5;
}
.scope-popover p {
  margin: 8px 0;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.quantity-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  color: #6b778c;
  font-size: 13px;
}

.quantity-values {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.quantity-note {
  flex-basis: 100%;
}

.quantity-panel strong {
  color: #172033;
}
.quantity-item {
  padding: 4px 10px;
  border-radius: 5px;
  background: #edf5f4;
  color: #08756f;
  font-weight: 700;
}
.quantity-note {
  color: #a16207;
}

.statistics-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px 16px;
  color: #6b778c;
  font-size: 13px;
}
.annual-issues {
  margin-bottom: 12px;
}
.annual-empty {
  padding: 12px 0;
  color: #6b778c;
  font-size: 13px;
}
.annual-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
}
.annual-figure {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px;
  border-radius: 6px;
  background: #f5f8fb;
}
.annual-figure span {
  color: #6b778c;
  font-size: 12px;
}
.annual-figure strong {
  color: #172033;
  font-size: 20px;
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  cursor: pointer;
}

.kpi-label {
  color: #6b778c;
  font-size: 13px;
}

.kpi-value {
  font-size: 26px;
  font-weight: 750;
}

.kpi-note {
  color: #6b778c;
  font-size: 12px;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.home-main,
.home-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.home-main {
  container: collection-main / inline-size;
}
.home-side {
  container: collection-side / inline-size;
}

.home-panel {
  min-width: 0;
  padding: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-head div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.panel-head span {
  font-weight: 700;
}

.panel-head strong {
  color: #6b778c;
  font-size: 13px;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.recent-row,
.work-row {
  display: grid;
  align-items: center;
  min-height: 42px;
  border: 1px solid #e5ecf4;
  border-radius: 6px;
  background: #f9fbfd;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.recent-row {
  grid-template-columns: minmax(0, 1.2fr) minmax(110px, 0.6fr) 90px;
  gap: 10px;
  padding: 8px 10px;
}

.ongoing-row {
  grid-template-columns: 100px minmax(0, 1.2fr) minmax(70px, 0.5fr) 100px 120px;
}

.recent-name {
  overflow: hidden;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  color: #6b778c;
  font-size: 12px;
}

.work-row {
  grid-template-columns: 12px minmax(0, 1fr) auto;
  gap: 8px;
  padding: 10px;
}

.work-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2b6eea;
}

.work-dot.info {
  background: #08756f;
}
.work-dot.warning {
  background: #d97706;
}
.work-dot.danger {
  background: #dc2626;
}
.work-dot.primary {
  background: #2b6eea;
}

@container collection-workbench (max-width: 1100px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .annual-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .home-header {
    align-items: flex-start;
    flex-direction: column;
  }
}

@container collection-workbench (max-width: 900px) {
  .overview-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@container collection-workbench (max-width: 640px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .annual-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container collection-main (max-width: 700px) {
  .ongoing-row {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 6px 10px;
  }
  .flow-kind {
    grid-column: 1;
    grid-row: 1;
    justify-self: start;
  }
  .flow-stage {
    grid-column: 2;
    grid-row: 1;
    justify-self: end;
  }
  .ongoing-row .recent-name {
    grid-column: 1 / -1;
    grid-row: 2;
  }
  .ongoing-row .recent-meta {
    grid-column: 1;
    grid-row: 3;
  }
  .ongoing-row .recent-meta:last-child {
    grid-column: 2;
  }
}

@container collection-side (max-width: 380px) {
  .recent-panel .recent-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
  .recent-panel .recent-name {
    grid-column: 1 / -1;
  }
}

@container collection-work-items (max-width: 640px) {
  .home-work-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@container collection-work-items (max-width: 380px) {
  .home-work-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 640px) {
  .collection-home {
    padding: 10px;
  }
}
.work-panel .home-work-grid {
  grid-template-columns: minmax(0, 1fr);
  gap: 4px;
}
.work-panel .work-row {
  padding: 6px 8px;
}
</style>

<style scoped>
button.kpi-card {
  border: 1px solid var(--n-border-color, #e5e7eb);
  background: #fff;
  color: #1f2937;
  text-align: left;
  font: inherit;
}
button.kpi-card:focus-visible {
  outline: 2px solid #2080f0;
  outline-offset: 2px;
}
.kpi-value {
  overflow-wrap: anywhere;
}
.quantity-panel .home-work-grid {
  grid-template-columns: 1fr;
  margin-top: 12px;
}
.composition-row {
  display: flex;
  justify-content: space-between;
}
button.kpi-card:hover {
  border-color: #2080f0;
  background: #f8fafc;
}
</style>
