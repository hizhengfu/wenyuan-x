import type { IngestRecordVo } from '../typings';
import { formatMetricValue } from './metricValue.ts';
import { metricDisplayName } from './metricName';

export function recordFieldText(value: unknown): string {
  if (value === undefined || value === null || value === '') return '--';
  if (typeof value === 'object') return JSON.stringify(value, null, 2);
  return String(value);
}

const alarmNames: Record<string, string> = {
  FIRE: '火警', FIRE_ALARM: '火警', FAULT: '故障', SHIELD: '屏蔽', SUPERVISION: '监管',
  START: '启动', FEEDBACK: '反馈', DELAY: '延时',
};
const severityNames: Record<string, string> = { NOTICE: '提示', WARNING: '警告', MAJOR: '严重', CRITICAL: '紧急' };
const qualityNames: Record<string, string> = { NORMAL: '正常', SUSPECT: '可疑', INVALID: '无效' };
const fireKeys = ['FIRE_TRANSMITTER_HEALTH', 'FIRE_SYSTEM_STATUS', 'FIRE_COMPONENT_STATUS', 'FIRE_TRANSMITTER_STATUS', 'FIRE_SYSTEM_ALARM'];
const attributeLabels: Record<string, string> = {
  systemType: '系统类型编码', systemTypeName: '系统类型', systemAddress: '系统地址',
  componentType: '部件类型编码', componentTypeName: '部件类型', componentAddress: '部件地址',
  zoneAddress: '区号', deviceAddress: '位号', description: '说明',
  status: '原始状态', messageType: '报文类型', recovery: '恢复标志',
  canonicalAlarmCode: '告警类型', message: '事件说明', severity: '告警等级', level: '事件等级',
  heartbeat: '心跳标志', protocolVersion: '协议版本', serialNumber: '报文流水号',
  sourceAddress: '来源地址', destinationAddress: '目标地址',
  operationFlags: '操作标志', operationFlag: '操作标志', operatorNumber: '操作员编号', operatorNo: '操作员编号',
  operatorId: '操作员编号', operator: '操作员编号', majorVersion: '主版本号', minorVersion: '子版本号',
  softwareVersion: '软件版本', systemTime: '传输装置系统时间',
};

export function fireRecordTime(value?: number | null) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '--';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '--';
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }).format(date);
}

export function recordTarget(record: IngestRecordVo): string {
  const a = record.attributes ?? {};
  return [
    a.systemTypeName, a.systemAddress != null ? `系统地址 ${a.systemAddress}` : undefined,
    a.zoneAddress != null ? `区号 ${a.zoneAddress}` : undefined,
    a.deviceAddress != null ? `位号 ${a.deviceAddress}` : undefined,
    a.componentAddress != null ? `部件地址 ${a.componentAddress}` : undefined,
  ].filter((value) => value !== undefined && value !== null && value !== '').join(' · ');
}

/** 泰和安 GB/T 26875.3 报文生成的记录：key 以 FIRE_ 开头且带协议类型号；其他设备的 FIRE_ 遥测不在此列。 */
function isFireProtocolRecord(record: IngestRecordVo) {
  return fireKeys.includes(record.key) || (record.key?.startsWith('FIRE_') && typeof record.attributes?.messageType === 'number');
}

/** 字段名以采集服务实际返回为准；协议类型 24、25、28 暂无现场样本，按常见命名依次查找，找不到时只保留原始属性。 */
function firstInteger(a: Record<string, unknown>, keys: string[], max: number) {
  for (const key of keys) {
    const value = a[key];
    if (value === null || value === undefined || (typeof value === 'string' && !value.trim())) continue;
    const number = Number(value);
    if (Number.isInteger(number) && number >= 0 && number <= max) return number;
  }
  return null;
}

const operationBits: Record<number, string> = { 0: '复位', 1: '消音', 2: '手动报警', 3: '警情清除', 4: '自检', 5: '查岗应答', 6: '测试' };

/** 类型 24 传输装置操作信息：操作标志按独立位定义，不套用状态位。 */
function fireOperationLabels(record: IngestRecordVo): string[] {
  const flags = firstInteger(record.attributes ?? {}, ['operationFlags', 'operationFlag', 'operation', 'flags', 'status'], 255);
  if (flags === null) return [];
  const labels = Object.entries(operationBits).filter(([bit]) => flags & (1 << Number(bit))).map(([, label]) => label);
  if (flags & 0x80) labels.push('未定义操作位 0x80');
  return labels;
}

