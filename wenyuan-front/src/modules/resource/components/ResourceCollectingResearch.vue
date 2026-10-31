<template>
  <section class="collecting-research" aria-label="数字收藏研究图谱">
    <div class="research-toolbar">
      <n-select
        v-model:value="focusedId"
        class="research-search"
        :options="searchOptions"
        placeholder="搜索计划或资源"
        aria-label="搜索计划或资源"
        filterable
        clearable
      />
      <div class="research-actions">
        <n-button v-if="focusedId" size="small" @click="focusedId = null">全部关联</n-button>
        <n-button-group size="small">
          <n-button aria-label="缩小图谱" :disabled="zoom <= 0.6" @click="changeZoom(-0.1)">
            <template #icon
              ><n-icon><IconMdiMinus /></n-icon
            ></template>
          </n-button>
          <n-button aria-label="重置图谱比例" title="重置为 100%" @click="zoom = 1"
            >{{ Math.round(zoom * 100) }}%</n-button
          >
          <n-button aria-label="放大图谱" :disabled="zoom >= 1.4" @click="changeZoom(0.1)">
            <template #icon
              ><n-icon><IconMdiPlus /></n-icon
            ></template>
          </n-button>
        </n-button-group>
        <n-button size="small" :loading="loading" @click="load">
          <template #icon
            ><n-icon><IconMdiRefresh /></n-icon></template
          >刷新
        </n-button>
      </div>
    </div>

    <div class="research-summary">
      <dl class="research-metrics">
        <div>
          <dt>计划总数</dt>
          <dd>{{ plans.length }}</dd>
        </div>
        <div>
          <dt>资源总数</dt>
          <dd>{{ resources.length }}</dd>
        </div>
        <div>
          <dt>关联总数</dt>
          <dd>{{ graph.edges.length }}</dd>
        </div>
        <div>
          <dt>已发证</dt>
          <dd>{{ data?.issuedCount ?? 0 }}<span> 份</span></dd>
        </div>
      </dl>
      <n-popover trigger="click" placement="bottom-end">
        <template #trigger><n-button text size="small">分布统计</n-button></template>
        <div class="research-distribution">
          <h3>资源类型</h3>
          <dl>
            <div v-for="(count, type) in data?.byType" :key="type">
              <dt>{{ typeName(String(type)) }}</dt>
              <dd>{{ count }}</dd>
            </div>
          </dl>
          <n-text v-if="!Object.keys(data?.byType || {}).length" depth="3">暂无数据</n-text>
          <h3>计划来源</h3>
          <dl>
            <div v-for="(count, source) in data?.bySource" :key="source">
              <dt>{{ source || '未登记' }}</dt>
              <dd>{{ count }}</dd>
            </div>
          </dl>
          <n-text v-if="!Object.keys(data?.bySource || {}).length" depth="3">暂无数据</n-text>
        </div>
      </n-popover>
    </div>

    <n-alert v-if="error" type="error" :show-icon="false">{{ error }}</n-alert>

    <div v-if="selectedNode" class="research-selection" aria-live="polite">
      <div class="research-selection__content">
        <strong>{{ selectedNode.name }}</strong>
        <span v-if="selectedDescription">{{ selectedDescription }}</span>
      </div>
      <n-button v-if="selectedNode.kind !== 'PLAN' && canPreview" text type="primary" @click="previewResource">
        查看资源<template #icon
          ><n-icon><IconMdiOpenInNew /></n-icon
        ></template>
      </n-button>
    </div>

    <div class="research-surface">
      <div class="research-columns" aria-hidden="true">
        <span
          ><i class="research-plan-dot" />征集计划 <small>{{ layout.planCount }}</small></span
        >
        <span
          ><i class="research-resource-dot" />收藏资源 <small>{{ layout.resourceCount }}</small></span
        >
      </div>
      <n-spin :show="loading" class="research-spin" content-class="research-spin-content">
        <div ref="viewport" class="research-viewport" tabindex="0" aria-label="关联图谱，可滚动查看，点击节点聚焦关联">
          <div v-if="!loading && !graph.nodes.length" class="research-empty">
            <n-empty :description="error ? '图谱暂不可用' : '暂无研究图谱'">
              <template v-if="!error" #extra>征集计划关联资源后，关系将在这里展示。</template>
            </n-empty>
          </div>
          <div
            v-else
            class="research-canvas-space"
            :style="{ width: `${layout.width * zoom}px`, height: `${layout.height * zoom}px` }"
          >
            <div
              class="research-canvas"
              :style="{ width: `${layout.width}px`, height: `${layout.height}px`, transform: `scale(${zoom})` }"
            >
              <svg class="research-edges" :width="layout.width" :height="layout.height" aria-hidden="true">
                <path
                  v-for="edge in layout.edges"
                  :key="edge.key"
                  :d="edge.path"
                  :class="{
                    'is-active': focusedId || (hoveredId && (edge.source === hoveredId || edge.target === hoveredId)),
                    'is-muted': hoveredId && edge.source !== hoveredId && edge.target !== hoveredId,
                  }"
                />
              </svg>
              <button
                v-for="node in layout.nodes"
                :key="node.id"
                type="button"
                class="research-node"
                :class="{ 'research-node--plan': node.kind === 'PLAN', 'is-selected': node.id === focusedId }"
                :style="{
                  left: `${node.x}px`,
                  top: `${node.y}px`,
                  width: `${layout.nodeWidth}px`,
                  height: `${layout.nodeHeight}px`,
                }"
                :title="node.name"
                :aria-label="`${node.kind === 'PLAN' ? '征集计划' : typeName(node.kind)}：${node.name}，${nodeRelation(node)}`"
                :aria-pressed="node.id === focusedId"
                @click="focusedId = node.id"
                @mouseenter="hoveredId = node.id"
                @mouseleave="hoveredId = null"
                @focus="hoveredId = node.id"
                @blur="hoveredId = null"
              >
                <span class="research-node__icon" aria-hidden="true">
                  <n-icon size="20"
                    ><IconMdiFolderOutline v-if="node.kind === 'PLAN'" /><IconMdiFileDocumentOutline v-else
                  /></n-icon>
                </span>
                <span class="research-node__body">
                  <strong>{{ node.name }}</strong>
                  <span>{{ node.kind === 'PLAN' ? '' : `${typeName(node.kind)} · ` }}{{ nodeRelation(node) }}</span>
                </span>
              </button>
            </div>
          </div>
        </div>
      </n-spin>
    </div>
    <ResourcePreview v-if="previewShow" v-model:show="previewShow" :resource-id="previewId" />
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue';
import { useElementSize } from '@vueuse/core';
import { useAuthStore } from '@/store/modules/auth';
import { research } from '../api/business';
import { RESOURCE_TYPE_OPTIONS } from '../api/resource';
import {
  collectingDegrees,
  focusCollectingGraph,
  layoutCollectingGraph,
  normalizeCollectingGraph,
} from '../utils/collectingGraph';
import type { CollectingNode, CollectingResearch } from '../utils/collectingGraph';

