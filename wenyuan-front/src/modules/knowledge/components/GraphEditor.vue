<template>
  <div class="graph-editor" @click="onRootClick">
    <!-- Toolbar -->
    <div class="editor-toolbar">
      <n-space align="center" size="small">
        <n-button size="small" type="primary" @click.stop="openAddNodeModal">
          <template #icon><HIcon icon="mdi:plus" /></template>
          添加节点
        </n-button>
        <n-button size="small" :type="connectMode ? 'warning' : 'default'" @click.stop="toggleConnectMode">
          <template #icon><HIcon :icon="connectMode ? 'mdi:vector-line' : 'mdi:link-variant'" /></template>
          <template v-if="!connectMode">连接节点</template>
          <template v-else-if="!connectFrom">连接中：点击源节点</template>
          <template v-else>已选源「{{ connectFrom }}」→ 点击目标节点</template>
        </n-button>
        <n-button v-if="connectMode" size="small" @click.stop="cancelConnect">取消</n-button>
        <n-divider vertical />
        <n-button size="small" @click.stop="resetLayout">
          <template #icon><HIcon icon="mdi:refresh" /></template>
          重新布局
        </n-button>
        <n-divider vertical />
        <n-text depth="3" style="font-size: 12px">右键节点/关系可编辑或删除</n-text>
      </n-space>
    </div>

    <!-- Body -->
    <div class="editor-body">
      <!-- Graph canvas -->
      <div
        ref="canvasWrap"
        class="canvas-wrap"
        @contextmenu.prevent="handleContextMenu"
        @click="handleCanvasClick"
      >
        <v-chart
          ref="refChart"
          class="wh-full"
          :option="chartOptions"
          :autoresize="true"
          @click="handleChartClick"
        />

        <!-- Context menu -->
        <div
          v-if="ctxMenu.show"
          class="ctx-menu"
          :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
          @click.stop
        >
          <template v-if="ctxMenu.type === 'node'">
            <div class="ctx-item" @click="editNode(ctxMenu.name); closeCtxMenu()">编辑节点</div>
            <div class="ctx-item" @click="setConnectFrom(ctxMenu.name); closeCtxMenu()">从此连线</div>
            <n-divider style="margin: 2px 0" />
            <div class="ctx-item danger" @click="deleteNode(ctxMenu.name); closeCtxMenu()">删除节点</div>
          </template>
          <template v-else-if="ctxMenu.type === 'edge'">
            <div class="ctx-item" @click="editEdge(ctxMenu.idx); closeCtxMenu()">编辑关系</div>
            <n-divider style="margin: 2px 0" />
            <div class="ctx-item danger" @click="deleteEdge(ctxMenu.idx); closeCtxMenu()">删除关系</div>
          </template>
          <template v-else>
            <div class="ctx-item" @click="openAddNodeModal(); closeCtxMenu()">添加节点</div>
          </template>
        </div>
      </div>

      <!-- Edit sidebar（绝对定位浮层，不影响画布宽度）-->
      <transition name="slide-right">
        <div v-if="editForm" class="edit-sidebar" @click.stop>
          <template v-if="editForm.type === 'node'">
            <div class="sidebar-header">
              <span class="sidebar-title">编辑节点</span>
              <n-space size="small">
                <n-button size="tiny" type="error" @click="deleteNode(editForm.originalName)">删除</n-button>
                <n-button size="tiny" @click="editForm = null">×</n-button>
              </n-space>
            </div>
            <n-form :model="editForm" label-placement="top" size="small">
              <n-form-item label="名称">
                <n-input v-model:value="editForm.name" />
              </n-form-item>
              <n-form-item label="分类">
                <CategorySelect v-model:value="editForm.categoryId" clearable placeholder="选择分类" />
              </n-form-item>
              <n-form-item label="关键词">
                <n-input v-model:value="editForm.keywords" placeholder="逗号分隔" />
              </n-form-item>
              <n-form-item label="简介">
                <n-input v-model:value="editForm.content" type="textarea" :rows="4" />
              </n-form-item>
              <n-form-item label="权重">
                <n-slider v-model:value="editForm.weight" :min="40" :max="100" :step="1" />
              </n-form-item>
              <n-button type="primary" block @click="saveNode">保存节点</n-button>
            </n-form>
          </template>

          <template v-else-if="editForm.type === 'edge'">
            <div class="sidebar-header">
              <span class="sidebar-title">编辑关系</span>
              <n-space size="small">
                <n-button size="tiny" type="error" @click="deleteEdge(editForm._idx)">删除</n-button>
                <n-button size="tiny" @click="editForm = null">×</n-button>
              </n-space>
            </div>
            <n-form :model="editForm" label-placement="top" size="small">
              <n-form-item label="关系名称">
                <n-input v-model:value="editForm.name" />
              </n-form-item>
              <n-form-item label="来源节点">
                <n-input v-model:value="editForm.sourceName" />
              </n-form-item>
              <n-form-item label="目标节点">
                <n-input v-model:value="editForm.targetName" />
              </n-form-item>
              <n-form-item label="方向">
                <n-select v-model:value="editForm.direction" :options="DIRECTION_OPTIONS" />
              </n-form-item>
              <n-form-item label="说明">
                <n-input v-model:value="editForm.content" type="textarea" :rows="3" />
              </n-form-item>
              <n-button type="primary" block @click="saveEdge">保存关系</n-button>
            </n-form>
          </template>
        </div>
      </transition>
    </div>

    <!-- Add node modal -->
    <n-modal v-model:show="showAddNodeModal" title="添加节点" preset="card" style="width: 480px">
      <n-form :model="addForm" label-placement="top" size="small">
        <n-form-item label="名称（必填）">
          <n-input v-model:value="addForm.name" placeholder="节点名称" />
        </n-form-item>
        <n-form-item label="分类">
          <CategorySelect v-model:value="addForm.categoryId" clearable />
        </n-form-item>
        <n-form-item label="关键词">
          <n-input v-model:value="addForm.keywords" placeholder="逗号分隔" />
        </n-form-item>
        <n-form-item label="简介">
          <n-input v-model:value="addForm.content" type="textarea" :rows="2" />
        </n-form-item>
        <n-form-item label="权重">
          <n-slider v-model:value="addForm.weight" :min="40" :max="100" :step="1" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddNodeModal = false">取消</n-button>
          <n-button type="primary" :disabled="!addForm.name.trim()" @click="confirmAddNode">确认添加</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Connect relation modal -->
    <n-modal v-model:show="showConnectModal" title="创建关系" preset="card" style="width: 420px">
      <n-form :model="connectForm" label-placement="top" size="small">
        <n-form-item label="关系名称（必填）">
          <n-input v-model:value="connectForm.name" placeholder="例如：属于、包含、关联" />
        </n-form-item>
        <n-form-item label="来源节点">
          <n-input :value="connectForm.sourceName" disabled />
        </n-form-item>
        <n-form-item label="目标节点">
          <n-input :value="connectForm.targetName" disabled />
        </n-form-item>
        <n-form-item label="方向">
          <n-select v-model:value="connectForm.direction" :options="DIRECTION_OPTIONS" />
        </n-form-item>
        <n-form-item label="说明">
          <n-input v-model:value="connectForm.content" type="textarea" :rows="2" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="cancelConnect">取消</n-button>
          <n-button type="primary" :disabled="!connectForm.name.trim()" @click="confirmConnect">创建关系</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { GraphChart } from 'echarts/charts';
