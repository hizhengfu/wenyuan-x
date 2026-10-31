import request from '@/utils/request';

/**
 * 通知中心 - 通知主体接口
 *
 * 后端控制器路径前缀：${apiPath}/notify-center
 */

/**
 * 分页查询
 * */
export function findPage(params: object) {
  return request({
    url: 'notify-center/page',
    method: 'get',
    params,
  });
}

/**
 * 详情（含 dispatch 状态聚合）
 * */
export function detail(id: string) {
  return request({
    url: `notify-center/${id}`,
    method: 'get',
  });
}

/**
 * 发起通知（手填路径）
 * */
export function create(data: object) {
  return request({
    url: 'notify-center',
    method: 'post',
    data,
  });
}

/**
 * 取消（仅 SCHEDULED / PROCESSING 可点）
 * */
export function cancel(id: string) {
  return request({
    url: `notify-center/${id}/cancel`,
    method: 'post',
  });
}

/**
 * 手动重发该 notify 下所有 FAIL 的 dispatch 行
 * */
export function retry(id: string) {
  return request({
    url: `notify-center/${id}/retry`,
    method: 'post',
  });
}

/**
 * 投递记录全局检索（运维 / 排错向）。
 *
 * 与"通知详情里的 dispatches"不同，本接口跨通知扁平查询：
 * 例如"用户 A 最近 7 天的所有投递"、"WX_CP 通道全部 FAIL 行"。
 * 支持过滤：notifyId / bizType / userName / channel / dispatchStatus / days（默认 30 天）。
 * 返回行含 bizType / bizId / templateCode / templateParam / content，并尽量补齐 userName 与 notifyTitle。
 * */
export function dispatchPage(params: object) {
  return request({
    url: 'notify-center/dispatch/page',
    method: 'get',
    params,
  });
}

/**
 * 当前在 sys_config 中启用的通道枚举名列表（如 ["EMAIL","IN_APP"]）。
 * 仅适合需要纯名称的场景；带 label 的 UI 选项请用 [enabledChannelOptions]。
 * */
export function enabledChannels() {
  return request({
    url: 'notify-center/enabled-channels',
    method: 'get',
  });
}

/**
 * 启用通道的可视化选项 `[{value,label}]`，后端已合并 `notify_channel` 字典 label
 * 与 `NOTIFY_<CHANNEL>_ENABLED` 启用配置。
 *
 * 前端 ChannelSelector / SubscriptionView 直接消费，无需自行做"字典+启用"过滤。
 * label 优先取字典（运维改名实时生效），字典缺项时兜底为 enum.description。
 * */
export function enabledChannelOptions() {
  return request({
    url: 'notify-center/enabled-channel-options',
    method: 'get',
  });
}
