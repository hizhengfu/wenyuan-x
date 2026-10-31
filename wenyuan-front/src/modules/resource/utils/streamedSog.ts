import { normalizePackagePath, type SogPackageFile } from './sogBundle';

type LodReference = { file: number; offset: number; count: number };
type LodNode = { children?: LodNode[]; lods?: Record<string, LodReference>; bound?: { min: number[]; max: number[] } };

export type StreamedSogManifest = {
  version?: 1;
  count: number;
  counts: number[];
  lodLevels: number;
  filenames: string[];
  tree: LodNode;
  environment?: string;
  bundledChunks: boolean;
  packageRoot?: string;
};

export function chunkPackagePath(manifest: StreamedSogManifest, path: string): string {
  return manifest.packageRoot ? `${manifest.packageRoot}/${path}` : path;
}

/** 首屏先加载点数最少的一层，避免以资源包总点数决定初始显存开销。 */
export function initialLevelForStreamedSog(manifest: StreamedSogManifest): number {
  return manifest.counts.indexOf(Math.min(...manifest.counts));
}

function safePath(value: string): boolean {
  return Boolean(value) && !value.startsWith('/') && !value.includes('\\') && !value.includes(':') &&
    !value.includes('?') && !value.includes('#') &&
    value.split('/').every((part) => Boolean(part) && part !== '.' && part !== '..');
}

function deriveCounts(tree: LodNode, levels: number, fileCount: number): number[] {
  const counts = Array.from({ length: levels }, () => 0);
  let visited = 0;
  const visit = (node: LodNode, depth: number) => {
    if (!node || depth > 64 || ++visited > 100_000) throw new Error('Streamed SOG 层级树无效');
    const min = node.bound?.min;
    const max = node.bound?.max;
    if (!Array.isArray(min) || !Array.isArray(max) || min.length !== 3 || max.length !== 3 ||
        min.some((value, index) => !Number.isFinite(value) || !Number.isFinite(max[index]) || value > max[index])) {
      throw new Error('Streamed SOG 层级树边界无效');
    }
    if (node.lods && !node.children) {
      const references = Object.entries(node.lods);
      if (!references.length) throw new Error('Streamed SOG 叶节点缺少 LOD 引用');
      for (const [key, reference] of references) {
        const level = Number(key);
        if (!Number.isInteger(level) || level < 0 || level >= levels ||
            !Number.isInteger(reference.file) || reference.file < 0 || reference.file >= fileCount ||
            !Number.isSafeInteger(reference.offset) || reference.offset < 0 ||
            !Number.isSafeInteger(reference.count) || reference.count <= 0 ||
            !Number.isSafeInteger(counts[level] + reference.count)) {
          throw new Error('Streamed SOG 的 LOD 引用无效');
        }
        counts[level] += reference.count;
      }
      return;
    }
    if (!Array.isArray(node.children) || node.children.length !== 2 || node.lods) {
      throw new Error('Streamed SOG 层级树无效');
    }
    node.children.forEach((child) => visit(child, depth + 1));
  };
  visit(tree, 0);
  if (counts.some((count) => count <= 0)) throw new Error('Streamed SOG 缺少 LOD 点数');
  return counts;
}

