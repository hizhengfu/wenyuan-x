// @vitest-environment happy-dom
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { defineComponent, h, KeepAlive, ref } from 'vue';
import { flushPromises, mount, shallowMount, type VueWrapper } from '@vue/test-utils';
import VersionDetail from '../AlarmRuleVersionDetail.vue';
import VersionLink from '../AlarmRuleVersionLink.vue';
import EventReference from '../AlarmEventReference.vue';
import EventDetail from '../AlarmEventDetail.vue';
import DeviceReference from '../DeviceReference.vue';
import LocationReference from '../LocationReference.vue';
import { alarmConditionText } from '../../utils/alarmRulePresentation';
const m = vi.hoisted(() => ({ version: vi.fn(), device: vi.fn(), location: vi.fn(), event: vi.fn() }));
vi.mock('../../api/ingest', () => ({
  ingestApi: {
    alarmRuleVersion: m.version,
    deviceReference: m.device,
    locationReference: m.location,
    alarmDetail: m.event,
  },
}));
vi.mock('@/modules/sys/components/UserText.vue', () => ({
  default: { name: 'UserText', props: ['value', 'name'], template: '<span>{{name}}</span>' },
}));
vi.mock('@/utils', () => ({ formatDateTime: (value: unknown) => String(value) }));
const Form = defineComponent({
  props: ['show'],
  setup:
    (props, { slots }) =>
    () =>
      props.show ? h('section', slots.default?.()) : null,
});
const Button = defineComponent({
  setup:
    (_, { slots }) =>
    () =>
      h('button', slots.default?.()),
});
const wrappers: VueWrapper[] = [];
const global = { renderStubDefaultSlot: true, stubs: { HPageForm: Form, NButton: Button, Button, HDictText: true } };
function open(component: any, props: Record<string, any>) {
  const w = shallowMount(component, { props, global });
  wrappers.push(w);
  return w;
}
function deferred() {
  let resolve!: (value: any) => void;
  const promise = new Promise<any>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function version(id = 'v1', extra: Record<string, any> = {}) {
  return {
    id,
    ruleId: 'rule',
    orgId: 'venue',
    ruleName: '库房温度预警',
    ruleCode: 'TEMP_HIGH',
    versionNo: 1,
    managedBy: 'MANUAL',
    versionStatus: 'SUPERSEDED',
    ruleType: 'VALUE_CONDITION',
    effectiveFrom: 1,
    effectiveTo: 2,
    triggerCount: 2,
    recoveryCount: 3,
    includeBackfill: false,
    currentPublishedVersionId: 'v2',
    standardRef: '专家依据',
    variants: [
      {
        id: 'variant',
        name: '库房',
        branches: [
          {
            id: 'branch',
            name: '温度过高',
            severity: 'WARNING',
            conditions: [
              { id: 'condition', recordKey: 'TEMP', fieldCode: 'VALUE', operator: 'GT', thresholdValue: 30 },
            ],
          },
        ],
      },
    ],
    ...extra,
  };
}
beforeEach(() => {
  m.version.mockResolvedValue({ data: version() });
  m.device.mockResolvedValue({
    data: {
      id: 'device',
      name: '温度探头',
      code: 'D1',
      modelCode: 'TEMP',
      locationPath: '一号馆 / 库房',
      externalDeviceId: 'vendor',
    },
  });
});
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.resetAllMocks();
});
it('按指定历史版本读取，展示原条件并标出本事件分支', async () => {
  const w = open(VersionDetail, { show: true, versionId: 'v1', ruleId: 'rule', orgId: 'venue', branchId: 'branch' });
  await flushPromises();
  expect(m.version).toHaveBeenCalledWith('v1');
  expect(m.version).toHaveBeenCalledTimes(1);
  expect(w.text()).toContain('库房温度预警 · V1');
  expect(w.text()).toContain('TEMP / VALUE 大于 30');
  expect(w.text()).toContain('本事件命中分支');
  expect(w.findComponent(Form).props()).not.toHaveProperty('width');
});
it.each([{ id: 'another' }, { ruleId: 'another-rule' }, { orgId: 'another-venue' }])(
  '拒绝错版本、错规则或错馆的回执 %j',
  async (extra) => {
    m.version.mockResolvedValue({ data: version('v1', extra) });
    const w = open(VersionDetail, { show: true, versionId: 'v1', ruleId: 'rule', orgId: 'venue' });
    await flushPromises();
    expect(w.text()).toContain('关联不一致');
    expect(w.text()).not.toContain('大于 30');
  },
);
it('切换和关闭窗口后忽略晚到的版本数据', async () => {
  const old = deferred();
  m.version.mockReturnValueOnce(old.promise);
  const w = open(VersionDetail, { show: true, versionId: 'v1' });
  m.version.mockResolvedValue({ data: version('v2', { versionNo: 2 }) });
  await w.setProps({ versionId: 'v2' });
  await flushPromises();
  old.resolve({ data: version() });
  await flushPromises();
  expect(w.text()).toContain('V2');
  expect(w.text()).not.toContain('V1');
  const late = deferred();
  m.version.mockReturnValueOnce(late.promise);
  await w.setProps({ versionId: 'v3' });
  await w.setProps({ show: false });
  late.resolve({ data: version('v3') });
  await flushPromises();
  expect(w.text()).toBe('');
});
it('读取失败后可以重试且不会保留旧条件', async () => {
  m.version.mockRejectedValueOnce(Error('无权查阅该规则'));
  const w = open(VersionDetail, { show: true, versionId: 'v1' });
  await flushPromises();
  expect(w.text()).toContain('无权查阅');
  expect(w.text()).not.toContain('大于 30');
  await w.find('button').trigger('click');
  await flushPromises();
  expect(w.text()).toContain('大于 30');
});
it('版本名称作为链接，点击时携带精确关联且切换后关闭', async () => {
  const w = open(VersionLink, {
    versionId: 'v1',
    ruleId: 'rule',
    orgId: 'venue',
    branchId: 'branch',
    ruleName: '温度规则',
    versionNo: 1,
  });
  expect(w.text()).toBe('温度规则 · V1');
  expect(m.version).not.toHaveBeenCalled();
  await w.find('button').trigger('click');
  expect(w.findComponent(VersionDetail).props()).toMatchObject({
    show: true,
    versionId: 'v1',
    ruleId: 'rule',
    orgId: 'venue',
    branchId: 'branch',
  });
  await w.setProps({ versionId: 'v2' });
  expect(w.findComponent(VersionDetail).props('show')).toBe(false);
});
it('来源事件入口只读并绑定来源事件编号', async () => {
  const w = open(EventReference, { eventId: 'source-A', label: '温度异常' });
  await w.find('button').trigger('click');
  expect(w.findComponent(EventDetail).props()).toMatchObject({ show: true, eventId: 'source-A', canManage: false });
  await w.setProps({ eventId: 'source-B' });
  expect(w.findComponent(EventDetail).props('show')).toBe(false);
});
it('关联设备按编号读取，保留入口快照名称，且切换后不显示旧资料', async () => {
  const w = open(DeviceReference, { deviceId: 'device', name: '触发时的设备名称' });
  expect(m.device).not.toHaveBeenCalled();
  await w.find('button').trigger('click');
  await flushPromises();
  expect(m.device).toHaveBeenCalledWith('device');
  expect(w.text()).toContain('一号馆 / 库房');
  expect(w.text()).toContain('触发时的设备名称');
  await w.setProps({ deviceId: 'next' });
  expect(w.findComponent(Form).props('show')).toBe(false);
  expect(w.text()).not.toContain('一号馆 / 库房');
});
it('设备编号不一致时显示明确错误', async () => {
  m.device.mockResolvedValue({ data: { id: 'wrong', name: '错误设备' } });
  const w = open(DeviceReference, { deviceId: 'device' });
  await w.find('button').trigger('click');
  await flushPromises();
  expect(w.text()).toContain('关联设备不一致');
  expect(w.text()).not.toContain('错误设备');
});
it('设备直报没有规则关联入口，处理人使用只读人员组件', async () => {
  m.event.mockResolvedValue({
    data: {
      event: {
        id: 'event',
        source: 'DEVICE',
        title: '设备直报',
        sourceCategory: 'VIDEO',
        deviceId: 'device',
        orgId: 'venue',
        occurrenceCount: 1,
        startedAt: 1,
        lastOccurredAt: 1,
      },
      handlingRecords: [
        {
          id: 'record',
          action: 'CONFIRMED',
          reason: '已确认',
          operatorId: 'person',
          operatorName: '张三',
          occurredAt: 1,
        },
      ],
    },
  });
  const w = open(EventDetail, { show: true, eventId: 'event', canManage: false });
  await flushPromises();
  const link = w.findAllComponents(VersionLink);
  expect(link[0].props('placeholder')).toBe('设备直报');
  expect(w.text()).toContain('视频预警');
  expect(link[0].props('versionId')).toBeUndefined();
  expect(w.findComponent({ name: 'UserText' }).props()).toMatchObject({ value: 'person', name: '张三' });
});
it('条件展示保留零值、文本和双边区间', () => {
  expect(
    alarmConditionText({ recordKey: 'TEMP', operator: 'BETWEEN', thresholdValue: 0, thresholdValue2: 5 }),
  ).toContain('0 ～ 5');
  expect(alarmConditionText({ operator: 'EQ', expectedValue: 'OFF' })).toContain('等于 OFF');
  expect(alarmConditionText({ operator: 'GT', thresholdValue: 300 }, 'DEVICE_OFFLINE')).toBe(
    '无数据时长（秒） 大于 300',
  );
});

