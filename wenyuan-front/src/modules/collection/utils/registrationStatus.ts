import type { CollectionVo } from '../typings/collection';
/** Registration fact is independent of an unavailable historical date. */
export const isRegistered = (row?: Pick<CollectionVo, 'registeredAt' | 'historicalConfirmedAt'> | null) =>
  row != null && ((row.registeredAt != null && row.registeredAt !== '') || row.historicalConfirmedAt != null);
export const registrationDateFallback = (row?: Pick<CollectionVo, 'registeredAt' | 'historicalConfirmedAt'> | null) =>
  row?.historicalConfirmedAt != null ? '原账未载／待考' : '尚未正式登记';
