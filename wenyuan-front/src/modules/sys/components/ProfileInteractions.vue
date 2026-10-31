<template>
  <section class="profile-interactions" :aria-label="props.kind ? currentTab.label : '我的足迹与互动'">
    <n-tabs
      v-if="!props.kind"
      :value="active"
      type="line"
      :theme-overrides="{ tabGapMediumLine: '20px' }"
      animated
      @update:value="changeTab"
    >
      <n-tab v-for="tab in tabs" :key="tab.key" :name="tab.key">{{ tab.label }}</n-tab>
    </n-tabs>
    <div class="interaction-toolbar">
      <n-text depth="3">{{ currentTab.description }}</n-text>
      <n-space :size="8">
        <n-button size="small" :disabled="loading || busy" @click="load">刷新</n-button>
        <n-popconfirm v-if="active === 'recent' || active === 'shares'" @positive-click="clearCurrent">
          <template #trigger>
            <n-button size="small" type="error" :disabled="!total || loading || busy || !!error">清空记录</n-button>
          </template>
          {{ active === 'recent' ? '清空当前 Web 客户端的浏览记录？' : '清空我的分享记录？历史分享次数仍会保留。' }}
        </n-popconfirm>
      </n-space>
    </div>
    <n-alert v-if="actionError" type="error" :show-icon="false" class="interaction-error" role="alert">{{
      actionError
    }}</n-alert>
    <HPageDataTable
      :key="`${active}:${tableVersion}`"
      ref="tableRef"
      class="interaction-table"
      :columns="columns"
      :fetch-data="fetchRows"
      :initial-page="paging[active].pageNum"
      :initial-page-size="paging[active].pageSize"
      :pagination="{ pageSizes: [10, 20, 50], pageSlot: 5, showQuickJumper: false, disabled: busy }"
      reset-page-on-size-change
      auto-scroll-x
      :show-header="false"
      :show-toolbar="false"
      :show-operate="false"
      :bordered="false"
      :enable-drag="false"
      row-class-name="interaction-row"
    >
      <template #empty>
        <n-result v-if="error" status="error" title="加载失败" description="暂时无法获取记录，请稍后重试。">
          <template #footer><n-button @click="load">重新加载</n-button></template>
        </n-result>
        <n-empty v-else :description="currentTab.empty" />
      </template>
    </HPageDataTable>
  </section>
</template>

