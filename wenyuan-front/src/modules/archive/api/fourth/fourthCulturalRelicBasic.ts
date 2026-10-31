import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'fourth/cultural/relic/basic/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `fourth/cultural/relic/basic/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'fourth/cultural/relic/basic/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'fourth/cultural/relic/basic/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'fourth/cultural/relic/basic/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'fourth/cultural/relic/basic/update',
        method: 'post',
        data
    })
}
