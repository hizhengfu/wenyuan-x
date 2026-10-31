interface Window {
  // L: import('leaflet').Map;

  embedpano?: any;
  removepano?: any;
}

/**
 * 接口数据结构
 * */
interface ApiData {
  message?: string;
  data?: any;
  status: number;
  timestamp: number;
}

/**
 * 提示信息
 * */
interface NotificationOptions {
  type?: string;
  title?: string;
  content?: string;
  duration?: number;
}

/**
 * 文件上传返回格式
 * */
interface UploadResponseFile {
  id: string;
  name: string;
  path: string;
  type: string;
  ext: string;
  hash: string;
  size: number;
  download: number;
  createTime: number;
}

/**
 * 全局变量
 * */
declare const APP_TARGET: string;
declare const APP_TITLE: string;
declare const APP_ADMIN_THEME: string;
declare const APP_ABBR_TITLE: string;
declare const APP_DESCRIPTION: string;
declare const APP_MOBILE_UI: boolean;
declare const APP_COPYRIGHT: string;
declare const APP_HOME_URL: string;
declare const APP_BASE_URL: string;
declare const APP_API_PATH: string;
declare const APP_API_FRONT_PATH: string;
declare const APP_API_PROXY: boolean;
declare const ENABLE_HKPLAYER: boolean;
declare const ENABLE_MARS3D: boolean;
declare const ENABLE_CESIUM: boolean;
declare const ENABLE_DRACO: boolean;
declare const ENABLE_MARMOSET: boolean;
declare const ENABLE_OAUTH2: boolean;
declare const ENABLE_UPDATE_VERSION: boolean;
