/**
 * 根据查询项真实排版后的纵向位置，找出第一行之外的查询项。
 */
export function findWrappedQueryFormItems(items: HTMLElement[], tolerance = 1) {
  const visibleItems = items.filter((item) => item.getClientRects().length > 0);
  if (visibleItems.length < 2) return [];

  const firstRowTop = visibleItems[0].offsetTop;
  return visibleItems.filter((item) => item.offsetTop > firstRowTop + tolerance);
}

/**
 * 查询项均在首行时，判断操作区能否在保留视觉间距的前提下紧随其后。
 * 使用固定几何边界，不依赖操作区当前采用内联还是自动边距布局。
 */
export function canPlaceQueryFormOperateInline(
  form: HTMLElement,
  visibleItems: HTMLElement[],
  operate: HTMLElement,
  gap = 12,
  tolerance = 0.5,
) {
  const lastVisibleItem = visibleItems.at(-1);
  if (!lastVisibleItem) return false;

  const formRight = form.getBoundingClientRect().right;
  const lastItemRight = lastVisibleItem.getBoundingClientRect().right;
  const operateWidth = operate.getBoundingClientRect().width;
  return lastItemRight + gap + operateWidth <= formRight + tolerance;
}
