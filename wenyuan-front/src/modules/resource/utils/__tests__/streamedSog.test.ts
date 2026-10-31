import { describe, expect, it } from 'vitest';
import { chunkPackagePath, completeLevelPaths, fileIndicesByView, fileIndicesForLevel, filesForChunk, initialLevelForStreamedSog, parseStreamedSogManifest } from '../streamedSog';

const manifest = {
  version: 1,
  count: 30,
  counts: [20, 10],
  lodLevels: 2,
  filenames: ['0_0/meta.json', '1_0/meta.json'],
  tree: {
    bound: { min: [-1, -1, -1], max: [1, 1, 1] },
    children: [
      { lods: { 0: { file: 0, offset: 0, count: 10 }, 1: { file: 1, offset: 0, count: 5 } } },
      { lods: { 0: { file: 0, offset: 10, count: 10 }, 1: { file: 1, offset: 5, count: 5 } } },
    ],
  },
} as const;

const files = [
  { relativePath: 'lod-meta.json', filePath: '/lod' },
  { relativePath: '0_0/meta.json', filePath: '/fine' },
  { relativePath: '0_0/means_l.webp', filePath: '/fine-means' },
  { relativePath: '1_0/meta.json', filePath: '/coarse' },
  { relativePath: '1_0/means_l.webp', filePath: '/coarse-means' },
];

