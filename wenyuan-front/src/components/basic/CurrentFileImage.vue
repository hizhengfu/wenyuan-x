<template>
  <div ref="container" class="current-file-image" v-bind="$attrs">
    <template v-if="eligible">
      <template v-if="managed">
        <NImage
          v-if="thumbnail.url.value"
          :src="thumbnail.url.value"
          :alt="alt"
          :preview-disabled="true"
          :img-props="{
            style: 'max-width: 100%',
            tabindex: previewDisabled && !groupPreview ? undefined : 0,
            role: previewDisabled && !groupPreview ? undefined : 'button',
            'aria-label': previewDisabled && !groupPreview ? undefined : '查看大图',
            onKeydown: previewKey,
          }"
          @click="activateImage"
          @error="imageError"
        />
        <NSpin v-else-if="thumbnail.loading.value" size="small" />
        <NText v-if="thumbnail.error.value || decodeError || preview.error.value" type="error" role="alert">
          {{ thumbnail.error.value || decodeError || preview.error.value }}
        </NText>
        <NButton
          v-if="!hideReload && (thumbnail.url.value || thumbnail.error.value || decodeError || preview.error.value)"
          text
          @click="refresh"
          >重新读取图片</NButton
        >
        <NSpin v-if="preview.loading.value" size="small" />
        <NImagePreview :src="preview.url.value" :show="showPreview" :on-close="closePreview" />
      </template>
      <NImage v-else-if="src" :src="src" :alt="alt" :preview-disabled="previewDisabled" @click="activateImage" />
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { NButton, NImage, NImagePreview, NSpin, NText } from 'naive-ui';
import {
  imageSizeUrl,
  readCurrentImage,
  isApplicationImageUrl,
  useCurrentImageLifecycle,
  useCurrentImageRead,
} from '@/utils/currentImage';

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  src?: string;
  alt?: string;
  revision?: string | number;
  lazy?: boolean;
  width?: number;
  height?: number;
  padding?: boolean;
  previewDisabled?: boolean;
  hideReload?: boolean;
  groupPreview?: boolean;
}>();
const container = ref<HTMLElement>();
const visible = ref(!props.lazy);
let observer: IntersectionObserver | undefined;
const emit = defineEmits<{ activate: [] }>();
function activateImage() {
  emit('activate');
  void openPreview();
}
const managed = computed(() => !!props.src && isApplicationImageUrl(props.src));
const readImage = (url: string, signal: AbortSignal) => readCurrentImage(url, signal, { silentError: true });
const thumbnail = useCurrentImageRead(readImage),
  preview = useCurrentImageRead(readImage);
const showPreview = ref(false),
  decodeError = ref('');
function closePreview() {
  showPreview.value = false;
  preview.clear();
}
function invalidate() {
  thumbnail.clear();
  closePreview();
  decodeError.value = '';
}
function reload() {
  if (visible.value && managed.value && props.src)
    void thumbnail.read(imageSizeUrl(props.src, props.width, props.height, props.padding));
}
const { eligible, refresh } = useCurrentImageLifecycle(
  () => [props.src, props.revision, props.width, props.height, props.padding],
  invalidate,
  reload,
);
onMounted(() => {
  if (!props.lazy) return;
  if (typeof IntersectionObserver === 'undefined' || !container.value) {
    visible.value = true;
    refresh();
    return;
  }
  observer = new IntersectionObserver(entries => {
    if (!entries.some(entry => entry.isIntersecting)) return;
    visible.value = true;
    observer?.disconnect();
    refresh();
  }, { rootMargin: '240px' });
  observer.observe(container.value);
});
onBeforeUnmount(() => observer?.disconnect());
function imageError() {
  thumbnail.clear();
  closePreview();
  decodeError.value = '图片无法解码，请核对文件内容。';
}
async function openPreview() {
  if (!eligible.value || props.previewDisabled || !managed.value || !props.src) return;
  showPreview.value = false;
  if (await preview.read(props.src)) showPreview.value = true;
  else if (preview.error.value) thumbnail.clear();
}
function previewKey(event: KeyboardEvent) {
  if ((props.previewDisabled && !props.groupPreview) || !['Enter', ' '].includes(event.key)) return;
  event.preventDefault();
  activateImage();
}
defineExpose({ refresh });
</script>
<style scoped>
.current-file-image {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  max-width: 100%;
}
.current-file-image :deep(img) {
  max-width: 100%;
}
</style>
