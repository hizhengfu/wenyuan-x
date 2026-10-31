// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, h } from 'vue';
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import Panel from '../RiskLinkagePanel.vue';
const m = vi.hoisted(() => ({ list: vi.fn(), action: vi.fn() }));
vi.mock('../../api', () => ({
  safetyApi: { riskLinkages: m.list, riskLinkageAction: m.action },
  unwrapResponse: (r: unknown) => r,
}));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/store', () => ({ useAppStore: () => ({ uploading: false }) }));
vi.mock('@/utils', () => ({ formatDateTime: () => 'now' }));
vi.mock('@/components/form/field/upload/upload.vue', () => ({ default: { template: '<div/>' } }));
vi.mock('@/modules/sys/components/form/field/UserSelect.vue', () => ({ default: { template: '<div/>' } }));
const Button = defineComponent({
  props: ['disabled', 'type'],
  setup:
    (props, { slots }) =>
    () =>
      h('button', { disabled: props.disabled, 'data-type': props.type }, slots.default?.()),
});
const Card = defineComponent({ template: '<div><slot name="header"/><slot name="header-extra"/><slot/></div>' });
const Form = defineComponent({ props: ['show'], template: '<div v-if="show"><slot/><slot name="form-action"/></div>' });
const wrappers: VueWrapper[] = [];
const task = (extra = {}) => ({
  id: 'task',
  type: 'INSPECTION',
  status: 'PENDING',
  assigneeId: 'user',
  instruction: '检查事件设备',
  planId: null,
  revision: 0,
  attachments: [],
  logs: [],
  ...extra,
});
function open() {
  const w = shallowMount(Panel, {
    props: {
      riskId: 'risk',
      orgId: 'org',
      closed: false,
      canManage: true,
      risk: {
        id: 'risk',
        title: '风险',
        deviceId: 'device',
        logs: [],
        workflow: { canCreateLinkage: true, linkageTypes: ['INSPECTION'] },
      } as any,
    },
    global: {
      renderStubDefaultSlot: true,
      directives: { download: {} },
      stubs: { HPageForm: Form, NButton: Button, Button, NCard: Card, Card, HDictText: true },
    },
  });
  wrappers.push(w);
  return w;
}
const button = (w: VueWrapper, label: string) => w.findAll('button').find((b) => b.text() === label)!;
beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  m.list.mockResolvedValue([task()]);
  m.action.mockResolvedValue(undefined);
});
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.useRealTimers();
});
it('巡检缺少计划时明确配置入口并阻止开始执行', async () => {
  const w = open();
  await flushPromises();
  expect(button(w, '配置巡检计划').attributes('data-type')).toBe('primary');
  expect(button(w, '开始执行').attributes()).toHaveProperty('disabled');
  expect(w.text()).toContain('请先选择覆盖本事件的巡检计划');
});
it('责任人和计划齐备后可以开始，提交期间其他动作禁用', async () => {
  m.list.mockResolvedValue([task({ planId: 'plan' })]);
  let resolve!: () => void;
  m.action.mockReturnValueOnce(new Promise<void>((done) => (resolve = done)));
  const w = open();
  await flushPromises();
  expect(button(w, '开始执行').attributes()).not.toHaveProperty('disabled');
  await button(w, '开始执行').trigger('click');
  expect(m.action).toHaveBeenCalledWith('risk', 'task', { action: 'START', expectedRevision: 0 });
  expect(button(w, '调整配置').attributes()).toHaveProperty('disabled');
  resolve();
  await flushPromises();
});
it('任务关联入口通知事件组件展开对应记录', async () => {
  m.list.mockResolvedValue([task({ eventContext: { originLogId: 'origin', title: '原风险' } })]);
  const w = open();
  await flushPromises();
  await button(w, '处置阶段发起 · now').trigger('click');
  expect(w.emitted('show-origin')).toEqual([['origin']]);
});
it('自动读取发现下游任务完成时更新事件进度', async () => {
  m.list
    .mockResolvedValueOnce([task({ status: 'PROCESSING', externalId: 'inspection' })])
    .mockResolvedValue([task({ status: 'COMPLETED', revision: 1 })]);
  const w = open();
  await flushPromises();
  expect(w.emitted('pending')).toEqual([[null], [1]]);
  await vi.advanceTimersByTimeAsync(10000);
  await flushPromises();
  expect(w.emitted('pending')).toEqual([[null], [1], [0]]);
  expect(w.emitted('changed')).toEqual([[]]);
});

vi.mock('@/modules/sys/components/UserText.vue', () => ({ default: { name: 'UserText', props: ['value', 'placeholder'], template: '<span />' } }));

vi.mock('@/modules/museum-device/api/ingest', () => ({ ingestApi: {} }));
