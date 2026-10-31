/**
 * 修复 Cesium 在高 DPI / 缩放下点击偏移的问题
 * （重写 ScreenSpaceEventHandler.prototype.getPosition）
 */
export function patchGetPosition(enable = true) {
  if (!enable) return;

  const proto = Cesium.ScreenSpaceEventHandler?.prototype;
  if (!proto) {
    console.warn('[CesiumFix] Cesium.ScreenSpaceEventHandler 未定义');
    return;
  }

  if (proto.__patched) {
    // 防止重复修复
    return;
  }

  const originalGetPosition = proto.getPosition;

  proto.getPosition = function (event: any, result: any) {
    const element = (this as any)._element;
    if (!element) return originalGetPosition.call(this, event, result);

    const scaleX = element.offsetWidth / element.getBoundingClientRect().width;
    const scaleY = element.offsetHeight / element.getBoundingClientRect().height;

    result = result || new Cesium.Cartesian2();

    if (element === document) {
      result.x = event.clientX * scaleX;
      result.y = event.clientY * scaleY;
      return result;
    }

    const rect = element.getBoundingClientRect();
    result.x = (event.clientX - rect.left) * scaleX;
    result.y = (event.clientY - rect.top) * scaleY;
    return result;
  };

  // 标记已修复
  (proto as any).__patched = true;

  console.info('[CesiumFix] ScreenSpaceEventHandler.getPosition 已修复');
}
