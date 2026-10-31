import { onBeforeUnmount, ref, type Ref } from 'vue';
import { cloneDatavWidget } from '../core/document';
import type { DatavDocument, DatavWidgetInstance } from '../core/types';
import type { DatavWidgetRegistry } from '../registry/widgetRegistry';

type Options = {
  canvasRef: Ref<HTMLElement | undefined>;
  document: Ref<DatavDocument>;
  registry: DatavWidgetRegistry;
  bringToFront: (item: DatavWidgetInstance) => void;
};

export type DatavResizeDirection = 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w' | 'nw';

/** 把 Pointer 位移转换为网格坐标，统一处理组件拖动、缩放和窗口级事件清理。 */
export function useDatavWidgetTransform(options: Options) {
  const draggingId = ref('');
  const guides = ref<{ vertical: number[]; horizontal: number[] }>({ vertical: [], horizontal: [] });
  let stopTracking: (() => void) | undefined;

  function track(
    event: PointerEvent,
    item: DatavWidgetInstance,
    update: (deltaX: number, deltaY: number, initial: DatavWidgetInstance) => void,
  ) {
    const canvas = options.canvasRef.value;
    if (!canvas) return;
    options.bringToFront(item);
    const rect = canvas.getBoundingClientRect();
    const startX = event.clientX;
    const startY = event.clientY;
    const initial = cloneDatavWidget(item);
    stopTracking?.();

    const handleMove = (moveEvent: PointerEvent) => {
      const canvasConfig = options.document.value.canvas;
      update(
        ((moveEvent.clientX - startX) / rect.width) * canvasConfig.columns,
        ((moveEvent.clientY - startY) / rect.height) * canvasConfig.rows,
        initial,
      );
    };
    const handleEnd = () => {
      window.removeEventListener('pointermove', handleMove);
      window.removeEventListener('pointerup', handleEnd);
      window.removeEventListener('pointercancel', handleEnd);
      draggingId.value = '';
      guides.value = { vertical: [], horizontal: [] };
      stopTracking = undefined;
    };
    stopTracking = handleEnd;
    window.addEventListener('pointermove', handleMove);
    window.addEventListener('pointerup', handleEnd);
    window.addEventListener('pointercancel', handleEnd);
  }

  function startDrag(event: PointerEvent, item: DatavWidgetInstance) {
    draggingId.value = item.id;
    track(event, item, (deltaX, deltaY, initial) => {
      const canvas = options.document.value.canvas;
      const bounds = options.canvasRef.value?.getBoundingClientRect();
      let x = clamp(initial.rect.x + deltaX, 0, canvas.columns - item.rect.w);
      let y = clamp(initial.rect.y + deltaY, 0, canvas.rows - item.rect.h);
      const verticalSnap = findClosestSnap(
        [x, x + item.rect.w / 2, x + item.rect.w],
        collectAlignmentTargets(options.document.value, item, 'vertical'),
        pixelsToGrid(6, bounds?.width, canvas.columns),
      );
      const horizontalSnap = findClosestSnap(
        [y, y + item.rect.h / 2, y + item.rect.h],
        collectAlignmentTargets(options.document.value, item, 'horizontal'),
        pixelsToGrid(6, bounds?.height, canvas.rows),
      );
      if (verticalSnap) x = clamp(x + verticalSnap.offset, 0, canvas.columns - item.rect.w);
      if (horizontalSnap) y = clamp(y + horizontalSnap.offset, 0, canvas.rows - item.rect.h);
      item.rect.x = round(x);
      item.rect.y = round(y);
      guides.value = {
        vertical: verticalSnap ? [round(verticalSnap.guide)] : [],
        horizontal: horizontalSnap ? [round(horizontalSnap.guide)] : [],
      };
    });
  }

  function startResize(event: PointerEvent, item: DatavWidgetInstance, direction: DatavResizeDirection = 'se') {
    const manifest = options.registry.resolve(item.widgetKey, item.widgetVersion);
    if (manifest?.resizable === false) return;
    draggingId.value = item.id;
    const minSize = manifest?.minSize || { w: 2, h: 3 };
    track(event, item, (deltaX, deltaY, initial) => {
      const canvas = options.document.value.canvas;
      const bounds = options.canvasRef.value?.getBoundingClientRect();
      let left = initial.rect.x;
      let top = initial.rect.y;
      let right = initial.rect.x + initial.rect.w;
      let bottom = initial.rect.y + initial.rect.h;
      const resizeWest = direction.includes('w');
      const resizeEast = direction.includes('e');
      const resizeNorth = direction.includes('n');
      const resizeSouth = direction.includes('s');

      if (resizeWest) left = clamp(initial.rect.x + deltaX, 0, right - minSize.w);
      if (resizeEast) right = clamp(initial.rect.x + initial.rect.w + deltaX, left + minSize.w, canvas.columns);
      if (resizeNorth) top = clamp(initial.rect.y + deltaY, 0, bottom - minSize.h);
      if (resizeSouth) bottom = clamp(initial.rect.y + initial.rect.h + deltaY, top + minSize.h, canvas.rows);

      const verticalSnap = resizeWest || resizeEast
        ? findClosestSnap(
          [resizeWest ? left : right],
          collectAlignmentTargets(options.document.value, item, 'vertical'),
          pixelsToGrid(6, bounds?.width, canvas.columns),
        )
        : undefined;
      const horizontalSnap = resizeNorth || resizeSouth
        ? findClosestSnap(
          [resizeNorth ? top : bottom],
          collectAlignmentTargets(options.document.value, item, 'horizontal'),
          pixelsToGrid(6, bounds?.height, canvas.rows),
        )
        : undefined;

      if (verticalSnap && resizeWest) left = clamp(left + verticalSnap.offset, 0, right - minSize.w);
      if (verticalSnap && resizeEast) right = clamp(right + verticalSnap.offset, left + minSize.w, canvas.columns);
      if (horizontalSnap && resizeNorth) top = clamp(top + horizontalSnap.offset, 0, bottom - minSize.h);
      if (horizontalSnap && resizeSouth) bottom = clamp(bottom + horizontalSnap.offset, top + minSize.h, canvas.rows);

      item.rect.x = round(left);
      item.rect.y = round(top);
      item.rect.w = round(right - left);
      item.rect.h = round(bottom - top);
      guides.value = {
        vertical: verticalSnap ? [round(verticalSnap.guide)] : [],
        horizontal: horizontalSnap ? [round(horizontalSnap.guide)] : [],
      };
    });
  }

  onBeforeUnmount(() => stopTracking?.());
  return { draggingId, guides, startDrag, startResize };
}

