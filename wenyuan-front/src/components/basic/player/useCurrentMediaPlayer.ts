import { nextTick, ref, watch } from 'vue';
import type Player from 'xgplayer';
import { useCurrentImageLifecycle } from '@/utils/currentImage';

/** 媒体流使用播放器原生传输；这里管理实例生命周期，不把整个媒体下载为 Blob。 */
export function useCurrentMediaPlayer(options: {
  source: () => string;
  configuration?: () => unknown;
  create: (url: string, state: { autoplay?: boolean; resumeTime: number }) => Player;
  ready?: (player: Player) => (() => void) | undefined;
}) {
  const source = ref(options.source());
  const error = ref('');
  let player: Player | undefined;
  let cleanupReady: (() => void) | undefined;
  let readyHandler: (() => void) | undefined;
  let errorHandler: (() => void) | undefined;
  let generation = 0;
  let autoplay: boolean | undefined;
  let pauseHandler: (() => void) | undefined;
  let playHandler: (() => void) | undefined;
  let lastPosition = { url: '', time: 0 };
  let instanceUrl = '';
  function destroy() {
    generation++;
    const old = player;
    player = undefined;
    if (old) {
      const time = Number(old.currentTime);
      lastPosition = { url: instanceUrl, time: Number.isFinite(time) && time > 0 ? time : 0 };
      if (readyHandler) old.off('ready', readyHandler);
      if (errorHandler) old.off('error', errorHandler);
      if (pauseHandler) old.off('pause', pauseHandler);
      if (playHandler) old.off('play', playHandler);
    }
    readyHandler = undefined;
    errorHandler = undefined;
    pauseHandler = undefined;
    playHandler = undefined;
    const cleanup = cleanupReady;
    cleanupReady = undefined;
    // 先关闭频谱和它的播放器监听，再销毁播放器，避免重复关闭 AudioContext。
    try {
      cleanup?.();
    } finally {
      old?.destroy();
    }
  }
  function invalidate() {
    destroy();
    error.value = '';
  }
  function reload() {
    const ticket = generation,
      url = source.value;
    if (!url) return;
    void nextTick().then(() => {
      if (ticket !== generation || !eligible.value) return;
      try {
        const current = options.create(url, { autoplay, resumeTime: lastPosition.url === url ? lastPosition.time : 0 });
        if (ticket !== generation || !eligible.value) {
          current.destroy();
          return;
        }
        player = current;
        instanceUrl = url;
        let prepared = false;
        readyHandler = () => {
          if (ticket !== generation || current !== player || prepared) return;
          prepared = true;
          try {
            cleanupReady = options.ready?.(current);
          } catch {
            error.value = '音频频谱不可用，仍可使用播放控件。';
          }
        };
        errorHandler = () => {
          if (ticket !== generation || current !== player) return;
          destroy();
          error.value = '媒体读取失败，请核对当前授权、文件状态和浏览器支持。';
        };
        pauseHandler = () => {
          if (ticket === generation && current === player) autoplay = false;
        };
        playHandler = () => {
          if (ticket === generation && current === player) autoplay = true;
        };
        current.on('pause', pauseHandler);
        current.on('play', playHandler);
        current.on('ready', readyHandler);
        current.on('error', errorHandler);
      } catch {
        destroy();
        error.value = '播放器初始化失败，请核对文件和播放配置。';
      }
    });
  }
  watch(
    options.source,
    (value) => {
      autoplay = undefined;
      source.value = value;
    },
    { flush: 'sync' },
  );
  const { eligible, refresh } = useCurrentImageLifecycle(
    () => [source.value, options.configuration?.()],
    invalidate,
    reload,
  );
  function startPlay(url?: string) {
    if (!eligible.value) return;
    autoplay = true;
    if (url && url !== source.value) {
      source.value = url;
      return;
    }
    if (!player) {
      refresh();
      return;
    }
    const current = player;
    const ticket = generation;
    const failed = () => {
      if (ticket === generation && current === player) error.value = '浏览器未能开始播放，请使用播放控件重试。';
    };
    try {
      Promise.resolve(current.play()).catch(failed);
    } catch {
      failed();
    }
  }
  function stopPlay() {
    autoplay = false;
    player?.pause();
  }
  return { error, refresh, startPlay, stopPlay };
}
