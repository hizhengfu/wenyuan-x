import request from '@/utils/request';

export type CollectionSearchGroupKey = 'ARCHIVE' | 'ACQUISITION' | 'CIRCULATION' | 'STORAGE' | 'FOUNDATION';

export interface CollectionSearchCollectionRef {
  id: string;
  totalRegNo: string;
  name: string;
  coverImage?: string | null;
}

export interface CollectionSearchItem {
  id: string;
  type: string;
  typeLabel: string;
  title: string;
  fileId?: string | null;
  subtitle?: string | null;
  summary?: string | null;
  matchedFields: string[];
  relatedCollections: CollectionSearchCollectionRef[];
  relatedCollectionCount: number;
  route: string;
  targetId?: string | null;
  targetYear?: number | null;
  occurredAt?: string | null;
}

export interface CollectionSearchGroup {
  key: CollectionSearchGroupKey;
  label: string;
  description: string;
  total: number;
  pageNum: number;
  pageSize: number;
  items: CollectionSearchItem[];
}

export interface CollectionUnifiedSearchResult {
  keyword: string;
  total: number;
  groups: CollectionSearchGroup[];
}

export const collectionSearchApi = {
  search(params: {
    keyword: string;
    type?: string;
    group?: CollectionSearchGroupKey;
    pageNum?: number;
    pageSize?: number;
  }) {
    return request({ url: '/collection/search', method: 'get', params });
  },
};
