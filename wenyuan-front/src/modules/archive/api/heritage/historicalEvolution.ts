     import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/heritage/historicalEvolution/list',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/heritage/historicalEvolution/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/heritage/historicalEvolution/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/heritage/historicalEvolution/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/heritage/historicalEvolution/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/heritage/historicalEvolution/update',
        method: 'post',
        data
    })
}
