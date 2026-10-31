import { Fragment, h, type VNode } from 'vue';
import { describe, expect, it, vi } from 'vitest';

import { filterQueryFieldNodes, queryFieldDescriptor } from '../AdvanceQueryFormFields';
import {
  defaultVisibleQueryKeys,
  mergeQuerySettingOptions,
  normalizeVisibleQueryKeys,
  parseStoredVisibleQueryKeys,
} from '../advanceQueryFormSettings';

describe('AdvanceQueryForm query settings', () => {
  it('discovers query fields from existing path and label props', () => {
    const keyword = h('div', { path: 'keyword', label: '关键字' });
    const dateRange = h('div', { path: ['createdAt', 'range'], label: '创建时间' });

    expect(queryFieldDescriptor(keyword)).toEqual({ label: '关键字', value: 'keyword' });
    expect(queryFieldDescriptor(dateRange)).toEqual({ label: '创建时间', value: 'createdAt.range' });
  });

  it('filters discovered fields while preserving non-configurable slot content', () => {
    const keyword = h('div', { path: 'keyword', label: '关键字' });
    const status = h('div', { path: 'status', label: '状态' });
    const append = h('span', { class: 'query-help' }, '说明');
    const fragment = h(Fragment, null, [keyword, status, append]) as VNode;

    const result = filterQueryFieldNodes([fragment], ['keyword'], true);

    expect(result.discovered).toEqual([
      { label: '关键字', value: 'keyword' },
      { label: '状态', value: 'status' },
    ]);
    expect(result.rendered).toEqual([keyword, append]);
  });

  it('merges only exceptional field configuration into auto-discovered fields', () => {
    const clearDate = vi.fn();
    const options = mergeQuerySettingOptions(
      [
        { label: '关键字', value: 'keyword' },
        { label: '入藏日期', value: 'acquireDate' },
      ],
      [
        {
          value: 'acquireDate',
          clearPaths: ['acquireDateStart', 'acquireDateEnd'],
          defaultVisible: false,
          onClear: clearDate,
        },
        { value: 'notRendered', label: '未渲染字段' },
      ],
      ['keyword'],
    );

    expect(options).toHaveLength(2);
    expect(options[0]).toMatchObject({
      label: '关键字',
      value: 'keyword',
      clearPaths: ['keyword'],
      disabled: true,
    });
    expect(options[1]).toMatchObject({
      label: '入藏日期',
      value: 'acquireDate',
      clearPaths: ['acquireDateStart', 'acquireDateEnd'],
      defaultVisible: false,
      disabled: false,
      onClear: clearDate,
    });
    expect(defaultVisibleQueryKeys(options)).toEqual(['keyword']);
  });

  it('normalizes stored selections against current fields and safely falls back', () => {
    const options = mergeQuerySettingOptions(
      [
        { label: '关键字', value: 'keyword' },
        { label: '状态', value: 'status' },
      ],
      [],
    );
    const defaults = defaultVisibleQueryKeys(options);

    expect(parseStoredVisibleQueryKeys('["status","removed"]', options, defaults)).toEqual(['status']);
    expect(parseStoredVisibleQueryKeys('{bad json', options, defaults)).toEqual(['keyword', 'status']);
    expect(normalizeVisibleQueryKeys([], options, defaults)).toEqual(['keyword', 'status']);
  });
});