export function parseStreamedSogManifest(value: unknown, files: SogPackageFile[], allowPartialFiles = false): StreamedSogManifest {
  const manifest = value as Partial<StreamedSogManifest> | null;
  if (!manifest || !Number.isInteger(manifest.lodLevels) ||
      (manifest.lodLevels ?? 0) < 1 || (manifest.lodLevels ?? 0) > 16 || !Array.isArray(manifest.filenames) ||
      !manifest.filenames.length || manifest.filenames.length > 100_000 || !manifest.tree) {
    throw new Error('Streamed SOG 根清单无效');
  }
  const filenames = manifest.filenames;
  const bundledChunks = filenames.every((path) => typeof path === 'string' && /\.sog$/i.test(path));
  if (filenames.some((path) => typeof path !== 'string' || !safePath(path) ||
      (!bundledChunks && !/\/meta\.json$/i.test(path))) ||
      new Set(filenames).size !== filenames.length ||
      (manifest.environment !== undefined && manifest.environment !== '' &&
        (typeof manifest.environment !== 'string' || !safePath(manifest.environment) ||
          !(/\.sog$/i.test(manifest.environment) || /\/meta\.json$/i.test(manifest.environment))))) {
    throw new Error('Streamed SOG 根清单无效');
  }
  const compatibleBundle = bundledChunks && manifest.version === undefined &&
    manifest.count === undefined && manifest.counts === undefined;
  if (!compatibleBundle && (manifest.version !== 1 || !Array.isArray(manifest.counts) ||
      manifest.counts.length !== manifest.lodLevels ||
      manifest.counts.some((count) => !Number.isSafeInteger(count) || count < 0) ||
      !Number.isSafeInteger(manifest.count) || manifest.counts.reduce((sum, count) => sum + count, 0) !== manifest.count)) {
    throw new Error('Streamed SOG 根清单无效');
  }
  const counts = compatibleBundle
    ? deriveCounts(manifest.tree, manifest.lodLevels!, filenames.length)
    : manifest.counts!;
  const available = new Set(files.map((file) => normalizePackagePath(file.relativePath || '')));
  const rootPath = normalizePackagePath(files.find((file) => file.role === 'MAIN' &&
    /(^|\/)lod-meta\.json$/i.test(file.relativePath || ''))?.relativePath ||
    files.find((file) => /(^|\/)lod-meta\.json$/i.test(file.relativePath || ''))?.relativePath || '');
  const packageRoot = rootPath.includes('/') ? rootPath.substring(0, rootPath.lastIndexOf('/')) : '';
  if (!allowPartialFiles) {
    for (const path of filenames) {
      if (!available.has(packageRoot ? `${packageRoot}/${path}` : path)) throw new Error(`Streamed SOG 缺少分块：${path}`);
    }
    if (manifest.environment && !available.has(packageRoot ? `${packageRoot}/${manifest.environment}` : manifest.environment)) {
      throw new Error(`Streamed SOG 缺少环境分块：${manifest.environment}`);
    }
  }
  const parsed: StreamedSogManifest = {
    ...manifest,
    count: compatibleBundle ? counts.reduce((sum, count) => sum + count, 0) : manifest.count!,
    counts,
    lodLevels: manifest.lodLevels!,
    filenames,
    tree: manifest.tree,
    bundledChunks,
    packageRoot,
  };
  for (let level = 0; level < parsed.lodLevels; level += 1) {
    if (!fileIndicesForLevel(parsed, level).length) throw new Error(`Streamed SOG 缺少 LOD ${level}`);
  }
  return parsed;
}

/** 使用树中真实的 file 索引，不依赖目录名的 <level>_<index> 命名约定。 */
export function fileIndicesForLevel(manifest: StreamedSogManifest, level: number): number[] {
  if (!Number.isInteger(level) || level < 0 || level >= manifest.lodLevels) return [];
  const indices = new Set<number>();
  const visit = (node: LodNode) => {
    if (node.lods) {
      const reference = node.lods[String(level)];
      if (reference) {
        if (!Number.isInteger(reference.file) || reference.file < 0 || reference.file >= manifest.filenames.length ||
            !Number.isInteger(reference.offset) || reference.offset < 0 ||
            !Number.isInteger(reference.count) || reference.count <= 0) {
          throw new Error(`Streamed SOG 的 LOD ${level} 引用无效`);
        }
        indices.add(reference.file);
      }
      return;
    }
    if (!Array.isArray(node.children) || node.children.length !== 2) throw new Error('Streamed SOG 层级树无效');
    node.children.forEach(visit);
  };
  visit(manifest.tree);
  return [...indices].sort((a, b) => a - b);
}

export type SogView = { position: readonly number[]; forward: readonly number[] };

