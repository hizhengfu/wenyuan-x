<template>
  <div class="flow-stat">
    <!-- 顶部操作栏：仅 showFilters 或 showRefresh 任一为真时渲染 -->
    <div v-if="showFilters || showRefresh" class="flow-stat__header">
      <div class="flow-stat__filters" v-if="showFilters">
        <n-form inline :show-feedback="false" size="small">
          <n-form-item label="流程">
            <FlowDefinitionSelect
              v-model:value="filters.definitionKey"
              placeholder="全部流程"
              style="width: 200px"
              @update:value="loadAll"
            />
          </n-form-item>
          <n-form-item label="时间范围">
            <n-date-picker
              v-model:value="dateRange"
              type="daterange"
              clearable
              :is-date-disabled="disableFutureDate"
              format="yyyy-MM-dd"
              @update:value="onDateRangeChange"
            />
          </n-form-item>
          <n-form-item label="发起人">
            <n-input
              v-model:value="filters.initiatorId"
              placeholder="发起人 ID"
              clearable
              style="width: 160px"
              @blur="loadAll"
              @keydown.enter="loadAll"
            />
          </n-form-item>
        </n-form>
      </div>
      <n-button v-if="showRefresh" :loading="loading" size="small" @click="loadAll">
        <template #icon>
          <n-icon><HIcon icon="mdi:refresh" /></n-icon>
        </template>
        刷新
      </n-button>
    </div>

    <!-- 概览卡片 -->
    <n-grid :cols="cardCols" :x-gap="12" :y-gap="12" responsive="screen">
      <n-gi v-for="card in overviewCards" :key="card.key">
        <n-card
          class="flow-stat__card"
          :class="`flow-stat__card--${card.color}`"
          hoverable
          size="small"
          @click="card.onClick && card.onClick()"
        >
          <div class="flow-stat__card-row">
            <div class="flow-stat__card-icon">
              <n-icon :size="28">
                <HIcon :icon="card.icon" />
              </n-icon>
            </div>
            <div class="flow-stat__card-info">
              <div class="flow-stat__card-label">{{ card.label }}</div>
              <div class="flow-stat__card-value">{{ card.value }}</div>
            </div>
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <!-- 趋势 + 状态分布 -->
    <n-grid :cols="2" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-top: 12px">
      <n-gi span="2 m:1">
        <n-card title="发起 / 完成趋势" size="small">
          <template #header-extra>
            <n-radio-group v-model:value="trendDays" size="small" @update:value="loadTrend">
              <n-radio-button :value="7">近 7 天</n-radio-button>
              <n-radio-button :value="30">近 30 天</n-radio-button>
              <n-radio-button :value="90">近 90 天</n-radio-button>
            </n-radio-group>
          </template>
          <h-chart v-if="trendOption" :option="trendOption" style="height: 320px" />
          <n-empty v-else description="暂无趋势数据" style="padding: 80px 0" />
        </n-card>
      </n-gi>
      <n-gi span="2 m:1">
        <n-card title="实例状态分布" size="small">
          <h-chart v-if="instancePieOption" :option="instancePieOption" style="height: 320px" />
          <n-empty v-else description="暂无数据" style="padding: 80px 0" />
        </n-card>
      </n-gi>
    </n-grid>

    <!-- 任务分布 + 流程排名 -->
    <n-grid :cols="2" :x-gap="12" :y-gap="12" responsive="screen" item-responsive style="margin-top: 12px">
      <n-gi span="2 m:1">
        <n-card title="任务状态分布" size="small">
          <h-chart v-if="taskPieOption" :option="taskPieOption" style="height: 320px" />
          <n-empty v-else description="暂无数据" style="padding: 80px 0" />
        </n-card>
      </n-gi>
      <n-gi span="2 m:1">
        <n-card title="流程实例 TOP 10" size="small">
          <h-chart v-if="rankingBarOption" :option="rankingBarOption" style="height: 320px" />
          <n-empty v-else description="暂无数据" style="padding: 80px 0" />
        </n-card>
      </n-gi>
    </n-grid>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, inject, onMounted, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import HIcon from '@/components/basic/DynamicIcon.vue'
import {
  getOverview,
  getInstanceStatus,
  getTaskStatus,
  getDefinitionRanking,
  getTrend,
  type FlowStatisticsScope,
  type FlowStatisticsFilters,
} from '../api/statistics'
import type {
  FlowStatisticsOverview,
  FlowStatusCount,
  FlowDefinitionStat,
  FlowTrendPoint,
} from '../typings/flow'
import FlowDefinitionSelect from './FlowDefinitionSelect.vue'

const props = withDefaults(
  defineProps<{
    /** 视角：mine（个人）/ global（仅 ADMIN） */
    scope: FlowStatisticsScope
    /** 是否显示筛选区（流程/时间范围/发起人）；通常仅 global 视角开启 */
    showFilters?: boolean
    /** 是否显示组件内部的"刷新"按钮；嵌入在外层（如任务中心 Tab 栏）已有刷新入口时设为 false */
    showRefresh?: boolean
  }>(),
  {
    showFilters: false,
    showRefresh: true,
  },
)

