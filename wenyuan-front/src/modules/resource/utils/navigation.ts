export interface ResourceNavigationMenu {
  id: string;
  name: string;
  path?: string;
  children?: ResourceNavigationMenu[];
}

/** Links use the current server menu, retaining configured parameters and the page identity. */
export function resourceMenuTarget(menus: ResourceNavigationMenu[], requested: string) {
  const [path, search = ''] = requested.split('?');
  function find(nodes: ResourceNavigationMenu[]): ResourceNavigationMenu | undefined {
    for (const menu of nodes) {
      if (!menu.children?.length && menu.path?.split('?')[0] === path) return menu;
      const nested = find(menu.children || []);
      if (nested) return nested;
    }
  }
  const menu = find(menus);
  if (!menu?.path) return null;
  const [pathname, configured = ''] = menu.path.split('?');
  return {
    path: pathname,
    query: {
      ...Object.fromEntries(new URLSearchParams(configured)),
      ...Object.fromEntries(new URLSearchParams(search)),
      menuId: menu.id,
      title: menu.name,
    },
  };
}
