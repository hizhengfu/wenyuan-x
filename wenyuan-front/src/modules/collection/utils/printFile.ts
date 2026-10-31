import { resolveCollectionPrintMeta } from './printDocumentMeta';
import type { CollectionPrintRenderRequest, CollectionPrintVo, CollectionVo } from '../typings/collection';

export type CollectionPrintFileFormat = 'PDF' | 'DOCX';

export function collectionPrintFileExtension(format: CollectionPrintFileFormat) {
  return format === 'PDF' ? 'pdf' : 'docx';
}

export function collectionPrintFilePath(data: CollectionPrintVo, format: CollectionPrintFileFormat = 'PDF') {
  const id = data.businessId || data.documentNo;
  if (!data.type || !id) return '';
  return `/collection/print/${encodeURIComponent(data.type)}/${encodeURIComponent(id)}.${collectionPrintFileExtension(format)}`;
}

export function collectionPrintBatchFilePath(type: string, ids: string[], format: CollectionPrintFileFormat = 'PDF') {
  const cleanIds = ids.map((id) => String(id || '').trim()).filter(Boolean);
  if (!type || !cleanIds.length) return '';
  const query = cleanIds.map((id) => `ids=${encodeURIComponent(id)}`).join('&');
  return `/collection/print/${encodeURIComponent(type)}/batch.${collectionPrintFileExtension(format)}?${query}`;
}

export function collectionPrintDirectFilePath(input: {
  data?: CollectionPrintVo | null;
  items?: CollectionPrintVo[];
  type?: string;
  id?: string;
  ids?: string[];
  title?: string;
  documentNo?: string;
}, format: CollectionPrintFileFormat = 'PDF') {
  if (input.data || input.items?.length) return '';
  const type = cleanText(input.type);
  const id = cleanText(input.id);
  const ids = (input.ids || []).map((item) => cleanText(item)).filter(Boolean);
  // 单件优先 path：/collection/print/{type}/{id}.pdf|docx（帐册登记表已验证；勿用 /collection/print?type&ids）
  const singleId = id || (ids.length === 1 ? ids[0] : '');
  if (type && singleId) {
    return collectionPrintFilePath(
      {
        type,
        title: cleanText(input.title),
        businessId: singleId,
        documentNo: cleanText(input.documentNo) || singleId,
        fields: [],
        collections: [],
      },
      format,
    );
  }
  // 多件走后端确有的 batch path：/collection/print/{type}/batch.pdf?ids=
  if (type && ids.length) return collectionPrintBatchFilePath(type, ids, format);
  return '';
}

/** 禁止的旧 query 形态（会 400「藏品不存在」）；仅用于单测/自检。 */
export function isForbiddenCollectionPrintQueryPath(path: string) {
  const text = String(path || '');
  return /^\/collection\/print\/?(?:\?|$)/.test(text.split('#')[0]);
}

export function buildCollectionPrintFileName(
  data: CollectionPrintVo | null | undefined,
  format: CollectionPrintFileFormat = 'PDF',
) {
  if (!data) return `藏品打印表单.${collectionPrintFileExtension(format)}`;
  const meta = resolveCollectionPrintMeta(data);
  const id = data.documentNo || data.businessId || data.type || 'PRINT';
  const safeId = String(id).replace(/[\\/:*?"<>|]/g, '_');
  return `${meta.title}_${safeId}.${collectionPrintFileExtension(format)}`;
}

export function buildCollectionPrintBatchFileName(
  type: string,
  count: number,
  format: CollectionPrintFileFormat = 'PDF',
) {
  const safeType = String(type || 'PRINT').replace(/[\\/:*?"<>|]/g, '_');
  return `藏品打印表单_批量${count}份_${safeType}.${collectionPrintFileExtension(format)}`;
}

function cleanText(value: unknown) {
  return String(value ?? '').trim();
}

function splitValues(value: unknown) {
  return cleanText(value)
    .split(/[,，;\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function collectionName(collection: CollectionVo | null | undefined) {
  return cleanText(collection?.name) || cleanText(collection?.totalRegNo) || cleanText(collection?.id);
}

function findCollection(collections: CollectionVo[] = [], id: unknown) {
  const text = cleanText(id);
  if (!text) return null;
  return collections.find((item) => cleanText(item.id) === text || cleanText(item.totalRegNo) === text) || null;
}

function collectionNamesByValue(collections: CollectionVo[] = [], value: unknown) {
  const values = splitValues(value);
  const names = values
    .map((item) => collectionName(findCollection(collections, item)))
    .filter(Boolean);
  if (names.length) return names.join('，');
  return collections.map((item) => collectionName(item)).filter(Boolean).join('，');
}

function categoryNameByValue(collections: CollectionVo[] = [], value: unknown) {
  const text = cleanText(value);
  const matched = text
    ? collections.find((item) => cleanText(item.categoryId) === text || cleanText(item.categoryName) === text)
    : null;
  return cleanText(matched?.categoryName) || cleanText(collections.find((item) => cleanText(item.categoryName))?.categoryName);
}

function normalizePrintField(data: CollectionPrintVo, field: CollectionPrintVo['fields'][number]) {
  const label = cleanText(field.label);
  const collections = data.collections || [];
  if (/文物分类|藏品分类/.test(label)) {
    const value = categoryNameByValue(collections, field.value);
    return value ? { ...field, value, dictType: undefined } : field;
  }
  if (/登账藏品|入账藏品|关联藏品/.test(label)) {
    const value = collectionNamesByValue(collections, field.value);
    return value ? { ...field, value, dictType: undefined } : field;
  }
  return field;
}

export function normalizeCollectionPrintData(data: CollectionPrintVo): CollectionPrintVo {
  if (!data.fields?.length) return data;
  const fields = data.fields.map((field) => normalizePrintField(data, field));
  const changed = fields.some((field, index) => field !== data.fields[index]);
  return changed ? { ...data, fields } : data;
}

export function buildCollectionPrintRenderRequest(input: {
  data?: CollectionPrintVo | null;
  items?: CollectionPrintVo[];
  type?: string;
  id?: string;
  ids?: string[];
}): CollectionPrintRenderRequest {
  const ids = (input.ids || []).map((id) => String(id || '').trim()).filter(Boolean);
  if (input.items?.length) return { items: input.items.map(normalizeCollectionPrintData) };
  if (input.data) return { data: normalizeCollectionPrintData(input.data) };
  return {
    type: input.type,
    id: input.id,
    ids,
  };
}