import { TooltipComponent, LegendComponent } from 'echarts/components';
import VChart from 'vue-echarts';
import { useMessage } from 'naive-ui';
import CategorySelect from './form/field/CategorySelect.vue';

use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent]);

const props = defineProps<{
  nodes: any[];
  relations: any[];
}>();

const emit = defineEmits<{
  'update:nodes': [nodes: any[]];
  'update:relations': [rels: any[]];
}>();

const message = useMessage();

const DIRECTION_OPTIONS = [
  { label: '→ 来源指向目标', value: 'LEFT2RIGHT' },
  { label: '← 目标指向来源', value: 'RIGHT2LEFT' },
  { label: '↔ 双向', value: 'TWOWAY' },
];

const localNodes = ref<any[]>(JSON.parse(JSON.stringify(props.nodes)));
const localRelations = ref<any[]>(JSON.parse(JSON.stringify(props.relations)));

watch(() => props.nodes, v => { localNodes.value = JSON.parse(JSON.stringify(v)); }, { deep: true });
watch(() => props.relations, v => { localRelations.value = JSON.parse(JSON.stringify(v)); }, { deep: true });

function emitChanges() {
  emit('update:nodes', localNodes.value);
  emit('update:relations', localRelations.value);
}

const refChart = ref<any>();
const canvasWrap = ref<HTMLElement>();

