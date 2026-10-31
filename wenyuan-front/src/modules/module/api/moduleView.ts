import request from '@/utils/request'

/**
 * 根据模块CODE与表CODE获取表信息
 * @param moduleCode 模块代码
 * @param tableCode 模块表代码
 * */
export function loadByModuleCodeAndTableCode(moduleCode: string, tableCode: string) {
  return request({
    url: `module/view/load/${moduleCode}/${tableCode}`,
    method: 'get'
  })
}

/**
 * @param tableId 模块表id
 * */
export function loadByTableId(tableId: string) {
  return request({
    url: `module/view/load/${tableId}`,
    method: 'get'
  })
}

/**
 * 获取对应表详细内容
 * */
export function get(tableId: string, id: string) {
  return request({
    url: `module/view/${tableId}/${id}`,
    method: 'get'
  })
}

/**
 * 根据表code查询
 * 实际意义不大
 * */
export function getByCode(tableCode: string, id: string) {
  return request({
    url: `module/view/code/${tableCode}/${id}`,
    method: 'get'
  })
}

/**
 * 数据列表
 * */
export function list(tableId: string, params: object) {
  return request({
    url: `module/view/${tableId}/list`,
    params: params,
    method: 'get'
  })
}

/**
 * 根据 tableCode 查询列表
 * 一般用于外部调用
 * */
export function listByCode(tableCode: string, params: object) {
  return request({
    url: `module/view/code/list/${tableCode}`,
    params: params,
    method: 'get'
  })
}

/**
 * 根据 tableName 查询列表
 * 一般用于外部调用
 * */
export function listByTableName(tableName: string, params: object) {
  return request({
    url: `module/view/table/list/${tableName}`,
    params: params,
    method: 'get'
  })
}

/**添加数据
 *
 * */
export function add(tableId: string, data: object) {
  return request({
    url: `module/view/${tableId}/create`,
    method: 'post',
    data
  })
}

/**
 * 复制数据
 * */
export function copy(tableId: string, id: string) {
  return request({
    url: `module/view/${tableId}/copy/${id}`,
    method: 'post'
  })
}

export function del(tableId: string, id: string) {
  return request({
    url: `module/view/${tableId}/delete/${id}`,
    method: 'post'
  })
}

export function edit(tableId: string, data: object) {
  return request({
    url: `module/view/${tableId}/update`,
    method: 'post',
    data
  })
}

/**
 * 导出数据
 * */
export function exportExcel(tableId: string, params: object) {
  return request({
    url: `module/view/${tableId}/export`,
    params: params,
    method: 'get'
  })
}
