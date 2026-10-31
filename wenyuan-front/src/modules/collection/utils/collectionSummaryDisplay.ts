export type CollectionSummaryLike = {
  id?: string | null;
  totalRegNo?: string | null;
  name?: string | null;
};

function splitIds(value?: string | null) {
  return (value || '')
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

export function collectionSummaryLabel(item: CollectionSummaryLike) {
  return [item.totalRegNo, item.name].map((value) => String(value || '').trim()).filter(Boolean).join(' ') || item.id || '--';
}

export function collectionSummaryCount(summaries?: CollectionSummaryLike[] | null, fallbackIds?: string | null) {
  return summaries?.length || splitIds(fallbackIds).length;
}

export function collectionSummaryPreview(summaries?: CollectionSummaryLike[] | null, fallbackIds?: string | null, limit = 2) {
  const rows = summaries || [];
  if (!rows.length) {
    const count = splitIds(fallbackIds).length;
    return count ? `${count} 件` : '--';
  }
  const labels = rows.slice(0, limit).map(collectionSummaryLabel);
  const more = rows.length > limit ? `，另 ${rows.length - limit} 件` : '';
  return `${labels.join('、')}${more}`;
}
