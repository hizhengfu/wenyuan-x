import * as XLSX from 'xlsx';
import type { CollectionImportErrorVo, CollectionImportRowVo } from '../typings/collection';

const titleToField: Record<string, keyof CollectionImportRowVo> = {
  总登记号: 'totalRegNo',
  原名: 'originalName', 原藏号: 'auxNo', 登记件套数: 'quantity', 登记数量单位: 'unit', 实际数量: 'actualQuantity', 实际数量单位: 'actualQuantityUnit', 尺寸单位: 'dimensionUnit', 质量单位: 'weightUnit', 保存状态: 'preserveState', 实收入馆日期: 'entryDate', 取得日期: 'acquireDate',
  分类号: 'classNo',
  名称: 'name',
  年代: 'era',
  质地: 'texture',
  '质量(重量)': 'weight',
  完残程度: 'completeness',
  完残状况: 'completeStatus',
  来源: 'source',
  入藏时间: 'acquireDate',
  藏品级别: 'level',
  藏品来源: 'sourceDesc',
  文物影像: 'coverImage',
  文物影像文件名: 'coverImageName',
};

const dimensionFields: Record<string, keyof CollectionImportRowVo> = {
  通高: 'height',
  口径: 'mouth',
  底径: 'bottom',
  腹径: 'belly',
  长: 'length',
  宽: 'width',
  直径: 'diameter',
};

export async function parseSurveyImportFile(
  file: File,
): Promise<{ rows: CollectionImportRowVo[]; errors: CollectionImportErrorVo[] }> {
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array', cellDates: false });
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  const raw = XLSX.utils.sheet_to_json<Record<string, any>>(sheet, { defval: '' });
  const errors: CollectionImportErrorVo[] = [];
  const rows = raw.map((source, index) => {
    const rowNo = index + 2;
    const row: CollectionImportRowVo = { rowNo };
    Object.entries(titleToField).forEach(([title, field]) => {
      const value = String(source[title] ?? '').trim();
      if (value) (row as Record<string, any>)[field] = ['quantity', 'actualQuantity'].includes(field) ? Number(value) : value;
    });
    const categoryName = String(source['类别'] ?? '').trim();
    if (categoryName) {
      row.categoryName = categoryName;
    }
    parseDimensions(String(source['外形尺寸'] ?? ''), row);
    if (!Number.isSafeInteger(row.quantity) || Number(row.quantity) <= 0 || !row.unit) errors.push({ rowNo, field: 'quantity', message: '请按原账填写登记件套数及单位，不能由实际数量推定' });
    if (!row.name) errors.push({ rowNo, field: 'name', message: '名称必填' });
    if (!categoryName) errors.push({ rowNo, field: 'categoryId', message: '类别必填' });
    if (!row.source) errors.push({ rowNo, field: 'source', message: '来源必填' });
    if (!row.level) errors.push({ rowNo, field: 'level', message: '藏品级别必填' });
    return row;
  });
  return { rows, errors };
}

function parseDimensions(value: string, row: CollectionImportRowVo) {
  value.split(/[；;]/).forEach((part) => {
    const [label, ...rest] = part.split(/[：:]/);
    const field = dimensionFields[label?.trim()];
    const text = rest.join(':').trim();
    if (field && text) (row as Record<string, any>)[field] = text;
  });
}
