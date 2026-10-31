// @vitest-environment happy-dom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Go2rtcPlayerCore, type Go2rtcPlayerCoreOptions } from '../go2rtcPlayerCore';

let sockets: FakeWebSocket[] = [];
let peerConnections: FakePeerConnection[] = [];
let cores: Go2rtcPlayerCore[] = [];

class FakeWebSocket extends EventTarget {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  readonly url: string;
  readyState = FakeWebSocket.CONNECTING;
  binaryType: BinaryType = 'blob';
  readonly sent: string[] = [];

  constructor(url: string) {
    super();
    this.url = url;
    sockets.push(this);
  }

  open(): void {
    this.readyState = FakeWebSocket.OPEN;
    this.dispatchEvent(new Event('open'));
  }

  message(value: string): void {
    this.dispatchEvent(new MessageEvent('message', { data: value }));
  }

  binary(value: ArrayBuffer): void {
    this.dispatchEvent(new MessageEvent('message', { data: value }));
  }

  send(value: string): void {
    this.sent.push(value);
  }

  close(): void {
    this.readyState = FakeWebSocket.CLOSED;
  }
}

class FakeMediaStream {
  constructor(private readonly tracks: MediaStreamTrack[] = []) {}

  getVideoTracks(): MediaStreamTrack[] {
    return this.tracks.filter((track) => track.kind === 'video');
  }

  getAudioTracks(): MediaStreamTrack[] {
    return this.tracks.filter((track) => track.kind === 'audio');
  }
}

class FakePeerConnection extends EventTarget {
  connectionState: RTCPeerConnectionState = 'new';
  localDescription: RTCSessionDescription | null = null;
  remoteDescription: RTCSessionDescription | null = null;
  private readonly transceivers: RTCRtpTransceiver[] = [];

  constructor() {
    super();
    peerConnections.push(this);
  }

  setConnectionState(state: RTCPeerConnectionState): void {
    this.connectionState = state;
    this.dispatchEvent(new Event('connectionstatechange'));
  }

  addTransceiver(kind: string): RTCRtpTransceiver {
    const track = { kind, stop: vi.fn() } as unknown as MediaStreamTrack;
    const transceiver = {
      currentDirection: 'recvonly',
      receiver: { track },
      sender: { track: null },
    } as unknown as RTCRtpTransceiver;
    this.transceivers.push(transceiver);
    return transceiver;
  }

  getTransceivers(): RTCRtpTransceiver[] {
    return this.transceivers;
  }

  getSenders(): RTCRtpSender[] {
    return [];
  }

  async createOffer(): Promise<RTCSessionDescriptionInit> {
    return { type: 'offer', sdp: 'v=0' };
  }

  async setLocalDescription(description: RTCSessionDescriptionInit): Promise<void> {
    this.localDescription = description as RTCSessionDescription;
  }

  async setRemoteDescription(description: RTCSessionDescriptionInit): Promise<void> {
    this.remoteDescription = description as RTCSessionDescription;
  }

  async addIceCandidate(): Promise<void> {}

  async getStats(): Promise<RTCStatsReport> {
    return new Map() as unknown as RTCStatsReport;
  }

  close(): void {
    this.connectionState = 'closed';
  }
}

function createCore(overrides: Partial<Go2rtcPlayerCoreOptions> = {}) {
  const video = document.createElement('video');
  Object.defineProperties(video, {
    readyState: { configurable: true, get: () => 2 },
    videoWidth: { configurable: true, get: () => 1280 },
    videoHeight: { configurable: true, get: () => 720 },
  });
  const onStage = vi.fn();
  const onFailure = vi.fn();
  const core = new Go2rtcPlayerCore(video, {
    modes: ['webrtc'],
    iceServers: [],
    reconnectTimeoutMs: 0,
    websocketOpenTimeoutMs: 1_000,
    peerDisconnectedGraceMs: 100,
    healthCheckIntervalMs: 100,
    healthFailureThreshold: 3,
    onStage,
    onFailure,
    ...overrides,
  });
  cores.push(core);
  return { core, video, onStage, onFailure };
}

