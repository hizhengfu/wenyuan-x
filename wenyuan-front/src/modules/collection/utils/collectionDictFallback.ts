/**
 * 藏品登记常用编码的兜底中文名，取值与 104 环境字典 col_relic_level / col_completeness /
 * col_preserve / col_source 一致；仅在字典未加载或缺项时使用，避免把 THIRD、DAMAGED 等编码直接展示。
 */
const fallbackLabels: Record<string, Record<string, string>> = {
  col_relic_level: { FIRST: '一级', SECOND: '二级', THIRD: '三级', GENERAL: '一般文物', UNRATED: '未定级' },
  col_completeness: { COMPLETE: '完整', BASIC: '基本完整', DAMAGED: '残缺', SEVERE: '严重残缺（含缺失部件）' },
  col_preserve: { A: '状态稳定，不需修复', B: '部分损腐，需要修复', C: '腐蚀损毁严重，急需修复' },
  col_source: {
    EXCAVATION: '考古发掘',
    COLLECT: '采集',
    PURCHASE: '收购',
    ALLOCATE: '调拨',
    TRANSFER: '拨交',
    EXCHANGE: '交换',
    DONATE: '捐赠',
    HANDOVER: '移交',
    PICK: '拣选',
    OLD: '旧藏',
    REPRO: '复制',
    DESIGNATED: '行政指定接收',
  },
};

export function collectionDictFallbackLabel(type: string | null | undefined, value: unknown) {
  if (!type) return undefined;
  const key = String(value ?? '').trim();
  return key ? fallbackLabels[type]?.[key] : undefined;
}

/** 已翻译结果仍等于原编码时，用兜底表替换；逗号分隔多值逐项处理。 */
export function withCollectionDictFallback(type: string | null | undefined, value: unknown, translated: string) {
  const raw = String(value ?? '').trim();
  if (!raw || !type || !fallbackLabels[type]) return translated;
  const parts = raw.split(',').map((item) => item.trim());
  const translatedParts = translated.split(/[,，]/).map((item) => item.trim());
  if (translatedParts.length !== parts.length) return translated;
  return translatedParts
    .map((label, index) => (label === parts[index] ? collectionDictFallbackLabel(type, parts[index]) || label : label))
    .join(translated.includes('，') ? '，' : ',');
}

/**
 * 历史补充说明中紧跟数字重复的长度/质量单位（如「6cmcm」「6厘米厘米」）只显示一次，原始记录不改。
 * 多字单位排在前面整体匹配，「6厘米」不会被当成「米」的重复；单位不同（如「6厘米米」）不合并。
 */
const REPEATED_UNIT_PATTERN = /(\d)\s*(千克|公斤|厘米|毫米|米|克|cm|mm|kg)(?:\s*\2)+(?![A-Za-z])/gi;

export function normalizeRepeatedMeasureUnit(text: string) {
  return text.replace(REPEATED_UNIT_PATTERN, '$1$2');
}
