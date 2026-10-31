<template>
  <NSpin :show="loading">
    <main class="research-home">
      <header class="home-header">
        <div>
          <h1>科学研究管理</h1>
        </div>
        <div class="header-actions">
          <NButton secondary :loading="loading" @click="load">刷新</NButton>
          <NButton type="primary" @click="go('/museum-research/stat')">统计分析</NButton>
        </div>
      </header>

      <NAlert v-if="partialFailure" type="warning" :bordered="false">
        部分科研数据暂未加载成功，可点击“刷新”重试。
      </NAlert>

      <section class="metric-strip" aria-label="科研关键指标">
        <button v-for="item in metrics" :key="item.label" type="button" @click="go(item.path)">
          <span>{{ item.label }}</span>
          <strong :class="{ warning: item.warning }">{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </button>
      </section>

      <section class="analysis-section">
        <div class="trend-block">
          <div class="section-head">
            <div>
              <h2>年度科研趋势</h2>
              <span>课题申报与成果产出</span>
            </div>
            <NButton text type="primary" @click="go('/museum-research/stat')">查看完整统计</NButton>
          </div>
          <HEChart v-if="trendYears.length" :option="trendOption" class="trend-chart" />
          <NEmpty v-else description="暂无年度科研数据" size="small" class="chart-empty" />
        </div>

        <div class="structure-block">
          <div class="section-head">
            <div>
              <h2>课题阶段结构</h2>
              <span>当前课题分布</span>
            </div>
          </div>
          <HEChart v-if="stageRows.length" :option="stageOption" class="structure-chart" />
          <NEmpty v-else description="暂无课题阶段数据" size="small" class="chart-empty" />
          <dl class="ratio-list">
            <div v-for="item in ratioRows" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </div>
      </section>

      <section class="work-section">
        <div class="todo-block">
          <div class="section-head">
            <div>
              <h2>工作事项</h2>
              <span>共 {{ todoTotal }} 项需要关注</span>
            </div>
          </div>
          <div class="todo-list">
            <button v-for="item in todoItems" :key="item.label" type="button" @click="go(item.path)">
              <span class="todo-copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.note }}</small>
              </span>
              <b :class="{ warning: item.warning }">{{ item.value }}</b>
              <span class="link-text">查看</span>
            </button>
          </div>
        </div>

        <div class="records-block">
          <NTabs type="line" animated>
            <NTabPane name="topics" tab="近期课题">
              <div v-if="recentTopics.length" class="record-list">
                <button
                  v-for="item in recentTopics"
                  :key="item.id || item.topicNo"
                  type="button"
                  @click="go('/museum-research/topic')"
                >
                  <span class="record-main">
                    <strong>{{ item.name || '未命名课题' }}</strong>
                    <small>{{ item.topicNo || '编号待生成' }}　{{ item.leaderName || '负责人待补充' }}</small>
                  </span>
                  <span class="record-status">
                    <HDictText type="research_topic_stage" :value="item.stage" />
                  </span>
                  <time>{{ formatDate(item.createTime) }}</time>
                </button>
              </div>
              <NEmpty v-else description="暂无科研课题" size="small" class="record-empty" />
              <div class="list-footer">
                <NButton text type="primary" @click="go('/museum-research/topic')">查看全部课题</NButton>
              </div>
            </NTabPane>

            <NTabPane name="achievements" tab="近期成果">
              <div v-if="recentAchievements.length" class="record-list">
                <button
                  v-for="item in recentAchievements"
                  :key="item.id || item.achievementNo"
                  type="button"
                  @click="go('/museum-research/achievement')"
                >
                  <span class="record-main">
                    <strong>{{ item.title || '未命名成果' }}</strong>
                    <small>{{ item.authors || '作者待补充' }}　{{ item.topicName || '未关联课题' }}</small>
                  </span>
                  <span class="record-status">
                    <HDictText type="research_achievement_type" :value="item.achievementType" />
                  </span>
                  <time>{{ formatDate(item.publishDate || item.createTime) }}</time>
                </button>
              </div>
              <NEmpty v-else description="暂无研究成果" size="small" class="record-empty" />
              <div class="list-footer">
                <NButton text type="primary" @click="go('/museum-research/achievement')">查看全部成果</NButton>
              </div>
            </NTabPane>
          </NTabs>
        </div>
      </section>
    </main>
  </NSpin>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { NAlert, NButton, NEmpty, NSpin, NTabPane, NTabs } from 'naive-ui';
import { useRouter } from 'vue-router';
import HEChart from '@/components/data/chart/EChart.vue';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { achievementApi } from '../api/achievement';
import { dataAccessApi } from '../api/dataAccess';
import { reviewApi } from '../api/review';
import { statApi } from '../api/stat';
import { topicApi } from '../api/topic';
import type { ResAchievementVo, ResTopicVo, ResearchStatVo } from '../typings';

const router = useRouter();
const loading = ref(false);
const partialFailure = ref(false);
const stat = ref<ResearchStatVo | null>(null);
const recentTopics = ref<ResTopicVo[]>([]);
const recentAchievements = ref<ResAchievementVo[]>([]);
const pendingReviewTotal = ref(0);
const pendingAccessTotal = ref(0);
const stageLabels = ref<Record<string, string>>({});

const metrics = computed(() => {
  const s = stat.value;
  return [
    {
      label: '科研课题',
      value: s?.topicTotal ?? 0,
      note: '在研 ' + (s?.topicInResearch ?? 0) + ' 项',
      path: '/museum-research/topic',
    },
    {
      label: '进度预警',
      value: s?.topicAlert ?? 0,
      note: '实施计划逾期',
      path: '/museum-research/plan',
      warning: true,
    },
    {
      label: '研究成果',
      value: s?.achievementTotal ?? 0,
      note: '评审通过 ' + (s?.achievementPassed ?? 0) + ' 项',
      path: '/museum-research/achievement',
    },
    {
      label: '成果转化',
      value: s?.transformTotal ?? 0,
      note: '完成 ' + (s?.transformCompleted ?? 0) + ' 项',
      path: '/museum-research/transform',
    },
    {
      label: '专家与评审',
      value: s?.expertTotal ?? 0,
      note: '评审记录 ' + (s?.reviewTotal ?? 0) + ' 条',
      path: '/museum-research/expert',
    },
    {
      label: '数据开放',
      value: s?.dataAccessApproved ?? 0,
      note: '申请 ' + (s?.dataAccessTotal ?? 0) + ' 件',
      path: '/museum-research/data-access',
    },
  ];
});

const trendYears = computed(() =>
  Array.from(
    new Set([
      ...(stat.value?.topicByYear ?? []).map((item) => String(item.name)),
      ...(stat.value?.achievementByYear ?? []).map((item) => String(item.name)),
    ]),
  ).sort((a, b) => a.localeCompare(b, 'zh-CN', { numeric: true })),
);

const trendOption = computed(() => {
  const topicMap = Object.fromEntries((stat.value?.topicByYear ?? []).map((item) => [String(item.name), item.count]));
  const achievementMap = Object.fromEntries(
    (stat.value?.achievementByYear ?? []).map((item) => [String(item.name), item.count]),
  );
  return {
    color: ['#08756f', '#536579'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0, right: 8, itemWidth: 14, itemHeight: 8 },
    grid: { left: 20, right: 18, top: 42, bottom: 20, containLabel: true },
    xAxis: {
      type: 'category',
      data: trendYears.value,
      axisLine: { lineStyle: { color: '#ccd5df' } },
      axisTick: { show: false },
      axisLabel: { color: '#677486' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#677486' },
      splitLine: { lineStyle: { color: '#edf1f5' } },
    },
    series: [
      {
        name: '课题数',
        type: 'bar',
        barMaxWidth: 26,
        data: trendYears.value.map((year) => topicMap[year] ?? 0),
        itemStyle: { borderRadius: [2, 2, 0, 0] },
      },
      {
        name: '成果数',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        lineStyle: { width: 2 },
        data: trendYears.value.map((year) => achievementMap[year] ?? 0),
      },
    ],
  };
});

const stageRows = computed(() => (stat.value?.topicByStage ?? []).filter((item) => item.count > 0));
const stageTotal = computed(() => stageRows.value.reduce((sum, item) => sum + item.count, 0));

const stageOption = computed(() => ({
  color: ['#08756f', '#3b8984', '#72a7a3', '#a8c5c2', '#cedddb', '#7b8998'],
  tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 项（{d}%）' },
  title: {
    text: String(stageTotal.value),
    subtext: '课题总量',
    left: 'center',
    top: '32%',
    textStyle: { color: '#172033', fontSize: 24, fontWeight: 700 },
    subtextStyle: { color: '#758295', fontSize: 12 },
  },
  legend: { bottom: 0, left: 'center', type: 'scroll', itemWidth: 10, itemHeight: 8 },
  series: [
    {
      name: '课题阶段',
      type: 'pie',
      radius: ['48%', '68%'],
      center: ['50%', '39%'],
      label: { show: false },
      emphasis: { label: { show: false } },
      data: stageRows.value.map((item) => ({
        name: stageLabels.value[item.name] || item.name,
        value: item.count,
      })),
    },
  ],
}));

const ratioRows = computed(() => {
  const s = stat.value;
  return [
    { label: '课题归档率', value: percent(s?.topicArchived ?? 0, s?.topicTotal ?? 0) },
    { label: '成果评审通过率', value: percent(s?.achievementPassed ?? 0, s?.achievementTotal ?? 0) },
    { label: '成果转化完成率', value: percent(s?.transformCompleted ?? 0, s?.transformTotal ?? 0) },
  ];
});

const todoItems = computed(() => [
  {
    label: '课题进度预警',
    value: stat.value?.topicAlert ?? 0,
    note: '存在逾期实施计划的课题',
    path: '/museum-research/plan',
    warning: true,
  },
  {
    label: '待完成专家评审',
    value: pendingReviewTotal.value,
    note: '已邀请但尚未提交评审意见',
    path: '/museum-research/review',
  },
  {
    label: '待审批数据申请',
    value: pendingAccessTotal.value,
    note: '科研数据开放使用申请',
    path: '/museum-research/data-access',
  },
]);

const todoTotal = computed(() => todoItems.value.reduce((sum, item) => sum + Number(item.value || 0), 0));

onMounted(load);

async function load() {
  loading.value = true;
  partialFailure.value = false;
  try {
    const results = await Promise.allSettled([
      statApi.overview(),
      topicApi.page({ pageNum: 1, pageSize: 5, sortBy: ['createTime'], sortDesc: [true] }),
      achievementApi.page({ pageNum: 1, pageSize: 5, sortBy: ['createTime'], sortDesc: [true] }),
      reviewApi.page({ pageNum: 1, pageSize: 1, reviewStatus: 'PENDING' }),
      dataAccessApi.page({ pageNum: 1, pageSize: 1, accessStatus: 'APPLYING' }),
      fetchDictType('research_topic_stage', true),
    ]);

    const [statResult, topicResult, achievementResult, reviewResult, accessResult, stageDictResult] = results;
    if (statResult.status === 'fulfilled') {
      stat.value = statResult.value?.data?.data ?? statResult.value?.data ?? null;
    }
    if (topicResult.status === 'fulfilled') {
      recentTopics.value = pagePayload<ResTopicVo>(topicResult.value).list;
    }
    if (achievementResult.status === 'fulfilled') {
      recentAchievements.value = pagePayload<ResAchievementVo>(achievementResult.value).list;
    }
    if (reviewResult.status === 'fulfilled') {
      pendingReviewTotal.value = pagePayload(reviewResult.value).total;
    }
    if (accessResult.status === 'fulfilled') {
      pendingAccessTotal.value = pagePayload(accessResult.value).total;
    }
    if (stageDictResult.status === 'fulfilled') {
      stageLabels.value = Object.fromEntries(
        (stageDictResult.value?.data ?? []).map((item: any) => [
          String(item.value ?? ''),
          String(item.label ?? item.value ?? ''),
        ]),
      );
    }

    partialFailure.value = results.some((item) => item.status === 'rejected');
  } finally {
    loading.value = false;
  }
}

function pagePayload<T>(response: any): { list: T[]; total: number } {
  const data = response?.data?.data ?? response?.data ?? response ?? {};
  const list = Array.isArray(data.list) ? data.list : Array.isArray(data) ? data : [];
  return { list, total: Number(data.total ?? list.length) };
}

function percent(value: number, total: number) {
  return total ? Math.round((value / total) * 100) + '%' : '0%';
}

function formatDate(value?: string) {
  return value ? String(value).replace('T', ' ').slice(0, 10) : '--';
}

function go(path: string) {
  router.push(path);
}
</script>

<style scoped>
.research-home {
  --ink: #172033;
  --muted: #697789;
  --line: #d9e1e8;
  --soft-line: #e9eef2;
  --surface: #fff;
  --page: #f4f6f8;
  --primary: #08756f;
  min-height: 100%;
  padding: 16px;
  color: var(--ink);
  background: var(--page);
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 4px 15px;
}

.home-header h1 {
  margin: 0;
  font-size: 23px;
  line-height: 1.25;
}

.header-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.metric-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin-top: 12px;
  border-block: 1px solid var(--line);
  background: var(--surface);
}

.metric-strip button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 4px 10px;
  min-width: 0;
  padding: 14px 17px;
  border: 0;
  border-right: 1px solid var(--soft-line);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.metric-strip button:last-child {
  border-right: 0;
}

.metric-strip button:hover,
.metric-strip button:focus-visible {
  background: #f7faf9;
  outline: none;
}

.metric-strip span {
  color: var(--muted);
  font-size: 13px;
}

.metric-strip strong {
  grid-row: span 2;
  font-size: 27px;
  line-height: 1;
}

.metric-strip small {
  overflow: hidden;
  color: #8793a1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.warning {
  color: #b45309 !important;
}

.analysis-section,
.work-section {
  display: grid;
  margin-top: 14px;
  border-block: 1px solid var(--line);
  background: var(--surface);
}

.analysis-section {
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 0.75fr);
}

