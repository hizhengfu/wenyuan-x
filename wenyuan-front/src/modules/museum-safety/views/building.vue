<template>
  <div v-if="!legacyLightningRoute" class="building-page">
    <NAlert v-if="canSelect" type="info" :bordered="false" class="lightning-entry">
      防雷已按整馆、馆区或建筑的整体系统独立管理，不再作为单个监测位置页签。
      <NButton text type="primary" @click="openLightning">进入防雷系统</NButton>
    </NAlert>
    <div class="building-monitor">
      <DomainMonitor title="建筑结构监测" profile="STRUCTURAL" :tabs="tabs" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAlert, NButton } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import DomainMonitor from '../components/DomainMonitor.vue';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const legacyLightningRoute = computed(() => route.query.tab === 'LIGHTNING');
const canSelect = computed(() => authStore.hasPermission('ADMIN,MUSEUM_SAFETY_SELECT'));

const tabs = [
  {
    key: 'STRUCTURE_HEALTH',
    label: '结构健康',
    domain: 'BUILDING' as const,
    metricCodes: ['STRUCTURAL_STRAIN', 'BUILDING_TILT', 'CRACK_WIDTH'],
  },
  { key: 'VIBRATION', label: '振动', domain: 'BUILDING' as const, metricCodes: ['BUILDING_VIBRATION'] },
  { key: 'SETTLEMENT', label: '沉降', domain: 'BUILDING' as const, metricCodes: ['SETTLEMENT'] },
];

function openLightning() {
  void router.push({
    path: '/museum-safety/lightning',
    query: route.query.orgId ? { orgId: route.query.orgId } : {},
  });
}

watch(
  () => route.query.tab,
  (tab) => {
    if (tab !== 'LIGHTNING') return;
    const { tab: _legacyTab, ...query } = route.query;
    void router.replace({ path: '/museum-safety/lightning', query });
  },
  { immediate: true },
);
</script>

<style scoped>
.building-page {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex-direction: column;
}
.lightning-entry {
  flex: none;
  margin-bottom: 10px;
}
.building-monitor {
  min-height: 0;
  flex: 1;
}
</style>
