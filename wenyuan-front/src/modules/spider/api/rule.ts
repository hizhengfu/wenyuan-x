import request from '@/utils/request'

export function list(params: any) {
  return request({
    url: 'spider/rule/list',
    params: params,
    method: 'get'
  })
}

export function get(id: string) {
  return request({
    url: `spider/rule/${id}`,
    method: 'get'
  })
}

export function add(data: any) {
  return request({
    url: 'spider/rule/create',
    method: 'post',
    data
  })
}

export function copy(id: string) {
  return request({
    url: 'spider/rule/copy/' + id,
    method: 'post'
  })
}


export function del(id: string) {
  return request({
    url: 'spider/rule/delete/' + id,
    method: 'post'
  })
}

export function edit(data: any) {
  return request({
    url: 'spider/rule/update',
    method: 'post',
    data
  })
}


//爬虫测试
export function test(params: any){
  return request({
    url:'spider/test',
    method:'get',
    params: params,
    timeout:60000
  })
}
