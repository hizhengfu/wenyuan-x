<template>
  <n-card
    size="small"
    bordered
    class="wh-full flex flex-col"
    content-style="flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 300px;"
  >
    <template #header>
      <n-space align="center">
        <n-icon size="22px"><IconMdiChartPie /></n-icon>
        <span>条件统计</span>
        <n-tag :bordered="false" size="small" type="info">参考统计分析.xlsx 10类组合</n-tag>
      </n-space>
    </template>
    <template #header-extra>
      <n-space size="small">
        <n-button size="small" @click="fullscreen = true">
          <template #icon><n-icon><IconMdiFullscreen /></n-icon></template>
          全屏
        </n-button>
        <HPrint size="20px" :printable="refChartWrap"></HPrint>
      </n-space>
    </template>

    <div class="analysis-layout">
      <!-- 左侧：分类树 -->
      <div class="analysis-left">
        <n-tabs v-model:value="leftTab" type="line" size="small">
          <n-tab-pane name="project" tab="遗址">
            <n-input v-model:value="filterTree" placeholder="筛选" size="small" clearable />
            <n-tree
              :data="projectTreeData"
              :pattern="filterTree"
              key-field="key"
              label-field="label"
              children-field="children"
              block-line
              expand-on-click
              :selected-keys="selectedProjectKeys"
              @update:selected-keys="onSelectProject"
              style="margin-top: 8px"
            />
          </n-tab-pane>
          <n-tab-pane name="culture" tab="考古学文化">
            <n-tree
              :data="cultureTreeData"
              key-field="key"
              label-field="label"
              children-field="children"
              block-line
              expand-on-click
              :selected-keys="selectedCultureKeys"
              @update:selected-keys="onSelectCulture"
            />
          </n-tab-pane>
        </n-tabs>
      </div>

      <!-- 右侧：检索表单 + 图表 -->
      <div class="analysis-right">
        <!-- 10 类组合按钮 -->
        <n-card size="small" :bordered="false" class="combo-card" content-style="padding:8px 12px;">
          <n-space size="small">
            <n-button
              v-for="(combo, idx) in COMBOS"
              :key="idx"
              size="small"
              :type="paramsForm.groupByKey === combo.key ? 'primary' : 'default'"
              @click="applyCombo(combo)"
            >
              {{ idx + 1 }}. {{ combo.title }}
            </n-button>
          </n-space>
        </n-card>

        <!-- 过滤条件 -->
        <n-card size="small" :bordered="false" class="filter-card" content-style="padding:8px 12px;">
          <n-form inline label-placement="left" label-width="80px" size="small" style='flex-wrap: wrap;'>
            <n-form-item label="出土日期">
              <n-date-picker v-model:value="paramsForm.unearthDateList" type="daterange" clearable />
            </n-form-item>
            <n-form-item label="文化因素">
              <HDictSelect
                v-model:value="paramsForm.culturalAttribute"
                type="wms_artifact_cultural_attribute"
                multiple
                clearable
                max-tag-count="responsive"
                style="width: 200px"
              />
            </n-form-item>
            <n-form-item label="时代">
              <HDictTreeSelect
                v-model:value="paramsForm.timePeriod"
                type="wms_artifact_year"
                cascade
                check-strategy="all"
                multiple
                checkable
                filterable
                clearable
                max-tag-count="responsive"
                style="width: 200px"
              />
            </n-form-item>
            <n-form-item label="质地">
              <HDictTreeSelect
                v-model:value="paramsForm.material"
                type="wms_artifact_material_type"
                multiple
                clearable
                filterable
                checkable
                max-tag-count="responsive"
                style="width: 200px"
              />
            </n-form-item>
            <n-form-item label="保存状况">
              <HDictSelect
                v-model:value="paramsForm.saveStatus"
                type="wms_artifact_preserve_status"
                multiple
                clearable
                max-tag-count="responsive"
                style="width: 160px"
              />
            </n-form-item>
            <n-form-item label="图表">
              <n-radio-group v-model:value="paramsForm.chartType">
                <n-radio-button value="bar">柱状</n-radio-button>
                <n-radio-button value="pie">饼图</n-radio-button>
              </n-radio-group>
            </n-form-item>
            <n-form-item>
              <n-button type="primary" @click="fetchData">
                <template #icon><n-icon><IconMdiSearch /></n-icon></template>
                统计分析
              </n-button>
            </n-form-item>
          </n-form>
        </n-card>

        <!-- 图表区 -->
        <div ref="refChartWrap" class="chart-wrap">
          <n-spin :show="loading">
            <n-empty v-if="!result?.nodes?.length" description="点击「统计分析」开始" size="small" />
            <template v-else>
              <n-space size="small" align="center" style="margin-bottom: 8px">
                <n-tag v-for="(d, i) in paramsForm.groupBy" :key="i" size="small" :type="i === 0 ? 'primary' : 'default'">
                  {{ i + 1 }}.{{ dimLabel(d) }}
                </n-tag>
                <span style="color:#999;font-size:12px">共 {{ result.total }} 件 · 点击图块查看明细</span>
              </n-space>
              <n-card size="small" :title="`一级：${dimLabel(paramsForm.groupBy[0])}`" class="primary-chart">
                <HChart :option="primaryOption" style="height: 340px" @click="onChartClick" />
              </n-card>
              <!-- 多级嵌套：旭日图（支持 2~4 层钻取） -->
              <n-card v-if="hasChildren" size="small" title="多级关联（旭日图，点击任意环钻取明细）" style="margin-top: 12px">
                <HChart :option="sunburstOption" style="height: 460px" @click="onSunburstClick" />
              </n-card>
            </template>
          </n-spin>
        </div>
      </div>
    </div>

    <!-- 钻取明细弹层 -->
    <n-modal v-model:show="drillVisible" preset="card" style="width: 80%; max-width: 1200px" :title="drillTitle">
      <n-data-table
        :columns="drillColumns"
        :data="drillRows"
        :pagination="{ pageSize: 20 }"
        :scroll-x="1200"
        size="small"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="exportDrillCsv">导出 CSV</n-button>
          <n-button @click="drillVisible = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 全屏 -->
    <n-modal v-model:show="fullscreen" preset="card" style="width: 96vw; height: 92vh" :title="comboTitle">
      <div style="height: calc(92vh - 80px); overflow: auto">
        <HChart :option="primaryOption" style="height: 70vh" @click="onChartClick" />
      </div>
    </n-modal>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue';
