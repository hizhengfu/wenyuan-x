/**
 * 修复草稿 / 审批表单：从 draft-context 的 collection 快照写入总账号+名称，
 * 供 CollectionSelect.fallbackLabel 使用，避免无 COLLECTION_SELECT 时裸 id。
 */

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : value == null ? '' : String(value).trim();
}

/** 拼「总账号 名称」展示文案；缺一则用另一项。 */
export function collectionSnapshotLabel(input: {
  collectionTotalRegNo?: unknown;
  collectionName?: unknown;
  totalRegNo?: unknown;
  name?: unknown;
} | null | undefined): string | undefined {
  if (!input) return undefined;
  const no = text(input.collectionTotalRegNo ?? input.totalRegNo);
  const name = text(input.collectionName ?? input.name);
  const label = [no, name].filter(Boolean).join(' ');
  return label || undefined;
}

/**
 * 将 draft-context 的 project + collection 合并为表单体。
 * project 可能不含 collectionName/collectionTotalRegNo（列表 VO 才带），必须从 collection 补齐。
 */
export function mergeDraftBodyWithCollectionSnapshot(
  project: Record<string, any> | null | undefined,
  collection: Record<string, any> | null | undefined,
  extras: { id?: string; collectionId: string },
): Record<string, any> {
  const base = project && typeof project === 'object' ? { ...project } : {};
  const fromCollectionName = text(collection?.name);
  const fromCollectionNo = text(collection?.totalRegNo);
  return {
    ...base,
    id: extras.id || base.id || undefined,
    collectionId: extras.collectionId,
    collectionName: text(base.collectionName) || fromCollectionName || undefined,
    collectionTotalRegNo: text(base.collectionTotalRegNo) || fromCollectionNo || undefined,
  };
}
