export const GROUP_SELECT_FOLDER_NODE_KEY = '__groupSelectFolderNode';

function cloneGroupSelectNode(node) {
  const hasChildren = Array.isArray(node?.children);
  const children = hasChildren ? node.children.map(cloneGroupSelectNode) : node?.children;
  const isFolder = Array.isArray(children) && children.length > 0;

  return {
    ...node,
    ...(hasChildren ? { children } : {}),
    [GROUP_SELECT_FOLDER_NODE_KEY]: isFolder,
  };
}

export function isGroupSelectFolderNode(option) {
  return Boolean(option?.[GROUP_SELECT_FOLDER_NODE_KEY]);
}

export function normalizeGroupSelectOptions(options = [], config = {}) {
  const nextOptions = Array.isArray(options) ? options : [];
  const singleRootLeafOnly = Boolean(config?.singleRootLeafOnly);
  const singleRootDefaultExpand = Boolean(config?.singleRootDefaultExpand || singleRootLeafOnly);

  if (!singleRootDefaultExpand || nextOptions.length !== 1) {
    return {
      options: nextOptions,
      defaultExpandedKeys: [],
      expandedKeys: undefined,
      singleRootLeafOnlyActive: false,
    };
  }

  const rootKey = nextOptions[0]?.id;
  const expandedKeys = rootKey === undefined || rootKey === null ? [] : [rootKey];

  return {
    options: singleRootLeafOnly ? nextOptions.map(cloneGroupSelectNode) : nextOptions,
    defaultExpandedKeys: expandedKeys,
    expandedKeys,
    singleRootLeafOnlyActive: singleRootLeafOnly,
  };
}
