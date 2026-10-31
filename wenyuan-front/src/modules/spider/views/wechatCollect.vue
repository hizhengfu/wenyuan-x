<template>
  <div class="wechat-workbench">
    <CollectMethodNav :method="method" :methods="methods" @select="selectMethod" />
    <n-spin v-if="loading" class="collection-loading" />
    <n-result v-else-if="loadError" status="error" title="采集功能加载失败" description="请检查服务连接后重试。">
      <template #footer><n-button @click="loadCapabilities">重试</n-button></template>
    </n-result>
    <n-empty v-else-if="!methods.length" description="当前账号没有可用的采集权限" />
    <keep-alive v-else>
      <WechatAutomaticCollect v-if="method === 'automatic'" />
      <WechatLinkCollect
        v-else
        :automatic-available="methods.includes('automatic')"
        @automatic="selectMethod('automatic')"
      />
    </keep-alive>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import CollectMethodNav from '../components/CollectMethodNav.vue';
import WechatLinkCollect from '../components/WechatLinkCollect.vue';
import WechatAutomaticCollect from '../components/WechatAutomaticCollect.vue';
import { capabilities } from '../api/wechat';
import { collectRoute, hasLegacyAutomaticMenu, type CollectMethod } from '../utils/collectNavigation';

defineOptions({ name: 'SpiderWechatCollect' });
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const loading = ref(true);
const loadError = ref(false);
const automaticAvailable = ref(false);
const method = ref<CollectMethod>('automatic');
let disposed = false;
const methods = computed<CollectMethod[]>(() => [
  ...(auth.hasPermission(['SPIDER_RULE_ALL', 'SPIDER_RULE_SELECT']) ? ['links' as const] : []),
  ...(automaticAvailable.value && auth.hasPermission(['CMS_COLLECT_ALL']) ? ['automatic' as const] : []),
]);
async function loadCapabilities() {
  loading.value = true;
  loadError.value = false;
  try {
    automaticAvailable.value = (await capabilities()).data.automatic;
  } catch (error: any) {
    if (error?.response?.status === 404) automaticAvailable.value = hasLegacyAutomaticMenu(auth.menus ?? []);
    else loadError.value = true;
  } finally {
    if (!disposed) loading.value = false;
  }
}
function selectMethod(next: CollectMethod) {
  if (!methods.value.includes(next)) return;
  method.value = next;
  const target = collectRoute(next);
  if (
    route.path !== target.path ||
    route.query.method !== next ||
    route.query.menuId !== '50003' ||
    route.query.title !== '公众号采集'
  ) {
    void router.replace(target);
  }
}
watch([() => route.path, () => route.query.method, methods, loading, loadError], () => {
  if (route.path !== '/spider/wechatCollect' || disposed || loading.value || loadError.value || !methods.value.length)
    return;
  const requested = route.query.method;
  const next = requested === 'links' || requested === 'automatic' ? requested : method.value;
  selectMethod(methods.value.includes(next) ? next : methods.value[0]);
});
onMounted(loadCapabilities);
onBeforeUnmount(() => {
  disposed = true;
});
</script>
<style scoped>
.wechat-workbench {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px;
  box-sizing: border-box;
}
.wechat-workbench > :first-child {
  flex-shrink: 0;
}
.wechat-workbench > :last-child {
  flex: 1;
  height: auto;
  min-height: 0;
}
.collection-loading {
  margin-top: 40px;
}
</style>
