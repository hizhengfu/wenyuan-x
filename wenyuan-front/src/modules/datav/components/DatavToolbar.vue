<template>
  <div ref="toolbarRef" class="datav-toolbar" :class="{ 'is-dragging': dragging }" :style="toolbarStyle">
    <div class="toolbar-drag-handle" title="拖动工具栏" @pointerdown.stop.prevent="startDrag">
      <span>⠿</span><span>工具栏</span>
    </div>
    <slot name="start" :editing="editing" />
    <template v-if="editing">
      <div class="gutter-control">
        <span>组件间距</span>
        <n-input-number :value="gutter" :min="0" :max="24" :step="1" size="small" @update:value="emit('update:gutter', $event)" />
      </div>
      <n-button size="small" @click="emit('add')">添加组件</n-button>
      <n-button size="small" @click="emit('reset')">恢复默认</n-button>
      <n-button size="small" @click="emit('cancel')">取消</n-button>
      <n-button size="small" type="primary" :loading="saving" @click="emit('save')">保存草稿</n-button>
    </template>
    <template v-else>
      <n-tag type="warning" size="small">{{ projectId ? `草稿预览 v${version}` : '默认草稿 · 尚未保存' }}</n-tag>
      <n-button v-if="needsPublish && canPublish" size="small" type="success" :loading="publishing" @click="emit('publish')">
        发布到展示端
      </n-button>
      <n-tooltip>
        <template #trigger>
          <n-button size="small" type="primary" class="edit-layout-button" @click="emit('edit')">
            <template #icon><IconMdiViewDashboardEdit /></template>编辑布局
          </n-button>
        </template>
        配置当前业务大屏
      </n-tooltip>
    </template>
    <slot name="end" :editing="editing" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps<{
  container?: HTMLElement;
  editing: boolean;
  projectId: string;
  version: number;
  needsPublish: boolean;
  canPublish: boolean;
  gutter: number;
  saving: boolean;
  publishing: boolean;
}>();
const emit = defineEmits<{
  edit: []; add: []; reset: []; cancel: []; save: []; publish: [];
  'update:gutter': [value: number | null];
}>();
const toolbarRef = ref<HTMLElement>();
const position = ref<{ x: number; y: number }>();
const dragging = ref(false);
let stopTracking: (() => void) | undefined;
let animationFrame = 0;
let resizeObserver: ResizeObserver | undefined;
const toolbarStyle = computed(() => position.value ? {
  left: '0px',
  top: '0px',
  right: 'auto',
  transform: `translate3d(${position.value.x}px, ${position.value.y}px, 0)`,
} : undefined);

function startDrag(event: PointerEvent) {
  const container = props.container;
  const toolbar = toolbarRef.value;
  if (!container || !toolbar) return;
  stopTracking?.();
  const containerRect = container.getBoundingClientRect();
  // ScreenWrap 会缩放整个大屏。DOMRect 是缩放后的屏幕像素，而 absolute/transform 使用缩放前的逻辑像素，不能混算。
  const scaleX = container.offsetWidth ? containerRect.width / container.offsetWidth : 1;
  const scaleY = container.offsetHeight ? containerRect.height / container.offsetHeight : 1;
  const initialX = position.value?.x ?? toolbar.offsetLeft;
  const initialY = position.value?.y ?? toolbar.offsetTop;
  const pointerStartX = event.clientX;
  const pointerStartY = event.clientY;
  const maxX = Math.max(0, container.clientWidth - toolbar.offsetWidth);
  const maxY = Math.max(0, container.clientHeight - toolbar.offsetHeight);
  let nextPosition = { x: initialX, y: initialY };
  // offsetWidth 会丢失小数，临界宽度下少于 1px 也会触发 flex 换行；还原真实逻辑宽度并向上取整。
  const logicalWidth = toolbar.getBoundingClientRect().width / scaleX;
  toolbar.style.width = `${Math.ceil(logicalWidth)}px`;
  toolbar.style.left = '0px';
  toolbar.style.top = '0px';
  toolbar.style.right = 'auto';
  toolbar.style.transform = `translate3d(${initialX}px, ${initialY}px, 0)`;
  position.value = nextPosition;
  dragging.value = true;

  // 位移直接写入 transform，避免每个 Pointer 事件触发 Vue 渲染和 left/top 布局计算。
  const renderPosition = () => {
    animationFrame = 0;
    toolbar.style.transform = `translate3d(${nextPosition.x}px, ${nextPosition.y}px, 0)`;
  };
  const move = (moveEvent: PointerEvent) => {
    nextPosition = {
      x: Math.round(Math.min(maxX, Math.max(0, initialX + (moveEvent.clientX - pointerStartX) / scaleX))),
      y: Math.round(Math.min(maxY, Math.max(0, initialY + (moveEvent.clientY - pointerStartY) / scaleY))),
    };
    if (!animationFrame) animationFrame = requestAnimationFrame(renderPosition);
  };
  const end = () => {
    if (animationFrame) cancelAnimationFrame(animationFrame);
    animationFrame = 0;
    toolbar.style.transform = `translate3d(${nextPosition.x}px, ${nextPosition.y}px, 0)`;
    toolbar.style.width = '';
    position.value = nextPosition;
    dragging.value = false;
    window.removeEventListener('pointermove', move);
    window.removeEventListener('pointerup', end);
    window.removeEventListener('pointercancel', end);
    stopTracking = undefined;
  };
  stopTracking = end;
  window.addEventListener('pointermove', move);
  window.addEventListener('pointerup', end);
  window.addEventListener('pointercancel', end);
}

