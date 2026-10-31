/** 科研统计 API（${apiPath}/research/stat） */
import request from '@/utils/request';

const BASE = '/research/stat';

export const statApi = {
  overview() {
    return request({ url: `${BASE}/overview`, method: 'get' });
  },
};
