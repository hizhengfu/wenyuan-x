// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, shallowMount, type VueWrapper } from '@vue/test-utils';
import { defineComponent, h, onMounted } from 'vue';
import RiskEventHandling from '../RiskEventHandling.vue';

const mocks = vi.hoisted(() => ({
  risk: vi.fn(),
  action: vi.fn(),
  success: vi.fn(),
  select: true,
  manage: true,
}));
vi.mock('../../api', () => ({
  safetyApi: { risk: mocks.risk, riskAction: mocks.action },
  unwrapResponse: (value: unknown) => value,
}));
vi.mock('@/store/modules/auth', () => ({
  useAuthStore: () => ({
    hasPermission: (permission: string) => (permission.includes('SELECT') ? mocks.select : mocks.manage),
  }),
}));
vi.mock('@/store', () => ({ useAppStore: () => ({ uploading: false }) }));
vi.mock('@/utils', () => ({ formatDateTime: () => '2026-09-30' }));
vi.mock('../../../museum-device/utils/venueCatalog', () => ({
  loadVenueCatalog: () => Promise.resolve([{ id: 'venue', name: '测试馆' }]),
}));
vi.mock('@/components/form/field/upload/upload.vue', () => ({ default: { name: 'UploadFiles', template: '<div />' } }));
vi.mock('@/modules/sys/components/form/field/UserSelect.vue', () => ({
  default: { name: 'UserSelect', template: '<div />' },
}));
vi.mock('../../../museum-device/components/MetricSeriesChart.vue', () => ({
  default: { name: 'MetricSeriesChart', template: '<div />' },
}));
vi.mock('../RiskLinkagePanel.vue', () => ({ default: { name: 'RiskLinkagePanel', template: '<div />' } }));
vi.mock('naive-ui', async (importOriginal) => ({
  ...(await importOriginal<typeof import('naive-ui')>()),
  useMessage: () => ({ success: mocks.success }),
}));

const PageForm = defineComponent({
  props: { show: Boolean },
  setup:
    (props, { slots }) =>
    () =>
      props.show ? h('section', [slots.default?.(), h('footer', slots['form-action']?.())]) : null,
});
const Button = defineComponent({
  props: { disabled: Boolean },
  setup:
    (props, { slots }) =>
    () =>
      h('button', { disabled: props.disabled }, slots.default?.()),
});
const Empty = defineComponent({
  props: { description: String },
  setup: (props) => () => h('p', props.description),
});
const Input = defineComponent({
  props: { value: String },
  emits: ['update:value'],
  setup:
    (props, { emit }) =>
    () =>
      h('textarea', {
        value: props.value || '',
        onInput: (event: Event) => emit('update:value', (event.target as HTMLTextAreaElement).value),
      }),
});
const Linkages = defineComponent({
  name: 'RiskLinkagePanel',
  props: ['riskId', 'risk'],
  emits: ['pending', 'changed', 'show-origin'],
  setup: (_, { emit, expose }) => {
    expose({ refresh: async () => {} });
    onMounted(() => emit('pending', 0));
    return () => h('div');
  },
});
const Card = defineComponent({ template: '<div><slot name="header-extra"/><slot/></div>' });
const Collapse = defineComponent({ name: 'TestCollapse', props: ['expandedNames'], template: '<div><slot/></div>' });
const mounted: VueWrapper[] = [];
function open(id = 'A', props: Record<string, unknown> = {}) {
  const wrapper = shallowMount(RiskEventHandling, {
    props: { id, show: true, ...props },
    global: {
      renderStubDefaultSlot: true,
      directives: { download: {} },
      stubs: {
        HPageForm: PageForm,
        NCard: Card,
        Card,
        NCollapse: Collapse,
        Collapse,
        NButton: Button,
        Button,
        Empty,
        NEmpty: Empty,
        NInput: Input,
        Input,
        RiskLinkagePanel: Linkages,
        HDictText: true,
      },
    },
  });
  mounted.push(wrapper);
  return wrapper;
}
function event(id: string, step = 0) {
  return {
    id,
    title: `事件 ${id}`,
    orgId: 'venue',
    deviceId: `device-${id}`,
    domain: 'ELECTRICITY',
    level: 'WARNING',
    status: 'PROCESSING',
    sourceType: 'MANUAL',
    assigneeId: 'person',
    logs: [],
    workflow: {
      step,
      nextAction: step === 2 ? 'PROCESSED' : 'CONFIRMED',
      triggerLogId: `trigger-${id}`,
      pendingLinkages: 0,
      blockedReason: null,
    },
  };
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
beforeEach(() => {
  vi.clearAllMocks();
  mocks.select = true;
  mocks.manage = true;
  mocks.risk.mockImplementation(async (id: string) => event(id));
  mocks.action.mockResolvedValue(undefined);
});
afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()));

