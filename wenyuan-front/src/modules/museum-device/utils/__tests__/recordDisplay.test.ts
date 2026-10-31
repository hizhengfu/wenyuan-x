import assert from 'node:assert/strict';
import { test } from 'vitest';
import type { IngestRecordVo } from '../../typings';
import { recordSummary, recordTarget, recordAttributes, recordDetails, fireRecordDetails, fireTransmitterState } from '../recordDisplay';

const base: IngestRecordVo = {
  id: '2100391390165622784', orgId: 'org', deviceId: 'device', inboxId: '2100391388143968256',
  sourceMessageId: 'message', sourceRecordId: 'record', recordType: 'DEVICE_EVENT',
  key: 'FIRE_COMPONENT_STATUS', stringValue: 'COMPONENT_STATUS_RECOVERY', values: {},
  attributes: { messageType: 135, recovery: true, status: 64, systemAddress: 1,
    zoneAddress: 1001, deviceAddress: 139, componentAddress: 65601675 },
  occurredAt: 1789607304000, receivedAt: 1789607304974, quality: 'NORMAL', backfill: false,
};

test('消防状态按报文类型和位集合解释，恢复不等于当前正常', () => {
  assert.equal(recordSummary(base), '反馈恢复上报');
  assert.equal(recordSummary({ ...base, attributes: { ...base.attributes, status: 32 } }), '启动恢复上报');
  assert.equal(recordSummary({ ...base, attributes: { ...base.attributes, status: 96 } }), '启动、反馈恢复上报');
  assert.match(recordTarget(base), /系统地址 1.*区号 1001.*位号 139.*65601675/);
  assert.equal(recordSummary({ ...base, key: 'FIRE_SYSTEM_STATUS', attributes: { messageType: 1, status: 8192 } }), '系统复位事件');
  assert.equal(recordSummary({ ...base, key: 'FIRE_TRANSMITTER_STATUS', attributes: { messageType: 21, status: 16 } }), '备电故障事件');
  assert.equal(recordSummary({ ...base, attributes: { messageType: 2, status: 16 } }), '监管事件');
  assert.equal(recordSummary({ ...base, recordType: 'ALARM_SIGNAL', key: 'FIRE_SYSTEM_ALARM', booleanValue: true,
    attributes: { canonicalAlarmCode: 'FIRE', messageType: 2, status: 3 } }), '火警触发上报');
  assert.equal(recordSummary({ ...base, recordType: 'ALARM_SIGNAL', key: 'FIRE_SYSTEM_ALARM', booleanValue: false,
    attributes: { canonicalAlarmCode: 'FIRE', messageType: 135 } }), '火警恢复上报');
  for (const status of [undefined, null, '', ' ', false, -1, 65536]) {
    assert.equal(recordSummary({ ...base, attributes: { ...base.attributes, status } }), 'COMPONENT_STATUS_RECOVERY');
  }
  assert.match(recordSummary({ ...base, attributes: { ...base.attributes, status: 512 } }), /未定义状态位 0x200/);
  // 类型 24 按操作标志解释（位 6 = 测试），不能套用部件状态位（位 6 = 反馈）。
  assert.equal(recordSummary({ ...base, attributes: { messageType: 24, status: 64 } }), '传输装置操作：测试');
});

