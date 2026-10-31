/** 预防性保护内部只读指标引用；标准指标统一在 museum-device 预置。 */
import request from '@/utils/request';

const BASE = '/preservation/sensor';

export const sensorApi = {
  all() {
    return request({ url: BASE, method: 'get' });
  }
};
