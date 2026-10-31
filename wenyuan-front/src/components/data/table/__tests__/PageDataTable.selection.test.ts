/**
 * @vitest-environment happy-dom
 */
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, nextTick } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PageDataTable from '../PageDataTable.vue';

vi.mock('sortablejs', () => ({
  default: { create: vi.fn() },
}));

vi.mock('@/store', () => ({
  useAppStore: () => ({ tableSize: 'small' }),
  useAuthStore: () => ({
    hasPermission: () => true,
    hasPermissionByCode: () => true,
  }),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/test/page', query: {} }),
}));

vi.mock('@/modules/sys/api/dict', () => ({
  type: vi.fn().mockResolvedValue({ data: [] }),
}));

vi.mock('@/utils', () => ({
  confirm: vi.fn(),
  exportNaive2Excel: vi.fn(),
  fileUrl: (value: unknown) => value,
  formatDateTime: (value: unknown) => String(value ?? ''),
  formatDateValue: (value: unknown) => String(value ?? ''),
  transformToArrayFormat: (value: unknown) => value,
  transformToTreeFormat: (value: unknown) => value,
}));

vi.mock('@/utils/request.ts', () => ({
  default: vi.fn(),
}));

vi.mock('@/components/basic/DynamicIcon.vue', () => ({
  default: { name: 'DynamicIcon', template: '<span />' },
}));

vi.mock('../../../form/form/AdvanceQueryForm.vue', () => ({
  default: defineComponent({
    name: 'AdvanceQueryForm',
    props: {
      alwaysVisibleQueryKeys: { type: Array, default: () => [] },
      defaultVisibleQueryKeys: { type: Array, default: () => [] },
      queryConfigKey: String,
      queryConfigOptions: { type: Array, default: () => [] },
      queryConfigurable: Boolean,
    },
    emits: ['query-field-visibility-change'],
    template: '<div><slot /><slot name="queryFormOperate" /></div>',
  }),
}));

vi.mock('@/components/basic/print/TemplatePrint.vue', () => ({
  default: { name: 'HTemplatePrint', template: '<span />' },
}));

const DataTableStub = defineComponent({
  name: 'NDataTable',
  props: {
    checkedRowKeys: { type: Array, default: () => [] },
    columns: { type: Array, default: () => [] },
    data: { type: Array, default: () => [] },
  },
  emits: [
    'update:checked-row-keys',
    'update:expanded-row-keys',
    'update:filters',
    'update:page',
    'update:page-size',
    'update:sorter',
  ],
  template: '<div data-testid="data-table" />',
});

type FetchData = ReturnType<typeof vi.fn>;

function createFetchData(): FetchData {
  return vi.fn().mockResolvedValue({
    data: {
      list: [],
      pageNum: 1,
      pageSize: 30,
      pages: 1,
      total: 0,
    },
  });
}

async function mountTable(
  options: {
    clearSelectionOnFetch?: boolean;
    columnConfigurable?: boolean;
    columns?: Array<Record<string, any>>;
    params?: Record<string, any>;
    queryConfigKey?: string;
    queryConfigurable?: boolean;
    showToolbar?: boolean;
    withQueryForm?: boolean;
  } = {},
) {
  const fetchData = createFetchData();
  const wrapper = shallowMount(PageDataTable, {
    props: {
      columns: options.columns ?? [],
      data: [{ id: 'old-resource' }],
      fetchData,
      initQuery: false,
      isAdd: false,
      isCopy: false,
      isDelete: false,
      isEdit: false,
      isExport: false,
      showHeader: false,
      showOperate: false,
      showToolbar: options.showToolbar ?? false,
      ...(options.params === undefined ? {} : { params: options.params }),
      ...(options.columnConfigurable === undefined ? {} : { columnConfigurable: options.columnConfigurable }),
      ...(options.queryConfigKey === undefined ? {} : { queryConfigKey: options.queryConfigKey }),
      ...(options.queryConfigurable === undefined ? {} : { queryConfigurable: options.queryConfigurable }),
      ...(options.clearSelectionOnFetch === undefined ? {} : { clearSelectionOnFetch: options.clearSelectionOnFetch }),
    },
    slots: options.withQueryForm ? { queryForm: '<div />' } : undefined,
    global: {
      stubs: {
        Card: { template: '<div><slot /></div>' },
        DataTable: DataTableStub,
        HFileDialogPreview: true,
        HFullScreen: true,
        'h-full-screen': true,
      },
    },
  });

  await flushPromises();
  return { fetchData, wrapper };
}

