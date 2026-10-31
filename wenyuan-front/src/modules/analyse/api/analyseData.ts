import request from '@/utils/request'


/**
 *  数据列表
 *  {type:'',code:'',dateTime:''}
 *
 * */
export function list(params: object) {
  return request({
    url: 'analyse/list',
    params: params,
    method: 'get'
  })
}


/**
 *  图表
 *  {chatType:'line',type:'',code:'',dateTime:''}
 *
 * */
export function chart(params: object) {
  return request({
    url: 'analyse/chart',
    params: params,
    method: 'get'
  })
}

/**
 *  地图
 * */
export function map(params: object) {
  return request({
    url: 'analyse/map',
    params: params,
    method: 'get'
  })
}

export function page(params: object) {
  return request({
    url: 'analyse/page',
    params: params,
    method: 'get'
  })
}


/**
 * 更新排序
 * */
export function updateSort(data: object) {
  return request({
    url: 'analyse/sort',
    method: 'post',
    data
  })
}


export function add(data: any) {
  return request({
    url: 'analyse/create',
    method: 'post',
    data
  })
}

export function edit(data: any) {
  return request({
    url: 'analyse/update',
    method: 'post',
    data
  })
}

/**
 * 统计数据编辑
 */
export function save(data: object) {
  return request({
    url: 'analyse/save',
    data: data,
    method: 'post'
  })
}

/**
 * 统计数据复制
 */
export function copy(id: string) {
  return request({
    url: 'analyse/copy/' + id,
    method: 'post'
  })
}

/**
 * 统计数据删除
 */
export function del(id: string) {
  return request({
    url: 'analyse/remove',
    params: {id: id},
    method: 'post'
  })
}
