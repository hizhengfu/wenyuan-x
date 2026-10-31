import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'fourth/cultural/relic/single/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `fourth/cultural/relic/single/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'fourth/cultural/relic/single/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'fourth/cultural/relic/single/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'fourth/cultural/relic/single/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'fourth/cultural/relic/single/update',
        method: 'post',
        data
    })
}
