/** @vitest-environment happy-dom */
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, h, KeepAlive, nextTick, ref } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PageDataTable from '../PageDataTable.vue';

vi.mock('sortablejs', () => ({ default: { create: vi.fn() } }));
vi.mock('@/store', () => ({ useAppStore: () => ({ tableSize: 'small' }), useAuthStore: () => ({ hasPermission: () => true, hasPermissionByCode: () => true }) }));
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/collection/catalog-card', query: {} }) }));
vi.mock('@/modules/sys/api/dict', () => ({ type: vi.fn().mockResolvedValue({ data: [] }) }));
vi.mock('@/utils', () => ({ confirm: vi.fn(), exportNaive2Excel: vi.fn(), fileUrl: (v: unknown) => v, formatDateTime: (v: unknown) => String(v ?? ''), formatDateValue: (v: unknown) => String(v ?? ''), transformToArrayFormat: (v: unknown) => v, transformToTreeFormat: (v: unknown) => v }));
vi.mock('@/utils/request.ts', () => ({ default: vi.fn() }));
vi.mock('@/components/basic/DynamicIcon.vue', () => ({ default: { name: 'DynamicIcon', template: '<span />' } }));
vi.mock('../../../form/form/AdvanceQueryForm.vue', () => ({ default: defineComponent({ template: '<div><slot /><slot name="queryFormOperate" /></div>' }) }));
vi.mock('@/components/basic/print/TemplatePrint.vue', () => ({ default: { name: 'HTemplatePrint', template: '<span />' } }));