.work-section {
  grid-template-columns: minmax(310px, 0.72fr) minmax(0, 1.28fr);
}

.trend-block,
.structure-block,
.todo-block,
.records-block {
  min-width: 0;
  padding: 16px 18px;
}

.trend-block,
.todo-block {
  border-right: 1px solid var(--line);
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 38px;
  margin-bottom: 8px;
}

.section-head h2 {
  margin: 0 0 3px;
  font-size: 16px;
  line-height: 1.25;
}

.section-head span {
  color: var(--muted);
  font-size: 12px;
}

.trend-chart {
  height: 340px;
}

.structure-chart {
  height: 255px;
}

.chart-empty {
  padding: 110px 0;
}

.ratio-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 6px 0 0;
  border-top: 1px solid var(--soft-line);
}

.ratio-list div {
  padding: 11px 8px 2px;
  border-right: 1px solid var(--soft-line);
  text-align: center;
}

.ratio-list div:last-child {
  border-right: 0;
}

.ratio-list dt {
  color: var(--muted);
  font-size: 11px;
}

.ratio-list dd {
  margin: 3px 0 0;
  font-size: 18px;
  font-weight: 700;
}

.todo-list {
  border-top: 1px solid var(--soft-line);
}

.todo-list button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 30px;
  align-items: center;
  gap: 14px;
  width: 100%;
  min-height: 70px;
  padding: 10px 2px;
  border: 0;
  border-bottom: 1px solid var(--soft-line);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.todo-list button:hover,
