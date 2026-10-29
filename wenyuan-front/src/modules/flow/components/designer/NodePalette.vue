<template>
  <div class="node-sidebar">
    <div class="sidebar-section">
      <div
        v-for="item in NODE_ITEMS"
        :key="item.type"
        class="palette-item"
        :title="`${item.tip}，拖入画布添加`"
        @mousedown.prevent="onMouseDown(item, $event)"
      >
        <span class="palette-icon" :style="{ background: item.color }">
          <HIcon :icon="item.iconName" class="palette-node-icon" />
        </span>
        {{ item.label }}
      </div>
    </div>

    <div class="sidebar-section">
      <div
        v-for="item in SUBFLOW_ITEMS"
        :key="item.type"
        class="palette-item"
        :title="`${item.tip}，拖入画布添加`"
        @mousedown.prevent="onMouseDown(item, $event)"
      >
        <span class="palette-icon" :style="{ background: item.color }">
          <HIcon :icon="item.iconName" class="palette-node-icon" />
        </span>
        {{ item.label }}
      </div>
    </div>

  </div>

  <!-- 拖拽幽灵节点 -->
  <Teleport to="body">
    <div
      v-if="dragging.active"
      class="drag-ghost"
      :style="{ left: dragging.x + 'px', top: dragging.y + 'px', background: dragging.color }"
    >
      <NIcon size="32" class="drag-ghost-icon">
        <HIcon :icon="dragging.iconName" />
      </NIcon>
      {{ dragging.label }}
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { reactive, onBeforeUnmount } from 'vue';
import HIcon from '@/components/basic/DynamicIcon.vue';
import { NODE_COLORS } from './nodes';

const props = defineProps<{
  lf: any;
  containerRef: HTMLElement | undefined;
}>();

// ─── 面板配置 ─────────────────────────────────────────────────

interface PaletteItem {
  type: string;
  nodeType: string;
  label: string;
  color: string;
  iconName: string;
  tip: string;
}

const NODE_ITEMS: PaletteItem[] = [
  { type: 'flow-handle', nodeType: 'HANDLE', label: '经办', color: NODE_COLORS.HANDLE.fill, iconName: 'm:flow-node-handle', tip: '经办节点：由经办人处理并提交' },
  { type: 'flow-approve', nodeType: 'APPROVE', label: '审批', color: NODE_COLORS.APPROVE.fill, iconName: 'm:flow-node-approve', tip: '审批节点：需要审批人同意或驳回' },
  { type: 'flow-cc', nodeType: 'CC', label: '抄送', color: NODE_COLORS.CC.fill, iconName: 'm:flow-node-cc', tip: '抄送节点：自动通知相关人员' },
  { type: 'flow-exclusive', nodeType: 'EXCLUSIVE', label: '条件', color: NODE_COLORS.EXCLUSIVE.fill, iconName: 'm:flow-node-exclusive', tip: '条件网关：根据条件选择分支' },
];

const SUBFLOW_ITEMS: PaletteItem[] = [
  { type: 'flow-subprocess', nodeType: 'SUBPROCESS', label: '子流程', color: NODE_COLORS.SUBPROCESS.fill, iconName: 'm:flow-node-subprocess', tip: '子流程节点：嵌套调用另一个流程' },
];

// ─── 拖拽状态 ─────────────────────────────────────────────────

const dragging = reactive({
  active: false,
  item: null as PaletteItem | null,
  x: 0,
  y: 0,
  color: '',
  label: '',
  iconName: '',
});

function onMouseDown(item: PaletteItem, event: MouseEvent) {
  dragging.active = true;
  dragging.item = item;
  dragging.x = event.clientX;
  dragging.y = event.clientY;
  dragging.color = item.color;
  dragging.label = item.label;
  dragging.iconName = item.iconName;

  document.addEventListener('mousemove', onDragMove, { capture: true });
  document.addEventListener('mouseup', onDragEnd, { capture: true });
}

function onDragMove(event: MouseEvent) {
  if (!dragging.active) return;
  dragging.x = event.clientX;
  dragging.y = event.clientY;
}

function onDragEnd(event: MouseEvent) {
  if (!dragging.active) return;

  const item = dragging.item;
  dragging.active = false;
  dragging.item = null;
  document.removeEventListener('mousemove', onDragMove, { capture: true });
  document.removeEventListener('mouseup', onDragEnd, { capture: true });

  if (!item || !props.lf || !props.containerRef) return;

  const rect = props.containerRef.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) return;

  const pos = props.lf.getPointByClient({ x: event.clientX, y: event.clientY });
  const { x, y } = pos.canvasOverlayPosition;

  props.lf.addNode({
    type: item.type,
    x,
    y,
    properties: { nodeType: item.nodeType, name: item.label, nodeKey: `node_${Date.now()}` },
  });
}

onBeforeUnmount(() => {
  document.removeEventListener('mousemove', onDragMove, { capture: true });
  document.removeEventListener('mouseup', onDragEnd, { capture: true });
});
</script>

<style scoped>
.node-sidebar {
  width: 100px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  user-select: none;
}

.sidebar-section {
  padding: 12px 8px;
}

.sidebar-section + .sidebar-section {
  border-top: 1px solid #f0f2f5;
}


.palette-item {
  padding: 7px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #303133;
  cursor: grab;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f5f7fa;
  border: 1px solid transparent;
  margin-bottom: 4px;
  transition: background 0.15s, border-color 0.15s;
}

.palette-item:last-child {
  margin-bottom: 0;
}

.palette-item:hover {
  background: #ecf5ff;
  border-color: #a0cfff;
}

.palette-item:active {
  cursor: grabbing;
  opacity: 0.75;
}

.palette-icon {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.palette-node-icon {
  width: 1em;
  height: 1em;
  flex-shrink: 0;
  font-size: 12px;
  color: #fff;
  display: block;
}

</style>

<style>
.drag-ghost {
  position: fixed;
  pointer-events: none;
  z-index: 9999;
  color: #fff;
  padding: 5px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  opacity: 0.88;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 5px;
  transform: translate(12px, -50%);
}

.drag-ghost-icon {
  font-size: 13px;
  color: #fff;
  display: block;
  flex-shrink: 0;
}

.drag-ghost-icon svg {
  width: 1em;
  height: 1em;
  display: block;
}
</style>
