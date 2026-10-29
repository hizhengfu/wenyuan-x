/** Frozen business metadata is display-only; it never supplies a content-read route. */
export interface HFileListFile {
  fileId?: string;
  id?: string;
  metadata?: string | Record<string, unknown>;
  name?: string;
  path?: string;
  type?: string;
  ext?: string;
}
export interface DisplayAttachment {
  id: string;
  name: string;
  path: string;
  type?: string;
  ext: string;
  url: string;
  status: string;
  createTime: string;
  originalUrl?: string;
}
function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}
function metadata(value: HFileListFile['metadata']): Record<string, unknown> {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}
export function normalizeDisplayAttachment(file: HFileListFile, index: number): DisplayAttachment {
  const frozen = metadata(file.metadata);
  const id = text(file.fileId) || text(file.id) || text(frozen.id);
  const path = text(frozen.path) || text(file.path);
  const name = text(frozen.name) || text(file.name) || `附件 ${index + 1}`;
  const ext = (text(frozen.ext) || text(file.ext)).replace(/^\./, '').toLowerCase();
  return { id, name, path, ext, type: text(frozen.type) || text(file.type), url: id || path,
    status: 'finished', createTime: '' };
}
export function fileReferences(value: string | readonly string[] | null | undefined): string[] {
  return [...new Set((Array.isArray(value) ? value : [value]).flatMap((part) =>
    typeof part === 'string' ? part.split(/[,，;；\r\n]/).map((id) => id.trim()).filter(Boolean) : []))];
}
export function attachmentIsImage(file: Pick<DisplayAttachment, 'type' | 'ext' | 'name' | 'url'>): boolean {
  const image = /^(jpg|jpeg|png|gif|webp|bmp)$/i;
  return /^image\//i.test(file.type || '') || image.test(file.ext) ||
    image.test(file.name.split(/[?#]/)[0].split('.').pop() || '') ||
    image.test(file.url.split(/[?#]/)[0].split('.').pop() || '');
}