const ResourcePreview = defineAsyncComponent(() => import('./ResourcePreview.vue'));
const auth = useAuthStore();
const data = ref<CollectingResearch | null>(null);
const loading = ref(false),
  error = ref('');
const focusedId = ref<string | null>(null),
  hoveredId = ref<string | null>(null);
const viewport = ref<HTMLElement>();
const { width, height } = useElementSize(viewport);
const zoom = ref(1);
const previewId = ref(''),
  previewShow = ref(false);
const canPreview = computed(() => auth.hasPermission('RESOURCE_ALL,RESOURCE_SELECT'));
const graph = computed(() => normalizeCollectingGraph(data.value));
const plans = computed(() => graph.value.nodes.filter((node) => node.kind === 'PLAN'));
const resources = computed(() => graph.value.nodes.filter((node) => node.kind !== 'PLAN'));
const degrees = computed(() => collectingDegrees(graph.value));
const focusedGraph = computed(() => focusCollectingGraph(graph.value, focusedId.value));
const layout = computed(() => layoutCollectingGraph(focusedGraph.value, width.value, height.value));
const selectedNode = computed(() => graph.value.nodes.find((node) => node.id === focusedId.value));
const selectedDescription = computed(() =>
  selectedNode.value?.kind === 'PLAN'
    ? selectedNode.value.background
    : selectedNode.value?.era
      ? `年代：${selectedNode.value.era}`
      : '',
);
const searchOptions = computed(() => [
  {
    type: 'group' as const,
    label: '征集计划',
    key: 'plans',
    children: plans.value.map((node) => ({ label: node.name, value: node.id })),
  },
  {
    type: 'group' as const,
    label: '收藏资源',
    key: 'resources',
    children: resources.value.map((node) => ({ label: node.name, value: node.id })),
  },
]);
function typeName(kind: string) {
  return RESOURCE_TYPE_OPTIONS.find((option) => option.value === kind)?.label || kind;
}
function nodeRelation(node: CollectingNode) {
  const count = degrees.value.get(node.id) || 0;
  return node.kind === 'PLAN' ? `${count} 个资源` : `${count} 个计划`;
}
function changeZoom(delta: number) {
  zoom.value = Math.min(1.4, Math.max(0.6, Math.round((zoom.value + delta) * 10) / 10));
}
function previewResource() {
  if (!selectedNode.value || selectedNode.value.kind === 'PLAN' || !canPreview.value) return;
  previewId.value = selectedNode.value.id;
  previewShow.value = true;
}
watch(focusedId, () => {
  hoveredId.value = null;
  viewport.value?.scrollTo({ top: 0, left: 0 });
});
let requestId = 0;
async function load() {
  const current = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const response: any = await research();
    if (current !== requestId) return;
    data.value = response.data;
    if (!graph.value.nodes.some((node) => node.id === focusedId.value)) focusedId.value = null;
  } catch {
    if (current === requestId) error.value = '研究图谱读取失败，请点击刷新重试。';
  } finally {
    if (current === requestId) loading.value = false;
  }
}
watch(
  () => auth.identityRevision,
  () => {
    data.value = null;
    focusedId.value = null;
    previewShow.value = false;
    load();
  },
);
onMounted(load);
</script>

