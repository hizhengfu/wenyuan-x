import request from '@/utils/request'

export function list(params: object) {
    return request({
        url: 'module/table/list',
        params: params,
        method: 'get'
    })
}

export function load(params: object) {
    return request({
        url: 'module/table/load',
        params: params,
        method: 'get'
    })
}

/**
 * 根据现有表导入表结构
 * */
export function importTable(params: object) {
    return request({
        url: 'module/table/importTable',
        params: params,
        method: 'post'
    })
}

/**
 * 更新布局信息
 * */
export function updateFormLayout(data: object) {
    return request({
        url: 'module/table/updateFormLayout',
        method: 'post',
        data
    })
}

//模块下的字段
export function columns(params: object) {
    return request({
        url: 'module/table/columns',
        params: params,
        method: 'get'
    })
}

//索引字段
export function index(params: object) {
    return request({
        url: 'module/table/index',
        params: params,
        method: 'get'
    })
}

//外键
export function reference(params: object) {
    return request({
        url: 'module/table/index',
        params: params,
        method: 'get'
    })
}

export function get(id: string) {
    return request({
        url: `module/table/${id}`,
        method: 'get'
    })
}

export function loadFormLayout(id: string, params: object) {
    return request({
        url: `module/table/loadFormLayout/${id}`,
        params: params || {},
        method: 'get'
    })
}

export function add(data: object) {
    return request({
        url: 'module/table/create',
        method: 'post',
        data
    })
}

export function del(id: string) {
    return request({
        url: 'module/table/delete/' + id,
        method: 'post'
    })
}

export function edit(data: object) {
    return request({
        url: 'module/table/update',
        method: 'post',
        data
    })
}

/**
 * 复制模块表
 * */
export function copy(data: object) {
    return request({
        url: `module/table/copy`,
        method: 'post',
        data
    })
}

/**
 * 发布模块
 * */
export function publish(id: string) {
    return request({
        url: `module/table/publish/${id}`,
        method: 'post'
    })
}

/**
 * 代码生成
 * */
export function gen(id: string, params?: object) {
    return request({
        url: `module/table/gen/${id}`,
        params: params || {},
        method: 'post'
    })
}

/**
 * 前端代码生成
 * */
export function code(id: string, params: object) {
    return request({
        url: `module/table/code/${id}`,
        params: params || {},
        method: 'post'
    })
}

/**
 * 启用模块表
 * */
export function enable(id: string) {
    return request({
        url: `module/table/enable/${id}`,
        method: 'post'
    })
}
