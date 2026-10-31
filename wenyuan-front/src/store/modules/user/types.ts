export interface UserState {
  token?: string,//登陆令牌
  userId?: string,//用户id
  name?: string,//名称
  avatar?: string,//头像
  email?: string,//邮箱
  groupId?: string,//机构id
  groupName?: string,//机构名称
  dataType?: string,//数据类型
  permissions?: string[],//权限
  roles?: string[],//角色
}
