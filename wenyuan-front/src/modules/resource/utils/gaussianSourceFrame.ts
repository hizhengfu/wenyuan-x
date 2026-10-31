import { inferPlanarSceneUp } from './cameraOrientation';
import { validVector } from './previewConfig';

export type Vector3 = [number, number, number];

export type GaussianSourceFrame = {
  center: Vector3;
  up: Vector3;
  diagonal: number;
  boundsMin: Vector3;
  boundsMax: Vector3;
  inferredUp: boolean;
};

// ControlServer / SuperSplat 的 Gaussian 模型绕 Z 轴 180° 后以 +Y 为上；
// 此处直接渲染原始 PLY 坐标，等价的相机上方向是 -Y。
const DEFAULT_UP: Vector3 = [0, -1, 0];

function canonicalUp(vector: Vector3): Vector3 {
  const length = Math.hypot(...vector);
  if (length < 1e-9) return DEFAULT_UP;
  const normalized = vector.map((value) => value / length) as Vector3;
  const largest = normalized.reduce((best, value, index) =>
    Math.abs(value) > Math.abs(normalized[best]) ? index : best, 0);
  const preferredSign = largest === 1 ? -1 : 1;
  return normalized[largest] * preferredSign < 0
    ? normalized.map((value) => value === 0 ? 0 : -value) as Vector3 : normalized;
}

/** 从原始点坐标的协方差求明显平面场景的法线；不明确时不猜测语义朝向。 */
function planarNormal(points: Vector3[]): Vector3 | null {
  if (points.length < 16) return null;
  const mean = [0, 0, 0];
  points.forEach((point) => point.forEach((value, axis) => { mean[axis] += value; }));
  mean.forEach((_, axis) => { mean[axis] /= points.length; });
  const matrix = Array.from({ length: 3 }, () => [0, 0, 0]);
  for (const point of points) {
    const delta = point.map((value, axis) => value - mean[axis]);
    for (let row = 0; row < 3; row += 1) {
      for (let col = row; col < 3; col += 1) matrix[row][col] += delta[row] * delta[col];
    }
  }
  for (let row = 0; row < 3; row += 1) {
    for (let col = row; col < 3; col += 1) {
      matrix[row][col] /= points.length;
      matrix[col][row] = matrix[row][col];
    }
  }
  const vectors = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
  for (let iteration = 0; iteration < 24; iteration += 1) {
    const pairs: Array<[number, number]> = [[0, 1], [0, 2], [1, 2]];
    const [p, q] = pairs.reduce((best, pair) =>
      Math.abs(matrix[pair[0]][pair[1]]) > Math.abs(matrix[best[0]][best[1]]) ? pair : best);
    const offDiagonal = matrix[p][q];
    if (Math.abs(offDiagonal) < 1e-10 * Math.max(1, matrix[0][0] + matrix[1][1] + matrix[2][2])) break;
    const tau = (matrix[q][q] - matrix[p][p]) / (2 * offDiagonal);
    const t = (tau < 0 ? -1 : 1) / (Math.abs(tau) + Math.sqrt(1 + tau * tau));
    const cosine = 1 / Math.sqrt(1 + t * t);
    const sine = t * cosine;
    const app = matrix[p][p];
    const aqq = matrix[q][q];
    matrix[p][p] = app - t * offDiagonal;
    matrix[q][q] = aqq + t * offDiagonal;
    matrix[p][q] = matrix[q][p] = 0;
    for (let axis = 0; axis < 3; axis += 1) {
      if (axis !== p && axis !== q) {
        const ap = matrix[axis][p];
        const aq = matrix[axis][q];
        matrix[axis][p] = matrix[p][axis] = cosine * ap - sine * aq;
        matrix[axis][q] = matrix[q][axis] = sine * ap + cosine * aq;
      }
      const vp = vectors[axis][p];
      const vq = vectors[axis][q];
      vectors[axis][p] = cosine * vp - sine * vq;
      vectors[axis][q] = sine * vp + cosine * vq;
    }
  }
  const eigenvalues = [0, 1, 2].map((axis) => ({ value: Math.max(0, matrix[axis][axis]), axis }))
    .sort((a, b) => a.value - b.value);
  if (eigenvalues[1].value < 1e-9 || eigenvalues[0].value > eigenvalues[1].value * 0.2 ||
      eigenvalues[1].value < eigenvalues[2].value * 0.12) return null;
  return canonicalUp(vectors.map((row) => row[eigenvalues[0].axis]) as Vector3);
}

