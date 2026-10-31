<template>
  <div class="toolbar">
    <div class="toolbar-actions">
      <!-- ── 组1：历史 ── -->
      <div v-if="!readonly" class="tb-group">
        <n-button-group>
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button size="small" aria-label="撤销" @click="lf?.undo()">
                <template #icon><HIcon icon="m:flow-undo" class="tb-icon" /></template>
              </n-button>
            </template>
            撤销 (Ctrl+Z)
          </n-tooltip>
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button size="small" aria-label="重做" @click="lf?.redo()">
                <template #icon><HIcon icon="m:flow-redo" class="tb-icon" /></template>
              </n-button>
            </template>
            重做 (Ctrl+Y)
          </n-tooltip>
        </n-button-group>
      </div>

      <n-divider v-if="!readonly" vertical class="tb-divider" />

      <!-- ── 组2：视图 ── -->
      <div class="tb-group">
        <n-button-group>
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button size="small" aria-label="适应画布" @click="lf?.fitView()">
                <template #icon><HIcon icon="m:flow-fit" class="tb-icon" /></template>
              </n-button>
            </template>
            适应画布
          </n-tooltip>
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button size="small" aria-label="放大" @click="lf?.zoom(true)">
                <template #icon><HIcon icon="m:flow-zoom-in" class="tb-icon" /></template>
              </n-button>
            </template>
            放大
          </n-tooltip>
          <n-tooltip placement="bottom">
            <template #trigger>
              <n-button size="small" aria-label="缩小" @click="lf?.zoom(false)">
                <template #icon><HIcon icon="m:flow-zoom-out" class="tb-icon" /></template>
              </n-button>
            </template>
            缩小
          </n-tooltip>
        </n-button-group>
      </div>

      <!-- ── 组3：选择 & 多选操作（编辑模式） ── -->
      <template v-if="!readonly">
        <n-divider vertical class="tb-divider" />
        <div class="tb-group">
          <n-button-group>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button size="small" :type="selectionMode ? 'primary' : 'default'" @click="toggleSelectionMode">
                  <template #icon><HIcon icon="m:flow-select-box" class="tb-icon" /></template>
                </n-button>
              </template>
              {{ selectionMode ? '切换为平移模式' : '框选模式：拖拽选中多个节点' }}
            </n-tooltip>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button size="small" @click="selectAll">
                  <template #icon><HIcon icon="m:flow-select-all" class="tb-icon" /></template>
                </n-button>
              </template>
              全选所有节点 (Ctrl+A)
            </n-tooltip>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button size="small" :disabled="!hasSelection" @click="deleteSelected">
                  <template #icon><HIcon icon="m:flow-delete" class="tb-icon" /></template>
                </n-button>
              </template>
              删除选中节点/边
            </n-tooltip>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button
                  size="small"
                  :disabled="selectedNodeCount === 0"
                  :type="selectedNodesLocked ? 'warning' : 'default'"
                  @click="toggleLock"
                >
                  <template #icon>
                    <HIcon :icon="selectedNodesLocked ? 'm:flow-unlock' : 'm:flow-lock'" class="tb-icon" />
                  </template>
                </n-button>
              </template>
              {{ selectedNodesLocked ? '解锁：恢复节点可移动' : '锁定：禁止选中节点移动' }}
            </n-tooltip>
          </n-button-group>
        </div>
      </template>

      <n-divider v-if="!readonly" vertical class="tb-divider" />

      <!-- ── 组4：对齐 / 分布 ── -->
      <div v-if="!readonly" class="tb-group">
        <n-button-group>
          <n-tooltip v-for="btn in ALIGN_BTNS" :key="btn.key" placement="bottom">
            <template #trigger>
              <n-button size="small" :disabled="selectedNodeCount < btn.minCount" @click="btn.action()">
                <template #icon><HIcon :icon="btn.icon" class="tb-icon" /></template>
              </n-button>
            </template>
            {{ btn.tip }}
          </n-tooltip>
        </n-button-group>
      </div>

      <!-- ── 组5：自动排版 ── -->
      <template v-if="!readonly">
        <n-divider vertical class="tb-divider" />
        <div class="tb-group">
          <n-button-group>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button size="small" aria-label="自动排版（从左到右）" @click="autoLayout('LR')">
                  <template #icon><HIcon icon="m:flow-auto-layout" class="tb-icon" /></template>
                </n-button>
              </template>
              自动排版（从左到右）
            </n-tooltip>
            <n-tooltip placement="bottom">
              <template #trigger>
                <n-button size="small" aria-label="自动排版（从上到下）" @click="autoLayout('TB')">
                  <template #icon><HIcon icon="m:flow-auto-layout" class="tb-icon" style="transform: rotate(90deg)" /></template>
                </n-button>
              </template>
              自动排版（从上到下）
            </n-tooltip>
          </n-button-group>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue';
