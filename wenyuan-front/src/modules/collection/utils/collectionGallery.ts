import type { CollectionVo } from '../typings/collection';

type PresenceValue = boolean | string | null | undefined;

export interface GalleryQueryInput {
  pageNum?: number | null;
  pageSize?: number | null;
  hasImage?: PresenceValue;
  [key: string]: unknown;
}

function normalizedPresence(value: PresenceValue, fallback: boolean | null = null): boolean | null {
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (trimmed === 'true') return true;
    if (trimmed === 'false') return false;
  }
  return fallback;
}

function normalizedValue(value: unknown): unknown {
  if (typeof value === 'string') return value.trim() || undefined;
  return value ?? undefined;
}

export function buildGalleryQuery(input: GalleryQueryInput) {
  const query: Record<string, unknown> = {
    pageNum: input.pageNum || 1,
    pageSize: input.pageSize || 24,
  };
  const hasImage = normalizedPresence(input.hasImage);
  if (hasImage !== null) query.hasImage = hasImage;
  Object.entries(input).forEach(([key, value]) => {
    if (key === 'pageNum' || key === 'pageSize' || key === 'hasImage') return;
    const normalized = key === 'hasAttachment' ? normalizedPresence(value as PresenceValue) : normalizedValue(value);
    if (normalized !== undefined && normalized !== null) query[key] = normalized;
  });
  return query;
}

export function galleryImageFileId(collection: Pick<CollectionVo, 'coverImage' | 'previewImage'>, fallbackFileId = '') {
  return collection.coverImage?.trim() || collection.previewImage?.trim() || fallbackFileId;
}

export function galleryCardMeta(collection: CollectionVo) {
  return [
    { label: '登记号', value: collection.totalRegNo || '--' },
    { label: '分类', value: collection.categoryName || '--' },
    { label: '年代', value: collection.era || '--' },
    { label: '库位', value: collection.warehouseName || '--' },
    { label: '影像/附件', value: `${collection.mediaCount || 0} / ${collection.attachmentCount || 0}` },
  ];
}
