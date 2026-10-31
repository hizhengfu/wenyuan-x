<template>
  <div ref="canvasRef" class="datav-canvas" @pointerdown="handleCanvasPointerDown">
    <div
      v-for="item in widgets"
      :key="item.id"
      class="datav-item"
      :class="{ 'is-item-selected': editing && selectedItemId === item.id }"
      :style="itemStyle(item)"
      @pointerdown="editing && $event.button === 0 && selectItem(item)"
      @contextmenu.prevent.stop="editing && openContextMenu($event, item)"
    >
      <div class="datav-widget">
        <slot name="widget" :widget="item" :manifest="resolveManifest(item)">
          <component :is="resolveManifest(item)?.component" v-if="resolveManifest(item)" />
          <div v-else class="missing-widget">组件 {{ item.widgetKey }}@{{ item.widgetVersion }} 已不存在</div>
        </slot>
      </div>
      <div
        v-if="editing"
        class="widget-editor-bar"
        :class="{ 'is-dragging': draggingId === item.id }"
        title="按住标题栏拖动组件"
        @pointerdown.stop.prevent="startItemDrag($event, item)"
      >
        <span class="widget-drag-grip">⠿</span>
        <span class="widget-drag-title">{{ widgetTitle(item) }}</span>
        <div class="widget-editor-actions" @pointerdown.stop>
          <n-button quaternary size="tiny" @click.stop="sendToBack(item)">置底</n-button>
          <n-button quaternary circle size="tiny" type="error" @click.stop="removeWidget(item.id)">
            <template #icon><IconMdiClose /></template>
          </n-button>
        </div>
      </div>
      <div
        v-for="direction in editing && selectedItemId === item.id && resolveManifest(item)?.resizable !== false ? resizeDirections : []"
        :key="direction"
        class="widget-resize-handle"
        :class="`is-${direction}`"
        :title="resizeTitle(direction)"
        @pointerdown.stop.prevent="startItemResize($event, item, direction)"
      ></div>
    </div>

    <template v-if="editing">
      <div
        v-for="position in guides.vertical"
        :key="`vertical-${position}`"
        class="alignment-guide is-vertical"
        :style="{ left: `${(position / document.canvas.columns) * 100}%` }"
      />
      <div
        v-for="position in guides.horizontal"
        :key="`horizontal-${position}`"
        class="alignment-guide is-horizontal"
        :style="{ top: `${(position / document.canvas.rows) * 100}%` }"
      />
    </template>

    <DatavContextMenu
      :show="contextMenu.show"
      :x="contextMenu.x"
      :y="contextMenu.y"
      @front="runContextAction(bringToFront)"
      @forward="runContextAction(bringForward)"
      @backward="runContextAction(sendBackward)"
      @back="runContextAction(sendToBack)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { DatavDocument, DatavWidgetInstance } from '../core/types';
import type { DatavWidgetRegistry } from '../registry/widgetRegistry';
import { useDatavLayers } from '../composables/useDatavLayers';
import { useDatavWidgetTransform } from '../composables/useDatavWidgetTransform';
import type { DatavResizeDirection } from '../composables/useDatavWidgetTransform';
import DatavContextMenu from './DatavContextMenu.vue';

