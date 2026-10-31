import type { EnvironmentCardVo } from '../typings';
export function hasLocationAssociation(basis: string | null | undefined): boolean {
  return basis === 'CURRENT_DEVICE_ASSOCIATION' || basis === 'RECORDED_CONFIGURATION';
}

export function locationAssociationLabel(basis: string | null | undefined): string {
  if (basis === 'CURRENT_DEVICE_ASSOCIATION') return '设备当前关联位置';
  if (basis === 'RECORDED_CONFIGURATION') return '采样时安装配置';
  return '历史位置待核实';
}

export const sampleStatusLabels: Record<string, string> = {
  STALE: '采样已过期',
  LOCATION_UNKNOWN: '采样位置待核实',
  LOCATION_MISMATCH: '移位前读数',
  FRESHNESS_UNKNOWN: '数据时效待评估',
  TIME_INVALID: '采样时间异常',
  DEVICE_INACTIVE: '设备已停用',
  DATA_INVALID: '数据无效',
  EVIDENCE_UNKNOWN: '暂无法判定',
  UNCONFIGURED: '未匹配生效标准',
};
export type EnvironmentCardStatus =
  | 'SAFE'
  | 'UNSAFE'
  | 'SUSPECT'
  | 'NO_DATA'
  | 'CONFLICT'
  | 'STALE'
  | 'LOCATION_UNKNOWN'
  | 'LOCATION_MISMATCH'
  | 'FRESHNESS_UNKNOWN'
  | 'TIME_INVALID'
  | 'DEVICE_INACTIVE'
  | 'DATA_INVALID'
  | 'EVIDENCE_UNKNOWN'
  | 'UNCONFIGURED';
export function environmentCardStatus(card: EnvironmentCardVo, elapsedMs = 0): EnvironmentCardStatus {
  if (card.dataValue == null || card.dateTime == null) return 'NO_DATA';
  if (!Number.isFinite(card.dataValue) || card.quality === 'INVALID') return 'DATA_INVALID';
  if (card.quality === 'SUSPECT') return 'SUSPECT';
  const map: Record<string, EnvironmentCardStatus> = {
    INVALID_DATA: 'DATA_INVALID',
    TIME_INVALID: 'TIME_INVALID',
    DEVICE_INACTIVE: 'DEVICE_INACTIVE',
    LOCATION_UNVERIFIED: 'LOCATION_UNKNOWN',
    LOCATION_MISMATCH: 'LOCATION_MISMATCH',
    NO_ACTIVE_STANDARD: 'UNCONFIGURED',
    THRESHOLD_CONFLICT: 'CONFLICT',
    FRESHNESS_UNASSESSED: 'FRESHNESS_UNKNOWN',
    STALE: 'STALE',
    NO_DATA: 'NO_DATA',
  };
  if (card.sampleStatus !== 'VALID') return map[card.sampleStatus || ''] || 'EVIDENCE_UNKNOWN';
  if (card.standardMatch && card.standardMatch !== 'MATCHED')
    return card.standardMatch in standardMatchDescriptions ? 'UNCONFIGURED' : 'EVIDENCE_UNKNOWN';
  if (card.thresholdConflict) return 'CONFLICT';
  if (!hasLocationAssociation(card.sampleLocationBasis)) return 'LOCATION_UNKNOWN';
  if (card.sampleLocationId !== card.sensorPointId) return 'LOCATION_MISMATCH';
  const limit = Number(card.maximumSampleAgeSeconds),
    sample = Number(card.dateTime),
    evaluated = Number(card.evaluatedAt);
  if (!(limit > 0) || !Number.isFinite(limit)) return 'FRESHNESS_UNKNOWN';
  if (card.evaluatedAt == null || !Number.isFinite(evaluated) || !Number.isFinite(sample) || sample > evaluated)
    return 'TIME_INVALID';
  // 以服务端 UTC 判断时刻加页面停留时间计龄，不依赖浏览器本地时钟是否正确。
  if (evaluated + Math.max(0, elapsedMs) - sample > limit * 1000) return 'STALE';
  if (card.safe == null || card.quality !== 'NORMAL') return 'EVIDENCE_UNKNOWN';
  return card.safe ? 'SAFE' : 'UNSAFE';
}
export function aggregateEnvironmentStatus(
  statuses: EnvironmentCardStatus[],
  activeAlarms = 0,
): EnvironmentCardStatus | 'PARTIAL' {
  if (activeAlarms > 0 || statuses.includes('UNSAFE')) return 'UNSAFE';
  if (!statuses.length || statuses.every((s) => s === 'NO_DATA')) return 'NO_DATA';
  const risk: EnvironmentCardStatus[] = [
    'CONFLICT',
    'DATA_INVALID',
    'TIME_INVALID',
    'LOCATION_MISMATCH',
    'LOCATION_UNKNOWN',
    'STALE',
    'FRESHNESS_UNKNOWN',
    'DEVICE_INACTIVE',
    'EVIDENCE_UNKNOWN',
    'SUSPECT',
    'UNCONFIGURED',
  ];
  const problem = risk.find((s) => statuses.includes(s));
  if (problem) return problem;
  return statuses.includes('NO_DATA') ? 'PARTIAL' : 'SAFE';
}

