<template>
  <section v-if="target" class="storage-overview">
    <div class="storage-heading">
      <strong>库区概况</strong><NButton text type="primary" @click="router.push(target)">位置管理与日常巡库</NButton>
    </div>
    <NAlert v-if="error" type="warning">{{ error }}<NButton text @click="load">重试</NButton></NAlert>
    <NSpin :show="busy"
      ><div class="storage-grid">
        <button
          v-for="area in areas"
          :key="area.id"
          type="button"
          class="storage-card"
          @click="router.push({ path: target.path, query: { ...target.query, warehouseId: area.id } })"
        >
          <strong>{{ area.name }}</strong
          ><span>在库登记记录 {{ area.count }} 条</span><span>实物数量 {{ area.quantity }}</span>
          <small v-if="area.unknown">{{ area.unknown }} 条数量待核实</small>
          <small v-if="area.capacity != null">容量 {{ area.capacity }}</small>
        </button>
      </div>
      <NEmpty v-if="!busy && !error && !areas.length" size="small" description="暂无可见库区"
    /></NSpin>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store';
import { warehouseApi } from '../api/warehouse';
import { collectionMenuTarget } from '../utils/collectionNavigation';
import { quantityText } from '../utils/collectionAnalysis';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import type { CollectionUsageSummary, WarehouseVo } from '../typings/collection';
const props = defineProps<{ usage: CollectionUsageSummary | null }>();
const auth = useAuthStore(),
  router = useRouter(),
  locations = ref<WarehouseVo[]>([]),
  error = ref('');
const target = computed(() => collectionMenuTarget(auth.menus || [], '/collection/warehouse'));
const session = useCatalogOperation(() => {
    locations.value = [];
    error.value = '';
  }),
  busy = session.busy;
const areas = computed(() =>
  locations.value
    .filter((row) => row.sceneType === 'STORAGE' && row.type === 'WAREHOUSE')
    .map((row) => {
      const ids = new Set<string>();
      const visit = (id: string) => {
        if (ids.has(id)) return;
        ids.add(id);
        locations.value.filter((v) => v.parentId === id && v.orgId === row.orgId).forEach((v) => v.id && visit(v.id));
      };
      if (row.id) visit(row.id);
      const items =
        props.usage?.items.filter(
          (item) => item.orgId === row.orgId && item.inStore && !!item.warehouseId && ids.has(item.warehouseId),
        ) || [];
      const quantities: Record<string, number> = {};
      for (const item of items)
        if (item.unit?.trim() && Number(item.quantity) > 0)
          quantities[item.unit.trim()] = (quantities[item.unit.trim()] || 0) + Number(item.quantity);
      return {
        ...row,
        unknown: items.filter((r) => !r.unit?.trim() || !Number.isSafeInteger(r.quantity) || Number(r.quantity) <= 0)
          .length,
        count: props.usage ? items.length : '--',
        quantity: props.usage ? quantityText(quantities, items.length ? '待核实' : '0') : '--',
      };
    }),
);
async function load() {
  if (!target.value || !session.active.value) return;
  session.reset();
  const ticket = session.begin('locations');
  if (ticket === null) return;
  try {
    const response: any = await warehouseApi.list();
    if (!session.current(ticket)) return;
    if (!Array.isArray(response?.data)) throw new Error('库区资料不完整');
    locations.value = response.data;
  } catch (e: any) {
    if (session.current(ticket)) error.value = e?.message || '库区资料读取失败';
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
.storage-overview {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.storage-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.storage-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}
.storage-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: left;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.storage-card span,
.storage-card small {
  font-size: 12px;
}
@media (max-width: 900px) {
  .storage-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .storage-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
