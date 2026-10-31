import request from '@/utils/request'
export function list (params: object) {
    return request({
        url: 'fourth/cultural/relic/gps/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `fourth/cultural/relic/gps/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'fourth/cultural/relic/gps/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'fourth/cultural/relic/gps/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'fourth/cultural/relic/gps/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'fourth/cultural/relic/gps/update',
        method: 'post',
        data
    })
}
