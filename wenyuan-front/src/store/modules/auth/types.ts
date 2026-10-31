export interface AuthState {
  /** Local generation invalidates callbacks from a previous shared browser login. */
  identityRevision: number;
  /** Hold the business/cache tree until the normal page reload rechecks the new login. */
  identitySyncing: boolean;
  /** 用户信息 */
  userInfo?: Auth.UserInfo;
  /** 用户token */
  token?: string;
  /** 角色主键 ID 列表 */
  roles?: string[];
  /** 角色 code 列表 */
  roleCodes?: string[];
  /** 权限列表 */
  permissions?: string[];
  /** 是否展示侧边栏 */
  showSidebar: boolean;
  /** 登录的加载状态 */
  loginLoading: boolean;
  /** 登录密码是否加密 */
  isEncrypt: boolean;
  /** 是否需要重设密码 */
  resetPassword: boolean;
  /** header 认证字段名称 **/
  authorizationName: string;

  /** 登录函数 **/
  authLogin: Function;

  /**
   * 门户id
   * */
  portalId?: string;

  /** 主菜单 */
  menus?: any[];
}