const router = useRouter()

const loading = ref(false)
const trendDays = ref(30)

const filters = ref<FlowStatisticsFilters>({
  definitionKey: undefined,
  startDate: undefined,
  endDate: undefined,
  initiatorId: undefined,
})

// n-date-picker 用 timestamp[2]，需要与 filters.startDate/endDate（yyyy-MM-dd 字符串）转换
const dateRange = ref<[number, number] | null>(null)

const overview = ref<FlowStatisticsOverview | null>(null)
const instanceStatus = ref<FlowStatusCount[]>([])
const taskStatus = ref<FlowStatusCount[]>([])
const ranking = ref<FlowDefinitionStat[]>([])
const trend = ref<FlowTrendPoint[]>([])

const cardCols = computed(() => 'xs:1 s:3 m:5 l:5')

// ─── 概览卡片定义 ─────────────────────────────────────────────

interface CardItem {
  key: string
  label: string
  value: number
  icon: string
  color: string
  onClick?: () => void
}

const overviewCards = computed<CardItem[]>(() => {
  if (!overview.value) return []
  const o = overview.value

  if (props.scope === 'global' && o.totalDefinitions != null) {
    return [
      {
        key: 'totalDef',
        label: '流程定义',
        value: o.totalDefinitions ?? 0,
        icon: 'mdi:draw',
        color: 'blue',
        onClick: () => router.push('/flow/definition'),
      },
      {
        key: 'activeDef',
        label: '已发布',
        value: o.activeDefinitions ?? 0,
        icon: 'mdi:check-decagram-outline',
        color: 'green',
      },
      {
        key: 'totalInst',
        label: '实例总数',
        value: o.totalInstances ?? 0,
        icon: 'mdi:file-tree-outline',
        color: 'cyan',
      },
      {
        key: 'runningInst',
        label: '进行中',
        value: o.runningInstances ?? 0,
        icon: 'mdi:progress-clock',
        color: 'purple',
      },
      {
        key: 'todayInst',
        label: '今日发起',
        value: o.todayInstances ?? 0,
        icon: 'mdi:calendar-today',
        color: 'orange',
      },
      {
        key: 'overdueTasks',
        label: '超时任务',
        value: o.overdueTasks ?? 0,
        icon: 'mdi:alarm-light-outline',
        color: 'red',
      },
    ]
  }

  return [
    {
      key: 'todo',
      label: '我的待办',
      value: o.todoCount,
      icon: 'mdi:clipboard-text-clock-outline',
      color: 'blue',
      onClick: () => router.push({ path: '/flow/task', query: { tab: 'todo' } }),
    },
    {
      key: 'done',
      label: '我的已办',
      value: o.doneCount,
      icon: 'mdi:clipboard-check-outline',
      color: 'green',
      onClick: () => router.push({ path: '/flow/task', query: { tab: 'done' } }),
    },
    {
      key: 'initiated',
      label: '我发起的',
      value: o.myRunningCount + o.myCompletedCount + o.myCanceledCount + o.myRejectedCount,
      icon: 'mdi:send-outline',
      color: 'cyan',
      onClick: () => router.push({ path: '/flow/task', query: { tab: 'initiated' } }),
    },
    {
      key: 'cc',
      label: '抄送未读',
      value: o.ccUnreadCount,
      icon: 'mdi:email-arrow-right-outline',
      color: 'orange',
      onClick: () => router.push({ path: '/flow/task', query: { tab: 'cc' } }),
    },
    {
      key: 'overdue',
      label: '我的超时',
      value: o.myOverdueCount,
      icon: 'mdi:alarm-light-outline',
      color: 'red',
      onClick: () => router.push({ path: '/flow/task', query: { tab: 'overdue' } }),
    },
  ]
})

// ─── 图表配置 ─────────────────────────────────────────────────

const STATUS_COLORS: Record<string, string> = {
  RUNNING: '#2080f0',
  COMPLETED: '#18a058',
  CANCELED: '#909399',
  REJECTED: '#d03050',
  PENDING: '#2080f0',
  TRANSFERRED: '#f0a020',
  SKIPPED: '#909399',
}

function buildPieOption(title: string, list: FlowStatusCount[]) {
  const data = list.filter((it) => it.count > 0)
  if (!data.length) return null
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    color: data.map((d) => STATUS_COLORS[d.status] || '#909399'),
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: true,
        label: { formatter: '{b}\n{c}' },
        data: data.map((d) => ({ name: d.label, value: d.count })),
      },
    ],
  }
}

const instancePieOption = computed(() => buildPieOption('实例状态', instanceStatus.value))
const taskPieOption = computed(() => buildPieOption('任务状态', taskStatus.value))