.todo-list button:focus-visible,
.record-list button:hover,
.record-list button:focus-visible {
  background: #f7faf9;
  outline: none;
}

.todo-copy,
.record-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.todo-copy strong,
.record-main strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-copy small,
.record-main small {
  overflow: hidden;
  color: var(--muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-list b {
  min-width: 32px;
  font-size: 21px;
  text-align: right;
}

.link-text {
  color: var(--primary);
  font-size: 12px;
}

.records-block {
  padding-top: 7px;
}

.record-list {
  border-top: 1px solid var(--soft-line);
}

.record-list button {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 105px 82px;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 54px;
  padding: 8px 2px;
  border: 0;
  border-bottom: 1px solid var(--soft-line);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.record-status {
  overflow: hidden;
  color: #435466;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-list time {
  color: var(--muted);
  font-size: 12px;
  text-align: right;
}

.record-empty {
  padding: 80px 0;
}

.list-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 9px;
}

@media (max-width: 1200px) {
  .metric-strip {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-strip button:nth-child(3) {
    border-right: 0;
  }

  .metric-strip button:nth-child(-n + 3) {
    border-bottom: 1px solid var(--soft-line);
  }

  .analysis-section,
  .work-section {
    grid-template-columns: 1fr;
  }

  .trend-block,
  .todo-block {
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }
}

@media (max-width: 720px) {
  .research-home {
    padding: 10px;
  }

  .home-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .metric-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-strip button:nth-child(3) {
    border-right: 1px solid var(--soft-line);
  }

  .metric-strip button:nth-child(2n) {
    border-right: 0;
  }

  .metric-strip button:nth-child(-n + 4) {
    border-bottom: 1px solid var(--soft-line);
  }

  .ratio-list {
    grid-template-columns: 1fr;
  }

  .ratio-list div {
    border-right: 0;
    border-bottom: 1px solid var(--soft-line);
  }

  .ratio-list div:last-child {
    border-bottom: 0;
  }

  .record-list button {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .record-list time {
    display: none;
  }
}
</style>
