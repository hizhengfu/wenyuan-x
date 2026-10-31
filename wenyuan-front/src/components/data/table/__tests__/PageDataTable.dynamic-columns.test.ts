/** @vitest-environment happy-dom */
import { flushPromises, mount } from '@vue/test-utils';
import { NCard, NConfigProvider, NDataTable } from 'naive-ui';
import { defineComponent, h, nextTick, shallowRef } from 'vue';
import { afterEach, expect, it, vi } from 'vitest';
import PageDataTable from '../PageDataTable.vue';

vi.mock('sortablejs', () => ({ default: { create: vi.fn() } }));
vi.mock('@/store', () => ({
  useAppStore: () => ({ tableSize: 'small' }),
  useAuthStore: () => ({ hasPermission: () => true, hasPermissionByCode: () => true }),
}));
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/dealer/listCertificate', query: {} }) }));
vi.mock('@/modules/sys/api/dict', () => ({ type: vi.fn().mockResolvedValue({ data: { NORTH: '北区' } }) }));
vi.mock('@/utils', () => ({
  confirm: vi.fn(),
  exportNaive2Excel: vi.fn(),
  fileUrl: (value: unknown) => value,
  formatDateTime: (value: unknown) => String(value ?? ''),
  formatDateValue: (value: unknown) => String(value ?? ''),
  transformToArrayFormat: (value: unknown) => value,
  transformToTreeFormat: (value: unknown) => value,
}));
vi.mock('@/utils/request.ts', () => ({ default: vi.fn() }));
vi.mock('@/components/basic/DynamicIcon.vue', () => ({ default: { template: '<span />' } }));
vi.mock('../../../form/form/AdvanceQueryForm.vue', () => ({ default: { template: '<div />' } }));
vi.mock('@/components/basic/print/TemplatePrint.vue', () => ({ default: { template: '<span />' } }));

const cleanups: Array<() => void> = [];
afterEach(() => cleanups.splice(0).reverse().forEach(cleanup => cleanup()));

it('renders asynchronous API leaf columns with empty children and preserves nested group headers', async () => {
  const columns = shallowRef<Array<Record<string, any>>>([]);
  const rows = shallowRef<Array<Record<string, any>>>([]);
  const Host = defineComponent({
    setup: () => () => h(NConfigProvider, null, {
      default: () => h(PageDataTable, {
        columns: columns.value,
        data: rows.value,
        pagination: false,
        initQuery: false,
        showHeader: false,
        showToolbar: false,
        showOperate: false,
        isAdd: false,
        isEdit: false,
        isDelete: false,
        isCopy: false,
        isExport: false,
      }),
    }),
  });
  const wrapper = mount(Host, {
    attachTo: document.body,
    global: { components: { NCard, NDataTable }, stubs: { HFileDialogPreview: true } },
  });
  cleanups.push(() => wrapper.unmount());
  await flushPromises();
  expect(wrapper.findComponent(NDataTable).exists()).toBe(true);

  // The API serializes children: [] on leaves, including grouped and dictionary columns.
  columns.value = [
    { title: '服务商名称', key: 'dealerFullName', children: [] },
    { title: '渠道区域', key: 'channelRegional', dictType: 'service_area', children: [] },
    {
      title: '产品家族', key: 'family', children: [
        {
          title: '产品系列', key: 'series', children: [
            { title: '授权编号', key: 'certificateNum', children: [] },
            { title: '有效期', key: 'validDate', children: [] },
          ],
        },
      ],
    },
  ];
  rows.value = [{
    id: 'dealer-1',
    dealerFullName: '测试服务商',
    channelRegional: 'NORTH',
    certificateNum: 'CERT-001',
    validDate: '2026-12-31',
  }];
  await flushPromises();
  await nextTick();

  const headers = wrapper.findAll('.n-data-table-thead th');
  expect(headers.map(header => header.text())).toEqual([
    '服务商名称', '渠道区域', '产品家族', '产品系列', '授权编号', '有效期',
  ]);
  expect.soft(headers.find(header => header.text() === '产品家族')!.attributes('colspan')).toBe('2');
  expect.soft(headers.find(header => header.text() === '产品系列')!.attributes('colspan')).toBe('2');
  expect.soft(headers.find(header => header.text() === '服务商名称')!.attributes('rowspan')).toBe('3');
  expect.soft(wrapper.findAll('.n-data-table-tbody > tr > td').map(cell => cell.text())).toEqual([
    '测试服务商', '北区', 'CERT-001', '2026-12-31',
  ]);
});
