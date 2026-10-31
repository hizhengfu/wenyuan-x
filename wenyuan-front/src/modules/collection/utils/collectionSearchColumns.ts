import type { CollectionFieldOption } from './collectionFieldSettings';

export const searchColumnFieldOptions: CollectionFieldOption[] = [
  { label: '缩略图', value: 'coverImage' },
  { label: '总登记号', value: 'totalRegNo' },
  { label: '名称', value: 'name' },
  { label: '分类号', value: 'classNo' },
  { label: '文物分类', value: 'categoryName' },
  { label: '年代', value: 'era' },
  { label: '质地', value: 'texture' },
  { label: '级别', value: 'level' },
  { label: '来源', value: 'source' },
  { label: '完残', value: 'completeness' },
  { label: '状态', value: 'lifeStatus' },
  { label: '取得日期', value: 'acquireDate' },
  { label: '库位', value: 'warehouseName' },
  { label: '编目', value: 'catalogStatus' },
  { label: '影像', value: 'mediaCount' },
  { label: '附件', value: 'attachmentCount' },
];

export const defaultSearchColumnKeys = [
  'coverImage',
  'totalRegNo',
  'name',
  'classNo',
  'categoryName',
  'era',
  'texture',
  'level',
  'source',
  'completeness',
  'lifeStatus',
  'mediaCount',
  'attachmentCount',
];

export const searchColumnStorageVersion = '2-thumbnail';

export function migrateSearchColumnKeys(keys: readonly string[], alreadyMigrated: boolean) {
  if (alreadyMigrated || keys.includes('coverImage')) return [...keys];
  return ['coverImage', ...keys];
}
