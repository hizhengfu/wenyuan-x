<template>
  <section class="org-overview">
    <div class="org-toolbar"><strong>总分馆概况</strong></div>
    <p v-if="!busy && !error && cards.length" class="scope-explanation" role="status">{{ scopeExplanation }}</p>
    <NAlert v-if="error" type="warning">{{ error }}<NButton text @click="load">重试</NButton></NAlert>
    <NSpin :show="busy"
      ><div class="org-card-grid">
        <article v-for="card in cards" :key="card.node.id" class="org-card">
          <div class="org-card-heading">
            <div class="org-card-title">
              <strong :style="{ paddingLeft: `${card.depth * 12}px` }">{{ card.node.name }}</strong
              ><NTag size="small" :bordered="false">{{ card.scopeLabel }}</NTag>
            </div>
            <div class="org-stat-links">
              <NButton v-if="card.mode === 'aggregate'" text type="primary" @click="enter(card.node, 'direct')"
                >直属统计</NButton
              ><NButton text type="primary" @click="enter(card.node, card.mode)">{{
                card.mode === 'aggregate' ? '汇总统计' : '查看统计'
              }}</NButton>
            </div>
          </div>
          <div class="org-figures">
            <div v-for="metric in card.metrics" :key="metric.label">
              <span>{{ metric.label }}</span
              ><strong class="metric-primary-value">{{ metric.value }}</strong>
              <small class="metric-primary-count"
                >{{ metric.count }} 条登记记录<span v-if="metric.unknown"
                  >；{{ metric.unknown }} 条数量待核实</span
                ></small
              >
              <div v-if="metric.direct" class="direct-figure">
                <span>{{ card.directScopeLabel }}</span
                ><strong class="metric-direct-value">{{ metric.direct.value }}</strong
                ><small class="metric-direct-count"
                  >{{ metric.direct.count }} 条登记记录<span v-if="metric.direct.unknown"
                    >；{{ metric.direct.unknown }} 条数量待核实</span
                  ></small
                >
              </div>
            </div>
          </div>
        </article>
      </div>
      <NEmpty v-if="!busy && !error && !cards.length" description="暂无可见收藏单位"
    /></NSpin>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { orgApi } from '../api/org';
import { collectionApi } from '../api/collection';
import { quantityText } from '../utils/collectionAnalysis';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import type { CollectionOrgOverviewNode, CollectionUsageSummary, CollectionUsageItem } from '../typings/collection';
defineProps<{ embedded?: boolean }>();
const auth = useAuthStore(),
  router = useRouter(),
  nodes = ref<CollectionOrgOverviewNode[]>([]),
  usage = ref<CollectionUsageSummary | null>(null),
  error = ref('');
const session = useCatalogOperation(() => {
    nodes.value = [];
    usage.value = null;
    error.value = '';
  }),
  busy = session.busy;
