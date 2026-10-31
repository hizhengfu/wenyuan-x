import request from '@/utils/request';

/** 概览数据 */
export function overview() {
  return request({
    url: 'resource/stat/overview',
    method: 'get',
  });
}

/** 按资源类型分布 */
export function byType() {
  return request({
    url: 'resource/stat/byType',
    method: 'get',
  });
}

/** 按分类分布 */
export function byCategory() {
  return request({
    url: 'resource/stat/byCategory',
    method: 'get',
  });
}

/** 最近 N 天创建趋势（默认 30） */
export function trend(days: number = 30) {
  return request({
    url: 'resource/stat/trend',
    method: 'get',
    params: { days },
  });
}

/** 近 N 天调用趋势 */
export function usageTrend(days: number = 30, action?: string) {
  return request({
    url: 'resource/stat/usageTrend',
    method: 'get',
    params: { days, action },
  });
}

/** 按使用场景统计 */
export function usageByScene(days: number = 30) {
  return request({
    url: 'resource/stat/usageByScene',
    method: 'get',
    params: { days },
  });
}

/** 按调用动作统计 */
export function usageByAction(days: number = 30) {
  return request({
    url: 'resource/stat/usageByAction',
    method: 'get',
    params: { days },
  });
}

/** 将字节数格式化为易读字符串 */
export function formatSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];
  const idx = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / Math.pow(1024, idx)).toFixed(2)} ${units[idx]}`;
}
