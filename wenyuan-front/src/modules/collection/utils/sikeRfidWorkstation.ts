import { rfidApi, rfidRequestId } from '../api/rfid';
import type { RfidScanSession } from '../typings/rfid';

export interface SikeLocalDevice {
  deviceId: string;
  deviceName: string;
  modelCode: 'SIKE_C5_WS_READER_V1' | 'SIKE_5A_WS_READER_V1';
  hardwareSerial: string;
  config: {
    serviceUrl: string;
    connectType: number;
    connectAddress: string;
    antennaMask: number;
    tidWordLength: number;
  };
}

interface SikeFrame {
  wid: number;
  baseOrApp?: number;
  msgId?: number;
  result?: number;
  data?: unknown;
}

interface SikeCommand {
  id: string;
  commandType: 'START_INVENTORY' | 'STOP_INVENTORY' | 'WRITE_EPC';
  businessId?: string;
  request: Record<string, unknown>;
}

const HEX = /^[0-9A-F]+$/;

export function sikeWriteData(epcValue: string): string {
  const epc = epcValue.trim().toUpperCase();
  if (epc.length < 4 || epc.length > 124 || epc.length % 2 !== 0 || !HEX.test(epc)) {
    throw new Error('EPC 必须是有效的偶数位十六进制');
  }
  // 厂商 demo getPc：EPC 按 16 bit 字补齐，字数放在 PC 的高 5 位。
  const words = Math.ceil(epc.length / 4);
  const pc = (words << 11).toString(16).padStart(4, '0').toUpperCase();
  return pc + epc.padEnd(words * 4, '0');
}

export function sikeTagReads(data: unknown): Array<{ epc: string; tid?: string; antenna?: number; rssi?: number; readerSerialNumber?: string }> {
  const values = Array.isArray(data) ? data : [data];
  return values.flatMap((value) => {
    if (!value || typeof value !== 'object') return [];
    const tag = value as Record<string, unknown>;
    const epc = typeof tag.epc === 'string' ? tag.epc.trim().toUpperCase() : '';
    const tid = typeof tag.tid === 'string' ? tag.tid.trim().toUpperCase() : undefined;
    if (!epc || epc.length % 2 !== 0 || !HEX.test(epc) || (tid && (tid.length % 2 !== 0 || !HEX.test(tid)))) return [];
    const antenna = Number(tag.antId);
    const rssi = Number(tag.rssi);
    const readerSerialNumber = typeof tag.readerSerialNumber === 'string' ? tag.readerSerialNumber.trim() : undefined;
    return [{
      epc,
      tid,
      antenna: Number.isInteger(antenna) && antenna > 0 && antenna <= 32 ? antenna : undefined,
      rssi: Number.isFinite(rssi) && rssi >= -150 && rssi <= 0 ? Math.round(rssi) : undefined,
      readerSerialNumber,
    }];
  });
}

export function sikeMatchesReaderIdentity(data: unknown, expectedSerial: string): boolean {
  if (!data || typeof data !== 'object') return false;
  const serial = (data as Record<string, unknown>).readerSerialNumber;
  return typeof serial === 'string' && serial.trim().length > 0 &&
    serial.trim().toUpperCase() === expectedSerial.trim().toUpperCase();
}

class SikeDeviceRuntime {
  private socket?: WebSocket;
  private connectionId = '';
  private verifiedReaderSerial = '';
  private stopped = false;
  private connecting = false;
  private busy = false;
  private timer?: number;
  private retryAfter = 0;
  private lastHeartbeatAt = 0;
  private pending?: { wid: number; msgId?: number; resolve: (frame: SikeFrame) => void; reject: (reason: Error) => void; timer: number };
  private sessionId = '';
  private inventoryRunning = false;
  private acceptingTags = false;
  private readonly streamId = rfidRequestId();
  private sequence = 0;
  private pendingBatch?: { sessionId: string; sequence: number; ids: string[] };
  private readQueue: Array<{ sessionId: string; epc: string; tid?: string; antenna?: number; rssi?: number; readerSerialNumber?: string; sourceEventId: string }> = [];

  constructor(private readonly device: SikeLocalDevice, private readonly onState: (deviceId: string, state: string) => void) {}

  get deviceId() { return this.device.deviceId; }

