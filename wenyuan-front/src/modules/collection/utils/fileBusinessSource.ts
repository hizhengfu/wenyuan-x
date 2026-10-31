import type { CollectionWordImportRecordVo } from '../typings/collection';

/** Resolve only records returned by the current collection's scoped history API. */
export function fileBusinessSource(
  file: { bizId?: string; fileId?: string },
  imports: CollectionWordImportRecordVo[],
): CollectionWordImportRecordVo | null {
  if (!file.bizId || !file.fileId) return null;
  const matches = imports.filter(source => source.id === file.bizId && source.sourceFileId === file.fileId &&
    ['REGISTRATION', 'DYNAMIC_TRACKING'].includes(source.documentType || ''));
  return matches.length === 1 ? matches[0] : null;
}
export function fileBusinessSourceLabel(source: CollectionWordImportRecordVo) {
  return `${source.documentType === 'REGISTRATION' ? '登记表导入' : '动态跟踪表导入'} · ${source.sourceFileName || '原件未命名'}`;
}
