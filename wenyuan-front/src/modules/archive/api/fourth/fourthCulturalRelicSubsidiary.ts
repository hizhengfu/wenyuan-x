import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'fourth/cultural/relic/attached/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `fourth/cultural/relic/attached/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'fourth/cultural/relic/attached/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'fourth/cultural/relic/attached/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'fourth/cultural/relic/attached/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'fourth/cultural/relic/attached/update',
        method: 'post',
        data
    })
}
