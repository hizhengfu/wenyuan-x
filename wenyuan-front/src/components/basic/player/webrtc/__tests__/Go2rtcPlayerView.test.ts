// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Go2rtcPlayerView from '../Go2rtcPlayerView.vue';
import { confirmH264Fallback, isH265RuntimeUnusable, resetH265RuntimeUnusableForTests } from '../h265Capability';
import { createPlayerFunPlayLimiter, type PlayerFunPlay } from '../playerTypes';

const coreMock = vi.hoisted(() => ({ instances: [] as any[] }));

vi.mock('webrtc-adapter', () => ({}));
vi.mock('../go2rtcPlayerCore', () => ({
  Go2rtcPlayerCore: class {
    public readonly video: HTMLVideoElement;
    public readonly options: any;
    public wsUrl = '';
    public destroyed = false;
    public destroyOptions: Array<{ clearVideo?: boolean } | undefined> = [];

    constructor(video: HTMLVideoElement, options: any) {
      this.video = video;
      this.options = options;
      coreMock.instances.push(this);
    }

    connect(wsUrl: string) {
      this.wsUrl = wsUrl;
    }

    destroy(options?: { clearVideo?: boolean }) {
      this.destroyed = true;
      this.destroyOptions.push(options);
    }

    switchMode(mode: 'auto' | 'webrtc' | 'mse') {
      return mode === 'auto' || mode === 'webrtc' || mode === 'mse';
    }

    async getDiagnostics() {
      return this.getDiagnosticsSnapshot();
    }

    getDiagnosticsSnapshot() {
      return {
        timestamp: new Date().toISOString(),
        wsState: this.destroyed ? 3 : 1,
        pcConnectionState: this.destroyed ? null : 'connected',
        activeMode: null,
        mseCodecs: null,
        mseProgress: { bytesReceived: 0, bytesAppended: 0, bufferedEnd: 0, successfulAppends: 0 },
        webrtc: null,
        hevcLevel: null,
        modeRecovery: {
          configuredModes: this.options.modes,
          attemptedModes: this.options.modes,
          failedModes: [],
          modeOverride: null,
          manualMode: null,
          switchCount: 0,
          maxSwitches: 1,
          lastSwitchReason: null,
        },
        presentation: {
          requestVideoFrameCallbackSupported: false,
          lastPresentedFrameAt: null,
          waitingForPrimaryFrame: false,
          sourceTransition: null,
        },
        failureContext: { lastWebrtcSelection: null, lastPrimaryCommitFailure: null, lastMseFailure: null },
        video: { readyState: 0, networkState: 0, currentTime: 0, videoWidth: 0, videoHeight: 0 },
      };
    }
  },
}));

const h265Failure = {
  kind: 'h265-decode' as const,
  message: 'decode failed',
  report: { type: 'CODEC_MISMATCH' as const, code: 'H265_DECODE_FAILED' as const, mode: 'webrtc' as const },
  mode: 'webrtc' as const,
};

const sourceFailure = {
  kind: 'source' as const,
  message: 'source failed',
  report: { type: 'SOURCE_CONNECTION' as const, code: 'CONNECTION_REFUSED' as const, mode: 'webrtc' as const },
  mode: 'webrtc' as const,
};

const unknownSourceFailure = {
  kind: 'source' as const,
  message: 'transport retry exhausted',
  report: { type: 'UNKNOWN' as const, code: 'UNKNOWN_STREAM_ERROR' as const, mode: 'webrtc' as const },
  mode: 'webrtc' as const,
};

const unknownCodecFailure = {
  kind: 'source' as const,
  message: 'no playback mode',
  report: { type: 'CODEC_MISMATCH' as const, code: 'UNSUPPORTED_CODEC' as const, mode: 'unknown' as const },
  mode: 'unknown' as const,
};

function codecFailure(mode: 'webrtc' | 'mse', attemptId?: number, attemptedModes?: Array<'webrtc' | 'mse'>) {
  return {
    kind: 'codec-unsupported' as const,
    message: 'codec unsupported',
    report: { type: 'CODEC_MISMATCH' as const, code: 'UNSUPPORTED_CODEC' as const, mode },
    mode,
    ...(attemptId === undefined ? {} : { attemptId }),
    ...(attemptedModes ? { attemptedModes } : {}),
  };
}

function createFunPlay() {
  return vi.fn(async (...args: any[]) => ({
    data: {
      wsUrl: `/stream/api/ws?src=camera-1-${args[5]}`,
      streamType: 'L1',
      fallbackUsed: false,
      playbackProfile: args[5],
    },
  }));
}

async function mountPlayer(funPlay: ReturnType<typeof createFunPlay>, props: Record<string, unknown> = {}) {
  const wrapper = mount(Go2rtcPlayerView, {
    props: { cameraId: 'camera-1', funPlay, ...props },
    attachTo: document.body,
  });
  await flushPromises();
  return wrapper;
}