const rankingBarOption = computed(() => {
  const list = ranking.value
  if (!list.length) return null
  const names = list.map((it) => it.definitionName).reverse()
  const running = list.map((it) => it.running).reverse()
  const completed = list.map((it) => it.completed).reverse()
  const rejected = list.map((it) => it.rejected).reverse()
  const canceled = list.map((it) => it.canceled).reverse()
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { bottom: 0 },
    grid: { left: 100, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: names },
    series: [
      { name: '进行中', type: 'bar', stack: 'total', color: STATUS_COLORS.RUNNING, data: running },
      { name: '已完成', type: 'bar', stack: 'total', color: STATUS_COLORS.COMPLETED, data: completed },
      { name: '已驳回', type: 'bar', stack: 'total', color: STATUS_COLORS.REJECTED, data: rejected },
      { name: '已撤销', type: 'bar', stack: 'total', color: STATUS_COLORS.CANCELED, data: canceled },
    ],
  }
})

const trendOption = computed(() => {
  const list = trend.value
  if (!list.length) return null
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 40, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: list.map((it) => it.date.slice(5)),
      boundaryGap: false,
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '发起',
        type: 'line',
        smooth: true,
        color: '#2080f0',
        data: list.map((it) => it.started),
        areaStyle: { opacity: 0.15 },
      },
      {
        name: '完成',
        type: 'line',
        smooth: true,
        color: '#18a058',
        data: list.map((it) => it.finished),
        areaStyle: { opacity: 0.15 },
      },
    ],
  }
})

// ─── 数据加载 ─────────────────────────────────────────────────

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadInstanceStatus(), loadTaskStatus(), loadRanking(), loadTrend()])
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  const res = await getOverview(props.scope, filters.value)
  overview.value = res.data?.data ?? res.data
}

async function loadInstanceStatus() {
  const res = await getInstanceStatus(props.scope, filters.value)
  instanceStatus.value = res.data?.data ?? res.data ?? []
}

async function loadTaskStatus() {
  const res = await getTaskStatus(props.scope, filters.value)
  taskStatus.value = res.data?.data ?? res.data ?? []
}

async function loadRanking() {
  const res = await getDefinitionRanking(props.scope, 10, filters.value)
  ranking.value = res.data?.data ?? res.data ?? []
}

async function loadTrend() {
  const res = await getTrend(props.scope, trendDays.value, filters.value)
  trend.value = res.data?.data ?? res.data ?? []
}

// ─── 工具 ─────────────────────────────────────────────────────

function disableFutureDate(ts: number) {
  return ts > Date.now()
}

function formatDate(ts: number): string {
  const d = new Date(ts)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function onDateRangeChange(range: [number, number] | null) {
  if (range && range.length === 2) {
    filters.value.startDate = formatDate(range[0])
    filters.value.endDate = formatDate(range[1])
  } else {
    filters.value.startDate = undefined
    filters.value.endDate = undefined
  }
  loadAll()
}

watch(() => props.scope, () => loadAll())

// 任务中心容器通过 provide('flowTaskRefresh', signal) 触发全局刷新；
// 此组件被嵌在 dashboard.vue 中时会响应；独立用在 statistics 页时 inject 默认 null，不影响。
const refreshSignal = inject<Ref<number> | null>('flowTaskRefresh', null)
if (refreshSignal) {
  watch(refreshSignal, () => loadAll())
}

onMounted(() => {
  loadAll()
})

defineExpose({ loadAll })
</script>

<style scoped lang="scss">
.flow-stat {
  padding: 12px;
  width: 100%;

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    gap: 12px;
    flex-wrap: wrap;
  }

  &__filters {
    flex: 1;
    min-width: 0;
  }

  &__card {
    cursor: pointer;
    transition: transform 0.15s ease, box-shadow 0.15s ease;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }
  }

  &__card-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  &__card-icon {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(32, 128, 240, 0.1);
    color: #2080f0;
  }

  &__card-info {
    flex: 1;
    min-width: 0;
  }

  &__card-label {
    font-size: 13px;
    color: #666;
    margin-bottom: 4px;
  }

  &__card-value {
    font-size: 22px;
    font-weight: 600;
    line-height: 1.2;
  }

  &__card--blue &__card-icon {
    background: rgba(32, 128, 240, 0.1);
    color: #2080f0;
  }
  &__card--green &__card-icon {
    background: rgba(24, 160, 88, 0.1);
    color: #18a058;
  }
  &__card--cyan &__card-icon {
    background: rgba(24, 144, 255, 0.1);
    color: #1890ff;
  }
  &__card--orange &__card-icon {
    background: rgba(240, 160, 32, 0.1);
    color: #f0a020;
  }
  &__card--red &__card-icon {
    background: rgba(208, 48, 80, 0.1);
    color: #d03050;
  }
  &__card--purple &__card-icon {
    background: rgba(144, 88, 192, 0.1);
    color: #9058c0;
  }
}
</style>