import { useMessage } from 'naive-ui';
import HPrint from '@/components/basic/print/Print.vue';
import { crossStat, drill } from '../../api/analysis';
import { list as listProjects } from '../../api/project';
import { useDict } from '@/utils/dict';

const message = useMessage();

/**
 * 组合预设 — 对应统计分析.xlsx 1~10 行；groupBy 序列即"多级钻取"层次。
 * 维度枚举与后端 EnumCrossDim 对齐。
 */
const COMBOS = [
  { key: 'C1', title: '文化因素构成', groupBy: ['CULTURAL_ATTRIBUTE'] },
  { key: 'C2', title: '质地+陶系关联', groupBy: ['MATERIAL', 'POTTERY_QUALITY', 'POTTERY_COLOR', 'DECORATION'] },
  { key: 'C3', title: '文化因素+质地', groupBy: ['CULTURAL_ATTRIBUTE', 'MATERIAL'] },
  { key: 'C4', title: '遗迹单位类型', groupBy: ['CELL_TYPE'] },
  { key: 'C5', title: '时代+质地', groupBy: ['TIME_PERIOD', 'MATERIAL'] },
  { key: 'C6', title: '用途多级(一→二→三)', groupBy: ['PURPOSE_L1', 'PURPOSE_L2', 'PURPOSE_L3'] },
  { key: 'C7', title: '用途+器型+质地', groupBy: ['PURPOSE_L1', 'ARTIFACT_TYPE', 'MATERIAL'] },
  { key: 'C8', title: '时代+遗址', groupBy: ['TIME_PERIOD', 'PROJECT'] },
  { key: 'C9', title: '时代构成', groupBy: ['TIME_PERIOD'] },
  { key: 'C10', title: '遗址+质地', groupBy: ['PROJECT', 'MATERIAL'] },
  { key: 'C11', title: '质地+用途', groupBy: ['MATERIAL', 'PURPOSE_L1'] },
  { key: 'C12', title: '质地+器型', groupBy: ['MATERIAL', 'ARTIFACT_TYPE'] },
  { key: 'C13', title: '瓷器窑口+窑系', groupBy: ['KILN_SITE', 'KILN_SYSTEM'] },
];

