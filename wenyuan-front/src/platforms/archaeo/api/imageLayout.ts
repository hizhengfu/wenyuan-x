import request from '@/utils/request';

export function page(params: object) {
  return request({ url: 'wms/imageLayout/page', params, method: 'get' });
}

export function get(id: string) {
  return request({ url: `wms/imageLayout/${id}`, method: 'get' });
}

export function create(data: object) {
  return request({ url: 'wms/imageLayout/create', method: 'post', data });
}

export function update(data: object) {
  return request({ url: 'wms/imageLayout/update', method: 'post', data });
}

export function del(id: string) {
  return request({ url: `wms/imageLayout/delete/${id}`, method: 'post' });
}
