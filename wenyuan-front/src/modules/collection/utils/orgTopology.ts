import type { CollectionOrgOverviewNode, CollectionOrgOverviewStats } from '../typings/collection';
import { buildCollectionStatusMetrics } from './workbenchMetrics';

export const ORG_TOPOLOGY_ROOT_ID = '__collection_org_root__';

export interface OrgTopologyNodeStats {
  id: string;
  code: string;
  depth: number;
  siblingCount: number;
  directTotal: number;
  aggregateTotal: number;
  inStoreCount: number;
  activeFlowCount: number;
  pendingCount: number;
  riskCount: number;
  inStoreRate: number;
  childCount: number;
  isSelected: boolean;
  isVirtual?: boolean;
  labelMode: 'full' | 'counts' | 'name';
}

export interface OrgTopologyTreeNode {
  id: string;
  name: string;
  value: number;
  nodeStats: OrgTopologyNodeStats;
  children?: OrgTopologyTreeNode[];
  itemStyle: {
    color: string;
    borderColor: string;
    borderWidth: number;
    shadowBlur: number;
    shadowColor: string;
  };
  lineStyle: {
    color: string;
    width: number;
  };
  label: Record<string, unknown>;
}

export interface OrgTopologyLayoutInput {
  leafCount: number;
  maxDepth: number;
  maxSiblingCount: number;
}

export interface OrgTopologyLayoutPolicy {
  verticalGap: number;
  rightPadding: number;
  defaultZoom: number;
  scaleLimit: {
    min: number;
    max: number;
  };
  edgeForkPosition: string;
}

const DEPTH_COLORS = ['#2563eb', '#0f766e', '#b45309', '#7c3aed'];

export function buildOrgTopologyTree(nodes: CollectionOrgOverviewNode[], selectedId = ''): OrgTopologyTreeNode[] {
  return nodes.map((node) => buildOrgTopologyNode(node, selectedId, 0, nodes.length));
}

export function buildOrgTopologyChartRoot(
  nodes: CollectionOrgOverviewNode[],
  rootTotals: CollectionOrgOverviewStats,
  selectedId = '',
): OrgTopologyTreeNode[] {
  const realRoots = buildOrgTopologyTree(nodes, selectedId);
  if (realRoots.length <= 1) return realRoots;
  const statusMetrics = buildCollectionStatusMetrics(rootTotals.total, rootTotals.byStatus);

  return [
    {
      id: ORG_TOPOLOGY_ROOT_ID,
      name: '收藏单位体系',
      value: rootTotals.total,
      nodeStats: {
        id: ORG_TOPOLOGY_ROOT_ID,
        code: `${realRoots.length} 个根节点`,
        depth: 0,
        siblingCount: 1,
        directTotal: rootTotals.total,
        aggregateTotal: rootTotals.total,
        ...statusMetrics,
        childCount: realRoots.length,
        isSelected: false,
        isVirtual: true,
        labelMode: 'full',
      },
      children: realRoots,
      itemStyle: topologyItemStyle('#111827', false, true),
      lineStyle: { color: '#8aa4bf', width: 2 },
      label: topologyLabelStyle('#111827', false, true, 'full'),
    },
  ];
}

export function resolveOrgTopologyLayout(input: OrgTopologyLayoutInput): OrgTopologyLayoutPolicy {
  const { leafCount, maxDepth, maxSiblingCount } = input;
  const verticalGap = maxSiblingCount >= 7 ? 44 : maxSiblingCount >= 4 ? 58 : 92;
  const rightPadding = maxSiblingCount >= 7 ? 340 : maxSiblingCount >= 4 ? 280 : 260;
  let defaultZoom = 1;
  if (leafCount >= 14 || maxDepth >= 4) {
    defaultZoom = 0.72;
  } else if (leafCount >= 9 || maxSiblingCount >= 7) {
    defaultZoom = 0.82;
  } else if (leafCount >= 5 || maxSiblingCount >= 4) {
    defaultZoom = 0.9;
  }
  const edgeForkPosition = maxSiblingCount >= 7 ? '64%' : maxSiblingCount >= 4 ? '60%' : '58%';

  return {
    verticalGap,
    rightPadding,
    defaultZoom,
    scaleLimit: { min: 0.68, max: 1.16 },
    edgeForkPosition,
  };
}

function buildOrgTopologyNode(
  node: CollectionOrgOverviewNode,
  selectedId: string,
  depth: number,
  siblingCount: number,
): OrgTopologyTreeNode {
  const color = DEPTH_COLORS[Math.min(depth, DEPTH_COLORS.length - 1)];
  const isSelected = node.id === selectedId;
  const childSiblingCount = node.children?.length ?? 0;
  const children = node.children?.map((child) => buildOrgTopologyNode(child, selectedId, depth + 1, childSiblingCount));
  const labelMode = resolveLabelMode(depth, siblingCount);
  const statusMetrics = buildCollectionStatusMetrics(node.aggregate.total, node.aggregate.byStatus);

  return {
    id: node.id,
    name: node.name,
    value: node.aggregate.total,
    nodeStats: {
      id: node.id,
      code: node.code || '未设置代码',
      depth,
      siblingCount,
      directTotal: node.direct.total,
      aggregateTotal: node.aggregate.total,
      ...statusMetrics,
      childCount: node.children?.length ?? 0,
      isSelected,
      labelMode,
    },
    children: children?.length ? children : undefined,
    itemStyle: topologyItemStyle(color, isSelected),
    lineStyle: { color: isSelected ? '#2563eb' : '#b7c7d9', width: isSelected ? 3 : 2 },
    label: topologyLabelStyle(color, isSelected, false, labelMode),
  };
}

function resolveLabelMode(depth: number, siblingCount: number): OrgTopologyNodeStats['labelMode'] {
  if (depth <= 0) return 'full';
  if (siblingCount >= 7) return 'name';
  if (siblingCount >= 4) return 'counts';
  return 'full';
}

function topologyItemStyle(color: string, selected: boolean, virtual = false) {
  return {
    color,
    borderColor: selected ? '#1d4ed8' : virtual ? '#111827' : color,
    borderWidth: selected ? 4 : virtual ? 3 : 2,
    shadowBlur: selected ? 18 : virtual ? 12 : 8,
    shadowColor: selected ? 'rgba(37, 99, 235, 0.32)' : 'rgba(15, 23, 42, 0.14)',
  };
}

function topologyLabelStyle(
  color: string,
  selected: boolean,
  virtual = false,
  labelMode: OrgTopologyNodeStats['labelMode'] = 'full',
) {
  return {
    backgroundColor: virtual ? '#111827' : selected ? '#eff6ff' : '#ffffff',
    borderColor: selected ? '#2563eb' : virtual ? '#111827' : '#d7e2ec',
    borderWidth: selected ? 2 : 1,
    borderRadius: 8,
    padding: labelMode === 'name' ? [5, 8] : labelMode === 'counts' ? [7, 10] : [10, 12],
    shadowBlur: selected ? 18 : 12,
    shadowColor: selected ? 'rgba(37, 99, 235, 0.2)' : 'rgba(15, 23, 42, 0.1)',
    color,
  };
}
