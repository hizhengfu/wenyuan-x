import type { MuseumSpatialDeviceVo } from '../typings';

export type SpatialMetricIconKind =
  | 'temperature'
  | 'humidity'
  | 'water'
  | 'fire'
  | 'air'
  | 'door'
  | 'light'
  | 'vibration'
  | 'pressure'
  | 'power'
  | 'weather'
  | 'sensor'
  | 'location';

const ICON_LABELS: Record<SpatialMetricIconKind, string> = {
  temperature: '温度',
  humidity: '湿度',
  water: '水浸',
  fire: '烟感/消防',
  air: '空气质量',
  door: '门磁',
  light: '光照',
  vibration: '振动',
  pressure: '压力',
  power: '电气',
  weather: '气象',
  sensor: '其他传感器',
  location: '下级位置',
};

const ICON_PATHS: Record<SpatialMetricIconKind, string> = {
  temperature: '<path d="M14 4v10.5a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"/><path d="M12 9v7"/>',
  humidity: '<path d="M12 2.8 6.6 9a7.2 7.2 0 1 0 10.8 0L12 2.8Z"/><path d="M9 15c.7 1.2 1.7 1.8 3 1.8"/>',
  water: '<path d="M12 3 7 8.8a6.7 6.7 0 1 0 10 0L12 3Z"/><path d="M4 20c2-1.3 4-1.3 6 0s4 1.3 6 0 3.3-1.3 4 0"/>',
  fire: '<path d="M12.5 2.5c.8 3.3-.8 4.7-2.2 6.3-1.5 1.7-2.7 3.3-1.5 5.7.8-1.8 2.1-2.6 3.4-3.7 2.1 1.9 3.3 3.8 2.7 6.2 1.8-1.2 3.1-3.2 3.1-5.7 0-3.8-2.4-6.7-5.5-8.8Z"/><path d="M9.2 20.5c-2.2-1-3.7-3-3.7-5.4"/>',
  air: '<path d="M4 8h9a2 2 0 1 0-2-2"/><path d="M3 12h15a2 2 0 1 1-2 2"/><path d="M5 16h7"/>',
  door: '<path d="M5 21V3h12v18"/><path d="M9 21V6h6v15"/><path d="M12 13h.01"/>',
  light: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  vibration: '<path d="M3 12h3l2-6 4 12 3-9 2 3h4"/>',
  pressure: '<path d="M5 19a8 8 0 1 1 14 0"/><path d="m12 13 4-4"/><path d="M8 19h8"/>',
  power: '<path d="m13 2-9 12h7l-1 8 9-12h-7l1-8Z"/>',
  weather: '<path d="M4 14a4 4 0 0 1 4-4 5 5 0 0 1 9.7 1.5A3.5 3.5 0 1 1 18 18H7a3 3 0 0 1-3-4Z"/><path d="M8 21v1M12 21v1M16 21v1"/>',
  sensor: '<circle cx="12" cy="12" r="3"/><path d="M6.3 6.3a8 8 0 0 0 0 11.4M17.7 6.3a8 8 0 0 1 0 11.4M3.5 3.5a12 12 0 0 0 0 17M20.5 3.5a12 12 0 0 1 0 17"/>',
  location: '<path d="M4 5h16v14H4z"/><path d="M8 9h2M14 9h2M8 13h2M14 13h2M10 19v-3h4v3"/>',
};