describe('ID-based risk event handling', () => {
  it('loads on opening without a route or caller-provided event data', async () => {
    const wrapper = open('A', { show: false });
    expect(mocks.risk).not.toHaveBeenCalled();
    await wrapper.setProps({ show: true });
    await flushPromises();
    expect(mocks.risk).toHaveBeenCalledWith('A');
    expect(wrapper.text()).toContain('事件 A');
    expect(wrapper.text()).toContain('测试馆');
    expect(wrapper.get('footer button').text()).toBe('确认风险');
  });

  it('does not fetch without an ID or viewing permission and supports read-only callers', async () => {
    const empty = open('');
    await flushPromises();
    expect(mocks.risk).not.toHaveBeenCalled();
    expect(empty.text()).toContain('请提供风险事件 ID');
    mocks.select = false;
    const forbidden = open('A');
    await flushPromises();
    expect(mocks.risk).not.toHaveBeenCalled();
    expect(forbidden.text()).toContain('无权查看风险事件');
    mocks.select = true;
    const readonly = open('A', { readonly: true });
    await flushPromises();
    expect(readonly.text()).toContain('事件 A');
    expect(readonly.find('footer button').exists()).toBe(false);
  });

  it('ignores an earlier ID response that arrives after the current event', async () => {
    const first = deferred<ReturnType<typeof event>>();
    mocks.risk.mockImplementation((id: string) => (id === 'A' ? first.promise : Promise.resolve(event(id))));
    const wrapper = open('A');
    await wrapper.setProps({ id: 'B' });
    await flushPromises();
    first.resolve(event('A'));
    await flushPromises();
    expect(wrapper.text()).toContain('事件 B');
    expect(wrapper.text()).not.toContain('事件 A');
  });

  it('invalidates a closed session and reloads the same ID when reopened', async () => {
    const first = deferred<ReturnType<typeof event>>();
    mocks.risk.mockReturnValueOnce(first.promise).mockResolvedValue(event('A'));
    const wrapper = open('A');
    await wrapper.setProps({ show: false });
    first.resolve(event('A'));
    await flushPromises();
    expect(wrapper.text()).toBe('');
    await wrapper.setProps({ show: true });
    await flushPromises();
    expect(mocks.risk).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('事件 A');
  });

  it('reports the saved ID without replacing or reopening another event', async () => {
    const saving = deferred<void>();
    mocks.action.mockReturnValue(saving.promise);
    const wrapper = open('A');
    await flushPromises();
    await wrapper.get('textarea').setValue('确认 A 的模拟风险');
    await wrapper.get('footer button').trigger('click');
    expect(mocks.action).toHaveBeenCalledWith(
      'A',
      expect.objectContaining({ expectedTriggerLogId: 'trigger-A', remark: '确认 A 的模拟风险' }),
    );
    await wrapper.setProps({ id: 'B' });
    await flushPromises();
    saving.resolve();
    await flushPromises();
    expect(wrapper.emitted('saved')).toEqual([['A']]);
    expect(mocks.risk.mock.calls.map(([id]) => id)).toEqual(['A', 'B']);
    expect(wrapper.text()).toContain('事件 B');
    expect(wrapper.get('textarea').element.value).toBe('');
  });

  it('retains the form after a failed action on the same event', async () => {
    mocks.action.mockRejectedValueOnce(new Error('本轮状态已更新'));
    const wrapper = open('A');
    await flushPromises();
    await wrapper.get('textarea').setValue('现场核查说明');
    await wrapper.get('footer button').trigger('click');
    await flushPromises();
    expect(wrapper.text()).toContain('本轮状态已更新');
    expect(wrapper.get('textarea').element.value).toBe('现场核查说明');
    expect(wrapper.emitted('saved')).toBeUndefined();
  });

  it('unlocks the next action after the final linkage completes while preserving the typed result', async () => {
    mocks.risk.mockResolvedValue(event('A', 2));
    const wrapper = open('A');
    await flushPromises();
    await wrapper.get('textarea').setValue('本事件复测正常');
    const linkages = wrapper.getComponent(Linkages);
    linkages.vm.$emit('pending', 1);
    await flushPromises();
    expect(wrapper.get('footer button').attributes()).toHaveProperty('disabled');
    linkages.vm.$emit('pending', 0);
    linkages.vm.$emit('changed');
    await flushPromises();
    expect(wrapper.get('footer button').attributes()).not.toHaveProperty('disabled');
    expect(wrapper.get('textarea').element.value).toBe('本事件复测正常');
    await wrapper.get('footer button').trigger('click');
    await flushPromises();
    expect(mocks.action).toHaveBeenCalledWith(
      'A',
      expect.objectContaining({ action: 'PROCESSED', result: '本事件复测正常' }),
    );
  });
  it('refreshes recovery without discarding the current step form, and resets it on a new trigger', async () => {
    mocks.risk.mockResolvedValue(event('A', 2));
    const wrapper = open('A');
    await flushPromises();
    await wrapper.get('textarea').setValue('本轮处置结果');
    const refresh = wrapper.findAll('button').find((button) => button.text() === '刷新进度')!;
    await refresh.trigger('click');
    await flushPromises();
    expect(wrapper.get('textarea').element.value).toBe('本轮处置结果');
    const next = event('A', 2);
    next.workflow.triggerLogId = 'new-trigger';
    mocks.risk.mockResolvedValue(next);
    await refresh.trigger('click');
    await flushPromises();
    expect(wrapper.get('textarea').element.value).toBe('');
    await wrapper.get('textarea').setValue('新轮结果');
    await wrapper.get('footer button').trigger('click');
    await flushPromises();
    expect(mocks.action).toHaveBeenCalledWith(
      'A',
      expect.objectContaining({ expectedTriggerLogId: 'new-trigger', result: '新轮结果' }),
    );
  });
  it('expands history when a task links to its originating event record', async () => {
    const wrapper = open('A');
    await flushPromises();
    wrapper.getComponent(Linkages).vm.$emit('show-origin', 'origin-log');
    await flushPromises();
    expect(wrapper.getComponent(Collapse).props('expandedNames')).toContain('history');
  });
});

vi.mock('@/modules/sys/components/UserText.vue', () => ({ default: { name: 'UserText', props: ['value', 'placeholder'], template: '<span />' } }));

it('来源告警和规则版本关联到当前风险的来源记录', async () => {
  mocks.risk.mockResolvedValue({ ...event('A'), sourceAlarm: { eventId: 'source-A', ruleId: 'rule-A', ruleVersionId: 'version-A', title: '温度异常', fieldCode: 'VALUE' } });
  const w = open(); await flushPromises();
  expect(w.findComponent({ name: 'AlarmEventReference' }).props('eventId')).toBe('source-A');
  expect(w.findComponent({ name: 'AlarmRuleVersionLink' }).props()).toMatchObject({ versionId: 'version-A', ruleId: 'rule-A', orgId: 'venue' });
});