type AlignmentAxis = 'vertical' | 'horizontal';

function collectAlignmentTargets(document: DatavDocument, current: DatavWidgetInstance, axis: AlignmentAxis) {
  const limit = axis === 'vertical' ? document.canvas.columns : document.canvas.rows;
  const targets = [0, limit / 2, limit];
  document.widgets.forEach((widget) => {
    if (widget.id === current.id) return;
    const start = axis === 'vertical' ? widget.rect.x : widget.rect.y;
    const size = axis === 'vertical' ? widget.rect.w : widget.rect.h;
    targets.push(start, start + size / 2, start + size);
  });
  return [...new Set(targets.map(round))];
}

function findClosestSnap(points: number[], targets: number[], threshold: number) {
  let closest: { guide: number; offset: number; distance: number } | undefined;
  points.forEach((point) => {
    targets.forEach((guide) => {
      const offset = guide - point;
      const distance = Math.abs(offset);
      if (distance <= threshold && (!closest || distance < closest.distance)) closest = { guide, offset, distance };
    });
  });
  return closest;
}

function pixelsToGrid(pixels: number, canvasPixels = 0, gridSize: number) {
  return canvasPixels > 0 ? (pixels / canvasPixels) * gridSize : 0.1;
}

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function round(value: number) {
  return Math.round(value * 1000) / 1000;
}