/** Prioritize files contributing to the current view, independently of filename order. */
export function fileIndicesByView(manifest: StreamedSogManifest, view: SogView): number[] {
  const indices = manifest.filenames.map((_, index) => index);
  const [px, py, pz] = view.position;
  const [fx, fy, fz] = view.forward;
  const forwardLength = Math.hypot(fx, fy, fz);
  if (![px, py, pz, fx, fy, fz].every(Number.isFinite) || forwardLength < 1e-6) return indices;
  const forward = [fx / forwardLength, fy / forwardLength, fz / forwardLength];
  const scores = new Map<number, { behind: number; cost: number }>();
  const validBound = (bound?: LodNode['bound']) => bound &&
    bound.min?.length === 3 && bound.max?.length === 3 &&
    [...bound.min, ...bound.max].every(Number.isFinite);
  const visit = (node: LodNode, parentBound?: LodNode['bound']) => {
    const bound = validBound(node.bound) ? node.bound : parentBound;
    if (node.lods && bound) {
      const center = bound.min.map((min, axis) => (min + bound.max[axis]) / 2);
      const radius = Math.hypot(...bound.min.map((min, axis) => (bound.max[axis] - min) / 2));
      const dx = center[0] - px;
      const dy = center[1] - py;
      const dz = center[2] - pz;
      const distance = Math.hypot(dx, dy, dz);
      const dot = distance > 1e-6 ? (dx * forward[0] + dy * forward[1] + dz * forward[2]) / distance : 1;
      const angularRadius = Math.min(1, radius / Math.max(distance, 1e-6));
      const score = {
        behind: dot + angularRadius < 0 ? 1 : 0,
        cost: Math.max(0, distance - radius) * (1 + 3 * Math.max(0, 1 - dot - angularRadius)),
      };
      for (const reference of Object.values(node.lods)) {
        const previous = scores.get(reference.file);
        if (!previous || score.behind < previous.behind ||
            (score.behind === previous.behind && score.cost < previous.cost)) {
          scores.set(reference.file, score);
        }
      }
    } else if (node.children) {
      node.children.forEach((child) => visit(child, bound));
    }
  };
  visit(manifest.tree);
  return indices.sort((a, b) => {
    const left = scores.get(a);
    const right = scores.get(b);
    return (left?.behind ?? 2) - (right?.behind ?? 2) ||
      (left?.cost ?? Infinity) - (right?.cost ?? Infinity) || a - b;
  });
}

/** 导出或完整性校验时列出该层的全部分块及可选环境分块。 */
export function completeLevelPaths(manifest: StreamedSogManifest, level: number, includeEnvironment = false): string[] {
  const paths = fileIndicesForLevel(manifest, level).map((index) =>
    chunkPackagePath(manifest, manifest.filenames[index]));
  if (includeEnvironment && manifest.environment) {
    paths.push(chunkPackagePath(manifest, manifest.environment));
  }
  return [...new Set(paths)];
}

export function filesForChunk(files: SogPackageFile[], metaPath: string): SogPackageFile[] {
  if (/\.sog$/i.test(metaPath)) {
    const direct = files.filter((file) => normalizePackagePath(file.relativePath || '') === metaPath);
    if (direct.length !== 1) throw new Error(`Streamed SOG 缺少分块：${metaPath}`);
    return [{ ...direct[0], role: 'MAIN' }];
  }
  const directory = metaPath.substring(0, metaPath.lastIndexOf('/') + 1);
  const chunk = files.filter((file) => normalizePackagePath(file.relativePath || '').startsWith(directory));
  if (!chunk.some((file) => normalizePackagePath(file.relativePath || '') === metaPath)) {
    throw new Error(`Streamed SOG 缺少分块：${metaPath}`);
  }
  return chunk.map((file) => ({
    ...file,
    role: normalizePackagePath(file.relativePath || '') === metaPath ? 'MAIN' : file.role,
  }));
}
