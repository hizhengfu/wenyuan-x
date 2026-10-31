import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'fourth/cultural/relic/specimen/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `fourth/cultural/relic/specimen/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'fourth/cultural/relic/specimen/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'fourth/cultural/relic/specimen/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'fourth/cultural/relic/specimen/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'fourth/cultural/relic/specimen/update',
        method: 'post',
        data
    })
}
