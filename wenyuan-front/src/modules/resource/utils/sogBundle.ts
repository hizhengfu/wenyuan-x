/**
 * SOG（Spatially Ordered Gaussians）预览所需的字节装配。
 *
 * PlayCanvas 的 gsplat 处理器用 `url.original` 推导扩展名来挑选解析器，而本项目的文件 URL 形如
 * `file/preview?fileId=...`，去掉查询串后没有扩展名，因此不能把 URL 直接交给引擎。这里的做法是
 * 自己带鉴权取回字节，再以 `.sog` 占位 URL + `contents` 注入，让引擎跳过网络请求。
 *
 * 单文件和普通目录版会归一到“打包版”再交给引擎：
 *   打包版  —— .sog 本身就是 ZIP，字节直接透传；
 *   目录版  —— meta.json 加若干固定命名的 WebP，需在内存中重新装配成 ZIP。
 * Streamed SOG 的 lod-meta.json 由 streamedSog.ts 选层；目录分块在此装配，.sog 分块直接透传。
 */

export type SogPackageFile = {
  relativePath?: string;
  originalName?: string;
  filePath?: string;
  role?: string;
  fileExt?: string;
  fileSize?: number;
};

type ZipInput = { name: string; data: Uint8Array };

const META_FILE_NAME = 'meta.json';
const SOG_PROPERTY_KEYS = ['means', 'scales', 'quats', 'sh0', 'shN'] as const;

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let index = 0; index < 256; index += 1) {
    let value = index;
    for (let bit = 0; bit < 8; bit += 1) {
      value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
    }
    table[index] = value >>> 0;
  }
  return table;
})();