const fireOperator = (record: IngestRecordVo) =>
  firstInteger(record.attributes ?? {}, ['operatorNumber', 'operatorNo', 'operatorId', 'operator'], 255);

function fireSoftwareVersion(record: IngestRecordVo) {
  const a = record.attributes ?? {};
  const major = firstInteger(a, ['majorVersion'], 255);
  const minor = firstInteger(a, ['minorVersion'], 255);
  if (major !== null && minor !== null) return `V${major}.${minor}`;
  return typeof a.softwareVersion === 'string' && a.softwareVersion.trim() ? a.softwareVersion.trim() : '';
}

function fireStatusLabels(record: IngestRecordVo): string[] {
  const a = record.attributes ?? {};
  const type = Number(a.messageType);
  // 只解释协议中已知的报文类型；不同类型不能共用状态位定义。
  const system = record.key === 'FIRE_SYSTEM_STATUS' && [1, 134].includes(type);
  const component = record.key === 'FIRE_COMPONENT_STATUS' && [2, 135].includes(type);
  // 传输装置状态暂无现场样本，按协议类型号识别，不依赖具体 key。
  const transmitter = isFireProtocolRecord(record) && [21, 136].includes(type);
  if ((!system && !component && !transmitter) || a.status == null || String(a.status).trim() === '' ||
      !['number', 'string'].includes(typeof a.status)) return [];
  const status = Number(a.status);
  if (!Number.isInteger(status) || status < 0 || status > (transmitter ? 255 : 65535)) return [];
  const bits: Record<number, string> = transmitter
    ? { 1: '火警', 2: '故障', 3: '主电故障', 4: '备电故障', 5: '监控中心通信故障', 6: '消防主机连接线路故障' }
    : { 1: '火警', 2: '故障', 3: '屏蔽', 4: '监管', 5: '启动', 6: '反馈', 7: '延时',
        ...(system ? { 8: '主电故障', 9: '备电故障', 10: '总线故障', 11: '手动状态', 12: '配置改变', 13: '系统复位' }
          : { 8: '电源故障' }) };
  const labels = Object.entries(bits).filter(([bit]) => status & (1 << Number(bit))).map(([, label]) => label);
  const knownMask = Object.keys(bits).reduce((mask, bit) => mask | (1 << Number(bit)), 1);
  const unknown = status & ~knownMask;
  if (unknown) labels.push(`未定义状态位 0x${unknown.toString(16).toUpperCase()}`);
  if (!labels.length) labels.push(status & 1 ? '正常运行模式' : '测试运行模式');
  return labels;
}

/**
 * 类型 21 / 136 传输装置运行状态：21 为当时状态，136 为所列状态已恢复。
 * 返回 null 表示不是传输装置状态记录；faults 只含火警、故障、电源及通信类位，不含运行模式。
 */
export function fireTransmitterState(record?: IngestRecordVo) {
  if (!record || !isFireProtocolRecord(record)) return null;
  const type = Number(record.attributes?.messageType);
  if (![21, 136].includes(type)) return null;
  const labels = fireStatusLabels(record);
  const recovery = type === 136 || record.attributes?.recovery === true;
  const faults = labels.filter((label) => !/运行模式$/.test(label));
  return { labels, recovery, faults: recovery ? [] : faults, recovered: recovery ? faults : [] };
}

export function fireRecordSummary(record?: IngestRecordVo, includeTarget = true): string {
  if (!record) return '--';
  const a = record.attributes ?? {};
  if (record.key === 'FIRE_TRANSMITTER_HEALTH') {
    return a.heartbeat === true ? '传输装置心跳上报' : `传输装置状态：${recordFieldText(record.stringValue)}`;
  }
  const type = Number(a.messageType);
  if (record.recordType !== 'ALARM_SIGNAL' && type === 24) {
    const labels = fireOperationLabels(record);
    const operator = fireOperator(record);
    return `传输装置操作${labels.length ? `：${labels.join('、')}` : '记录'}${operator === null ? '' : `（操作员 ${operator}）`}`;
  }
  if (record.recordType !== 'ALARM_SIGNAL' && type === 25) {
    const version = fireSoftwareVersion(record);
    return version ? `传输装置软件版本 ${version}` : '传输装置软件版本上报';
  }
  if (record.recordType !== 'ALARM_SIGNAL' && type === 28) return '传输装置系统时间上报';
  const recovery = a.recovery === true || [134, 135, 136].includes(Number(a.messageType));
  let description: string;
  if (record.recordType === 'ALARM_SIGNAL') {
    const code = recordFieldText(a.canonicalAlarmCode ?? record.stringValue?.split(':')[0]);
    const action = recovery || record.booleanValue === false ? '恢复上报'
      : record.booleanValue === true ? '触发上报' : '上报';
    description = `${alarmNames[code] ?? code}${action}`;
  } else {
    const labels = fireStatusLabels(record);
    description = labels.length ? `${labels.join('、')}${recovery ? '恢复上报' : '事件'}`
      : recordFieldText(a.message ?? record.stringValue ?? record.key);
  }
  const target = recordTarget(record);
  return includeTarget && target ? `${description}｜${target}` : description;
}

