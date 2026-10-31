import type { AlarmSourceCategory } from '../typings';

export const ALARM_SOURCE_CATEGORY_LABELS: Record<AlarmSourceCategory, string> = {
  VIDEO: '视频预警',
  ENV_ANALYSIS: '环境监测分析预警',
  OTHER_EVENT: '其它预警',
};
export const ALARM_SOURCE_CATEGORY_OPTIONS = Object.entries(ALARM_SOURCE_CATEGORY_LABELS).map(([value, label]) => ({ value, label }));

export const INGEST_RECORD_TYPE_DICT = 'museum_ingest_record_type';
export const INGEST_DATA_QUALITY_DICT = 'museum_ingest_data_quality';
export const LOCATION_TYPE_DICT = 'museum_location_type';