function frameFromBounds(min: Vector3, max: Vector3, up: Vector3, inferredUp: boolean): GaussianSourceFrame {
  return {
    center: min.map((value, axis) => (value + max[axis]) / 2) as Vector3,
    up,
    diagonal: Math.hypot(...min.map((value, axis) => max[axis] - value)),
    boundsMin: min,
    boundsMax: max,
    inferredUp,
  };
}

export function gaussianFrameFromBounds(min?: number[], max?: number[]): GaussianSourceFrame | null {
  if (!validVector(min) || !validVector(max) || min.some((value, axis) => value > max[axis])) return null;
  const candidate = inferPlanarSceneUp(min, max);
  return frameFromBounds([...min], [...max], canonicalUp(candidate || DEFAULT_UP), Boolean(candidate));
}

/** 均匀抽样后的已解码 PLY 坐标；截去两端离群点，避免少数飞点把镜头拉远。 */
export function gaussianFrameFromPoints(rawPoints: ReadonlyArray<ReadonlyArray<number>>): GaussianSourceFrame | null {
  const points = rawPoints.filter((point): point is Vector3 => validVector(point as number[]))
    .map((point) => [...point] as Vector3);
  if (points.length < 16) return null;
  const axes = [0, 1, 2].map((axis) => points.map((point) => point[axis]).sort((a, b) => a - b));
  const fullMin = axes.map((axis) => axis[0]) as Vector3;
  const fullMax = axes.map((axis) => axis[axis.length - 1]) as Vector3;
  const fullRadius = Math.hypot(...fullMin.map((value, axis) => fullMax[axis] - value)) / 2;
  let min = fullMin;
  let max = fullMax;
  // 与 ControlServer 一致：仅当裁去 2%／5% 离群点能明显收紧半径时才采用裁剪边界。
  if (points.length >= 200) {
    for (const divisor of [50, 20]) {
      const trim = Math.max(1, Math.floor(points.length / divisor));
      const candidateMin = axes.map((axis) => axis[trim]) as Vector3;
      const candidateMax = axes.map((axis) => axis[points.length - 1 - trim]) as Vector3;
      const candidateRadius = Math.hypot(...candidateMin.map((value, axis) => candidateMax[axis] - value)) / 2;
      if (fullRadius > candidateRadius * 1.15) {
        min = candidateMin;
        max = candidateMax;
        break;
      }
    }
  }
  const axisUp = inferPlanarSceneUp(min, max);
  const inliers = points.filter((point) => point.every((value, axis) => value >= min[axis] && value <= max[axis]));
  const normal = planarNormal(inliers);
  const up = canonicalUp(axisUp && normal && Math.abs(dot(axisUp, normal)) > 0.98
    ? axisUp : normal || axisUp || DEFAULT_UP);
  return frameFromBounds(min, max, up, Boolean(normal || axisUp));
}

export function gaussianCameraPosition(center: Vector3, distance: number, up: Vector3): Vector3 {
  // 保持旧预览熟悉的观察侧；只在该侧落到所选上方向的下方时翻到上方。
  // Gaussian Splat 往往只从采集相机所在的一侧重建得好，贸然换观察侧会出现巨大模糊斑块。
  const direction: Vector3 = [0.75, -1, 0.55];
  const vertical = newVector(up);
  const height = dot(direction, vertical);
  if (height < 0) {
    direction.forEach((value, axis) => { direction[axis] = value - 2 * height * vertical[axis]; });
  }
  return center.map((value, axis) => value + distance * direction[axis]) as Vector3;
}

function dot(a: Vector3, b: Vector3): number {
  return a.reduce((sum, value, axis) => sum + value * b[axis], 0);
}

function newVector(value: Vector3): Vector3 {
  const length = Math.hypot(...value);
  return length < 1e-9 ? DEFAULT_UP : value.map((part) => part / length) as Vector3;
}