/** 通用记录只保留原值/来源说明，不套用消防状态含义。 */
export function recordSummary(record: IngestRecordVo): string {
  if (isFireProtocolRecord(record)) return fireRecordSummary(record, false);
  const value = formatMetricValue({ ...record, unit: record.metricUnit || record.unit });
  const message = record.attributes?.message;
  if (record.recordType !== 'TELEMETRY' && typeof message === 'string' && message.trim()) {
    return value === '--' ? message : `${message}｜${value}`;
  }
  return value;
}

export function recordAttributes(record: IngestRecordVo) {
  return Object.entries(record.attributes ?? {}).map(([key, value]) => ({
    key, label: attributeLabels[key] ?? key, value: recordFieldText(value),
  }));
}

export function fireRecordDetails(record: IngestRecordVo) {
  const a = record.attributes ?? {};
  return [
    ['事件摘要', fireRecordSummary(record)], ['设备', record.deviceName ?? record.deviceId],
    ['发生时间', fireRecordTime(record.occurredAt)], ['接收时间', fireRecordTime(record.receivedAt)],
    ['系统类型', a.systemTypeName ?? a.systemType], ['系统地址', a.systemAddress],
    ['部件类型', a.componentTypeName ?? a.componentType], ['部件地址', a.componentAddress],
    ['区号', a.zoneAddress], ['位号', a.deviceAddress], ['部件说明', a.description],
    ['原始状态', a.status], ['报文类型', a.messageType],
    ...(Number(a.messageType) === 24 ? [['操作内容', fireOperationLabels(record).join('、')], ['操作员编号', fireOperator(record)]] : []),
    ...(Number(a.messageType) === 25 ? [['软件版本', fireSoftwareVersion(record)]] : []),
    ['原始恢复标志', a.recovery === true ? '是' : a.recovery === false ? '否' : '--'],
    ['告警等级', severityNames[String(a.severity ?? a.level)] ?? a.severity ?? a.level],
    ['数据质量', qualityNames[record.quality] ?? record.quality],
    ['历史补录', record.backfill === true ? '是' : record.backfill === false ? '否' : '--'],
    ['原始指标', record.key], ['记录 ID', record.id], ['来源报文 ID', record.inboxId],
  ].map(([label, value]) => ({ label: String(label), value: recordFieldText(value) }));
}

export function recordDetails(record: IngestRecordVo) {
  const types: Record<string, string> = { TELEMETRY: '遥测数据', STATE_CHANGE: '状态变化', DEVICE_EVENT: '设备事件',
    ALARM_SIGNAL: '告警信号', COMMAND_RESULT: '指令结果', DEVICE_HEALTH: '设备健康' };
  return [
    ['设备', record.deviceName ?? record.deviceId], ['数据类型', types[record.recordType] ?? record.recordType],
    ['指标', metricDisplayName(record.key, record.metricName, record.sourceMetricName)], ['指标编码', record.key],
    ['厂商原名', record.sourceMetricName], ['厂商单位/状态', record.sourceUnit],
    ['对应目标', recordTarget(record)],
    ['发生时间', fireRecordTime(record.occurredAt)], ['接收时间', fireRecordTime(record.receivedAt)],
    ['设备位置', record.locationPath ?? record.locationName ?? record.locationId],
    ['网关', record.gatewayName ?? record.gatewayId],
    ['原始数值', record.numericValue], ['单位', record.metricUnit ?? record.unit],
    ['原始文本值', record.stringValue], ['原始布尔值', record.booleanValue],
    ['多字段数值', record.values], ['数据质量', qualityNames[record.quality] ?? record.quality],
    ['历史补录', record.backfill === true ? '是' : record.backfill === false ? '否' : '--'],
    ['记录 ID', record.id], ['来源报文 ID', record.inboxId],
    ['来源消息 ID', record.sourceMessageId], ['来源记录 ID', record.sourceRecordId],
  ].map(([label, value]) => ({ label: String(label), value: recordFieldText(value) }));
}