import HIcon from '@/components/basic/DynamicIcon.vue';

const props = defineProps<{
  lf: any;
  title?: string;
  readonly?: boolean;
}>();

// ─── 选中状态（订阅 lf 事件维护） ──────────────────────────────

const selectedNodeCount = ref(0);
const selectedNodesLocked = ref(false);
const selectionMode = ref(false);
const hasSelectedEdge = ref(false);
const hasSelection = computed(() => selectedNodeCount.value > 0 || hasSelectedEdge.value);

function getSelectedNodes(): any[] {
  if (!props.lf) return [];
  return (props.lf.getSelectElements(true) as any).nodes || [];
}

function refreshSelection() {
  const nodes = getSelectedNodes();
  selectedNodeCount.value = nodes.length;
  selectedNodesLocked.value = nodes.length > 0 && nodes.every((n: any) => n.properties?._locked === true);
}

// 当 lf 实例就绪时订阅事件
let unsubscribeFns: (() => void)[] = [];

watch(
  () => props.lf,
  (lf) => {
    unsubscribeFns.forEach((fn) => fn());
    unsubscribeFns = [];
    if (!lf) return;

    const onNodeClick = () => setTimeout(refreshSelection, 0);
    const onEdgeClick = () => {
      selectedNodeCount.value = 0;
      selectedNodesLocked.value = false;
      hasSelectedEdge.value = true;
    };
    const onBlankClick = () => {
      selectedNodeCount.value = 0;
      selectedNodesLocked.value = false;
      hasSelectedEdge.value = false;
    };

    lf.on('node:click', onNodeClick);
    lf.on('edge:click', onEdgeClick);
    lf.on('blank:click', onBlankClick);
    lf.on('selection:drop', refreshSelection);
    lf.on('selection:selected', refreshSelection);
    lf.on('node:select', refreshSelection);
    lf.on('node:unselect', refreshSelection);
    lf.on('node:delete', onBlankClick);
    lf.on('edge:delete', onBlankClick);

    unsubscribeFns = [
      () => lf.off('node:click', onNodeClick),
      () => lf.off('edge:click', onEdgeClick),
      () => lf.off('blank:click', onBlankClick),
      () => lf.off('selection:drop', refreshSelection),
      () => lf.off('selection:selected', refreshSelection),
      () => lf.off('node:select', refreshSelection),
      () => lf.off('node:unselect', refreshSelection),
      () => lf.off('node:delete', onBlankClick),
      () => lf.off('edge:delete', onBlankClick),
    ];
  },
  { immediate: true },
);

onBeforeUnmount(() => unsubscribeFns.forEach((fn) => fn()));

// ─── 工具栏操作 ───────────────────────────────────────────────

const PROTECTED_NODE_TYPES = ['START', 'END'];