it('位置入口保留触发快照，打开后显示当前资料并忽略关闭后的回执', async () => {
  const pending = deferred();
  m.location.mockReturnValueOnce(pending.promise);
  const w = open(LocationReference, { locationId: 'place', name: '触发时的库房路径' });
  expect(w.text()).toBe('触发时的库房路径');
  await w.find('button').trigger('click');
  expect(m.location).toHaveBeenCalledWith('place');
  await w.setProps({ locationId: 'another' });
  pending.resolve({ data: { id: 'place', name: '当前库房', code: 'L1', path: '移动后的路径', type: 'SPACE' } });
  await flushPromises();
  expect(w.text()).not.toContain('移动后的路径');
  expect(w.findComponent(Form).props('show')).toBe(false);
});

it('切换 KeepAlive 页面时关闭关联弹窗，恢复页面不自动重开', async () => {
  const active = ref(true);
  const Host = defineComponent({
    setup: () => () =>
      h(KeepAlive, {}, () =>
        active.value ? h(DeviceReference, { deviceId: 'device', name: '设备入口' }) : h('aside', '其他页'),
      ),
  });
  const w = mount(Host, { global });
  wrappers.push(w);
  await w.find('button').trigger('click');
  await flushPromises();
  expect(w.findComponent(Form).props('show')).toBe(true);
  active.value = false;
  await flushPromises();
  expect(w.text()).toBe('其他页');
  active.value = true;
  await flushPromises();
  expect(w.findComponent(Form).props('show')).toBe(false);
  expect(w.text()).not.toContain('一号馆 / 库房');
});

