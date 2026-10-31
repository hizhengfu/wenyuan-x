export type StreamFailureType = 'SOURCE_CONNECTION' | 'SOURCE_PROTOCOL' | 'CODEC_MISMATCH' | 'SIGNALING' | 'UNKNOWN';

export type StreamFailureMode = 'mse' | 'webrtc' | 'unknown';

export interface StreamFailureReport {
  type: StreamFailureType;
  code:
    | 'CONNECTION_REFUSED'
    | 'CONNECTION_TIMEOUT'
    | 'CONNECTION_RESET'
    | 'SOURCE_EOF'
    | 'NETWORK_UNREACHABLE'
    | 'RTSP_DESCRIBE_FAILED'
    | 'SOURCE_UNAUTHORIZED'
    | 'STREAM_NOT_FOUND'
    | 'INVALID_SOURCE_RESPONSE'
    | 'CODEC_NOT_MATCHED'
    | 'UNSUPPORTED_CODEC'
    | 'H265_DECODE_FAILED'
    | 'WEBRTC_SIGNALING_FAILED'
    | 'UNKNOWN_STREAM_ERROR';
  mode: StreamFailureMode;
}

function detectMode(message: string): StreamFailureMode {
  if (/\bmse\s*:/i.test(message)) return 'mse';
  if (/\bwebrtc(?:\/|\s*:)/i.test(message)) return 'webrtc';
  return 'unknown';
}

/**
 * 将 go2rtc 错误归一化为稳定的错误类型和错误码。
 * 不返回原始文本，避免后续上报时意外携带源地址或凭证。
 */
export function classifyStreamFailure(message: string): StreamFailureReport {
  const normalized = message.trim().toLowerCase();
  const mode = detectMode(message);

  if (normalized.includes('codecs not matched') || normalized.includes('codec not matched')) {
    return { type: 'CODEC_MISMATCH', code: 'CODEC_NOT_MATCHED', mode };
  }
  if (
    normalized.includes('unsupported codec') ||
    normalized.includes('codec unsupported') ||
    normalized.includes('no supported codec')
  ) {
    return { type: 'CODEC_MISMATCH', code: 'UNSUPPORTED_CODEC', mode };
  }

  if (normalized.includes('wrong response on describe')) {
    return { type: 'SOURCE_PROTOCOL', code: 'RTSP_DESCRIBE_FAILED', mode };
  }
  if (
    normalized.includes('401 unauthorized') ||
    normalized.includes('403 forbidden') ||
    normalized.includes('authentication failed')
  ) {
    return { type: 'SOURCE_PROTOCOL', code: 'SOURCE_UNAUTHORIZED', mode };
  }
  if (normalized.includes('no such stream')) {
    return { type: 'SOURCE_PROTOCOL', code: 'STREAM_NOT_FOUND', mode };
  }
  if (
    normalized.includes('invalid sdp') ||
    normalized.includes('invalid rtsp') ||
    normalized.includes('unsupported scheme')
  ) {
    return { type: 'SOURCE_PROTOCOL', code: 'INVALID_SOURCE_RESPONSE', mode };
  }

  if (normalized.includes('connection refused')) {
    return { type: 'SOURCE_CONNECTION', code: 'CONNECTION_REFUSED', mode };
  }
  if (
    normalized.includes('i/o timeout') ||
    normalized.includes('connection timeout') ||
    normalized.includes('context deadline exceeded') ||
    normalized.includes('probe producer timeout')
  ) {
    return { type: 'SOURCE_CONNECTION', code: 'CONNECTION_TIMEOUT', mode };
  }
  if (normalized.includes('connection reset') || normalized.includes('broken pipe')) {
    return { type: 'SOURCE_CONNECTION', code: 'CONNECTION_RESET', mode };
  }
  if (/\beof\b/.test(normalized)) {
    return { type: 'SOURCE_CONNECTION', code: 'SOURCE_EOF', mode };
  }
  if (normalized.includes('network is unreachable') || normalized.includes('no route to host')) {
    return { type: 'SOURCE_CONNECTION', code: 'NETWORK_UNREACHABLE', mode };
  }

  if (
    normalized.includes('webrtc/offer') ||
    normalized.includes('webrtc/answer') ||
    normalized.includes('ice failed')
  ) {
    return { type: 'SIGNALING', code: 'WEBRTC_SIGNALING_FAILED', mode: 'webrtc' };
  }

  return { type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR', mode };
}

export function isRefreshableSourceFailure(report: StreamFailureReport): boolean {
  return report.type === 'SOURCE_CONNECTION' || report.type === 'SOURCE_PROTOCOL';
}

/**
 * 首帧前可先复用当前 go2rtc 播放地址重试一次的慢源错误。
 * 这里只包含可能由 RTSP 源启动较慢引起的超时/DESCRIBE 失败；鉴权、地址不存在和无效协议仍立即刷新源。
 */
export function isSameSourceRetryableFailure(report: StreamFailureReport): boolean {
  return (
    (report.type === 'SOURCE_CONNECTION' && report.code === 'CONNECTION_TIMEOUT') ||
    (report.type === 'SOURCE_PROTOCOL' && report.code === 'RTSP_DESCRIBE_FAILED')
  );
}

/** 后端可直接映射为枚举的安全参数；不上传 go2rtc 原始错误文本。 */
export function streamFailureParams(report?: StreamFailureReport): Record<string, string> {
  if (!report) return {};
  return {
    errorType: report.type,
    errorCode: report.code,
    errorMode: report.mode,
  };
}

export function streamFailureMessage(report: StreamFailureReport): string {
  switch (report.type) {
    case 'CODEC_MISMATCH':
      return '当前视频编码与浏览器不兼容，正在请求兼容播放方案';
    case 'SOURCE_CONNECTION':
      return '视频源连接失败，正在重新获取播放地址';
    case 'SOURCE_PROTOCOL':
      return '视频源协议异常，正在切换备用播放源';
    case 'SIGNALING':
      return '视频信令连接异常，正在重新连接';
    default:
      return '流媒体服务返回未知错误，正在重新连接';
  }
}
