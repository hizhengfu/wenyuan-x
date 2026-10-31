export function resourceTablePage<T>(rows: T[], params: { pageNum?: number; pageSize?: number }) {
  const page = Math.max(1, Number(params.pageNum) || 1);
  const size = Math.max(1, Number(params.pageSize) || 20);
  const start = (page - 1) * size;
  return { data: { list: rows.slice(start, start + size), total: rows.length, pages: Math.ceil(rows.length / size), startRow: start } };
}
