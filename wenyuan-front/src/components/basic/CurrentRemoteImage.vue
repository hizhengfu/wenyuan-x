<template>
  <img v-if="eligible && showFallback" v-bind="$attrs" :src="fallbackSrc" alt="" />
  <NImage
    v-else-if="eligible && displayUrl"
    v-bind="$attrs"
    :src="displayUrl"
    :preview-src="undefined"
    :preview-disabled="managed || previewDisabled"
    :img-props="imageDomProps()"
    @click="openPreview"
    @error="onDecodeError"
  >
    <template v-for="(_, name) in $slots" #[name]="slotProps"><slot :name="name" v-bind="slotProps || {}" /></template>
  </NImage>
  <span
    v-if="eligible && managed && !fallbackSrc && (thumbnail.loading.value || preview.loading.value || hasError || showReload)"
    class="current-remote-image-status"
  >
    <NSpin v-if="thumbnail.loading.value || preview.loading.value" size="small" />
    <NText v-if="hasError" type="error" role="alert">
      {{ thumbnail.error.value || preview.error.value || decodeError }}
    </NText>
    <NButton v-if="showReload" text @click="refresh">重新读取图片</NButton>
  </span>
  <NImagePreview :src="preview.url.value" :show="showPreview" :on-close="closePreview" />
</template>
<script setup lang="ts">
import { computed, ref, useAttrs } from 'vue';
import { NImage, NImagePreview, NButton, NText, NSpin } from 'naive-ui';
import {
  readCurrentImage,
  useCurrentImageRead,
  useCurrentImageLifecycle,
  type CurrentImageReader,
} from '@/utils/currentImage';

defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    src?: string;
    thumbnailSrc?: string;
    managed?: boolean;
    reader?: CurrentImageReader;
    revision?: unknown;
    previewDisabled?: boolean;
    reloadOnErrorOnly?: boolean;
    /** 可选缩略图：缺图时静默显示占位图，不影响其他图片的错误提示。 */
    fallbackSrc?: string;
    hideReload?: boolean;
  }>(),
  { managed: true },
);
const attrs = useAttrs();
const reader: CurrentImageReader = (url, signal) =>
  props.reader ? props.reader(url, signal) : readCurrentImage(url, signal, { silentError: !!props.fallbackSrc });
const thumbnail = useCurrentImageRead(reader),
  preview = useCurrentImageRead(reader);
const showPreview = ref(false),
  decodeError = ref('');
const displayUrl = computed(() => (props.managed ? thumbnail.url.value : props.src));
const showFallback = computed(() =>
  Boolean(props.fallbackSrc && (!props.src || thumbnail.error.value || preview.error.value || decodeError.value)),
);
const hasError = computed(() => Boolean(thumbnail.error.value || preview.error.value || decodeError.value));
const showReload = computed(() =>
  !props.hideReload && Boolean(hasError.value || (!props.reloadOnErrorOnly && thumbnail.url.value)),
);
function closePreview() {
  showPreview.value = false;
  preview.clear();
}
function invalidate() {
  thumbnail.clear();
  closePreview();
  decodeError.value = '';
}
const { eligible, refresh } = useCurrentImageLifecycle(
  () => [
    props.src,
    props.thumbnailSrc,
    props.revision,
    props.managed,
    props.previewDisabled,
    props.reader,
    props.fallbackSrc,
  ],
  invalidate,
  () => {
    if (props.managed && props.src) void thumbnail.read(props.thumbnailSrc || props.src);
  },
);
async function openPreview() {
  if (!eligible.value || !props.managed || props.previewDisabled || !props.src) return;
  closePreview();
  if (await preview.read(props.src)) showPreview.value = true;
  else if (preview.error.value) thumbnail.clear();
}
function onDecodeError() {
  if (!props.managed && !props.fallbackSrc) return;
  thumbnail.clear();
  closePreview();
  decodeError.value = '图片无法解码，请核对文件内容。';
}
function imageDomProps() {
  const original = (attrs['img-props'] || attrs.imgProps || {}) as Record<string, any>;
  if (!props.managed || props.previewDisabled) return original;
  return {
    ...original,
    tabindex: original.tabindex ?? 0,
    role: 'button',
    onKeydown: (event: KeyboardEvent) => {
      const handlers = original.onKeydown;
      if (Array.isArray(handlers)) handlers.forEach((handler) => handler(event));
      else if (typeof handlers === 'function') handlers(event);
      if (!event.defaultPrevented && ['Enter', ' '].includes(event.key)) {
        event.preventDefault();
        void openPreview();
      }
    },
  };
}
defineExpose({ refresh });
</script>
<style scoped>
.current-remote-image-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
