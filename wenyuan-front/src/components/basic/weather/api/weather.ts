import request from '@/utils/request';
export function weather(id: string) {
  return request({
    url: `common/weather/${id}`,
    method: 'get',
  });
}
