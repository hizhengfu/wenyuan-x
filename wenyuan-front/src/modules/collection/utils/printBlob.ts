import type { CollectionPrintFileFormat } from './printFile';

/** 格式检查用于拒绝错误页面，不替代原件摘要或完整文档结构验证。 */
export async function requireCollectionPrintBlob(value: unknown, format: CollectionPrintFileFormat): Promise<Blob> {
  if (!(value instanceof Blob) || !value.size) throw new Error('打印文件为空');
  const mime = value.type.split(';')[0].trim().toLowerCase();
  if (format === 'PDF') {
    if (!['application/pdf', 'application/octet-stream'].includes(mime) || (await value.slice(0, 5).text()) !== '%PDF-')
      throw new Error('未返回有效 PDF 打印文件');
    return mime === 'application/pdf' ? value : new Blob([value], { type: 'application/pdf' });
  }
  const header = new Uint8Array(await value.slice(0, 4).arrayBuffer());
  if (
    !['application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/octet-stream'].includes(
      mime,
    ) ||
    header[0] !== 80 ||
    header[1] !== 75 ||
    header[2] !== 3 ||
    header[3] !== 4
  )
    throw new Error('未返回有效 Word 打印文件');
  return value;
}