describe('Go2rtcPlayerView profile orchestration', () => {
  beforeEach(() => {
    coreMock.instances.length = 0;
    resetH265RuntimeUnusableForTests();
    document.body.innerHTML = '';
  });

  it('switches ORIGINAL to H264 once, confirms the first frame, and reuses the 30-day result', async () => {
    const firstFunPlay = createFunPlay();
    const firstWrapper = await mountPlayer(firstFunPlay);
    expect(firstFunPlay.mock.calls[0][5]).toBe('ORIGINAL');

    await coreMock.instances[0].options.onFailure(h265Failure);
    await flushPromises();
    expect(firstFunPlay).toHaveBeenCalledTimes(2);
    expect(firstFunPlay.mock.calls[1][5]).toBe('H264');

    await coreMock.instances[1].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(isH265RuntimeUnusable('camera-1')).toBe(true);
    firstWrapper.unmount();

    const secondFunPlay = createFunPlay();
    const secondWrapper = await mountPlayer(secondFunPlay);
    expect(secondFunPlay.mock.calls[0][5]).toBe('H264');
    secondWrapper.unmount();
  });

  it('stops after the H264 variant also fails instead of cycling back to ORIGINAL', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    await coreMock.instances[0].options.onFailure(h265Failure);
    await flushPromises();
    await coreMock.instances[1].options.onFailure(h265Failure);
    await flushPromises();

    expect(funPlay).toHaveBeenCalledTimes(2);
    expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);
    expect(wrapper.text()).toContain('请核查服务器转码与摄像头编码规格');
    wrapper.unmount();
  });

  it('uses the backend effective profile for a static source instead of treating requested H264 as real H264', async () => {
    confirmH264Fallback('camera-1', 'auto');
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=static-${args[5]}`,
        streamType: 'STATIC',
        fallbackUsed: false,
        playbackProfile: 'ORIGINAL',
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);
    expect(funPlay.mock.calls[0][5]).toBe('H264');

    await coreMock.instances[0].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.265',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    expect(wrapper.text()).not.toContain('H.264 兼容流实际仍为 H.265');
    expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
    expect(funPlay).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('rejects an effective H264 source that actually presents H265 without recording the attempt as playing', async () => {
    confirmH264Fallback('camera-1', 'auto');
    const wrapper = await mountPlayer(createFunPlay());

    coreMock.instances[0].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.265',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    expect(wrapper.text()).toContain('H.264 兼容流实际仍为 H.265');
    const attempt = (wrapper.vm as any).getPlaybackDiagnostics().coreAttempts.recentAttempts[0];
    expect(attempt).toEqual(expect.objectContaining({ status: 'failed', playing: null }));
    expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
    wrapper.unmount();
  });

  it('does not request another H264 variant when a static effective ORIGINAL source cannot decode', async () => {
    confirmH264Fallback('camera-1', 'auto');
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=static-${args[5]}`,
        streamType: 'STATIC',
        fallbackUsed: false,
        playbackProfile: 'ORIGINAL',
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);

    await coreMock.instances[0].options.onFailure(h265Failure);
    await flushPromises();

    expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);
    expect(wrapper.text()).toContain('静态 H.265 视频源无法解码');
    expect(wrapper.text()).not.toContain('H.264 兼容流仍无法解码');
    wrapper.unmount();
  });

  it('keeps H264 for one source refresh, then returns to ORIGINAL once without a profile loop', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      expect(funPlay.mock.calls[0][5]).toBe('H264');

      await coreMock.instances[0].options.onFailure(sourceFailure);
      await flushPromises();
      expect(funPlay).toHaveBeenCalledTimes(1);
      const failureDiagnostics = (wrapper.vm as any).getPlaybackDiagnostics();
      expect(failureDiagnostics.diagnosis).toEqual(
        expect.objectContaining({
          title: '摄像头或上游视频源连接失败',
          conclusion: expect.stringContaining('媒体服务未能连接'),
        }),
      );
      expect(failureDiagnostics.coreAttempts.recentAttempts[0]).toEqual(
        expect.objectContaining({ status: 'failed', lastFailure: expect.objectContaining({ kind: 'source' }) }),
      );
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(funPlay.mock.calls[1][5]).toBe('H264');
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(true);

      await coreMock.instances[1].options.onFailure(sourceFailure);
      await flushPromises();
      expect(funPlay.mock.calls[2][5]).toBe('ORIGINAL');
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);

      await coreMock.instances[2].options.onFailure(h265Failure);
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'H264', 'ORIGINAL']);
      expect(wrapper.text()).toContain('已回退原始视频源');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('switches after every enabled mode is codec-terminal even when capability preflight reports H265 support', async () => {
    const getCapabilities = vi.fn(() => ({ codecs: [{ mimeType: 'video/H265' }] }));
    const isTypeSupported = vi.fn(() => true);
    vi.stubGlobal('RTCRtpReceiver', { getCapabilities });
    vi.stubGlobal('MediaSource', { isTypeSupported });
    try {
      resetH265RuntimeUnusableForTests();
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay, { mode: 'webrtc,mse' });

      await coreMock.instances[0].options.onFailure(codecFailure('webrtc'));
      await flushPromises();
      expect(funPlay).toHaveBeenCalledTimes(1);

      await coreMock.instances[0].options.onFailure(codecFailure('mse'));
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);
      const diagnostics = (wrapper.vm as any).getPlaybackDiagnostics();
      expect(diagnostics.h265.usable).toBe(true);
      expect(diagnostics.h265.lastCodecTerminal).toEqual(
        expect.objectContaining({
          expectedModes: ['webrtc', 'mse'],
          reports: {
            webrtc: expect.objectContaining({ code: 'UNSUPPORTED_CODEC' }),
            mse: expect.objectContaining({ code: 'UNSUPPORTED_CODEC' }),
          },
        }),
      );

      await coreMock.instances[1].options.onFailure(codecFailure('webrtc'));
      await coreMock.instances[1].options.onFailure(codecFailure('mse'));
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);
      expect(wrapper.text()).toContain('已启用的播放方式均不支持当前视频编码');
      wrapper.unmount();
    } finally {
      vi.unstubAllGlobals();
      resetH265RuntimeUnusableForTests();
    }
  });

  it('preselects H264 on the first source request when the browser explicitly cannot use H265', async () => {
    vi.stubGlobal('RTCRtpReceiver', { getCapabilities: () => ({ codecs: [{ mimeType: 'video/H264' }] }) });
    vi.stubGlobal('MediaSource', { isTypeSupported: () => false });
    try {
      resetH265RuntimeUnusableForTests();
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay, { mode: 'webrtc,mse' });

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);
      expect((wrapper.vm as any).getPlaybackDiagnostics().h265.usable).toBe(false);
      wrapper.unmount();
    } finally {
      vi.unstubAllGlobals();
      resetH265RuntimeUnusableForTests();
    }
  });

  it('uses the modes actually started by Core instead of waiting for an unavailable configured mode', async () => {
    vi.stubGlobal('RTCRtpReceiver', { getCapabilities: () => ({ codecs: [{ mimeType: 'video/H265' }] }) });
    vi.stubGlobal('MediaSource', { isTypeSupported: () => false });
    try {
      resetH265RuntimeUnusableForTests();
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay, { mode: 'webrtc,mse' });

      await coreMock.instances[0].options.onFailure(codecFailure('webrtc', 11, ['webrtc']));
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);
      wrapper.unmount();
    } finally {
      vi.unstubAllGlobals();
      resetH265RuntimeUnusableForTests();
    }
  });

  it('never combines codec terminal evidence from different Core attempts', async () => {
    vi.stubGlobal('RTCRtpReceiver', { getCapabilities: () => ({ codecs: [{ mimeType: 'video/H265' }] }) });
    vi.stubGlobal('MediaSource', { isTypeSupported: () => true });
    try {
      resetH265RuntimeUnusableForTests();
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay, { mode: 'webrtc,mse' });
      const core = coreMock.instances[0];

      await core.options.onFailure(codecFailure('webrtc', 21, ['webrtc', 'mse']));
      await core.options.onFailure(codecFailure('mse', 22, ['webrtc', 'mse']));
      await flushPromises();
      expect(funPlay).toHaveBeenCalledTimes(1);

      await core.options.onFailure(codecFailure('webrtc', 22, ['webrtc', 'mse']));
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);
      wrapper.unmount();
    } finally {
      vi.unstubAllGlobals();
      resetH265RuntimeUnusableForTests();
    }
  });

  it('reacquires UNKNOWN transport failures without marking the camera source as failed', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);

      const staleCore = coreMock.instances[0];
      await staleCore.options.onFailure(unknownSourceFailure);
      await staleCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();
      expect(wrapper.text()).toContain('未知错误');
      expect((wrapper.vm as any).getPlaybackDiagnostics().recovery.lastSourceFailure).toEqual(
        expect.objectContaining({ type: 'UNKNOWN', code: 'UNKNOWN_STREAM_ERROR', mode: 'webrtc' }),
      );
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'ORIGINAL']);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(false);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('normalizes cached H264 to ORIGINAL after a static response before later source recovery', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      const funPlay = vi.fn(async (...args: any[]) => ({
        data: {
          wsUrl: `/stream/api/ws?src=static-${args[5]}`,
          streamType: 'STATIC',
          fallbackUsed: false,
          playbackProfile: 'ORIGINAL',
        },
      }));
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);
      expect(funPlay.mock.calls[0][5]).toBe('H264');
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);

      await coreMock.instances[0].options.onFailure(sourceFailure);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'ORIGINAL']);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('returns to ORIGINAL when the one H264 source refresh request itself rejects', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      let requestCount = 0;
      const funPlay = vi.fn(async (...args: any[]) => {
        requestCount += 1;
        if (requestCount === 2) throw new Error('refresh failed');
        return {
          data: {
            wsUrl: `/stream/api/ws?src=camera-1-${args[5]}`,
            streamType: 'L1',
            fallbackUsed: false,
            playbackProfile: args[5],
          },
        };
      });
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);

      await coreMock.instances[0].options.onFailure(sourceFailure);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'H264', 'ORIGINAL']);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('bounds a cached H264 play API rejection before returning to ORIGINAL', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      let requestCount = 0;
      const funPlay = vi.fn(async (...args: any[]) => {
        requestCount += 1;
        if (requestCount <= 2) throw new Error('play API failed');
        return {
          data: {
            wsUrl: `/stream/api/ws?src=camera-1-${args[5]}`,
            streamType: 'L1',
            fallbackUsed: false,
            playbackProfile: args[5],
          },
        };
      });
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);

      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'H264', 'ORIGINAL']);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(false);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not block a cached H264 /play request on the asynchronous H265 capability report', async () => {
    const mediaCapabilitiesDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaCapabilities');
    let resolveCapability!: (value: { supported: boolean; smooth: boolean; powerEfficient: boolean }) => void;
    const pendingCapability = new Promise<{ supported: boolean; smooth: boolean; powerEfficient: boolean }>(
      (resolve) => {
        resolveCapability = resolve;
      },
    );
    const decodingInfo = vi.fn(() => pendingCapability);
    Object.defineProperty(navigator, 'mediaCapabilities', {
      configurable: true,
      value: { decodingInfo },
    });
    confirmH264Fallback('camera-1', 'auto');
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    try {
      expect(decodingInfo).toHaveBeenCalledTimes(2);
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);
      expect(coreMock.instances).toHaveLength(1);
    } finally {
      resolveCapability({ supported: true, smooth: true, powerEfficient: true });
      await flushPromises();
      wrapper.unmount();
      if (mediaCapabilitiesDescriptor)
        Object.defineProperty(navigator, 'mediaCapabilities', mediaCapabilitiesDescriptor);
      else Reflect.deleteProperty(navigator, 'mediaCapabilities');
    }
  });

  it('does not create a stale Core when stream-status synchronously reconnects', async () => {
    let requestCount = 0;
    const funPlay = vi.fn(async (...args: any[]) => {
      requestCount += 1;
      return {
        data: {
          wsUrl: `/stream/api/ws?src=source-${requestCount}`,
          streamType: 'L1',
          fallbackUsed: false,
          playbackProfile: args[5],
        },
      };
    });
    let wrapper: any;
    let reconnected = false;
    const onStreamStatus = vi.fn(() => {
      if (reconnected) return;
      reconnected = true;
      (wrapper.vm as any).reconnectNow();
    });

    wrapper = mount(Go2rtcPlayerView, {
      props: { cameraId: 'camera-1', funPlay: funPlay as ReturnType<typeof createFunPlay>, onStreamStatus },
      attachTo: document.body,
    });
    await flushPromises();

    expect(funPlay).toHaveBeenCalledTimes(2);
    expect(onStreamStatus).toHaveBeenCalledTimes(2);
    expect(coreMock.instances).toHaveLength(1);
    expect(coreMock.instances[0].wsUrl).toContain('source-2');
    wrapper.unmount();
  });

  it('does not create a Core when stream-status synchronously stops playback', async () => {
    const funPlay = createFunPlay();
    let wrapper: any;
    const onStreamStatus = vi.fn(() => (wrapper.vm as any).stopPlay());

    wrapper = mount(Go2rtcPlayerView, {
      props: { cameraId: 'camera-1', funPlay, onStreamStatus },
      attachTo: document.body,
    });
    await flushPromises();

    expect(funPlay).toHaveBeenCalledOnce();
    expect(onStreamStatus).toHaveBeenCalledOnce();
    expect(coreMock.instances).toHaveLength(0);
    expect((wrapper.vm as any).getPlaybackDiagnostics()).toEqual(expect.objectContaining({ status: 'idle' }));
    wrapper.unmount();
  });

  it('does not loop or switch profiles for an unscoped codec failure without per-mode evidence', async () => {
    confirmH264Fallback('camera-1', 'auto');
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);

    await coreMock.instances[0].options.onFailure(unknownCodecFailure);
    await flushPromises();

    expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);
    expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
    expect(wrapper.text()).toContain('没有可用的播放方式');

    await coreMock.instances[0].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(wrapper.text()).toContain('没有可用的播放方式');
    wrapper.unmount();
  });

  it('aborts an obsolete play request when the camera changes', async () => {
    const pending: Array<{ args: any[]; resolve: (value: any) => void }> = [];
    const funPlay = vi.fn(
      (...args: any[]) =>
        new Promise((resolve) => {
          pending.push({ args, resolve });
        }),
    );
    const wrapper = mount(Go2rtcPlayerView, {
      props: { cameraId: 'camera-1', funPlay },
      attachTo: document.body,
    });
    await flushPromises();
    const firstSignal = pending[0].args[7]?.signal as AbortSignal;
    expect(firstSignal?.aborted).toBe(false);

    await wrapper.setProps({ cameraId: 'camera-2' });
    await flushPromises();
    expect(firstSignal.aborted).toBe(true);
    expect(pending).toHaveLength(2);

    pending[1].resolve({
      data: { wsUrl: '/stream/api/ws?src=camera-2', streamType: 'L1', playbackProfile: 'ORIGINAL' },
    });
    await flushPromises();
    expect(coreMock.instances).toHaveLength(1);
    wrapper.unmount();
  });

  it('retains source acquisition failure and retry diagnostics before any Core is created', async () => {
    vi.useFakeTimers();
    try {
      const requestError = Object.assign(new Error('sensitive playback URL must not be exposed'), {
        code: 'ERR_BAD_RESPONSE',
        response: { status: 503, data: { token: 'secret' } },
      });
      const funPlay = vi.fn(async () => {
        throw requestError;
      });
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, { streamType: 'L0' });
      const diagnostics = (wrapper.vm as any).getPlaybackDiagnostics();

      expect(coreMock.instances).toHaveLength(0);
      expect(diagnostics).toEqual(
        expect.objectContaining({
          status: 'error',
          stage: 'retrying',
          core: null,
          diagnosis: expect.objectContaining({
            title: '播放地址获取失败',
            conclusion: expect.stringContaining('尚未创建媒体 Core'),
          }),
          coreAttempts: { totalAttempts: 0, recentAttempts: [] },
          sourceAcquisition: {
            totalAttempts: 1,
            recentAttempts: [
              expect.objectContaining({
                attempt: 1,
                status: 'failed',
                request: expect.objectContaining({
                  streamType: 'L0',
                  playbackProfile: 'ORIGINAL',
                  forceRefresh: false,
                  sourceError: false,
                }),
                response: null,
                error: {
                  name: 'Error',
                  code: 'ERR_BAD_RESPONSE',
                  httpStatus: 503,
                  message: '播放地址请求失败（HTTP 503）',
                },
              }),
            ],
          },
        }),
      );
      expect(diagnostics.recovery.scheduledSourceRetry).toEqual(
        expect.objectContaining({ attempt: 1, delayMs: 3_000, forceRefresh: false, sourceError: false }),
      );
      expect(JSON.stringify(diagnostics)).not.toContain('sensitive playback URL');
      expect(JSON.stringify(diagnostics)).not.toContain('secret');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('splits capability, raw-provider, and total source acquisition time without requiring a limiter callback', async () => {
    const mediaCapabilitiesDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaCapabilities');
    let resolveCapability!: (value: { supported: boolean; smooth: boolean; powerEfficient: boolean }) => void;
    const capability = new Promise<{ supported: boolean; smooth: boolean; powerEfficient: boolean }>((resolve) => {
      resolveCapability = resolve;
    });
    Object.defineProperty(navigator, 'mediaCapabilities', {
      configurable: true,
      value: { decodingInfo: vi.fn(() => capability) },
    });
    let now = 1_000;
    const dateNow = vi.spyOn(Date, 'now').mockImplementation(() => now);
    let resolveProvider!: (value: unknown) => void;
    const funPlay = vi.fn(
      (..._args: Parameters<PlayerFunPlay>) =>
        new Promise((resolve) => {
          resolveProvider = resolve;
        }),
    );
    const wrapper = mount(Go2rtcPlayerView, {
      props: { cameraId: 'camera-timing', funPlay },
      attachTo: document.body,
    });
    try {
      await flushPromises();
      expect(funPlay).not.toHaveBeenCalled();

      now = 1_040;
      resolveCapability({ supported: true, smooth: true, powerEfficient: true });
      await flushPromises();
      expect(funPlay).toHaveBeenCalledOnce();
      expect(funPlay.mock.calls[0][7]).toEqual(
        expect.objectContaining({ signal: expect.any(AbortSignal), onQueueTiming: expect.any(Function) }),
      );

      now = 1_100;
      resolveProvider({
        data: {
          wsUrl: '/stream/api/ws?src=camera-timing',
          streamType: 'L1',
          fallbackUsed: false,
          playbackProfile: 'ORIGINAL',
        },
      });
      await flushPromises();

      const sourceAttempt = (wrapper.vm as any).getPlaybackDiagnostics().sourceAcquisition.recentAttempts[0];
      expect(sourceAttempt).toEqual(
        expect.objectContaining({
          status: 'succeeded',
          capabilityWaitMs: 40,
          queueWaitMs: 0,
          providerRequestMs: 60,
          totalMs: 100,
          durationMs: 100,
        }),
      );
    } finally {
      wrapper.unmount();
      dateNow.mockRestore();
      if (mediaCapabilitiesDescriptor)
        Object.defineProperty(navigator, 'mediaCapabilities', mediaCapabilitiesDescriptor);
      else Reflect.deleteProperty(navigator, 'mediaCapabilities');
    }
  });

  it('records limiter queue wait separately from provider execution time', async () => {
    confirmH264Fallback('camera-1', 'auto');
    let now = 1_000;
    const dateNow = vi.spyOn(Date, 'now').mockImplementation(() => now);
    const resolvers: Array<(value: unknown) => void> = [];
    const provider = vi.fn(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const limited = createPlayerFunPlayLimiter(provider as PlayerFunPlay, () => 1);
    const blocker = limited('blocker');
    now = 1_050;
    const wrapper = mount(Go2rtcPlayerView, {
      props: { cameraId: 'camera-1', funPlay: limited },
      attachTo: document.body,
    });
    try {
      await flushPromises();
      expect(provider).toHaveBeenCalledOnce();

      now = 1_250;
      resolvers[0]({});
      await blocker;
      await flushPromises();
      expect(provider).toHaveBeenCalledTimes(2);

      now = 1_300;
      resolvers[1]({
        data: {
          wsUrl: '/stream/api/ws?src=camera-queued',
          streamType: 'L1',
          fallbackUsed: false,
          playbackProfile: 'H264',
        },
      });
      await flushPromises();

      const sourceAttempt = (wrapper.vm as any).getPlaybackDiagnostics().sourceAcquisition.recentAttempts[0];
      expect(sourceAttempt).toEqual(
        expect.objectContaining({
          capabilityWaitMs: 0,
          queueWaitMs: 200,
          providerRequestMs: 50,
          totalMs: 250,
        }),
      );
    } finally {
      wrapper.unmount();
      dateNow.mockRestore();
    }
  });

  it('delays only the initial grid start and still exposes manual mode switching', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = mount(Go2rtcPlayerView, {
        props: { cameraId: 'camera-1', funPlay, startDelayMs: 500 },
        attachTo: document.body,
      });
      await flushPromises();
      expect(funPlay).not.toHaveBeenCalled();

      await vi.advanceTimersByTimeAsync(499);
      expect(funPlay).not.toHaveBeenCalled();
      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();
      expect(funPlay).toHaveBeenCalledOnce();
      expect((wrapper.vm as any).switchPlaybackMode('mse')).toBe(true);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('uses two video elements and swaps only after the standby core presents its first frame', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      const videos = wrapper.findAll('video');
      expect(videos).toHaveLength(2);
      expect(wrapper.find('canvas').exists()).toBe(false);

      const firstCore = coreMock.instances[0];
      await firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();
      expect(firstCore.video.classList.contains('is-active')).toBe(true);

      await firstCore.options.onFailure(sourceFailure);
      await flushPromises();
      expect(firstCore.destroyOptions).toContainEqual({ clearVideo: false });
      expect(firstCore.video.classList.contains('is-active')).toBe(true);

      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      const secondCore = coreMock.instances[1];
      expect(secondCore.video).not.toBe(firstCore.video);
      expect(secondCore.video.classList.contains('is-active')).toBe(false);
      expect(firstCore.video.classList.contains('is-active')).toBe(true);

      const oldVideoLoad = vi.spyOn(firstCore.video, 'load');
      await secondCore.options.onPlaying({
        mode: 'mse',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();
      expect(secondCore.video.classList.contains('is-active')).toBe(true);
      expect(firstCore.video.classList.contains('is-active')).toBe(false);
      expect(oldVideoLoad).toHaveBeenCalledOnce();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps only the synchronous snapshot after a presented frame while the information panel is closed', async () => {
    const wrapper = await mountPlayer(createFunPlay());
    const playerCore = coreMock.instances[0];
    const diagnosticsSpy = vi.spyOn(playerCore, 'getDiagnostics');

    playerCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await wrapper.vm.$nextTick();

    expect(playerCore.video.classList.contains('is-active')).toBe(true);
    expect(wrapper.find('.status-overlay').exists()).toBe(false);
    expect((wrapper.vm as any).getPlaybackDiagnostics()).toEqual(
      expect.objectContaining({ status: 'playing', stage: 'playing' }),
    );
    expect(diagnosticsSpy).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('refreshes full stats after playing only when the information panel is already open', async () => {
    const wrapper = await mountPlayer(createFunPlay());
    const firstCore = coreMock.instances[0];
    const diagnosticsSpy = vi.spyOn(firstCore, 'getDiagnostics');
    await wrapper.find('.video-info-button').trigger('click');
    await flushPromises();
    expect(diagnosticsSpy).toHaveBeenCalledOnce();
    diagnosticsSpy.mockClear();

    let resolveDiagnostics!: (value: any) => void;
    const pendingDiagnostics = new Promise<any>((resolve) => {
      resolveDiagnostics = resolve;
    });
    diagnosticsSpy.mockReturnValue(pendingDiagnostics);

    firstCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await wrapper.vm.$nextTick();
    expect(diagnosticsSpy).toHaveBeenCalledOnce();
    const liveDiagnostics = firstCore.getDiagnosticsSnapshot();
    firstCore.options.onActiveRecovery({
      message: 'WebRTC 画面停滞',
      failedMode: 'webrtc',
      diagnostics: liveDiagnostics,
    });
    await wrapper.vm.$nextTick();

    resolveDiagnostics({ ...liveDiagnostics, wsState: WebSocket.CLOSED, pcConnectionState: null });
    await flushPromises();

    const firstAttempt = (wrapper.vm as any).getPlaybackDiagnostics().coreAttempts.recentAttempts[0];
    expect(firstAttempt.diagnostics).toEqual(expect.objectContaining({ wsState: 1, pcConnectionState: 'connected' }));
    wrapper.unmount();
  });

  it('keeps playing when the synchronous snapshot fails without starting a closed-panel stats request', async () => {
    const wrapper = await mountPlayer(createFunPlay());
    const playerCore = coreMock.instances[0];
    const diagnosticsSpy = vi.spyOn(playerCore, 'getDiagnostics');
    vi.spyOn(playerCore, 'getDiagnosticsSnapshot').mockImplementation(() => {
      throw new Error('diagnostics unavailable');
    });

    playerCore.options.onPlaying({
      mode: 'mse',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    expect(playerCore.video.classList.contains('is-active')).toBe(true);
    expect((wrapper.vm as any).getPlaybackDiagnostics()).toEqual(
      expect.objectContaining({ status: 'playing', stage: 'playing' }),
    );
    expect(diagnosticsSpy).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('recovers an active transport on the standby video without reacquiring the play URL', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    const firstCore = coreMock.instances[0];
    await firstCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    firstCore.options.onActiveRecovery({
      message: 'WebRTC 画面停滞，正在恢复',
      failedMode: 'webrtc',
      diagnostics: firstCore.getDiagnosticsSnapshot(),
    });
    await flushPromises();
    expect(funPlay).toHaveBeenCalledOnce();
    expect(coreMock.instances).toHaveLength(2);
    expect(coreMock.instances[1].options.modes).toEqual(['mse']);
    expect(coreMock.instances[1].options.attemptKind).toBe('active-alternate');
    expect(firstCore.destroyOptions).toContainEqual({ clearVideo: false });
    expect(firstCore.video.classList.contains('is-active')).toBe(true);
    expect(coreMock.instances[1].video.classList.contains('is-active')).toBe(false);

    await coreMock.instances[1].options.onPlaying({
      mode: 'mse',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(coreMock.instances[1].video.classList.contains('is-active')).toBe(true);
    expect(firstCore.video.classList.contains('is-active')).toBe(false);
    wrapper.unmount();
  });

  it('refreshes /play after an active alternate fails without using its codec failure as H264 evidence', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      const firstCore = coreMock.instances[0];
      firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      firstCore.options.onActiveRecovery({
        message: 'WebRTC 画面停滞',
        failedMode: 'webrtc',
        diagnostics: firstCore.getDiagnosticsSnapshot(),
      });
      const alternateCore = coreMock.instances[1];
      expect(alternateCore.options.attemptKind).toBe('active-alternate');
      await alternateCore.options.onFailure({
        ...h265Failure,
        mode: 'mse',
        report: { ...h265Failure.report, mode: 'mse' },
      });
      await flushPromises();

      expect(funPlay).toHaveBeenCalledOnce();
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'ORIGINAL']);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(false);
      expect(coreMock.instances[2].options.attemptKind).toBe('initial');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps a static effective ORIGINAL profile after an active alternate fails without caching H264', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      const funPlay = vi.fn(async (...args: any[]) => ({
        data: {
          wsUrl: `/stream/api/ws?src=static-${args[5]}`,
          streamType: 'STATIC',
          fallbackUsed: false,
          playbackProfile: 'ORIGINAL',
        },
      }));
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>);
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264']);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);

      const firstCore = coreMock.instances[0];
      await firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.265',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();
      firstCore.options.onActiveRecovery({
        message: 'WebRTC 画面停滞',
        failedMode: 'webrtc',
        diagnostics: firstCore.getDiagnosticsSnapshot(),
      });
      await flushPromises();

      const alternateCore = coreMock.instances[1];
      expect(alternateCore.options.attemptKind).toBe('active-alternate');
      expect(alternateCore.options.modes).toEqual(['mse']);
      await alternateCore.options.onFailure({
        ...h265Failure,
        mode: 'mse',
        report: { ...h265Failure.report, mode: 'mse' },
      });
      await flushPromises();

      expect(funPlay).toHaveBeenCalledOnce();
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'ORIGINAL']);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(false);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('preserves sourceError only for an explicit source failure from the active alternate', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      const firstCore = coreMock.instances[0];
      firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      firstCore.options.onActiveRecovery({
        message: 'WebRTC 画面停滞',
        failedMode: 'webrtc',
        diagnostics: firstCore.getDiagnosticsSnapshot(),
      });
      await coreMock.instances[1].options.onFailure(sourceFailure);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();

      expect(funPlay).toHaveBeenCalledTimes(2);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(true);
      expect(funPlay.mock.calls[1][4]).toEqual(sourceFailure.report);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('counts an active-alternate H264 refresh so the next failure returns to ORIGINAL without a loop', async () => {
    vi.useFakeTimers();
    try {
      confirmH264Fallback('camera-1', 'auto');
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      const firstCore = coreMock.instances[0];
      firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      firstCore.options.onActiveRecovery({
        message: 'WebRTC 画面停滞',
        failedMode: 'webrtc',
        diagnostics: firstCore.getDiagnosticsSnapshot(),
      });
      await coreMock.instances[1].options.onFailure(sourceFailure);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'H264']);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(true);

      await coreMock.instances[2].options.onFailure(sourceFailure);
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['H264', 'H264', 'ORIGINAL']);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('reacquires the source after the one alternate mode also fails instead of flipping modes forever', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = createFunPlay();
      const wrapper = await mountPlayer(funPlay);
      const firstCore = coreMock.instances[0];
      await firstCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      firstCore.options.onActiveRecovery({
        message: 'WebRTC 画面停滞',
        failedMode: 'webrtc',
        diagnostics: firstCore.getDiagnosticsSnapshot(),
      });
      const alternateCore = coreMock.instances[1];
      expect(alternateCore.options.modes).toEqual(['mse']);
      await alternateCore.options.onPlaying({
        mode: 'mse',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      alternateCore.options.onActiveRecovery({
        message: 'MSE 画面停滞',
        failedMode: 'mse',
        diagnostics: alternateCore.getDiagnosticsSnapshot(),
      });
      expect(coreMock.instances).toHaveLength(2);
      expect(funPlay).toHaveBeenCalledOnce();

      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(funPlay).toHaveBeenCalledTimes(2);
      expect(funPlay.mock.calls[1][1]).toBe(true);
      expect(funPlay.mock.calls[1][3]).toBe(false);
      expect(coreMock.instances[2].options.modes).toEqual(['webrtc', 'mse']);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('freezes live Core diagnostics before destroy instead of recording the closed teardown state', async () => {
    const wrapper = await mountPlayer(createFunPlay());
    const firstCore = coreMock.instances[0];
    const liveDiagnostics = firstCore.getDiagnosticsSnapshot();
    expect(liveDiagnostics.wsState).toBe(1);
    // Core 的真实回调发生在内部 transport 已断开之后，故障前快照由事件显式携带。
    firstCore.destroyed = true;

    firstCore.options.onActiveRecovery({
      message: 'WebRTC 画面停滞',
      failedMode: 'webrtc',
      diagnostics: liveDiagnostics,
    });
    await flushPromises();

    expect(firstCore.destroyed).toBe(true);
    expect(firstCore.getDiagnosticsSnapshot().wsState).toBe(3);
    const firstAttempt = (wrapper.vm as any).getPlaybackDiagnostics().coreAttempts.recentAttempts[0];
    expect(firstAttempt.diagnostics).toEqual(expect.objectContaining({ wsState: 1, pcConnectionState: 'connected' }));
    wrapper.unmount();
  });

  it('keeps the current frame visible until a changed stream presents its first frame', async () => {
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=camera-1-${args[2]}`,
        streamType: args[2],
        fallbackUsed: false,
        playbackProfile: args[5],
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, {
      streamType: 'L1',
      allowConfirmedSubstreamFallback: true,
    });
    const firstCore = coreMock.instances[0];
    await firstCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    await wrapper.setProps({ streamType: 'L0' });
    await flushPromises();
    const secondCore = coreMock.instances[1];
    expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L1', 'L0']);
    expect(secondCore.video).not.toBe(firstCore.video);
    expect(firstCore.video.classList.contains('is-active')).toBe(true);
    expect(secondCore.video.classList.contains('is-active')).toBe(false);

    await secondCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(secondCore.video.classList.contains('is-active')).toBe(true);
    expect(firstCore.video.classList.contains('is-active')).toBe(false);
    wrapper.unmount();
  });

  it('falls back from an automatically requested L0 to the previously confirmed L1 without clearing the old frame', async () => {
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=camera-1-${args[2]}`,
        streamType: args[2],
        fallbackUsed: false,
        playbackProfile: args[5],
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, {
      streamType: 'L1',
      allowConfirmedSubstreamFallback: true,
    });
    const substreamCore = coreMock.instances[0];
    await substreamCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    await wrapper.setProps({ streamType: 'L0' });
    await flushPromises();
    const mainStreamCore = coreMock.instances[1];
    await mainStreamCore.options.onFailure(sourceFailure);
    await flushPromises();

    expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L1', 'L0', 'L1']);
    expect(coreMock.instances[2].options.modes).toEqual(['webrtc', 'mse']);
    expect(substreamCore.video.classList.contains('is-active')).toBe(true);
    expect((wrapper.vm as any).getPlaybackDiagnostics().recovery.adaptiveStreamFallback).toEqual(
      expect.objectContaining({ reason: expect.stringContaining('连接失败') }),
    );
    wrapper.unmount();
  });

  it('restarts a failed normalized L1 Core when the automatic L0 fallback returns to confirmed L1', async () => {
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=camera-1-${args[2]}`,
        streamType: args[2] === 'L0' ? 'L1' : args[2],
        fallbackUsed: args[2] === 'L0',
        playbackProfile: args[5],
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, {
      streamType: 'L1',
      allowConfirmedSubstreamFallback: true,
    });
    const confirmedSubstreamCore = coreMock.instances[0];
    confirmedSubstreamCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    await wrapper.setProps({ streamType: 'L0' });
    await flushPromises();
    const normalizedFallbackCore = coreMock.instances[1];
    expect((wrapper.vm as any).getPlaybackDiagnostics().actual).toEqual(
      expect.objectContaining({ streamType: 'L1', fallbackUsed: true }),
    );

    await normalizedFallbackCore.options.onFailure(sourceFailure);
    await flushPromises();

    expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L1', 'L0', 'L1']);
    expect(coreMock.instances).toHaveLength(3);
    expect(confirmedSubstreamCore.video.classList.contains('is-active')).toBe(true);
    expect(normalizedFallbackCore.destroyOptions).toContainEqual({ clearVideo: false });
    wrapper.unmount();
  });

  it('does not override an explicit manual L0 choice with the adaptive L1 fallback', async () => {
    vi.useFakeTimers();
    try {
      const funPlay = vi.fn(async (...args: any[]) => ({
        data: {
          wsUrl: `/stream/api/ws?src=camera-1-${args[2]}`,
          streamType: args[2],
          fallbackUsed: false,
          playbackProfile: args[5],
        },
      }));
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, {
        streamType: 'L1',
        allowConfirmedSubstreamFallback: true,
      });
      await coreMock.instances[0].options.onPlaying({
        mode: 'webrtc',
        codec: 'H.264',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      (wrapper.vm as any).setStreamType('L0');
      await flushPromises();
      await coreMock.instances[1].options.onFailure(sourceFailure);
      await flushPromises();

      expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L1', 'L0']);
      expect((wrapper.vm as any).getPlaybackDiagnostics().recovery.adaptiveStreamFallback).toBeNull();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not reconnect when an L1 fallback is normalized to the already active L0 stream', async () => {
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: '/stream/api/ws?src=camera-1-l0',
        streamType: 'L0',
        fallbackUsed: true,
        playbackProfile: args[5],
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, { streamType: 'L1' });
    expect(funPlay).toHaveBeenCalledOnce();

    await wrapper.setProps({ streamType: 'L0' });
    await flushPromises();
    expect(funPlay).toHaveBeenCalledOnce();
    expect(coreMock.instances).toHaveLength(1);
    expect((wrapper.vm as any).getPlaybackDiagnostics().actual).toEqual(
      expect.objectContaining({ streamType: 'L0', fallbackUsed: false }),
    );
    wrapper.unmount();
  });

  it('cancels an in-flight L0 request instead of reusing its stale actual L1 snapshot', async () => {
    vi.useFakeTimers();
    try {
      let requestCount = 0;
      let resolveStaleRequest!: (value: unknown) => void;
      const funPlay = vi.fn((...args: any[]) => {
        requestCount += 1;
        if (requestCount === 2) {
          return new Promise((resolve) => {
            resolveStaleRequest = resolve;
          });
        }
        return Promise.resolve({
          data: {
            wsUrl: `/stream/api/ws?src=source-${requestCount}`,
            streamType: 'L1',
            fallbackUsed: args[2] === 'L0',
            playbackProfile: args[5],
          },
        });
      });
      const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, { streamType: 'L0' });
      const initialCore = coreMock.instances[0];

      await initialCore.options.onFailure(sourceFailure);
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L0', 'L0']);

      await wrapper.setProps({ streamType: 'L1' });
      await flushPromises();
      expect(funPlay.mock.calls.map((call) => call[2])).toEqual(['L0', 'L0', 'L1']);
      expect(funPlay.mock.calls[1][7].signal.aborted).toBe(true);
      expect(coreMock.instances).toHaveLength(2);
      expect(coreMock.instances[1].wsUrl).toContain('source-3');

      resolveStaleRequest({
        data: {
          wsUrl: '/stream/api/ws?src=stale-source-2',
          streamType: 'L0',
          fallbackUsed: false,
          playbackProfile: 'ORIGINAL',
        },
      });
      await flushPromises();

      expect(coreMock.instances).toHaveLength(2);
      expect(coreMock.instances.some((playerCore) => playerCore.wsUrl.includes('stale-source-2'))).toBe(false);
      expect((wrapper.vm as any).getPlaybackDiagnostics().actual).toEqual(
        expect.objectContaining({ streamType: 'L1' }),
      );
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('records H264 playback capability against the backend actual fallback stream', async () => {
    const funPlay = vi.fn(async (...args: any[]) => ({
      data: {
        wsUrl: `/stream/api/ws?src=camera-1-l0-${args[5]}`,
        streamType: 'L0',
        fallbackUsed: true,
        playbackProfile: args[5],
      },
    }));
    const wrapper = await mountPlayer(funPlay as ReturnType<typeof createFunPlay>, { streamType: 'L1' });

    await coreMock.instances[0].options.onFailure(h265Failure);
    await flushPromises();
    expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL', 'H264']);

    await coreMock.instances[1].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(isH265RuntimeUnusable('camera-1', 'L0')).toBe(true);
    expect(isH265RuntimeUnusable('camera-1', 'L1')).toBe(false);
    wrapper.unmount();
  });

  it('switches from automatic playback to MSE through the unified control bar', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    const firstCore = coreMock.instances[0];
    await firstCore.options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    const protocolButton = wrapper.find('.protocol-control-button');
    expect(protocolButton.text()).toContain('自动 · WebRTC');
    await protocolButton.trigger('click');
    expect(wrapper.find('.protocol-menu').exists()).toBe(true);
    await wrapper.find('.protocol-menu [data-value="mse"]').trigger('click');
    await flushPromises();

    expect(funPlay).toHaveBeenCalledOnce();
    expect(coreMock.instances).toHaveLength(2);
    expect(coreMock.instances[1].options.modes).toEqual(['mse']);
    expect(firstCore.destroyOptions).toContainEqual({ clearVideo: false });
    expect(firstCore.video.classList.contains('is-active')).toBe(true);
    expect(coreMock.instances[1].video.classList.contains('is-active')).toBe(false);

    await coreMock.instances[1].options.onPlaying({
      mode: 'mse',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();
    expect(coreMock.instances[1].video.classList.contains('is-active')).toBe(true);
    expect(wrapper.find('.protocol-control-button').text()).toContain('MSE');

    await wrapper.setProps({ cameraId: 'camera-2' });
    await flushPromises();
    expect(coreMock.instances.at(-1).options.modes).toEqual(['mse']);
    expect(wrapper.find('.protocol-control-button').text()).toContain('MSE');
    wrapper.unmount();
  });

  it('shows readable current video information from the unified information button', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    await coreMock.instances[0].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    expect(wrapper.find('.video-info-panel').exists()).toBe(false);
    await wrapper.find('.video-info-button').trigger('click');
    await flushPromises();

    const panel = wrapper.find('.video-info-panel');
    expect(panel.exists()).toBe(true);
    expect(panel.text()).toContain('camera-1');
    expect(panel.text()).toContain('自动码流 → 子码流');
    expect(panel.text()).toContain('自动选择 → WebRTC');
    expect(panel.text()).toContain('H.264');
    expect(panel.text()).toContain('当前播放正常');
    expect(panel.text()).toContain('复制诊断');

    await panel.find('.video-info-close').trigger('click');
    expect(wrapper.find('.video-info-panel').exists()).toBe(false);
    wrapper.unmount();
  });

  it('reports H265 smooth and power-efficiency hints without treating them as codec incompatibility', async () => {
    const mediaCapabilitiesDescriptor = Object.getOwnPropertyDescriptor(navigator, 'mediaCapabilities');
    Object.defineProperty(navigator, 'mediaCapabilities', {
      configurable: true,
      value: {
        decodingInfo: vi.fn(async () => ({ supported: true, smooth: false, powerEfficient: false })),
      },
    });
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    try {
      const playerCore = coreMock.instances[0];
      Object.defineProperty(playerCore.video, 'videoWidth', { configurable: true, value: 3840 });
      Object.defineProperty(playerCore.video, 'videoHeight', { configurable: true, value: 2160 });
      playerCore.options.onPlaying({
        mode: 'webrtc',
        codec: 'H.265',
        runtime: null,
        hevcLevel: null,
      });
      await flushPromises();

      const diagnostics = (wrapper.vm as any).getPlaybackDiagnostics();
      expect(diagnostics.diagnosis).toEqual(
        expect.objectContaining({
          level: 'attention',
          title: expect.stringContaining('解码开销'),
          evidence: expect.arrayContaining([
            expect.stringContaining('当前实际流：子码流'),
            '视频尺寸：3840 × 2160',
            'H.265 能力预检：平滑=否；节能=否',
          ]),
          suggestions: expect.arrayContaining([expect.stringContaining('建议对比子码流或 H.264')]),
        }),
      );
      expect(funPlay.mock.calls.map((call) => call[5])).toEqual(['ORIGINAL']);
      expect(isH265RuntimeUnusable('camera-1', 'auto')).toBe(false);
    } finally {
      wrapper.unmount();
      if (mediaCapabilitiesDescriptor)
        Object.defineProperty(navigator, 'mediaCapabilities', mediaCapabilitiesDescriptor);
      else Reflect.deleteProperty(navigator, 'mediaCapabilities');
    }
  });

  it('retains the latest five Core attempts after automatic connection replacement', async () => {
    const wrapper = await mountPlayer(createFunPlay());

    for (let index = 0; index < 6; index += 1) {
      const currentCore = coreMock.instances.at(-1);
      currentCore.options.onStage('waiting-frame', `等待第 ${index + 1} 次首帧`);
      currentCore.options.onActiveRecovery({
        message: `第 ${index + 1} 次主画面恢复`,
        failedMode: null,
        diagnostics: currentCore.getDiagnosticsSnapshot(),
      });
    }
    await flushPromises();

    const diagnostics = (wrapper.vm as any).getPlaybackDiagnostics();
    expect(diagnostics.coreAttempts.totalAttempts).toBe(7);
    expect(diagnostics.coreAttempts.recentAttempts).toHaveLength(5);
    expect(diagnostics.coreAttempts.recentAttempts.map((attempt: any) => attempt.attempt)).toEqual([3, 4, 5, 6, 7]);
    expect(diagnostics.coreAttempts.recentAttempts[0]).toEqual(
      expect.objectContaining({
        status: 'replaced',
        finishedAt: expect.any(String),
        stopReason: {
          code: 'active-recovery',
          label: '主画面异常，创建新的播放连接',
        },
      }),
    );
    expect(diagnostics.coreAttempts.recentAttempts.at(-1)).toEqual(
      expect.objectContaining({ status: 'connecting', finishedAt: null }),
    );
    wrapper.unmount();
  });

  it('shows and selects readonly diagnostics when automatic copy fails', async () => {
    const clipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    const execCommandDescriptor = Object.getOwnPropertyDescriptor(document, 'execCommand');
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText: vi.fn().mockRejectedValue(new Error('clipboard denied')) },
    });
    Object.defineProperty(document, 'execCommand', {
      configurable: true,
      value: vi.fn(() => false),
    });

    const wrapper = await mountPlayer(createFunPlay());
    try {
      await wrapper.find('.video-info-button').trigger('click');
      await wrapper.find('.video-info-actions button:last-child').trigger('click');
      await flushPromises();

      const textarea = wrapper.find<HTMLTextAreaElement>('.video-info-diagnostics-text');
      expect(textarea.exists()).toBe(true);
      expect(textarea.element.readOnly).toBe(true);
      expect(textarea.element.value).toContain('"sourceAcquisition"');
      expect(wrapper.find('.video-info-content').text()).toContain('诊断文本已全选');
      expect(document.activeElement).toBe(textarea.element);
      expect(textarea.element.selectionStart).toBe(0);
      expect(textarea.element.selectionEnd).toBe(textarea.element.value.length);

      await wrapper.find('.video-info-actions button:first-child').trigger('click');
      expect(wrapper.find('.video-info-diagnostics-text').exists()).toBe(false);
      expect(wrapper.find('.video-info-list').exists()).toBe(true);
    } finally {
      wrapper.unmount();
      if (clipboardDescriptor) Object.defineProperty(navigator, 'clipboard', clipboardDescriptor);
      else Reflect.deleteProperty(navigator, 'clipboard');
      if (execCommandDescriptor) Object.defineProperty(document, 'execCommand', execCommandDescriptor);
      else Reflect.deleteProperty(document, 'execCommand');
    }
  });

  it('selects the requested stream through the lightweight popup menu', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);

    const streamButton = wrapper.find('.stream-control-button');
    expect(streamButton.text()).toContain('自动 · 子码流');
    await streamButton.trigger('click');
    const streamMenu = wrapper.find('.stream-menu');
    expect(streamMenu.exists()).toBe(true);
    expect(streamMenu.find('[data-value="auto"]').attributes('aria-checked')).toBe('true');

    await streamMenu.find('[data-value="L0"]').trigger('click');
    await flushPromises();
    expect(funPlay.mock.calls.at(-1)?.[2]).toBe('L0');
    expect(wrapper.find('.stream-control-button').text()).toContain('主码流');
    expect(wrapper.find('.stream-menu').exists()).toBe(false);
    wrapper.unmount();
  });

  it('keeps audio enabled for a raw player unless the caller disables it', async () => {
    const defaultWrapper = await mountPlayer(createFunPlay());
    expect(coreMock.instances[0].options.receiveAudio).toBe(true);
    defaultWrapper.unmount();

    coreMock.instances.length = 0;
    const mutedGridWrapper = await mountPlayer(createFunPlay(), { receiveAudio: false });
    expect(coreMock.instances[0].options.receiveAudio).toBe(false);
    mutedGridWrapper.unmount();
  });

  it('suspends without clearing the presented frame and resumes through the standby video slot', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    const firstCore = coreMock.instances[0];
    await firstCore.options.onPlaying({ mode: 'webrtc', codec: 'H.264', runtime: null, hevcLevel: null });
    await flushPromises();
    const videos = wrapper.findAll('video');
    expect(videos[0].classes()).toContain('is-active');

    (wrapper.vm as any).suspendPlay();
    await flushPromises();
    expect(firstCore.destroyOptions.at(-1)).toEqual({ clearVideo: false });
    expect(wrapper.find('.reconnect-tip').exists()).toBe(true);
    expect(videos[0].classes()).toContain('is-active');

    (wrapper.vm as any).resumePlay();
    await flushPromises();
    expect(funPlay).toHaveBeenCalledTimes(2);
    const resumedCore = coreMock.instances[1];
    expect(resumedCore.video).not.toBe(firstCore.video);
    expect(videos[0].classes()).toContain('is-active');

    await resumedCore.options.onPlaying({ mode: 'webrtc', codec: 'H.264', runtime: null, hevcLevel: null });
    await flushPromises();
    expect(videos[1].classes()).toContain('is-active');
    wrapper.unmount();
  });

  it('clears a retained old camera while suspended and connects only the replacement on resume', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    await coreMock.instances[0].options.onPlaying({
      mode: 'webrtc',
      codec: 'H.264',
      runtime: null,
      hevcLevel: null,
    });
    await flushPromises();

    (wrapper.vm as any).suspendPlay();
    await wrapper.setProps({ cameraId: 'camera-2' });
    await flushPromises();
    expect(funPlay).toHaveBeenCalledTimes(1);
    expect(wrapper.find('.status-overlay').exists()).toBe(true);
    expect(wrapper.find('.reconnect-tip').exists()).toBe(false);

    (wrapper.vm as any).resumePlay();
    await flushPromises();
    expect(funPlay).toHaveBeenCalledTimes(2);
    expect(funPlay.mock.calls[1][0]).toBe('camera-2');
    wrapper.unmount();
  });

  it('does not let resume override a manual stop, while an explicit start can leave suspension', async () => {
    const funPlay = createFunPlay();
    const wrapper = await mountPlayer(funPlay);
    (wrapper.vm as any).suspendPlay();
    (wrapper.vm as any).stopPlay();
    (wrapper.vm as any).resumePlay();
    await flushPromises();
    expect(funPlay).toHaveBeenCalledTimes(1);
    expect(wrapper.text()).toContain('暂无信号');

    (wrapper.vm as any).startPlay();
    await flushPromises();
    expect(funPlay).toHaveBeenCalledTimes(2);
    wrapper.unmount();
  });
});