<style scoped>
.collecting-research {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  min-height: 0;
}
.research-toolbar,
.research-summary,
.research-selection {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex: none;
}
.research-search {
  flex: 1;
  min-width: 180px;
  max-width: 360px;
}
.research-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}
.research-metrics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 24px;
  margin: 0;
}
.research-metrics > div {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.research-metrics dt {
  color: var(--resource-muted);
  font-size: 12px;
}
.research-metrics dd {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.research-metrics dd span {
  font-size: 12px;
  font-weight: 400;
  color: var(--resource-muted);
}
.research-selection {
  padding: 12px 16px;
  border-left: 3px solid var(--resource-accent);
  background: color-mix(in srgb, var(--resource-accent) 5%, var(--resource-surface));
  border-radius: 4px;
}
.research-selection__content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.research-selection__content strong {
  font-size: 14px;
  overflow-wrap: anywhere;
}
.research-selection__content > span {
  font-size: 12px;
  color: var(--resource-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.research-selection > .n-button {
  flex: none;
}
.research-surface {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--resource-border);
  border-radius: 10px;
  background: var(--resource-surface);
}
.research-columns {
  display: flex;
  justify-content: space-between;
  flex: none;
  padding: 16px 32px;
  border-bottom: 1px solid var(--resource-border);
  font-size: 13px;
  font-weight: 600;
}
.research-columns > span {
  display: flex;
  align-items: center;
  gap: 8px;
}
.research-columns i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.research-plan-dot {
  background: var(--resource-accent);
}
.research-resource-dot {
  background: #23887d;
}
.research-columns small {
  color: var(--resource-muted);
  font-size: 12px;
  font-weight: 400;
}
.research-spin,
.research-spin :deep(.research-spin-content) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.research-viewport {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  overscroll-behavior: contain;
  background-image: radial-gradient(
    color-mix(in srgb, var(--resource-border) 65%, transparent) 0.7px,
    transparent 0.7px
  );
  background-size: 20px 20px;
  scrollbar-width: thin;
}
.research-canvas-space {
  position: relative;
}
.research-canvas {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: top left;
}
.research-edges {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.research-edges path {
  fill: none;
  stroke: color-mix(in srgb, var(--resource-text) 25%, var(--resource-surface));
  stroke-width: 1.5;
  transition:
    stroke 150ms ease,
    opacity 150ms ease;
}
.research-edges path.is-active {
  stroke: var(--resource-accent);
  stroke-width: 2;
}
.research-edges path.is-muted {
  opacity: 0.2;
}
.research-node {
  position: absolute;
  display: flex;
  align-items: center;
  gap: 12px;
  box-sizing: border-box;
  padding: 10px 14px;
  border: 1px solid var(--resource-border);
  border-left: 3px solid #23887d;
  border-radius: 8px;
  color: var(--resource-text);
  background: var(--resource-surface);
  text-align: left;
  font: inherit;
  cursor: pointer;
  box-shadow: 0 2px 5px #00000005;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;
}
.research-node--plan {
  border-left-color: var(--resource-accent);
}
.research-node:hover,
.research-node.is-selected {
  border-color: var(--resource-accent);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--resource-accent) 12%, transparent);
}
.research-node.is-selected {
  background: color-mix(in srgb, var(--resource-accent) 5%, var(--resource-surface));
}
.research-node__icon {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #23887d;
  background: color-mix(in srgb, #23887d 8%, var(--resource-surface));
}
.research-node--plan .research-node__icon {
  color: var(--resource-accent);
  background: color-mix(in srgb, var(--resource-accent) 8%, var(--resource-surface));
}
.research-node__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.research-node__body strong {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  line-height: 20px;
  overflow-wrap: anywhere;
}
.research-node__body > span {
  color: var(--resource-muted);
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}
.research-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 24px;
  box-sizing: border-box;
}
.research-distribution {
  width: 240px;
  max-height: 400px;
  overflow: auto;
}
.research-distribution h3 {
  margin: 8px 0 12px;
  font-size: 13px;
  font-weight: 600;
}
.research-distribution dl {
  margin: 0 0 20px;
}
.research-distribution dl:last-child {
  margin-bottom: 0;
}
.research-distribution dl > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 4px 0;
  font-size: 13px;
}
.research-distribution dt {
  overflow-wrap: anywhere;
}
.research-distribution dd {
  margin: 0;
  flex: none;
  font-variant-numeric: tabular-nums;
}
@container (max-width: 760px) {
  .research-toolbar {
    flex-wrap: wrap;
    gap: 10px;
  }
  .research-search {
    flex-basis: 100%;
    max-width: none;
  }
  .research-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .research-metrics {
    gap: 8px 16px;
  }
  .research-summary {
    align-items: flex-start;
  }
  .research-columns {
    padding: 12px 20px;
  }
  .collecting-research {
    gap: 12px;
  }
}
@container (max-width: 600px) {
  .research-node {
    padding: 10px;
  }
  .research-node__icon {
    display: none;
  }
  .research-node__body strong {
    font-size: 13px;
  }
  .research-node__body > span {
    font-size: 11px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .research-node,
  .research-edges path {
    transition: none;
  }
}
</style>