function deleteSelected() {
  const lf = props.lf;
  if (!lf || props.readonly) return;
  const selected = lf.getSelectElements(true) as { nodes: any[]; edges: any[] };
  const nodes = (selected.nodes || []).filter((n: any) => !PROTECTED_NODE_TYPES.includes(n.properties?.nodeType));
  const edges = selected.edges || [];
  nodes.forEach((n: any) => lf.deleteNode(n.id));
  edges.forEach((e: any) => lf.deleteEdge(e.id));
  selectedNodeCount.value = 0;
  selectedNodesLocked.value = false;
  hasSelectedEdge.value = false;
}

function selectAll() {
  const lf = props.lf;
  if (!lf) return;
  const gm = (lf as any).graphModel;
  if (typeof gm?.selectAll === 'function') {
    gm.selectAll();
  } else {
    const graphData = lf.getGraphData() as any;
    (graphData.nodes || []).forEach((n: any) => {
      const model = lf.getNodeModelById(n.id) as any;
      if (model) model.isSelected = true;
    });
  }
  setTimeout(refreshSelection, 0);
}

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value;
  if (selectionMode.value) {
    (props.lf as any)?.openSelectionSelect?.();
  } else {
    (props.lf as any)?.closeSelectionSelect?.();
  }
}

function toggleLock() {
  const lf = props.lf;
  if (!lf) return;
  const nodes = getSelectedNodes();
  if (!nodes.length) return;
  const shouldLock = !selectedNodesLocked.value;
  nodes.forEach((n: any) => {
    const model = lf.getNodeModelById(n.id) as any;
    if (!model) return;
    model.draggable = !shouldLock;
    lf.setProperties(n.id, { ...n.properties, _locked: shouldLock });
  });
  selectedNodesLocked.value = shouldLock;
}

// ─── 对齐/分布 ───────────────────────────────────────────────

function moveNodeToPos(id: string, targetX: number, targetY: number) {
  const model = props.lf?.getNodeModelById(id) as any;
  if (!model) return;
  if (typeof model.moveTo === 'function') model.moveTo(targetX, targetY);
  else if (typeof model.move === 'function') model.move(targetX - model.x, targetY - model.y);
  else { model.x = targetX; model.y = targetY; }
}

const ALIGN_BTNS = [
  {
    key: 'equal-size',
    tip: '所有选中节点大小设为一致（以第一个为准）',
    icon: 'm:flow-equal-size',
    minCount: 2,
    action: () => {
      const nodes = getSelectedNodes();
      if (nodes.length < 2) return;
      const [first, ...rest] = nodes;
      rest.forEach((n: any) => {
        const m = props.lf?.getNodeModelById(n.id) as any;
        if (m) { m.width = first.width; m.height = first.height; }
      });
    },
  },
  {
    key: 'align-h',
    tip: '选中节点对齐到同一水平中心线',
    icon: 'm:flow-align-h',
    minCount: 2,
    action: () => {
      const nodes = getSelectedNodes();
      if (nodes.length < 2) return;
      const refY = nodes[0].y;
      nodes.forEach((n: any) => moveNodeToPos(n.id, n.x, refY));
    },
  },
  {
    key: 'align-v',
    tip: '选中节点对齐到同一垂直中心线',
    icon: 'm:flow-align-v',
    minCount: 2,
    action: () => {
      const nodes = getSelectedNodes();
      if (nodes.length < 2) return;
      const refX = nodes[0].x;
      nodes.forEach((n: any) => moveNodeToPos(n.id, refX, n.y));
    },
  },
  {
    key: 'distribute-h',
    tip: '选中节点水平方向均匀分布（需 ≥3 个）',
    icon: 'm:flow-distribute-h',
    minCount: 3,
    action: () => {
      const nodes = [...getSelectedNodes()].sort((a, b) => a.x - b.x);
      if (nodes.length < 3) return;
      const step = (nodes[nodes.length - 1].x - nodes[0].x) / (nodes.length - 1);
      nodes.forEach((n: any, i: number) => moveNodeToPos(n.id, nodes[0].x + step * i, n.y));
    },
  },
  {
    key: 'distribute-v',
    tip: '选中节点垂直方向均匀分布（需 ≥3 个）',
    icon: 'm:flow-distribute-v',
    minCount: 3,
    action: () => {
      const nodes = [...getSelectedNodes()].sort((a, b) => a.y - b.y);
      if (nodes.length < 3) return;
      const step = (nodes[nodes.length - 1].y - nodes[0].y) / (nodes.length - 1);
      nodes.forEach((n: any, i: number) => moveNodeToPos(n.id, n.x, nodes[0].y + step * i));
    },
  },
];

