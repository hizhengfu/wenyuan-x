import type { CollectionVo } from '../typings/collection';

const relicLevelLabels: Record<string, string> = {
  FIRST: '一级',
  SECOND: '二级',
  THIRD: '三级',
  GENERAL: '一般文物',
  UNRATED: '未定级',
};

const lifeStatusLabels: Record<string, string> = {
  PENDING: '待定',
  IN_STORE: '在库',
  IN_USE: '提用中',
  RESTORING: '修复中',
  ON_LOAN: '借出',
  MISSING: '盘亏',
  DEREGISTERED: '已退出馆藏',
  PRE_OUT: '待出库',
  RETURN_HOLD: '归还待核查',
};

export type CollectionSelectOption = {
  label: string;
  value: string;
  description: string;
  searchText: string;
  [key: string]: unknown;
};

function text(value: unknown) {
  return String(value ?? '').trim();
}

function dictionaryLabel(value: unknown, labels: Record<string, string>, fallback: string) {
  const key = text(value);
  return (key && labels[key]) || key || fallback;
}

export function buildCollectionSelectOption(collection: CollectionVo): CollectionSelectOption {
  const totalRegNo = text(collection.totalRegNo);
  const name = text(collection.name);
  const category = text(collection.categoryName) || '未分类';
  const era = text(collection.era) || '未填写';
  const level = dictionaryLabel(collection.level, relicLevelLabels, '未填写');
  const warehouse = text(collection.warehouseName) || (text(collection.warehouseId) ? '库位名称待核对' : '未登记库位');
  const lifeStatus = dictionaryLabel(collection.lifeStatus, lifeStatusLabels, '未设置');
  const value = text(collection.id);
  const label = [totalRegNo, name].filter(Boolean).join(' ') || value || '未命名藏品';
  const description = [
    `分类：${category}`,
    `年代：${era}`,
    `级别：${level}`,
    `库位：${warehouse}`,
    `状态：${lifeStatus}${collection.inventoryHold ? "（清点异常待处理）" : ""}`,
  ].join(' · ');

  return {
    label,
    value,
    description,
    searchText: [
      label,
      description,
      text(collection.level),
      text(collection.lifeStatus),
    ].filter(Boolean).join(' ').toLocaleLowerCase(),
  };
}

export function collectionSelectOptionMatches(pattern: string, option: CollectionSelectOption) {
  const keyword = text(pattern).toLocaleLowerCase();
  return !keyword || option.searchText.includes(keyword);
}
