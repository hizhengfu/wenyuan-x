import { cloneDeep, isEqual } from 'lodash-es';
import { importConfirmation } from './importResponses';
import type { CollectionFileImportItemVo, CollectionFileImportResultVo } from '../typings/collection';
const object = (v: any) => v != null && typeof v === 'object' && !Array.isArray(v);
const text = (v: any) => typeof v === 'string' && !!v.trim();
const count = (v: any) => Number.isSafeInteger(v) && v >= 0;
const ids = (v: any) => Array.isArray(v) && v.every(text) && new Set(v).size === v.length;
function read(response: any): CollectionFileImportResultVo {
  const d = response?.data;
  if (!object(d) || typeof d.valid !== 'boolean' || !Array.isArray(d.errors) || d.errors.some((e: any) => !object(e) || !count(e.rowNo) || !text(e.field) || !text(e.message))) throw new Error('文件关联响应不完整，请重新核对');
  if (d.valid && d.errors.length) throw new Error('文件关联结果状态不一致，请重新核对');
  if (!d.valid) return { ...d, errors: d.errors.length ? d.errors : [{ rowNo: 0, field: 'server', message: '本次文件关联未通过校验，请重新核对' }] };
  const confirmation = importConfirmation(d.confirmation);
  if (![d.matchedCount,d.mediaCount,d.attachmentCount].every(count) || d.matchedCount !== d.mediaCount + d.attachmentCount ||
      !ids(d.createdIds) || !ids(d.unchangedIds) || !Array.isArray(d.matches) || d.matches.length !== d.matchedCount ||
      d.matches.some((r: any,i: number) => !object(r) || r.rowNo !== i + 1 || !text(r.collectionId) || !text(r.collectionName) || !text(r.totalRegNo) || r.orgId !== confirmation.orgId ||
        !text(r.fileId) || !['MEDIA','ATTACHMENT'].includes(r.kind) || !/^[0-9a-f]{64}$/.test(r.contentSha256) || !count(r.size) || (r.existingId != null && !text(r.existingId)))) throw new Error('文件匹配对象或内容依据不完整，请重新预校验');
  if (d.matches.filter((r: any) => r.kind === 'MEDIA').length !== d.mediaCount || d.matches.filter((r: any) => r.kind === 'ATTACHMENT').length !== d.attachmentCount) throw new Error('文件关联类型数量不一致');
  return cloneDeep({ ...d, confirmation });
}
export function batchFileValidation(response: unknown, items: CollectionFileImportItemVo[]) {
  const d=read(response); if (!d.valid) return d;
  if (d.createdIds.length || d.matches.length !== items.length || d.matches.some((r,i) => r.fileId !== items[i].fileId || r.kind !== (items[i].kind || 'MEDIA') || (items[i].totalRegNo?.trim() && r.totalRegNo !== items[i].totalRegNo?.trim())) ||
      !isEqual(d.unchangedIds,d.matches.flatMap(r => r.existingId ? [r.existingId] : []))) throw new Error('预校验与本次上传文件或登记号不一致，请重新核对');
  return d;
}
export function batchFileCommit(response: unknown, expected: CollectionFileImportResultVo) {
  const d=read(response); if (!d.valid) return d;
  const withoutBinding = (rows: CollectionFileImportResultVo['matches']) => rows.map(({bindingId, ...row}) => row);
  if (!isEqual(d.confirmation,expected.confirmation) || !isEqual(withoutBinding(d.matches),withoutBinding(expected.matches)) ||
      d.matches.some(r => !text(r.bindingId)) || !isEqual(d.unchangedIds,expected.unchangedIds) ||
      !isEqual(d.createdIds,d.matches.filter(r => !r.existingId).map(r => r.bindingId)) ||
      d.matches.some(r => r.existingId && r.bindingId !== r.existingId) || new Set([...d.createdIds,...d.unchangedIds]).size !== d.matchedCount) throw new Error('文件关联回执与本次确认不一致，请核对实际结果后再办理');
  return d;
}
