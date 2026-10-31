export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export async function exportRows(rows: Record<string, any>[], name: string) {
  const XLSX = await import('xlsx');
  const worksheet = XLSX.utils.json_to_sheet(rows);
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, worksheet, '数据');
  XLSX.writeFile(book, `${name}.xlsx`);
}
export async function readRows(file: File) {
  if (file.size > 10 * 1024 * 1024) throw new Error('导入文件不能超过10MB');
  const XLSX = await import('xlsx');
  const book = XLSX.read(await file.arrayBuffer(), { type: 'array', cellDates: false });
  const rows = XLSX.utils.sheet_to_json<Record<string, any>>(book.Sheets[book.SheetNames[0]], { raw: false });
  if (rows.length > 5000) throw new Error('每次最多导入5000条');
  return rows;
}
export function publicEntryUrl(path: string, params: Record<string, string>) {
  const base = new URL(window.location.href);
  base.hash = '';
  // Both hash and history deployments retain their configured base through the Vue router at callers.
  return `${path}${path.includes('?') ? '&' : '?'}${new URLSearchParams(params)}`;
}
