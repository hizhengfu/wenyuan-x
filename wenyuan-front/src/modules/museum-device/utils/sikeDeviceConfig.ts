export interface SikeDeviceConfig {
  serviceUrl: string;
  connectType: number;
  connectAddress: string;
  antennaMask: number;
  tidWordLength: number;
}

/** 斯科随附服务 application.yaml 的 9090 端口及 demo 使用的本机端点。 */
export const SIKE_DEFAULT_SERVICE_URL = 'ws://127.0.0.1:9090/rfidService/1234';

/** 厂家 demo 的连接示例；USB-HID 地址必须由本机服务扫描取得。 */
export function sikeDefaultConnectAddress(connectType: number): string {
  if (connectType === 1) return '192.168.1.168:8160';
  if (connectType === 2) return 'COM7:115200';
  if (connectType === 3) return 'COM7:115200:1';
  return '';
}

export const emptySikeDeviceConfig = (): SikeDeviceConfig => ({
  serviceUrl: SIKE_DEFAULT_SERVICE_URL,
  connectType: 1,
  connectAddress: sikeDefaultConnectAddress(1),
  antennaMask: 1,
  tidWordLength: 6,
});

export const isSikeDeviceModel = (modelCode?: string | null) =>
  modelCode === 'SIKE_C5_WS_READER_V1' || modelCode === 'SIKE_5A_WS_READER_V1';

export function parseSikeDeviceConfig(raw?: string | null): { config: SikeDeviceConfig; error: string } {
  const config = emptySikeDeviceConfig();
  if (!raw?.trim()) return { config, error: '' };
  try {
    const value = JSON.parse(raw);
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('invalid');
    return {
      config: {
        serviceUrl: typeof value.serviceUrl === 'string' ? value.serviceUrl : '',
        connectType: Number(value.connectType ?? 1),
        connectAddress: typeof value.connectAddress === 'string' ? value.connectAddress : '',
        antennaMask: Number(value.antennaMask ?? 1),
        tidWordLength: Number(value.tidWordLength ?? 6),
      },
      error: '',
    };
  } catch {
    return {
      config: { ...config, serviceUrl: '', connectAddress: '' },
      error: '原有斯科实例配置不是有效 JSON，请核对后重新填写连接参数。',
    };
  }
}

export function validateSikeDeviceConfig(config: SikeDeviceConfig, antennaCount?: number | null): string {
  const urlError = validateSikeServiceUrl(config.serviceUrl);
  if (urlError) return urlError;
  if (![1, 2, 3, 4].includes(config.connectType)) return '请选择读写器连接方式';
  if (!config.connectAddress.trim() || config.connectAddress.trim().length > 100) return '请输入读写器连接地址';
  const maxAntennas = Math.min(16, Number(antennaCount) || 16);
  if (!Number.isInteger(config.antennaMask) || config.antennaMask < 1 || config.antennaMask >= 2 ** maxAntennas) {
    return `请在已登记的 ${maxAntennas} 个天线中选择至少一个工作天线`;
  }
  if (!Number.isInteger(config.tidWordLength) || config.tidWordLength < 1 || config.tidWordLength > 16) {
    return 'TID 读取长度应为 1 至 16 个字';
  }
  return '';
}

function validateSikeServiceUrl(serviceUrl: string): string {
  let url: URL;
  try {
    url = new URL(serviceUrl.trim());
  } catch {
    return '请输入有效的本机 WebSocket 服务地址';
  }
  if (
    url.protocol !== 'ws:' || url.hostname !== '127.0.0.1' ||
    !/^ws:\/\/127\.0\.0\.1:\d+\/rfidService\//.test(serviceUrl.trim()) ||
    !Number(url.port) || !/^\/rfidService\/[A-Za-z0-9_-]{1,64}$/.test(url.pathname) ||
    url.search || url.hash || url.username || url.password
  ) return '服务地址必须为 ws://127.0.0.1:端口/rfidService/标识';
  return '';
}

export function serializeSikeDeviceConfig(config: SikeDeviceConfig): string {
  return JSON.stringify({
    serviceUrl: config.serviceUrl.trim(),
    connectType: config.connectType,
    connectAddress: config.connectAddress.trim(),
    antennaMask: config.antennaMask,
    tidWordLength: config.tidWordLength,
  });
}

