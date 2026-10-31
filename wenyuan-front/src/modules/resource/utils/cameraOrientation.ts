import { validVector } from './previewConfig';

type Vector3 = [number, number, number];

const AXES: Vector3[] = [[0, 1, 0], [0, 0, 1], [1, 0, 0]];

function alignment(a: Vector3, b: Vector3): number {
  const aLength = Math.hypot(...a);
  const bLength = Math.hypot(...b);
  if (aLength < 1e-9 || bLength < 1e-9) return 1;
  return Math.abs(a.reduce((sum, value, index) => sum + value * b[index], 0) / (aLength * bLength));
}

/** 仅在某一轴明显比另两轴薄时，才从包围盒推断平面场景的候选上方向。 */
export function inferPlanarSceneUp(min?: number[], max?: number[]): Vector3 | null {
  if (!validVector(min) || !validVector(max)) return null;
  const spans = min.map((value, index) => max[index] - value);
  if (spans.some((span) => !Number.isFinite(span) || span < 0)) return null;
  const sorted = spans.map((span, axis) => ({ span, axis })).sort((a, b) => a.span - b.span);
  if (sorted[1].span < 1e-9 || sorted[0].span > sorted[1].span * 0.4) return null;
  return [0, 1, 2].map((axis) => Number(axis === sorted[0].axis)) as Vector3;
}

/** SOG 没有全局 up 字段；只从文件几何推断明显的平面法线，否则使用格式坐标系的 Y 轴。 */
export function sourceSceneUp(min?: number[], max?: number[]): Vector3 {
  return inferPlanarSceneUp(min, max) || AXES[0];
}

/** 保存的 cameraUp 若几乎与观察方向平行，不能直接拿来当环绕轴。 */
export function resolveOrbitUp(
  position: Vector3,
  target: Vector3,
  savedUp?: number[] | null,
  boundsMin?: number[],
  boundsMax?: number[],
  fallbackUp: Vector3 = AXES[0],
): Vector3 {
  const view = position.map((value, index) => value - target[index]) as Vector3;
  const inferredUp = inferPlanarSceneUp(boundsMin, boundsMax);
  const preferredUp = validVector(savedUp) && Math.hypot(...savedUp) > 1e-9
    ? [...savedUp] as Vector3 : inferredUp || fallbackUp;
  if (alignment(preferredUp, view) < 0.95) {
    const length = Math.hypot(...preferredUp);
    return preferredUp.map((value) => value / length) as Vector3;
  }
  if (inferredUp && alignment(inferredUp, view) < 0.95) return inferredUp;
  return AXES.reduce((best, axis) => alignment(axis, view) < alignment(best, view) ? axis : best);
}
