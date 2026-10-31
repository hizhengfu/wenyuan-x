import type { RfidRead, RfidReadClassification, RfidScanPreview, RfidScanSession } from '../typings/rfid';

export type RfidReviewFilter = RfidReadClassification | 'EXPECTED_ITEMS';
export type RfidReviewRow = Omit<RfidRead, 'firstSeenAt' | 'lastSeenAt'> &
  Partial<Pick<RfidRead, 'firstSeenAt' | 'lastSeenAt'>>;

export function rfidReviewRows(preview: RfidScanPreview | null, filter: RfidReviewFilter): RfidReviewRow[] {
  if (!preview) return [];
  const missing: RfidReviewRow[] = preview.missingItems.map((item) => ({
    collectionId: item.collectionId,
    collectionNo: item.collectionNo,
    collectionName: item.collectionName,
    epc: item.expectedEpc || '',
    direction: 'UNKNOWN',
    readCount: 0,
    classification: 'MISSING',
  }));
  if (filter === 'MISSING') return missing;
  const matching = preview.reads.filter((read) =>
    filter === 'EXPECTED_ITEMS' ? read.classification === 'EXPECTED' : read.classification === filter,
  );
  if (filter !== 'EXPECTED_ITEMS') return matching;
  // 应读数量按藏品去重；漏读项来自服务端冻结清单，不从读到的标签反推。
  const expected = new Map<string, RfidReviewRow>();
  for (const row of [...matching, ...missing]) {
    if (row.collectionId) expected.set(row.collectionId, row);
  }
  return [...expected.values()];
}

export interface RfidFinalizeEligibility {
  session: RfidScanSession | null;
  preview: RfidScanPreview | null;
  previewOnly: boolean;
  leaseHeld: boolean;
  busy: boolean;
  permitted: boolean;
  overridePermitted: boolean;
  force: boolean;
  reason: string;
}

export function canFinalizeRfidScan(context: RfidFinalizeEligibility): boolean {
  const { session, preview } = context;
  if (context.previewOnly || context.busy || !context.leaseHeld || !context.permitted) return false;
  if (!session || !preview || session.id !== preview.sessionId) return false;
  if (session.state !== 'PREVIEW_READY' || preview.state !== 'PREVIEW_READY') return false;
  if (!preview.stopAcknowledged || !preview.settled) return false;
  if (context.force && (!context.overridePermitted || !context.reason.trim())) return false;
  if (!preview.blockReasons.length) return preview.canFinalize;
  return context.force && context.overridePermitted && Boolean(context.reason.trim());
}