function dataTable(wrapper: VueWrapper) {
  return wrapper.findComponent(DataTableStub);
}

async function selectRow(wrapper: VueWrapper, id = 'resource-1') {
  dataTable(wrapper).vm.$emit('update:checked-row-keys', [id]);
  await nextTick();
  expect(wrapper.emitted('updateCheckedRowKeys')?.at(-1)?.[0]).toEqual([id]);
}

describe('PageDataTable selection scope', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('preserves checked rows across fetch scheduling by default', async () => {
    const { fetchData, wrapper } = await mountTable();
    await selectRow(wrapper);

    (wrapper.vm as any).handleQuery();
    await nextTick();

    expect(wrapper.emitted('updateCheckedRowKeys')?.at(-1)?.[0]).toEqual(['resource-1']);
    expect(fetchData).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchData).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1 }));

    wrapper.unmount();
  });

  it('clears checked rows immediately before a debounced query request when enabled', async () => {
    const { fetchData, wrapper } = await mountTable({ clearSelectionOnFetch: true });
    await selectRow(wrapper);

    (wrapper.vm as any).handleQuery();
    await nextTick();

    expect(wrapper.emitted('updateCheckedRowKeys')?.at(-1)?.[0]).toEqual([]);
    expect(fetchData).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(99);
    expect(fetchData).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1);
    expect(fetchData).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1 }));

    wrapper.unmount();
  });

  it('clears checked rows immediately and requests the new page on update:page', async () => {
    const { fetchData, wrapper } = await mountTable({ clearSelectionOnFetch: true });
    await selectRow(wrapper);
    expect(dataTable(wrapper).props('data')).toEqual([{ id: 'old-resource' }]);

    dataTable(wrapper).vm.$emit('update:page', 2);
    await nextTick();

    expect(wrapper.emitted('updateCheckedRowKeys')?.at(-1)?.[0]).toEqual([]);
    expect(dataTable(wrapper).props('data')).toEqual([]);
    expect(fetchData).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchData).toHaveBeenCalledTimes(1);
    expect(fetchData).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 2, pageSize: 30 }));

    wrapper.unmount();
  });

  it('filters visible columns without requesting page data again', async () => {
    const { fetchData, wrapper } = await mountTable({
      columns: [
        { key: 'name', title: '名称' },
        { key: 'era', title: '年代' },
        { key: 'operate', title: '操作', type: 'operate' },
      ],
      showToolbar: true,
    });
    await flushPromises();
    fetchData.mockClear();

    (wrapper.vm as any).updateVisibleColumnKeys(['name']);
    await nextTick();

    expect((dataTable(wrapper).props('columns') as Array<{ key?: string }>).map((column) => column.key)).toEqual([
      'name',
      'operate',
    ]);
    expect(fetchData).not.toHaveBeenCalled();

    wrapper.unmount();
  });

  it('shows asynchronous dynamic columns when the initial column list is empty', async () => {
    const { wrapper } = await mountTable({ columns: [], showToolbar: true });

    await wrapper.setProps({
      columns: [
        { key: 'dealerFullName', title: '服务商名称' },
        { key: 'dealerNum', title: '服务商编号' },
      ],
    });
    await flushPromises();

    expect((dataTable(wrapper).props('columns') as Array<{ key?: string }>).map((column) => column.key)).toEqual([
      'dealerFullName',
      'dealerNum',
    ]);

    wrapper.unmount();
  });

  it('keeps configured always-visible columns selected', async () => {
    const { wrapper } = await mountTable({
      columns: [
        { key: 'name', title: '名称' },
        { key: 'status', title: '状态' },
      ],
      showToolbar: true,
    });
    await wrapper.setProps({ alwaysVisibleColumnKeys: ['name'] });
    await flushPromises();

    (wrapper.vm as any).updateVisibleColumnKeys(['status']);
    await nextTick();

    expect((dataTable(wrapper).props('columns') as Array<{ key?: string }>).map((column) => column.key)).toEqual([
      'name',
      'status',
    ]);

    wrapper.unmount();
  });

  it('keeps all columns when column configuration is explicitly disabled', async () => {
    const { wrapper } = await mountTable({
      columnConfigurable: false,
      columns: [
        { key: 'name', title: '名称' },
        { key: 'status', title: '状态' },
      ],
      showToolbar: true,
    });

    (wrapper.vm as any).updateVisibleColumnKeys(['name']);
    await nextTick();

    expect((dataTable(wrapper).props('columns') as Array<{ key?: string }>).map((column) => column.key)).toEqual([
      'name',
      'status',
    ]);

    wrapper.unmount();
  });

  it('keeps query settings and its cache disabled by default', async () => {
    const { wrapper } = await mountTable({ withQueryForm: true });
    const queryForm = wrapper.findComponent({ name: 'AdvanceQueryForm' });

    expect(queryForm.props('queryConfigurable')).toBe(false);
    expect(queryForm.props('queryConfigKey')).toBeUndefined();

    wrapper.unmount();
  });

  it('clears an active hidden field before querying when query settings are explicitly enabled', async () => {
    const params = { keyword: '青铜器', status: null };
    const { fetchData, wrapper } = await mountTable({ params, queryConfigurable: true, withQueryForm: true });
    const queryForm = wrapper.findComponent({ name: 'AdvanceQueryForm' });

    expect(queryForm.props('queryConfigurable')).toBe(true);
    expect(queryForm.props('queryConfigKey')).toBe('__APP_PAGE_DATA_TABLE_QUERY_FIELDS__:/test/page');
    queryForm.vm.$emit('query-field-visibility-change', {
      visibleKeys: ['status'],
      hiddenKeys: ['keyword'],
      options: [
        { label: '关键字', value: 'keyword', clearPaths: ['keyword'] },
        { label: '状态', value: 'status', clearPaths: ['status'] },
      ],
      reason: 'user',
    });
    await nextTick();

    expect(params.keyword).toBeNull();
    expect(fetchData).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchData).toHaveBeenCalledTimes(1);
    expect(fetchData).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1 }));
    expect(fetchData.mock.calls[0][0]).not.toHaveProperty('keyword');

    wrapper.unmount();
  });

  it('allows multiple tables on the same route to override the generated query cache key', async () => {
    const { wrapper } = await mountTable({
      queryConfigKey: 'dashboard.collection-table',
      queryConfigurable: true,
      withQueryForm: true,
    });

    expect(wrapper.findComponent({ name: 'AdvanceQueryForm' }).props('queryConfigKey')).toBe(
      'dashboard.collection-table',
    );

    wrapper.unmount();
  });

  it('supports compound-field cleanup without a second page-level visibility implementation', async () => {
    const params = { acquireDateStart: 1, acquireDateEnd: 2 };
    const clearRange = vi.fn(() => true);
    const { fetchData, wrapper } = await mountTable({ params, queryConfigurable: true, withQueryForm: true });
    const queryForm = wrapper.findComponent({ name: 'AdvanceQueryForm' });

    queryForm.vm.$emit('query-field-visibility-change', {
      visibleKeys: ['keyword'],
      hiddenKeys: ['acquireDate'],
      options: [
        {
          label: '入藏日期',
          value: 'acquireDate',
          clearPaths: ['acquireDateStart', 'acquireDateEnd'],
          onClear: clearRange,
        },
      ],
      reason: 'user',
    });
    await nextTick();

    expect(params).toEqual({ acquireDateStart: null, acquireDateEnd: null });
    expect(clearRange).toHaveBeenCalledWith({ params });

    await vi.advanceTimersByTimeAsync(100);
    expect(fetchData).toHaveBeenCalledTimes(1);

    wrapper.unmount();
  });
});