// ─── 自动排版（基于 Dagre 插件） ────────────────────────────

function autoLayout(rankdir: 'LR' | 'TB' = 'LR') {
  const lf = props.lf as any;
  if (!lf || props.readonly) return;
  const dagre = lf.extension?.dagre;
  if (!dagre || typeof dagre.layout !== 'function') {
    console.warn('Dagre 布局插件未启用');
    return;
  }
  dagre.layout({ rankdir, nodesep: 40, ranksep: 80 });
  // Dagre 只调整节点位置，边的路径/锚点需要基于新坐标重算
  refreshEdges(rankdir);
  setTimeout(() => lf.fitView?.(), 50);
}

function refreshEdges(rankdir: 'LR' | 'TB') {
  const lf = props.lf as any;
  if (!lf) return;
  const edges = (lf.getGraphData() as any).edges || [];
  edges.forEach((e: any) => {
    const model = lf.getEdgeModelById?.(e.id) as any;
    if (!model) return;

    // 1) 清空折线中间点，避免沿用旧路径
    if ('pointsList' in model) model.pointsList = [];
    model.points = '';

    // 2) 依据源/目标节点当前位置重算锚点
    const sourceModel = lf.getNodeModelById?.(e.sourceNodeId) as any;
    const targetModel = lf.getNodeModelById?.(e.targetNodeId) as any;
    if (sourceModel && targetModel) {
      const sAnchor = pickAnchor(sourceModel, targetModel, rankdir, 'source');
      const tAnchor = pickAnchor(targetModel, sourceModel, rankdir, 'target');
      if (sAnchor) model.updateStartPoint?.({ x: sAnchor.x, y: sAnchor.y });
      if (tAnchor) model.updateEndPoint?.({ x: tAnchor.x, y: tAnchor.y });
    }

    // 3) 触发路径重算
    if (typeof model.updatePoints === 'function') model.updatePoints();
    else if (typeof model.refresh === 'function') model.refresh();
  });
}

// 依据排版方向，为 source/target 挑选最合理的锚点（LR 取左右，TB 取上下）
function pickAnchor(self: any, other: any, rankdir: 'LR' | 'TB', role: 'source' | 'target') {
  const anchors: Array<{ x: number; y: number; id?: string }> = self.getDefaultAnchor?.() || self.anchors || [];
  if (!anchors.length) return null;
  if (rankdir === 'LR') {
    // source 偏右、target 偏左
    return anchors.reduce((best, cur) =>
      role === 'source'
        ? cur.x > best.x ? cur : best
        : cur.x < best.x ? cur : best,
    );
  }
  // TB：source 偏下、target 偏上
  return anchors.reduce((best, cur) =>
    role === 'source'
      ? cur.y > best.y ? cur : best
      : cur.y < best.y ? cur : best,
  );
}

// 暴露给 FlowDesigner 的键盘快捷键
defineExpose({ deleteSelected, selectAll });
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding: 6px 14px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
  gap: 8px;
}


.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-wrap: wrap;
}

.tb-group {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.tb-divider {
  height: 18px !important;
  margin: 0 4px !important;
}

/* HIcon 的根元素就是 SVG，直接限定尺寸，避免旋转后覆盖相邻按钮。 */
.tb-icon {
  width: 1em;
  height: 1em;
  font-size: 15px;
  display: block;
  line-height: 1;
  pointer-events: none;
}
</style>
