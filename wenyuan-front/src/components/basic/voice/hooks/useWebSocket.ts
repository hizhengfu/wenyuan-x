import type { Ref } from 'vue-demi';
import { ref, onMounted, onUnmounted } from 'vue-demi';
import Websocket from '@/utils/websocket';

// websocket连接
export function useWebSocket(options: any) {
  const ws = new Websocket(options);
  const result: Ref<any> = ref('');

  onMounted(() => {
    // 建立ws连接
    ws.connection((res: any) => {
      result.value = res.data || {};
    });
  });

  onUnmounted(() => {
    ws.disconnect(); // 断开wsl链接
  });

  return {
    result,
  };
}
