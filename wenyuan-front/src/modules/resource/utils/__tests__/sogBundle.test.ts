import { describe, expect, it } from 'vitest';
import {
  buildSogBundleFromPackage,
  buildStoreZip,
  crc32,
  findSogEntryFile,
  normalizePackagePath,
  sogDataFileNames,
} from '../sogBundle';

const encoder = new TextEncoder();

/**
 * 按 PlayCanvas SogBundleParser 的读取方式解析 ZIP：
 * EOCD 必须正好位于文件末尾 22 字节处，中央目录由 cdOffset/cdSize 定位。
 */
function parseZipLikePlayCanvas(buffer: ArrayBuffer) {
  const view = new DataView(buffer);
  const u16 = (offset: number) => view.getUint16(offset, true);
  const u32 = (offset: number) => view.getUint32(offset, true);

  const eocdOffset = view.byteLength - 22;
  expect(u32(eocdOffset)).toBe(0x06054b50);

  const numFiles = u16(eocdOffset + 8);
  const cdSize = u32(eocdOffset + 12);
  const cdOffset = u32(eocdOffset + 16);
  expect(cdSize).toBe(eocdOffset - cdOffset);

  const entries: Array<{ name: string; data: Uint8Array }> = [];
  let offset = cdOffset;
  for (let index = 0; index < numFiles; index += 1) {
    expect(u32(offset)).toBe(0x02014b50);
    const method = u16(offset + 10);
    const crc = u32(offset + 16);
    const compressedSize = u32(offset + 20);
    const uncompressedSize = u32(offset + 24);
    const nameLength = u16(offset + 28);
    const localOffset = u32(offset + 42);
    const name = new TextDecoder().decode(new Uint8Array(buffer, offset + 46, nameLength));

    expect(method).toBe(0);
    expect(compressedSize).toBe(uncompressedSize);

    expect(u32(localOffset)).toBe(0x04034b50);
    const localNameLength = u16(localOffset + 26);
    const dataStart = localOffset + 30 + localNameLength;
    const data = new Uint8Array(buffer, dataStart, compressedSize);
    expect(u32(localOffset + 14)).toBe(crc);
    expect(crc32(data)).toBe(crc);

    entries.push({ name, data });
    offset += 46 + nameLength;
  }
  return entries;
}

describe('crc32', () => {
  it('matches the standard check value', () => {
    expect(crc32(encoder.encode('123456789'))).toBe(0xcbf43926);
    expect(crc32(new Uint8Array())).toBe(0);
  });
});

describe('buildStoreZip', () => {
  it('produces an archive the SOG bundle parser can read back', () => {
    const meta = encoder.encode('{"version":2,"count":7}');
    const webp = new Uint8Array([1, 2, 3, 4, 5]);

    const entries = parseZipLikePlayCanvas(
      buildStoreZip([
        { name: 'meta.json', data: meta },
        { name: 'means_l.webp', data: webp },
      ]),
    );

    expect(entries.map((entry) => entry.name)).toEqual(['meta.json', 'means_l.webp']);
    expect(new TextDecoder().decode(entries[0].data)).toBe('{"version":2,"count":7}');
    expect(Array.from(entries[1].data)).toEqual([1, 2, 3, 4, 5]);
  });

  it('handles an empty archive', () => {
    const buffer = buildStoreZip([]);
    expect(buffer.byteLength).toBe(22);
    expect(parseZipLikePlayCanvas(buffer)).toEqual([]);
  });
});

describe('sogDataFileNames', () => {
  it('collects referenced files across all properties', () => {
    expect(
      sogDataFileNames({
        means: { files: ['means_l.webp', 'means_u.webp'] },
        scales: { files: ['scales.webp'] },
        quats: { files: ['quats.webp'] },
        sh0: { files: ['sh0.webp'] },
        shN: { files: ['shN_centroids.webp', 'shN_labels.webp'] },
      }),
    ).toEqual([
      'means_l.webp',
      'means_u.webp',
      'scales.webp',
      'quats.webp',
      'sh0.webp',
      'shN_centroids.webp',
      'shN_labels.webp',
    ]);
  });

  it('tolerates a scene without higher order sh', () => {
    expect(sogDataFileNames({ means: { files: ['means_l.webp'] } })).toEqual(['means_l.webp']);
    expect(sogDataFileNames({})).toEqual([]);
    expect(sogDataFileNames(null)).toEqual([]);
  });
});

describe('findSogEntryFile', () => {
  it('prefers the main role and accepts nested entry files', () => {
    const files = [
      { relativePath: 'hall/means_l.webp', filePath: '2', role: 'TEXTURE' },
      { relativePath: 'hall/meta.json', filePath: '1', role: 'MAIN' },
    ];
    expect(findSogEntryFile(files)?.filePath).toBe('1');
    expect(findSogEntryFile([{ relativePath: 'hall\\meta.json', filePath: '3' }])?.filePath).toBe('3');
    expect(findSogEntryFile([{ relativePath: 'hall/tileset.json', filePath: '4' }])).toBeUndefined();
  });
});

