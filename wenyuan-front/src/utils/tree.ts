import { get, isArray } from 'lodash-es';

/**
 * 树转换配置参数
 * */
interface TreeConfig {
  idKey?: string;
  pIdKey?: string;
  childKey?: string;
}

/**
 * 数组转换到树状结构
 * */
export function transformToTreeFormat(
  source: Array<any> | any,
  config: TreeConfig = {
    idKey: 'id',
    pIdKey: 'pId',
    childKey: 'children',
  },
) {
  if (!source) return [];
  let i;
  let l;

  const key = config.idKey || 'id';

  const parentKey = config.pIdKey || 'pId';

  const childKey = config.childKey || 'children';

  if (!key || key === '' || !source) return [];
  // let arr = cloneDeep(source);
  const arr = source;
  if (isArray(arr)) {
    const r = [];
    const tmpMap = [];
    for (i = 0, l = arr.length; i < l; i++) {
      tmpMap[arr[i][key]] = arr[i];
      //防止重复渲染
      if (!arr[i].__extra) {
        arr[i].__extra = {
          level: 0,
          extend: false,
        };
      }
    }
    for (i = 0, l = arr.length; i < l; i++) {
      const parentId = get(arr[i], parentKey);
      if (tmpMap[parentId] && arr[i][key] !== parentId) {
        if (!tmpMap[parentId][childKey]) {
          tmpMap[parentId][childKey] = [];
        }
        //防止重复添加到对象中，部分环境下数据有缓存，原始数据可能存在被修改
        if (!tmpMap[get(arr[i], parentKey)][childKey].includes(arr[i])) {
          //修正数据中存在isLeaf时无效的叶子节点判断
          if (tmpMap[get(arr[i], parentKey)]['isLeaf']) {
            tmpMap[get(arr[i], parentKey)]['isLeaf'] = false;
          }
          tmpMap[get(arr[i], parentKey)][childKey].push(arr[i]);
          if (tmpMap[get(arr[i], parentKey)][childKey].length > 0) {
            tmpMap[get(arr[i], parentKey)]['isLeaf'] = false;
          }
        }
      } else {
        r.push(arr[i]);
      }
    }
    return r;
  } else {
    return [arr];
  }
}

/**
 * 树形结构转换为数组，顺序一致
 * */
export function transformToArrayFormat(
  arr: any[] | any = [],
  config: TreeConfig = {
    idKey: 'id',
    pIdKey: 'pId',
    childKey: 'children',
  },
  level: number = 0,
) {
  const childKey = config.childKey || 'children';

  let r: any[] = [];
  if (isArray(arr)) {
    for (let i = 0, l = arr.length; i < l; i++) {
      arr[i].__extra = { level: level };
      r.push(arr[i]);
      if (arr[i][childKey]) {
        arr[i].__extra.expanded = true;
        r = r.concat(transformToArrayFormat(arr[i][childKey], config, level + 1));
      }
    }
  } else {
    arr.__extra = { level: level };
    r.push(arr);
    if (arr[childKey]) {
      arr.__extra.expanded = true;
      r = r.concat(transformToArrayFormat(arr[childKey], config, level + 1));
    }
  }
  return r;
}

/**
 * 从树形结构中获取所有父节点
 * */
export function getAllParentNodeByParam(
  nodes: Array<any> | undefined,
  value: any,
  key?: string,
  config?: {
    childKey: 'children';
  },
): Array<any> | null {
  if (!nodes || !value) return null;
  const idKey = key || 'id';
  const childKey = (config && config.childKey) || 'children';

  const l = nodes.length;
  for (let i = 0; i < l; i++) {
    const node = nodes[i];
    // @ts-ignore
    if (node[idKey] === value) {
      return [node];
    }
    const children = node[childKey];
    const tmp = getAllParentNodeByParam(children, value, idKey, config);
    if (tmp) {
      tmp.unshift(node);
      return tmp;
    }
  }
  return null;
}

/**
 * 从树形结构中找到对应的节点
 * */
export function getNodeByParam(
  nodes: Array<any> | null | undefined,
  value?: string,
  key = 'id',
  config: any = {},
): any | null {
  if (!nodes || !value) {
    return null;
  }
  const childKey = config.childKey || 'children';
  const l = nodes.length;
  for (let i = 0; i < l; i++) {
    const node = nodes[i];
    if (node[key] === value) {
      return node;
    }
    const children = node[childKey];
    const tmp = getNodeByParam(children, value, key, config);
    if (tmp) {
      return tmp;
    }
  }
  return null;
}

/**
 * 计算有多少叶子节点数量
 * */
export function sumNodeLeafLength(node: any, childKey: string = 'children'): number {
  if (isArray(node)) {
    return node.reduce((sum, child) => sum + sumNodeLeafLength(child, childKey), 0);
  } else {
    if (!node[childKey] || node[childKey].length === 0) {
      return 1;
    }
  }
  return node[childKey].reduce((sum: number, child: any) => sum + sumNodeLeafLength(child, childKey), 0);
}
/**
 * 递归计算某节点（或节点数组）所有子节点的 count 总和
 * @param node 节点或节点数组
 * @param childKey 子节点字段名，默认 'children'
 * @param countKey count 字段名，默认 'count'
 */
export function sumNodeCount(node: any, childKey: string = 'children', countKey: string = 'count'): number {
  // node 是数组 → 每个元素累加
  if (Array.isArray(node)) {
    return node.reduce((sum, child) => sum + sumNodeCount(child, childKey, countKey), 0);
  }

  // 当前节点自身的 count
  let total = node[countKey] ?? 0;

  const children = node[childKey];
  if (!children || children.length === 0) {
    return total;
  }

  // 累加子节点结果
  return total + children.reduce((sum: number, child: any) => sum + sumNodeCount(child, childKey, countKey), 0);
}
