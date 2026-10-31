export interface HistoryWindow<T> {
  list: T[];
  nextCursor?: string | null;
}

interface PageRequest {
  pageNum?: number;
  pageSize?: number;
}

/** Sequential history pagination without a full-table COUNT or deep OFFSET. */
export class HistoryCursorPager<T> {
  private filterKey = '';
  private cursors = new Map<number, string | null>([[1, null]]);
  private generation = 0;

  reset() {
    this.filterKey = '';
    this.cursors = new Map([[1, null]]);
    this.generation += 1;
  }

  async fetch(
    page: PageRequest,
    filters: Record<string, unknown>,
    request: (params: Record<string, unknown>) => Promise<HistoryWindow<T>>,
  ) {
    const pageNum = Math.max(1, Math.trunc(Number(page.pageNum) || 1));
    const pageSize = Math.max(1, Math.trunc(Number(page.pageSize) || 30));
    const filterKey = JSON.stringify({ filters, pageSize });
    if (pageNum === 1 || filterKey !== this.filterKey) {
      this.filterKey = filterKey;
      this.cursors = new Map([[1, null]]);
      this.generation += 1;
    }
    const cursor = this.cursors.get(pageNum);
    if (pageNum > 1 && cursor === undefined) throw new Error('请从第一页依次浏览历史记录');
    const generation = this.generation;
    const result = await request({ ...filters, cursor: cursor || undefined, limit: pageSize });
    if (generation !== this.generation) throw new Error('历史查询已更新');
    if (!Array.isArray(result?.list)) throw new Error('历史记录数据格式异常');
    this.cursors.delete(pageNum + 1);
    if (result.nextCursor) this.cursors.set(pageNum + 1, result.nextCursor);
    const total = (pageNum - 1) * pageSize + result.list.length + (result.nextCursor ? 1 : 0);
    return {
      data: {
        list: result.list,
        total,
        pages: pageNum + (result.nextCursor ? 1 : 0),
        extend: '按时间倒序浏览 · 总数未计算',
      },
    };
  }
}
