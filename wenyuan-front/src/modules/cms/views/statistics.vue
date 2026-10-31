<template>
  <main class="cms-statistics" :style="statisticsTheme">
    <header class="statistics-heading">
      <h1>统计分析</h1>
      <div class="heading-actions">
        <n-button v-if="canExport" size="small" secondary @click="exportTasks?.open()"
          ><template #icon
            ><n-icon><TasksIcon /></n-icon></template
          >导出任务</n-button
        >
        <n-tooltip
          ><template #trigger
            ><n-button size="small" quaternary circle aria-label="刷新统计" :loading="loading" @click="load"
              ><template #icon
                ><n-icon><RefreshIcon /></n-icon></template></n-button></template
          >刷新统计</n-tooltip
        >
      </div>
    </header>
    <n-tabs
      v-model:value="section"
      type="line"
      class="statistics-nav"
      @update:value="
        page = 1;
        load();
      "
    >
      <n-tab name="overview">内容概况</n-tab><n-tab name="contents">内容与质量</n-tab
      ><n-tab name="feedback">调查统计</n-tab><n-tab name="traffic">网站访问</n-tab
      ><n-tab name="effectiveness">传播效果</n-tab><n-tab name="publishing">编辑与发布</n-tab>
    </n-tabs>
    <section class="filter-panel" aria-label="统计筛选">
      <n-form
        label-placement="left"
        :label-width="32"
        size="small"
        :show-feedback="false"
        class="statistics-filters"
        :class="{ 'has-date': showDateRange }"
      >
        <n-form-item label="站点"
          ><n-select
            v-model:value="filters.siteId"
            :options="sites"
            label-field="name"
            value-field="id"
            clearable
            aria-label="筛选站点"
            placeholder="全部站点"
        /></n-form-item>
        <n-form-item label="栏目"
          ><n-select
            v-model:value="filters.categoryId"
            :options="categories"
            label-field="name"
            value-field="id"
            filterable
            clearable
            aria-label="筛选栏目"
            placeholder="全部栏目"
        /></n-form-item>
        <n-form-item v-if="section !== 'feedback'" label="类型"
          ><n-select
            v-model:value="filters.module"
            :options="moduleOptions"
            clearable
            aria-label="内容类型"
            placeholder="全部类型"
        /></n-form-item>
        <n-form-item v-else label="问卷"
          ><n-select
            v-model:value="filters.feedbackId"
            :options="feedbackForms"
            label-field="name"
            value-field="id"
            :loading="formsLoading"
            remote
            filterable
            clearable
            aria-label="筛选问卷"
            placeholder="搜索问卷"
            @search="searchForms"
            @update:value="page = 1"
        /></n-form-item>
        <n-form-item v-if="showDateRange" label="时间" class="date-filter"
          ><n-date-picker v-model:value="dateRange" type="daterange" clearable class="date-range"
        /></n-form-item>
        <n-form-item v-if="section === 'feedback'">
          <n-checkbox v-model:checked="showReplyStats" @update:checked="toggleReplyStats">显示回复数据</n-checkbox>
        </n-form-item>
        <n-button
          type="primary"
          size="small"
          :loading="loading"
          class="filter-query"
          @click="
            page = 1;
            load();
          "
          ><template #icon
            ><n-icon><SearchIcon /></n-icon></template
          >查询</n-button
        >
      </n-form>
    </section>
    <n-spin :show="loading" class="statistics-results">
      <div class="section-stack">
        <n-alert v-if="error" type="error">{{ error }}</n-alert>
        <div v-if="sectionMetrics.length" class="metric-grid">
          <article v-for="card in sectionMetrics" :key="card.label" class="metric-card">
            <div class="metric-top">
              <span class="metric-label">{{ card.label }}</span
              ><span
                class="metric-icon"
                :style="{
                  color: `var(--cms-${card.tone})`,
                  background: `color-mix(in srgb, var(--cms-${card.tone}) 9%, transparent)`,
                }"
                ><n-icon :size="18"><component :is="card.icon" /></n-icon
              ></span>
            </div>
            <div class="metric-bottom">
              <n-statistic :value="card.value" /><span v-if="card.detail" class="metric-detail">{{ card.detail }}</span>
            </div>
          </article>
        </div>
        <template v-if="section === 'overview'">
          <n-card title="内容分布" size="small" :bordered="false" class="stat-panel">
            <div class="distribution-layout">
              <StatBreakdown :rows="typeBreakdown" /><n-data-table
                size="small"
                :bordered="false"
                :columns="typeColumns"
                :data="typeRows"
                :max-height="260"
              />
            </div>
          </n-card>
          <div class="statistics-grid">
            <n-card title="空栏目" size="small" :bordered="false" class="stat-panel"
              ><template #header-extra
                ><span class="count-label">{{ data.emptyCategories?.length || 0 }} 个</span></template
              ><n-data-table
                v-if="data.emptyCategories?.length"
                size="small"
                :bordered="false"
                :columns="categoryColumns"
                :data="data.emptyCategories"
                :max-height="280" /><n-empty v-else size="small" description="暂无空栏目" class="panel-empty"
            /></n-card>
            <n-card title="长期未更新栏目" size="small" :bordered="false" class="stat-panel"
              ><template #header-extra
                ><span class="count-label">{{ data.staleCategories?.length || 0 }} 个</span></template
              ><n-data-table
                v-if="data.staleCategories?.length"
                size="small"
                :bordered="false"
                :columns="categoryColumns"
                :data="data.staleCategories"
                :max-height="280" /><n-empty
                v-else
                size="small"
                description="暂无超过 90 天未更新的栏目"
                class="panel-empty"
            /></n-card>
          </div>
        </template>
        <template v-if="section === 'contents'">
          <n-card title="内容明细" size="small" :bordered="false" class="stat-panel">
            <template #header-extra
              ><n-button v-if="canExport" size="small" secondary :loading="exporting" @click="download('contents')"
                ><template #icon
                  ><n-icon><ExportIcon /></n-icon></template
                >导出明细</n-button
              ></template
            >
            <div class="detail-filters">
              <n-select
                v-model:value="filters.state"
                :options="stateOptions"
                clearable
                aria-label="发布状态"
                placeholder="发布状态 / 回收站"
                class="detail-select"
              />
              <n-select
                v-model:value="filters.quality"
                :options="qualityOptions"
                clearable
                aria-label="质量问题"
                placeholder="质量问题"
                class="detail-select"
              />
              <n-input
                v-model:value="filters.title"
                clearable
                aria-label="内容标题"
                placeholder="搜索内容标题"
                class="detail-title"
              />
              <n-checkbox v-model:checked="filterContentDate">按时间筛选</n-checkbox>
              <n-select
                v-if="filterContentDate"
                v-model:value="filters.dateField"
                :options="dateOptions"
                aria-label="时间依据"
                class="detail-select"
              />
              <n-button
                size="small"
                @click="
                  page = 1;
                  load();
                "
                >筛选</n-button
              >
            </div>
            <n-data-table
              size="small"
              :bordered="false"
              :columns="contentColumns"
              :data="data.list || []"
              :row-key="(row) => row.id"
              :scroll-x="1290"
            />
            <div class="table-footer">
              <span class="count-label">共 {{ data.total || 0 }} 条内容</span
              ><n-pagination v-model:page="page" :page-size="20" :item-count="data.total || 0" @update:page="load()" />
            </div>
          </n-card>
          <n-card title="内容分布" size="small" :bordered="false" class="stat-panel">
            <template #header-extra
              ><div class="panel-actions">
                <n-select
                  v-model:value="dimension"
                  :options="dimensionOptions"
                  size="small"
                  aria-label="分布维度"
                  class="dimension-select"
                  @update:value="loadDistribution"
                /><n-button
                  v-if="canExport"
                  size="small"
                  secondary
                  :loading="exporting"
                  @click="download('distribution')"
                  ><template #icon
                    ><n-icon><ExportIcon /></n-icon></template
                  >导出分布</n-button
                >
              </div></template
            >
            <div class="distribution-layout">
              <StatBreakdown :rows="distribution" /><n-data-table
                size="small"
                :bordered="false"
                :columns="distributionColumns"
                :data="distribution"
                :max-height="280"
              />
            </div>
          </n-card>
        </template>
        <template v-if="section === 'traffic'">
          <n-card title="访问趋势" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra
              ><n-button v-if="canExport" size="small" secondary :loading="exporting" @click="download('traffic')"
                ><template #icon
                  ><n-icon><ExportIcon /></n-icon></template
                >导出趋势</n-button
              ></template
            ><StatTrend :rows="data.trend || []"
          /></n-card>
          <div class="statistics-grid">
            <n-card title="访问来源" size="small" :bordered="false" class="stat-panel"
              ><template v-if="data.sources?.length"
                ><StatBreakdown :rows="data.sources" /><n-data-table
                  size="small"
                  :bordered="false"
                  :columns="distributionColumns"
                  :data="data.sources"
                  :max-height="220" /></template
              ><n-empty v-else size="small" description="暂无访问来源数据" class="panel-empty"
            /></n-card>
            <n-card title="访问设备" size="small" :bordered="false" class="stat-panel"
              ><template v-if="data.devices?.length"
                ><StatBreakdown :rows="data.devices" /><n-data-table
                  size="small"
                  :bordered="false"
                  :columns="distributionColumns"
                  :data="data.devices"
                  :max-height="220" /></template
              ><n-empty v-else size="small" description="暂无访问设备数据" class="panel-empty"
            /></n-card>
          </div>
          <n-card title="热门页面" size="small" :bordered="false" class="stat-panel"
            ><n-data-table
              size="small"
              :bordered="false"
              :columns="distributionColumns"
              :data="data.pages || []"
              :max-height="360"
          /></n-card>
        </template>
        <template v-if="section === 'feedback'">
          <n-card title="答卷提交趋势" size="small" :bordered="false" class="stat-panel"
            ><StatTrend :rows="data.trend || []" :metrics="[{ key: 'count', label: '答卷数量' }]"
          /></n-card>
          <n-card title="问卷选项分布" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra
              ><n-button
                v-if="canExport"
                size="small"
                secondary
                :loading="exporting"
                @click="download('feedbackOptions')"
                ><template #icon
                  ><n-icon><ExportIcon /></n-icon></template
                >导出选项</n-button
              ></template
            ><n-data-table
              size="small"
              :bordered="false"
              :columns="optionColumns"
              :data="data.options || []"
              :max-height="360"
          /></n-card>
          <n-card title="答卷明细" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra
              ><div class="panel-actions">
                <n-select
                  v-if="showReplyStats"
                  v-model:value="replyFilter"
                  :options="replyOptions"
                  size="small"
                  clearable
                  aria-label="回复状态"
                  placeholder="全部回复状态"
                  class="reply-select"
                  @update:value="
                    page = 1;
                    load();
                  "
                /><n-button v-if="canExport" size="small" secondary :loading="exporting" @click="download('feedback')"
                  ><template #icon
                    ><n-icon><ExportIcon /></n-icon></template
                  >导出明细</n-button
                >
              </div></template
            ><n-data-table size="small" :bordered="false" :columns="answerColumns" :data="answers.list || []" />
            <div class="table-footer">
              <span class="count-label">共 {{ answers.total || 0 }} 份答卷</span
              ><n-pagination
                v-model:page="page"
                :page-size="20"
                :item-count="answers.total || 0"
                @update:page="loadAnswers"
              /></div
          ></n-card>
        </template>
        <template v-if="section === 'effectiveness'">
          <n-card title="内容传播排行" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra
              ><n-button v-if="canExport" size="small" secondary :loading="exporting" @click="download('effectiveness')"
                ><template #icon
                  ><n-icon><ExportIcon /></n-icon></template
                >导出排行</n-button
              ></template
            ><n-data-table
              size="small"
              :bordered="false"
              :columns="effectColumns"
              :data="Array.isArray(data) ? data : []"
              :scroll-x="900"
          /></n-card>
        </template>
        <template v-if="section === 'publishing'">
          <n-card title="编辑与发布趋势" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra
              ><n-button v-if="canExport" size="small" secondary :loading="exporting" @click="download('publishing')"
                ><template #icon
                  ><n-icon><ExportIcon /></n-icon></template
                >导出贡献</n-button
              ></template
            ><StatTrend :rows="publishingTrend" :metrics="publishingMetrics" /><n-collapse class="trend-details"
              ><n-collapse-item title="每日操作明细" name="details"
                ><n-data-table
                  size="small"
                  :bordered="false"
                  :columns="publishColumns"
                  :data="data.trend || []"
                  :max-height="300" /></n-collapse-item></n-collapse
          ></n-card>
          <div class="statistics-grid">
            <n-card title="机构贡献" size="small" :bordered="false" class="stat-panel"
              ><n-data-table
                size="small"
                :bordered="false"
                :columns="departmentColumns"
                :data="data.departments || []"
                :max-height="320" /></n-card
            ><n-card title="人员贡献" size="small" :bordered="false" class="stat-panel"
              ><n-data-table
                size="small"
                :bordered="false"
                :columns="actorColumns"
                :data="data.actors || []"
                :max-height="320"
            /></n-card>
          </div>
          <n-card title="待审核内容" size="small" :bordered="false" class="stat-panel"
            ><template #header-extra><span class="count-label">最多 100 条</span></template
            ><n-data-table
              size="small"
              :bordered="false"
              :columns="pendingColumns"
              :data="data.pending || []"
              :max-height="360"
          /></n-card>
        </template>
      </div>
    </n-spin>
    <CmsStatisticsExportTasks ref="exportTasks" />
    <n-modal v-model:show="answerOpen" preset="card" title="答卷详情" style="width: min(800px, 95vw)"
      ><n-spin :show="answerLoading"
        ><h3>{{ answerContent.title }}</h3>
        <n-descriptions :column="1" bordered
          ><n-descriptions-item v-for="(item, index) in answerContent.questions || []" :key="index" :label="item.title"
            ><span style="white-space: pre-wrap">{{ item.value }}</span></n-descriptions-item
          ><n-descriptions-item v-if="showReplyStats" label="管理员回复（可选）"
            ><span style="white-space: pre-wrap">{{
              answerContent.answerReply || '尚未回复'
            }}</span></n-descriptions-item
          ></n-descriptions
        ></n-spin
      ></n-modal
    >
    <n-modal v-model:show="publicationOpen" preset="card" title="内容发布与版本" style="width: min(960px, 95vw)"
      ><CmsPublicationPanel
        v-if="selectedContent"
        :module="selectedContent.module"
        :content-id="selectedContent.id"
        @changed="load"
    /></n-modal>
  </main>
