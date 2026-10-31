import request from '@/utils/request';

export function chart(params: object) {
  return request({
    url: 'wms/analysis/chart',
    params: params,
    method: 'get',
  });
}

export function statStock(params: object) {
  return request({
    url: 'wms/analysis/statStock',
    params: params,
    method: 'get',
  });
}

/**
 * 多维交叉统计 — 对应 xlsx「统计分析」10 类
 * params: { groupBy: EnumCrossDim[], chartType: 'bar'|'pie', ...filters }
 */
export function crossStat(params: object) {
  return request({
    url: 'wms/analysis/crossStat',
    method: 'post',
    data: params,
  });
}

/**
 * 钻取明细 — 点击图表块时拉取文物清单
 */
export function drill(baseParams: object, path: Array<[string, string]>) {
  return request({
    url: 'wms/analysis/drill',
    method: 'post',
    data: { baseParams, path },
  });
}

/**
 * 出土器物登记表（智能统计）— 单位×材质交叉，含型式计数与礼器序排列
 */
export function registrationTable(params: { projectId: string }) {
  return request({
    url: 'wms/analysis/registrationTable',
    params,
    method: 'get',
  });
}
