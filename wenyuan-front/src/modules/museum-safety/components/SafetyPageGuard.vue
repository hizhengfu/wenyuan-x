<template>
  <div v-if="allowed" class="safety-page-guard">
    <slot />
  </div>
  <div v-else class="safety-forbidden">
    <NResult status="403" title="无权访问场馆安全" description="请联系管理员授予场馆安全查看权限。" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NResult } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';

const authStore = useAuthStore();
const allowed = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));
</script>

<style scoped>
.safety-page-guard {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}
.safety-forbidden {
  display: grid;
  width: 100%;
  height: 100%;
  min-height: 420px;
  place-items: center;
  background: var(--card-color);
}
</style>
