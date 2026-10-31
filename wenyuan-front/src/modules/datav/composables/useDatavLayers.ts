import type { ComputedRef } from 'vue';
import type { DatavWidgetInstance } from '../core/types';

/** 管理自由层叠画布的 zIndex；不会改变组件坐标或执行碰撞避让。 */
export function useDatavLayers(widgets: ComputedRef<DatavWidgetInstance[]>) {
  function bringToFront(item: DatavWidgetInstance) {
    item.zIndex = Math.max(0, ...widgets.value.map((current) => current.zIndex)) + 1;
  }

  function sendToBack(item: DatavWidgetInstance) {
    item.zIndex = Math.min(1, ...widgets.value.map((current) => current.zIndex)) - 1;
  }

  function normalizeLayers() {
    widgets.value
      .map((item, index) => ({ item, index }))
      .sort((a, b) => a.item.zIndex - b.item.zIndex || a.index - b.index)
      .forEach(({ item }, index) => {
        item.zIndex = index + 1;
      });
  }

  function moveLayer(item: DatavWidgetInstance, offset: -1 | 1) {
    normalizeLayers();
    const ordered = [...widgets.value].sort((a, b) => a.zIndex - b.zIndex);
    const index = ordered.findIndex((current) => current.id === item.id);
    const target = ordered[index + offset];
    if (!target) return;
    [item.zIndex, target.zIndex] = [target.zIndex, item.zIndex];
  }

  return {
    bringToFront,
    bringForward: (item: DatavWidgetInstance) => moveLayer(item, 1),
    sendBackward: (item: DatavWidgetInstance) => moveLayer(item, -1),
    sendToBack,
  };
}
