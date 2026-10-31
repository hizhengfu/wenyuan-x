  import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/standard/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/standard/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/standard/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/standard/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/standard/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/standard/update',
        method: 'post',
        data
    })
}
