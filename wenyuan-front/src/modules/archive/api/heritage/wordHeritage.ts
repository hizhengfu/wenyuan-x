     import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/heritage/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/heritage/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/heritage/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/heritage/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/heritage/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/heritage/update',
        method: 'post',
        data
    })
}
