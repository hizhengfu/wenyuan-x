/**
 * 模块表类型
 * */
export interface ModuleTable {
  title: string,//模块表名称
  code: string,//模块表代码
  id: string,//模块表id
  enableAuth: boolean,//启用权限
  enableFlow: boolean,//启用工作流
  enabledPermission: boolean,
  isTree: boolean,//是否树形
  module: Module,
  columns: ModuleTableColumn[],
  formLayout: any,//表单布局
}

/**
 * 模块类型
 * */
export interface Module {
  id: string,
  name: string
}

/**
 * 模块表列类型
 * */
export interface ModuleTableColumn {
  id: string,
  tableId: string,
  name: string,
  isExport: boolean,
  isHidden: boolean,
  isInsert: boolean,
  isList: boolean,
  isNull: boolean,
  isPk: boolean,
  isQuery: boolean,
  isSortable: boolean,
  isUpdate: boolean,
  isTitle: boolean,
  type: number,
  scale?: number,
  size?: number,
  sort: number,

  fieldLabel: string,
  fieldDict?: string,
  fieldType?: string,
}


export interface ModuleGenCode {
  apiContent: string,
  apiFileName: string,
  viewContent: string,
  viewFileName: string,
}