export const standardMatchDescriptions: Record<string, string> = {
  NO_PUBLISHED_RULE: '当前位置的该指标未找到已发布的保护阈值规则，请核对方案是否已启用以及是否覆盖此位置和指标。',
  RULE_NOT_ACTIVE: '对应的保护规则当前未启用，请在保护方案中核对运行状态。',
  NO_EFFECTIVE_VERSION: '对应规则当前没有生效版本，请核对方案启用情况及生效时间。',
  LOCATION_NOT_COVERED: '当前生效规则未覆盖这个监测位置，请核对方案的适用范围和适用对象。',
  NO_VALUE_THRESHOLD: '当前规则未提供可用于此指标的数值阈值，请核对方案中的上下限设置。',
};

export const sampleStatusDescriptions: Record<string, string> = {
  STALE: '最近采集的数据已超过共同评估确定的采样有效期，暂无法判断当前环境是否达标。',
  LOCATION_UNKNOWN: '尚未核实采样对应的位置，暂无法将该读数用于此位置的环境评价。',
  LOCATION_MISMATCH: '该读数对应的位置与当前监测位置不一致，请核对设备关联位置。',
  FRESHNESS_UNKNOWN: '尚无有效的采样时效评估依据，暂无法判断该读数能否代表当前环境。',
  TIME_INVALID: '采集时间或接收时间异常，请核对设备和服务器时间。',
  DEVICE_INACTIVE: '采集设备已停用，保留读数供查阅。',
  DATA_INVALID: '当前读数未通过数据质量校验，暂无法用于环境评价。',
  EVIDENCE_UNKNOWN: '当前监测信息不足，暂无法判断是否达标，请核对采样、位置及保护标准。',
  UNCONFIGURED: '当前位置的该指标未匹配到生效的保护阈值，暂无法判断是否达标。',
  CONFLICT: '适用保护标准的阈值区间存在冲突，请核对来源方案中的上下限。',
  SUSPECT: '当前读数被标记为可疑，请核实后再用于环境评价。',
  NO_DATA: '尚无该指标的采样数据，暂无法判断是否达标。',
};

/** 标准匹配与数据质量、时效独立；旧接口缺少证据时保留未知状态。 */
export function hasMatchedStandard(card: EnvironmentCardVo): boolean | null {
  if (card.standardMatch) {
    if (card.standardMatch === 'MATCHED') return true;
    return card.standardMatch in standardMatchDescriptions ? false : null;
  }
  if (card.sampleStatus === 'NO_ACTIVE_STANDARD') return false;
  if (card.thresholdConflict || card.safeMinValue != null || card.safeMaxValue != null) return true;
  if (['VALID', 'THRESHOLD_CONFLICT', 'FRESHNESS_UNASSESSED', 'STALE'].includes(card.sampleStatus || '')) return true;
  return null;
}

export function standardMatchLabel(card: EnvironmentCardVo): string {
  const matched = hasMatchedStandard(card);
  if (matched === false) return '未匹配生效标准';
  if (matched === null) return '标准状态待核实';
  return card.thresholdConflict ? '阈值冲突' : '已匹配生效标准';
}

export function environmentStatusDescription(card: EnvironmentCardVo, elapsedMs = 0): string {
  const status = environmentCardStatus(card, elapsedMs);
  if (status === 'SAFE') return '当前有效读数处于适用保护标准的阈值范围内。';
  if (status === 'UNSAFE') return '当前有效读数超出适用保护标准的阈值范围，请查看预警和处置记录。';
  if (status === 'UNCONFIGURED')
    return standardMatchDescriptions[card.standardMatch || ''] || sampleStatusDescriptions.UNCONFIGURED;
  return sampleStatusDescriptions[status] || sampleStatusDescriptions.EVIDENCE_UNKNOWN;
}

export function missingStandardCount(cards: EnvironmentCardVo[]): number {
  return cards.filter((card) => hasMatchedStandard(card) === false).length;
}
