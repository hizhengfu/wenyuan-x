import type {
  PermissionApiEndpoint,
  PermissionCatalogItem,
  PermissionCatalogModule,
  PermissionCatalogSnapshot,
} from '@/modules/sys/api/permission';

export type PermissionMappingFilter = 'ALL' | 'MAPPED' | 'UNMAPPED' | 'GLOBAL';
export type PermissionUsageFilter = 'ALL' | 'USED' | 'UNUSED';

export interface PermissionCatalogFilter {
  moduleCode?: string;
  keyword?: string;
  mapping: PermissionMappingFilter;
  usage: PermissionUsageFilter;
}

export interface PermissionCatalogTreeItem extends PermissionCatalogItem {
  children?: PermissionCatalogTreeItem[];
}

type PermissionCatalogResponse = Omit<Partial<PermissionCatalogSnapshot>, 'diagnostics' | 'totals'> & {
  diagnostics?: Partial<PermissionCatalogSnapshot['diagnostics']> | null;
  totals?: Partial<PermissionCatalogSnapshot['totals']> | null;
};

function normalizeEndpoint(endpoint: PermissionApiEndpoint): PermissionApiEndpoint {
  return {
    ...endpoint,
    authorityCodes: Array.isArray(endpoint.authorityCodes) ? endpoint.authorityCodes : [],
    roleCodes: Array.isArray(endpoint.roleCodes) ? endpoint.roleCodes : [],
  };
}

function normalizeEndpoints(endpoints?: PermissionApiEndpoint[] | null) {
  return Array.isArray(endpoints) ? endpoints.map(normalizeEndpoint) : [];
}

export function createEmptyPermissionCatalog(): PermissionCatalogSnapshot {
  return {
    modules: [],
    permissions: [],
    unmappedEndpoints: [],
    diagnostics: { codeNotDeclared: [], multiApplicationCodes: [] },
    totals: { permissionCount: 0, usedPermissionCount: 0, endpointCount: 0, unmappedEndpointCount: 0 },
  };
}

export function normalizePermissionCatalog(response?: PermissionCatalogResponse | null): PermissionCatalogSnapshot {
  const fallback = createEmptyPermissionCatalog();
  const permissions = (response?.permissions ?? []).map((permission) => ({
    ...permission,
    applicationIds: Array.isArray(permission.applicationIds) ? permission.applicationIds : [],
    endpoints: normalizeEndpoints(permission.endpoints),
  }));
  return {
    ...fallback,
    ...response,
    modules: response?.modules ?? [],
    permissions,
    unmappedEndpoints: normalizeEndpoints(response?.unmappedEndpoints),
    diagnostics: {
      codeNotDeclared: (response?.diagnostics?.codeNotDeclared ?? []).map((issue) => ({
        ...issue,
        endpoints: normalizeEndpoints(issue.endpoints),
      })),
      multiApplicationCodes: response?.diagnostics?.multiApplicationCodes ?? [],
    },
    totals: {
      ...fallback.totals,
      ...response?.totals,
    },
  };
}

export function isAggregatePermission(permission: Pick<PermissionCatalogItem, 'code'>) {
  return permission.code.trim().toLocaleUpperCase().endsWith('_ALL');
}

export function isGlobalBypassPermission(permission: Pick<PermissionCatalogItem, 'code' | 'globalBypass'>) {
  return permission.globalBypass || permission.code.trim().toLocaleUpperCase() === 'ADMIN';
}

function permissionChildren(permissions: PermissionCatalogItem[]) {
  const childrenByParent = new Map<string, PermissionCatalogItem[]>();
  permissions.forEach((permission) => {
    const parentId = permission.parentId?.trim();
    if (!parentId || parentId === '0') return;
    const children = childrenByParent.get(parentId) ?? [];
    children.push(permission);
    childrenByParent.set(parentId, children);
  });
  return childrenByParent;
}

function collectDescendantIds(childrenByParent: Map<string, PermissionCatalogItem[]>, permissionId: string) {
  const descendantIds: string[] = [];
  const visited = new Set<string>([permissionId]);
  const pending = [...(childrenByParent.get(permissionId) ?? [])];
  while (pending.length) {
    const child = pending.shift()!;
    if (visited.has(child.id)) continue;
    visited.add(child.id);
    descendantIds.push(child.id);
    pending.push(...(childrenByParent.get(child.id) ?? []));
  }
  return descendantIds;
}