describe('Streamed SOG', () => {
  it('selects chunks from tree references and keeps each chunk isolated', () => {
    const parsed = parseStreamedSogManifest(manifest, files);
    expect(fileIndicesForLevel(parsed, 0)).toEqual([0]);
    expect(fileIndicesForLevel(parsed, 1)).toEqual([1]);
    const chunk = filesForChunk(files, '1_0/meta.json');
    expect(chunk.map((file) => file.relativePath)).toEqual(['1_0/meta.json', '1_0/means_l.webp']);
    expect(chunk[0].role).toBe('MAIN');
  });

  it('starts at the least expensive LOD even when it is not the last level', () => {
    const parsed = parseStreamedSogManifest(manifest, files);
    expect(initialLevelForStreamedSog(parsed)).toBe(1);
    expect(initialLevelForStreamedSog({ ...parsed, counts: [5, 20] })).toBe(0);
  });

  it('rejects missing or unsafe referenced chunks', () => {
    expect(() => parseStreamedSogManifest(manifest, files.slice(0, 3))).toThrow('缺少分块');
    const partial = parseStreamedSogManifest(manifest, files.slice(0, 1), true);
    expect(fileIndicesForLevel(partial, 1)).toEqual([1]);
    expect(() => parseStreamedSogManifest({ ...manifest, filenames: ['../escape/meta.json', '1_0/meta.json'] }, files)).toThrow('根清单无效');
  });

  it('resolves chunks beneath a ZIP wrapper directory', () => {
    const wrapped = files.map((file) => ({
      ...file,
      relativePath: `hash/${file.relativePath}`,
      role: file.relativePath === 'lod-meta.json' ? 'MAIN' : undefined,
    }));
    const parsed = parseStreamedSogManifest(manifest, wrapped);
    const path = chunkPackagePath(parsed, parsed.filenames[1]);
    expect(path).toBe('hash/1_0/meta.json');
    expect(filesForChunk(wrapped, path).length).toBe(2);
  });

  it('accepts bundled SOG chunks with counts derived from the LOD tree', () => {
    const bundled = {
      environment: '',
      lodLevels: 2,
      filenames: ['0_0.sog', '1_0.sog'],
      tree: {
        bound: { min: [-1, -1, -1], max: [1, 1, 1] },
        lods: { 0: { file: 0, offset: 0, count: 20 }, 1: { file: 1, offset: 0, count: 10 } },
      },
    };
    const bundledFiles = [
      { relativePath: 'lod-meta.json', filePath: '/lod' },
      { relativePath: '0_0.sog', filePath: '/fine' },
      { relativePath: '1_0.sog', filePath: '/coarse' },
    ];
    const parsed = parseStreamedSogManifest(bundled, bundledFiles);
    expect(parsed.bundledChunks).toBe(true);
    expect(parsed.counts).toEqual([20, 10]);
    expect(fileIndicesForLevel(parsed, 1)).toEqual([1]);
    expect(filesForChunk(bundledFiles, '1_0.sog')).toEqual([{ ...bundledFiles[2], role: 'MAIN' }]);
    expect(() => parseStreamedSogManifest({ ...bundled, filenames: ['0_0.sog', '../1_0.sog'] }, bundledFiles)).toThrow('根清单无效');
    expect(() => parseStreamedSogManifest(bundled, bundledFiles.slice(0, 2))).toThrow('缺少分块');
  });

  it('collects a complete manually selected LOD and its environment before display', () => {
    const bundled = {
      environment: 'env.sog', lodLevels: 2,
      filenames: ['0_0.sog', '0_1.sog', '1_0.sog'],
      tree: { bound: { min: [-1, -1, -1], max: [1, 1, 1] }, children: [
        { bound: { min: [-1, -1, -1], max: [0, 1, 1] }, lods: { 0: { file: 0, offset: 0, count: 10 }, 1: { file: 2, offset: 0, count: 5 } } },
        { bound: { min: [0, -1, -1], max: [1, 1, 1] }, lods: { 0: { file: 1, offset: 0, count: 10 }, 1: { file: 2, offset: 5, count: 5 } } },
      ] },
    };
    const parsed = parseStreamedSogManifest(bundled, [
      { relativePath: 'lod-meta.json', filePath: '/lod' },
      ...[...bundled.filenames, bundled.environment].map((relativePath) => ({ relativePath, filePath: `/${relativePath}` })),
    ]);
    expect(completeLevelPaths(parsed, 0, true)).toEqual(['0_0.sog', '0_1.sog', 'env.sog']);
    expect(completeLevelPaths(parsed, 1, true)).toEqual(['1_0.sog', 'env.sog']);
  });

  it('prioritizes chunks near the saved view before chunks behind the camera', () => {
    const spatial = {
      version: 1, count: 6, counts: [3, 3], lodLevels: 2,
      filenames: ['behind.sog', 'front.sog', 'side.sog', 'coarse.sog'],
      tree: {
        bound: { min: [-12, -1, -12], max: [12, 1, 12] },
        children: [
          { bound: { min: [-1, -1, -11], max: [1, 1, -9] }, lods: { 0: { file: 1, offset: 0, count: 1 }, 1: { file: 3, offset: 0, count: 1 } } },
          { bound: { min: [-12, -1, -11], max: [12, 1, 11] }, children: [
            { bound: { min: [9, -1, -11], max: [11, 1, -9] }, lods: { 0: { file: 2, offset: 0, count: 1 }, 1: { file: 3, offset: 1, count: 1 } } },
            { bound: { min: [-1, -1, 9], max: [1, 1, 11] }, lods: { 0: { file: 0, offset: 0, count: 1 }, 1: { file: 3, offset: 2, count: 1 } } },
          ] },
        ],
      },
    };
    const parsed = parseStreamedSogManifest(spatial, [
      { relativePath: 'lod-meta.json', filePath: '/lod' },
      ...spatial.filenames.map((relativePath) => ({ relativePath, filePath: `/${relativePath}` })),
    ]);
    const frontView = fileIndicesByView(parsed, { position: [0, 0, 0], forward: [0, 0, -1] });
    expect(frontView.indexOf(1)).toBeLessThan(frontView.indexOf(2));
    expect(frontView.indexOf(2)).toBeLessThan(frontView.indexOf(0));
    const reverseView = fileIndicesByView(parsed, { position: [0, 0, 0], forward: [0, 0, 1] });
    expect(reverseView.indexOf(0)).toBeLessThan(reverseView.indexOf(1));
  });
});