export function sikeAntennaNumbers(mask: number): number[] {
  return Array.from({ length: 16 }, (_, index) => index + 1).filter((antenna) => (mask & (1 << (antenna - 1))) !== 0);
}

/** USB-HID 没有固定地址，厂商协议提供已接入 HID 的只读查询。 */
export async function discoverSikeHidAddress(serviceUrl: string): Promise<string> {
  const error = validateSikeServiceUrl(serviceUrl);
  if (error) throw new Error(error);
  return new Promise<string>((resolve, reject) => {
    const socket = new WebSocket(serviceUrl.trim());
    let finished = false;
    const timeout = window.setTimeout(() => fail('USB-HID 扫描超时，请检查本机服务'), 8000);
    const close = () => { window.clearTimeout(timeout); socket.close(); };
    const fail = (reason: string) => {
      if (finished) return;
      finished = true;
      close();
      reject(new Error(reason));
    };
    socket.onopen = () => {
      socket.send(JSON.stringify({ wid: 4, baseOrApp: 2, msgId: 0x03, data: {} }));
    };
    socket.onmessage = (event) => {
      let frame: { wid?: number; msgId?: number; result?: number; data?: unknown };
      try { frame = JSON.parse(event.data); } catch { return; }
      if (frame.wid !== 4 || frame.msgId !== 0x03) return;
      if (frame.result !== 0) { fail(`USB-HID 扫描失败：${String(frame.data || frame.result)}`); return; }
      const address = Array.isArray(frame.data)
        ? frame.data.find((item): item is string => typeof item === 'string' && item.trim().length > 0)?.trim()
        : undefined;
      if (!address) { fail('未扫描到 USB-HID 设备，请检查连接后重试'); return; }
      finished = true;
      close();
      resolve(address);
    };
    socket.onerror = () => fail('无法连接本机斯科服务，请在设备所在的工作站电脑上操作');
    socket.onclose = () => fail('本机服务已断开');
  });
}

/** 登记时只执行厂商连接及设备信息查询，不启动盘存或写卡。 */
export async function probeSikeReader(config: SikeDeviceConfig, antennaCount?: number | null): Promise<string> {
  const error = validateSikeDeviceConfig(config, antennaCount);
  if (error) throw new Error(error);
  return new Promise<string>((resolve, reject) => {
    const socket = new WebSocket(config.serviceUrl.trim());
    let finished = false;
    let waitingForIdentity = false;
    const timeout = window.setTimeout(() => fail('读写器查询超时，请检查本机服务与连接地址'), 8000);
    const close = () => { window.clearTimeout(timeout); socket.close(); };
    const fail = (reason: string) => {
      if (finished) return;
      finished = true;
      close();
      reject(new Error(reason));
    };
    socket.onopen = () => {
      socket.send(JSON.stringify({ wid: 0, connectType: config.connectType, data: config.connectAddress.trim() }));
    };
    socket.onmessage = (event) => {
      let frame: { wid?: number; msgId?: number; result?: number; data?: unknown };
      try { frame = JSON.parse(event.data); } catch { return; }
      if (!waitingForIdentity && frame.wid === 0) {
        if (frame.result !== 0) { fail(`读写器连接失败：${String(frame.data || frame.result)}`); return; }
        waitingForIdentity = true;
        socket.send(JSON.stringify({ wid: 4, baseOrApp: 1, msgId: 0x00, data: {} }));
      } else if (waitingForIdentity && frame.wid === 4 && frame.msgId === 0x00) {
        const data = frame.data as { readerSerialNumber?: unknown } | null;
        const serial = typeof data?.readerSerialNumber === 'string' ? data.readerSerialNumber.trim() : '';
        if (frame.result !== 0 || !serial) { fail('读写器未返回有效的硬件序列号'); return; }
        finished = true;
        close();
        resolve(serial);
      }
    };
    socket.onerror = () => fail('无法连接本机斯科服务，请在设备所在的工作站电脑上操作');
    socket.onclose = () => fail('本机服务已断开');
  });
}