<script setup lang="ts">
import { computed, h, onActivated, onBeforeUnmount, onDeactivated, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { NA, NButton, NPopconfirm, NTime, type DataTableColumns } from 'naive-ui';
import HPageDataTable from '@/components/data/table/PageDataTable.vue';
import { useAuthStore } from '@/store';
import {
  clearInteractions,
  defaultWebInteractionClient,
  interactionList,
  removeInteraction,
  type InteractionClient,
  type InteractionItem,
  type InteractionListKind,
} from '../api/interaction';
import { interactionLocation, type InteractionPageResolver } from '../utils/interactionNavigation';

// 平台可覆盖产品分组和稳定页面路由映射，列表本身始终按 Web 客户端查询。
const props = defineProps<{
  client?: InteractionClient;
  resolvePage?: InteractionPageResolver;
  kind?: InteractionListKind;
}>();
const client = computed<InteractionClient>(() => ({ ...defaultWebInteractionClient, ...props.client, type: 'WEB' }));
const router = useRouter();
const auth = useAuthStore();
const tabs: {
  key: InteractionListKind;
  label: string;
  description: string;
  empty: string;
  timeLabel: string;
  removeLabel: string;
  removeConfirm: string;
}[] = [
  {
    key: 'recent',
    label: '最近浏览',
    description: '按最近访问时间排序，仅显示当前 Web 客户端的记录',
    empty: '暂无浏览记录',
    timeLabel: '浏览时间',
    removeLabel: '移除',
    removeConfirm: '移除这条浏览记录？',
  },
  {
    key: 'shares',
    label: '我的分享',
    description: '记录每次发起的分享，按分享时间排序',
    empty: '暂无分享记录',
    timeLabel: '分享时间',
    removeLabel: '删除记录',
    removeConfirm: '删除这条分享记录？历史分享次数仍会保留。',
  },
  {
    key: 'favorites',
    label: '我的收藏',
    description: '按收藏时间排序',
    empty: '暂无收藏内容',
    timeLabel: '收藏时间',
    removeLabel: '取消收藏',
    removeConfirm: '取消收藏这条内容？',
  },
  {
    key: 'likes',
    label: '我的点赞',
    description: '按点赞时间排序',
    empty: '暂无点赞内容',
    timeLabel: '点赞时间',
    removeLabel: '取消点赞',
    removeConfirm: '取消点赞这条内容？',
  },
];
const active = ref<InteractionListKind>(props.kind || 'recent');
const currentTab = computed(() => tabs.find((tab) => tab.key === active.value)!);
// 各列表独立保留分页状态，切换标签不会丢失用户当前页。
const paging = reactive<Record<InteractionListKind, { pageNum: number; pageSize: number }>>({
  recent: { pageNum: 1, pageSize: 10 },
  shares: { pageNum: 1, pageSize: 10 },
  favorites: { pageNum: 1, pageSize: 10 },
  likes: { pageNum: 1, pageSize: 10 },
});
const tableRef = ref<InstanceType<typeof HPageDataTable>>();
const tableVersion = ref(0);
const total = ref(0);
const loading = ref(false);
const busy = ref(false);
const pendingId = ref('');
const error = ref(false);
const actionError = ref('');
// 仅声明业务列，表格尺寸、滚动、加载状态和分页均由统一组件处理。
const columns = computed<DataTableColumns<InteractionItem>>(() => [
  {
    title: '标题',
    key: 'title',
    minWidth: 100,
    ellipsis: { tooltip: true },
    render: (row) => {
      const title = row.available ? row.page?.title || '未命名内容' : '内容已失效或暂无访问权限';
      const location = interactionLocation(row, client.value, router, props.resolvePage);
      return location
        ? h(
            NA,
            {
              class: 'interaction-title',
              href: router.resolve(location).href,
              title,
              onClick: (event: MouseEvent) => {
                event.preventDefault();
                void openItem(row);
              },
            },
            { default: () => title },
          )
        : h('span', { title: row.available ? `${title}（当前客户端暂不支持打开此内容）` : title }, title);
    },
  },
  ...(active.value === 'shares'
    ? [
        {
          title: '分享来源',
          key: 'source',
          width: 130,
          ellipsis: { tooltip: true },
          render: (row: InteractionItem) => shareSource(row) || '—',
        },
      ]
    : []),
  {
    title: currentTab.value.timeLabel,
    key: 'time',
    width: 140,
    render: (row) => h('span', { class: 'interaction-time' }, h(NTime, { time: row.time, format: 'yyyy-MM-dd HH:mm' })),
  },
  {
    title: '操作',
    key: 'actions',
    width: 100,
    align: 'center',
    fixed: 'right',
    render: (row) =>
      h(
        NPopconfirm,
        { onPositiveClick: () => removeItem(row) },
        {
          trigger: () =>
            h(
              NButton,
              {
                size: 'small',
                type: 'error',
                disabled: busy.value || loading.value,
                loading: pendingId.value === row.id,
              },
              { default: () => currentTab.value.removeLabel },
            ),
          default: () => currentTab.value.removeConfirm,
        },
      ),
  },
]);
let requestId = 0;
let mutationId = 0;
let abort: AbortController | undefined;
let suspended = false;

/** 使旧请求序号失效并取消网络读取，防止旧响应覆盖当前标签或身份的数据。 */
function cancelLoad() {
  ++requestId;
  abort?.abort();
  loading.value = false;
}
/** 刷新当前页，并取消上一轮读取；数据与加载展示交给统一表格。 */
function load() {
  cancelLoad();
  error.value = false;
  if (suspended) return;
  loading.value = true;
  tableRef.value?.handleRefresh();
}

/**
 * 将统一表格的分页参数交给个人列表接口，只处理当前身份与标签的响应。
 * 删除导致页码超界时切到最后一个有效页；失败保留明确的重试入口。
 */
async function fetchRows({ pageNum, pageSize }: { pageNum: number; pageSize: number }) {
  cancelLoad();
  error.value = false;
  if (suspended || !auth.userInfo?.id || auth.identitySyncing) return null;
  const id = requestId;
  const kind = active.value;
  paging[kind] = { pageNum, pageSize };
  abort = new AbortController();
  loading.value = true;
  try {
    const response = await interactionList(kind, pageNum, pageSize, { ...client.value }, abort.signal);
    if (id !== requestId) return null;
    const data = response.data;
    if (!data || !Array.isArray(data.list) || !Number.isFinite(data.total) || data.total < 0)
      throw new Error('invalid page');
    total.value = data.total;
    const lastPage = Math.max(1, Math.ceil(data.total / pageSize));
    if (pageNum > lastPage) {
      paging[kind].pageNum = lastPage;
      tableRef.value?.handlePageChange(lastPage);
      return null;
    }
    return { data: { ...data, pages: lastPage } };
  } catch {
    if (id !== requestId) return null;
    error.value = true;
    total.value = 0;
    return { data: { list: [], total: 0, pages: 1 } };
  } finally {
    if (id === requestId) loading.value = false;
  }
}

/** 切换后由新表格实例加载，各标签独立记住页码和每页条数。 */
function changeTab(value: InteractionListKind) {
  if (active.value === value) return;
  cancelLoad();
  active.value = value;
  total.value = 0;
  error.value = false;
  actionError.value = '';
}

/**
 * 串行提交一次移除或清空，防止重复点击；只刷新仍停留的原标签。
 * 账号变化或卸载后使旧操作回调失效；组件隐藏期间不更新列表和错误提示。
 */
async function mutate(operation: () => Promise<unknown>, id = '') {
  if (busy.value) return;
  const ticket = ++mutationId;
  const kind = active.value;
  busy.value = true;
  pendingId.value = id;
  actionError.value = '';
  try {
    await operation();
    if (ticket !== mutationId || suspended) return;
    if (active.value === kind) load();
  } catch {
    if (ticket === mutationId && active.value === kind && !suspended) actionError.value = '操作失败，请重试。';
  } finally {
    if (ticket === mutationId) {
      busy.value = false;
      pendingId.value = '';
    }
  }
}
/** 捕获确认时的列表类型和客户端，移除这一条记录或取消对应收藏/点赞状态。 */
function removeItem(row: InteractionItem) {
  const kind = active.value;
  const sourceClient = { ...client.value };
  return mutate(() => removeInteraction(kind, row, sourceClient), row.id);
}
/** 仅允许清空当前浏览或分享列表，并固定本次操作的客户端上下文。 */
function clearCurrent() {
  const kind = active.value;
  if (kind !== 'recent' && kind !== 'shares') return;
  const sourceClient = { ...client.value };
  return mutate(() => clearInteractions(kind, sourceClient));
}
/** 点击时重新按当前版本解析目标，避免直接使用失效或其他客户端的历史路径。 */
async function openItem(row: InteractionItem) {
  const location = interactionLocation(row, client.value, router, props.resolvePage);
  if (!location) return;
  try {
    await router.push(location);
  } catch {
    actionError.value = '暂时无法打开此内容，请稍后重试。';
  }
}
/** 将分享来源客户端类型转换为用户可读名称。 */
function clientLabel(type: InteractionClient['type']) {
  return { WEB: 'Web', MINI_PROGRAM: '小程序', APP: 'App' }[type];
}
/** 显示常见分享渠道名称；未知渠道保留服务端原值，便于兼容业务扩展。 */
function channelLabel(channel: string) {
  return (
    ({ 'copy-link': '复制链接', wechat: '微信', moments: '朋友圈', system: '系统分享' } as Record<string, string>)[
      channel
    ] || channel
  );
}

/** 在同一行显示分享客户端与渠道，悬停时可查看完整来源。 */
function shareSource(row: InteractionItem) {
  return [row.client && clientLabel(row.client.type), row.channel && channelLabel(row.channel)]
    .filter(Boolean)
    .join(' · ');
}

// 嵌入个人中心时由外层统一导航，独立使用时仍显示组件自己的四个标签。
watch(
  () => props.kind,
  (kind) => {
    if (kind) changeTab(kind);
  },
);

// 身份、产品或路由版本变化后立即清空旧数据，并使未完成的写操作回调失效。
watch(
  () => [auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, client.value.appId, client.value.routeVersion],
  () => {
    ++mutationId;
    busy.value = false;
    pendingId.value = '';
    actionError.value = '';
    total.value = 0;
    for (const page of Object.values(paging)) page.pageNum = 1;
    cancelLoad();
    error.value = false;
    ++tableVersion.value;
  },
  { immediate: true, flush: 'sync' },
);
// KeepAlive 隐藏期间不接受旧读取；重新激活时刷新一次，首次激活避免重复请求。
onDeactivated(() => {
  suspended = true;
  cancelLoad();
});
onActivated(() => {
  if (suspended) {
    suspended = false;
    load();
  }
});
// 组件卸载后，读写请求的回调都不能再写入页面状态。
onBeforeUnmount(() => {
  suspended = true;
  ++mutationId;
  cancelLoad();
});
</script>

<style scoped>
.profile-interactions {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.profile-interactions > :deep(.n-tabs),
.interaction-toolbar,
.interaction-error {
  flex-shrink: 0;
}
.interaction-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 16px;
}
.interaction-error {
  margin-bottom: 12px;
}
/* 表格占满余下高度，分页由 HPageDataTable 固定在表格底部。 */
.interaction-table {
  flex: 1;
  height: auto;
  min-height: 0;
}
.interaction-table :deep(.n-card-content) {
  min-height: 0;
  padding: 0 !important;
  overflow: hidden !important;
}
.interaction-table :deep(.n-data-table) {
  min-height: 0;
}
.interaction-table :deep(.interaction-time) {
  font-size: 13px;
  white-space: nowrap;
}
.interaction-table :deep(.n-pagination) {
  flex-wrap: wrap;
  row-gap: 8px;
}
</style>