const editForm = ref<any>(null);
const connectMode = ref(false);
const connectFrom = ref<string | null>(null);
const showAddNodeModal = ref(false);
const showConnectModal = ref(false);
const addForm = ref({ name: '', categoryId: null as string | null, keywords: '', content: '', weight: 60 });
const connectForm = ref({ name: '', sourceName: '', targetName: '', direction: 'LEFT2RIGHT', content: '' });
const ctxMenu = ref({ show: false, x: 0, y: 0, type: '', name: '', idx: -1 });
const lastHovered = ref<{ type: string; name: string; idx: number } | null>(null);
let echartClickFired = false;

// ── 节点位置缓存 ──
const nodePositions = ref<Record<string, { x: number; y: number }>>({});

// ── 拖拽保护标志 ──
// ECharts 在所有节点都 fixed 时，force 仿真瞬间"收敛"，finished 会在拖拽中途触发。
// 若此时 capturePositions 更新 nodePositions，会触发 chartOptions recompute → VChart setOption，
// 把中途坐标写入 fixed:true，ECharts 锁住节点，导致拖拽被打断。
// isDraggingNode = true 期间跳过 capturePositions，mouseup 后再统一捕获。
let isDraggingNode = false;

function capturePositions() {
  const chart = refChart.value?.chart;
  if (!chart) return;
  try {
    const data = chart.getModel().getSeriesByIndex(0).getData();
    data.each(['x', 'y'], (x: number, y: number, idx: number) => {
      if (!isFinite(x) || !isFinite(y)) return;
      const name = data.getName(idx);
      const cur = nodePositions.value[name];
      if (!cur || Math.abs(cur.x - x) > 1 || Math.abs(cur.y - y) > 1) {
        nodePositions.value[name] = { x, y };
      }
    });
  } catch { /* ignore */ }
}

function resetLayout() {
  nodePositions.value = {};
}

// 拖拽结束后捕获坐标（由 mouseup 调用，不依赖 finished）
function onDragEnd() {
  if (!isDraggingNode) return;
  isDraggingNode = false;
  // 用 nextTick 等 ECharts 渲染完毕后再读坐标
  nextTick(() => capturePositions());
}
// 鼠标移出 canvas 也要解除拖拽锁，避免遗漏
const onDocMouseUp = () => onDragEnd();
onMounted(() => document.addEventListener('mouseup', onDocMouseUp));
onUnmounted(() => document.removeEventListener('mouseup', onDocMouseUp));

// ── chartOptions：只追踪结构数据，不追踪 editForm/connectFrom ──
// 高亮状态由 applyHighlights() 通过局部 setOption 更新，
// 局部更新不含 layout/force，不会重启 force 仿真，也不会中断拖拽
const chartOptions = computed(() => buildOption());

