const supportedExtensions = new Set([
  'KHR_materials_unlit',
  'KHR_materials_pbrSpecularGlossiness',
  'KHR_texture_transform',
  'KHR_mesh_quantization',
  'KHR_materials_clearcoat',
  'EXT_texture_webp',
  'KHR_materials_sheen',
  'KHR_materials_transmission',
  'KHR_materials_volume',
  'KHR_materials_ior',
  'KHR_materials_emissive_strength',
  'KHR_materials_specular',
  'KHR_materials_iridescence',
  'KHR_lights_punctual',
  'KHR_draco_mesh_compression',
  'EXT_meshopt_compression',
  'KHR_texture_basisu',
]);

const JSON_LIMIT = 32 * 1024 * 1024;
export function modelDocument(bytes: ArrayBuffer, glb: boolean) {
  let jsonBytes: Uint8Array, tail: Uint8Array | undefined;
  if (glb) {
    if (bytes.byteLength < 20) throw new Error('GLB 文件头不完整。');
    const view = new DataView(bytes),
      count = view.getUint32(12, true);
    if (
      view.getUint32(0, true) !== 0x46546c67 ||
      view.getUint32(4, true) !== 2 ||
      view.getUint32(8, true) !== bytes.byteLength ||
      view.getUint32(16, true) !== 0x4e4f534a ||
      !count ||
      count % 4 ||
      count > JSON_LIMIT ||
      20 + count > bytes.byteLength
    )
      throw new Error('GLB 头或 JSON 块无效。');
    let offset = 20 + count;
    while (offset < bytes.byteLength) {
      if (offset + 8 > bytes.byteLength) throw new Error('GLB 数据块不完整。');
      const length = view.getUint32(offset, true);
      if (length % 4 || offset + 8 + length > bytes.byteLength) throw new Error('GLB 数据块越界。');
      offset += 8 + length;
    }
    jsonBytes = new Uint8Array(bytes, 20, count);
    tail = new Uint8Array(bytes, 20 + count);
  } else {
    if (bytes.byteLength > JSON_LIMIT) throw new Error('glTF JSON 超过 32 MiB 解析限额。');
    jsonBytes = new Uint8Array(bytes);
  }
  const document = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(jsonBytes));
  if (document?.asset?.version !== '2.0') throw new Error('当前预览要求 glTF 2.0。');
  for (const name of document.extensionsRequired || [])
    if (!supportedExtensions.has(name)) throw new Error(`当前预览尚未接入所需扩展：${name}，请下载原件核对。`);
  const compressed = [...(document.extensionsUsed || []), ...(document.extensionsRequired || [])].some((name) =>
    ['KHR_draco_mesh_compression', 'EXT_meshopt_compression', 'KHR_texture_basisu'].includes(name),
  );
  if (
    compressed &&
    [...(document.extensionsUsed || []), ...(document.extensionsRequired || [])].includes(
      'KHR_materials_pbrSpecularGlossiness',
    )
  )
    throw new Error('压缩模型与旧版高光光泽材质的组合尚未接入，请下载原件核对。');
  return { document, tail, compressed };
}

export function encodeModelDocument(parsed: ReturnType<typeof modelDocument>, glb: boolean): Blob {
  const json = new TextEncoder().encode(JSON.stringify(parsed.document));
  if (!glb) return new Blob([json], { type: 'model/gltf+json' });
  const length = Math.ceil(json.length / 4) * 4,
    head = new ArrayBuffer(20),
    view = new DataView(head);
  view.setUint32(0, 0x46546c67, true);
  view.setUint32(4, 2, true);
  view.setUint32(8, 20 + length + parsed.tail!.length, true);
  view.setUint32(12, length, true);
  view.setUint32(16, 0x4e4f534a, true);
  return new Blob([head, json, new Uint8Array(length - json.length).fill(32), parsed.tail as Uint8Array<ArrayBuffer>], {
    type: 'model/gltf-binary',
  });
}
