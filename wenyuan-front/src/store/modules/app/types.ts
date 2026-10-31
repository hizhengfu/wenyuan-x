import { Ref } from 'vue';

export interface AppState {
  title: string;
  abbrTitle: string;
  /** 应用id **/
  applicationId: string;
  /** 版权所有 **/
  copyright: string;
  /** 页面路径 **/
  path?: string;
  /** 文件上传中 */
  uploading: boolean;
  /** 重载页面(控制页面的显示) */
  reloadFlag: boolean;
  /** 项目配置的抽屉可见状态 */
  settingDrawerVisible: boolean;
  /** 系统静默，不提示 */
  silent: boolean;
  /** 侧边栏折叠状态 */
  sideCollapse: Ref<boolean>;
  /*是否全屏*/
  fullscreen: boolean;
  /*表格大小*/
  tableSize: Ref<'small' | 'medium' | 'large'>;
  /** vertical-mix模式下 侧边栏的固定状态 */
  mixSideFixed: boolean;
  /**
   * 当前后端装载的模块 id 列表（来自 GET /api/config/modules）。
   * 装载即注入对应 Configs schema 行的模块（如 message2 装载后此列表含 'notify'），
   * 前端据此决定是否显示通知中心入口/铃铛等"模块级"功能。
   */
  installedModules: string[];
}
