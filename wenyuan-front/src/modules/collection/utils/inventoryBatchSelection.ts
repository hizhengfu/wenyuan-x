export type InventoryBatchLike = {
  batchName?: string | null;
};

export type InventoryBatchQueryLike = {
  batchName: string | null;
};

export type InventoryBatchFormLike = {
  batchName?: string | null;
};

function normalizedBatchName(value: string | null | undefined) {
  return value?.trim() || null;
}

export function applyInventoryBatchSelection<TQuery extends InventoryBatchQueryLike, TForm extends InventoryBatchFormLike>(
  batch: InventoryBatchLike,
  query: TQuery,
  form?: TForm,
) {
  const batchName = normalizedBatchName(batch.batchName);
  query.batchName = batchName;
  if (form) form.batchName = batchName ?? undefined;
  return batchName;
}

export function clearInventoryBatchSelection<TQuery extends InventoryBatchQueryLike, TForm extends InventoryBatchFormLike>(
  query: TQuery,
  form?: TForm,
) {
  query.batchName = null;
  if (form) form.batchName = undefined;
  return null;
}

export function inventoryBatchTitle(batchName: string | null | undefined) {
  return normalizedBatchName(batchName) ? `当前批次：${normalizedBatchName(batchName)}` : '未选择批次';
}

export type InventoryBatchSummaryRowLike = {
  batchName: string;
  total: number;
  matchCount: number;
  surplusCount: number;
  missingCount: number;
  abnormalCount: number;
  attachmentCount: number;
  firstInventoryId?: string;
  lastCheckedAt?: number | null;
  /** 盘点批次配置 id（GET /inventory/batch）；没有配置的历史批次为空。 */
  configId?: string;
  /** 已配置的批次是否已核实方案开始清点（配置里有 plan）。 */
  started?: boolean;
};

export type InventoryBatchConfigLike = {
  id?: string;
  batchName?: string | null;
  plan?: unknown;
};

export type InventoryBatchMergeFilter = {
  keyword?: string | null;
  batchName?: string | null;
  /** 其余按记录过滤的条件（藏品、结果、审批状态、附件、时间）；有值时未产生记录的批次不可能命中，不补入。 */
  [key: string]: unknown;
};

const RECORD_ONLY_FILTERS = ['collectionId', 'result', 'flowStatus', 'hasAttachment', 'checkedAtStart', 'checkedAtEnd'];

function hasValue(value: unknown) {
  return value !== null && value !== undefined && String(value).trim() !== '';
}

/**
 * 清点批次列表 = 按记录汇总（GET /inventory/batches，后端只汇总已有清点记录的批次）
 * + 已配置但还没有记录的批次（GET /inventory/batch），后者计数为 0。
 */
export function mergeInventoryBatchSummaries<TRow extends InventoryBatchSummaryRowLike>(
  summaries: TRow[] | null | undefined,
  configs: InventoryBatchConfigLike[] | null | undefined,
  filter: InventoryBatchMergeFilter = {},
): InventoryBatchSummaryRowLike[] {
  const configByName = new Map<string, InventoryBatchConfigLike>();
  for (const config of configs || []) {
    const name = normalizedBatchName(config.batchName);
    if (name && !configByName.has(name)) configByName.set(name, config);
  }
  const rows: InventoryBatchSummaryRowLike[] = (summaries || []).map((row) => {
    const config = configByName.get(normalizedBatchName(row.batchName) || '');
    return config ? { ...row, configId: config.id, started: Boolean(config.plan) } : { ...row };
  });
  if (RECORD_ONLY_FILTERS.some((key) => hasValue(filter[key]))) return rows;
  const listed = new Set(rows.map((row) => normalizedBatchName(row.batchName)));
  const batchFilter = normalizedBatchName(filter.batchName as string | null | undefined)?.toLowerCase();
  const keyword = normalizedBatchName(filter.keyword as string | null | undefined)?.toLowerCase();
  for (const [name, config] of configByName) {
    if (listed.has(name)) continue;
    const lower = name.toLowerCase();
    if (batchFilter && !lower.includes(batchFilter)) continue;
    if (keyword && !lower.includes(keyword)) continue;
    rows.push({
      batchName: name,
      total: 0,
      matchCount: 0,
      surplusCount: 0,
      missingCount: 0,
      abnormalCount: 0,
      attachmentCount: 0,
      lastCheckedAt: null,
      configId: config.id,
      started: Boolean(config.plan),
    });
  }
  return rows;
}
