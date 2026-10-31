import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'control/media/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `control/media/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'control/media/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'control/media/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'control/media/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'control/media/update',
    method: 'post',
    data,
  });
}

export function getAllVenueMediaDevice(venueId: any) {
  return request({
    url: `control/media/device/${venueId}`,
    method: 'get',
  });
}

export function getMediaDevice(mediaId: any) {
  return request({
    url: `control/media/getMediaDevice/${mediaId}`,
    method: 'get',
  });
}

/**
 * 存储场景设备关系
 * */
export function saveMediaDevice(mediaId: string, data: string[]) {
  return request({
    url: `control/media/saveMediaDevice/${mediaId}`,
    data: data,
    method: 'post',
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'control/media/sort',
    method: 'post',
    data,
  });
}
