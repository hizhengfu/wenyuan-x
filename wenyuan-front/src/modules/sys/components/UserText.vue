<template>
  <span class="user-text" :title="ids.join('、') || undefined" :aria-busy="loading || undefined">{{ text }}</span>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';
import { userNameResolver, type UserDisplayName } from '../utils/userDisplayNames';

/** Read-only system user labels. A supplied name preserves a business snapshot. */
const props = withDefaults(
  defineProps<{
    value?: string | number | readonly (string | number)[] | null;
    name?: string | null;
    placeholder?: string;
    separator?: string;
    showLoginName?: boolean;
  }>(),
  { placeholder: '--', separator: '、', showLoginName: false },
);
const auth = useAuthStore();
const ids = computed(() => [
  ...new Set(
    (Array.isArray(props.value) ? props.value : [props.value])
      .flatMap((value) => (value == null ? [] : String(value).split(/[,，;；\r\n]/)))
      .map((value) => value.trim())
      .filter(Boolean),
  ),
]);
const scope = computed(() =>
  JSON.stringify([
    auth.token,
    auth.userInfo?.id,
    auth.userInfo?.groupId,
    [...(auth.permissions || [])].sort(),
  ]),
);
const labels = ref<Record<string, UserDisplayName | null>>({});
const loading = ref(false);
const text = computed(() => {
  if (props.name?.trim() && ids.value.length <= 1) return props.name.trim();
  if (!ids.value.length) return props.placeholder;
  return ids.value
    .map((id) => {
      const user = labels.value[id];
      const name = user?.name?.trim() || user?.loginName?.trim();
      if (!name) return `人员编号 ${id}`;
      return props.showLoginName && user?.name && user.loginName ? `${name}（${user.loginName}）` : name;
    })
    .join(props.separator);
});
let generation = 0;
watch(
  () => [ids.value.join(','), props.name, scope.value],
  async () => {
    const token = ++generation;
    labels.value = {};
    loading.value = false;
    const resolver = userNameResolver(auth, scope.value);
    if (!auth.token || !ids.value.length || (props.name?.trim() && ids.value.length <= 1)) return;
    const requested = [...ids.value];
    loading.value = true;
    const rows = await Promise.all(requested.map((id) => resolver.resolve(id)));
    if (token !== generation) return;
    labels.value = Object.fromEntries(requested.map((id, index) => [id, rows[index]]));
    loading.value = false;
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  ++generation;
});
</script>
<style scoped>
.user-text {
  overflow-wrap: anywhere;
}
</style>