describe('buildSogBundleFromPackage', () => {
  it('repacks a directory layout into a bundled sog', async () => {
    const meta = {
      version: 2,
      count: 3,
      means: { files: ['means_l.webp', 'means_u.webp'] },
      scales: { files: ['scales.webp'] },
      quats: { files: ['quats.webp'] },
      sh0: { files: ['sh0.webp'] },
    };
    const files = [
      { relativePath: 'hall/meta.json', filePath: 'meta', role: 'MAIN' },
      { relativePath: 'hall/means_l.webp', filePath: 'a', role: 'TEXTURE' },
      { relativePath: 'hall/means_u.webp', filePath: 'b', role: 'TEXTURE' },
      { relativePath: 'hall/scales.webp', filePath: 'c', role: 'TEXTURE' },
      { relativePath: 'hall/quats.webp', filePath: 'd', role: 'TEXTURE' },
      { relativePath: 'hall/sh0.webp', filePath: 'e', role: 'TEXTURE' },
    ];
    const bytesByPath: Record<string, Uint8Array> = {
      meta: encoder.encode(JSON.stringify(meta)),
      a: new Uint8Array([10]),
      b: new Uint8Array([11]),
      c: new Uint8Array([12]),
      d: new Uint8Array([13]),
      e: new Uint8Array([14]),
    };

    const buffer = await buildSogBundleFromPackage(files, async (file) => bytesByPath[file.filePath!]);
    const entries = parseZipLikePlayCanvas(buffer);

    expect(entries.map((entry) => entry.name)).toEqual([
      'meta.json',
      'means_l.webp',
      'means_u.webp',
      'scales.webp',
      'quats.webp',
      'sh0.webp',
    ]);
    expect(entries[1].data[0]).toBe(10);
    expect(entries[5].data[0]).toBe(14);
  });

  it('fails loudly when a referenced data file is missing', async () => {
    const meta = { version: 2, count: 1, means: { files: ['means_l.webp', 'means_u.webp'] } };
    const files = [
      { relativePath: 'hall/meta.json', filePath: 'meta', role: 'MAIN' },
      { relativePath: 'hall/means_l.webp', filePath: 'a', role: 'TEXTURE' },
    ];

    await expect(
      buildSogBundleFromPackage(files, async (file) => encoder.encode(file.filePath === 'meta' ? JSON.stringify(meta) : '')),
    ).rejects.toThrow('means_u.webp');
  });

  it('resolves nested references relative to the entry directory', async () => {
    const meta = { version: 2, count: 1, means: { files: ['data/means_l.webp'] } };
    const files = [
      { relativePath: 'hall/meta.json', filePath: 'meta', role: 'MAIN' },
      { relativePath: 'hall/data/means_l.webp', filePath: 'means' },
      { relativePath: 'other/data/means_l.webp', filePath: 'wrong' },
    ];
    const read: string[] = [];

    const buffer = await buildSogBundleFromPackage(files, async (file) => {
      read.push(file.filePath!);
      return file.filePath === 'meta' ? encoder.encode(JSON.stringify(meta)) : encoder.encode(file.filePath!);
    });

    expect(read).toContain('means');
    expect(read).not.toContain('wrong');
    expect(parseZipLikePlayCanvas(buffer).map((entry) => entry.name)).toEqual(['meta.json', 'data/means_l.webp']);
  });

  it('rejects path escape external urls and duplicate references', async () => {
    const makeFiles = (meta: any) => [
      { relativePath: 'hall/meta.json', filePath: JSON.stringify(meta), role: 'MAIN' },
      { relativePath: 'hall/means.webp', filePath: 'data' },
    ];
    const read = async (file: any) => encoder.encode(file.role === 'MAIN' ? file.filePath : 'data');

    await expect(buildSogBundleFromPackage(makeFiles({ means: { files: ['../means.webp'] } }), read))
      .rejects.toThrow('路径不安全');
    await expect(buildSogBundleFromPackage(makeFiles({ means: { files: ['https://example.com/means.webp'] } }), read))
      .rejects.toThrow('外部地址');
    await expect(buildSogBundleFromPackage(makeFiles({ means: { files: ['means.webp', 'means.webp'] } }), read))
      .rejects.toThrow('重复');
  });

  it('rejects a package without an entry file', async () => {
    await expect(
      buildSogBundleFromPackage([{ relativePath: 'hall/readme.txt', filePath: 'x' }], async () => new Uint8Array()),
    ).rejects.toThrow('meta.json');
  });
});

describe('normalizePackagePath', () => {
  it('normalizes separators and leading markers', () => {
    expect(normalizePackagePath('hall\\sub\\meta.json')).toBe('hall/sub/meta.json');
    expect(normalizePackagePath('./hall/meta.json')).toBe('hall/meta.json');
    expect(normalizePackagePath('/hall/meta.json')).toBe('hall/meta.json');
  });
});
