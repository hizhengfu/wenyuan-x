<template>
  <span>{{ name || (loading ? '正在读取馆名…' : '馆名暂不可用') }}</span>
</template>

<script setup lang="ts">
import { onActivated, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { orgApi } from '../api/org';

const props = defineProps<{ orgId?: string | null }>();
const auth = useAuthStore();
const name = ref('');
const loading = ref(false);
let active = true;
let request = 0;

async function loadName() {
  const version = ++request;
  name.value = '';
  loading.value = false;
  const id = props.orgId?.trim();
  if (!id || !active || auth.identitySyncing || !auth.userInfo?.id) return;
  loading.value = true;
  try {
    const response: any = await orgApi.get(id);
    if (version !== request || !active) return;
    if (response?.data?.id === id && typeof response.data.name === 'string') {
      name.value = response.data.name.trim();
    }
  } catch {
    // 馆名读取失败不阻断档案查阅，也不把内部编号当作馆名。
  } finally {
    if (version === request) loading.value = false;
  }
}

watch(
  () => [props.orgId, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing],
  () => void loadName(),
  { immediate: true, flush: 'sync' },
);
onActivated(() => {
  if (active) return;
  active = true;
  void loadName();
});
function stop() {
  active = false;
  request++;
  name.value = '';
  loading.value = false;
}
onDeactivated(stop);
onBeforeUnmount(stop);
</script>
