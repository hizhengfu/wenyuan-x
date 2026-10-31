import { describe, expect, it } from 'vitest';
import { metricDisplayName } from '../metricName';

describe('指标展示名称', () => {
  it('标准名称与厂商原名分别处理', () => {
    expect(metricDisplayName('TEMP', '温度', '柜内温度')).toBe('温度');
    expect(metricDisplayName('HUMIDITY', '相对湿度', '湿度')).toBe('相对湿度');
    expect(metricDisplayName('TEMP', null, '柜内温度')).toBe('TEMP');
  });

  it('扩展编码保留，展示优先使用厂商名称且可以随元数据刷新', () => {
    expect(metricDisplayName('EXT_OLD', null, '热面温度')).toBe('热面温度');
    expect(metricDisplayName('EXT_OLD', null, '加热面温度')).toBe('加热面温度');
    expect(metricDisplayName('EXT_OLD', '旧展示名', '加热面温度')).toBe('加热面温度');
  });

  it('兼容升级前没有新增字段的响应及空白厂商名称', () => {
    expect(metricDisplayName('EXT_OLD')).toBe('EXT_OLD');
    expect(metricDisplayName('EXT_OLD', null, '  ')).toBe('EXT_OLD');
    expect(metricDisplayName('')).toBe('--');
  });
});