</template>
<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue';
import { NButton, useThemeVars, type DataTableColumns } from 'naive-ui';
import ContentIcon from '~icons/mdi/file-document-outline';
import FolderIcon from '~icons/mdi/folder-outline';
import EmptyIcon from '~icons/mdi/folder-alert-outline';
import ClockIcon from '~icons/mdi/clock-outline';
import EyeIcon from '~icons/mdi/eye-outline';
import VisitorsIcon from '~icons/mdi/account-group-outline';
import BotIcon from '~icons/mdi/robot-outline';
import SurveyIcon from '~icons/mdi/clipboard-text-outline';
import ReplyIcon from '~icons/mdi/message-reply-outline';
import CheckIcon from '~icons/mdi/check-circle-outline';
import ReviewIcon from '~icons/mdi/clipboard-check-outline';
import RefreshIcon from '~icons/mdi/refresh';
import SearchIcon from '~icons/mdi/magnify';
import ExportIcon from '~icons/mdi/tray-arrow-down';
import TasksIcon from '~icons/mdi/clipboard-list-outline';
import dayjs from 'dayjs';
import { useAuthStore } from '@/store';
import { statistics, exportStatistics, type CmsStatParams, type StatRow } from '../api/statistics';
import CmsPublicationPanel from '../components/CmsPublicationPanel.vue';
import StatTrend from '../components/StatTrend.vue';
import StatBreakdown from '../components/StatBreakdown.vue';
import CmsStatisticsExportTasks from '../components/CmsStatisticsExportTasks.vue';
const exportTasks = ref<InstanceType<typeof CmsStatisticsExportTasks>>();
const auth = useAuthStore();
const theme = useThemeVars();
const statisticsTheme = computed(() => ({
  '--cms-primary': theme.value.primaryColor,
  '--cms-info': theme.value.infoColor,
  '--cms-warning': theme.value.warningColor,
  '--cms-success': theme.value.successColor,
  '--cms-text': theme.value.textColor1,
  '--cms-muted': theme.value.textColor3,
  '--cms-border': theme.value.borderColor,
  '--cms-surface': theme.value.cardColor,
  '--cms-page': theme.value.bodyColor,
}));
const canReadFeedback = computed(() => auth.hasPermissionByCode('SELECT', 'CMS_FEEDBACK'));
const answerOpen = ref(false);
const answerLoading = ref(false);
const answerContent = ref<StatRow>({});
let answerDetailId = 0;
async function showAnswer(row: StatRow) {
  const id = ++answerDetailId;
  answerOpen.value = true;
  answerLoading.value = true;
  answerContent.value = {};
  try {
    const response = await statistics<StatRow>(`feedback/answers/${row.id}`);
    if (id === answerDetailId) answerContent.value = response.data;
  } catch {
    if (id === answerDetailId) {
      answerOpen.value = false;
      error.value = '答卷详情加载失败或无权查看';
    }
  } finally {
    if (id === answerDetailId) answerLoading.value = false;
  }
}
const canExport = computed(() => auth.hasPermissionByCode('EXPORT', 'CMS_STAT'));
const sites = ref<StatRow[]>([]);
const categories = ref<StatRow[]>([]);
const filters = ref<CmsStatParams>({ dateField: 'CONTENT_DATE' });
const dateRange = ref<[number, number] | null>([
  dayjs().subtract(29, 'day').startOf('day').valueOf(),
  dayjs().startOf('day').valueOf(),
]);
const filterContentDate = ref(false);
const dimension = ref('CATEGORY');
const section = ref('overview');
const page = ref(1);
const data = ref<StatRow>({});
const answers = ref<StatRow>({});
const distribution = ref<StatRow[]>([]);
const loading = ref(false);
const exporting = ref(false);
const error = ref('');
const replyFilter = ref<'yes' | 'no' | null>(null);
const showReplyStats = ref(false);
const selectedContent = ref<StatRow | null>(null);
const publicationOpen = ref(false);
// 存量概况不使用时间区间；内容明细只有启用时间筛选后才展示区间控件。
const showDateRange = computed(
  () => section.value !== 'overview' && (section.value !== 'contents' || filterContentDate.value),
);
const moduleOptions = ref<{ value: string; label: string }[]>([]);
const stateOptions = ['DRAFT', 'AUDIT', 'PUBLISHED', 'SCHEDULED', 'OFFLINE', 'TRASH', 'LEGACY'].map((value, i) => ({
  value,
  label: ['草稿', '待审核', '已发布', '定时发布', '已下线', '回收站', '历史内容'][i],
}));
const stateNames = Object.fromEntries(stateOptions.map((item) => [item.value, item.label]));
const qualityOptions = [
  { value: 'NO_IMAGE', label: '缺少封面' },
  { value: 'NO_DESCRIPTION', label: '缺少摘要' },
  { value: 'NO_CONTENT', label: '缺少正文' },
  { value: 'STALE', label: '90天未更新' },
];
const dateOptions = [
  { value: 'CONTENT_DATE', label: '原录入内容日期' },
  { value: 'CREATED', label: '真实创建时间' },
  { value: 'PUBLISHED', label: '实际发布时间' },
];
const dimensionOptions = [
  { value: 'CATEGORY', label: '按栏目（含附属归属）' },
  { value: 'AUTHOR', label: '按作者' },
  { value: 'CREATOR', label: '按创建人' },
  { value: 'STATUS', label: '按当前状态' },
  { value: 'DAY', label: '按内容日期' },
];
const replyOptions = [
  { value: 'yes', label: '已回复' },
  { value: 'no', label: '未回复' },
];
function toggleReplyStats() {
  if (!showReplyStats.value) replyFilter.value = null;
  page.value = 1;
  void load();
}
function params(target = section.value): CmsStatParams {
  const result: CmsStatParams = { ...filters.value, pageNum: page.value, pageSize: 20, dimension: dimension.value };
  if (
    dateRange.value &&
    target !== 'overview' &&
    ((target !== 'contents' && target !== 'distribution') || filterContentDate.value)
  ) {
    result.start = dateRange.value[0];
    result.end = dayjs(dateRange.value[1]).add(1, 'day').startOf('day').valueOf();
  }
  // 问卷筛选与内容类型独立；回复统计关闭时，不携带上次选择的回复条件。
  if (['feedback', 'feedbackOptions'].includes(target)) {
    result.module = 'FEEDBACK';
    result.state = null;
    result.quality = null;
    result.title = undefined;
    result.includeReplyStats = showReplyStats.value;
    result.replied = showReplyStats.value && replyFilter.value ? replyFilter.value === 'yes' : undefined;
  } else {
    result.feedbackId = null;
    delete result.replied;
    delete result.includeReplyStats;
  }

  return result;
}
let loadId = 0;
async function load() {
  const id = ++loadId;
  ++distributionId;
  ++answersId;
  const target = section.value;
  loading.value = true;
  error.value = '';
  try {
    const response = await statistics<StatRow>(target, params(target));
    if (id !== loadId) return;
    data.value = response.data;
    if (target === 'contents') await loadDistribution();
    if (target === 'feedback') await loadAnswers();
  } catch (exception: any) {
    if (id === loadId) {
      data.value = {};
      error.value = exception?.response?.data?.message || '统计加载失败，请重试';
    }
  } finally {
    if (id === loadId) loading.value = false;
  }
}
let distributionId = 0;
async function loadDistribution() {
  const id = ++distributionId;
  try {
    const response = await statistics<StatRow[]>('distribution', params('distribution'));
    if (id === distributionId) distribution.value = response.data;
  } catch {
    if (id === distributionId) {
      distribution.value = [];
      error.value = '内容分布加载失败';
    }
  }
}
let answersId = 0;
async function loadAnswers() {
  const id = ++answersId;
  try {
    const response = await statistics<StatRow>('feedback/answers', params('feedback'));
    if (id === answersId) answers.value = response.data;
  } catch {
    if (id === answersId) {
      answers.value = {};
      error.value = '答卷明细加载失败';
    }
  }
}
async function download(target: string) {
  if (exporting.value) return;
  exporting.value = true;
  try {
    const response = await exportStatistics(target, params(target));
    exportTasks.value?.open(response.data);
  } catch {
    error.value = '导出失败，请重试';
  } finally {
    exporting.value = false;
  }
}
function formatTime(value: number | null | undefined) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '未记录';
}
function drillCategory(row: StatRow) {
  filters.value.categoryId = row.id;
  section.value = 'contents';
  page.value = 1;
  void load();
}
function manageContent(row: StatRow) {
  selectedContent.value = row;
  publicationOpen.value = true;
}
const metric = (label: string, value: any, icon = ContentIcon, tone = 'primary', detail = '') => ({
  label,
  value: value ?? 0,
  icon,
  tone,
  detail,
});
const sectionMetrics = computed(() => {
  const current = data.value;
  switch (section.value) {
    case 'overview':
      return [
        metric('内容总量', current.total),
        metric('栏目数量', current.categoryCount, FolderIcon, 'info'),
        metric('空栏目', current.emptyCategories?.length, EmptyIcon, 'warning'),
        metric('长期未更新栏目', current.staleCategories?.length, ClockIcon, 'warning'),
      ];
    case 'traffic':
      return [
        metric('页面浏览量 PV', current.pv, EyeIcon),
        metric('独立访客 UV', current.uv, VisitorsIcon, 'info'),
        metric('爬虫请求', current.bots, BotIcon, 'warning'),
      ];
    case 'feedback':
      return [
        metric('答卷总数', current.total, SurveyIcon, 'primary', '按提交时间统计'),
        ...(showReplyStats.value
          ? [
              metric('待回复', current.pending, ReplyIcon, 'warning'),
              metric('回复率 %', Number((current.replyRate || 0).toFixed(1)), CheckIcon, 'success'),
              metric(
                '平均回复耗时（小时）',
                Number(((current.averageReplySeconds || 0) / 3600).toFixed(1)),
                ClockIcon,
                'info',
              ),
            ]
          : []),
      ];
    case 'publishing':
      return [
        metric(
          '平均审核耗时（小时）',
          Number(((current.averageReviewSeconds || 0) / 3600).toFixed(1)),
          ReviewIcon,
          'info',
          `${current.reviewSamples || 0} 个审核样本`,
        ),
        metric('当前待审核', current.pendingTotal, ClockIcon, 'warning'),
      ];
    default:
      return [];
  }
});
const moduleLabel = (value: string) => moduleOptions.value.find((item) => item.value === value)?.label || value;
const typeRows = computed<StatRow[]>(() =>
  (data.value.types || []).slice().sort((a: StatRow, b: StatRow) => b.total - a.total),
);
const typeBreakdown = computed(() =>
  typeRows.value.map((row) => ({ label: moduleLabel(row.module), count: row.total })),
);
const typeColumns: DataTableColumns<StatRow> = [
  {
    key: 'module',
    title: '类型',
    render: (row) =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          class: 'stat-table-link',
          onClick: () => {
            filters.value.module = row.module;
            section.value = 'contents';
            page.value = 1;
            void load();
          },
        },
        () => moduleLabel(row.module),
      ),
  },
  { key: 'total', title: '数量', align: 'right' },
  { key: 'normal', title: '已发布', align: 'right' },
  { key: 'audit', title: '未发布 / 待审核', align: 'right' },
];
const categoryColumns: DataTableColumns<StatRow> = [
  {
    key: 'name',
    title: '栏目',
    render: (row) =>
      h(NButton, { text: true, class: 'stat-table-link', onClick: () => drillCategory(row) }, () => row.name),
  },
  { key: 'module', title: '类型', render: (row) => moduleLabel(row.module) },
  { key: 'lastUpdated', title: '最后更新', render: (row) => formatTime(row.lastUpdated) },
];
const contentColumns: DataTableColumns<StatRow> = [
  {
    key: 'title',
    title: '标题',
    width: 300,
    ellipsis: { tooltip: true },
    render: (row) =>
      h(
        NButton,
        { text: true, type: 'primary', class: 'stat-table-link', onClick: () => manageContent(row) },
        () => row.title,
      ),
  },
  { key: 'module', title: '类型', width: 140, render: (row) => moduleLabel(row.module) },
  { key: 'categoryName', title: '栏目', width: 160 },
  {
    key: 'status',
    title: '公开状态',
    width: 110,
    render: (row) => (row.status === 'NORMAL' ? '公开' : row.status === 'DELETE' ? '已删除' : '非公开'),
  },
  { key: 'state', title: '发布状态', width: 120, render: (row) => stateNames[row.state] || row.status },
  { key: 'author', title: '作者', width: 120 },
  { key: 'createTime', title: '内容日期', width: 170, render: (row) => formatTime(row.createTime) },
  { key: 'publishedAt', title: '实际发布', width: 170, render: (row) => formatTime(row.publishedAt) },
];
const distributionColumns: DataTableColumns<StatRow> = [
  { key: 'label', title: '分类' },
  { key: 'count', title: '数量', align: 'right', width: 100, sorter: 'default' },
];
const optionColumns: DataTableColumns<StatRow> = [
  { key: 'question', title: '题目' },
  { key: 'option', title: '选项' },
  { key: 'count', title: '选择次数' },
];
const answerColumns = computed<DataTableColumns<StatRow>>(() => [
  {
    key: 'title',
    title: '答卷标题',
    render: (row) =>
      canReadFeedback.value
        ? h(
            NButton,
            { text: true, type: 'primary', class: 'stat-table-link', onClick: () => showAnswer(row) },
            () => row.title,
          )
        : row.title,
  },
  { key: 'createTime', title: '提交时间', render: (row) => formatTime(row.createTime) },
  ...(showReplyStats.value
    ? [
        { key: 'replyTime', title: '回复时间', render: (row: StatRow) => formatTime(row.replyTime) },
        { key: 'replyBy', title: '回复人' },
      ]
    : []),
]);
const effectColumns: DataTableColumns<StatRow> = [
  {
    key: 'title',
    title: '内容',
    width: 320,
    render: (row) =>
      h(NButton, { text: true, class: 'stat-table-link', onClick: () => manageContent(row) }, () => row.title),
  },
  { key: 'views', title: '阅读量', align: 'right', sorter: 'default' },
  { key: 'likes', title: '点赞净新增', align: 'right' },
  { key: 'favorites', title: '收藏净新增', align: 'right' },
  { key: 'shares', title: '分享次数', align: 'right' },
];
const kindLabels: Record<string, string> = {
  CREATE: '创建',
  EDIT: '编辑',
  PUBLISH: '发布',
  AUDIT: '提交审核',
  TRASH: '移入回收站',
  OFFLINE: '下线',
  AUTO_OFFLINE: '自动下线',
};
// 页签切换期间可能仍保留上一页签的数据，只接受带操作类型的发布事件。
const publishingEvents = computed<StatRow[]>(() =>
  (data.value.trend || []).filter((row: StatRow) => typeof row.kind === 'string' && row.kind.length > 0),
);
const publishingMetrics = computed(() =>
  [...new Set<string>(publishingEvents.value.map((row) => row.kind))].map((key) => ({
    key,
    label: kindLabels[key] || key,
  })),
);
// 将日期和操作的分组结果合并为趋势序列，每日明细仍使用接口原始记录。
const publishingTrend = computed(() => {
  const days = new Map<string, StatRow>();
  for (const row of publishingEvents.value) {
    const day = days.get(row.day) || { day: row.day };
    day[row.kind] = (day[row.kind] || 0) + row.count;
    days.set(row.day, day);
  }
  return [...days.values()].map((day) =>
    Object.fromEntries([
      ['day', day.day],
      ...publishingMetrics.value.map((metric) => [metric.key, day[metric.key] || 0]),
    ]),
  );
});
const actorColumns: DataTableColumns<StatRow> = [
  { key: 'actorId', title: '操作人', render: (row) => row.actorName || row.actorId },
  { key: 'kind', title: '操作', render: (row) => kindLabels[row.kind] || row.kind },
  { key: 'count', title: '次数', align: 'right', width: 80 },
];
const departmentColumns: DataTableColumns<StatRow> = [
  { key: 'groupName', title: '机构' },
  { key: 'kind', title: '操作', render: (row) => kindLabels[row.kind] || row.kind },
  { key: 'count', title: '次数', align: 'right', width: 80 },
];
const publishColumns: DataTableColumns<StatRow> = [
  { key: 'day', title: '日期' },
  { key: 'kind', title: '操作', render: (row) => kindLabels[row.kind] || row.kind },
  { key: 'count', title: '次数', align: 'right', width: 80 },
];
const pendingColumns: DataTableColumns<StatRow> = [
  {
    key: 'title',
    title: '标题',
    render: (row) =>
      h(
        NButton,
        { text: true, type: 'primary', class: 'stat-table-link', onClick: () => manageContent(row) },
        () => row.title || row.id,
      ),
  },
  { key: 'submittedAt', title: '提交审核时间', render: (row) => formatTime(row.submittedAt) },
  {
    key: 'hours',
    title: '等待时长（小时）',
    render: (row) => (row.submittedAt ? Math.max(0, Math.floor((Date.now() - row.submittedAt) / 3600000)) : '未记录'),
  },
];
let filtersId = 0;
async function loadFilters() {
  const id = ++filtersId;
  try {
    const response = await statistics<StatRow>('filters', { siteId: filters.value.siteId });
    if (id === filtersId) {
      if (!filters.value.siteId || !sites.value.length) sites.value = response.data.sites;
      categories.value = response.data.categories;
      moduleOptions.value = response.data.modules;
    }
  } catch {
    if (id === filtersId) error.value = '站点和栏目加载失败';
  }
}
const feedbackForms = ref<StatRow[]>([]);
const formsLoading = ref(false);
let formsId = 0;
async function searchForms(keyword = '') {
  const id = ++formsId;
  formsLoading.value = true;
  try {
    const response = await statistics<StatRow>('feedback/forms', {
      siteId: filters.value.siteId,
      categoryId: filters.value.categoryId,
      title: keyword,
    });
    if (id === formsId) feedbackForms.value = response.data.list;
  } catch {
    if (id === formsId) {
      feedbackForms.value = [];
      error.value = '问卷列表加载失败';
    }
  } finally {
    if (id === formsId) formsLoading.value = false;
  }
}
watch(
  () => [filters.value.siteId, filters.value.categoryId],
  () => {
    filters.value.feedbackId = null;
    feedbackForms.value = [];
    ++formsId;
    if (section.value === 'feedback') void searchForms();
  },
);
watch(section, (value) => {
  if (value === 'feedback') void searchForms();
});
watch(
  () => filters.value.siteId,
  () => {
    filters.value.categoryId = null;
    categories.value = [];
    void loadFilters();
  },
);
onMounted(async () => {
  await loadFilters();
  await load();
});
</script>
<style scoped>
.cms-statistics {
  height: 100%;
  min-height: 0;
  box-sizing: border-box;
  padding: 12px 16px 16px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  color: var(--cms-text);
  background: var(--cms-page);
  container-type: inline-size;
}
.statistics-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  flex-shrink: 0;
}
.statistics-heading h1 {
  margin: 0;
  font-size: 20px;
  line-height: 1.5;
  font-weight: 600;
}
.heading-actions,
.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.statistics-nav {
  flex-shrink: 0;
}
.statistics-nav :deep(.n-tabs-tab) {
  font-size: 13px;
  padding: 9px 0;
}
.filter-panel {
  border: 1px solid var(--cms-border);
  border-radius: 10px;
  padding: 10px 14px;
  background: var(--cms-surface);
  flex-shrink: 0;
}
.statistics-filters {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: center;
  gap: 10px 16px;
}
.statistics-filters.has-date {
  grid-template-columns: repeat(3, minmax(0, 1fr)) minmax(275px, 1.5fr) auto;
}
.statistics-filters :deep(.n-form-item) {
  min-width: 0;
}
.statistics-filters :deep(.n-form-item-blank) {
  min-width: 0;
}
.statistics-filters :deep(.n-form-item-label) {
  font-size: 12px;
  color: var(--cms-muted);
  padding-right: 8px;
}
.date-range {
  width: 100%;
  min-width: 0;
}
.date-range :deep(.n-input__input-el) {
  font-size: 12px;
}
.filter-query {
  justify-self: end;
}
.statistics-results {
  min-width: 0;
  flex-shrink: 0;
}
.section-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}
.metric-card {
  border: 1px solid var(--cms-border);
  border-radius: 10px;
  padding: 12px 16px;
  background: var(--cms-surface);
  min-width: 0;
}
.metric-top,
.metric-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.metric-label {
  color: var(--cms-muted);
  font-size: 12px;
}
.metric-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.metric-bottom {
  margin-top: 4px;
  align-items: baseline;
}
.metric-card :deep(.n-statistic-value__content) {
  font-size: 30px;
  font-weight: 600;
  line-height: 1.2;
}
.metric-detail,
.count-label {
  color: var(--cms-muted);
  font-size: 12px;
  white-space: nowrap;
}
.stat-panel {
  min-width: 0;
  border: 1px solid var(--cms-border);
  border-radius: 10px;
  background: var(--cms-surface);
}
.stat-panel :deep(.n-card-header) {
  padding: 14px 16px 12px;
  gap: 12px;
}
.stat-panel :deep(.n-card-header__main) {
  font-size: 14px;
  font-weight: 600;
}
.stat-panel :deep(.n-card__content) {
  padding: 0 16px 16px;
}
.statistics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.distribution-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 24px;
  align-items: center;
}
.panel-empty {
  padding: 24px 12px;
}
.detail-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}
.detail-select {
  width: 165px;
}
.detail-title {
  width: 200px;
}
.dimension-select {
  width: 180px;
}
.reply-select {
  width: 150px;
}
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 14px;
}
.trend-details {
  margin-top: 12px;
  border-top: 1px solid var(--cms-border);
  padding-top: 12px;
}
:deep(.stat-table-link) {
  max-width: 100%;
  font-size: 12px;
}
:deep(.stat-table-link .n-button__content) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stat-panel :deep(.n-data-table-th),
.stat-panel :deep(.n-data-table-td) {
  font-size: 12px;
}
@container (max-width: 900px) {
  .statistics-filters.has-date {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .has-date .date-filter {
    grid-column: 1 / 3;
  }
}
@container (max-width: 800px) {
  .statistics-filters,
  .statistics-filters.has-date {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .has-date .date-filter {
    grid-column: 1 / -1;
  }
  .has-date .filter-query {
    grid-column: 2;
    grid-row: 2;
  }
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .statistics-grid,
  .distribution-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .distribution-layout {
    gap: 12px;
  }
}
@container (max-width: 520px) {
  .statistics-filters,
  .statistics-filters.has-date {
    grid-template-columns: minmax(0, 1fr);
  }
  .has-date .filter-query {
    grid-column: auto;
    grid-row: auto;
  }
  .filter-query {
    width: 100%;
  }
  .stat-panel :deep(.n-card-header) {
    flex-wrap: wrap;
  }
  .stat-panel :deep(.n-card-header__extra) {
    max-width: 100%;
    margin-left: 0;
  }
  .panel-actions {
    flex-wrap: wrap;
  }
  .table-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .metric-card {
    padding: 10px 12px;
  }
  .metric-detail {
    white-space: normal;
  }
  .detail-select,
  .detail-title {
    flex: 1 1 150px;
  }
}
@media (max-width: 700px) {
  .cms-statistics {
    padding: 10px;
  }
}
</style>
