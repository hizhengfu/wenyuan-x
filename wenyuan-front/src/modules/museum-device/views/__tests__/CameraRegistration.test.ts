// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import DevicePage from '../device.vue';
const mocks = vi.hoisted(() => ({ register: vi.fn(), batch: vi.fn(), save: vi.fn(), locations: vi.fn() }));
vi.mock('../../api/gateway', () => ({ gatewayApi: { page: vi.fn(async () => ({ data: { list: [{ id: 'GW', name: '视频来源网关' }] } })) } }));
vi.mock('../../api/ingest', () => ({ ingestApi: {
  catalog: vi.fn(async () => ({ data: { protocols: [], codecs: [], deviceModels: [], builtInConnectors: [], standardMetrics: [] } })),
  registerDeviceCandidate: mocks.register, registerDeviceCandidates: mocks.batch, saveDevice: mocks.save,
  locations: mocks.locations,
} }));
vi.mock('@/utils', () => ({ formatDateTime: (value: unknown) => String(value) }));
vi.mock('@/utils/dict', async () => { const { ref } = await import('vue'); return { getDictLabel: () => '', useDict: () => ({ options: ref([]) }) }; });
vi.mock('naive-ui', async (original) => ({ ...await original<typeof import('naive-ui')>(),
  useMessage: () => ({ success: vi.fn(), warning: vi.fn() }), useDialog: () => ({ warning: vi.fn() }),
}));
const Form = defineComponent({
  inheritAttrs: false,
  props: ['show', 'handleSubmit'],
  setup: (props, { slots }) => () => props.show ? h('section', { class: 'registration' }, [slots.default?.(),
    h('button', { class: 'submit', onClick: () => props.handleSubmit() }, '保存')]) : null,
});
const Item = defineComponent({ props: ['label'], setup: (props, { slots }) => () => h('label', [props.label, slots.default?.()]) });
const Input = defineComponent({ props: ['value', 'readonly'], emits: ['update:value'],
  setup: (props, { emit }) => () => h('input', { value: props.value, readonly: props.readonly,
    onInput: (event: Event) => emit('update:value', (event.target as HTMLInputElement).value) }),
});
const Select = defineComponent({ props: ['value', 'options'], emits: ['update:value'],
  setup: (props, { emit }) => () => h('select', { value: props.value,
    onChange: (event: Event) => emit('update:value', (event.target as HTMLSelectElement).value) },
  (props.options || []).map((option: any) => h('option', { value: option.value }, option.label))),
});
let wrapper: VueWrapper<any>;
const candidate = { id: 'C', orgId: 'ORG', gatewayId: 'GW', kind: 'EXTERNAL_DEVICE', name: '展厅摄像头',
  externalDeviceId: 'Cam:01', protocolCode: 'EXTERNAL_PUSH_V1', suggestedModelCode: 'VIDEO_CAMERA_V1', suggestedModelName: '视频平台摄像头' };
beforeEach(async () => {
  vi.clearAllMocks();
  mocks.locations.mockResolvedValue({ data: [{ id: 'L1', code: '1', name: '一号展厅' }, { id: 'L2', code: '2', name: '二号展厅' }] });
  mocks.register.mockResolvedValue({ data: { deviceId: 'D', replayPending: false } });
  mocks.batch.mockResolvedValue({ data: { deviceIds: ['D'], deviceCount: 1, replayPending: false } });
  wrapper = shallowMount(DevicePage, { global: { renderStubDefaultSlot: true, stubs: {
    HPageForm: Form, NFormItem: Item, FormItem: Item, NInput: Input, Input, NSelect: Select, Select,
    HPageDataTable: { inheritAttrs: false, template: '<main><slot/><slot name="toolbar"/></main>' },
    HPageView: true, HDictSelect: true, HDictText: true,
  } } });
  await flushPromises();
});
afterEach(() => wrapper.unmount());
it('registers camera using only displayed source details name and manually selected location', async () => {
  await wrapper.vm.openCandidateRegistration(candidate); await flushPromises();
  const form = wrapper.get('.registration');
  expect(form.text()).toContain('视频平台摄像头'); expect(form.text()).toContain('视频来源网关');
  expect(form.text()).not.toContain('业务角色'); expect(form.text()).not.toContain('实例配置');
  expect(form.findAll('select')).toHaveLength(1);
  await form.get('select').setValue('L2');
  await form.findAll('input')[0].setValue('人工维护名称');
  await form.get('.submit').trigger('click'); await flushPromises();
  expect(mocks.register).toHaveBeenCalledWith('C', expect.objectContaining({ name: '人工维护名称', locationId: 'L2', kind: 'EXTERNAL_DEVICE' }));
  expect(mocks.register.mock.calls[0][1]).not.toHaveProperty('rfidHardwareSerial');
  expect(mocks.register.mock.calls[0][1]).not.toHaveProperty('rfidAntennaCount');
  expect(mocks.register.mock.calls[0][1]).not.toHaveProperty('instanceConfig');
});
it('batch camera registration does not send RFID fields', async () => {
  await wrapper.vm.openBatchRegistration([candidate]); await flushPromises();
  wrapper.vm.batchLocationId = 'L1'; await wrapper.vm.submitBatchRegistration();
  expect(mocks.batch).toHaveBeenCalledWith({ candidateIds: ['C'], locationId: 'L1', businessRoles: undefined, rfidAntennaCount: undefined });
});
it('editing registered camera keeps its source identity without requiring hardware model selection', async () => {
  await wrapper.vm.onEdit({ ...candidate, id: 'D', modelCode: 'VIDEO_CAMERA_V1', code: 'VIDEO-code',
    locationId: 'L1', instanceConfig: '{"sourceId":"camera"}' }); await flushPromises();
  const form = wrapper.get('.registration');
  expect(form.findAll('select')).toHaveLength(1);
  await form.findAll('input')[0].setValue('新名称'); await form.get('.submit').trigger('click'); await flushPromises();
  expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({ name: '新名称', instanceConfig: '{"sourceId":"camera"}', modelCode: 'VIDEO_CAMERA_V1' }));
});
it('physical candidates retain model and protocol registration fields', async () => {
  await wrapper.vm.openCandidateRegistration({ ...candidate, kind: 'PHYSICAL', suggestedModelCode: 'MODEL', protocolCode: 'MQTT' });
  await flushPromises();
  expect(wrapper.get('.registration').text()).toContain('业务角色');
  expect(wrapper.get('.registration').text()).toContain('实例配置');
  expect(wrapper.get('.registration').findAll('select').length).toBeGreaterThan(1);
});
