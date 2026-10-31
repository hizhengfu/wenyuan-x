import { describe, expect, it, vi } from 'vitest';
import { HistoryCursorPager } from '../historyCursorPager';

describe('history cursor pager', () => {
  it('uses each next cursor and resets it when filters change', async () => {
    const pager = new HistoryCursorPager<number>();
    const request = vi
      .fn()
      .mockResolvedValueOnce({ list: [3, 2], nextCursor: 'cursor-2' })
      .mockResolvedValueOnce({ list: [1], nextCursor: null })
      .mockResolvedValueOnce({ list: [9], nextCursor: null });
    const first = await pager.fetch({ pageNum: 1, pageSize: 2 }, { key: 'TEMP' }, request);
    expect(first.data.pages).toBe(2);
    expect(first.data.total).toBe(3);
    const second = await pager.fetch({ pageNum: 2, pageSize: 2 }, { key: 'TEMP' }, request);
    expect(second.data.list).toEqual([1]);
    expect(second.data.pages).toBe(2);
    expect(request.mock.calls[1][0].cursor).toBe('cursor-2');
    await pager.fetch({ pageNum: 1, pageSize: 2 }, { key: 'HUMIDITY' }, request);
    expect(request.mock.calls[2][0].cursor).toBeUndefined();
    await expect(pager.fetch({ pageNum: 2, pageSize: 2 }, { key: 'HUMIDITY' }, request)).rejects.toThrow('依次浏览');
  });

  it('does not accept an older response after a new query', async () => {
    const pager = new HistoryCursorPager<number>();
    let resolveOld!: (value: { list: number[]; nextCursor: string }) => void;
    const old = pager.fetch(
      { pageNum: 1 },
      { key: 'TEMP' },
      () =>
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
    );
    await pager.fetch({ pageNum: 1 }, { key: 'HUMIDITY' }, async () => ({ list: [5], nextCursor: null }));
    resolveOld({ list: [4], nextCursor: 'stale' });
    await expect(old).rejects.toThrow('已更新');
  });
});
