<template>
  <div class="task-center">
    <n-tabs :value="currentTab" type="line" animated @update:value="onTabChange">
      <n-tab v-for="t in tabs" :key="t.name" :name="t.name">
        <n-badge
          :value="counts[t.name] ?? 0"
          :max="99"
          :type="t.badgeType"
          :show="(counts[t.name] ?? 0) > 0"
        >
          <span class="task-center__tab">{{ t.label }}</span>
        </n-badge>
      </n-tab>
      <template #suffix>
        <n-tooltip placement="left" trigger="hover">
          <template #trigger>
            <n-button
              quaternary
              circle
              size="small"
              :loading="refreshing"
              class="task-center__refresh"
              aria-label="刷新"
              @click="onRefresh"
            >
              <template #icon>
                <n-icon><HIcon icon="mdi:refresh" /></n-icon>
              </template>
            </n-button>
          </template>
          刷新
        </n-tooltip>
      </template>
    </n-tabs>

    <div class="task-center__body">
      <keep-alive>
        <component :is="currentComponent" :key="currentTab" :showHeader="false" />
      </keep-alive>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, provide, ref, watch, type Component } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getOverview } from '../../api/statistics';
import type { FlowStatisticsOverview } from '../../typings/flow';
import HIcon from '@/components/basic/DynamicIcon.vue';

import DashboardPane from '../../components/task-panes/DashboardPane.vue';
import BusinessTypeCardsPane from '../../components/task-panes/BusinessTypeCardsPane.vue';
import TodoPane from '../../components/task-panes/TodoPane.vue';
import DonePane from '../../components/task-panes/DonePane.vue';
import InitiatedPane from '../../components/task-panes/InitiatedPane.vue';
import AllInstancesPane from '../../components/task-panes/AllInstancesPane.vue';
import CcPane from '../../components/task-panes/CcPane.vue';
import OverduePane from '../../components/task-panes/OverduePane.vue';
import { useAuthStore } from '@/store';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

type TabName = 'dashboard' | 'modules' | 'todo' | 'done' | 'initiated' | 'all' | 'cc' | 'overdue';

interface TabItem {
  name: TabName;
  label: string;
  component: Component;
  badgeType?: 'default' | 'info' | 'success' | 'warning' | 'error';
  requiresFlowAdmin?: boolean;
}

/**
 * 使用 markRaw 避免 Vue 把组件代理成响应式对象（性能优化 + 消除控制台警告）。
 * 所有 pane 都是无 props 的容器组件，模板里的 :is 切换不需要 v-bind props。
 *
 * "按模块"Tab 展示按 businessType 聚合的卡片网格，点击卡片跳到 todo Tab 并带 businessType 过滤。
 */
const canViewAllInstances = computed(() => authStore.hasPermission('ADMIN,FLOW_ALL,FLOW_MANAGE'));

const allTabs: TabItem[] = [
  { name: 'dashboard', label: '工作台', component: markRaw(DashboardPane) },
  { name: 'modules', label: '按模块', component: markRaw(BusinessTypeCardsPane), badgeType: 'info' },
  { name: 'todo', label: '我的待办', component: markRaw(TodoPane), badgeType: 'error' },
  { name: 'done', label: '我的已办', component: markRaw(DonePane) },
  { name: 'initiated', label: '我发起的', component: markRaw(InitiatedPane), badgeType: 'info' },
  { name: 'all', label: '全部流程', component: markRaw(AllInstancesPane), requiresFlowAdmin: true },
  { name: 'cc', label: '抄送我的', component: markRaw(CcPane), badgeType: 'warning' },
  { name: 'overdue', label: '超时催办', component: markRaw(OverduePane), badgeType: 'error' },
];

const tabs = computed(() => allTabs.filter((tab) => !tab.requiresFlowAdmin || canViewAllInstances.value));

/** 从 URL query.tab 解析当前 Tab，缺省/非法时回退到 dashboard */
const currentTab = computed<TabName>(() => {
  const t = String(route.query.tab ?? '');
  return tabs.value.find((it) => it.name === t)?.name ?? 'dashboard';
});

const currentComponent = computed<Component>(
  () => tabs.value.find((t) => t.name === currentTab.value)?.component ?? DashboardPane,
);

const counts = ref<Record<TabName, number>>({
  dashboard: 0,
  modules: 0,
  todo: 0,
  done: 0,
  initiated: 0,
  all: 0,
  cc: 0,
  overdue: 0,
});

const refreshing = ref(false);

/** 子组件 inject 'flowTaskRefresh' 后 watch 该 ref，自增即触发各自的数据重载（不重挂组件） */
const refreshSignal = ref(0);
provide('flowTaskRefresh', refreshSignal);

function onTabChange(name: TabName) {
  // 仅替换 query，不会触发整页 router 切换（route.path 不变）
  router.replace({ path: '/flow/task', query: { tab: name } });
}

async function loadCounts() {
  try {
    const res = await getOverview('mine');
    const o = (res.data?.data ?? res.data) as FlowStatisticsOverview | null;
    if (!o) return;
    counts.value = {
      dashboard: 0,
      // 同一待办数量只在“我的待办”显示。
      modules: 0,
      todo: o.todoCount ?? 0,
      done: 0,
      initiated: o.myRunningCount ?? 0,
      all: 0,
      cc: o.ccUnreadCount ?? 0,
      overdue: o.myOverdueCount ?? 0,
    };
  } catch {
    /* 静默失败 */
  }
}

async function onRefresh() {
  refreshing.value = true;
  refreshSignal.value++;
  try {
    await loadCounts();
  } finally {
    setTimeout(() => (refreshing.value = false), 200);
  }
}

/** 兼容老的子路径（/flow/task/todo 等）：归一化到 query 写法 */
function normalizeLegacyPath() {
  // 例：/flow/task/todo → tab=todo
  const m = route.path.match(/^\/flow\/task\/([^/]+)\/?$/);
  if (m && tabs.value.some((t) => t.name === m[1])) {
    router.replace({ path: '/flow/task', query: { tab: m[1] } });
    return;
  }
  if (route.path === '/flow/task' && !route.query.tab) {
    router.replace({ path: '/flow/task', query: { tab: 'dashboard' } });
  }
}

onMounted(() => {
  normalizeLegacyPath();
  loadCounts();
});
watch(() => route.fullPath, normalizeLegacyPath);
// 仅当 query.tab 变化时刷新统计数（避免冗余请求）
watch(
  () => route.query.tab,
  () => loadCounts(),
);
</script>

<style scoped lang="scss">
.task-center {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;

  :deep(.n-tabs) {
    flex-shrink: 0;
    padding: 12px 12px 0;
  }

  &__tab {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  &__refresh {
    margin-right: 4px;
  }

  &__body {
    flex: 1;
    display: flex;
    flex-direction: column;
    position: relative;
    min-height: 0;
    overflow-x: hidden;
    overflow-y: auto;
    margin-top: 15px;
    background: var(--n-color, #fff);

    :deep(.pageTable) {
      position: relative;
    }

    :deep(.pageTable .page-table-toolbar) {
      position: absolute !important;
      top: 8px !important;
      right: 12px !important;
      z-index: 3;
    }
  }
}
</style>
