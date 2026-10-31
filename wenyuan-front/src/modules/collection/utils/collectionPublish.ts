export type CollectionPublishRow = {
  id?: string | null;
  published?: boolean | null;
  catalogStatus?: string | null;
  lifeStatus?: string | null;
};
/** 旧标志只能说明曾启用供稿，当前公开稿和实际渠道须进入核查记录确认。 */
export function publishStatus(row: CollectionPublishRow) {
  return row.published
    ? { text: '有CMS供稿历史，当前状态待核对', type: 'info' }
    : { text: '无CMS供稿记录', type: 'default' };
}
export function publishActionText(_row: CollectionPublishRow) {
  return '公开核查与实发记录';
}
export function canManageDisclosure(row: CollectionPublishRow) {
  return Boolean(row.id);
}
