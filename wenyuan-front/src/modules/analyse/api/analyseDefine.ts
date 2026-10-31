import request from '@/utils/request'

export function list(params: any) {
    return request({
        url: 'analyse/define/list',
        params: params,
        method: 'get'
    })
}

export function get(id: string) {
    return request({
        url: `analyse/define/${id}`,
        method: 'get'
    })
}

export function add(data: any) {
    return request({
        url: 'analyse/define/create',
        method: 'post',
        data
    })
}

export function copy(id: string) {
    return request({
        url: 'analyse/define/copy/' + id,
        method: 'post'
    })
}


export function del(id: string) {
    return request({
        url: 'analyse/define/delete/' + id,
        method: 'post'
    })
}

export function edit(data: any) {
    return request({
        url: 'analyse/define/update',
        method: 'post',
        data
    })
}