export function crc32(bytes: Uint8Array): number {
  let crc = 0xffffffff;
  for (let index = 0; index < bytes.length; index += 1) {
    crc = CRC_TABLE[(crc ^ bytes[index]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

export function normalizePackagePath(path: string): string {
  return path.replace(/\\/g, '/').replace(/^\.\//, '').replace(/^\/+/, '');
}

export function baseName(path: string): string {
  const normalized = normalizePackagePath(path);
  const index = normalized.lastIndexOf('/');
  return index < 0 ? normalized : normalized.substring(index + 1);
}

function parentDir(path: string): string {
  const normalized = normalizePackagePath(path);
  const index = normalized.lastIndexOf('/');
  return index < 0 ? '' : normalized.substring(0, index);
}

/**
 * 生成只做存储（compression method 0）的 ZIP。
 *
 * PlayCanvas 的 SogBundleParser 按固定偏移读取中央目录与 EOCD，且要求 EOCD 正好位于文件末尾
 * （无注释、不支持 Zip64），因此这里严格按该布局输出，不写入注释字段。
 */
export function buildStoreZip(entries: ZipInput[]): ArrayBuffer {
  if (entries.length > 64) throw new Error('SOG 资源包文件数超出限制');
  const normalizedNames = entries.map((entry) => safeSogReference(entry.name));
  if (new Set(normalizedNames).size !== normalizedNames.length) throw new Error('SOG 资源包存在重复文件名');
  const encoder = new TextEncoder();
  const prepared = entries.map((entry, index) => ({
    nameBytes: encoder.encode(normalizedNames[index]),
    data: entry.data,
    crc: crc32(entry.data),
  }));

  const localSize = prepared.reduce((sum, item) => sum + 30 + item.nameBytes.length + item.data.length, 0);
  const centralSize = prepared.reduce((sum, item) => sum + 46 + item.nameBytes.length, 0);
  const buffer = new ArrayBuffer(localSize + centralSize + 22);
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const localOffsets: number[] = [];
  let offset = 0;

  prepared.forEach((item) => {
    localOffsets.push(offset);
    view.setUint32(offset, 0x04034b50, true);
    view.setUint16(offset + 4, 20, true);
    view.setUint16(offset + 6, 0, true);
    view.setUint16(offset + 8, 0, true);
    view.setUint16(offset + 10, 0, true);
    view.setUint16(offset + 12, 0, true);
    view.setUint32(offset + 14, item.crc, true);
    view.setUint32(offset + 18, item.data.length, true);
    view.setUint32(offset + 22, item.data.length, true);
    view.setUint16(offset + 26, item.nameBytes.length, true);
    view.setUint16(offset + 28, 0, true);
    offset += 30;
    bytes.set(item.nameBytes, offset);
    offset += item.nameBytes.length;
    bytes.set(item.data, offset);
    offset += item.data.length;
  });

  const centralStart = offset;
  prepared.forEach((item, index) => {
    view.setUint32(offset, 0x02014b50, true);
    view.setUint16(offset + 4, 20, true);
    view.setUint16(offset + 6, 20, true);
    view.setUint16(offset + 8, 0, true);
    view.setUint16(offset + 10, 0, true);
    view.setUint16(offset + 12, 0, true);
    view.setUint16(offset + 14, 0, true);
    view.setUint32(offset + 16, item.crc, true);
    view.setUint32(offset + 20, item.data.length, true);
    view.setUint32(offset + 24, item.data.length, true);
    view.setUint16(offset + 28, item.nameBytes.length, true);
    view.setUint16(offset + 30, 0, true);
    view.setUint16(offset + 32, 0, true);
    view.setUint16(offset + 34, 0, true);
    view.setUint16(offset + 36, 0, true);
    view.setUint32(offset + 38, 0, true);
    view.setUint32(offset + 42, localOffsets[index], true);
    offset += 46;
    bytes.set(item.nameBytes, offset);
    offset += item.nameBytes.length;
  });

  view.setUint32(offset, 0x06054b50, true);
  view.setUint16(offset + 4, 0, true);
  view.setUint16(offset + 6, 0, true);
  view.setUint16(offset + 8, prepared.length, true);
  view.setUint16(offset + 10, prepared.length, true);
  view.setUint32(offset + 12, offset - centralStart, true);
  view.setUint32(offset + 16, centralStart, true);
  view.setUint16(offset + 20, 0, true);

  return buffer;
}

/** 目录版 SOG 的入口文件，即 meta.json。 */
export function findSogEntryFile(files: SogPackageFile[]): SogPackageFile | undefined {
  return (
    files.find((item) => item.role === 'MAIN' && baseName(item.relativePath || '').toLowerCase() === META_FILE_NAME) ||
    files.find((item) => baseName(item.relativePath || '').toLowerCase() === META_FILE_NAME)
  );
}

/**
 * meta.json 中的文件名是相对入口目录的裸名，这里优先在同级目录内匹配，
 * 其次在整个资源包内按唯一文件名匹配。
 */
function resolveSogDataFile(
  files: SogPackageFile[],
  entryDir: string,
  fileName: string,
): SogPackageFile | undefined {
  const reference = safeSogReference(fileName);
  const exactPath = entryDir ? `${entryDir}/${reference}` : reference;
  const exact = files.filter((item) => normalizePackagePath(item.relativePath || '') === exactPath);
  if (exact.length === 1) return exact[0];
  if (exact.length > 1 || reference.includes('/')) return undefined;

  // 兼容旧数据只允许裸文件名做全包唯一回退，否则同名文件会绑错。
  const matched = files.filter((item) => baseName(item.relativePath || '') === reference);
  return matched.length === 1 ? matched[0] : undefined;
}

function safeSogReference(value: string): string {
  if (!value || value.includes('\\') || value.startsWith('/') || value.startsWith('//')) {
    throw new Error(`SOG 数据文件路径不安全：${value}`);
  }
  if (/^[a-z][a-z0-9+.-]*:/i.test(value)) throw new Error(`SOG 数据文件不能使用外部地址：${value}`);
  const parts = value.split('/');
  if (parts.some((part) => !part || part === '.' || part === '..')) {
    throw new Error(`SOG 数据文件路径不安全：${value}`);
  }
  return parts.join('/');
}

export function sogDataFileNames(meta: any): string[] {
  return SOG_PROPERTY_KEYS.flatMap((key) => {
    const files = meta?.[key]?.files;
    return Array.isArray(files) ? files.filter((item: unknown): item is string => typeof item === 'string') : [];
  });
}

export async function buildSogBundleFromPackage(
  files: SogPackageFile[],
  readFile: (file: SogPackageFile) => Promise<Uint8Array>,
): Promise<ArrayBuffer> {
  const entry = findSogEntryFile(files);
  if (!entry?.filePath) throw new Error('资源包缺少 SOG 入口文件 meta.json');

  const entryBytes = await readFile(entry);
  let meta: any;
  try {
    meta = JSON.parse(new TextDecoder().decode(entryBytes));
  } catch {
    throw new Error('SOG 入口文件 meta.json 不是合法的 JSON');
  }

  const entryDir = parentDir(entry.relativePath || '');
  const names = sogDataFileNames(meta).map(safeSogReference);
  if (new Set(names).size !== names.length) throw new Error('SOG meta.json 存在重复的数据文件引用');
  const loaded = await Promise.all(names.map(async (name) => {
    const match = resolveSogDataFile(files, entryDir, name);
    if (!match?.filePath) throw new Error(`资源包缺少 SOG 数据文件：${name}`);
    return { name, data: await readFile(match) };
  }));
  const entries: ZipInput[] = [{ name: META_FILE_NAME, data: entryBytes }, ...loaded];
  return buildStoreZip(entries);
}
