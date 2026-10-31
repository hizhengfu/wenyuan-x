/** Display metadata belongs to the selected archive; it never grants access to file content. */
export interface CollectionReadonlyFile {
  fileId?: string;
  id?: string;
  metadata?: string | Record<string, unknown>;
  name?: string;
  path?: string;
  type?: string;
  ext?: string;
}
export function readonlyFileIds(value: unknown): string[] {
  return [...new Set((Array.isArray(value) ? value : [value]).flatMap((item) =>
    typeof item === 'string' ? item.split(/[,，;；\r\n]/).map((part) => part.trim()).filter(Boolean) : []))];
}
export function readonlyFiles(value: unknown, archiveFiles: CollectionReadonlyFile[] = []): CollectionReadonlyFile[] {
  return readonlyFileIds(value).map((fileId) => archiveFiles.find((file) => (file.fileId || file.id) === fileId) || { fileId });
}
