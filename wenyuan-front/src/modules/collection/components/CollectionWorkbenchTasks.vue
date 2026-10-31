<template>
  <section v-if="target" class="task-panel">
    <div class="task-heading">
      <strong>安全提醒</strong
      ><NSpace
        ><NButton text :loading="busy" @click="load">刷新</NButton
        ><NButton text type="primary" @click="router.push(target)">全部待办</NButton></NSpace
      >
    </div>
    <NAlert v-if="error" type="warning">{{ error }}<NButton text @click="load">重试</NButton></NAlert>
    <div class="task-counts">
      <button
        v-for="item in counters"
        :key="item.status"
        type="button"
        @click="router.push({ path: target.path, query: { ...target.query, status: item.status } })"
      >
        <span>{{ item.label }}</span
        ><strong>{{ busy ? '--' : (counts[item.status] ?? '--') }}</strong>
      </button>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { safetyTaskApi } from '../api/safetyTask';
import { collectionMenuTarget } from '../utils/collectionNavigation';
const auth = useAuthStore(),
  router = useRouter();
const target = computed(() => collectionMenuTarget(auth.menus || [], '/collection/safety-task'));
const counters = [
  { status: 'OVERDUE', label: '逾期' },
  { status: 'ORIGINAL_ERROR', label: '原件待核查' },
  { status: 'PENDING', label: '待办理' },
  { status: 'IN_PROGRESS', label: '办理中' },
];
const counts = ref<Record<string, number>>({}),
  error = ref('');
const session = useCatalogOperation(() => {
    counts.value = {};
    error.value = '';
  }),
  busy = session.busy;
async function load() {
  if (!target.value || !session.active.value) return;
  session.reset();
  const ticket = session.begin('tasks');
  if (ticket === null) return;
  try {
    const responses = await Promise.all(
      counters.map((item) => safetyTaskApi.page({ pageNum: 1, pageSize: 1, status: item.status })),
    );
    if (!session.current(ticket)) return;
    const next: Record<string, number> = {};
    responses.forEach((response: any, index) => {
      if (!Number.isSafeInteger(response?.data?.total) || response.data.total < 0 || !Array.isArray(response.data.list))
        throw new Error('待办数据不完整，请重试');
      next[counters[index].status] = response.data.total;
    });
    counts.value = next;
  } catch (e: any) {
    if (session.current(ticket)) error.value = e?.message || '待办读取失败';
  } finally {
    session.finish(ticket);
  }
}
onMounted(load);
watch(session.active, (active) => {
  if (active) void load();
});
watch(target, () => void load());
watch(
  () => [auth.token, auth.userInfo?.id, auth.identityRevision, auth.identitySyncing, auth.roles, auth.permissions],
  () => {
    session.reset();
    if (auth.token && !auth.identitySyncing) void load();
  },
  { deep: true, flush: 'sync' },
);
</script>
<style scoped>
.task-panel {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.task-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.task-counts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}
.task-counts button {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: 0;
  border-radius: 6px;
  padding: 8px;
  background: rgba(127, 127, 127, 0.06);
  color: inherit;
  cursor: pointer;
  font-size: 12px;
}
</style>