const RULES: Array<{ kind: SpatialMetricIconKind; patterns: RegExp[] }> = [
  { kind: 'water', patterns: [/WATER[_ -]?LEAK/i, /FLOOD/i, /LEAKAGE/i, /水浸|漏水/] },
  { kind: 'fire', patterns: [/SMOKE/i, /FIRE/i, /FLAME/i, /烟感|烟雾|火焰|消防/] },
  { kind: 'temperature', patterns: [/TEMPERATURE/i, /(^|[^A-Z])TEMP([^A-Z]|$)/i, /温度/] },
  { kind: 'humidity', patterns: [/HUMIDITY/i, /(^|[^A-Z])RH([^A-Z]|$)/i, /湿度/] },
  { kind: 'air', patterns: [/CO2/i, /PM2[._]?5/i, /PM10/i, /TVOC|VOC/i, /AIR[_ -]?QUALITY/i, /空气质量|二氧化碳/] },
  { kind: 'door', patterns: [/DOOR/i, /CONTACT/i, /门磁|门禁|开合/] },
  { kind: 'light', patterns: [/ILLUMINANCE/i, /LUX/i, /LIGHT/i, /光照|照度/] },
  { kind: 'vibration', patterns: [/VIBRATION/i, /ACCEL/i, /震动|振动|倾斜/] },
  { kind: 'pressure', patterns: [/PRESSURE/i, /BAROM/i, /压力|气压/] },
  { kind: 'power', patterns: [/VOLTAGE/i, /CURRENT/i, /POWER/i, /BATTERY/i, /电压|电流|功率|电量/] },
  { kind: 'weather', patterns: [/WIND/i, /RAIN/i, /WEATHER/i, /风速|风向|雨量|气象/] },
];

const DISPLAY_PRIORITY: Partial<Record<SpatialMetricIconKind, number>> = {
  temperature: 10,
  humidity: 20,
  air: 30,
  water: 40,
  fire: 40,
};

const DEVICE_HEALTH_PATTERN =
  /BATTERY|电池|设备电量|剩余电量|DEVICE[_ -]?VOLTAGE|SUPPLY[_ -]?VOLTAGE|设备电压|供电电压/i;

/** 核心监测指标优先，电池/供电等设备健康指标最后；同级保持接口原顺序。 */
export function sortSpatialMetrics<T extends { key: string; name?: string | null }>(
  metrics: readonly T[],
): T[] {
  return [...metrics].sort((left, right) => metricDisplayPriority(left) - metricDisplayPriority(right));
}

function metricDisplayPriority(metric: { key: string; name?: string | null }): number {
  const text = `${metric.key} ${metric.name || ''}`;
  if (DEVICE_HEALTH_PATTERN.test(text)) return 100;
  const kind = RULES.find((rule) => rule.patterns.some((pattern) => pattern.test(text)))?.kind;
  return kind ? DISPLAY_PRIORITY[kind] ?? 50 : 50;
}

export function resolveSpatialMetricIcon(device?: MuseumSpatialDeviceVo | null): SpatialMetricIconKind {
  return resolveSpatialMetricIcons(device, 1)[0];
}

/** 返回设备具备的主要指标类型；用于温湿度等多指标设备的组合图标。 */
export function resolveSpatialMetricIcons(
  device?: MuseumSpatialDeviceVo | null,
  limit = 2,
): SpatialMetricIconKind[] {
  if (!device) return ['sensor'];
  const metricText = (device.latestMetrics || []).flatMap((metric) => [metric.key, metric.name || '']).join(' ');
  const metricKinds = RULES.filter((rule) => rule.patterns.some((pattern) => pattern.test(metricText))).map(
    (rule) => rule.kind,
  );
  if (metricKinds.length) return metricKinds.slice(0, Math.max(1, limit));

  const fallbackText = [device.modelCode, device.code, device.name].filter(Boolean).join(' ');
  if (
    /温湿度|TEMPERATURE.{0,16}HUMIDITY|HUMIDITY.{0,16}TEMPERATURE|EM300[-_ ]?TH/i.test(fallbackText)
  ) {
    const temperatureHumidity: SpatialMetricIconKind[] = ['temperature', 'humidity'];
    return temperatureHumidity.slice(0, Math.max(1, limit));
  }
  const matched = RULES.find((rule) => rule.patterns.some((pattern) => pattern.test(fallbackText)));
  return [matched?.kind || 'sensor'];
}

export function spatialMetricIconLabel(kind: SpatialMetricIconKind): string {
  return ICON_LABELS[kind];
}

export function spatialMetricIconSvg(kind: SpatialMetricIconKind): string {
  return `<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICON_PATHS[kind]}</svg>`;
}
