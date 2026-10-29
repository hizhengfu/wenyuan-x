import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'museum/task/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `museum/task/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'museum/task/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'museum/task/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'museum/task/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'museum/task/update',
    method: 'post',
    data
  })
}


export function listTaskDetail(params: object) {
  return request({
    url: 'museum/task/listTaskDetail',
    method: 'get',
    params
  })
}

export function addTaskDetail(data: object) {
  return request({
    url: 'museum/task/addTaskDetail',
    method: 'post',
    data
  })
}

export function saveTaskDetail(data: object) {
  return request({
    url: 'museum/task/saveTaskDetail',
    method: 'post',
    data
  })
}

export function updateTaskSort(data: object) {
  return request({
    url: 'museum/task/updateTaskSort',
    method: 'post',
    data
  })
}

export function deleteTaskDetail(id: string) {
  return request({
    url: 'museum/task/deleteTaskDetail',
    method: 'post',
    params: {id: id}
  })
}



