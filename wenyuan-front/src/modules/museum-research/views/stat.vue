<template>
  <NSpin :show="loading">
    <main class="research-stat">
      <section class="stat-toolbar">
        <div class="title-block">
          <span>统计分析</span>
          <h1>科研成果统计</h1>
          <p>按年度 / 成果类型 / 研究方向 / 研究团队多维分析，供管理决策参考</p>
        </div>
        <NButton size="small" secondary :loading="loading" @click="load">刷新</NButton>
      </section>

      <section class="kpi-grid">
        <div v-for="item in kpiCards" :key="item.label" class="kpi-tile">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </div>
      </section>

      <section class="stat-section">
        <div class="section-head">
          <div>
            <span>成果统计</span>
            <strong>产出与质量</strong>
          </div>
        </div>
        <div class="chart-grid">
          <div v-for="chart in achievementCharts" :key="chart.title" class="chart-panel">
            <div class="chart-title">
              <span>{{ chart.title }}</span>
              <small>{{ chart.rows.length }} 项</small>
            </div>
            <HEChart v-if="chart.rows.length" :option="chart.option" style="height: 250px" />
            <NEmpty v-else description="暂无数据" size="small" style="padding: 60px 0" />
          </div>
        </div>
      </section>

      <section class="stat-section">
        <div class="section-head">
          <div>
            <span>课题概览</span>
            <strong>立项与实施</strong>
          </div>
        </div>
        <div class="chart-grid">
          <div v-for="chart in topicCharts" :key="chart.title" class="chart-panel">
            <div class="chart-title">
              <span>{{ chart.title }}</span>
              <small>{{ chart.rows.length }} 项</small>
            </div>
            <HEChart v-if="chart.rows.length" :option="chart.option" style="height: 250px" />
            <NEmpty v-else description="暂无数据" size="small" style="padding: 60px 0" />
          </div>
        </div>
      </section>
    </main>
  </NSpin>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { NButton, NEmpty, NSpin } from 'naive-ui';
import HEChart from '@/components/data/chart/EChart.vue';
import { type as fetchDictType } from '@/modules/sys/api/dict';
import { statApi } from '../api/stat';
import type { ResNameCountVo, ResearchStatVo } from '../typings';

const loading = ref(false);
const stat = ref<ResearchStatVo | null>(null);
const dictLabels = ref<Record<string, Record<string, string>>>({});

const DICT_TYPES = [
  'research_achievement_type',
  'research_direction',
  'research_topic_stage',
  'research_topic_level',
  'research_transform_type',
];

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const [resp] = await Promise.all([statApi.overview(), loadDictLabels()]);
    stat.value = (resp as any)?.data ?? null;
  } finally {
    loading.value = false;
  }
}

async function loadDictLabels() {
  const entries = await Promise.all(
    DICT_TYPES.map(async (type) => {
      try {
        const resp: any = await fetchDictType(type, true);
        return [
          type,
          Object.fromEntries(
            (resp?.data ?? []).map((item: any) => [String(item.value ?? ''), String(item.label ?? item.value ?? '')]),
          ),
        ] as const;
      } catch {
        return [type, {}] as const;
      }
    }),
  );
  dictLabels.value = Object.fromEntries(entries);
}

function label(dictType: string | null, name: string) {
  if (!dictType) return name;
  return dictLabels.value[dictType]?.[name] || name;
}

const kpiCards = computed(() => {
  const s = stat.value;
  if (!s) return [];
  return [
    { label: '课题总数', value: s.topicTotal, note: `在研 ${s.topicInResearch} · 已归档 ${s.topicArchived}` },
    { label: '进度预警课题', value: s.topicAlert, note: '实施计划逾期未完成' },
    { label: '成果总数', value: s.achievementTotal, note: `通过评审 ${s.achievementPassed}` },
    { label: '本年新增成果', value: s.achievementThisYear, note: `评价 ${s.evalTotal} 条 · 均分 ${s.achievementAvgScore ?? '—'}` },
    { label: '成果转化', value: s.transformTotal, note: `已完成 ${s.transformCompleted}` },
    { label: '专家 / 数据授权', value: `${s.expertTotal} / ${s.dataAccessApproved}`, note: `评审 ${s.reviewTotal} 条 · 数据申请 ${s.dataAccessTotal}` },
  ];
});

type ChartDef = { title: string; rows: ResNameCountVo[]; option: any };

const achievementCharts = computed<ChartDef[]>(() => {
  const s = stat.value;
  if (!s) return [];
  return [
    buildChart('年度成果产出', s.achievementByYear, null, 'bar'),
    buildChart('按成果类型', s.achievementByType, 'research_achievement_type', 'pie'),
    buildChart('按研究方向', s.achievementByDirection, 'research_direction', 'pie'),
    buildChart('按研究团队', s.achievementByTeam, null, 'bar'),
    buildChart('转化类型分布', s.transformByType, 'research_transform_type', 'pie'),
  ];
});

const topicCharts = computed<ChartDef[]>(() => {
  const s = stat.value;
  if (!s) return [];
  return [
    buildChart('年度课题申报', s.topicByYear, null, 'bar'),
    buildChart('课题阶段分布', s.topicByStage, 'research_topic_stage', 'pie'),
    buildChart('课题方向分布', s.topicByDirection, 'research_direction', 'pie'),
    buildChart('课题级别分布', s.topicByLevel, 'research_topic_level', 'pie'),
  ];
});

function buildChart(
  title: string,
  rows: ResNameCountVo[] | null | undefined,
  dictType: string | null,
  kind: 'pie' | 'bar',
): ChartDef {
  const chartRows = Array.isArray(rows) ? rows : [];
  const named = chartRows.map((r) => ({ name: label(dictType, r.name), value: r.count }));
  const option =
    kind === 'pie'
      ? {
          tooltip: { trigger: 'item' },
          legend: { bottom: 0, type: 'scroll' },
          series: [
            {
              name: title,
              type: 'pie',
              radius: ['42%', '66%'],
              center: ['50%', '42%'],
              avoidLabelOverlap: true,
              data: named,
            },
          ],
        }
      : {
          tooltip: { trigger: 'axis' },
          grid: { left: 36, right: 16, top: 28, bottom: 52, containLabel: true },
          xAxis: {
            type: 'category',
            data: named.map((r) => r.name),
            axisLabel: { interval: 0, rotate: named.length > 4 ? 30 : 0 },
          },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{ name: title, type: 'bar', barMaxWidth: 34, data: named.map((r) => r.value) }],
        };
  return { title, rows: chartRows, option };
}
</script>

<style scoped>
.research-stat {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.stat-toolbar,
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

.title-block span,
.section-head span {
  color: #08756f;
  font-size: 13px;
  font-weight: 700;
}

.title-block h1 {
  margin: 4px 0;
  font-size: 22px;
  line-height: 1.25;
}

.title-block p {
  margin: 0;
  color: #6b778c;
  font-size: 13px;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
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
  font-size: 24px;
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

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 12px;
}

.chart-panel {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e3e9f2;
  border-radius: 6px;
  background: #fbfdff;
}

.chart-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.chart-title span {
  font-size: 14px;
  font-weight: 700;
}

.chart-title small {
  color: #6b778c;
}

@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .kpi-grid,
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
