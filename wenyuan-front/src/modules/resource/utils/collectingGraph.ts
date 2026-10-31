export interface CollectingNode {
  id: string;
  name: string;
  kind: string;
  background?: string | null;
  era?: string | null;
}

export interface CollectingEdge {
  source: string;
  target: string;
}

export interface CollectingGraph {
  nodes: CollectingNode[];
  edges: CollectingEdge[];
}

export interface CollectingResearch extends CollectingGraph {
  issuedCount: number;
  byType: Record<string, number>;
  bySource: Record<string, number>;
}

/** Keep only valid plan–resource relations and avoid drawing the same relation twice. */
export function normalizeCollectingGraph(data?: Partial<CollectingGraph> | null): CollectingGraph {
  const nodes = new Map<string, CollectingNode>();
  for (const node of data?.nodes || []) {
    if (node.id && !nodes.has(node.id)) nodes.set(node.id, node);
  }
  const edges = new Map<string, CollectingEdge>();
  for (const edge of data?.edges || []) {
    const source = nodes.get(edge.source),
      target = nodes.get(edge.target);
    if (!source || !target || (source.kind === 'PLAN') === (target.kind === 'PLAN')) continue;
    const relation = source.kind === 'PLAN' ? edge : { source: edge.target, target: edge.source };
    edges.set(JSON.stringify([relation.source, relation.target]), relation);
  }
  return { nodes: [...nodes.values()], edges: [...edges.values()] };
}

export function focusCollectingGraph(graph: CollectingGraph, id?: string | null): CollectingGraph {
  if (!id || !graph.nodes.some((node) => node.id === id)) return graph;
  const edges = graph.edges.filter((edge) => edge.source === id || edge.target === id);
  const ids = new Set([id, ...edges.flatMap((edge) => [edge.source, edge.target])]);
  return { nodes: graph.nodes.filter((node) => ids.has(node.id)), edges };
}

export function collectingDegrees(graph: CollectingGraph): Map<string, number> {
  const result = new Map(graph.nodes.map((node) => [node.id, 0]));
  for (const edge of graph.edges) {
    result.set(edge.source, (result.get(edge.source) || 0) + 1);
    result.set(edge.target, (result.get(edge.target) || 0) + 1);
  }
  return result;
}

/** Stable row spacing keeps labels legible; larger graphs scroll instead of compressing labels. */
export function layoutCollectingGraph(graph: CollectingGraph, viewportWidth: number, viewportHeight: number) {
  const width = Math.max(320, viewportWidth || 0);
  const padding = width < 600 ? 16 : 32;
  const nodeWidth = Math.min(240, (width - padding * 2 - 56) / 2);
  const nodeHeight = 84,
    rowGap = 100;
  const byName = (a: CollectingNode, b: CollectingNode) =>
    a.name.localeCompare(b.name, 'zh-CN') || a.id.localeCompare(b.id);
  const plans = graph.nodes.filter((node) => node.kind === 'PLAN').sort(byName);
  const planIndex = new Map(plans.map((node, i) => [node.id, i]));
  const ranks = new Map<string, number[]>();
  for (const edge of graph.edges) {
    const rank = planIndex.get(edge.source);
    if (rank !== undefined) ranks.set(edge.target, [...(ranks.get(edge.target) || []), rank]);
  }
  const rank = (id: string) => {
    const values = ranks.get(id);
    return values?.length ? values.reduce((sum, value) => sum + value, 0) / values.length : plans.length;
  };
  const resources = graph.nodes
    .filter((node) => node.kind !== 'PLAN')
    .sort((a, b) => rank(a.id) - rank(b.id) || byName(a, b));
  const count = Math.max(plans.length, resources.length, 1);
  const height = Math.max(viewportHeight || 0, 24 + (count - 1) * rowGap + nodeHeight);
  const place = (nodes: CollectingNode[], x: number) => {
    const top = (height - ((nodes.length - 1) * rowGap + nodeHeight)) / 2;
    return nodes.map((node, index) => ({ ...node, x, y: top + index * rowGap }));
  };
  const nodes = [...place(plans, padding), ...place(resources, width - padding - nodeWidth)];
  const positions = new Map(nodes.map((node) => [node.id, node]));
  const edges = graph.edges.flatMap((edge) => {
    const source = positions.get(edge.source),
      target = positions.get(edge.target);
    if (!source || !target) return [];
    const x1 = source.x + nodeWidth,
      y1 = source.y + nodeHeight / 2;
    const x2 = target.x,
      y2 = target.y + nodeHeight / 2;
    const bend = (x2 - x1) * 0.45;
    return [
      {
        ...edge,
        key: JSON.stringify([edge.source, edge.target]),
        path: `M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`,
      },
    ];
  });
  return {
    nodes,
    edges,
    width,
    height,
    nodeWidth,
    nodeHeight,
    planCount: plans.length,
    resourceCount: resources.length,
  };
}
