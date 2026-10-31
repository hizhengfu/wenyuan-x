import request from '@/utils/request'

export function list(params: object) {
    return request({
        url: 'module/index/list',
        params: params,
        method: 'get'
    })
}

export function get(id: string) {
    return request({
        url: `module/index/${id}`,
        method: 'get'
    })
}

export function add(data: object) {
    return request({
        url: 'module/index/create',
        method: 'post',
        data
    })
}

export function del(id: string) {
    return request({
        url: 'module/index/delete/' + id,
        method: 'post'
    })
}

export function edit(data: object) {
    return request({
        url: 'module/index/update',
        method: 'post',
        data
    })
}