const DataTableStub = defineComponent({
  name: 'NDataTable', props: ['data', 'loading', 'pagination', 'columns', 'checkedRowKeys'],
  template: '<div data-testid="table">{{ JSON.stringify(data) }}</div>',
});
const global = { stubs: { Card: { template: '<div><slot /></div>' }, DataTable: DataTableStub, HFileDialogPreview: true, HFullScreen: true, 'h-full-screen': true } };
const options = { columns: [], data: [{ id: 'settled' }], params: { keyword: 'A' }, initQuery: false, isAdd: false, isCopy: false, isDelete: false, isEdit: false, isExport: false, showHeader: false, showOperate: false, showToolbar: false };
const cleanup: Array<() => void> = [];
function deferred() {
  let resolve!: (value: any) => void, reject!: (reason: any) => void;
  const promise = new Promise<any>((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
function response(id: string, total = 1) { return { data: { list: [{ id }], pages: total, total, pageNum: 1, pageSize: 30 } }; }
function table(wrapper: VueWrapper) { return wrapper.findComponent(DataTableStub); }
function snapshot(wrapper: VueWrapper) { return { data: table(wrapper).props('data'), pagination: { ...table(wrapper).props('pagination') }, loading: table(wrapper).props('loading'), emits: JSON.stringify(wrapper.emitted()) }; }
async function create(fetchData: ReturnType<typeof vi.fn>) {
  const wrapper = shallowMount(PageDataTable, { props: { ...options, fetchData }, global });
  cleanup.push(() => wrapper.unmount()); await flushPromises(); return wrapper;
}
async function query(wrapper: VueWrapper, keyword: string, dispatch = true) {
  await wrapper.setProps({ params: { keyword } }); (wrapper.vm as any).handleQuery(); await nextTick();
  if (dispatch) await vi.advanceTimersByTimeAsync(100);
}
describe('PageDataTable owns only its current query response', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { cleanup.splice(0).forEach(fn => fn()); vi.clearAllTimers(); vi.useRealTimers(); vi.restoreAllMocks(); });

  it('keeps the successful B rows, pagination and emitted result when A completes last', async () => {
    const a = deferred(), b = deferred(); const fetch = vi.fn().mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise); const wrapper = await create(fetch);
    await query(wrapper, 'A'); await query(wrapper, 'B');
    b.resolve(response('B', 7)); await flushPromises(); const current = snapshot(wrapper);
    a.resolve(response('A', 99)); await flushPromises();
    expect(table(wrapper).props('data')).toEqual([{ id: 'B' }]); expect(snapshot(wrapper)).toEqual(current);
    expect(fetch.mock.calls.map(x => x[0].keyword)).toEqual(['A', 'B']); expect(wrapper.emitted('loadData')).toHaveLength(1);
  });
  it('invalidates A when B is scheduled, before the B debounce dispatches', async () => {
    const a = deferred(), b = deferred(); const fetch = vi.fn().mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise); const wrapper = await create(fetch);
    await query(wrapper, 'A'); await query(wrapper, 'B', false);
    a.resolve(response('A')); await flushPromises(); expect(wrapper.emitted('loadData')).toBeUndefined(); expect(table(wrapper).props('data')).toEqual([]);
    await vi.advanceTimersByTimeAsync(100); b.resolve(response('B')); await flushPromises(); expect(table(wrapper).props('data')).toEqual([{ id: 'B' }]);
  });
  it('does not let an old rejection clear the current loading state or log a current error', async () => {
    const a = deferred(), b = deferred(); const fetch = vi.fn().mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise); const wrapper = await create(fetch); const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    await query(wrapper, 'A'); await query(wrapper, 'B'); expect(table(wrapper).props('loading')).toBe(true);
    a.reject(new Error('old A failure')); await flushPromises(); expect(table(wrapper).props('loading')).toBe(true); expect(error).not.toHaveBeenCalled();
    b.resolve(response('B')); await flushPromises(); expect(table(wrapper).props('loading')).toBe(false); expect(table(wrapper).props('data')).toEqual([{ id: 'B' }]);
  });
  it('cancels a queued query when the actual component unmounts', async () => {
    const fetch = vi.fn().mockResolvedValue(response('A')); const wrapper = await create(fetch);
    await query(wrapper, 'A', false); wrapper.unmount(); await vi.advanceTimersByTimeAsync(100); expect(fetch).not.toHaveBeenCalled();
  });
  it('ignores an in-flight response after unmount, including loadData and update:data events', async () => {
    const a = deferred(); const wrapper = await create(vi.fn().mockReturnValue(a.promise)); await query(wrapper, 'A'); wrapper.unmount();
    a.resolve(response('A')); await flushPromises(); expect(wrapper.emitted('loadData')).toBeUndefined(); expect(wrapper.emitted('update:data')).toBeUndefined();
  });
  it('resumes an unfinished query once on real KeepAlive return while discarding its old response', async () => {
    const visible = ref(true), a = deferred(), b = deferred(); const fetch = vi.fn().mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
    const Host = defineComponent({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(PageDataTable, { ...options, fetchData: fetch }) : null }) });
    const host = mount(Host, { global }); cleanup.push(() => host.unmount()); await flushPromises(); const wrapper = host.findComponent(PageDataTable);
    (wrapper.vm as any).handleQuery(); await vi.advanceTimersByTimeAsync(100); visible.value = false; await nextTick(); visible.value = true; await nextTick();
    a.resolve(response('A')); await flushPromises(); expect(wrapper.emitted('loadData')).toBeUndefined(); expect(table(wrapper).props('data')).toEqual([]);
    await vi.advanceTimersByTimeAsync(100); expect(fetch).toHaveBeenCalledTimes(2); b.resolve(response('B', 3)); await flushPromises(); expect(table(wrapper).props('data')).toEqual([{ id: 'B' }]); expect(table(wrapper).props('loading')).toBe(false);
  });
  it('does not auto-query an unqueried or already settled KeepAlive cache', async () => {
    const visible = ref(true); const fetch = vi.fn().mockResolvedValue(response('settled-current'));
    const Host = defineComponent({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(PageDataTable, { ...options, fetchData: fetch }) : null }) });
    const host = mount(Host, { global }); cleanup.push(() => host.unmount()); await flushPromises(); const wrapper = host.findComponent(PageDataTable);
    visible.value = false; await nextTick(); visible.value = true; await nextTick(); await vi.advanceTimersByTimeAsync(100); expect(fetch).not.toHaveBeenCalled();
    (wrapper.vm as any).handleQuery(); await vi.advanceTimersByTimeAsync(100); await flushPromises(); expect(fetch).toHaveBeenCalledTimes(1);
    visible.value = false; await nextTick(); visible.value = true; await nextTick(); await vi.advanceTimersByTimeAsync(100); expect(fetch).toHaveBeenCalledTimes(1); expect(table(wrapper).props('data')).toEqual([{ id: 'settled-current' }]);
  });
  it('preserves the current error behavior and allows an explicit retry', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('current failure')).mockResolvedValueOnce(response('retry')); const wrapper = await create(fetch); const error = vi.spyOn(console, 'error').mockImplementation(() => {});
    await query(wrapper, 'A'); await flushPromises(); expect(error).toHaveBeenCalledTimes(1); expect(table(wrapper).props('loading')).toBe(false);
    await query(wrapper, 'A'); await flushPromises(); expect(table(wrapper).props('data')).toEqual([{ id: 'retry' }]); expect(wrapper.emitted('loadData')).toHaveLength(1);
  });
});
