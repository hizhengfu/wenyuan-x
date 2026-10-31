import { onBeforeUnmount, onMounted, ref, toValue, watch, type MaybeRefOrGetter } from 'vue';
import { rfidApi } from '../api/rfid';
import type { RfidWorkstationRuntime } from '../typings/rfid';

export function useRfidWorkstationRuntime(
  workstationId: MaybeRefOrGetter<string>,
  previewOnly: MaybeRefOrGetter<boolean>,
) {
  const snapshot = ref<RfidWorkstationRuntime | null>(null);
  const loading = ref(false);
  const failed = ref(false);
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;

  function invalidate() {
    generation += 1;
    clearTimeout(timer);
    timer = undefined;
    loading.value = false;
    snapshot.value = null;
  }

  async function refresh() {
    const stationId = toValue(workstationId);
    if (toValue(previewOnly) || !stationId || document.hidden) return;
    clearTimeout(timer);
    const current = ++generation;
    loading.value = true;
    failed.value = false;
    try {
      const response: any = await rfidApi.workstationRuntime();
      if (current !== generation) return;
      if (response?.data?.workstationId !== stationId) throw new Error('工作站状态不匹配');
      snapshot.value = response.data;
      timer = setTimeout(() => void refresh(), 15000);
    } catch {
      if (current !== generation) return;
      snapshot.value = null;
      failed.value = true;
      // 查询失败后显示未知并等待用户重试，避免后台持续弹出通用接口错误。
    } finally {
      if (current === generation) loading.value = false;
    }
  }

  function restart() {
    invalidate();
    failed.value = false;
    void refresh();
  }
  watch([() => toValue(workstationId), () => toValue(previewOnly)], restart);
  function visibilityChanged() {
    if (document.hidden) invalidate();
    else restart();
  }
  onMounted(() => {
    document.addEventListener('visibilitychange', visibilityChanged);
    void refresh();
  });
  onBeforeUnmount(() => {
    invalidate();
    document.removeEventListener('visibilitychange', visibilityChanged);
  });
  return { snapshot, loading, failed, refresh };
}
