<template>
  <CurrentRemoteImage
    v-bind="$attrs"
    :src="source"
    :reader="readApi"
    :revision="readRevision"
    :preview-disabled="previewDisabled"
  />
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { apiUrl } from '@/utils';
import CurrentRemoteImage from '@/components/basic/CurrentRemoteImage.vue';
import { readCurrentApiImage } from '@/utils/currentImage';

defineOptions({ inheritAttrs: false });
const props = defineProps<{
  src: string;
  params?: object;
  revision?: string | number;
  previewDisabled?: boolean;
}>();
const readRevision = computed(() => [props.params, props.revision]);
const source = computed(() => {
  if (!props.src) return undefined;
  // 保留显式协议供 API 路径检查拒绝，不能把它改写成合法 API 相对路径。
  return /^[a-z][a-z\d+.-]*:/i.test(props.src) ? props.src : apiUrl(props.src);
});
const readApi = (url: string, signal: AbortSignal) => readCurrentApiImage(url, signal, props.params);
</script>
