   import request from '@/utils/request'

export function list (params: object) {
    return request({
        url: 'archive/category/list',
        params: params,
        method: 'get'
    })
}
export function treeList (params: object) {
    return request({
        url: 'archive/category/tree',
        params: params,
        method: 'get'
    })
}

export function get (id: string) {
    return request({
        url: `archive/category/${id}`,
        method: 'get'
    })
}

export function add (data: object) {
    return request({
        url: 'archive/category/create',
        method: 'post',
        data
    })
}

export function copy (id: string) {
    return request({
        url: 'archive/category/copy/' + id,
        method: 'post'
    })
}

export function del (id: string) {
    return request({
        url: 'archive/category/delete/' + id,
        method: 'post'
    })
}

export function edit (data: object) {
    return request({
        url: 'archive/category/update',
        method: 'post',
        data
    })
}
