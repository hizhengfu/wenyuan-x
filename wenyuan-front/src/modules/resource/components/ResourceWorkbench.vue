<template>
  <ResourcePage show-title title="数字资源">
    <template #actions>
      <n-button :loading="loading" @click="refresh"
        ><template #icon
          ><n-icon><IconMdiRefresh /></n-icon></template
        >刷新</n-button
      >
      <n-button v-if="target('/resource/browse')" type="primary" @click="open('/resource/browse')"
        ><template #icon
          ><n-icon><IconMdiMagnify /></n-icon></template
        >检索资源</n-button
      >
    </template>
    <n-alert v-if="error" type="error" class="mb-4">{{ error }}</n-alert>
    <div class="resource-overview" :aria-busy="loading">
      <div class="resource-metrics">
        <button
          v-for="(card, index) in metrics"
          :key="card.key"
          class="resource-metric"
          :class="{ 'resource-metric--primary': index === 0 }"
          :disabled="!target(card.path)"
          :aria-label="`${card.label} ${value(card.key)}`"
          @click="open(card.path)"
        >
          <span class="resource-metric__label"
            >{{ card.label }}<n-icon aria-hidden="true"><IconMdiArrowTopRight /></n-icon
          ></span>
          <strong>{{ value(card.key) }}</strong>
        </button>
      </div>
      <aside class="resource-queue resource-panel">
        <h2 class="resource-section-title">待办</h2>
        <button v-for="card in tasks" :key="card.key" :disabled="!target(card.path)" @click="open(card.path)">
          <span>{{ card.label }}</span
          ><strong>{{ value(card.key) }}</strong
          ><n-icon aria-hidden="true"><IconMdiArrowRight /></n-icon>
        </button>
      </aside>
    </div>
    <section class="resource-coverage resource-panel" aria-labelledby="resource-coverage-title">
      <div>
        <h2 id="resource-coverage-title" class="resource-section-title">馆藏数字化</h2>
        <div v-if="data?.coverage?.available" class="resource-coverage__count">
          <strong>{{ data.coverage.digitized.toLocaleString() }}</strong
          ><span>/ {{ data.coverage.eligible.toLocaleString() }} 件</span>
        </div>
        <span v-else class="resource-quiet-help">{{ loading ? '读取中…' : '暂无可用藏品统计' }}</span>
      </div>
      <div class="resource-coverage__progress" v-if="percentage !== null">
        <div>
          <span>完成进度</span><strong>{{ percentage }}<small>%</small></strong>
        </div>
        <n-progress
          type="line"
          :percentage="percentage"
          :show-indicator="false"
          :height="6"
          :color="theme.primaryColor"
        />
      </div>
      <n-tooltip trigger="hover"
        ><template #trigger
          ><button class="resource-info-button" aria-label="查看数字化统计口径">
            <n-icon><IconMdiInformationOutline /></n-icon></button></template
        >{{ data?.coverage?.description || '按正式在册藏品去重，关联资源须审核通过且未禁用。' }}</n-tooltip
      >
    </section>
    <nav class="resource-business-links" aria-label="专题业务">
      <button
        v-for="card in businesses"
        :key="card.key"
        class="resource-business-link"
        :disabled="!target(card.path)"
        @click="open(card.path)"
      >
        <n-icon size="22" aria-hidden="true"
          ><IconMdiTerrain v-if="card.key === 'sceneCount'" /><IconMdiBookmarkMultipleOutline
            v-else-if="card.key === 'planCount'" /><IconMdiPaletteOutline v-else /></n-icon
        ><span>{{ card.label }}</span
        ><strong>{{ value(card.key) }}</strong
        ><n-icon class="resource-business-link__arrow" aria-hidden="true"><IconMdiArrowTopRight /></n-icon>
      </button>
    </nav>
    <slot />
  </ResourcePage>
</template>
<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { useThemeVars } from 'naive-ui';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/modules/auth';
import { workbench } from '../api/business';
import { resourceMenuTarget } from '../utils/navigation';
import ResourcePage from './ResourcePage.vue';
const emit = defineEmits<{ refresh: [] }>();
const theme = useThemeVars();
const auth = useAuthStore(),
  router = useRouter(),
  data = ref<any>(),
  loading = ref(false),
  error = ref('');
