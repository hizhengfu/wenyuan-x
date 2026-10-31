/** 阶梯式重连延迟；持续失败时逐步退避，大屏场景不停止重试。 */
const RECONNECT_STEPS = [3_000, 5_000, 10_000, 20_000, 30_000, 60_000];

/** 稳定播放超过 30 秒后断流时优先快速恢复，否则按失败次数退避。 */
export function calcReconnectDelay(count: number, elapsed = 0): number {
  if (elapsed > 30_000) return 2_000;
  return RECONNECT_STEPS[Math.min(count, RECONNECT_STEPS.length - 1)];
}

/** 由绝对截止时间计算倒计时，向上取整后自然呈现 3、2、1。 */
export function getRetryCountdownSeconds(deadline: number, now = Date.now()): number {
  return Math.max(0, Math.ceil((deadline - now) / 1_000));
}

export function formatRetryHint(seconds: number, attempt: number): string {
  return `${seconds}s 后自动重连（第 ${attempt} 次）`;
}
