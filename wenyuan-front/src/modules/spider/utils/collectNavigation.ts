export type CollectMethod = 'links' | 'automatic';

export const collectRoute = (method: CollectMethod) => ({
  path: '/spider/wechatCollect',
  query: { menuId: '50003', title: '公众号采集', method },
});

interface Menu {
  path?: string;
  enabled?: boolean;
  children?: Menu[];
}
/** 仅供前后端分批更新时识别旧版能力；新后端以实际装配结果为准。 */
export function hasLegacyAutomaticMenu(menus: Menu[]): boolean {
  return menus.some(
    (menu) =>
      menu.enabled !== false &&
      (menu.path?.split('?')[0] === '/spider/collect' || hasLegacyAutomaticMenu(menu.children ?? [])),
  );
}
