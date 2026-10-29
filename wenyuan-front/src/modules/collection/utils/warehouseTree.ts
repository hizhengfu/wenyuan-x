import type { WarehouseVo } from '../typings/collection';

export interface WarehouseTreeNode extends WarehouseVo {
  [key: string]: unknown;
  id: string;
  name: string;
  children?: WarehouseTreeNode[];
}

function compareWarehouse(left: WarehouseVo, right: WarehouseVo) {
  const sortDiff = Number(left.sort || 0) - Number(right.sort || 0);
  if (sortDiff !== 0) return sortDiff;
  return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN');
}

export function extractWarehouseRows(response: any): WarehouseVo[] {
  const payload = response?.data?.data ?? response?.data ?? response;
  const rows = payload?.list ?? payload;
  return Array.isArray(rows) ? rows : [];
}

export function buildWarehouseTree(rows: WarehouseVo[], excludedId?: string | null): WarehouseTreeNode[] {
  const nodes = rows
    .filter((row): row is WarehouseVo & { id: string; name: string } => Boolean(row.id && row.name))
    .map((row) => ({ ...row, children: [] as WarehouseTreeNode[] }));
  const nodeMap = new Map(nodes.map((node) => [node.id, node]));
  const roots: WarehouseTreeNode[] = [];

  nodes.forEach((node) => {
    const parent = node.parentId ? nodeMap.get(node.parentId) : undefined;
    if (parent) parent.children?.push(node);
    else roots.push(node);
  });

  function pruneAndSort(items: WarehouseTreeNode[]): WarehouseTreeNode[] {
    return items
      .filter((item) => item.id !== excludedId)
      .sort(compareWarehouse)
      .map((item) => {
        const children = pruneAndSort(item.children || []);
        return children.length ? { ...item, children } : { ...item, children: undefined };
      });
  }

  return pruneAndSort(roots);
}

/** 取得指定库位及其全部下级节点，用于选择上级库房时按整棵子树筛选。 */
export function collectWarehouseSubtreeIds(rows: WarehouseVo[], rootId: string): Set<string> {
  const childrenByParent = new Map<string, string[]>();
  rows.forEach((row) => {
    if (!row.id || !row.parentId) return;
    const children = childrenByParent.get(row.parentId) || [];
    children.push(row.id);
    childrenByParent.set(row.parentId, children);
  });

  const result = new Set<string>();
  const pending = [rootId];
  while (pending.length) {
    const id = pending.pop()!;
    if (result.has(id)) continue;
    result.add(id);
    pending.push(...(childrenByParent.get(id) || []));
  }
  return result;
}

/** 树表筛选时保留命中节点的祖先，避免子库位失去层级路径。 */
export function filterWarehouseRows(rows: WarehouseVo[], predicate: (row: WarehouseVo) => boolean): WarehouseVo[] {
  const byId = new Map(rows.map((row) => [row.id, row]));
  const visibleIds = new Set<string>();

  rows.filter(predicate).forEach((row) => {
    let current: WarehouseVo | undefined = row;
    const visited = new Set<string>();
    while (current?.id && !visited.has(current.id)) {
      visibleIds.add(current.id);
      visited.add(current.id);
      current = current.parentId ? byId.get(current.parentId) : undefined;
    }
  });

  return rows.filter((row) => Boolean(row.id && visibleIds.has(row.id)));
}