async function clampPosition() {
  if (!position.value || !props.container || !toolbarRef.value) return;
  await nextTick();
  // 边界同样使用逻辑尺寸，避免在缩放大屏中每次 ResizeObserver 回调都把位置向左上方压缩。
  const maxX = Math.max(0, props.container.clientWidth - toolbarRef.value.offsetWidth);
  const maxY = Math.max(0, props.container.clientHeight - toolbarRef.value.offsetHeight);
  position.value = {
    x: Math.min(maxX, Math.max(0, position.value.x)),
    y: Math.min(maxY, Math.max(0, position.value.y)),
  };
}

watch(() => props.editing, clampPosition);
onMounted(() => {
  if (typeof ResizeObserver === 'undefined') return;
  resizeObserver = new ResizeObserver(() => void clampPosition());
  if (toolbarRef.value) resizeObserver.observe(toolbarRef.value);
  if (props.container) resizeObserver.observe(props.container);
});
onBeforeUnmount(() => {
  if (animationFrame) cancelAnimationFrame(animationFrame);
  resizeObserver?.disconnect();
  stopTracking?.();
});
</script>

<style scoped lang="scss">
.datav-toolbar {
  position: absolute;
  box-sizing: border-box;
  top: 8px;
  right: 10px;
  /* 始终高于组件标题栏、缩放手柄、辅助线和右键菜单。 */
  z-index: 2147483647;
  display: flex;
  max-width: calc(100% - 20px);
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 10px;
  background: linear-gradient(135deg, rgb(3 20 34 / 96%), rgb(4 45 70 / 92%));
  border: 1px solid var(--datav-accent-soft, rgb(0 149 255 / 35%));
  border-radius: 7px;
  box-shadow: 0 8px 24px rgb(0 0 0 / 45%), inset 0 0 18px var(--datav-accent-soft, rgb(0 149 255 / 35%));
  backdrop-filter: blur(8px);
  pointer-events: auto;
  will-change: transform;

  &.is-dragging {
    box-shadow: 0 4px 12px rgb(0 0 0 / 32%);
    backdrop-filter: none;
  }
}
.edit-layout-button { box-shadow: 0 0 12px var(--datav-accent-soft, rgb(0 149 255 / 35%)); }
.toolbar-drag-handle {
  display: flex;
  height: 28px;
  align-items: center;
  gap: 5px;
  padding: 0 7px;
  color: var(--datav-accent, #0095ff);
  font-size: 12px;
  background: rgb(255 255 255 / 6%);
  border: 1px dashed var(--datav-accent-soft, rgb(0 149 255 / 35%));
  border-radius: 4px;
  cursor: grab;
  touch-action: none;
  user-select: none;
  &:active { cursor: grabbing; }
}
.gutter-control {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 7px;
  color: #dceeff;
  font-size: 12px;
  background: rgb(0 0 0 / 25%);
  border: 1px solid var(--datav-accent-soft, rgb(0 149 255 / 35%));
  border-radius: 5px;
  :deep(.n-input-number) { width: 76px; }
}
</style>