function buildOption() {
  const catSet = new Set<string>();
  localNodes.value.forEach(n => { if (n.categoryId) catSet.add(n.categoryId); });
  const categories = Array.from(catSet).map(id => ({ name: id }));
  const catIndex = (id?: string) => {
    if (!id) return 0;
    const i = categories.findIndex(c => c.name === id);
    return i >= 0 ? i : 0;
  };

  const echartNodes = localNodes.value.map(n => {
    const pos = nodePositions.value[n.name];
    return {
      name: n.name,
      content: n.content,
      category: catIndex(n.categoryId),
      symbolSize: Math.max(30, Math.min(70, (n.weight ?? 60) * 0.7)),
      ...(pos ? { x: pos.x, y: pos.y, fixed: true } : {}),
      itemStyle: { borderWidth: 4, shadowBlur: 12 },
    };
  });

  const echartLinks = localRelations.value.map(r => ({
    source: r.sourceName,
    target: r.targetName,
    name: r.name,
    lineStyle: r.direction === 'TWOWAY' ? { type: 'dashed' } : {},
    symbol: r.direction === 'RIGHT2LEFT' ? ['arrow', ''] : ['', 'arrow'],
  }));

  // 所有节点都有坐标（已完成初始布局）时关闭布局动画：
  //   - 后续 setOption（添加节点/连线/保存）触发的 force 仿真瞬间完成，节点不会抖动
  // 有新节点尚未定位时保持动画：让 force 动态定位新节点，用户可看到布局过程
  const allPositioned = localNodes.value.length > 0
    && localNodes.value.every(n => !!nodePositions.value[n.name]);

  return {
    tooltip: {
      trigger: 'item',
      formatter: (x: any) => x.dataType === 'edge' ? `关系：${x.data.name}` : undefined,
    },
    series: [{
      type: 'graph',
      layout: 'force',
      draggable: true,
      roam: true,
      // ★ 不写 zoom 字段 —— ECharts merge 模式保留用户当前缩放/平移，
      //   写了 zoom:1 会在每次 setOption 时把用户的缩放重置
      emphasis: { focus: 'adjacency' },
      categories,
      edgeLabel: {
        show: true,
        fontSize: 12,
        formatter: (x: any) => x.data.name,
        overflow: 'truncate',
        width: 80,
      },
      label: { show: true, fontSize: 12 },
      force: {
        repulsion: 2000,
        edgeLength: 130,
        // ★ 关键：所有节点已定位后禁用 layoutAnimation
        //   即使 force 仿真因 setOption 重启，也在同一帧内瞬间完成，不产生可见抖动
        layoutAnimation: !allPositioned,
      },
      data: echartNodes,
      links: echartLinks,
    }],
    ...(categories.length > 0 ? { legend: [{ data: categories.map(c => c.name) }] } : {}),
  };
}

// ── applyHighlights：局部 setOption，只更新 data/links 的视觉属性 ──
// ★ 不含 layout/force/roam/zoom —— ECharts merge 模式会：
//   1. 保留节点的 x/y/fixed（合并，不覆盖），拖拽后位置不丢失
//   2. 不重启 force 仿真，节点不抖动
//   3. 不重置用户缩放（无 zoom 字段）
function applyHighlights() {
  const chart = refChart.value?.chart;
  if (!chart) return;

  const selectedName = editForm.value?.type === 'node' ? editForm.value.originalName : null;
  const selectedEdgeIdx = editForm.value?.type === 'edge' ? editForm.value._idx : -1;
  const connectName = connectFrom.value;

  const nodeData = localNodes.value.map(n => ({
    name: n.name,
    symbolSize: Math.max(30, Math.min(70, (n.weight ?? 60) * 0.7)),
    itemStyle: {
      borderWidth: n.name === connectName ? 8 : n.name === selectedName ? 6 : 4,
      borderColor: n.name === connectName ? '#18a058' : n.name === selectedName ? '#f0a020' : undefined,
      shadowBlur: n.name === connectName || n.name === selectedName ? 24 : 12,
      shadowColor: n.name === connectName ? '#18a058' : n.name === selectedName ? '#f0a020' : undefined,
    },
  }));

  const linkData = localRelations.value.map((r, i) => ({
    source: r.sourceName,
    target: r.targetName,
    name: r.name,
    lineStyle: {
      ...(r.direction === 'TWOWAY' ? { type: 'dashed' } : {}),
      ...(i === selectedEdgeIdx ? { width: 4, color: '#f0a020' } : {}),
    },
    symbol: r.direction === 'RIGHT2LEFT' ? ['arrow', ''] : ['', 'arrow'],
  }));

  chart.setOption({ series: [{ data: nodeData, links: linkData }] });
}

// ── 高亮触发策略 ──
// ECharts 6 中 finished 事件在【任何 setOption 渲染完成】后都会触发（ECharts 5 只在 force 收敛时触发）
// 若在 finished 里调 applyHighlights，会形成：
//   finished → applyHighlights → setOption → 新渲染 → finished → applyHighlights → …（无限循环）
// 改为用 Vue watch 驱动，彻底规避此问题：
//   1. 选中/连线状态变化 → watch([editForm, connectFrom]) → nextTick(applyHighlights)
//   2. 数据结构变化（VChart 已调 setOption 重绘）→ watch(chartOptions) → nextTick(applyHighlights)
// nextTick 保证在 VChart 的 setOption 渲染完成后再补高亮，ECharts 主流程已结束，无限制。

