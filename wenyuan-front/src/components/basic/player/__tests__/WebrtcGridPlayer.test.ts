// @vitest-environment happy-dom
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WebrtcGridPlayer from '../WebrtcGridPlayer.vue';
import {
  createPlayerFunPlayLimiter,
  defaultPlayerFunPlay,
  MAX_WEBRTC_GRID_SLOTS,
  normalizeWebrtcGridLayout,
  type PlayerFunPlay,
  type StreamStatusReport,
} from '../webrtc/playerTypes';

interface MockPlayerInstance {
  cameraId: () => string;
  funPlay: () => PlayerFunPlay;
  streamType: () => string | number;
  allowConfirmedSubstreamFallback: () => boolean;
  mode: () => string;
  iceServers: () => RTCIceServer[];
  receiveAudio: () => boolean;
  startDelayMs: () => number;
  startPlay: ReturnType<typeof vi.fn>;
  stopPlay: ReturnType<typeof vi.fn>;
  suspendPlay: ReturnType<typeof vi.fn>;
  resumePlay: ReturnType<typeof vi.fn>;
  reconnectNow: ReturnType<typeof vi.fn>;
  setStreamType: ReturnType<typeof vi.fn>;
  switchPlaybackMode: ReturnType<typeof vi.fn>;
  getPlaybackDiagnostics: ReturnType<typeof vi.fn>;
  copyPlaybackDiagnostics: ReturnType<typeof vi.fn>;
  reportStreamStatus: (report: StreamStatusReport) => void;
}

const playerMock = vi.hoisted(() => ({ instances: [] as MockPlayerInstance[] }));
const requestMock = vi.hoisted(() => vi.fn(async (_options: Record<string, unknown>) => ({})));

vi.mock('@/utils/request', () => ({ default: requestMock }));
vi.mock('../WebrtcPlayer.vue', async () => {
  const { defineComponent, h } = await import('vue');
  return {
    default: defineComponent({
      name: 'WebrtcPlayer',
      emits: ['stream-status'],
      props: {
        cameraId: { type: String, default: '' },
        cameraName: { type: String, default: '' },
        funPlay: { type: Function, required: true },
        streamType: { type: [String, Number], default: 'auto' },
        allowConfirmedSubstreamFallback: { type: Boolean, default: false },
        showStreamSwitch: { type: Boolean, default: true },
        mode: { type: String, default: 'webrtc,mse' },
        iceServers: { type: Array, default: () => [] },
        receiveAudio: { type: Boolean, default: true },
        startDelayMs: { type: Number, default: 0 },
      },
      setup(props, { emit, expose }) {
        const instance: MockPlayerInstance = {
          cameraId: () => props.cameraId,
          funPlay: () => props.funPlay as PlayerFunPlay,
          streamType: () => props.streamType,
          allowConfirmedSubstreamFallback: () => props.allowConfirmedSubstreamFallback,
          mode: () => props.mode,
          iceServers: () => props.iceServers as RTCIceServer[],
          receiveAudio: () => props.receiveAudio,
          startDelayMs: () => props.startDelayMs,
          startPlay: vi.fn(),
          stopPlay: vi.fn(),
          suspendPlay: vi.fn(),
          resumePlay: vi.fn(),
          reconnectNow: vi.fn(),
          setStreamType: vi.fn(),
          switchPlaybackMode: vi.fn(() => true),
          getPlaybackDiagnostics: vi.fn(() => ({ cameraId: props.cameraId })),
          copyPlaybackDiagnostics: vi.fn(async () => props.cameraId),
          reportStreamStatus: (report) => emit('stream-status', report),
        };
        playerMock.instances.push(instance);
        expose(instance);
        return () =>
          h('div', {
            class: 'player-stub',
            'data-camera-id': props.cameraId,
            'data-stream-type': props.streamType,
            'data-mode': props.mode,
            'data-ice-count': props.iceServers.length,
            'data-receive-audio': String(props.receiveAudio),
            'data-start-delay': props.startDelayMs,
          });
      },
    }),
  };
});

function exposedGrid(wrapper: ReturnType<typeof mount>) {
  return wrapper.vm as unknown as {
    startPlay(cameraId: string, legacyServerId?: string): void;
    stopPlay(index?: number): void;
    reconnectNow(index?: number): void;
    getPlaybackDiagnostics(index?: number): unknown;
    switchPlaybackMode(value: 'auto' | 'webrtc' | 'mse', index?: number): boolean | undefined;
  };
}

