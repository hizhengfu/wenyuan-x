import { getAuthenticationHeaders } from '@/utils/auth';
import request from '@/utils/request'
import { useAuthStore } from '@/store'

/** AI 能力状态：{ chatAvailable, embeddingAvailable, model } */
export function status() {
  return request({ url: 'ai/status', method: 'get' })
}

/** 回显当前 AI 配置（apiKey 脱敏） */
export function getConfig() {
  return request({ url: 'ai/config', method: 'get' })
}

/** 测试连接：{ baseUrl?, apiKey?, chatModel? } 留空回退已存配置 */
export function testConn(data: any) {
  return request({ url: 'ai/config/test', method: 'post', data })
}

/** 保存 AI 配置：{ enabled, baseUrl, apiKey?, chatModel, temperature?, maxTokens?, embeddingModel? } */
export function saveConfig(data: any) {
  return request({ url: 'ai/config', method: 'post', data })
}

/** 对话：{ system?, user?, messages?, options? } → { content, model } */
export function chat(data: any) {
  return request({ url: 'ai/chat', method: 'post', data })
}

export interface StreamResult {
  /** false 表示服务端未以 event-stream 响应（异常/网关缓冲），调用方应回退普通 chat() */
  streamed: boolean
  model?: string
}

/**
 * 流式对话（SSE）。增量经 onDelta 回调，结束返回 { streamed, model }。
 * 用原生 fetch（axios 不支持流式读取），手动复用 request 的鉴权头与 baseURL。
 * 过程出错（HTTP 非 2xx / error 事件 / 网络中断）会 throw，由调用方决定是否回退。
 */
export async function streamChat(
  data: { user?: string; system?: string; messages?: any[]; options?: any },
  onDelta: (text: string) => void,
  signal?: AbortSignal,
): Promise<StreamResult> {
  const authStore = useAuthStore()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  Object.assign(headers, getAuthenticationHeaders(authStore.getToken, authStore.authorizationName))

  const resp = await fetch(`${APP_BASE_URL}${APP_API_PATH}/ai/chat/stream`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(data),
    signal,
  })
  if (!resp.ok || !resp.body) throw new Error(`stream_http_${resp.status}`)
  if (!(resp.headers.get('content-type') || '').includes('text/event-stream')) {
    return { streamed: false }
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let model: string | undefined
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    let sep: number
    while ((sep = buf.indexOf('\n\n')) >= 0) {
      const evt = buf.slice(0, sep)
      buf = buf.slice(sep + 2)
      const line = evt.split('\n').find((l) => l.startsWith('data:'))
      if (!line) continue
      const payload = line.slice(5).trim()
      if (!payload || payload === '[DONE]') continue
      let obj: any
      try {
        obj = JSON.parse(payload)
      } catch {
        continue
      }
      if (obj.error) throw new Error(obj.error)
      if (obj.delta) onDelta(obj.delta as string)
      if (obj.model) model = obj.model as string
      if (obj.done) return { streamed: true, model }
    }
  }
  return { streamed: true, model }
}