// 选中/连线变化 → 重绘高亮
watch([editForm, connectFrom], () => { nextTick(applyHighlights); }, { deep: false });

// 数据变化（添加/删除/保存节点关系）→ VChart 已调 setOption → nextTick 补高亮
// 注意：chartOptions 每次重算都是新对象引用，watch 每次都会触发，
// applyHighlights 内部是幂等的局部 setOption，多调一次没有副作用
watch(chartOptions, () => { nextTick(applyHighlights); });

// ── ECharts 事件 ──
onMounted(() => {
  nextTick(() => {
    const chart = refChart.value?.chart;
    if (!chart) return;
    //
    // finished 只负责捕获坐标（非拖拽中），不调 applyHighlights（避免 ECharts 6 无限循环）
    chart.on('finished', () => {
      if (!isDraggingNode) capturePositions();
    });
    //
    // // 拖拽保护：mousedown 在节点上时设 flag，防止 finished 中途捕获破坏拖拽
    // chart.on('mousedown', (e: any) => {
    //   if (e.dataType === 'node') isDraggingNode = true;
    // });
    // ECharts 内部 mouseup（canvas 内释放）
    chart.on('mouseup', () => onDragEnd());
    //
    chart.on('mouseover', (event: any) => {
      if (event.dataType === 'node') {
        lastHovered.value = { type: 'node', name: event.name, idx: -1 };
      } else if (event.dataType === 'edge') {
        const idx = localRelations.value.findIndex(
          r => r.sourceName === event.data.source && r.targetName === event.data.target && r.name === event.data.name
        );
        lastHovered.value = { type: 'edge', name: event.data?.name ?? '', idx };
      }
    });
    chart.on('mouseout', () => { lastHovered.value = null; });
  });
});

// ── 点击事件 ──
function handleChartClick(event: any) {
  echartClickFired = true;
  closeCtxMenu();

  if (event.dataType === 'node') {
    if (connectMode.value) {
      if (!connectFrom.value) {
        connectFrom.value = event.name;
      } else if (connectFrom.value !== event.name) {
        connectForm.value = {
          name: '',
          sourceName: connectFrom.value,
          targetName: event.name,
          direction: 'LEFT2RIGHT',
          content: '',
        };
        showConnectModal.value = true;
      }
    } else {
      editNode(event.name);
    }
  } else if (event.dataType === 'edge') {
    if (!connectMode.value) {
      const idx = localRelations.value.findIndex(
        r => r.sourceName === event.data.source &&
             r.targetName === event.data.target &&
             r.name === event.data.name
      );
      editEdge(idx);
    }
  }
}

function handleCanvasClick() {
  if (echartClickFired) { echartClickFired = false; return; }
  editForm.value = null;
  if (connectMode.value) connectFrom.value = null;
  closeCtxMenu();
}

function onRootClick() { closeCtxMenu(); }

// ── 右键菜单 ──
function handleContextMenu(event: MouseEvent) {
  ctxMenu.value = {
    show: true,
    x: event.offsetX,
    y: event.offsetY,
    type: lastHovered.value?.type ?? 'empty',
    name: lastHovered.value?.name ?? '',
    idx: lastHovered.value?.idx ?? -1,
  };
}

function closeCtxMenu() { ctxMenu.value.show = false; }

// ── 节点操作 ──
function editNode(name: string) {
  const node = localNodes.value.find(n => n.name === name);
  if (!node) return;
  editForm.value = { type: 'node', ...JSON.parse(JSON.stringify(node)), originalName: name };
}

function saveNode() {
  const { originalName, type, ...fields } = editForm.value;
  const idx = localNodes.value.findIndex(n => n.name === originalName);
  if (idx < 0) return;
  if (fields.name !== originalName) {
    localRelations.value.forEach(r => {
      if (r.sourceName === originalName) r.sourceName = fields.name;
      if (r.targetName === originalName) r.targetName = fields.name;
    });
    if (connectFrom.value === originalName) connectFrom.value = fields.name;
    if (nodePositions.value[originalName]) {
      nodePositions.value[fields.name] = nodePositions.value[originalName];
      delete nodePositions.value[originalName];
    }
  }
  localNodes.value[idx] = { ...fields };
  editForm.value = { ...editForm.value, originalName: fields.name };
  emitChanges();
  message.success('节点已保存');
}

