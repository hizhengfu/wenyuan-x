/**
 * 藏品选择器远程检索权限，与后端 CollectionController.page / get 一致：
 * @PreAuthorize("hasAnyAuthority('ADMIN','COLLECTION_ADMIN','COLLECTION_SELECT')")
 * 无此码时不得发起 /collection/page、/collection/{id}，否则会 403。
 */
export const COLLECTION_SELECT_QUERY_CODES = Object.freeze([
  'ADMIN',
  'COLLECTION_ADMIN',
  'COLLECTION_SELECT',
] as const);

export const COLLECTION_SELECT_QUERY_PERMISSION = COLLECTION_SELECT_QUERY_CODES.join(',');
