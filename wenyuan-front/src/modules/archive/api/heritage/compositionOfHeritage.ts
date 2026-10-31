 import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/heritage/compose/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/heritage/compose/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/heritage/compose/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/heritage/compose/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/heritage/compose/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/heritage/compose/update',
        method: 'post',
        data
    })
}
