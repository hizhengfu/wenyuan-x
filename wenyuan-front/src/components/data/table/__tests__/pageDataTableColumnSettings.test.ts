import { describe, expect, it } from 'vitest';

import {
  collectColumnSettingOptions,
  filterVisibleTableColumns,
  normalizeVisibleColumnKeys,
} from '../pageDataTableColumnSettings';

describe('PageDataTable column settings', () => {
  const columns = [
    { key: 'sequence', title: '序号' },
    {
      key: 'numberGroup',
      title: '藏品编号',
      children: [
        { key: 'numberType', title: '编号类型' },
        { key: 'totalRegNo', title: '编号' },
      ],
    },
    { key: 'hiddenByPage', title: '页面固定隐藏', show: false },
    { key: 'operate', title: '操作', type: 'operate' },
  ];

  it('derives selectable leaf columns and excludes operation or permanently hidden columns', () => {
    expect(collectColumnSettingOptions(columns)).toEqual([
      { label: '序号', value: 'sequence' },
      { label: '编号类型', value: 'numberType' },
      { label: '编号', value: 'totalRegNo' },
    ]);
  });

  it('recursively filters grouped columns while keeping operation columns', () => {
    const filtered = filterVisibleTableColumns(
      columns,
      new Set(['sequence', 'numberType', 'totalRegNo']),
      new Set(['totalRegNo']),
      new Set(),
    );

    expect(filtered.map((column) => column.key)).toEqual(['numberGroup', 'operate']);
    expect(filtered[0].children?.map((column) => column.key)).toEqual(['totalRegNo']);
  });

  it('falls back to configured defaults when stored keys are invalid or empty', () => {
    const options = collectColumnSettingOptions(columns);
    expect(normalizeVisibleColumnKeys(['removed'], options, ['sequence', 'totalRegNo'])).toEqual([
      'sequence',
      'totalRegNo',
    ]);
    expect(normalizeVisibleColumnKeys(['numberType', 'numberType'], options, [])).toEqual(['numberType']);
  });
});