const document = defineModel<DatavDocument>({ required: true });
const props = defineProps<{ widgetRegistry: DatavWidgetRegistry; editing: boolean }>();
const canvasRef = ref<HTMLElement>();
const widgets = computed(() => document.value.widgets);
const selectedItemId = ref('');
const contextMenu = ref<{ show: boolean; x: number; y: number; item?: DatavWidgetInstance }>({ show: false, x: 0, y: 0 });
const resizeDirections: DatavResizeDirection[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w'];
const { bringToFront, bringForward, sendBackward, sendToBack } = useDatavLayers(widgets);
const { draggingId, guides, startDrag, startResize } = useDatavWidgetTransform({
  canvasRef,
  document,
  registry: props.widgetRegistry,
  bringToFront,
});

function resolveManifest(item: DatavWidgetInstance) {
  return props.widgetRegistry.resolve(item.widgetKey, item.widgetVersion);
}

function widgetTitle(item: DatavWidgetInstance) {
  return resolveManifest(item)?.title || item.widgetKey;
}

function itemStyle(item: DatavWidgetInstance) {
  const canvas = document.value.canvas;
  const gutter = canvas.gutter || 0;
  const halfGutter = gutter / 2;
  return {
    left: `calc(${(item.rect.x / canvas.columns) * 100}% + ${halfGutter}px)`,
    top: `calc(${(item.rect.y / canvas.rows) * 100}% + ${halfGutter}px)`,
    width: `calc(${(item.rect.w / canvas.columns) * 100}% - ${gutter}px)`,
    height: `calc(${(item.rect.h / canvas.rows) * 100}% - ${gutter}px)`,
    zIndex: item.zIndex,
  };
}

function startItemDrag(event: PointerEvent, item: DatavWidgetInstance) {
  closeContextMenu();
  selectedItemId.value = item.id;
  startDrag(event, item);
}

function startItemResize(event: PointerEvent, item: DatavWidgetInstance, direction: DatavResizeDirection) {
  closeContextMenu();
  selectedItemId.value = item.id;
  startResize(event, item, direction);
}

function selectItem(item: DatavWidgetInstance) {
  selectedItemId.value = item.id;
  bringToFront(item);
}

function resizeTitle(direction: DatavResizeDirection) {
  const labels: Record<DatavResizeDirection, string> = {
    n: '向上调整大小',
    ne: '向右上调整大小',
    e: '向右调整大小',
    se: '向右下调整大小',
    s: '向下调整大小',
    sw: '向左下调整大小',
    w: '向左调整大小',
    nw: '向左上调整大小',
  };
  return labels[direction];
}

function openContextMenu(event: MouseEvent, item: DatavWidgetInstance) {
  const canvas = canvasRef.value;
  if (!canvas) return;
  selectedItemId.value = item.id;
  const rect = canvas.getBoundingClientRect();
  contextMenu.value = {
    show: true,
    x: Math.min(92, Math.max(0, ((event.clientX - rect.left) / rect.width) * 100)),
    y: Math.min(80, Math.max(0, ((event.clientY - rect.top) / rect.height) * 100)),
    item,
  };
}

function closeContextMenu() {
  contextMenu.value.show = false;
}

function handleCanvasPointerDown(event: PointerEvent) {
  closeContextMenu();
  if (props.editing && event.target === event.currentTarget) selectedItemId.value = '';
}

function runContextAction(action: (item: DatavWidgetInstance) => void) {
  if (contextMenu.value.item) action(contextMenu.value.item);
  closeContextMenu();
}

function removeWidget(id: string) {
  document.value = { ...document.value, widgets: widgets.value.filter((item) => item.id !== id) };
  if (selectedItemId.value === id) selectedItemId.value = '';
}

watch(() => props.editing, (editing) => {
  if (!editing) selectedItemId.value = '';
});
</script>

<style scoped lang="scss">
.datav-canvas { position: relative; width: 100%; height: 100%; overflow: hidden; }
.datav-item { position: absolute; min-width: 40px; min-height: 40px; }
.datav-widget { position: relative; width: 100%; height: 100%; overflow: hidden; }
.datav-widget :deep(> *) { width: 100% !important; height: 100% !important; margin: 0 !important; }
.widget-editor-bar {
  position: absolute;
  top: 6px;
  right: 6px;
  left: 6px;
  z-index: 2147483000;
  display: flex;
  height: 36px;
  align-items: center;
  justify-content: flex-end;
  padding: 0 5px 0 10px;
  color: #eaf7ff;
  font-size: 14px;
  background: linear-gradient(90deg, var(--datav-primary, #003867), var(--datav-panel, rgb(0 56 103 / 30%)));
  border: 1px solid var(--datav-accent, #0095ff);
  border-radius: 5px;
  box-shadow: 0 4px 14px rgb(0 0 0 / 42%);
  cursor: grab;
  pointer-events: auto;
  touch-action: none;
  user-select: none;
  &.is-dragging { background: var(--datav-primary, #003867); border-color: var(--datav-accent, #0095ff); cursor: grabbing; }
}
.widget-drag-title {
  position: absolute;
  left: 50%;
  max-width: calc(100% - 130px);
  overflow: hidden;
  font-weight: 600;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  transform: translateX(-50%);
  pointer-events: none;
}
.widget-drag-grip { position: absolute; left: 10px; color: var(--datav-accent, #0095ff); font-size: 20px; line-height: 1; }
.widget-editor-actions { position: relative; z-index: 1; display: flex; align-items: center; gap: 2px; }
.missing-widget { display: flex; width: 100%; height: 100%; align-items: center; justify-content: center; color: #ffb6b6; background: rgb(45 10 10 / 65%); border: 1px dashed #d75c5c; }
.is-item-selected { background: var(--datav-panel, rgb(0 56 103 / 30%)); outline: 1px dashed var(--datav-accent, #0095ff); }
.widget-resize-handle {
  position: absolute;
  z-index: 2147483001;
  touch-action: none;
  &::after {
    position: absolute;
    background: var(--datav-accent, #0095ff);
    border: 1px solid rgb(234 247 255 / 90%);
    border-radius: 2px;
    box-shadow: 0 0 6px var(--datav-accent, #0095ff);
    content: '';
  }
  &.is-n,
  &.is-s {
    left: 50%;
    width: 42px;
    height: 12px;
    cursor: ns-resize;
    transform: translateX(-50%);
    &::after { top: 4px; left: 8px; width: 24px; height: 2px; }
  }
  &.is-n { top: -6px; }
  &.is-s { bottom: -6px; }
  &.is-e,
  &.is-w {
    top: 50%;
    width: 12px;
    height: 42px;
    cursor: ew-resize;
    transform: translateY(-50%);
    &::after { top: 8px; left: 4px; width: 2px; height: 24px; }
  }
  &.is-e { right: -6px; }
  &.is-w { left: -6px; }
  &.is-ne,
  &.is-se,
  &.is-sw,
  &.is-nw {
    width: 16px;
    height: 16px;
    &::after { inset: 4px; }
  }
  &.is-ne { top: -6px; right: -6px; cursor: nesw-resize; }
  &.is-se { right: -6px; bottom: -6px; cursor: nwse-resize; }
  &.is-sw { bottom: -6px; left: -6px; cursor: nesw-resize; }
  &.is-nw { top: -6px; left: -6px; cursor: nwse-resize; }
}
.alignment-guide {
  position: absolute;
  z-index: 2147482990;
  background: var(--datav-accent, #0095ff);
  box-shadow: 0 0 7px var(--datav-accent, #0095ff);
  opacity: 0.9;
  pointer-events: none;
  &.is-vertical { top: 0; bottom: 0; width: 1px; transform: translateX(-0.5px); }
  &.is-horizontal { right: 0; left: 0; height: 1px; transform: translateY(-0.5px); }
}
</style>
