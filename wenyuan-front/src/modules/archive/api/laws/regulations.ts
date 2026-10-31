 import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/regulation/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/regulation/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/regulation/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/regulation/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/regulation/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/regulation/update',
        method: 'post',
        data
    })
}