describe('Go2rtcPlayerCore bounded transport recovery', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    sockets = [];
    peerConnections = [];
    cores = [];
    vi.stubGlobal('WebSocket', FakeWebSocket);
    vi.stubGlobal('RTCPeerConnection', FakePeerConnection);
    vi.stubGlobal('MediaStream', FakeMediaStream);
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue();
    vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => undefined);
    vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => undefined);
  });

  afterEach(() => {
    cores.forEach((core) => core.destroy());
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    document.body.innerHTML = '';
  });

  it('receives audio by default and omits audio from both negotiations when disabled', () => {
    const { core: defaultCore } = createCore();
    (defaultCore as any).stopped = false;
    (defaultCore as any).setupWebrtc();
    expect(peerConnections[0].getTransceivers().map((item) => item.receiver.track.kind)).toEqual(['video', 'audio']);

    const { core: videoOnlyCore } = createCore({ receiveAudio: false });
    (videoOnlyCore as any).stopped = false;
    (videoOnlyCore as any).setupWebrtc();
    expect(peerConnections[1].getTransceivers().map((item) => item.receiver.track.kind)).toEqual(['video']);

    class AllCodecsMediaSource extends EventTarget {
      static isTypeSupported(): boolean {
        return true;
      }
    }
    expect((defaultCore as any).supportedMseCodecs(AllCodecsMediaSource)).toContain('mp4a.40.2');
    const videoOnlyCodecs = (videoOnlyCore as any).supportedMseCodecs(AllCodecsMediaSource) as string;
    expect(videoOnlyCodecs).toContain('avc1.640029');
    expect(videoOnlyCodecs).toContain('hvc1.1.6.L153.B0');
    expect(videoOnlyCodecs).not.toMatch(/mp4a|flac|opus/i);
  });

  it('uses MSE trim hysteresis and clears trim/rate state during cleanup', async () => {
    const { core, video } = createCore({ modes: ['mse'] });
    video.currentTime = 7.7;
    const remove = vi.fn();
    const sourceBuffer = {
      updating: false,
      buffered: {
        length: 1,
        start: () => 0,
        end: () => 8,
      },
      remove,
    } as unknown as SourceBuffer;
    const mediaSource = { setLiveSeekableRange: vi.fn() } as unknown as MediaSource;

    (core as any).updateMseBufferedRange(sourceBuffer, mediaSource);
    expect(remove).toHaveBeenCalledWith(0, 3);
    expect(video.playbackRate).toBe(1.05);

    (core as any).updateMseBufferedRange(sourceBuffer, mediaSource);
    expect(remove).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(2_000);
    (core as any).updateMseBufferedRange(sourceBuffer, mediaSource);
    expect(remove).toHaveBeenCalledTimes(2);

    (core as any).cleanupMseForWebrtc();
    expect((core as any).lastMseTrimAt).toBeNull();
    expect(video.playbackRate).toBe(1);
  });

  it('reports video playback quality without requiring browser support', () => {
    const { core, video } = createCore();
    video.playbackRate = 1.05;
    Object.defineProperty(video, 'getVideoPlaybackQuality', {
      configurable: true,
      value: () => ({ totalVideoFrames: 1_000, droppedVideoFrames: 7, corruptedVideoFrames: 2 }),
    });

    expect(core.getDiagnosticsSnapshot().video).toMatchObject({
      playbackRate: 1.05,
      playbackQuality: { totalVideoFrames: 1_000, droppedVideoFrames: 7, corruptedVideoFrames: 2 },
    });

    Object.defineProperty(video, 'getVideoPlaybackQuality', { configurable: true, value: undefined });
    expect(core.getDiagnosticsSnapshot().video.playbackQuality).toBeNull();
  });

  it('records current-attempt socket/MSE timing and clears it before a reconnect attempt', async () => {
    const { core } = createCore({ modes: ['webrtc'] });
    core.connect('ws://example.test/stream?src=camera-original');
    const firstAttemptId = (core as any).attemptTiming.attemptId as number;

    await vi.advanceTimersByTimeAsync(40);
    sockets[0].open();
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());
    await vi.advanceTimersByTimeAsync(10);
    sockets[0].binary(new Uint8Array([1]).buffer);
    await vi.advanceTimersByTimeAsync(10);
    (core as any).recordAttemptTiming(firstAttemptId, 'firstWebrtcMediaMs');

    expect(core.getDiagnosticsSnapshot().attemptTiming).toMatchObject({
      attemptId: firstAttemptId,
      socketOpenMs: 40,
      firstMseByteMs: 50,
      firstWebrtcMediaMs: 60,
      firstMediaMs: 50,
    });
    (core as any).recordAttemptTiming(firstAttemptId + 1, 'playingMs');
    expect(core.getDiagnosticsSnapshot().attemptTiming).not.toHaveProperty('playingMs');

    (core as any).disconnectTransport(false);
    expect(core.getDiagnosticsSnapshot().attemptTiming).toBeNull();
    (core as any).stopped = false;
    (core as any).openSocket();
    const secondAttemptTiming = core.getDiagnosticsSnapshot().attemptTiming;
    expect(secondAttemptTiming?.attemptId).not.toBe(firstAttemptId);
    expect(secondAttemptTiming).toEqual({ attemptId: secondAttemptTiming?.attemptId });
  });

  it('accepts hidden decoded dimensions without preselection stats and samples real codec at commit', async () => {
    const onPlaying = vi.fn();
    const { core, video } = createCore({ modes: ['webrtc'], onPlaying });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    Object.defineProperties(hiddenVideo, {
      videoWidth: { configurable: true, get: () => 1280 },
      videoHeight: { configurable: true, get: () => 720 },
    });
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const frameCallbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        frameCallbacks.push(callback);
        return frameCallbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    const sampleWebrtcRuntime = vi.spyOn(core as any, 'sampleWebrtcRuntime').mockImplementation(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });
    (core as any).stopped = false;
    (core as any).activeAttemptId = 71;
    (core as any).attemptTiming = { attemptId: 71, startedAt: Date.now() };
    (core as any).activeMode = 'webrtc';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    ((core as any).messageHandlers as Map<string, unknown>).set('webrtc', vi.fn());

    await vi.advanceTimersByTimeAsync(75);
    await expect((core as any).waitForWebrtcVideoEvidence(hiddenVideo, pc)).resolves.toBe(true);
    expect(sampleWebrtcRuntime).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(25);
    const playing = (core as any).reportPlaying('webrtc');
    expect(frameCallbacks).toHaveLength(1);
    frameCallbacks[0](performance.now());
    await playing;

    expect(sampleWebrtcRuntime).toHaveBeenCalledOnce();
    expect(onPlaying).toHaveBeenCalledWith(expect.objectContaining({ mode: 'webrtc', codec: 'H.264' }));
    expect(core.getDiagnosticsSnapshot().attemptTiming).toMatchObject({
      firstWebrtcMediaMs: 75,
      firstMediaMs: 75,
      primaryPresentedMs: 100,
      playingMs: 100,
    });
  });

  it('bounds a WebSocket that never opens and retries the same URL without codec failure', async () => {
    const { core, onStage, onFailure } = createCore({
      websocketOpenTimeoutMs: 100,
      reconnectTimeoutMs: 300,
    });
    core.connect('ws://example.test/stream?src=camera-original');

    expect(sockets.map((socket) => socket.url)).toEqual(['ws://example.test/stream?src=camera-original']);
    await vi.advanceTimersByTimeAsync(299);
    expect(sockets).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);

    expect(sockets.map((socket) => socket.url)).toEqual([
      'ws://example.test/stream?src=camera-original',
      'ws://example.test/stream?src=camera-original',
    ]);
    expect(onStage).toHaveBeenCalledWith('reconnecting', '流媒体信令连接超时，正在重连');

    await vi.advanceTimersByTimeAsync(100);
    expect(sockets).toHaveLength(2);
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        report: expect.objectContaining({ type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR' }),
      }),
    );
    expect(onFailure.mock.calls.some(([event]) => event.kind === 'h265-decode')).toBe(false);

    core.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('cancels a transient disconnected state, then recovers an active WebRTC winner after grace', async () => {
    const { core, onFailure } = createCore({ reconnectTimeoutMs: 30_000 });
    core.connect('ws://example.test/stream?src=camera-original');
    sockets[0].open();
    await Promise.resolve();
    const pc = peerConnections[0];
    expect(pc).toBeDefined();

    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';
    (core as any).hiddenVideo = document.createElement('video');
    pc.setConnectionState('disconnected');
    await vi.advanceTimersByTimeAsync(50);
    pc.setConnectionState('connected');
    await vi.advanceTimersByTimeAsync(100);
    expect(sockets).toHaveLength(1);

    pc.setConnectionState('disconnected');
    await vi.advanceTimersByTimeAsync(100);
    await vi.advanceTimersByTimeAsync(1);

    expect(sockets).toHaveLength(2);
    expect(sockets[1].url).toBe('ws://example.test/stream?src=camera-original');
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('recovers an active failed peer immediately without waiting for disconnected grace', async () => {
    const { core, onFailure } = createCore({
      peerDisconnectedGraceMs: 5_000,
      reconnectTimeoutMs: 30_000,
    });
    core.connect('ws://example.test/stream?src=camera-original');
    sockets[0].open();
    await Promise.resolve();
    const pc = peerConnections[0];
    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';

    pc.setConnectionState('failed');
    await vi.advanceTimersByTimeAsync(1);

    expect(sockets).toHaveLength(2);
    expect(sockets[1].url).toBe('ws://example.test/stream?src=camera-original');
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('reports codec incompatibility per terminal mode while preserving the competing mode', () => {
    const { core, onFailure } = createCore();
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';
    (core as any).activeAttemptId = 7;
    (core as any).attemptedModes = ['mse', 'webrtc'];
    handlers.set('mse', vi.fn());
    handlers.set('webrtc', vi.fn());

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'mse: codecs not matched' }));
    expect(handlers.has('mse')).toBe(false);
    expect(handlers.has('webrtc')).toBe(true);
    expect((core as any).externallyRecovering).toBe(false);
    expect(onFailure).toHaveBeenLastCalledWith(
      expect.objectContaining({
        kind: 'codec-unsupported',
        mode: 'mse',
        attemptId: 7,
        attemptedModes: ['mse', 'webrtc'],
        report: expect.objectContaining({ code: 'CODEC_NOT_MATCHED', mode: 'mse' }),
      }),
    );

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'webrtc: unsupported codec' }));
    expect(handlers.has('webrtc')).toBe(false);
    expect(onFailure).toHaveBeenLastCalledWith(
      expect.objectContaining({
        kind: 'codec-unsupported',
        mode: 'webrtc',
        report: expect.objectContaining({ code: 'UNSUPPORTED_CODEC', mode: 'webrtc' }),
      }),
    );
    expect((core as any).externallyRecovering).toBe(true);
  });

  it('keeps transport failures out of codec/H265 failure reporting', () => {
    const { core, onFailure } = createCore();
    (core as any).stopped = false;
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'mse: i/o timeout' }));

    expect(onFailure).toHaveBeenCalledTimes(1);
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        report: expect.objectContaining({ type: 'SOURCE_CONNECTION', code: 'CONNECTION_TIMEOUT' }),
      }),
    );
  });

  it('reacquires the same profile when the last mode is codec-terminal but another mode ended without codec proof', () => {
    const { core, onFailure } = createCore();
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    (core as any).stopped = false;
    (core as any).activeAttemptId = 8;
    (core as any).attemptedModes = ['webrtc', 'mse'];
    // WebRTC 已因信令错误退出，只剩 MSE 给出 codec 终止证据。
    handlers.set('mse', vi.fn());

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'mse: codecs not matched' }));

    expect(onFailure.mock.calls.map(([event]) => event.kind)).toEqual(['codec-unsupported', 'source']);
    expect(onFailure.mock.calls[1][0]).toEqual(
      expect.objectContaining({
        kind: 'source',
        report: expect.objectContaining({ type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR' }),
      }),
    );
    expect(onFailure.mock.calls.some(([event]) => event.kind === 'h265-decode')).toBe(false);
  });

  it('reconnects a pure WebRTC player when go2rtc rejects the offer', async () => {
    const { core, onFailure } = createCore();
    core.connect('ws://example.test/stream?src=camera-original');
    sockets[0].open();
    await Promise.resolve();

    sockets[0].message(JSON.stringify({ type: 'error', value: 'webrtc/offer: signaling failed' }));
    await vi.advanceTimersByTimeAsync(0);

    expect(sockets).toHaveLength(2);
    expect(sockets[1].url).toBe('ws://example.test/stream?src=camera-original');
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('drops only a pre-frame MSE pipeline while WebRTC is still competing', () => {
    const { core, video, onFailure } = createCore();
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';
    (core as any).playingReported = false;
    handlers.set('mse', vi.fn());
    handlers.set('webrtc', vi.fn());

    (core as any).handleMsePipelineFailure('MSE 缓冲异常，正在重连');

    expect(handlers.has('mse')).toBe(false);
    expect(handlers.has('webrtc')).toBe(true);
    expect((core as any).activeMode).toBeNull();
    expect(video.getAttribute('src')).toBeNull();
    expect(onFailure).not.toHaveBeenCalled();
    expect(sockets).toHaveLength(0);
  });

  it('keeps an attempt-scoped H265 MSE terminal while WebRTC is still competing', () => {
    const { core, video, onFailure } = createCore({ modes: ['webrtc', 'mse'] });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    Object.defineProperty(video, 'error', { configurable: true, get: () => ({ code: 3 }) });
    (core as any).stopped = false;
    (core as any).activeAttemptId = 73;
    (core as any).attemptedModes = ['mse', 'webrtc'];
    (core as any).activeMode = 'mse';
    (core as any).playingReported = false;
    (core as any).mseCodecs = 'video/mp4; codecs="hvc1.1.6.L153.B0"';
    handlers.set('mse', vi.fn());
    handlers.set('webrtc', vi.fn());

    (core as any).onPrimaryVideoError();

    expect(handlers.has('mse')).toBe(false);
    expect(handlers.has('webrtc')).toBe(true);
    expect((core as any).activeMode).toBeNull();
    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'codec-unsupported',
        mode: 'mse',
        attemptId: 73,
        attemptedModes: ['mse', 'webrtc'],
      }),
    );
    expect((core as any).externallyRecovering).toBe(false);

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'webrtc: codecs not matched' }));

    expect(onFailure).toHaveBeenCalledTimes(2);
    expect(onFailure.mock.calls[1][0]).toEqual(
      expect.objectContaining({
        kind: 'codec-unsupported',
        mode: 'webrtc',
        attemptId: 73,
        attemptedModes: ['mse', 'webrtc'],
      }),
    );
    expect(onFailure.mock.calls.some(([event]) => event.kind === 'source' || event.kind === 'h265-decode')).toBe(false);

    (core as any).resetAttemptState();
    expect((core as any).codecUnsupportedModes.size).toBe(0);
    expect((core as any).automaticWinner).toBeNull();
  });

  it('restores the one-shot internal retry budget only after a confirmed first frame', async () => {
    const { core } = createCore({ modes: ['mse'] });
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());
    (core as any).internalReconnectCount = 1;
    (core as any).automaticAlternatePending = true;
    expect((core as any).internalReconnectCount).toBe(1);

    await (core as any).reportPlaying('mse');

    expect((core as any).internalReconnectCount).toBe(0);
    expect((core as any).automaticAlternatePending).toBe(false);
    expect((core as any).hasProducedFirstFrame).toBe(true);
  });

  it('rejects a delayed playing callback after its mode has been removed', async () => {
    const onPlaying = vi.fn();
    const { core } = createCore({ modes: ['mse'], onPlaying });
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('mse', vi.fn());

    let releaseSample!: () => void;
    (core as any).sampleWebrtcRuntime = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          releaseSample = resolve;
        }),
    );
    const pendingPlaying = (core as any).reportPlaying('mse');
    await Promise.resolve();
    handlers.delete('mse');
    (core as any).activeMode = null;
    (core as any).mediaSource = null;
    releaseSample();
    await pendingPlaying;

    expect(onPlaying).not.toHaveBeenCalled();
    expect((core as any).playingReported).toBe(false);
  });

  it('rejects a delayed playing callback after a new attempt starts in the same mode', async () => {
    const onPlaying = vi.fn();
    const { core } = createCore({ modes: ['mse'], onPlaying });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    const firstMediaSource = {};
    (core as any).stopped = false;
    (core as any).activeAttemptId = 1;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = firstMediaSource;
    handlers.set('mse', vi.fn());

    let releaseSample!: () => void;
    (core as any).sampleWebrtcRuntime = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          releaseSample = resolve;
        }),
    );
    const pendingPlaying = (core as any).reportPlaying('mse');
    await Promise.resolve();
    (core as any).activeAttemptId = 2;
    (core as any).mediaSource = {};
    releaseSample();
    await pendingPlaying;

    expect(onPlaying).not.toHaveBeenCalled();
    expect((core as any).playingReported).toBe(false);
  });

  it('does not recreate a health timer when onPlaying synchronously destroys the Core', async () => {
    let playerCore!: Go2rtcPlayerCore;
    const onPlaying = vi.fn(() => playerCore.destroy());
    const { core } = createCore({ modes: ['mse'], onPlaying, healthCheckIntervalMs: 100 });
    playerCore = core;
    (core as any).stopped = false;
    (core as any).activeAttemptId = 3;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    const committed = await (core as any).reportPlaying('mse');

    expect(committed).toBe(false);
    expect(onPlaying).toHaveBeenCalledOnce();
    expect((core as any).healthTimer).toBeNull();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('rejects a delayed first-frame timeout after that attempt has already played', async () => {
    const { core, onFailure } = createCore({ modes: ['webrtc'] });
    const initialMse = { bytesReceived: 0, bytesAppended: 0, bufferedEnd: 0, successfulAppends: 0 };
    (core as any).stopped = false;
    (core as any).activeAttemptId = 11;
    (core as any).connectStartedAt = 123;

    let releaseSample!: () => void;
    (core as any).sampleWebrtcRuntime = vi.fn(
      () =>
        new Promise<void>((resolve) => {
          releaseSample = resolve;
        }),
    );
    const pendingTimeout = (core as any).handleFirstFrameTimeout(initialMse, 123, 11);
    (core as any).playingReported = true;
    releaseSample();
    await pendingTimeout;

    expect(onFailure).not.toHaveBeenCalled();
    expect(sockets).toHaveLength(0);
  });

  it('keeps stale WebRTC signaling from reaching or closing a newer attempt', async () => {
    let rejectOldLocalDescription!: (reason?: unknown) => void;
    vi.spyOn(FakePeerConnection.prototype, 'setLocalDescription').mockImplementationOnce(
      () =>
        new Promise<void>((_resolve, reject) => {
          rejectOldLocalDescription = reject;
        }),
    );
    const { core, onFailure } = createCore({ modes: ['webrtc'] });
    core.connect('ws://example.test/stream?src=camera-original');
    sockets[0].open();
    await Promise.resolve();
    await Promise.resolve();

    const oldPc = peerConnections[0];
    core.reconnectNow();
    sockets[1].open();
    await Promise.resolve();
    await Promise.resolve();
    const currentPc = peerConnections[1];
    const sentBeforeStaleEvents = sockets[1].sent.length;

    oldPc.dispatchEvent(new Event('icecandidate'));
    rejectOldLocalDescription(new Error('stale local description'));
    await Promise.resolve();
    await Promise.resolve();

    expect((core as any).pc).toBe(currentPc);
    expect(sockets).toHaveLength(2);
    expect(sockets[1].sent).toHaveLength(sentBeforeStaleEvents);
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('requires consecutive post-frame stalls and only performs same-URL recovery', async () => {
    const { core, video, onFailure } = createCore({ modes: ['mse'] });
    let currentTime = 0;
    Object.defineProperty(video, 'paused', { configurable: true, get: () => false });
    Object.defineProperty(video, 'currentTime', {
      configurable: true,
      get: () => currentTime,
      set: (value: number) => {
        currentTime = value;
      },
    });
    (core as any).wsUrl = 'ws://example.test/stream?src=camera-original';
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).activeMode = 'mse';
    (core as any).connectStartedAt = Date.now();
    (core as any).startHealthMonitor('mse');

    await vi.advanceTimersByTimeAsync(200);
    expect(sockets).toHaveLength(0);
    currentTime = 1;
    await vi.advanceTimersByTimeAsync(100);
    expect(sockets).toHaveLength(0);

    await vi.advanceTimersByTimeAsync(300);
    await vi.runOnlyPendingTimersAsync();
    expect(sockets).toHaveLength(1);
    expect(sockets[0].url).toBe('ws://example.test/stream?src=camera-original');
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('uses fresh primary rVFC evidence without polling WebRTC stats', async () => {
    const { core, video } = createCore({ modes: ['webrtc'] });
    Object.defineProperty(video, 'paused', { configurable: true, get: () => false });
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn(() => 1),
      cancelVideoFrameCallback: vi.fn(),
    });
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';
    (core as any).healthProbeGeneration = 7;
    (core as any).healthBaseline = { currentTime: 1, framesDecoded: 10, framesRendered: 10 };
    (core as any).latestRuntime = { inboundVideo: { framesDecoded: 10, framesRendered: 10 } };
    (core as any).lastPresentedFrameAt = Date.now();
    const sampleWebrtcRuntime = vi.spyOn(core as any, 'sampleWebrtcRuntime');

    await (core as any).checkPlaybackHealth('webrtc', 7);

    expect(sampleWebrtcRuntime).not.toHaveBeenCalled();
    expect((core as any).consecutiveHealthFailures).toBe(0);
  });

  it('keeps polling WebRTC stats when primary rVFC is unavailable', async () => {
    const { core, video } = createCore({ modes: ['webrtc'] });
    Object.defineProperty(video, 'paused', { configurable: true, get: () => false });
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';
    (core as any).healthProbeGeneration = 9;
    (core as any).healthBaseline = { currentTime: 1, framesDecoded: 10, framesRendered: 10 };
    (core as any).latestRuntime = { inboundVideo: { framesDecoded: 10, framesRendered: 10 } };
    const sampleWebrtcRuntime = vi.spyOn(core as any, 'sampleWebrtcRuntime').mockImplementation(async () => {
      (core as any).latestRuntime = { inboundVideo: { framesDecoded: 11, framesRendered: 11 } };
    });

    await (core as any).checkPlaybackHealth('webrtc', 9);

    expect(sampleWebrtcRuntime).toHaveBeenCalledOnce();
    expect((core as any).consecutiveHealthFailures).toBe(0);
  });

  it('does not report playing until the primary video actually presents a frame', async () => {
    const onPlaying = vi.fn();
    const { core, video } = createCore({ modes: ['mse'], onPlaying, primaryFrameTimeoutMs: 500 });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    (core as any).stopped = false;
    (core as any).activeAttemptId = 31;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    const pending = (core as any).reportPlaying('mse');
    expect(onPlaying).not.toHaveBeenCalled();
    expect(callbacks).toHaveLength(1);

    callbacks[0](performance.now());
    await pending;

    expect(onPlaying).toHaveBeenCalledOnce();
    expect((core as any).playingReported).toBe(true);
    expect((await core.getDiagnostics()).presentation.lastPresentedFrameAt).not.toBeNull();
  });

  it('waits for loadeddata on browsers without video frame callbacks', async () => {
    const onPlaying = vi.fn();
    const { core, video } = createCore({ modes: ['mse'], onPlaying, primaryFrameTimeoutMs: 500 });
    let readyState = 0;
    let videoWidth = 0;
    let videoHeight = 0;
    Object.defineProperties(video, {
      readyState: { configurable: true, get: () => readyState },
      videoWidth: { configurable: true, get: () => videoWidth },
      videoHeight: { configurable: true, get: () => videoHeight },
    });
    (core as any).stopped = false;
    (core as any).activeAttemptId = 33;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    const pending = (core as any).reportPlaying('mse');
    expect(onPlaying).not.toHaveBeenCalled();
    readyState = 2;
    videoWidth = 1280;
    videoHeight = 720;
    video.dispatchEvent(new Event('loadeddata'));
    await pending;

    expect(onPlaying).toHaveBeenCalledOnce();
  });

  it('pauses primary-frame confirmation while an ancestor is hidden and resumes when visible', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({
      modes: ['mse'],
      onPlaying,
      firstFrameTimeoutMs: 100,
      primaryFrameTimeoutMs: 100,
    });
    const wrapper = document.createElement('div');
    wrapper.style.visibility = 'hidden';
    wrapper.append(video);
    document.body.append(wrapper);
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    (core as any).stopped = false;
    (core as any).connectStartedAt = 345;
    (core as any).activeAttemptId = 34;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).startFirstFrameProbe();
    const pending = (core as any).reportPlaying('mse');
    await vi.advanceTimersByTimeAsync(500);

    expect(onPlaying).not.toHaveBeenCalled();
    expect(onFailure).not.toHaveBeenCalled();
    expect(callbacks).toHaveLength(0);
    expect((await core.getDiagnostics()).presentation.waitingForPrimaryFrame).toBe(true);

    wrapper.style.visibility = 'visible';
    await Promise.resolve();
    await Promise.resolve();
    expect(callbacks).toHaveLength(1);
    callbacks[0](performance.now());
    await pending;

    expect(onPlaying).toHaveBeenCalledOnce();
    expect((core as any).playingReported).toBe(true);
  });

  it('locks a painted MSE winner and only releases the late WebRTC loser', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({ modes: ['webrtc', 'mse'], onPlaying });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    pc.addTransceiver('audio');
    const hiddenVideo = document.createElement('video');
    Object.defineProperties(hiddenVideo, {
      videoWidth: { configurable: true, get: () => 1280 },
      videoHeight: { configurable: true, get: () => 720 },
    });
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    handlers.set('mse', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 31;
    (core as any).attemptedModes = ['mse', 'webrtc'];
    (core as any).activeMode = 'mse';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).mediaSource = {};
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';

    const lateWebrtcSelection = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    const msePlaying = (core as any).reportPlaying('mse');
    expect(callbacks).toHaveLength(1);
    callbacks[0](performance.now());
    await Promise.all([lateWebrtcSelection, msePlaying]);

    expect((core as any).automaticWinner).toBe('mse');
    expect((core as any).activeMode).toBe('mse');
    expect((core as any).mediaSource).not.toBeNull();
    expect((core as any).pc).toBeNull();
    expect(pc.connectionState).toBe('closed');
    expect(handlers.has('mse')).toBe(true);
    expect(handlers.has('webrtc')).toBe(false);
    expect((core as any).failedModes.size).toBe(0);
    expect((core as any).codecUnsupportedModes.size).toBe(0);
    expect(onPlaying).toHaveBeenCalledWith(expect.objectContaining({ mode: 'mse', codec: 'H.264' }));
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('does not let a stale presentation callback lock or close a newer attempt', async () => {
    const onPlaying = vi.fn();
    const { core, video } = createCore({ modes: ['webrtc', 'mse'], onPlaying });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    const oldMediaSource = {};
    const newMediaSource = {};
    const newPc = new FakePeerConnection();
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('mse', vi.fn());
    handlers.set('webrtc', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 80;
    (core as any).activeMode = 'mse';
    (core as any).mediaSource = oldMediaSource;
    (core as any).pc = newPc;

    const stalePlaying = (core as any).reportPlaying('mse');
    expect(callbacks).toHaveLength(1);
    (core as any).activeAttemptId = 81;
    (core as any).mediaSource = newMediaSource;
    callbacks[0](performance.now());
    await stalePlaying;

    expect((core as any).automaticWinner).toBeNull();
    expect((core as any).pc).toBe(newPc);
    expect(newPc.connectionState).toBe('new');
    expect(onPlaying).not.toHaveBeenCalled();
  });

  it('keeps MSE alive until the selected WebRTC stream is painted by the primary video', async () => {
    const onPlaying = vi.fn();
    const { core, video } = createCore({ modes: ['webrtc', 'mse'], onPlaying });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    let primaryStream: unknown = null;
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      get: () => primaryStream,
      set: (value) => {
        primaryStream = value;
      },
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    handlers.set('mse', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 32;
    (core as any).activeMode = 'mse';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).mediaSource = {};
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });

    const pending = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    for (let attempt = 0; attempt < 10 && callbacks.length === 0; attempt += 1) await Promise.resolve();

    expect((core as any).mediaSource).not.toBeNull();
    expect(handlers.has('mse')).toBe(true);
    expect(onPlaying).not.toHaveBeenCalled();
    expect(callbacks).toHaveLength(1);
    callbacks[0](performance.now());
    await pending;

    expect((core as any).mediaSource).toBeNull();
    expect(handlers.has('mse')).toBe(false);
    expect((core as any).activeMode).toBe('webrtc');
    expect(onPlaying).toHaveBeenCalledWith(expect.objectContaining({ mode: 'webrtc', codec: 'H.264' }));
  });

  it('does not let a cancelled late MSE frame callback replace a painted WebRTC winner', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({ modes: ['webrtc', 'mse'], onPlaying });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    let primaryStream: unknown = null;
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      get: () => primaryStream,
      set: (value) => {
        primaryStream = value;
      },
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    handlers.set('mse', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 33;
    (core as any).attemptedModes = ['mse', 'webrtc'];
    (core as any).activeMode = 'mse';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).mediaSource = {};
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });

    const msePlaying = (core as any).reportPlaying('mse');
    expect(callbacks).toHaveLength(1);
    const lateMseFrame = callbacks[0];
    const webRtcSelection = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    for (let attempt = 0; attempt < 20 && callbacks.length < 2; attempt += 1) await Promise.resolve();

    expect(callbacks).toHaveLength(2);
    callbacks[1](performance.now());
    await webRtcSelection;
    lateMseFrame(performance.now());
    await msePlaying;

    expect((core as any).automaticWinner).toBe('webrtc');
    expect((core as any).activeMode).toBe('webrtc');
    expect((core as any).pc).toBe(pc);
    expect(pc.connectionState).not.toBe('closed');
    expect((core as any).mediaSource).toBeNull();
    expect(handlers.has('webrtc')).toBe(true);
    expect(handlers.has('mse')).toBe(false);
    expect(onPlaying).toHaveBeenCalledOnce();
    expect(onPlaying).toHaveBeenCalledWith(expect.objectContaining({ mode: 'webrtc', codec: 'H.264' }));
    expect(onFailure).not.toHaveBeenCalled();
  });

  it('does not promote a WebRTC candidate that has tracks but no decoded video evidence', async () => {
    const { core, onFailure } = createCore({
      modes: ['webrtc', 'mse'],
      webrtcEvidenceTimeoutMs: 100,
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    pc.addTransceiver('audio');
    const hiddenVideo = document.createElement('video');
    Object.defineProperties(hiddenVideo, {
      videoWidth: { configurable: true, get: () => 0 },
      videoHeight: { configurable: true, get: () => 0 },
    });
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    handlers.set('mse', vi.fn());
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).mediaSource = {};
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = { inboundVideo: null, selectedCandidatePair: null };
    });

    const pending = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    await vi.advanceTimersByTimeAsync(99);
    expect((core as any).pc).toBe(pc);
    await vi.advanceTimersByTimeAsync(1);
    await pending;

    expect((core as any).pc).toBeNull();
    expect((core as any).activeMode).toBe('mse');
    expect(handlers.has('mse')).toBe(true);
    expect(onFailure).not.toHaveBeenCalled();
    expect((await core.getDiagnostics()).failureContext.lastWebrtcSelection).toEqual(
      expect.objectContaining({ confirmed: false, hiddenVideoWidth: 0, hiddenVideoHeight: 0 }),
    );
  });

  it('resets the primary video once when WebRTC paints only after a clean MediaStream handoff', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({
      modes: ['webrtc'],
      onPlaying,
      primaryFrameTimeoutMs: 50,
    });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    let primaryStream: unknown = null;
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      get: () => primaryStream,
      set: (value) => {
        primaryStream = value;
      },
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 61;
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });

    const pending = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    for (let attempt = 0; attempt < 10 && callbacks.length === 0; attempt += 1) await Promise.resolve();
    expect(callbacks).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(50);
    for (let attempt = 0; attempt < 10 && callbacks.length < 2; attempt += 1) await Promise.resolve();
    expect(callbacks).toHaveLength(2);
    callbacks[1](performance.now());
    await pending;

    expect(HTMLMediaElement.prototype.load).toHaveBeenCalledOnce();
    expect(onPlaying).toHaveBeenCalledWith(expect.objectContaining({ mode: 'webrtc', codec: 'H.264' }));
    expect(onFailure).not.toHaveBeenCalled();
    expect((await core.getDiagnostics()).failureContext.lastPrimaryCommitFailure).toEqual(
      expect.objectContaining({ phase: 'initial', mseRestorable: false }),
    );
  });

  it('does not retry the same WebRTC media handoff inside an active-alternate Core', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({
      modes: ['webrtc'],
      attemptKind: 'active-alternate',
      onPlaying,
      primaryFrameTimeoutMs: 50,
    });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    let primaryStream: unknown = null;
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      get: () => primaryStream,
      set: (value) => {
        primaryStream = value;
      },
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 62;
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });

    const pending = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    for (let attempt = 0; attempt < 10 && callbacks.length === 0; attempt += 1) await Promise.resolve();
    await vi.advanceTimersByTimeAsync(50);
    await pending;

    expect(callbacks).toHaveLength(1);
    expect(HTMLMediaElement.prototype.load).not.toHaveBeenCalled();
    expect(onPlaying).not.toHaveBeenCalled();
    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(expect.objectContaining({ kind: 'source', mode: 'webrtc' }));
    expect((core as any).pc).toBeNull();
    expect((core as any).internalReconnectCount).toBe(0);
  });

  it('rebuilds MSE once when WebRTC has frames but the primary video does not present them', async () => {
    class AvailableMediaSource extends EventTarget {
      static isTypeSupported(): boolean {
        return true;
      }
    }
    vi.stubGlobal('MediaSource', AvailableMediaSource);
    const onPlaying = vi.fn();
    const { core, video, onStage } = createCore({
      modes: ['webrtc', 'mse'],
      onPlaying,
      primaryFrameTimeoutMs: 50,
    });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    let primaryStream: unknown = null;
    Object.defineProperty(video, 'srcObject', {
      configurable: true,
      get: () => primaryStream,
      set: (value) => {
        primaryStream = value;
      },
    });
    const pc = new FakePeerConnection();
    pc.addTransceiver('video');
    const hiddenVideo = document.createElement('video');
    let hiddenStream: unknown = new FakeMediaStream(pc.getTransceivers().map((item) => item.receiver.track));
    Object.defineProperty(hiddenVideo, 'srcObject', {
      configurable: true,
      get: () => hiddenStream,
      set: (value) => {
        hiddenStream = value;
      },
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    handlers.set('webrtc', vi.fn());
    handlers.set('mse', vi.fn());
    (core as any).stopped = false;
    (core as any).activeAttemptId = 62;
    (core as any).activeMode = 'mse';
    (core as any).pc = pc;
    (core as any).hiddenVideo = hiddenVideo;
    (core as any).mediaSource = { readyState: 'open' };
    (core as any).objectUrl = 'blob:mse-source';
    (core as any).mseCodecs = 'video/mp4; codecs="avc1.640029"';
    const openSocket = vi.fn();
    (core as any).openSocket = openSocket;
    (core as any).sampleWebrtcRuntime = vi.fn(async () => {
      (core as any).latestRuntime = {
        inboundVideo: {
          codecMimeType: 'video/H264',
          bytesReceived: 1_000,
          packetsReceived: 10,
          framesReceived: 1,
          framesDecoded: 1,
          framesRendered: 1,
        },
      };
    });

    const pending = (core as any).selectWebrtcIfPreferred(hiddenVideo, pc);
    for (let attempt = 0; attempt < 10 && callbacks.length === 0; attempt += 1) await Promise.resolve();
    await vi.advanceTimersByTimeAsync(50);
    await pending;

    expect(callbacks).toHaveLength(1);
    expect(HTMLMediaElement.prototype.load).not.toHaveBeenCalled();
    expect((core as any).modeOverride).toBe('mse');
    expect((core as any).modeSwitchCount).toBe(1);
    expect((core as any).primarySourceTransition).toBe('to-mse');
    expect(onPlaying).not.toHaveBeenCalled();
    expect(onStage).toHaveBeenCalledWith('reconnecting', 'WebRTC 主画面未能呈现，正在回退 MSE');
    expect((await core.getDiagnostics()).failureContext.lastPrimaryCommitFailure).toEqual(
      expect.objectContaining({ phase: 'initial', mseRestorable: true }),
    );

    await vi.advanceTimersByTimeAsync(1);
    expect(openSocket).toHaveBeenCalledOnce();
  });

  it('switches a failed active WebRTC session to MSE once without changing the source profile', () => {
    class AvailableMediaSource extends EventTarget {
      static isTypeSupported(): boolean {
        return true;
      }
    }
    vi.stubGlobal('MediaSource', AvailableMediaSource);
    const { core, onStage } = createCore({
      modes: ['webrtc', 'mse'],
      maxModeSwitches: 1,
    });
    (core as any).wsUrl = 'ws://example.test/stream?src=camera-original';
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';

    expect((core as any).trySwitchToAlternateMode('播放画面停滞')).toBe(true);
    expect((core as any).modeOverride).toBe('mse');
    expect((core as any).modeSwitchCount).toBe(1);
    expect((core as any).automaticAlternatePending).toBe(true);
    expect((core as any).failedModes.has('webrtc')).toBe(true);
    expect((core as any).trySwitchToAlternateMode('再次失败')).toBe(false);
    expect(onStage).toHaveBeenCalledWith('reconnecting', expect.stringContaining('正在切换到MSE'));
  });

  it('hands post-playing recovery to the outer dual-video buffer without clearing the current frame', () => {
    const onActiveRecovery = vi.fn();
    const { core, onStage } = createCore({
      modes: ['webrtc', 'mse'],
      onActiveRecovery,
    });
    (core as any).wsUrl = 'ws://example.test/stream?src=camera-original';
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).hasProducedFirstFrame = true;
    (core as any).activeMode = 'webrtc';
    (core as any).scheduleReconnect('WebRTC 画面停滞，正在恢复');

    expect(onActiveRecovery).toHaveBeenCalledOnce();
    expect(onActiveRecovery).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'WebRTC 画面停滞，正在恢复',
        failedMode: 'webrtc',
        diagnostics: expect.objectContaining({ activeMode: 'webrtc' }),
      }),
    );
    expect(onStage).toHaveBeenCalledWith('reconnecting', 'WebRTC 画面停滞，正在恢复');
    expect(HTMLMediaElement.prototype.load).not.toHaveBeenCalled();
    expect((core as any).reconnectTimer).toBeNull();
    expect((core as any).modeSwitchCount).toBe(0);
  });

  it('uses a short deadline for an automatic alternate and reacquires the source without an internal retry', async () => {
    const { core, onFailure } = createCore({
      modes: ['webrtc', 'mse'],
      firstFrameTimeoutMs: 25_000,
      alternateFirstFrameTimeoutMs: 100,
    });
    (core as any).stopped = false;
    (core as any).connectStartedAt = 456;
    (core as any).activeAttemptId = 51;
    (core as any).activeMode = 'mse';
    (core as any).modeOverride = 'mse';
    (core as any).automaticAlternatePending = true;
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).startFirstFrameProbe();
    await vi.advanceTimersByTimeAsync(99);
    expect(onFailure).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        mode: 'mse',
        message: expect.stringContaining('重新获取播放地址'),
      }),
    );
    expect(sockets).toHaveLength(0);
    expect((core as any).internalReconnectCount).toBe(0);
  });

  it('reacquires the source immediately when the automatic alternate transport fails', () => {
    const { core, onFailure } = createCore({ modes: ['webrtc', 'mse'] });
    (core as any).stopped = false;
    (core as any).modeOverride = 'mse';
    (core as any).automaticAlternatePending = true;

    (core as any).scheduleReconnect('MSE 连接失败');

    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        mode: 'mse',
        report: expect.objectContaining({ code: 'UNKNOWN_STREAM_ERROR' }),
      }),
    );
    expect(sockets).toHaveLength(0);
  });

  it('gives an outer active-alternate Core a short deadline and no same-URL retry', async () => {
    const { core, onFailure } = createCore({
      modes: ['mse'],
      attemptKind: 'active-alternate',
      firstFrameTimeoutMs: 25_000,
      alternateFirstFrameTimeoutMs: 100,
      maxInternalReconnects: 5,
    });
    (core as any).stopped = false;
    (core as any).connectStartedAt = 456;
    (core as any).activeAttemptId = 52;
    (core as any).activeMode = 'mse';
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).startFirstFrameProbe();
    await vi.advanceTimersByTimeAsync(99);
    expect(onFailure).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        mode: 'mse',
        message: expect.stringContaining('重新获取播放地址'),
      }),
    );
    expect((core as any).maxInternalReconnects).toBe(0);
    expect((core as any).internalReconnectCount).toBe(0);
    expect(sockets).toHaveLength(0);
  });

  it('ignores an active-alternate deadline that belongs to an older attempt', async () => {
    const { core, onFailure } = createCore({
      modes: ['mse'],
      attemptKind: 'active-alternate',
      alternateFirstFrameTimeoutMs: 100,
    });
    (core as any).stopped = false;
    (core as any).connectStartedAt = 700;
    (core as any).activeAttemptId = 70;
    (core as any).activeMode = 'mse';
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).startFirstFrameProbe();
    (core as any).activeAttemptId = 71;
    (core as any).connectStartedAt = 701;
    await vi.advanceTimersByTimeAsync(100);

    expect(onFailure).not.toHaveBeenCalled();
    expect((core as any).internalReconnectCount).toBe(0);
    expect(sockets).toHaveLength(0);
  });

  it('does not extend an active-alternate deadline while the primary frame is still pending', async () => {
    const onPlaying = vi.fn();
    const { core, video, onFailure } = createCore({
      modes: ['mse'],
      attemptKind: 'active-alternate',
      alternateFirstFrameTimeoutMs: 100,
      primaryFrameTimeoutMs: 3_000,
      onPlaying,
    });
    const callbacks: Array<(now: number) => void> = [];
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn((callback: (now: number) => void) => {
        callbacks.push(callback);
        return callbacks.length;
      }),
      cancelVideoFrameCallback: vi.fn(),
    });
    (core as any).stopped = false;
    (core as any).connectStartedAt = 800;
    (core as any).activeAttemptId = 80;
    (core as any).activeMode = 'mse';
    (core as any).mediaSource = {};
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    const pendingPlaying = (core as any).reportPlaying('mse');
    (core as any).startFirstFrameProbe();
    expect(callbacks).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(100);
    await pendingPlaying;

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(expect.objectContaining({ kind: 'source', mode: 'mse' }));
    expect(onPlaying).not.toHaveBeenCalled();
    expect((core as any).playingReported).toBe(false);
  });

  it('turns an active-alternate codec terminal into a source refresh without profile evidence', () => {
    const { core, onFailure } = createCore({
      modes: ['webrtc'],
      attemptKind: 'active-alternate',
      maxInternalReconnects: 2,
    });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    (core as any).stopped = false;
    (core as any).activeAttemptId = 53;
    (core as any).activeMode = 'webrtc';
    (core as any).attemptedModes = ['webrtc'];
    handlers.set('webrtc', vi.fn());

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'webrtc: codecs not matched' }));

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'source',
        mode: 'webrtc',
        report: expect.objectContaining({ type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR' }),
      }),
    );
    expect(
      onFailure.mock.calls.some(([event]) => event.kind === 'codec-unsupported' || event.kind === 'h265-decode'),
    ).toBe(false);
    expect((core as any).codecUnsupportedModes.size).toBe(0);
    expect((core as any).internalReconnectCount).toBe(0);
  });

  it('keeps an active-alternate H265 decode failure out of global profile evidence', () => {
    const { core, video, onFailure } = createCore({
      modes: ['mse'],
      attemptKind: 'active-alternate',
    });
    Object.defineProperty(video, 'error', { configurable: true, get: () => ({ code: 3 }) });
    (core as any).stopped = false;
    (core as any).activeAttemptId = 54;
    (core as any).activeMode = 'mse';
    (core as any).mseCodecs = 'video/mp4; codecs="hvc1.1.6.L153.B0"';
    ((core as any).messageHandlers as Map<string, unknown>).set('mse', vi.fn());

    (core as any).onPrimaryVideoError();

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(expect.objectContaining({ kind: 'source', mode: 'mse' }));
    expect(
      onFailure.mock.calls.some(([event]) => event.kind === 'h265-decode' || event.kind === 'codec-unsupported'),
    ).toBe(false);
    expect((core as any).codecUnsupportedModes.size).toBe(0);
  });

  it('reacquires the source immediately for an active-alternate transport failure', () => {
    const { core, onFailure } = createCore({
      modes: ['mse'],
      attemptKind: 'active-alternate',
      maxInternalReconnects: 3,
    });
    (core as any).stopped = false;
    (core as any).activeMode = 'mse';

    (core as any).scheduleReconnect('MSE 连接失败');

    expect(onFailure).toHaveBeenCalledOnce();
    expect(onFailure).toHaveBeenCalledWith(expect.objectContaining({ kind: 'source', mode: 'mse' }));
    expect((core as any).internalReconnectCount).toBe(0);
    expect(sockets).toHaveLength(0);
  });

  it('switches modes when decode counters advance but the primary video stops presenting frames', async () => {
    class AvailableMediaSource extends EventTarget {
      static isTypeSupported(): boolean {
        return true;
      }
    }
    vi.stubGlobal('MediaSource', AvailableMediaSource);
    const { core, video } = createCore({
      modes: ['webrtc', 'mse'],
      healthCheckIntervalMs: 5_000,
      healthFailureThreshold: 3,
    });
    Object.defineProperty(video, 'paused', { configurable: true, get: () => false });
    Object.assign(video, {
      requestVideoFrameCallback: vi.fn(() => 1),
      cancelVideoFrameCallback: vi.fn(),
    });
    (core as any).wsUrl = 'ws://example.test/stream?src=camera-original';
    (core as any).stopped = false;
    (core as any).playingReported = true;
    (core as any).activeMode = 'webrtc';
    (core as any).healthProbeGeneration = 7;
    (core as any).healthBaseline = { currentTime: 1, framesDecoded: 10, framesRendered: 10 };
    (core as any).latestRuntime = {
      inboundVideo: { framesDecoded: 11, framesRendered: 11 },
    };
    (core as any).lastPresentedFrameAt = Date.now() - 20_000;
    const sampleWebrtcRuntime = vi.spyOn(core as any, 'sampleWebrtcRuntime');

    await (core as any).checkPlaybackHealth('webrtc', 7);

    expect(sampleWebrtcRuntime).toHaveBeenCalledOnce();
    expect((core as any).modeOverride).toBe('mse');
    expect((core as any).modeSwitchCount).toBe(1);
  });

  it('does not turn an alternate-mode codec error into H264 evidence for a previously working mode', () => {
    const { core, onFailure } = createCore({ modes: ['webrtc', 'mse'] });
    const handlers = (core as any).messageHandlers as Map<string, unknown>;
    (core as any).stopped = false;
    (core as any).activeAttemptId = 41;
    (core as any).activeMode = 'mse';
    (core as any).attemptedModes = ['mse'];
    (core as any).failedModes.add('webrtc');
    handlers.set('mse', vi.fn());

    (core as any).handleJsonMessage(JSON.stringify({ type: 'error', value: 'mse: codecs not matched' }));

    expect(onFailure.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        kind: 'codec-unsupported',
        attemptedModes: ['webrtc', 'mse'],
      }),
    );
    expect(onFailure.mock.calls[1][0]).toEqual(expect.objectContaining({ kind: 'source' }));
  });
});