/** 维度中文名（用于钻取面包屑/图例提示） */
const DIM_LABELS: Record<string, string> = {
  PROJECT: '遗址',
  PROJECT_PLOT: '探方',
  PROJECT_CELL: '遗迹单位',
  CELL_TYPE: '遗迹类型',
  MATERIAL: '质地',
  ARTIFACT_TYPE: '器型',
  TIME_PERIOD: '时代',
  CULTURAL_ATTRIBUTE: '文化因素',
  PURPOSE_L1: '用途一级',
  PURPOSE_L2: '用途二级',
  PURPOSE_L3: '用途三级',
  POTTERY_QUALITY: '陶质',
  POTTERY_COLOR: '陶色',
  DECORATION: '纹饰',
  KILN_SITE: '窑口',
  KILN_SYSTEM: '窑系',
  SAVE_STATUS: '保存状况',
  STORAGE_STATUS: '存放状态',
};

const leftTab = ref('project');
const filterTree = ref('');
const fullscreen = ref(false);
const loading = ref(false);
const refChartWrap = ref();
const result = ref<any>(null);

const projectTreeData = ref<any[]>([]);
const cultureTreeData = ref<any[]>([]);
const selectedProjectKeys = ref<string[]>([]);
const selectedCultureKeys = ref<string[]>([]);

interface ParamsForm {
  groupByKey: string;
  groupBy: string[];
  chartType: 'bar' | 'pie';
  projectId?: string;
  culturalAttribute?: string[];
  timePeriod?: string[];
  material?: string[];
  saveStatus?: string[];
  unearthDateList?: [number, number] | null;
}

const paramsForm = ref<ParamsForm>({
  groupByKey: 'C1',
  groupBy: ['CULTURAL_ATTRIBUTE'],
  chartType: 'bar',
});

const comboTitle = computed(() => {
  const c = COMBOS.find((x) => x.key === paramsForm.value.groupByKey);
  return c?.title ?? '统计';
});

const hasChildren = computed(() => paramsForm.value.groupBy.length > 1 && (result.value?.nodes ?? []).some((n: any) => n.children?.length));

function applyCombo(combo: { key: string; groupBy: string[] }) {
  paramsForm.value.groupByKey = combo.key;
  paramsForm.value.groupBy = combo.groupBy;
  fetchData();
}

function onSelectProject(keys: string[]) {
  selectedProjectKeys.value = keys;
  paramsForm.value.projectId = keys[0];
  fetchData();
}

function onSelectCulture(keys: string[]) {
  selectedCultureKeys.value = keys;
  paramsForm.value.culturalAttribute = keys.length ? keys : undefined;
  fetchData();
}

function buildParams() {
  const p: any = {
    groupBy: paramsForm.value.groupBy,
    chartType: paramsForm.value.chartType,
    projectId: paramsForm.value.projectId,
    culturalAttribute: paramsForm.value.culturalAttribute?.join(','),
    timePeriod: paramsForm.value.timePeriod?.join(','),
    material: paramsForm.value.material?.join(','),
    saveStatus: paramsForm.value.saveStatus?.join(','),
  };
  if (paramsForm.value.unearthDateList?.length === 2) {
    p.unearthDateStart = new Date(paramsForm.value.unearthDateList[0]).toISOString();
    p.unearthDateEnd = new Date(paramsForm.value.unearthDateList[1]).toISOString();
  }
  return p;
}

async function fetchData() {
  loading.value = true;
  try {
    const resp: any = await crossStat(buildParams());
    result.value = resp.data;
  } catch (e: any) {
    message.error(e?.message || '统计失败');
  } finally {
    loading.value = false;
  }
}

/** 一级图表 option */
const primaryOption = computed(() => {
  const nodes = result.value?.nodes ?? [];
  if (paramsForm.value.chartType === 'pie') {
    return {
      tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
      legend: { orient: 'vertical', left: 'left' },
      series: [{
        type: 'pie',
        radius: ['35%', '65%'],
        data: nodes.map((n: any) => ({ name: n.name, value: n.value })),
      }],
    };
  }
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, bottom: 60, top: 30 },
    xAxis: { type: 'category', data: nodes.map((n: any) => n.name), axisLabel: { interval: 0, rotate: 30 } },
    yAxis: { type: 'value', name: '数量(件)' },
    series: [{ type: 'bar', data: nodes.map((n: any) => n.value), itemStyle: { color: '#3DAEFF' } }],
  };
});

function dimLabel(dim: string): string {
  return DIM_LABELS[dim] ?? dim;
}

