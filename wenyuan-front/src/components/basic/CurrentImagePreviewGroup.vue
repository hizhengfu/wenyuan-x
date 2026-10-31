<template>
  <NImagePreview
    :src="displayUrl"
    :show="eligible && show && !!displayUrl"
    :on-close="close"
    :on-prev="items.length > 1 ? previous : undefined"
    :on-next="items.length > 1 ? next : undefined"
    show-toolbar-tooltip
  />
  <NModal
    v-if="eligible && show && selected && !displayUrl"
    :show="true"
    :mask-closable="true"
    @update:show="
      (value) => {
        if (!value) close();
      }
    "
  >
    <div class="image-read-status" role="status">
      <NSpin v-if="image.loading.value" size="small" />
      <NText>{{ image.error.value || '正在读取当前影像' }}</NText>
      <NButton v-if="image.error.value" @click="refresh">重新读取图片</NButton>
      <NButton @click="close">关闭预览</NButton>
    </div>
  </NModal>
</template>
<script setup lang="ts">
import { computed, watch } from 'vue';
import { NButton, NImagePreview, NModal, NSpin, NText } from 'naive-ui';
import { isApplicationImageUrl, useCurrentImageLifecycle, useCurrentImageRead } from '@/utils/currentImage';

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  items: { id: string; src: string }[];
  current: number;
  show: boolean;
  revision?: string | number;
}>();
const emit = defineEmits<{
  'update:current': [value: number];
  'update:show': [value: boolean];
  denied: [];
}>();
const selected = computed(() => props.items[props.current]);
const image = useCurrentImageRead();
let generation = 0;
function clear() {
  generation++;
  image.clear();
}
const managed = computed(() => !!selected.value && isApplicationImageUrl(selected.value.src));
const displayUrl = computed(() => (managed.value ? image.url.value : selected.value?.src));
function close() {
  clear();
  emit('update:show', false);
}
function reload() {
  if (props.show && managed.value && selected.value) {
    const ticket = generation;
    void image.read(selected.value.src).then((ok) => {
      if (ticket === generation && !ok && image.error.value) emit('denied');
    });
  }
}
const { eligible, refresh } = useCurrentImageLifecycle(() => [props.items, props.current, props.show], clear, reload);
watch(
  eligible,
  (value) => {
    if (!value) close();
  },
  { flush: 'sync' },
);
watch(() => props.revision, close, { flush: 'sync' });
watch(
  selected,
  (value) => {
    if (!value) close();
  },
  { flush: 'sync' },
);
function select(step: number) {
  clear();
  if (props.items.length > 1) emit('update:current', (props.current + step + props.items.length) % props.items.length);
}
function previous() {
  select(-1);
}
function next() {
  select(1);
}
</script>
<style scoped>
.image-read-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px;
  background: var(--n-color, white);
  border-radius: 8px;
}
</style>