  stopInventoryBestEffort() {
    if (this.inventoryRunning && this.socket?.readyState === WebSocket.OPEN) {
      try { this.socket.send(JSON.stringify({ wid: 4, baseOrApp: 0, msgId: 0xff, data: {} })); } catch { /* 即将关闭连接 */ }
    }
  }

  start() {
    this.timer = window.setInterval(() => { void this.tick(); }, 1000);
    void this.tick();
  }

  async drainCommands() {
    for (let attempt = 0; attempt < 30 && this.busy; attempt += 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 100));
    }
    if (!this.busy) await this.tick();
  }

  setSession(session: RfidScanSession | null) {
    if (session?.deviceId === this.device.deviceId && ['STARTING', 'READING', 'STOPPING'].includes(session.state)) {
      this.sessionId = session.id;
    }
  }

  private async tick() {
    if (this.stopped || this.busy || this.connecting) return;
    if (!this.connectionId) {
      if (Date.now() < this.retryAfter) return;
      await this.connect();
      return;
    }
    this.busy = true;
    try {
      if (Date.now() - this.lastHeartbeatAt >= 10_000) {
        await rfidApi.sikeHeartbeat(this.device.deviceId, this.connectionId);
        this.lastHeartbeatAt = Date.now();
      }
      const response: any = await rfidApi.sikeCommands(this.device.deviceId, this.connectionId);
      for (const command of (response?.data || []) as SikeCommand[]) {
        await this.execute(command);
      }
      await this.flushReads();
    } catch (error) {
      this.onState(this.device.deviceId, error instanceof Error ? error.message : '本机连接中断');
      await this.disconnect();
      this.retryAfter = Date.now() + 3000;
    } finally {
      this.busy = false;
    }
  }

  private async connect() {
    this.connecting = true;
    this.onState(this.device.deviceId, '正在连接本机服务');
    try {
      const socket = await new Promise<WebSocket>((resolve, reject) => {
        const ws = new WebSocket(this.device.config.serviceUrl);
        const timer = window.setTimeout(() => { ws.close(); reject(new Error('本机服务连接超时')); }, 5000);
        ws.onopen = () => { window.clearTimeout(timer); resolve(ws); };
        ws.onerror = () => { window.clearTimeout(timer); reject(new Error('无法访问本机服务；请检查服务及浏览器本地网络权限')); };
      });
      if (this.stopped) { socket.close(); return; }
      this.socket = socket;
      socket.onmessage = (event) => this.onMessage(event.data);
      socket.onclose = () => {
        this.onState(this.device.deviceId, '本机连接已断开，扫描会话需重新核对');
        void this.disconnect();
      };
      const result = await this.send({ wid: 0, connectType: this.device.config.connectType, data: this.device.config.connectAddress } as SikeFrame);
      if (result.result !== 0) throw new Error(`读写器连接失败：${String(result.data || result.result)}`);
      const identity = await this.send({ wid: 4, baseOrApp: 1, msgId: 0x00, data: {} });
      if (identity.result !== 0 || !sikeMatchesReaderIdentity(identity.data, this.device.hardwareSerial)) {
        throw new Error('读写器硬件序列号与设备档案不一致，已拒绝执行命令');
      }
      this.verifiedReaderSerial = (identity.data as { readerSerialNumber: string }).readerSerialNumber.trim().toUpperCase();
      const response: any = await rfidApi.sikeConnect(this.device.deviceId);
      this.connectionId = String(response?.data?.connectionId || '');
      if (!this.connectionId) throw new Error('未取得服务端设备连接标识');
      this.lastHeartbeatAt = Date.now();
      this.onState(this.device.deviceId, '已连接');
    } catch (error) {
      this.onState(this.device.deviceId, error instanceof Error ? error.message : '连接失败');
      await this.disconnect();
      this.retryAfter = Date.now() + 3000;
    } finally {
      this.connecting = false;
    }
  }

  private onMessage(payload: string) {
    let frame: SikeFrame;
    try { frame = JSON.parse(payload) as SikeFrame; } catch { return; }
    if (this.pending && frame.wid === this.pending.wid && (this.pending.msgId === undefined || frame.msgId === this.pending.msgId)) {
      const pending = this.pending;
      this.pending = undefined;
      window.clearTimeout(pending.timer);
      pending.resolve(frame);
      return;
    }
    if (frame.wid === 5 && this.sessionId && this.acceptingTags) {
      for (const read of sikeTagReads(frame.data)) {
        // 5A 标签日志的序列号扩展字段可省略，缺省时使用本连接设备信息查询已核验的身份。
        const readerSerialNumber = read.readerSerialNumber?.trim().toUpperCase() || this.verifiedReaderSerial;
        if (!this.verifiedReaderSerial || readerSerialNumber !== this.verifiedReaderSerial) {
          this.onState(this.device.deviceId, '硬件序列号与设备档案不一致，扫描已中断');
          void this.disconnect();
          break;
        }
        if (this.readQueue.length >= 1000) {
          this.onState(this.device.deviceId, '读数缓冲已满，扫描已中断；请核对会话');
          void this.disconnect();
          break;
        }
        this.readQueue.push({ sessionId: this.sessionId, ...read, readerSerialNumber, sourceEventId: rfidRequestId() });
      }
    }
    if (frame.wid === 11) { void this.disconnect(); }
  }

  private send(frame: SikeFrame): Promise<SikeFrame> {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN || this.pending) return Promise.reject(new Error('本机服务暂不可用'));
    return new Promise((resolve, reject) => {
      const timer = window.setTimeout(() => {
        this.pending = undefined;
        reject(new Error('读写器命令应答超时'));
      }, 8000);
      this.pending = { wid: frame.wid, msgId: frame.msgId, resolve, reject, timer };
      this.socket!.send(JSON.stringify(frame));
    });
  }

  private async execute(command: SikeCommand) {
    let success = false;
    let message = '';
    let discardConnection = false;
    try {
      const mask = this.device.config.antennaMask;
      if (!Number.isInteger(mask) || mask < 1 || mask > 65535) throw new Error('设备天线配置无效');
      let frame: SikeFrame;
      if (command.commandType === 'START_INVENTORY') {
        this.sessionId = String(command.request.sessionId || command.businessId || '');
        this.acceptingTags = true;
        frame = { wid: 4, baseOrApp: 0, msgId: 0x10, data: { antennaEnable: mask, inventoryMode: 1, readTid: { mode: 0, len: this.device.config.tidWordLength } } };
      } else if (command.commandType === 'STOP_INVENTORY') {
        frame = { wid: 4, baseOrApp: 0, msgId: 0xff, data: {} };
      } else if (command.commandType === 'WRITE_EPC') {
        const tid = String(command.request.tidFilter || '').toUpperCase();
        if (!tid || tid.length % 2 !== 0 || !HEX.test(tid)) throw new Error('写标命令缺少有效 TID 过滤条件');
        frame = { wid: 4, baseOrApp: 0, msgId: 0x11, data: {
          antennaEnable: mask, area: 1, start: 1, hexPassword: '',
          hexWriteData: sikeWriteData(String(command.request.epc || '')),
          filter: { area: 2, bitStart: 0, bitLength: tid.length * 4, hexData: tid },
        } };
      } else {
        throw new Error('不支持的设备命令');
      }
      const response = await this.send(frame);
      success = response.result === 0;
      message = success ? '设备已确认' : String(response.data || `设备返回 ${response.result}`);
      if (command.commandType === 'START_INVENTORY' && success) this.inventoryRunning = true;
      if (command.commandType === 'STOP_INVENTORY' && success) this.inventoryRunning = false;
      if (command.commandType === 'START_INVENTORY' && !success) {
        this.acceptingTags = false;
        this.sessionId = '';
        this.readQueue = [];
      }
      if (command.commandType === 'STOP_INVENTORY' && success) {
        await this.flushReads();
        window.setTimeout(() => {
          if (this.sessionId === command.businessId) {
            this.acceptingTags = false;
            this.sessionId = '';
          }
        }, 1500);
      }
      if (command.commandType === 'STOP_INVENTORY' && !success) discardConnection = true;
    } catch (error) {
      success = false;
      message = error instanceof Error ? error.message : '读写器命令失败';
      discardConnection = message.includes('应答超时') || command.commandType === 'STOP_INVENTORY';
      if (command.commandType === 'START_INVENTORY') {
        this.acceptingTags = false;
        this.sessionId = '';
        this.readQueue = [];
      }
    }
    try {
      await rfidApi.sikeComplete(this.device.deviceId, command.id, {
        connectionId: this.connectionId, success, resultCode: success ? 'OK' : 'SIKE_ERROR', message,
      });
    } finally {
      if (discardConnection) await this.disconnect();
    }
  }

  private async flushReads() {
    while (this.readQueue.length && this.connectionId && !this.stopped) {
      if (!this.pendingBatch) {
        const sessionId = this.readQueue[0].sessionId;
        this.pendingBatch = {
          sessionId,
          sequence: this.sequence,
          ids: this.readQueue.filter((item) => item.sessionId === sessionId).slice(0, 50).map((item) => item.sourceEventId),
        };
      }
      const pending = this.pendingBatch;
      const uploaded = new Set(pending.ids);
      const reads = this.readQueue.filter((item) => uploaded.has(item.sourceEventId));
      const payload = {
        connectionId: this.connectionId,
        streamId: this.streamId,
        sequence: pending.sequence,
        reads: reads.map(({ sessionId: _sessionId, sourceEventId: _sourceEventId, ...read }) => read),
      };
      try {
        await rfidApi.sikeReadBatch(this.device.deviceId, pending.sessionId, payload);
      } catch {
        // 上次请求可能已经入库而 HTTP 应答丢失；同一流和序号重传由服务端去重。
        await rfidApi.sikeReadBatch(this.device.deviceId, pending.sessionId, payload);
      }
      this.readQueue = this.readQueue.filter((item) => !uploaded.has(item.sourceEventId));
      this.pendingBatch = undefined;
      this.sequence += 1;
    }
  }

  private async disconnect() {
    const connectionId = this.connectionId;
    this.connectionId = '';
    this.verifiedReaderSerial = '';
    if (this.pending) {
      window.clearTimeout(this.pending.timer);
      this.pending.reject(new Error('本机服务连接已断开'));
      this.pending = undefined;
    }
    const socket = this.socket;
    this.socket = undefined;
    if (socket) {
      socket.onclose = null;
      // 页面隐藏、租约丢失或断网时尽力让本机读写器停止射频；最终状态仍以服务端确认结果为准。
      if (this.inventoryRunning && socket.readyState === WebSocket.OPEN) {
        try { socket.send(JSON.stringify({ wid: 4, baseOrApp: 0, msgId: 0xff, data: {} })); } catch { /* 连接可能已关闭 */ }
      }
      socket.close();
    }
    this.inventoryRunning = false;
    this.acceptingTags = false;
    this.sessionId = '';
    if (connectionId) {
      try { await rfidApi.sikeDisconnect(this.device.deviceId, connectionId); } catch { /* 租约超时自动清除在线状态 */ }
    }
    // 断线后服务端将未结束的本机会话标为失败，旧读数不能重传进新连接或新会话。
    this.readQueue = [];
    this.pendingBatch = undefined;
  }

  async stop() {
    this.stopped = true;
    if (this.timer) window.clearInterval(this.timer);
    await this.disconnect();
    this.readQueue = [];
    this.pendingBatch = undefined;
  }
}

export class SikeWorkstationRuntime {
  private devices: SikeDeviceRuntime[] = [];
  private disposed = false;

  constructor(private readonly onState: (deviceId: string, deviceName: string, state: string) => void) {}

  async start() {
    const response: any = await rfidApi.sikeDevices();
    if (this.disposed) return;
    this.devices = ((response?.data || []) as SikeLocalDevice[]).map((device) =>
      new SikeDeviceRuntime(device, (deviceId, state) => this.onState(deviceId, device.deviceName, state)));
    this.devices.forEach((device) => device.start());
  }

  setSession(session: RfidScanSession | null) { this.devices.forEach((device) => device.setSession(session)); }

  handles(deviceId: string) { return this.devices.some((device) => device.deviceId === deviceId); }

  async drainCommands() { await Promise.all(this.devices.map((device) => device.drainCommands())); }

  stopInventoryBestEffort() { this.devices.forEach((device) => device.stopInventoryBestEffort()); }

  async stop() { this.disposed = true; await Promise.all(this.devices.map((device) => device.stop())); this.devices = []; }
}