export function aggregatePermissionDescendants(permissions: PermissionCatalogItem[]) {
  const childrenByParent = permissionChildren(permissions);
  const descendants = new Map<string, string[]>();
  permissions.filter(isAggregatePermission).forEach((permission) => {
    const ids = collectDescendantIds(childrenByParent, permission.id);
    if (ids.length) descendants.set(permission.id, ids);
  });
  return descendants;
}

export function normalizePermissionSelection(permissions: PermissionCatalogItem[], ids: Iterable<string>) {
  const selected = new Set(ids);
  const permissionById = new Map(permissions.map((permission) => [permission.id, permission]));
  const selectedGlobalIds = [...selected].filter((id) => {
    const permission = permissionById.get(id);
    return permission ? isGlobalBypassPermission(permission) : false;
  });
  if (selectedGlobalIds.length) return new Set(selectedGlobalIds);

  const descendants = aggregatePermissionDescendants(permissions);
  [...selected].forEach((id) => descendants.get(id)?.forEach((descendantId) => selected.delete(descendantId)));
  return selected;
}

export function effectivePermissionSelection(permissions: PermissionCatalogItem[], ids: Iterable<string>) {
  const selected = normalizePermissionSelection(permissions, ids);
  if ([...selected].some((id) => permissions.some((permission) => permission.id === id && isGlobalBypassPermission(permission)))) {
    return new Set(permissions.map((permission) => permission.id));
  }
  const effective = new Set(selected);
  const descendants = aggregatePermissionDescendants(permissions);
  selected.forEach((id) => descendants.get(id)?.forEach((descendantId) => effective.add(descendantId)));
  return effective;
}

export function updatePermissionSelection(
  permissions: PermissionCatalogItem[],
  currentIds: Iterable<string>,
  targetIds: Iterable<string>,
  checked: boolean,
) {
  const selected = normalizePermissionSelection(permissions, currentIds);
  const permissionById = new Map(permissions.map((permission) => [permission.id, permission]));
  const descendants = aggregatePermissionDescendants(permissions);
  const targets = [...new Set(targetIds)].filter((id) => permissionById.has(id));
  const globalIds = new Set(
    permissions.filter(isGlobalBypassPermission).map((permission) => permission.id),
  );

  const removeAggregateAncestors = (targetId: string) => {
    let parentId = permissionById.get(targetId)?.parentId?.trim();
    const visited = new Set<string>();
    while (parentId && parentId !== '0' && !visited.has(parentId)) {
      visited.add(parentId);
      const parent = permissionById.get(parentId);
      if (!parent) break;
      if (isAggregatePermission(parent)) selected.delete(parent.id);
      parentId = parent.parentId?.trim();
    }
  };

  if (checked) {
    const globalTargets = targets.filter((id) => globalIds.has(id));
    if (globalTargets.length) return new Set(globalTargets);

    globalIds.forEach((id) => selected.delete(id));
    const regularTargets = targets.filter((id) => !isAggregatePermission(permissionById.get(id)!));
    const aggregateTargets = targets.filter((id) => isAggregatePermission(permissionById.get(id)!));

    regularTargets.forEach((targetId) => {
      removeAggregateAncestors(targetId);
      selected.add(targetId);
    });
    // 聚合权限最后处理：批量选择同时包含父子项时，父级 ALL 胜出且只保存父级。
    aggregateTargets.reverse().forEach((targetId) => {
      removeAggregateAncestors(targetId);
      selected.add(targetId);
      descendants.get(targetId)?.forEach((descendantId) => selected.delete(descendantId));
    });
    return normalizePermissionSelection(permissions, selected);
  }

  if (targets.length) globalIds.forEach((id) => selected.delete(id));
  for (const targetId of targets) {
    selected.delete(targetId);
    removeAggregateAncestors(targetId);
  }
  return selected;
}

