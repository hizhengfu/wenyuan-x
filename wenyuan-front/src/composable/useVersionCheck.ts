// src/hooks/useVersionCheck.ts
import { h, onBeforeUnmount, onMounted, ref } from 'vue';
import { NButton, useNotification } from 'naive-ui';
import { useStorage, useDebounceFn } from '@vueuse/core';

interface VersionInfo {
  version: string;
  timestamp: number;
}

export function useVersionCheck(options?: { debounceMs?: number; minInterval?: number }) {
  if (import.meta.env.DEV || !ENABLE_UPDATE_VERSION) {
    return;
  }

  const { debounceMs = 1000, minInterval = 30000 } = options || {};

  const notification = useNotification();
  const localTimestamp = useStorage<number | null>('__VERSION__' + APP_TARGET.toUpperCase(), null);
  const isNotificationShown = ref(false);
  const lastCheckTime = ref(0);

  const checkVersion = async () => {
    // 最小间隔控制，避免短时间内重复请求
    const now = Date.now();
    if (now - lastCheckTime.value < minInterval) {
      return;
    }
    lastCheckTime.value = now;

    try {
      const res = await fetch(`./version.json?t=${now}`);
      if (!res.ok) return;

      const data: VersionInfo = await res.json();
      const remoteTimestamp = data.timestamp;

      if (localTimestamp.value === null) {
        localTimestamp.value = remoteTimestamp;
        return;
      }

      if (remoteTimestamp > localTimestamp.value && !isNotificationShown.value) {
        showUpdateNotification(data.version, remoteTimestamp);
      }
    } catch (error) {
      console.error('Version check failed:', error);
    }
  };

  // 使用 vueuse 的防抖函数
  const debouncedCheckVersion = useDebounceFn(checkVersion, debounceMs);

  const showUpdateNotification = (newVersion: string, remoteTimestamp: number) => {
    isNotificationShown.value = true;

    notification.info({
      title: '发现新版本',
      content: '检测到系统有更新,为了您的使用体验,请刷新页面。',
      duration: 0,
      closable: false,
      keepAliveOnHover: true,
      action: () =>
        h(
          NButton,
          {
            type: 'primary',
            size: 'small',
            onClick: () => {
              localTimestamp.value = remoteTimestamp;
              window.location.reload();
            },
          },
          { default: () => '立即刷新' },
        ),
      onClose: () => {
        localTimestamp.value = remoteTimestamp;
        isNotificationShown.value = false;
      },
    });
  };

  const handleVisibilityChange = () => {
    if (document.visibilityState === 'visible') {
      debouncedCheckVersion();
    }
  };

  onMounted(() => {
    checkVersion(); // 首次挂载立即检查
    document.addEventListener('visibilitychange', handleVisibilityChange);
  });

  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', handleVisibilityChange);
  });
}
