<template>
  <span>{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { get as getUser } from '@/modules/sys/api/user';

const userNameCache = new Map<string, string>();

const props = defineProps<{
  id?: string | null;
  name?: string | null;
}>();

const resolvedName = ref('');
const fallbackText = ref('');

const displayText = computed(() => {
  const name = String(props.name || '').trim();
  if (name) return name;
  return resolvedName.value || fallbackText.value || '--';
});

watch(
  () => [props.id, props.name] as const,
  async ([id, name]) => {
    resolvedName.value = '';
    fallbackText.value = '';
    if (String(name || '').trim()) return;

    const userId = String(id || '').trim();
    if (!userId) return;
    if (!shouldResolveUserId(userId)) {
      fallbackText.value = userId;
      return;
    }
    const cached = userNameCache.get(userId);
    if (cached) {
      resolvedName.value = cached;
      return;
    }

    try {
      const resp: any = await getUser(userId);
      const user = resp?.data ?? {};
      const userName = formatUserName(user) || userId;
      userNameCache.set(userId, userName);
      resolvedName.value = userName;
    } catch {
      userNameCache.set(userId, userId);
      fallbackText.value = userId;
    }
  },
  { immediate: true },
);

function formatUserName(user: any) {
  const name = String(user?.name || '').trim();
  const loginName = String(user?.loginName || user?.username || '').trim();
  if (name && loginName) return `${name}(${loginName})`;
  return name || loginName;
}

function shouldResolveUserId(value: string) {
  return /^\d{8,}$/.test(value) || /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
</script>