it.each([
  ['31.1250', '℃', '31.1250 ℃'],
  ['0', '%', '0 %'],
  ['OFFLINE', null, 'OFFLINE'],
  [null, '℃', '--'],
])('事件详情显示首次触发值及其单位 %s %s', async (triggerValue, triggerUnit, expected) => {
  m.event.mockResolvedValue({ data: { event: { id: 'unit-event', source: 'RULE', triggerValue, triggerUnit }, handlingRecords: [] } });
  const w = open(EventDetail, { show: true, eventId: 'unit-event', canManage: false });
  await flushPromises();
  const item = w.findAllComponents({ name: 'DescriptionsItem' }).find((component) => component.props('label') === '触发值');
  expect(item?.text()).toBe(expected);
});

it.each([false, true])('外部完成沿用已处理状态并显示来源结果，无需本地流程（附带外部流程=%s）', async (withFlow) => {
  m.event.mockResolvedValue({ data: {
    event: { id: 'external-completed', source: 'DEVICE', deviceId: 'device', lifecycleStatus: 'OPEN' },
    handling: { status: 'PROCESSED', sourceCode: 'VIDEO', externalId: 'TICKET-1',
      ...(withFlow ? { flowInstanceId: 'FLOW-1', flowStatus: 'COMPLETED' } : {}),
      operatorId: 'external-person', operatorName: '外部张三', occurredAt: 123,
      reason: '外部核查已完成', details: { result: '现场复核通过' } },
    handlingRecords: [{ id: 'r1', action: 'PROCESSED', reason: '外部核查已完成', sourceCode: 'VIDEO',
      operatorId: 'external-person', operatorName: '外部张三', occurredAt: 123 }],
  } });
  const w = open(EventDetail, { show: true, eventId: 'external-completed', canManage: true });
  await flushPromises();
  expect(w.text()).toContain('外部处置完成');
  expect(w.text()).toContain('TICKET-1');
  expect(w.text()).toContain('现场复核通过');
  expect(w.text()).toContain('外部张三');
  expect(w.text()).toContain('VIDEO');
  if (withFlow) expect(w.text()).toContain('FLOW-1');
  else expect(w.text()).not.toContain('外部流程');
  const descriptions = w.findAllComponents({ name: 'DescriptionsItem' });
  expect(descriptions.find((item) => item.props('label') === '处置时间')?.text()).toBe('123');
  expect(w.findAllComponents({ name: 'FormItem' }).find((item) => item.props('label') === '当前操作')?.text()).toBe('补充说明');
  expect(w.findComponent({ name: 'UserText' }).exists()).toBe(false);
  const dictionaryProps = w.findAllComponents({ name: 'HDictText' }).map((item) => item.attributes());
  expect(dictionaryProps).toEqual(expect.arrayContaining([
    expect.objectContaining({ type: 'museum_alarm_handling_status', value: 'PROCESSED' }),
    expect.objectContaining({ type: 'museum_alarm_lifecycle_status', value: 'OPEN' }),
  ]));
});