const descendantIds = (node: CollectionOrgOverviewNode): string[] => [node.id, ...node.children.flatMap(descendantIds)];
const branchOrgIds = computed(() => new Set(nodes.value.flatMap((node) => node.children.flatMap(descendantIds))));
const scopeExplanation = computed(() => {
  if (!branchOrgIds.value.size) return '当前可见馆没有下级分馆，按各馆直属数据展示。';
  const scope = '汇总统计包含本馆及当前可见分馆；本馆直属与各分馆直属分别列示，不应将汇总数字再与分馆数字相加。';
  return usage.value?.items.some((item) => branchOrgIds.value.has(item.orgId))
    ? scope
    : `${scope} 当前范围内暂无分馆在册数据，汇总与直属数字相同。`;
});
type Figure = { value: string; count: number; unknown: number };
type OrgCard = {
  node: CollectionOrgOverviewNode;
  depth: number;
  mode: 'direct' | 'aggregate';
  scopeLabel: string;
  directScopeLabel: string;
  metrics: (Figure & { label: string; direct?: Figure })[];
};
function figure(rows: CollectionUsageItem[]): Figure {
  const quantities: Record<string, number> = {};
  let unknown = 0;
  for (const row of rows) {
    const unit = row.unit?.trim();
    if (!unit || !Number.isSafeInteger(row.quantity) || Number(row.quantity) <= 0) {
      unknown++;
      continue;
    }
    quantities[unit] = (quantities[unit] || 0) + Number(row.quantity);
  }
  return { value: quantityText(quantities, unknown ? '待核实' : '0'), count: rows.length, unknown };
}
const selectors = [
  { label: '藏品总量', match: (_: CollectionUsageItem) => true },
  { label: '当前外借', match: (item: CollectionUsageItem) => item.onLoan },
  { label: '当前展出', match: (item: CollectionUsageItem) => item.onExhibition },
];
const cards = computed(() => {
  const result: OrgCard[] = [];
  const walk = (node: CollectionOrgOverviewNode, depth: number) => {
    // 常见总馆 + 分馆：总馆同时列汇总和直属，分馆只列自身，深层数据仍纳入总馆汇总。
    const aggregate = depth === 0 && node.children.length > 0,
      ids = new Set(aggregate ? descendantIds(node) : [node.id]);
    const rows = usage.value?.items.filter((item) => ids.has(item.orgId)) || [],
      direct = rows.filter((item) => item.orgId === node.id);
    const actualRoot = !node.parentId || node.parentId === '0';
    result.push({
      node,
      depth,
      mode: aggregate ? 'aggregate' : 'direct',
      scopeLabel: aggregate
        ? actualRoot
          ? '全馆汇总（可见范围）'
          : '本馆及可见分馆汇总'
        : depth > 0
          ? '分馆直属'
          : '本馆直属',
      directScopeLabel: actualRoot ? '总馆直属' : '本馆直属',
      metrics: selectors.map(({ label, match }) => ({
        label,
        ...figure(rows.filter(match)),
        ...(aggregate ? { direct: figure(direct.filter(match)) } : {}),
      })),
    });
    node.children.forEach((child) => walk(child, depth + 1));
  };
  nodes.value.forEach((node) => walk(node, 0));
  return result;
});
function enter(node: CollectionOrgOverviewNode, mode: 'direct' | 'aggregate') {
  void router.push({ path: '/collection/stat', query: { orgId: node.id, scopeMode: mode } });
}
async function load() {
  if (!session.active.value) return;
  session.reset();
  const ticket = session.begin('overview');
  if (ticket === null) return;
  try {
    const [orgs, response]: any[] = await Promise.all([orgApi.overview(), collectionApi.usageSummary()]);
    if (!session.current(ticket)) return;
    if (
      !Array.isArray(orgs?.data) ||
      !Array.isArray(response?.data?.items) ||
      response.data.total !== response.data.items.length
    )
      throw new Error('馆别统计资料不完整');
    nodes.value = orgs.data;
    usage.value = response.data;
  } catch (e: any) {
    if (session.current(ticket)) error.value = e?.message || '馆别统计读取失败';
  } finally {
    session.finish(ticket);
  }
}
onMounted(load);
watch(session.active, (active) => {
  if (active) void load();
});
watch(
  () => [auth.token, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, auth.roles, auth.permissions],
  () => {
    session.reset();
    if (auth.token && !auth.identitySyncing) void load();
  },
  { deep: true, flush: 'sync' },
);
</script>
<style scoped>
.org-toolbar,
.org-card-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.org-toolbar {
  margin-bottom: 12px;
}
.scope-explanation {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.6;
  opacity: 0.75;
}
.org-stat-links {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  flex-shrink: 0;
  gap: 12px;
}
.org-card-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}
.org-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.org-card {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.org-figures {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}
.org-figures > div,
.direct-figure {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.org-figures span,
.org-figures small {
  font-size: 12px;
}
.org-figures strong {
  font-size: 20px;
  overflow-wrap: anywhere;
}
.direct-figure {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid rgba(127, 127, 127, 0.18);
}
.direct-figure strong {
  font-size: 16px;
}
@media (max-width: 800px) {
  .org-card-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 520px) {
  .org-card-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
