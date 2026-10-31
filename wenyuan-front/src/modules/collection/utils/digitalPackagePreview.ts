import { requirePackagePath } from './digitalPackagePath';
export { resolvePackageReference } from './digitalPackagePath';
import { prepareVerifiedPackageGraph } from './digitalPackageGraph';
import { isEqual } from 'lodash-es';
import { digitalAcquisitionApi as api } from '../api/digitalAcquisition';

export interface DigitalPreviewResource {
  id: string;
  content: { fileIds: string[]; packageFiles?: Record<string, string> | null };
  files: { fileId: string; name: string; path: string; storageType: string; size: number; sha256: string }[];
}
export interface DigitalPreviewContext {
  signal: AbortSignal;
  isCurrent: () => boolean;
  progress?: (done: number, total: number) => void;
}
const LIMIT = 256 * 1024 * 1024;
const JSON_LIMIT = 32 * 1024 * 1024;
function signature(resource: DigitalPreviewResource) {
  return {
    id: resource.id,
    fileIds: [...resource.content.fileIds].sort(),
    packageFiles: resource.content.packageFiles,
    files: [...resource.files].sort((a, b) => a.fileId.localeCompare(b.fileId)),
  };
}
function validate(resource: DigitalPreviewResource, entry: string) {
  const map = resource.content.packageFiles;
  if (!map || !Object.keys(map).length) throw new Error('该历史成果未登记包路径，请先核实原始目录。');
  Object.keys(map).forEach(requirePackagePath);
  if (!Object.hasOwn(map, entry) || !/\.(gltf|glb|json|tileset)$/i.test(entry))
    throw new Error('请选择清单中的 glTF、GLB 或瓦片清单。');
  const ids = new Set(resource.files.map((f) => f.fileId));
  if (
    ids.size !== resource.files.length ||
    ids.size !== new Set(Object.values(map)).size ||
    !isEqual([...ids].sort(), [...resource.content.fileIds].sort()) ||
    Object.values(map).some((id) => !ids.has(id))
  )
    throw new Error('文件编号与包清单不一致，请重新读取成果。');
  let size = 0;
  for (const f of resource.files) {
    if (!Number.isSafeInteger(f.size) || f.size < 0 || !/^[0-9a-f]{64}$/.test(f.sha256))
      throw new Error('文件缺少有效的冻结大小或 SHA-256，不能确认预览内容。');
    size += f.size;
  }
  if (size > LIMIT) throw new Error('成果包超过 256 MiB 浏览器预览限额，请下载原件使用专业工具核对。');
  return map;
}
/** All original bytes are verified before creating loader-readable URLs. */
export async function prepareDigitalPackagePreview(
  projectId: string,
  planId: string,
  expected: DigitalPreviewResource,
  entry: string,
  context: DigitalPreviewContext,
) {
  const urls: string[] = [];
  let disposed = false;
  const dispose = () => {
    if (!disposed) {
      disposed = true;
      urls.splice(0).forEach((url) => URL.revokeObjectURL(url));
    }
  };
  const check = () => {
    if (disposed || context.signal.aborted || !context.isCurrent()) throw new DOMException('预览已取消', 'AbortError');
  };
  const read = async () => {
    check();
    const response: any = await api.detail(projectId, context.signal);
    check();
    const plans = response?.data?.plans?.filter((p: any) => p.id === planId);
    const resources = plans?.length === 1 ? plans[0].resources?.filter((r: any) => r.id === expected.id) : [];
    if (resources?.length !== 1 || !isEqual(signature(resources[0]), signature(expected)))
      throw new Error('成果清单或冻结版本已变化，请关闭并重新读取资料。');
    return resources[0] as DigitalPreviewResource;
  };
  context.signal.addEventListener('abort', dispose, { once: true });
  try {
    validate(expected, entry);
    const current = await read(),
      map = validate(current, entry),
      blobs = new Map<string, Blob>();
    const expectedEntry = current.files.find((f) => f.fileId === map[entry])!;
    if (/\.(gltf|json|tileset)$/i.test(entry) && expectedEntry.size > JSON_LIMIT)
      throw new Error('glTF JSON 超过 32 MiB 解析限额。');
    for (const file of current.files) {
      check();
      context.progress?.(blobs.size, current.files.length);
      const response: any = await api.original(projectId, file.fileId, context.signal);
      check();
      if (
        !(response?.blob instanceof Blob) ||
        response.integrity !== 'sha256-verified' ||
        response.blob.size !== file.size
      )
        throw new Error('原件缺少内容核验结果或大小不一致，不能预览。');
      const bytes = await response.blob.arrayBuffer();
      check();
      const digest = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), (b) =>
        b.toString(16).padStart(2, '0'),
      ).join('');
      check();
      if (digest !== file.sha256) throw new Error('原件内容与本批冻结摘要不一致，不能预览。');
      blobs.set(file.fileId, response.blob);
    }
    await read();
    check();
    const makeUrl = (blob: Blob) => {
      check();
      const url = URL.createObjectURL(blob);
      urls.push(url);
      return url;
    };
    const result = await prepareVerifiedPackageGraph(entry, map, blobs, makeUrl, check);
    check();
    context.progress?.(current.files.length, current.files.length);
    check();
    return {
      ...result,
      dispose: () => {
        context.signal.removeEventListener('abort', dispose);
        dispose();
      },
    };
  } catch (error) {
    context.signal.removeEventListener('abort', dispose);
    dispose();
    throw error;
  }
}