function cameraIds(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('.player-stub').map((node) => {
    const cameraId = node.attributes('data-camera-id');
    if (cameraId === undefined) throw new Error('player stub is missing data-camera-id');
    return cameraId;
  });
}

describe('WebrtcGridPlayer contract', () => {
  beforeEach(() => {
    playerMock.instances.length = 0;
    requestMock.mockClear();
  });

  it('preserves imperative slots while split shrinks and expands', async () => {
    const wrapper = mount(WebrtcGridPlayer, { props: { split: '2x2', videoCodeList: [] } });
    await wrapper.findAll('.grid-cell')[3].trigger('click');
    exposedGrid(wrapper).startPlay('camera-local', 'legacy-server-id');
    await nextTick();

    expect(cameraIds(wrapper)).toEqual(['camera-local']);
    expect(wrapper.emitted('update:videoCodeList')?.at(-1)?.[0]).toEqual([
      { id: '' },
      { id: '' },
      { id: '' },
      { id: 'camera-local' },
    ]);

    await wrapper.setProps({ split: '1x1' });
    expect(cameraIds(wrapper)).toEqual([]);

    await wrapper.setProps({ split: '2x2' });
    expect(cameraIds(wrapper)).toEqual(['camera-local']);

    await wrapper.setProps({ split: '1x1' });
    exposedGrid(wrapper).stopPlay(3);
    await wrapper.setProps({ split: '2x2' });
    expect(cameraIds(wrapper)).toEqual([]);
    wrapper.unmount();
  });

  it('treats an external list replacement as authoritative', async () => {
    const wrapper = mount(WebrtcGridPlayer, { props: { split: '2x2', videoCodeList: [] } });
    exposedGrid(wrapper).startPlay('camera-local');
    await nextTick();
    expect(cameraIds(wrapper)).toEqual(['camera-local']);

    await wrapper.setProps({
      videoCodeList: [{ code: 'camera-external', label: '外部摄像头', serverId: 'server-1' }],
    });
    expect(cameraIds(wrapper)).toEqual(['camera-external']);

    await wrapper.findAll('.grid-cell')[1].trigger('click');
    exposedGrid(wrapper).startPlay('camera-local-2');
    await nextTick();
    expect(wrapper.emitted('update:videoCodeList')?.at(-1)?.[0]).toEqual([
      {
        code: 'camera-external',
        label: '外部摄像头',
        serverId: 'server-1',
        id: 'camera-external',
        name: '外部摄像头',
      },
      { id: 'camera-local-2' },
    ]);

    await wrapper.setProps({ videoCodeList: [] });
    expect(cameraIds(wrapper)).toEqual([]);
    wrapper.unmount();
  });

  it('forwards mode and ICE servers and exposes the selected player controls', async () => {
    const iceServers = [{ urls: 'stun:stun.example.test:3478' }];
    const wrapper = mount(WebrtcGridPlayer, {
      props: { videoCodeList: ['camera-1'], mode: 'mse', iceServers },
    });
    await nextTick();

    const player = wrapper.get('.player-stub');
    expect(player.attributes('data-mode')).toBe('mse');
    expect(player.attributes('data-ice-count')).toBe('1');
    expect(player.attributes('data-receive-audio')).toBe('false');
    expect(playerMock.instances[0].iceServers()).toEqual(iceServers);
    expect(playerMock.instances[0].receiveAudio()).toBe(false);

    exposedGrid(wrapper).reconnectNow();
    expect(playerMock.instances[0].reconnectNow).toHaveBeenCalledOnce();
    expect(exposedGrid(wrapper).getPlaybackDiagnostics()).toEqual({ cameraId: 'camera-1' });
    expect(exposedGrid(wrapper).switchPlaybackMode('mse')).toBe(true);
    expect(playerMock.instances[0].switchPlaybackMode).toHaveBeenCalledWith('mse');
    await wrapper.setProps({ receiveAudio: true });
    expect(playerMock.instances[0].receiveAudio()).toBe(true);
    wrapper.unmount();
  });

  it('uses the main stream for single or focused playback and the substream for a multi-grid', async () => {
    const wrapper = mount(WebrtcGridPlayer, {
      props: { split: '2x2', videoCodeList: ['camera-1', 'camera-2'], restoreSubstreamDelayMs: 0 },
    });
    await nextTick();
    expect(playerMock.instances.map((instance) => instance.streamType())).toEqual(['L1', 'L1']);
    expect(playerMock.instances.every((instance) => instance.allowConfirmedSubstreamFallback())).toBe(true);

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');
    expect(playerMock.instances[1].streamType()).toBe('L1');

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L1');

    await wrapper.setProps({ split: '1x1' });
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');
    wrapper.unmount();
  });

  it('prioritizes the focused layout over an explicit stream choice unless adaptation is disabled', async () => {
    const wrapper = mount(WebrtcGridPlayer, {
      props: { split: '2x2', videoCodeList: ['camera-1'], streamType: 'L0', restoreSubstreamDelayMs: 0 },
    });
    await nextTick();
    expect(playerMock.instances[0].streamType()).toBe('L1');

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L1');

    await wrapper.setProps({ adaptiveStreamType: false });
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');
    expect(playerMock.instances[0].allowConfirmedSubstreamFallback()).toBe(false);
    wrapper.unmount();
  });

  it('delays restoring the substream and cancels the downgrade when focus returns within three seconds', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '2x2', videoCodeList: ['camera-1'] },
      });
      await nextTick();
      expect(playerMock.instances[0].streamType()).toBe('L1');

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await flushPromises();
      expect(playerMock.instances[0].streamType()).toBe('L0');

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await flushPromises();
      expect(wrapper.find('.cell-focused').exists()).toBe(false);
      expect(playerMock.instances[0].streamType()).toBe('L0');

      await vi.advanceTimersByTimeAsync(2_000);
      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(2_000);
      await flushPromises();
      expect(playerMock.instances[0].streamType()).toBe('L0');

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(2_999);
      await flushPromises();
      expect(playerMock.instances[0].streamType()).toBe('L0');

      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();
      expect(playerMock.instances[0].streamType()).toBe('L1');
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps a camera on the main stream after the backend reports that no substream is available', async () => {
    const wrapper = mount(WebrtcGridPlayer, {
      props: { split: '2x2', videoCodeList: ['camera-1'] },
    });
    await nextTick();
    expect(playerMock.instances[0].streamType()).toBe('L1');

    playerMock.instances[0].reportStreamStatus({
      cameraId: 'camera-1',
      requestedStreamType: 'L1',
      actualStreamType: 'L0',
      fallbackUsed: true,
    });
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');
    expect(wrapper.emitted('stream-status')?.at(-1)?.[0]).toEqual(
      expect.objectContaining({ cameraId: 'camera-1', index: 0, actualStreamType: 'L0' }),
    );

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[0].streamType()).toBe('L0');
    wrapper.unmount();
  });

  it('starts grid players in bounded waves instead of negotiating every slot at once', async () => {
    const wrapper = mount(WebrtcGridPlayer, {
      props: {
        split: '3x3',
        videoCodeList: Array.from({ length: 9 }, (_, index) => `camera-${index + 1}`),
        maxConcurrentSourceRequests: 3,
      },
    });
    await nextTick();

    expect(playerMock.instances.map((instance) => instance.startDelayMs())).toEqual([
      0, 0, 0, 300, 300, 300, 600, 600, 600,
    ]);
    wrapper.unmount();
  });

  it('calculates initial waves by populated cameras instead of sparse slot indexes', async () => {
    const sparseList = Array.from({ length: 9 }, (_, index) => (index === 4 || index === 8 ? `camera-${index}` : ''));
    const wrapper = mount(WebrtcGridPlayer, {
      props: {
        split: '3x3',
        videoCodeList: sparseList,
        maxConcurrentSourceRequests: 1,
        startBatchDelayMs: 300,
      },
    });
    await nextTick();

    expect(playerMock.instances.map((instance) => instance.cameraId())).toEqual(['camera-4', 'camera-8']);
    expect(playerMock.instances.map((instance) => instance.startDelayMs())).toEqual([0, 300]);
    wrapper.unmount();
  });

  it('starts a later slot immediately when the camera is replaced imperatively', async () => {
    const provider = vi.fn(async () => ({}));
    const wrapper = mount(WebrtcGridPlayer, {
      props: {
        split: '3x3',
        videoCodeList: Array.from({ length: 9 }, (_, index) => `camera-${index + 1}`),
        funPlay: provider,
        maxConcurrentSourceRequests: 3,
        startBatchDelayMs: 300,
      },
    });
    await nextTick();
    expect(playerMock.instances[8].startDelayMs()).toBe(600);

    await wrapper.findAll('.grid-cell')[8].trigger('click');
    exposedGrid(wrapper).startPlay('camera-interactive');
    await nextTick();
    expect(playerMock.instances[8].cameraId()).toBe('camera-interactive');
    expect(playerMock.instances[8].startDelayMs()).toBe(0);

    await playerMock.instances[8].funPlay()('camera-interactive', false, 'L1', false, undefined, 'ORIGINAL', true, {});
    expect(provider).toHaveBeenCalledWith(
      'camera-interactive',
      false,
      'L1',
      false,
      undefined,
      'ORIGINAL',
      true,
      expect.objectContaining({ priority: 'interactive' }),
    );
    await wrapper.setProps({ startBatchDelayMs: 301 });
    expect(playerMock.instances[8].startDelayMs()).toBe(602);
    wrapper.unmount();
  });

  it('keeps the seven-argument play provider compatible and forwards an optional abort signal', async () => {
    const abortController = new AbortController();
    await defaultPlayerFunPlay('camera-1', false, 'auto', false, undefined, 'ORIGINAL', true, {
      signal: abortController.signal,
    });

    expect(requestMock).toHaveBeenCalledWith(
      expect.objectContaining({
        signal: abortController.signal,
        params: expect.objectContaining({ id: 'camera-1', playbackProfile: 'ORIGINAL', h265Usable: true }),
      }),
    );

    await defaultPlayerFunPlay('camera-2', false, 'auto', false, undefined, 'ORIGINAL', false);
    expect(requestMock.mock.calls.at(-1)?.[0]).not.toHaveProperty('signal');
  });

  it('bounds concurrent source requests and cancels a request while it is still queued', async () => {
    const resolvers: Array<(value: unknown) => void> = [];
    const provider = vi.fn(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const limited = createPlayerFunPlayLimiter(provider, () => 2);
    const first = limited('camera-1');
    const second = limited('camera-2');
    const third = limited('camera-3');
    const queuedAbort = new AbortController();
    const abortedQueueTiming = vi.fn();
    const fourth = limited('camera-4', false, 'auto', false, undefined, 'ORIGINAL', undefined, {
      signal: queuedAbort.signal,
      onQueueTiming: abortedQueueTiming,
    });

    expect(provider).toHaveBeenCalledTimes(2);
    const fourthResult = expect(fourth).rejects.toMatchObject({ name: 'AbortError' });
    queuedAbort.abort();
    await fourthResult;
    expect(provider).toHaveBeenCalledTimes(2);
    expect(abortedQueueTiming).not.toHaveBeenCalled();

    resolvers[0]({});
    await first;
    await flushPromises();
    expect(provider).toHaveBeenCalledTimes(3);
    resolvers[1]({});
    resolvers[2]({});
    await Promise.all([second, third]);
  });

  it('reports queue wait only when a limited request actually enters the provider', async () => {
    let now = 1_000;
    const dateNow = vi.spyOn(Date, 'now').mockImplementation(() => now);
    const resolvers: Array<(value: unknown) => void> = [];
    const provider = vi.fn(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const limited = createPlayerFunPlayLimiter(provider, () => 1);
    const firstQueueTiming = vi.fn();
    const secondQueueTiming = vi.fn();
    try {
      const first = limited('camera-1', false, 'auto', false, undefined, 'ORIGINAL', undefined, {
        onQueueTiming: firstQueueTiming,
      });
      now = 1_100;
      const second = limited('camera-2', false, 'auto', false, undefined, 'ORIGINAL', undefined, {
        onQueueTiming: secondQueueTiming,
      });

      expect(firstQueueTiming).toHaveBeenCalledWith(0);
      expect(secondQueueTiming).not.toHaveBeenCalled();
      now = 1_450;
      resolvers[0]({});
      await first;
      await flushPromises();
      expect(secondQueueTiming).toHaveBeenCalledWith(350);

      resolvers[1]({});
      await second;
    } finally {
      dateNow.mockRestore();
    }
  });

  it('runs interactive requests before normal waiters without preempting active work', async () => {
    const order: string[] = [];
    const resolvers: Array<() => void> = [];
    const provider = vi.fn(
      (cameraId: string) =>
        new Promise((resolve) => {
          order.push(cameraId);
          resolvers.push(() => resolve({}));
        }),
    );
    const limited = createPlayerFunPlayLimiter(provider, () => 1);
    const normalContext = { priority: 'normal' as const };
    const interactiveContext = { priority: 'interactive' as const };
    const requests = [
      limited('active-a', false, 'auto', false, undefined, 'ORIGINAL', undefined, normalContext),
      limited('normal-b1', false, 'auto', false, undefined, 'ORIGINAL', undefined, normalContext),
      limited('normal-b2', false, 'auto', false, undefined, 'ORIGINAL', undefined, normalContext),
      limited('priority-p1', false, 'auto', false, undefined, 'ORIGINAL', undefined, interactiveContext),
      limited('priority-p2', false, 'auto', false, undefined, 'ORIGINAL', undefined, interactiveContext),
    ];

    expect(order).toEqual(['active-a']);
    for (const expected of ['priority-p1', 'priority-p2', 'normal-b1', 'normal-b2']) {
      resolvers.at(-1)?.();
      await flushPromises();
      expect(order.at(-1)).toBe(expected);
    }
    resolvers.at(-1)?.();
    await Promise.all(requests);
    expect(order).toEqual(['active-a', 'priority-p1', 'priority-p2', 'normal-b1', 'normal-b2']);
  });

  it('cancels a stale focused request and prioritizes the latest focus during a rapid switch', async () => {
    const order: string[] = [];
    const resolvers: Array<() => void> = [];
    const provider = vi.fn(
      (cameraId: string) =>
        new Promise((resolve) => {
          order.push(cameraId);
          resolvers.push(() => resolve({}));
        }),
    );
    const wrapper = mount(WebrtcGridPlayer, {
      props: {
        split: '2x2',
        videoCodeList: ['camera-1', 'camera-2', 'camera-3'],
        funPlay: provider,
        maxConcurrentSourceRequests: 1,
        restoreSubstreamDelayMs: 0,
      },
    });
    await nextTick();

    const running = playerMock.instances[2].funPlay()('background-active');
    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    const staleController = new AbortController();
    const stale = playerMock.instances[0].funPlay()(
      'camera-1-main',
      false,
      'L0',
      false,
      undefined,
      'ORIGINAL',
      undefined,
      { signal: staleController.signal },
    );
    const staleResult = expect(stale).rejects.toMatchObject({ name: 'AbortError' });

    await wrapper.findAll('.grid-cell')[1].trigger('dblclick');
    staleController.abort();
    await staleResult;
    const oldSlotDowngrade = playerMock.instances[0].funPlay()('camera-1-sub');
    const latestFocus = playerMock.instances[1].funPlay()('camera-2-main');

    expect(order).toEqual(['background-active']);
    resolvers[0]();
    await running;
    await flushPromises();
    expect(order).toEqual(['background-active', 'camera-2-main']);

    resolvers[1]();
    await latestFocus;
    await flushPromises();
    expect(order).toEqual(['background-active', 'camera-2-main', 'camera-1-sub']);
    resolvers[2]();
    await oldSlotDowngrade;
    wrapper.unmount();
  });

  it('drains queued source requests when the runtime concurrency limit increases', async () => {
    let limit = 1;
    const resolvers: Array<(value: unknown) => void> = [];
    const provider = vi.fn(
      () =>
        new Promise((resolve) => {
          resolvers.push(resolve);
        }),
    );
    const limited = createPlayerFunPlayLimiter(provider, () => limit);
    const requests = [limited('camera-1'), limited('camera-2'), limited('camera-3')];
    expect(provider).toHaveBeenCalledTimes(1);

    limit = 3;
    limited.refreshLimit();
    expect(provider).toHaveBeenCalledTimes(3);
    resolvers.forEach((resolve) => resolve({}));
    await Promise.all(requests);
  });

  it('supports a real 1+5 layout and bounds malformed or oversized splits', async () => {
    expect(normalizeWebrtcGridLayout('1+5')).toEqual({
      value: '1+5',
      cols: 3,
      rows: 3,
      total: 6,
      onePlusFive: true,
    });
    expect(normalizeWebrtcGridLayout(`${MAX_WEBRTC_GRID_SLOTS + 1}x1`).value).toBe('2x2');
    expect(normalizeWebrtcGridLayout('not-a-grid').value).toBe('2x2');

    const wrapper = mount(WebrtcGridPlayer, { props: { split: '1+5' } });
    expect(wrapper.findAll('.grid-cell')).toHaveLength(6);
    expect(wrapper.get('.grid-wrap').classes()).toContain('grid-wrap--one-plus-five');
    expect(wrapper.get('.grid-wrap').attributes('data-split')).toBe('1+5');

    await wrapper.setProps({ split: '100x100' });
    expect(wrapper.findAll('.grid-cell')).toHaveLength(4);
    expect(wrapper.get('.grid-wrap').attributes('data-split')).toBe('2x2');
    wrapper.unmount();
  });

  it('keeps hidden streams running by default and pauses them only when opted in', async () => {
    const wrapper = mount(WebrtcGridPlayer, {
      props: { videoCodeList: ['camera-1', 'camera-2'], pauseHiddenOnFocus: false },
    });
    await flushPromises();
    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[1].stopPlay).not.toHaveBeenCalled();
    expect(playerMock.instances[1].suspendPlay).not.toHaveBeenCalled();

    await wrapper.setProps({ pauseHiddenOnFocus: true });
    await flushPromises();
    expect(playerMock.instances[1].stopPlay).toHaveBeenCalledOnce();

    await wrapper.setProps({ videoCodeList: ['camera-1', 'camera-3'] });
    await flushPromises();
    expect(playerMock.instances[1].cameraId()).toBe('camera-3');
    expect(playerMock.instances[1].stopPlay).toHaveBeenCalledTimes(2);

    await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
    await flushPromises();
    expect(playerMock.instances[1].startPlay).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('does not suspend a 3x3 grid when focus exits before the three-second dwell', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '3x3', videoCodeList: ['camera-1', 'camera-2', 'camera-3'] },
      });
      await flushPromises();

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(2_999);
      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();

      expect(playerMock.instances.slice(1).every((instance) => instance.suspendPlay.mock.calls.length === 0)).toBe(
        true,
      );
      expect(playerMock.instances.slice(1).every((instance) => instance.resumePlay.mock.calls.length === 0)).toBe(true);
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('suspends hidden 3x3 streams after a stable focus and resumes their retained frames on exit', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '3x3', videoCodeList: ['camera-1', 'camera-2', 'camera-3'] },
      });
      await flushPromises();

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(playerMock.instances[0].suspendPlay).not.toHaveBeenCalled();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledOnce();
      expect(playerMock.instances[2].suspendPlay).toHaveBeenCalledOnce();
      expect(playerMock.instances[1].stopPlay).not.toHaveBeenCalled();

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await flushPromises();
      expect(playerMock.instances[1].resumePlay).toHaveBeenCalledOnce();
      expect(playerMock.instances[2].resumePlay).toHaveBeenCalledOnce();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('debounces the old focus again when switching and returning after hidden streams were suspended', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '3x3', videoCodeList: ['camera-1', 'camera-2', 'camera-3'] },
      });
      await flushPromises();

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledOnce();

      await wrapper.findAll('.grid-cell')[1].trigger('dblclick');
      await flushPromises();
      expect(playerMock.instances[1].resumePlay).toHaveBeenCalledOnce();
      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(2_999);
      await flushPromises();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledOnce();

      await vi.advanceTimersByTimeAsync(1);
      await flushPromises();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledTimes(2);
      expect(playerMock.instances[0].suspendPlay).not.toHaveBeenCalled();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps a hidden replacement camera suspended and resumes only its current id', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '3x3', videoCodeList: ['camera-1', 'camera-2'] },
      });
      await flushPromises();
      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledOnce();

      await wrapper.setProps({ videoCodeList: ['camera-1', 'camera-new'] });
      await flushPromises();
      expect(playerMock.instances[1].cameraId()).toBe('camera-new');
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledTimes(2);

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await flushPromises();
      expect(playerMock.instances[1].resumePlay).toHaveBeenCalledOnce();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });

  it('keeps explicit destructive pause authoritative after energy suspension', async () => {
    vi.useFakeTimers();
    try {
      const wrapper = mount(WebrtcGridPlayer, {
        props: { split: '3x3', videoCodeList: ['camera-1', 'camera-2'] },
      });
      await flushPromises();
      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await vi.advanceTimersByTimeAsync(3_000);
      await flushPromises();
      expect(playerMock.instances[1].suspendPlay).toHaveBeenCalledOnce();

      await wrapper.setProps({ pauseHiddenOnFocus: true });
      await flushPromises();
      expect(playerMock.instances[1].stopPlay).toHaveBeenCalledOnce();

      await wrapper.findAll('.grid-cell')[0].trigger('dblclick');
      await flushPromises();
      expect(playerMock.instances[1].startPlay).toHaveBeenCalledOnce();
      wrapper.unmount();
    } finally {
      vi.useRealTimers();
    }
  });
});
