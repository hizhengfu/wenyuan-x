import request from '@/utils/request';

export function list(params: any) {
  return request({
    url: 'control/scene/list',
    params: params,
    method: 'get',
  });
}

export function get(id: string) {
  return request({
    url: `control/scene/${id}`,
    method: 'get',
  });
}

export function add(data: any) {
  return request({
    url: 'control/scene/create',
    method: 'post',
    data,
  });
}

export function copy(id: string) {
  return request({
    url: 'control/scene/copy/' + id,
    method: 'post',
  });
}

export function del(id: string) {
  return request({
    url: 'control/scene/delete/' + id,
    method: 'post',
  });
}

export function edit(data: any) {
  return request({
    url: 'control/scene/update',
    method: 'post',
    data,
  });
}

/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'control/scene/sort',
    method: 'post',
    data,
  });
}

/**
 * 获取场景下的设备树
 * */
export function getSceneDeviceTree(sceneId: string) {
  return request({
    url: `control/scene/getSceneDeviceTree/${sceneId}`,
    method: 'get',
  });
}

export function getSceneDevice(sceneId: any) {
  return request({
    url: `control/scene/getSceneDevice/${sceneId}`,
    method: 'get',
  });
}

/**
 * 存储场景设备关系
 * */
export function saveSceneDevice(sceneId: string, data: string[]) {
  return request({
    url: `control/scene/saveSceneDevice/${sceneId}`,
    data: data,
    method: 'post',
  });
}

/**
 * 获取设备对应的场景数据配置
 * */
export function getSceneDeviceCommand(params: object) {
  return request({
    url: 'control/scene/getSceneDeviceCommand',
    params,
    method: 'get',
  });
}

/**
 * 存储场景设备指令关系
 * */
export function saveSceneDeviceCommand(data: any) {
  return request({
    url: 'control/scene/saveSceneDeviceCommand',
    data: data,
    method: 'post',
  });
}
