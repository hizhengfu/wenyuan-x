/** 藏品影像：视频识别与影像目录层级（台账「影像与附件」使用）。 */

export type MediaLike = { mediaType?: string | null };
export type MediaFolderLike = { id?: string | null; parentId?: string | null; name?: string | null; sort?: number | null };

/** 影像类型字典 col_media_type 里的视频（后端 CollectionInstall：VIDEO「视频」）。视频不能当图片加载缩略图。 */
export function isVideoMedia(item: MediaLike | null | undefined) {
  return String(item?.mediaType ?? '').trim().toUpperCase() === 'VIDEO';
}

export type MediaFolderTreeNode<T extends MediaFolderLike = MediaFolderLike> = T & {
  key: string;
  label: string;
  depth: number;
  children?: MediaFolderTreeNode<T>[];
};

function folderSort(a: MediaFolderLike, b: MediaFolderLike) {
  return (a.sort ?? 0) - (b.sort ?? 0) || String(a.name ?? '').localeCompare(String(b.name ?? ''), 'zh-CN');
}

/**
 * 按 parentId 组装目录树：上级不存在（已删或不在本藏品）时挂到顶层；有环时断在已访问节点，保证每个目录只出现一次。
 * 叶子节点不带 children 字段，NTreeSelect / NDataTable 不会显示展开箭头。
 */
export function buildMediaFolderTree<T extends MediaFolderLike>(folders: T[] | null | undefined): MediaFolderTreeNode<T>[] {
  const list = (folders || []).filter((item) => item && String(item.id ?? '').trim());
  const ids = new Set(list.map((item) => String(item.id)));
  const byParent = new Map<string, T[]>();
  const roots: T[] = [];
  for (const item of list) {
    const parent = String(item.parentId ?? '').trim();
    if (parent && parent !== String(item.id) && ids.has(parent)) {
      if (!byParent.has(parent)) byParent.set(parent, []);
      byParent.get(parent)!.push(item);
    } else roots.push(item);
  }
  const visited = new Set<string>();
  const build = (item: T, depth: number): MediaFolderTreeNode<T> => {
    const id = String(item.id);
    visited.add(id);
    const children = (byParent.get(id) || [])
      .filter((child) => !visited.has(String(child.id)))
      .sort(folderSort)
      .map((child) => build(child, depth + 1));
    const node = { ...item, key: id, label: String(item.name ?? '').trim() || id, depth } as MediaFolderTreeNode<T>;
    if (children.length) node.children = children;
    return node;
  };
  const tree = roots.sort(folderSort).map((item) => build(item, 0));
  // 只在环里互相引用、没有顶层入口的目录，补到顶层，避免在界面上消失。
  for (const item of list.slice().sort(folderSort)) if (!visited.has(String(item.id))) tree.push(build(item, 0));
  return tree;
}

/** 目录全路径，如「测试-c206-影像目录 / 测试-c206-子目录」，用于表格「目录」列。 */
export function mediaFolderPath(folders: MediaFolderLike[] | null | undefined, folderId: string | null | undefined) {
  const byId = new Map((folders || []).filter((f) => f?.id).map((f) => [String(f.id), f]));
  const names: string[] = [];
  const seen = new Set<string>();
  let cursor = folderId ? byId.get(String(folderId)) : undefined;
  while (cursor && !seen.has(String(cursor.id))) {
    seen.add(String(cursor.id));
    names.unshift(String(cursor.name ?? '').trim() || String(cursor.id));
    cursor = cursor.parentId ? byId.get(String(cursor.parentId)) : undefined;
  }
  return names.join(' / ');
}

/** 收集树中所有有子节点的 key，用于新建子目录后保持父行展开。 */
export function mediaFolderExpandableKeys(
  nodes: Array<{ key?: string; children?: any[] }> | null | undefined,
): string[] {
  const keys: string[] = [];
  const walk = (list: Array<{ key?: string; children?: any[] }> | undefined) => {
    for (const n of list || []) {
      if (n?.children?.length) {
        if (n.key != null && n.key !== '') keys.push(String(n.key));
        walk(n.children);
      }
    }
  };
  walk(nodes || []);
  return keys;
}
