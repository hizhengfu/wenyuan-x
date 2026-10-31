import { describe, expect, it } from 'vitest';

import { canPlaceQueryFormOperateInline, findWrappedQueryFormItems } from '../advanceQueryFormLayout';

function createItem(offsetTop: number, visible = true) {
  return {
    offsetTop,
    getClientRects: () => ({ length: visible ? 1 : 0 }),
  } as HTMLElement;
}

function createBox(right: number, width: number) {
  return {
    getBoundingClientRect: () => ({ right, width }),
  } as HTMLElement;
}

describe('AdvanceQueryForm layout', () => {
  it('finds items placed below the first visual row', () => {
    const first = createItem(12);
    const second = createItem(12);
    const third = createItem(61);
    const fourth = createItem(61);

    expect(findWrappedQueryFormItems([first, second, third, fourth])).toEqual([third, fourth]);
  });

  it('ignores hidden items and uses the first visible item as the row baseline', () => {
    const hidden = createItem(0, false);
    const firstVisible = createItem(20);
    const sameRow = createItem(21);
    const wrapped = createItem(70);

    expect(findWrappedQueryFormItems([hidden, firstVisible, sameRow, wrapped])).toEqual([wrapped]);
  });

  it('does not report overflow when fewer than two items are visible', () => {
    expect(findWrappedQueryFormItems([createItem(0, false), createItem(20)])).toEqual([]);
  });

  it('places operations inline only when the fixed 12px gap fits', () => {
    const form = createBox(960, 960);
    const operate = createBox(960, 100);

    expect(canPlaceQueryFormOperateInline(form, [createBox(848, 200)], operate)).toBe(true);
    expect(canPlaceQueryFormOperateInline(form, [createBox(849, 200)], operate)).toBe(false);
  });
});
