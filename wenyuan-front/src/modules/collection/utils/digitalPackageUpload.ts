import request from '@/utils/request';

export const DIGITAL_CHUNK_SIZE = 2 * 1024 * 1024;
export interface DigitalPackageResult {
  fileIds: string[];
  packageFiles: Record<string, string>;
}
export interface PackageUploadContext {
  signal: AbortSignal;
  isCurrent: () => boolean;
  progress?: (completed: number, total: number, path: string) => void;
}
interface UploadResponse { status: unknown; data?: any }

/** Validate the complete selection before creating any server upload sessions. */
export function digitalPackageEntries(files: readonly File[]) {
  if (!files.length) throw new Error('请选择实际成果文件。');
  const paths = new Set<string>();
  return files.map(file => {
    const path = file.webkitRelativePath || file.name;
    if (!path.trim() || path.startsWith('/') || /[\\:\x00-\x1f\x7f]/.test(path) ||
      path.split('/').some(part => !part || part === '.' || part === '..'))
      throw new Error('模型包须使用不含空段、点段或盘符的相对文件路径。');
    if (paths.has(path)) throw new Error(`文件路径重复：${path}`);
    paths.add(path);
    return { path, file };
  });
}

/** Upload first, then return one complete manifest; callers never receive a partial map. */
export async function uploadDigitalPackage(files: readonly File[], context: PackageUploadContext): Promise<DigitalPackageResult> {
  const entries = digitalPackageEntries(files);
  const mapped: [string, string][] = [];
  const check = () => {
    if (context.signal.aborted || !context.isCurrent()) throw new DOMException('导入已取消', 'AbortError');
  };
  const post = async (url: '/file/upload' | '/file/chunk', body: FormData) => {
    check();
    const response = await request.post<any, UploadResponse>(url, body, {
      signal: context.signal, timeout: 0, adapter: 'fetch',
      headers: { 'Content-Type': 'multipart/form-data' },
      fetchOptions: { cache: 'no-store', redirect: 'error' },
    });
    check();
    const accepted = url === '/file/chunk' ? response?.status === 'success' :
      response?.status === 200 || response?.status === 'OK';
    if (!accepted) throw new Error('上传未成功，请核对文件及当前授权后重试。');
    return response.data;
  };
  const phase = (name: string, session?: string) => {
    const body = new FormData();
    body.append('phase', name);
    if (session) body.append('session_id', session);
    return body;
  };
  for (const { file, path } of entries) {
    check();
    context.progress?.(mapped.length, entries.length, path);
    let stored: any;
    if (file.size <= DIGITAL_CHUNK_SIZE) {
      const body = new FormData();
      body.append('file', file, file.name);
      body.append('module', 'collection');
      stored = await post('/file/upload', body);
    } else {
      const start = await post('/file/chunk', phase('start'));
      if (typeof start?.session_id !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(start.session_id) ||
        start.end_offset !== DIGITAL_CHUNK_SIZE)
        throw new Error('分片上传会话无效，请重新选择文件。');
      for (let offset = 0; offset < file.size; offset += DIGITAL_CHUNK_SIZE) {
        const body = phase('upload', start.session_id);
        body.append('start_offset', String(offset));
        body.append('chunk', file.slice(offset, Math.min(offset + DIGITAL_CHUNK_SIZE, file.size)), file.name);
        await post('/file/chunk', body);
      }
      const body = phase('finish', start.session_id);
      body.append('name', file.name);
      body.append('size', String(file.size));
      body.append('module', 'collection');
      stored = await post('/file/chunk', body);
    }
    if (typeof stored?.id !== 'string' || !stored.id.trim() || /[,，;\n]/.test(stored.id) ||
      (stored.size != null && Number(stored.size) !== file.size))
      throw new Error('上传结果的文件编号或大小无效，本次清单未写入表单。');
    mapped.push([path, stored.id]);
  }
  check();
  context.progress?.(entries.length, entries.length, '');
  check();
  return { fileIds: [...new Set(mapped.map(([, id]) => id))], packageFiles: Object.fromEntries(mapped) };
}
