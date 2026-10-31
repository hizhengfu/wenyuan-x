/** 后端返回的用户权益相关类型 */
declare namespace ApiAuth {
  /** 返回的token和刷新token */
  type token = string;
  /** 返回的用户信息 */
  type user = Auth.UserInfo;
}

/** 用户相关模块 */
declare namespace Auth {
  /** 用户信息 */
  interface UserInfo {
    token?: string,//登陆令牌
    id?: string,//用户id
    name?: string,//名称
    username?: string,//名称
    avatar?: string,//头像
    email?: string,//邮箱
    groupId?: string,//机构id
    groupName?: string,//机构名称
    dataType?: string,//数据类型
    enabled?: boolean,//数据类型
    permissions?: string[],//功能权限 code
    roles?: string[],//角色主键 ID
    roleCodes?: string[],//角色 code
    rolesName?: string,//角色显示名称（逗号分隔）
    createTime?: number,//创建时间
  }
}
