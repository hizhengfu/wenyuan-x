/**
 * 通知中心相关 TS 类型，与后端 module/message2 的 domain 一一对应。
 */

/** 通道枚举：与后端 EnumMessageNotifyChannel 对齐 */
export type EnumMessageNotifyChannel =
  | 'EMAIL'
  | 'SMS'
  | 'IN_APP'
  | 'APP_PUSH'
  | 'WX_MP'
  | 'WX_MA'
  | 'WX_CP'
  | 'WX_BOT';

/** 通知主体状态 */
export type EnumMessageNotifyStatus = 'SCHEDULED' | 'PROCESSING' | 'DONE' | 'CANCELED';

/** 投递行状态 */
export type EnumMessageDispatchStatus = 'WAIT' | 'SUCCESS' | 'FAIL' | 'SKIPPED' | 'RETRYING';

/** 站内信已读状态 */
export type EnumMessageInAppReadStatus = 'UNREAD' | 'READ';

/**
 * 接收范围：roleIds / groupIds / userIds 任意组合，最终在后端去重展开。
 * botNames：仅 WX_BOT 通道用，对应 Configs 中 NOTIFY_WX_BOT_WEBHOOKS 的 name。
 */
export interface MessageNotifyTargetSpec {
  roleIds?: string[];
  groupIds?: string[];
  userIds?: string[];
  botNames?: string[];
  /** 裸邮箱（不查用户表，强制送达），仅 EMAIL 通道生效 */
  emails?: string[];
  /** 裸手机号（不查用户表，强制送达），仅 SMS 通道生效 */
  phones?: string[];
}

/** 邮件附件 */
export interface MessageNotifyAttachment {
  name: string;
  url: string;
  contentType?: string;
}

/** 通知重要级别（替代老 sys/notify EnumNotifyLevel；与字典 notify_level 对齐） */
export type EnumMessageNotifyLevel = 'NORMAL' | 'WARN' | 'IMPORTANT';

/** 后台手填请求 VO */
export interface MessageNotifyVo {
  id?: string;
  title: string;
  content: string;
  summary?: string;
  channels: EnumMessageNotifyChannel[];
  scope: MessageNotifyTargetSpec;
  bizType?: string;
  bizId?: string;
  smsTemplateCode?: string;
  smsTemplateParam?: string;
  smsContent?: string;
  idempotencyKey?: string;
  lang?: string;
  attachments?: MessageNotifyAttachment[];
  scheduledAt?: string;
  /** 重要级别（公告语义），与字典 notify_level 对齐 */
  level?: EnumMessageNotifyLevel | string;
  /** 点击跳转 URL（IN_APP / 公告页消费） */
  clickUrl?: string;
  /** true=本通知不被定期清理（公告永久保留） */
  noExpire?: boolean;
}

/** 通知主体（列表 / 详情返回值） */
export interface MessageNotifyDao {
  id: string;
  title: string;
  content: string;
  summary?: string;
  channels: string;
  scopeJson?: string;
  recipientCount: number;
  notifyStatus: EnumMessageNotifyStatus;
  bizType?: string;
  bizId?: string;
  smsTemplateCode?: string;
  smsTemplateParam?: string;
  smsContent?: string;
  idempotencyKey?: string;
  lang?: string;
  attachments?: string;
  scheduledAt?: string;
  level?: EnumMessageNotifyLevel | string;
  clickUrl?: string;
  noExpire?: boolean;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

/** 详情接口的 dispatch 聚合 */
export interface MessageNotifyDispatchSummary {
  total: number;
  success: number;
  fail: number;
  skipped: number;
  wait: number;
  retrying: number;
}

/**
 * 单条 dispatch 行。
 *
 * `notifyTitle` / `userName` 仅在「全局投递记录页」用 [dispatchPage] 拉取时由后端 join 填充，
 * 通知详情对话框拉到的 dispatches 不含这两个字段。
 */
export interface MessageNotifyDispatchRow {
  id: string;
  notifyId: string;
  notifyTitle?: string;
  bizType?: string;
  bizId?: string;
  content?: string;
  templateCode?: string;
  templateParam?: string;
  userId?: string;
  userName?: string;
  channel: EnumMessageNotifyChannel;
  target?: string;
  dispatchStatus: EnumMessageDispatchStatus;
  message?: string;
  retryCount: number;
  retryable: boolean;
  nextRetryAt?: string;
  createTime?: string;
  updateTime?: string;
}

/** 通知详情接口返回 */
export interface MessageNotifyDetail extends MessageNotifyDao {
  summary: string;
  dispatchSummary: MessageNotifyDispatchSummary;
  dispatches: MessageNotifyDispatchRow[];
}

/** 模板编辑 VO（写）/ 详情 VO（读） */
export interface MessageTemplateVo {
  id?: string;
  code: string;
  name: string;
  bizType?: string;
  defaultChannels?: string;
  paramKeys?: string;
  systemBuiltin?: boolean;
  lang?: string;
  attachments?: string;