const metrics = [
  { key: 'resourceCount', label: '数字资源', path: '/resource/resource' },
  { key: 'modelCount', label: '三维模型', path: '/resource/resource?resourceType=MODEL_3D' },
  { key: 'panoramaCount', label: 'VR 全景', path: '/resource/resource?resourceType=PANORAMA' },
  { key: 'teachingCount', label: '教学资源', path: '/resource/resource?purpose=TEACHING' },
];
const tasks = [
  { key: 'pendingReview', label: '待审核', path: '/resource/resource?flowStatus=AUDIT' },
  { key: 'pendingPublish', label: '待发布', path: '/resource/resource?flowStatus=PASS&status=DRAFT' },
];
const businesses = [
  { key: 'sceneCount', label: '文化景观', path: '/resource/landscape' },
  { key: 'planCount', label: '数字收藏', path: '/resource/collecting' },
  { key: 'productCount', label: '文创产品', path: '/resource/products' },
];
const percentage = computed(() =>
  data.value?.coverage?.available && data.value.coverage.eligible > 0
    ? Math.round((data.value.coverage.digitized / data.value.coverage.eligible) * 1000) / 10
    : null,
);
function value(key: string) {
  const v = data.value?.[key];
  return typeof v === 'number' ? v.toLocaleString() : '—';
}
function target(path: string) {
  return resourceMenuTarget(auth.menus || [], path);
}
function open(path: string) {
  const t = target(path);
  if (t) router.push(t);
}
function refresh() {
  load();
  emit('refresh');
}
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const r: any = await workbench();
    data.value = r.data;
  } catch {
    data.value = undefined;
    error.value = '指标读取失败，请重试';
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.resource-overview {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 240px;
  gap: 20px;
}
.resource-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--resource-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--resource-surface);
}
.resource-metric {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: space-between;
  gap: 28px;
  min-width: 0;
  padding: 24px 20px;
  border: 0;
  border-right: 1px solid var(--resource-border);
  background: transparent;
  text-align: left;
  color: inherit;
  cursor: pointer;
}
.resource-metric:last-child {
  border-right: 0;
}
.resource-metric--primary {
  background: color-mix(in srgb, var(--resource-accent) 6%, var(--resource-surface));
}
.resource-metric__label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  white-space: nowrap;
}
.resource-metric__label .n-icon {
  color: var(--resource-muted);
}
.resource-metric strong {
  font-size: clamp(30px, 3.4vw, 46px);
  font-weight: 550;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -0.06em;
}
.resource-metric--primary strong {
  color: var(--resource-accent);
}
.resource-metric:hover:not(:disabled) {
  background: color-mix(in srgb, var(--resource-accent) 10%, var(--resource-surface));
}
.resource-queue {
  padding: 20px;
}
.resource-queue h2 {
  font-size: 13px;
  color: var(--resource-muted);
  margin-bottom: 10px;
}
.resource-queue button {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 12px;
  border: 0;
  padding: 9px 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.resource-queue button strong {
  margin-left: auto;
  font-size: 22px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
.resource-queue button .n-icon {
  color: var(--resource-muted);
}
.resource-queue button:hover:not(:disabled) {
  color: var(--resource-accent);
}
.resource-coverage {
  position: relative;
  display: flex;
  align-items: center;
  gap: 40px;
  margin-top: 20px;
}
.resource-coverage h2 {
  margin-bottom: 12px;
  font-size: 14px;
}
.resource-coverage__count {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-variant-numeric: tabular-nums;
}
.resource-coverage__count strong {
  font-size: 28px;
  font-weight: 550;
  letter-spacing: -0.04em;
}
.resource-coverage__count span {
  color: var(--resource-muted);
}
.resource-coverage__progress {
  flex: 1;
  max-width: 580px;
  margin-left: auto;
  padding-right: 20px;
}
.resource-coverage__progress > div:first-child {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 12px;
}
.resource-coverage__progress span {
  color: var(--resource-muted);
  font-size: 12px;
}
.resource-coverage__progress strong {
  font-size: 25px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.resource-coverage__progress small {
  font-size: 13px;
  margin-left: 2px;
}
.resource-info-button {
  position: absolute;
  top: 14px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 0;
  background: none;
  color: var(--resource-muted);
  cursor: help;
  border-radius: 5px;
}
.resource-business-links {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}
.resource-business-link {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 22px;
  border: 1px solid var(--resource-border);
  border-radius: 12px;
  background: var(--resource-surface);
  color: inherit;
  cursor: pointer;
  text-align: left;
}
.resource-business-link > .n-icon:first-child {
  color: var(--resource-accent);
}
.resource-business-link strong {
  margin-left: auto;
  font-size: 21px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.resource-business-link__arrow {
  color: var(--resource-muted);
}
.resource-business-link:hover:not(:disabled) {
  border-color: var(--resource-accent);
  transform: translateY(-2px);
}
.resource-metric:disabled,
.resource-business-link:disabled,
.resource-queue button:disabled {
  cursor: default;
}
.resource-metric:disabled .n-icon,
.resource-business-link:disabled .resource-business-link__arrow,
.resource-queue button:disabled .n-icon {
  visibility: hidden;
}
@container (max-width:900px) {
  .resource-overview {
    grid-template-columns: minmax(0, 1fr) 200px;
    gap: 16px;
  }
  .resource-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .resource-metric {
    padding: 20px;
    gap: 20px;
  }
  .resource-metric:nth-child(2) {
    border-right: 0;
  }
  .resource-metric:nth-child(-n + 2) {
    border-bottom: 1px solid var(--resource-border);
  }
  .resource-queue {
    display: flex;
    flex-direction: column;
    justify-content: center;
  }
}
@container (max-width:620px) {
  .resource-overview {
    grid-template-columns: 1fr;
  }
  .resource-queue {
    flex-direction: row;
    align-items: center;
    gap: 18px;
    padding: 14px 18px;
  }
  .resource-queue h2 {
    margin: 0;
    white-space: nowrap;
  }
  .resource-queue button {
    gap: 8px;
  }
  .resource-queue button .n-icon {
    display: none;
  }
  .resource-coverage {
    gap: 20px;
    align-items: stretch;
    flex-direction: column;
  }
  .resource-coverage__progress {
    width: 100%;
    max-width: none;
    padding: 0;
  }
  .resource-business-links {
    gap: 10px;
  }
  .resource-business-link {
    padding: 16px 12px;
    flex-wrap: wrap;
    gap: 10px;
  }
  .resource-business-link > .n-icon:first-child {
    display: none;
  }
  .resource-business-link span {
    flex-basis: 100%;
    font-size: 13px;
  }
  .resource-business-link strong {
    margin-left: 0;
  }
  .resource-business-link__arrow {
    margin-left: auto;
  }
}
</style>