/** 旭日图 option：把后端嵌套 nodes 树直接喂给 echarts sunburst */
const sunburstOption = computed(() => {
  const toSun = (ns: any[]): any[] =>
    (ns ?? []).map((n: any) => ({
      name: n.name,
      value: n.value,
      ...(n.children?.length ? { children: toSun(n.children) } : {}),
    }));
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} 件' },
    series: [{
      type: 'sunburst',
      radius: ['12%', '95%'],
      data: toSun(result.value?.nodes ?? []),
      label: { minAngle: 8 },
      levels: [
        {},
        { r0: '12%', r: '40%', label: { rotate: 'tangential' } },
        { r0: '40%', r: '66%' },
        { r0: '66%', r: '82%', label: { align: 'right' } },
        { r0: '82%', r: '95%', label: { position: 'outside', padding: 3, silent: false } },
      ],
      emphasis: { focus: 'ancestor' },
    }],
  };
});

/** 钻取相关 */
const drillVisible = ref(false);
const drillTitle = ref('');
const drillRows = ref<any[]>([]);
const drillColumns = [
  { title: '编号', key: 'code', width: 140 },
  { title: '文物名称', key: 'name', width: 160 },
  { title: '材质', key: 'material', width: 100 },
  { title: '器型', key: 'artifactType', width: 100 },
  { title: '时代', key: 'timePeriod', width: 100 },
  { title: '文化因素', key: 'culturalAttribute', width: 120 },
  { title: '遗址', key: 'projectName', width: 140 },
  { title: '遗迹单位', key: 'projectCell', width: 120 },
  { title: '数量', key: 'quantity', width: 80 },
];

async function onChartClick(e: any) {
  const dim = paramsForm.value.groupBy[0];
  const name = e?.name;
  if (!dim || !name) return;
  await openDrill([[dim, name]]);
}

/** 旭日图点击：treePathInfo[0] 是根，后续每层对应 groupBy[i] */
async function onSunburstClick(e: any) {
  const info = e?.treePathInfo;
  const dims = paramsForm.value.groupBy;
  if (!info || info.length < 2) return;
  const path: Array<[string, string]> = [];
  for (let i = 1; i < info.length; i++) {
    const dim = dims[i - 1];
    if (dim && info[i]?.name) path.push([dim, info[i].name]);
  }
  if (path.length) await openDrill(path);
}

async function openDrill(path: Array<[string, string]>) {
  loading.value = true;
  try {
    const resp: any = await drill(buildParams(), path);
    drillRows.value = resp.data ?? [];
    drillTitle.value = `钻取明细：${path.map((p) => p[1]).join(' / ')}（${drillRows.value.length}件）`;
    drillVisible.value = true;
  } catch (e: any) {
    message.error(e?.message || '钻取失败');
  } finally {
    loading.value = false;
  }
}

function exportDrillCsv() {
  if (!drillRows.value.length) return;
  const headers = drillColumns.map((c: any) => c.title);
  const keys = drillColumns.map((c: any) => c.key);
  const csv = [headers.join(','), ...drillRows.value.map((r) => keys.map((k) => `"${r[k] ?? ''}"`).join(','))].join('\n');
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `${drillTitle.value}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}

/** 初始化左侧树 */
async function initTrees() {
  try {
    const resp: any = await listProjects({ pageSize: 0 });
    const list = resp.data?.list ?? resp.data ?? [];
    projectTreeData.value = list.map((p: any) => ({ key: p.id, label: p.name }));
  } catch {
    /* ignore */
  }
  try {
    const { options } = useDict('wms_artifact_cultural_attribute');
    // 等异步加载，简单 setTimeout 等一帧
    setTimeout(() => {
      const list: any[] = options.value || [];
      cultureTreeData.value = list.map((d: any) => ({ key: d.value, label: d.label }));
    }, 50);
  } catch {
    /* ignore */
  }
}

onMounted(() => {
  initTrees();
  fetchData();
});
</script>

<style scoped>
.analysis-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 12px;
}
.analysis-left {
  width: 260px;
  border-right: 1px solid #eee;
  padding: 8px;
  overflow: auto;
}
.analysis-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 8px;
  overflow: hidden;
}
.combo-card,
.filter-card {
  flex: 0 0 auto;
  flex-wrap: wrap;
}
.chart-wrap {
  flex: 1;
  overflow: auto;
  padding: 4px;
}
.primary-chart :deep(.n-card-header) {
  padding: 8px 16px;
}
</style>
