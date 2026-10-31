// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import Maintenance from '../../views/data-maintenance.vue';

const m = vi.hoisted(() => ({
  allowed: true,
  venues: vi.fn(),
  metrics: vi.fn(),
  devices: vi.fn(),
  previewDuplicates: vi.fn(),
  cleanDuplicates: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
}));
vi.mock('@/store', () => ({ useAuthStore: () => ({ hasPermission: () => m.allowed }) }));
vi.mock('@/utils', () => ({ formatDateTime: (value: unknown) => String(value) }));
vi.mock('@/modules/sys/components/UserText.vue', () => ({
  default: { props: ['value'], template: '<span>{{value}}</span>' },
}));
vi.mock('file-saver', () => ({ saveAs: vi.fn() }));
vi.mock('naive-ui', async (original) => ({
  ...(await original<typeof import('naive-ui')>()),
  useDialog: () => ({ warning: m.warning }),
  useMessage: () => ({ success: m.success }),
}));
vi.mock('../../api/gateway', () => ({ gatewayApi: { venues: m.venues } }));
vi.mock('../../api/ingest', () => ({ ingestApi: { standardMetrics: m.metrics, recordFilterOptions: m.devices } }));
vi.mock('../../api/dataMaintenance', () => ({
  maintenanceApi: {
    previewDuplicates: m.previewDuplicates,
    cleanDuplicates: m.cleanDuplicates,
    operations: vi.fn().mockResolvedValue({ data: { list: [], total: 0 } }),
  },
}));

const Button = defineComponent({
  props: ['disabled'],
  setup:
    (props, { slots }) =>
    () =>
      h('button', { disabled: props.disabled }, slots.default?.()),
});
const Select = defineComponent({
  props: ['value', 'options', 'disabled', 'placeholder'],
  emits: ['update:value'],
  setup:
    (props, { emit }) =>
    () =>
      h(
        'select',
        {
          value: props.value,
          disabled: props.disabled,
          'aria-label': props.placeholder,
          onChange: (event: Event) => emit('update:value', (event.target as HTMLSelectElement).value),
        },
        (props.options || []).map((option: { value: string; label: string }) =>
          h('option', { value: option.value }, option.label),
        ),
      ),
});
const Input = defineComponent({
  props: ['value', 'placeholder', 'disabled'],
  emits: ['update:value'],
  setup:
    (props, { emit }) =>
    () =>
      h('input', {
        value: props.value,
        placeholder: props.placeholder,
        disabled: props.disabled,
        onInput: (event: Event) => emit('update:value', (event.target as HTMLInputElement).value),
      }),
});
const wrappers: VueWrapper[] = [];
function button(w: VueWrapper, label: string) {
  return w.findAll('button').find((item) => item.text() === label)!;
}
async function open() {
  const wrapper = shallowMount(Maintenance, {
    global: {
      renderStubDefaultSlot: true,
      stubs: { NButton: Button, Button, NSelect: Select, Select, NInput: Input, Input, HPageDataTable: true },
    },
  });
  wrappers.push(wrapper);
  await flushPromises();
  return wrapper;
}
beforeEach(() => {
  vi.clearAllMocks();
  m.allowed = true;
  m.venues.mockResolvedValue({ data: [{ id: 'ORG', name: '场馆' }] });
  m.metrics.mockResolvedValue({ data: [{ code: 'TEMP', name: '温度', recordType: 'TELEMETRY' }] });
  m.devices.mockResolvedValue({ data: { devices: [{ id: 'DEVICE', name: '传感器', code: '0001' }] } });
  m.previewDuplicates.mockResolvedValue({
    data: { previewHash: 'clean-preview', scanned: 5, removable: 2, protected: 1, conflicts: 1, groups: [] },
  });
  m.cleanDuplicates.mockResolvedValue({ data: { operationId: 'CLEAN', affected: 2, skipped: 1 } });
});
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount());
});

it('requires device metric range and confirmation for cleaning then invalidates the executed preview', async () => {
  const w = await open();
  await w.find('select[aria-label="请选择设备"]').setValue('DEVICE');
  await w.find('select[aria-label="请选择指标"]').setValue('TEMP');
  await w.find('input[placeholder="填写清洗原因"]').setValue('清理重复报文');
  await button(w, '预览重复数据').trigger('click');
  await flushPromises();
  expect(w.text()).toContain('可清理 2 条，受引用保护 1 条，冲突 1 组');
  expect(m.cleanDuplicates).not.toHaveBeenCalled();
  await button(w, '清理预览中的重复数据').trigger('click');
  await m.warning.mock.calls[0][0].onPositiveClick();
  await flushPromises();
  expect(m.cleanDuplicates).toHaveBeenCalledWith(
    expect.objectContaining({ orgId: 'ORG', deviceId: 'DEVICE', metricCode: 'TEMP' }),
    'clean-preview',
    '清理重复报文',
  );
  expect(button(w, '清理预览中的重复数据').attributes('disabled')).toBeDefined();
});

it('does not fetch operational data or offer mutations without management permission', async () => {
  m.allowed = false;
  const w = await open();
  expect(w.text()).toContain('需要采集管理权限');
  expect(m.venues).not.toHaveBeenCalled();
  expect(m.devices).not.toHaveBeenCalled();
  expect(w.find('input[type="file"]').exists()).toBe(false);
});