function deleteNode(name: string) {
  localNodes.value = localNodes.value.filter(n => n.name !== name);
  localRelations.value = localRelations.value.filter(r => r.sourceName !== name && r.targetName !== name);
  delete nodePositions.value[name];
  if (editForm.value?.originalName === name || editForm.value?.name === name) editForm.value = null;
  if (connectFrom.value === name) connectFrom.value = null;
  emitChanges();
  message.success('节点及其关系已删除');
}

function openAddNodeModal() {
  addForm.value = { name: '', categoryId: null, keywords: '', content: '', weight: 60 };
  showAddNodeModal.value = true;
}

function confirmAddNode() {
  const name = addForm.value.name.trim();
  if (!name) return;
  if (localNodes.value.some(n => n.name === name)) { message.warning('节点名称已存在'); return; }
  localNodes.value.push({ ...addForm.value, name });
  showAddNodeModal.value = false;
  emitChanges();
  message.success('节点已添加');
}

// ── 关系操作 ──
function editEdge(idx: number) {
  if (idx < 0 || idx >= localRelations.value.length) return;
  editForm.value = { type: 'edge', ...JSON.parse(JSON.stringify(localRelations.value[idx])), _idx: idx };
}

function saveEdge() {
  const { type, _idx, ...fields } = editForm.value;
  localRelations.value[_idx] = { ...fields };
  editForm.value = { ...editForm.value };
  emitChanges();
  message.success('关系已保存');
}

function deleteEdge(idx: number) {
  if (idx < 0 || idx >= localRelations.value.length) return;
  localRelations.value.splice(idx, 1);
  if (editForm.value?._idx === idx) editForm.value = null;
  emitChanges();
  message.success('关系已删除');
}

// ── 连接模式 ──
function toggleConnectMode() {
  connectMode.value = !connectMode.value;
  if (!connectMode.value) cancelConnect();
}

function setConnectFrom(name: string) {
  connectMode.value = true;
  connectFrom.value = name;
}

function cancelConnect() {
  connectMode.value = false;
  connectFrom.value = null;
  showConnectModal.value = false;
}

function confirmConnect() {
  if (!connectForm.value.name.trim()) return;
  localRelations.value.push({ ...connectForm.value, name: connectForm.value.name.trim() });
  cancelConnect();
  emitChanges();
  message.success('关系已创建');
}
</script>

<style scoped>
.graph-editor {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.editor-toolbar {
  padding: 8px 12px;
  border-bottom: 1px solid var(--n-border-color, #e0e0e6);
  flex-shrink: 0;
  background: var(--n-color, #fff);
}

.editor-body {
  position: absolute;
  top:47px;
  right:0;
  left:0;
  bottom: 0;
  overflow: hidden;
  display: flex;
}

.canvas-wrap {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.wh-full {
  width: 100%;
  height: 100%;
}

.edit-sidebar {
  position: absolute;
  top: 0;
  right: 0;
  width: 260px;
  height: 100%;
  border-left: 1px solid var(--n-border-color, #e0e0e6);
  padding: 12px;
  overflow-y: auto;
  background: var(--n-color, #fff);
  box-shadow: -4px 0 16px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--n-border-color, #e0e0e6);
}

.sidebar-title { font-weight: 600; font-size: 14px; }

.ctx-menu {
  position: absolute;
  background: var(--n-color, #fff);
  border: 1px solid var(--n-border-color, #e0e0e6);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  min-width: 130px;
  overflow: hidden;
  padding: 4px 0;
}

.ctx-item {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  transition: background 0.12s;
}

.ctx-item:hover { background: var(--n-item-color-hover, #f3f5f9); }
.ctx-item.danger { color: #d03050; }
.ctx-item.danger:hover { background: #fff0f2; }

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}

.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(260px);
  opacity: 0;
}
</style>
