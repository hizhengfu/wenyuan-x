/**
 * 快速时间选择参数
 * */
export interface QuickDateParams {
  /**
   * 选择的时间值
   * */
  dateValue: string | number[];
  /**
   * 选择的时间类型
   * */
  dateType: string;
  /**
   * 数据查询类型 hour 0 date 1 month 2 year 3
   * */
  dataType: number;
}

/**
 * 文件快传
 * */
export interface QuickUploadFile {
  hash: string;
  /**
   * 文件名
   * */
  name?: string;
  /**
   * 文件关联id
   * */
  contentId?: string;
}

/**
 * 分片上传
 * */
export interface ChunkUploadFile {
  phase: 'start' | 'upload' | 'finish';
  chunk?: Blob;
  /**
   * 文件大小
   * */
  size?: number;
  /**
   * 文件名
   * */
  name?: string;
  /**
   * 文件关联id
   * */
  contentId?: string;
  /**
   * 会话id
   * */
  session_id?: string;
  /**
   * 偏移量
   * */
  start_offset?: number;
  /**
   * 重试次数
   * */
  retries: number;
}

/**
 * 抽屉宽度解析后的对象
 */
export interface FormDrawerWidth {
  value: number; // 数值部分，例如 500 或 50
  unit: 'px' | '%'; // 单位，'px' 或 '%'
}

/**
 * 活跃抽屉的详细信息
 */
export interface FormDrawerActiveDrawer {
  id: string; // 抽屉的唯一ID
  initialWidth: string | number; // 原始传入的宽度（例如：500, "600px", "50%"）
  currentWidth: string; // 当前计算后的宽度（总是带单位的字符串，如 "450px", "50%"）
}
