import type { CollectionVo } from '../typings/collection';

export type WorkbenchStatInput = {
  total?: number;
  byStatus?: Record<string, number>;
  registeredThisMonth?: number;
};

export type WorkbenchMetrics = {
  total: number;
  inStoreCount: number;
  activeFlowCount: number;
  pendingCount: number;
  riskCount: number;
  registeredThisMonth: number;
  inStoreRate: number;
};

export type CollectionStatusMetrics = Pick<
  WorkbenchMetrics,
  'inStoreCount' | 'activeFlowCount' | 'pendingCount' | 'riskCount' | 'inStoreRate'
>;

function percent(value: number, total: number) {
  if (!total) return 0;
  return Math.round((value / total) * 100);
}

export function buildCollectionStatusMetrics(total: number, byStatus: Record<string, number> = {}): CollectionStatusMetrics {
  const inStoreCount = byStatus.IN_STORE ?? 0;
  const activeFlowCount = (byStatus.IN_USE ?? 0) + (byStatus.ON_LOAN ?? 0) + (byStatus.RESTORING ?? 0);
  const pendingCount = byStatus.PENDING ?? 0;
  const riskCount = (byStatus.RESTORING ?? 0) + (byStatus.MISSING ?? 0);

  return {
    inStoreCount,
    activeFlowCount,
    pendingCount,
    riskCount,
    inStoreRate: percent(inStoreCount, total),
  };
}

export function buildWorkbenchMetrics(stat: WorkbenchStatInput): WorkbenchMetrics {
  const byStatus = stat.byStatus ?? {};
  const total = stat.total ?? 0;
  const statusMetrics = buildCollectionStatusMetrics(total, byStatus);

  return {
    total,
    ...statusMetrics,
    registeredThisMonth: stat.registeredThisMonth ?? 0,
  };
}

export function hasCollectionImage(item: CollectionVo) {
  return Boolean(item.coverImage) || Number(item.mediaCount ?? 0) > 0;
}

export function pickFeaturedCollections(items: CollectionVo[], limit = 6) {
  return items
    .filter(hasCollectionImage)
    .slice(0, limit);
}
