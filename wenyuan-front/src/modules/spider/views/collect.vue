<template><div>正在打开公众号采集…</div></template>
<script setup lang="ts">
import { onActivated, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useTabStore } from '@/store';
import { collectRoute } from '../utils/collectNavigation';

defineOptions({ name: 'SpiderCollectAlias' });
const router = useRouter();
const route = useRoute();
const tabs = useTabStore();
let redirecting = false;
async function redirect() {
  if (redirecting || route.path !== '/spider/collect') return;
  redirecting = true;
  try {
    const target = collectRoute('automatic');
    await router.replace(target);
    if (route.path === target.path) tabs.removeTab('/spider/collect');
  } finally {
    redirecting = false;
  }
}
onMounted(redirect);
onActivated(redirect);
</script>
