/**
 * 出库相关凭证的打印类型。取值以后端为准：
 * module-collection domain/EnumCollectionPrintType（MOVEMENT、REQUISITION、MOUNTING），
 * 标题见 service/CollectionPrintTemplateRegistry：
 * - MOVEMENT「藏品出入库凭证」，按出入库流水 id 打印（CollectionPrintService.movementPrint）；
 * - REQUISITION「馆藏藏品提用单」，按提用单 id 打印（requisitionPrint）；
 * - MOUNTING「书画装裱交接凭证」，按出入库流水 id 打印（mountingPrint）。
 * 批量导出走 GET /collection/print/{type}/batch.pdf|docx?ids=…
 */
export const COLLECTION_VOUCHER_PRINT_TYPES = {
  MOVEMENT: { type: 'MOVEMENT', title: '藏品出入库凭证' },
  REQUISITION: { type: 'REQUISITION', title: '馆藏藏品提用单' },
  MOUNTING: { type: 'MOUNTING', title: '书画装裱交接凭证' },
} as const;

export type CollectionVoucherPrintKind = keyof typeof COLLECTION_VOUCHER_PRINT_TYPES;

/** 勾选行转成批量打印 id：去空、去重、保持勾选顺序。 */
export function voucherExportIds(rows: Array<{ id?: string | number | null }> | null | undefined): string[] {
  const seen = new Set<string>();
  const ids: string[] = [];
  for (const row of rows || []) {
    const id = String(row?.id ?? '').trim();
    if (!id || seen.has(id)) continue;
    seen.add(id);
    ids.push(id);
  }
  return ids;
}

/** 批量导出弹窗的标题，例如「书画装裱交接凭证（3 份）」。 */
export function voucherExportTitle(kind: CollectionVoucherPrintKind, count: number) {
  const title = COLLECTION_VOUCHER_PRINT_TYPES[kind].title;
  return count > 1 ? `${title}（${count} 份）` : title;
}