export function buildPermissionCatalogTree(
  permissions: PermissionCatalogItem[],
  visibleIds: ReadonlySet<string> = new Set(permissions.map((permission) => permission.id)),
): PermissionCatalogTreeItem[] {
  const permissionById = new Map(permissions.map((permission) => [permission.id, permission]));
  const includedIds = new Set(visibleIds);

  visibleIds.forEach((id) => {
    let parentId = permissionById.get(id)?.parentId?.trim();
    const visited = new Set<string>();
    while (parentId && parentId !== '0' && !visited.has(parentId)) {
      visited.add(parentId);
      const parent = permissionById.get(parentId);
      if (!parent) break;
      includedIds.add(parent.id);
      parentId = parent.parentId?.trim();
    }
  });

  const rowById = new Map<string, PermissionCatalogTreeItem>();
  permissions.forEach((permission) => {
    if (includedIds.has(permission.id)) rowById.set(permission.id, { ...permission, children: [] });
  });

  const roots: PermissionCatalogTreeItem[] = [];
  permissions.forEach((permission) => {
    const row = rowById.get(permission.id);
    if (!row) return;
    const parent = permission.parentId ? rowById.get(permission.parentId) : undefined;
    if (parent) parent.children!.push(row);
    else roots.push(row);
  });

  rowById.forEach((row) => {
    if (!row.children?.length) delete row.children;
  });
  return roots;
}

export function sameStringSet(left: Set<string>, right: Set<string>) {
  return left.size === right.size && [...left].every((id) => right.has(id));
}

export function filterPermissionModules(modules: PermissionCatalogModule[], keyword?: string) {
  const tokens = (keyword || '').trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  if (!tokens.length) return modules;
  return modules.filter((module) => {
    const searchText = `${module.label} ${module.code}`.toLocaleLowerCase();
    return tokens.every((token) => searchText.includes(token));
  });
}

const endpointSearchTextCache = new WeakMap<PermissionApiEndpoint, string>();

export function endpointSearchText(endpoint: PermissionApiEndpoint) {
  const cached = endpointSearchTextCache.get(endpoint);
  if (cached !== undefined) return cached;
  const searchText = [
    endpoint.httpMethod,
    endpoint.path,
    endpoint.controller,
    endpoint.handler,
    endpoint.summary,
    endpoint.rawExpression,
    ...(endpoint.authorityCodes ?? []),
    ...(endpoint.roleCodes ?? []),
  ]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase();
  endpointSearchTextCache.set(endpoint, searchText);
  return searchText;
}

export function filterPermissionEndpoints(
  endpoints: PermissionApiEndpoint[],
  keyword?: string,
  httpMethod?: string,
) {
  const tokens = (keyword || '').trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  const normalizedMethod = httpMethod?.trim().toLocaleUpperCase();
  return endpoints.filter((endpoint) => {
    if (normalizedMethod && normalizedMethod !== 'ALL' && endpoint.httpMethod.toLocaleUpperCase() !== normalizedMethod) {
      return false;
    }
    if (!tokens.length) return true;
    const searchText = endpointSearchText(endpoint);
    return tokens.every((token) => searchText.includes(token));
  });
}

const permissionSearchTextCache = new WeakMap<PermissionCatalogItem, string>();

export function permissionSearchText(permission: PermissionCatalogItem) {
  const cached = permissionSearchTextCache.get(permission);
  if (cached !== undefined) return cached;
  const endpointText = permission.endpoints.map(endpointSearchText).join(' ');

  const searchText = [
    permission.name,
    permission.code,
    permission.moduleCode,
    permission.moduleLabel,
    permission.source,
    endpointText,
  ]
    .filter(Boolean)
    .join(' ')
    .toLocaleLowerCase();
  permissionSearchTextCache.set(permission, searchText);
  return searchText;
}

export function filterPermissionCatalog(permissions: PermissionCatalogItem[], filter: PermissionCatalogFilter) {
  const tokens = (filter.keyword || '').trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);

  return permissions.filter((permission) => {
    if (filter.moduleCode && permission.moduleCode !== filter.moduleCode) return false;
    if (filter.mapping === 'MAPPED' && permission.endpointCount === 0) return false;
    if (filter.mapping === 'UNMAPPED' && permission.endpointCount !== 0) return false;
    if (filter.mapping === 'GLOBAL' && !permission.globalBypass) return false;
    if (filter.usage === 'USED' && !permission.used) return false;
    if (filter.usage === 'UNUSED' && permission.used) return false;
    if (!tokens.length) return true;

    const searchText = permissionSearchText(permission);
    return tokens.every((token) => searchText.includes(token));
  });
}
