import { type as fetchDictType } from '@/modules/sys/api/dict';

export interface CollectionSurveyExportVo {
  originalName?: string; originalNo?: string; quantity?: number; unit?: string;
  actualQuantity?: number; actualQuantityUnit?: string; dimensionUnit?: string; weightUnit?: string;
  preserveState?: string; entryDate?: string; receiptCompletedAt?: string; registeredAt?: string; entryVoucherNo?: string;
  exchangeCode?: string; standardDimensions?: string; standardWeight?: string;
  registrationDetails?: unknown; qualityIssues?: string[];
  totalRegNo?: string;
  classNo?: string;
  name?: string;
  era?: string;
  categoryName?: string;
  texture?: string;
  dimensions?: string;
  weight?: string;
  completeness?: string;
  completeStatus?: string;
  source?: string;
  acquireDate?: string;
  level?: string;
  sourceDesc?: string;
  orgName?: string;
  image?: string;
}

export const SURVEY_EXPORT_COLUMNS: { key: keyof CollectionSurveyExportVo; title: string }[] = [
  { key: 'totalRegNo', title: '总登记号' },
  { key: 'classNo', title: '分类号' },
  { key: 'name', title: '名称' },
  { key: 'era', title: '年代' },
  { key: 'categoryName', title: '类别' },
  { key: 'texture', title: '质地' },
  { key: 'dimensions', title: '外形尺寸' },
  { key: 'weight', title: '质量(重量)' },
  { key: 'completeness', title: '完残程度' },
  { key: 'completeStatus', title: '完残状况' },
  { key: 'source', title: '来源' },
  { key: 'acquireDate', title: '取得日期' },
  { key: 'level', title: '藏品级别' },
  { key: 'sourceDesc', title: '藏品来源' },
  { key: 'orgName', title: '收藏单位名称' },
  { key: 'image', title: '文物影像' },
  { key: 'originalName', title: '原名' }, { key: 'originalNo', title: '原藏号' },
  { key: 'quantity', title: '登记件套数' }, { key: 'unit', title: '登记数量单位' },
  { key: 'actualQuantity', title: '实际数量' }, { key: 'actualQuantityUnit', title: '实际数量单位' },
  { key: 'dimensionUnit', title: '尺寸单位' }, { key: 'weightUnit', title: '质量单位' },
  { key: 'preserveState', title: '保存状态' }, { key: 'entryDate', title: '实收入馆日期' },
  { key: 'receiptCompletedAt', title: '核收完成时间' }, { key: 'registeredAt', title: '正式登记时间' },
  { key: 'entryVoucherNo', title: '入馆凭证号' }, { key: 'exchangeCode', title: '规范机构代码' },
  { key: 'standardDimensions', title: '规范尺寸' }, { key: 'standardWeight', title: '规范质量' },
  { key: 'registrationDetails', title: '结构化著录' }, { key: 'qualityIssues', title: '待核实项目' },
];

type DictOption = { label: string; value: string };
type DictCache = Record<string, DictOption[]>;

const surveyDictTypes: Partial<Record<keyof CollectionSurveyExportVo, string>> = {
  texture: 'col_texture',
  completeness: 'col_completeness',
  source: 'col_source',
  preserveState: 'col_preserve',
  level: 'col_relic_level',
};

async function loadSurveyDicts() {
  const types = Array.from(new Set(Object.values(surveyDictTypes)));
  const entries = await Promise.all(
    types.map(async (type) => {
      const resp: any = await fetchDictType(type, true);
      const options = (resp?.data ?? []).map((item: any) => ({
        label: String(item.label ?? item.name ?? item.value ?? ''),
        value: String(item.value ?? ''),
      }));
      return [type, options] as const;
    }),
  );
  return Object.fromEntries(entries) as DictCache;
}

function surveyCellText(row: CollectionSurveyExportVo, key: keyof CollectionSurveyExportVo, dicts: DictCache) {
  const value = row[key];
  const text = value != null && typeof value === 'object' ? JSON.stringify(value) : String(value ?? '');
  const dictType = surveyDictTypes[key];
  if (!dictType || !text) return text;
  return dicts[dictType]?.find((item) => item.value === text)?.label || text;
}

export async function buildSurveyCsv(rows: CollectionSurveyExportVo[]) {
  const dicts = await loadSurveyDicts();
  const header = SURVEY_EXPORT_COLUMNS.map((col) => col.title).join(',');
  const body = rows
    .map((row) => SURVEY_EXPORT_COLUMNS.map((col) => `"${surveyCellText(row, col.key, dicts).replace(/"/g, '""')}"`).join(','))
    .join('\n');
  return '﻿' + header + '\n' + body;
}

export async function downloadSurveyCsv(rows: CollectionSurveyExportVo[], fileName = '藏品普查导出.csv') {
  const blob = new Blob([await buildSurveyCsv(rows)], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(a.href);
}