  emailSubjectTpl?: string;
  emailBodyTpl?: string;

  smsTemplateCode?: string;
  smsParamMapping?: string;
  smsContentTpl?: string;

  inAppTitleTpl?: string;
  inAppContentTpl?: string;

  pushTitleTpl?: string;
  pushBodyTpl?: string;
  pushPayloadTpl?: string;

  wxMpAppCode?: string;
  wxMpTemplateId?: string;
  wxMpUrl?: string;
  wxMpDataMapping?: string;

  wxMaAppCode?: string;
  wxMaTemplateId?: string;
  wxMaPage?: string;
  wxMaDataMapping?: string;
  wxMaMiniprogramState?: string;

  wxCpAgentId?: number;
  wxCpMsgType?: string;
  wxCpTitle?: string;
  wxCpContentTpl?: string;
  wxCpUrl?: string;

  wxBotMsgType?: string;
  wxBotContentTpl?: string;
  wxBotMentionUserIds?: string;
  createBy?: string;
  createTime?: string;
  updateBy?: string;
  updateTime?: string;
}

/** 模板预览返回 */
export interface MessageTemplatePreview {
  emailSubject?: string;
  emailBody?: string;
  smsTemplateCode?: string;
  smsTemplateParam?: string;
  smsContent?: string;
  inAppTitle?: string;
  inAppContent?: string;
  pushTitle?: string;
  pushBody?: string;
  pushPayload?: string;
  wxMpAppCode?: string;
  wxMpTemplateId?: string;
  wxMpUrl?: string;
  wxMpData?: string;
  wxMaAppCode?: string;
  wxMaTemplateId?: string;
  wxMaPage?: string;
  wxMaData?: string;
  wxMaMiniprogramState?: string;
  wxCpAgentId?: number;
  wxCpMsgType?: string;
  wxCpTitle?: string;
  wxCpContent?: string;
  wxCpUrl?: string;
  wxBotMsgType?: string;
  wxBotContent?: string;
  wxBotMentionUserIds?: string;
}

/** 站内信收件箱行（当前用户视角） */
export interface MessageInboxItem {
  id: string;
  notifyId: string;
  title: string;
  summary?: string;
  content?: string;
  bizType?: string;
  /** 重要级别（公告会带，普通站内信通常为空） */
  level?: EnumMessageNotifyLevel | string;
  /** 点击跳转 URL；前端"点行查看"会优先打开此链接而非详情对话框 */
  clickUrl?: string;
  readStatus: EnumMessageInAppReadStatus;
  readTime?: string;
  createTime: string;
}

/** 订阅偏好（一行一个 bizType） */
export interface MessageSubscriptionItem {
  bizType: string;
  disabledChannels: EnumMessageNotifyChannel[];
}

export interface MessageSubscriptionVo {
  items: MessageSubscriptionItem[];
}
