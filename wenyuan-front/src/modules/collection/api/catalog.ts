import request from '@/utils/request';
import type {
  CatalogStatus,
  CollectionCatalogContentVo,
  CollectionCatalogVo,
} from '../typings/collection';

const BASE = '/collection/catalog';

export const catalogApi = {
  page(params: any) {
    return request({ url: `${BASE}/page`, method: 'get', params });
  },
  get(collectionId: string) {
    return request({ url: `${BASE}/${collectionId}`, method: 'get' });
  },
  save(collectionId: string, content: CollectionCatalogContentVo, expectedStateHash: string) {
    return request({ url: `${BASE}/${collectionId}`, method: 'put', data: content, params: { expectedStateHash } });
  },
  saveAndSubmit(collectionId: string, content: CollectionCatalogContentVo, expectedStateHash: string) {
    return request({ url: `${BASE}/${collectionId}/save-submit`, method: 'post', data: content, params: { expectedStateHash } });
  },
  submit(collectionId: string, expectedStateHash: string) {
    return request({ url: `${BASE}/${collectionId}/submit`, method: 'post', params: { expectedStateHash } });
  },
  /** 编目审核：APPROVED 通过并登账（写正式登记时间、计入总账），REJECTED 驳回待修改 */
  review(
    collectionId: string,
    status: Extract<CatalogStatus, 'APPROVED' | 'REJECTED'>,
    opinion: string | undefined,
    expectedStateHash: string,
    options: { silentError?: boolean } = {},
  ) {
    return request({ url: `${BASE}/${collectionId}/review`, method: 'post', data: { status, opinion }, params: { expectedStateHash }, ...options });
  },
  revision(collectionId: string) {
    return request({ url: `${BASE}/${collectionId}/revision`, method: 'post' });
  },
  versions(collectionId: string) {
    return request({ url: `${BASE}/${collectionId}/versions`, method: 'get' });
  },
  card(collectionId: string) {
    return request({ url: `${BASE}/${collectionId}/card`, method: 'get' });
  },
  displayName(row: CollectionCatalogVo) {
    return row.content.formalName || row.content.totalName || row.collectionName || '未定名藏品';
  },
};