test('其他设备的数值、布尔值和文本保持原语义；未知属性不丢失', () => {
  const ordinary = { ...base, key: 'OTHER_STATUS', attributes: { status: 64, custom: { enabled: false, value: 0 } } };
  assert.equal(recordSummary({ ...ordinary, booleanValue: false }), '否');
  assert.equal(recordSummary({ ...ordinary, booleanValue: true }), '是');
  assert.equal(recordSummary({ ...ordinary, stringValue: 'OPEN' }), 'OPEN');
  assert.equal(recordSummary({ ...ordinary, recordType: 'TELEMETRY', numericValue: 0, unit: '℃' }), '0 ℃');
  assert.equal(recordSummary({ ...ordinary, recordType: 'TELEMETRY', numericValue: 23.4567, unit: '℃' }), '23.457 ℃');
  const fields = recordAttributes(ordinary);
  assert.equal(fields.find((field) => field.key === 'status')?.value, '64');
  assert.deepEqual(JSON.parse(fields.find((field) => field.key === 'custom')!.value), { enabled: false, value: 0 });
  const details = Object.fromEntries(recordDetails({ ...ordinary, booleanValue: false, numericValue: 0 })
    .map(({ label, value }) => [label, value]));
  assert.equal(details['原始布尔值'], 'false');
  assert.equal(details['原始数值'], '0');
  assert.equal(details['记录 ID'], base.id);
  assert.equal(details['发生时间'], '2026-09-17 09:08:24');
  assert.equal(details['历史补录'], '否');
  assert.deepEqual(recordAttributes({ ...ordinary, attributes: {} }), []);
});

test('按厂家协议示例解释传输装置操作、状态、软件版本和系统时间；其他设备的 FIRE_ 遥测不受影响', () => {
  const event = { ...base, key: 'FIRE_TRANSMITTER_OPERATION', stringValue: 'TRANSMITTER_OPERATION' };
  // 协议附录：操作标志 04 = 手动报警，操作员编号默认 0。
  assert.equal(recordSummary({ ...event, attributes: { messageType: 24, operationFlags: 4, operatorNumber: 0 } }), '传输装置操作：手动报警（操作员 0）');
  assert.equal(recordSummary({ ...event, attributes: { messageType: 24, status: 3 } }), '传输装置操作：复位、消音');
  assert.equal(recordSummary({ ...event, attributes: { messageType: 24 } }), '传输装置操作记录');
  assert.equal(recordSummary({ ...event, attributes: { messageType: 24, status: 128 } }), '传输装置操作：未定义操作位 0x80');
  const labels = Object.fromEntries(recordAttributes({ ...event, attributes: { messageType: 24, operationFlags: 4, operatorNumber: 0 } })
    .map(({ label, value }) => [label, value]));
  assert.deepEqual([labels['操作标志'], labels['操作员编号']], ['4', '0']);
  const details = Object.fromEntries(fireRecordDetails({ ...event, attributes: { messageType: 24, operationFlags: 4, operatorNumber: 0 } })
    .map(({ label, value }) => [label, value]));
  assert.deepEqual([details['操作内容'], details['操作员编号']], ['手动报警', '0']);
  assert.equal(recordSummary({ ...event, attributes: { messageType: 25, majorVersion: 1, minorVersion: 5 } }), '传输装置软件版本 V1.5');
  assert.equal(recordSummary({ ...event, attributes: { messageType: 25 } }), '传输装置软件版本上报');
  assert.equal(recordSummary({ ...event, attributes: { messageType: 28 } }), '传输装置系统时间上报');

  // 协议附录：传输装置状态 09 = 正常监控 + 主电故障；类型 136 携带同一位表示已恢复。
  const transmitter = { ...base, key: 'FIRE_TRANSMITTER_STATE', stringValue: 'TRANSMITTER_STATUS', attributes: { messageType: 21, status: 9 } };
  assert.equal(recordSummary(transmitter), '主电故障事件');
  assert.deepEqual(fireTransmitterState(transmitter)?.faults, ['主电故障']);
  const recovered = fireTransmitterState({ ...transmitter, attributes: { messageType: 136, status: 9 } });
  assert.deepEqual([recovered?.faults, recovered?.recovered], [[], ['主电故障']]);
  assert.deepEqual(fireTransmitterState({ ...transmitter, attributes: { messageType: 21, status: 1 } })?.faults, []);
  assert.equal(fireTransmitterState(base), null);

  // 其他设备的消防温感遥测没有协议类型号，保持数值显示。
  const telemetry = { ...base, key: 'FIRE_TEMPERATURE', recordType: 'TELEMETRY' as const, numericValue: 36.5, unit: '℃', attributes: {} };
  assert.equal(recordSummary(telemetry), '36.5 ℃');
  assert.equal(fireTransmitterState(telemetry), null);
});
