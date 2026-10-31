import { modelDocument, encodeModelDocument } from './digitalModelDocument';
import { resolvePackageReference } from './digitalPackagePath';
const JSON_LIMIT = 32 * 1024 * 1024,
  MAX_DEPTH = 64;
type Kind = 'entry' | 'content' | 'model' | 'dependency' | 'schema';
const decode = (bytes: Uint8Array) => new TextDecoder('utf-8', { fatal: true }).decode(bytes);
const magic = (bytes: ArrayBuffer) =>
  bytes.byteLength >= 4 ? String.fromCharCode(...new Uint8Array(bytes, 0, 4)) : '';
function json(bytes: ArrayBuffer) {
  if (bytes.byteLength > JSON_LIMIT) throw new Error('瓦片或模型 JSON 超过32 MiB解析限额。');
  return JSON.parse(decode(new Uint8Array(bytes)));
}
/** Only reads the complete verified byte map. Never asks the SDK to resolve original package paths. */
export async function prepareVerifiedPackageGraph(
  entry: string,
  map: Record<string, string>,
  blobs: Map<string, Blob>,
  makeUrl: (blob: Blob) => string,
  check: () => void,
) {
  const completed = new Map<string, string>(),
    visiting = new Set<string>();
  let compressed = false,
    tiles = false;
  const resolve = (base: string, uri: string) => {
    const path = resolvePackageReference(base, uri);
    if (!Object.hasOwn(map, path)) throw new Error(`模型依赖未在本包登记：${path}`);
    return path;
  };
  async function rewrite(node: any, base: string, kind: 'model' | 'tiles' | 'schema', depth: number): Promise<void> {
    check();
    if (depth > 256) throw new Error('模型 JSON 嵌套层级过深。');
    if (!node || typeof node !== 'object') return;
    if (kind === 'tiles' && (node.implicitTiling || node.extensions?.['3DTILES_implicit_tiling']))
      throw new Error('隐式瓦片须先展开依赖后预览。');
    for (const key of Object.keys(node)) {
      if (key === 'extras') continue;
      const value = node[key];
      if (['uri', 'url', 'schemaUri'].includes(key)) {
        if (typeof value !== 'string') throw new Error('模型依赖地址格式无效。');
        if (/^data:/i.test(value)) {
          if (
            kind !== 'model' ||
            !/^data:(application\/(octet-stream|gltf-buffer)|image\/(png|jpeg|webp|ktx2));base64,[a-z0-9+/=\r\n]*$/i.test(
              value,
            )
          )
            throw new Error('模型内嵌依赖格式尚不支持。');
          continue;
        }
        node[key] = await visit(
          resolve(base, value),
          key === 'schemaUri' ? 'schema' : kind === 'tiles' ? 'content' : 'dependency',
        );
      } else await rewrite(value, base, kind, depth + 1);
    }
  }
  async function convertModel(data: ArrayBuffer, path: string, glb: boolean) {
    const parsed = modelDocument(data, glb);
    compressed ||= parsed.compressed;
    await rewrite(parsed.document, path, 'model', 0);
    return encodeModelDocument(parsed, glb);
  }
  function tileHeader(data: ArrayBuffer, size: number, type: string) {
    const v = new DataView(data);
    if (data.byteLength < size || v.getUint32(4, true) !== 1 || v.getUint32(8, true) !== data.byteLength)
      throw new Error(`${type} 瓦片头或长度无效。`);
    return v;
  }
  async function embeddedModel(data: ArrayBuffer, offset: number, path: string) {
    if (offset + 12 > data.byteLength) throw new Error('瓦片缺少内嵌GLB。');
    const v = new DataView(data),
      length = v.getUint32(offset + 8, true);
    if (offset + length > data.byteLength || length < 20) throw new Error('内嵌GLB越界。');
    if (new Uint8Array(data, offset + length).some((x) => x !== 0 && x !== 32)) throw new Error('内嵌GLB尾部无效。');
    return convertModel(data.slice(offset, offset + length), path, true);
  }
  async function binaryTile(data: ArrayBuffer, path: string, depth: number): Promise<Blob> {
    check();
    if (depth > MAX_DEPTH) throw new Error('复合瓦片嵌套过深。');
    const type = magic(data);
    if (type === 'cmpt') {
      const v = tileHeader(data, 16, type),
        count = v.getUint32(12, true);
      if (!count || count > 10000) throw new Error('复合瓦片数量无效。');
      let offset = 16;
      const parts: Blob[] = [];
      for (let i = 0; i < count; i++) {
        if (offset + 12 > data.byteLength) throw new Error('复合瓦片不完整。');
        const n = v.getUint32(offset + 8, true);
        if (n < 12 || offset + n > data.byteLength) throw new Error('复合瓦片越界。');
        parts.push(await binaryTile(data.slice(offset, offset + n), path, depth + 1));
        offset += n;
      }
      if (offset !== data.byteLength) throw new Error('复合瓦片总长度不一致。');
      const head = data.slice(0, 16);
      new DataView(head).setUint32(8, 16 + parts.reduce((sum, p) => sum + p.size, 0), true);
      return new Blob([head, ...parts]);
    }
    if (!['b3dm', 'i3dm', 'pnts'].includes(type)) throw new Error('瓦片容器格式尚不支持。');
    const headerSize = type === 'i3dm' ? 32 : 28,
      v = tileHeader(data, headerSize, type);
    let offset = headerSize;
    for (let i = 0; i < 4; i++) {
      const n = v.getUint32(12 + i * 4, true);
      if (offset + n > data.byteLength) throw new Error('瓦片属性表越界。');
      if (i % 2 === 0 && n) {
        if (n > JSON_LIMIT) throw new Error('瓦片属性JSON过大。');
        JSON.parse(decode(new Uint8Array(data, offset, n)).replace(/[\x00 ]+$/, ''));
      }
      offset += n;
    }
    if (type === 'pnts') {
      if (offset !== data.byteLength) throw new Error('点云瓦片总长度不一致。');
      return new Blob([data]);
    }
    let body: Blob;
    if (type === 'i3dm' && v.getUint32(28, true) === 0) {
      const uri = decode(new Uint8Array(data, offset)).replace(/[\x00 ]+$/, '');
      body = new Blob([await visit(resolve(path, uri), 'model')]);
    } else {
      if (type === 'i3dm' && v.getUint32(28, true) !== 1) throw new Error('i3dm 模型格式标识无效。');
      body = await embeddedModel(data, offset, path);
    }
    const padding = (8 - ((offset + body.size) % 8)) % 8,
      head = data.slice(0, offset);
    new DataView(head).setUint32(8, offset + body.size + padding, true);
    return new Blob([
      head,
      body,
      new Uint8Array(padding).fill(type === 'i3dm' && v.getUint32(28, true) === 0 ? 32 : 0),
    ]);
  }
  async function visit(path: string, kind: Kind): Promise<string> {
    check();
    const key = kind + ':' + path;
    if (visiting.has(path)) throw new Error('瓦片或模型依赖存在循环。');
    if (completed.has(key)) return completed.get(key)!;
    if (visiting.size >= MAX_DEPTH) throw new Error('瓦片依赖嵌套超过64层。');
    visiting.add(path);
    try {
      const original = blobs.get(map[path]);
      if (!original) throw new Error('缺少已核验的模型依赖。');
      let result: Blob = original;
      if (kind === 'dependency') {
        const type = /\.png$/i.test(path)
          ? 'image/png'
          : /\.jpe?g$/i.test(path)
            ? 'image/jpeg'
            : /\.webp$/i.test(path)
              ? 'image/webp'
              : /\.ktx2$/i.test(path)
                ? 'image/ktx2'
                : 'application/octet-stream';
        result = original.slice(0, undefined, type);
      } else {
        const data = await original.arrayBuffer();
        check();
        const tag = magic(data);
        if (tag === 'glTF') result = await convertModel(data, path, true);
        else if (['b3dm', 'i3dm', 'pnts', 'cmpt'].includes(tag)) result = await binaryTile(data, path, 0);
        else {
          const document = json(data);
          if (kind === 'schema') {
            await rewrite(document, path, 'schema', 0);
            result = new Blob([JSON.stringify(document)], { type: 'application/json' });
          } else if (document?.root && document?.asset) {
            if (!['1.0', '1.1'].includes(document.asset.version)) throw new Error('瓦片清单版本尚不支持。');
            for (const extension of document.extensionsRequired || [])
              if (
                ![
                  '3DTILES_content_gltf',
                  '3DTILES_multiple_contents',
                  '3DTILES_metadata',
                  '3DTILES_bounding_volume_S2',
                ].includes(extension)
              )
                throw new Error(`瓦片所需扩展尚不支持：${extension}`);
            if (kind === 'entry') tiles = true;
            await rewrite(document, path, 'tiles', 0);
            result = new Blob([JSON.stringify(document)], { type: 'application/json' });
          } else result = await convertModel(data, path, false);
        }
      }
      check();
      const url = makeUrl(result);
      completed.set(key, url);
      return url;
    } finally {
      visiting.delete(path);
    }
  }
  const url = await visit(entry, 'entry');
  check();
  return { url, compressed, fileType: tiles ? 'tileset' : /\.glb$/i.test(entry) ? 'glb' : 'gltf' };
}
