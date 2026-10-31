export interface CollectionNavigationMenu {
  id: string;
  name: string;
  path?: string;
  children?: CollectionNavigationMenu[];
}

/** Links use the current server menu, retaining configured parameters and the page identity. */
export function collectionMenuTarget(menus: CollectionNavigationMenu[], requested: string) {
  const [path, search = ''] = requested.split('?');
  function find(nodes: CollectionNavigationMenu[]): CollectionNavigationMenu | undefined {
    for (const menu of nodes) {
      if (!menu.children?.length && menu.path?.split('?')[0] === path) return menu;
      const nested = find(menu.children || []);
      if (nested) return nested;
    }
  }
  const menu = find(menus);
  if (!menu?.path) return null;
  const [pathname, configured = ''] = menu.path.split('?');
  return { path: pathname, query: {
    ...Object.fromEntries(new URLSearchParams(configured)),
    ...Object.fromEntries(new URLSearchParams(search)),
    menuId: menu.id, title: menu.name,
  } };
}

export const collectionEntryActions = [
  { key: 'rules', label: '制度与备案', note: '制度版本、报送凭据和备案回执', permissions: 'COLLECTION_ADMIN,COLLECTION_ORG_MANAGE' },
  { key: 'paper', label: '纸账与财务核对', note: '纸账保管、移交及财务核对', permissions: 'COLLECTION_ADMIN,COLLECTION_LEDGER_CREATE,COLLECTION_LEDGER_CORRECT,COLLECTION_LEDGER_FINANCE' },
  { key: 'care', label: '日常检查与养护', note: '选择藏品，登记检查与养护原件', permissions: 'COLLECTION_ADMIN,COLLECTION_CARE_MANAGE' },
  { key: 'staffing', label: '库管任职与交接', note: '实际任职、岗位分离和离任交接', permissions: 'COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE', path: '/collection/warehouse?entryAction=staffing' },
  { key: 'inventory', label: '清点与离任移交', note: '逐件清点、负责人离任移交和年度报送', permissions: 'COLLECTION_ADMIN,COLLECTION_STORAGE', path: '/collection/inventory?entryAction=compliance' },
  { key: 'grading', label: '行政定级与备案', note: '省级定级申请、结果及国家备案原件', permissions: 'COLLECTION_ADMIN,COLLECTION_APPRAISE', path: '/collection/appraisal?entryAction=grading' },
] as const;
export type CollectionEntryAction = typeof collectionEntryActions[number];

export interface CollectionBusinessContext {
  orgs: { value: string; label: string }[];
  defaultOrgId: string | null;
}
export function readCollectionBusinessContext(value: unknown): CollectionBusinessContext {
  const data = value as CollectionBusinessContext;
  if (!data || !Array.isArray(data.orgs) || data.orgs.some(org => !org ||
    typeof org.value !== 'string' || !org.value || typeof org.label !== 'string' || !org.label) ||
    new Set(data.orgs.map(org => org.value)).size !== data.orgs.length ||
    (data.defaultOrgId !== null && (typeof data.defaultOrgId !== 'string' ||
      !data.orgs.some(org => org.value === data.defaultOrgId)))) throw new Error('办理馆别读取不完整，请重试');
  return { orgs: data.orgs.map(org => ({ ...org })), defaultOrgId: data.defaultOrgId };
}
