import request, { type HttpResponse } from '@/utils/request';
import { version } from '~/package.json';

/** 统一个人列表类型，分别对应浏览、分享、收藏和点赞的分页入口。 */
export type InteractionListKind = 'recent' | 'shares' | 'favorites' | 'likes';
/** 客户端分组及版本；type + appId 决定浏览记录归属，版本不参与页面去重。 */
export interface InteractionClient {
  type: 'WEB' | 'MINI_PROGRAM' | 'APP';
  /** 同一客户端类型下的产品标识，必须与采集端保持一致。 */
  appId: string;
  /** 产品版本，供诊断和事件归因使用。 */
  version?: string | null;
  /** 路由协议版本，只有明确匹配时才能使用旧路径回退。 */
  routeVersion?: string | null;
}
/** 公共目录提供的页面描述，key + params 为稳定身份，path 仅作兼容条件满足时的导航回退。 */
export interface InteractionPage {
  key: string;
  /** 仅放区分页面的身份参数，例如内容ID；不包含令牌或临时埋点参数。 */
  params: Record<string, string>;
  title: string;
  cover?: string | null;
  path?: string | null;
}
/** 个人列表项；失效内容保留记录ID供移除，page 为空，time 使用 epoch 毫秒。 */
export interface InteractionItem {
  /** 浏览/分享为个人记录ID；收藏/点赞为统一对象ID。 */
  id: string;
  /** 公共目录对象ID；未登记的私有页面浏览可为空。 */
  targetId: string | null;
  page: InteractionPage | null;
  available: boolean;
  time: number;
  /** 浏览或分享的来源快照；收藏/点赞关系不记录来源客户端。 */
  client?: InteractionClient | null;
  channel?: string | null;
}
/** 服务端分页结果；pageNum 从1开始，pageSize=0 表示全部，total 为真实总数。 */
export interface InteractionList {
  list: InteractionItem[];
  total: number;
  pageNum: number;
  pageSize: number;
}

// 采集和个人中心共用；default 分组兼容原有 Web 历史，产品可在 setupRouter 时统一覆盖。
export const defaultWebInteractionClient: InteractionClient = {
  type: 'WEB',
  appId: 'default',
  version,
  routeVersion: '1',
};

/** 应用启动时配置 Web 产品与版本，避免上报和最近浏览查询使用不同的客户端分组。 */
export function configureWebInteractionClient(client: Partial<Omit<InteractionClient, 'type'>>) {
  Object.assign(defaultWebInteractionClient, client, { type: 'WEB' });
}

/**
 * 一次成功展示只上报一次，重试复用请求体中的 eventId；浏览计数与历史由后端统一缓冲。
 * 路由守卫不猜测业务 targetId；业务页面可在成功加载后显式提供已登记的对象ID。
 */
export function recordInteractionView(
  page: InteractionPage,
  client: InteractionClient = defaultWebInteractionClient,
  targetId?: string,
) {
  return request({
    url: 'interaction/view',
    method: 'post',
    data: {
      eventId: eventId(),
      client: { ...client },
      page: { ...page, params: { ...page.params } },
      ...(targetId ? { targetId } : {}),
    },
    silentError: true,
  });
}

/**
 * 查询指定个人列表，用户由登录态决定。仅浏览列表按客户端类型和产品隔离，其余列表跨端共享。
 * 允许调用方取消旧请求；失败交给页面显示，认证失败仍沿用统一请求拦截器。
 */
export function interactionList(
  kind: InteractionListKind,
  pageNum: number,
  pageSize: number,
  client: InteractionClient,
  signal?: AbortSignal,
) {
  return request<unknown, HttpResponse<InteractionList>>({
    url: `interaction/${kind}`,
    method: 'get',
    params: { pageNum, pageSize, ...(kind === 'recent' ? { clientType: client.type, appId: client.appId } : {}) },
    signal,
    silentError: true,
  });
}

/** 为一次真实状态操作生成去重ID；统一请求层重试会复用已经生成的请求体。 */
function eventId(): string {
  // getRandomValues 在 HTTP 内网也可用，不依赖仅限安全上下文的 randomUUID。
  return Array.from(crypto.getRandomValues(new Uint8Array(16)), (value) => value.toString(16).padStart(2, '0')).join(
    '',
  );
}

/**
 * 通过 POST 移除一项：浏览/分享删除个人记录，收藏/点赞明确设置为取消。
 * 删除分享记录不回减累计分享次数；失效收藏/点赞仍可按对象ID取消。
 */
export function removeInteraction(kind: InteractionListKind, item: InteractionItem, client: InteractionClient) {
  const state = kind === 'favorites' || kind === 'likes';
  return request({
    url: `interaction/${state ? (kind === 'favorites' ? 'favorite' : 'like') : kind}/delete`,
    method: 'post',
    data: state
      ? { eventId: eventId(), client, targetId: item.targetId || item.id, enabled: false }
      : { ids: [item.id], ...(kind === 'recent' ? { client } : {}) },
    silentError: true,
  });
}

/** 通过 POST 清空本人当前客户端的浏览，或本人全部分享记录；不清空累计统计。 */
export function clearInteractions(kind: 'recent' | 'shares', client: InteractionClient) {
  return request({
    url: `interaction/${kind}/clear`,
    method: 'post',
    data: kind === 'recent' ? { client } : undefined,
    silentError: true,
  });
}
