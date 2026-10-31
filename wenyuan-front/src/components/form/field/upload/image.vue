<template>
  <CurrentRemoteImage
    v-bind="$attrs"
    :src="source"
    :thumbnail-src="formatSrc"
    :managed="managed"
    :preview-disabled="previewDisabled"
    :revision="revision"
  >
    <template #placeholder
      ><n-icon :size="size"><IconMdiImage /></n-icon
    ></template>
  </CurrentRemoteImage>
</template>
<script lang="ts" setup>
import { fileUrl } from '@/utils';
import { computed, type PropType } from 'vue';
import CurrentRemoteImage from '@/components/basic/CurrentRemoteImage.vue';
import { isApplicationImageUrl } from '@/utils/currentImage';
import { normalizeImageReference } from '@/utils/imageReference';

defineOptions({ inheritAttrs: false });

const props = defineProps({
  previewDisabled: { type: Boolean, default: false },
  revision: { type: [String, Number] },
  quality: { type: Number, default: 0.7 },
  rotate: { type: Number, default: 0 },
  src: {
    type: String,
  },
  width: {
    //图片宽
    type: Number,
  },
  height: {
    //图片高
    type: Number,
  },
  type: {
    //处理方式
    //f 自适应宽高 p 根据宽高填充 c 是否位圆形 q 图像质量 0~1 r旋转角度 b 填充背景颜色，当p的时候有效
    type: String as PropType<'p' | 'f' | 'c' | 'q' | 'r'>,
  },
  bgColor: { type: String },
  size: {
    type: Number,
    default: 120,
  },
  mode: {
    type: String,
    default: 'online',
  },
});

const source = computed(() => {
  const reference = normalizeImageReference(props.src);
  return reference ? fileUrl(reference) : undefined;
});
const managed = computed(() => !!source.value && isApplicationImageUrl(source.value));
const formatSrc = computed(() => {
  if (!source.value || !managed.value) return source.value;
  const { size, type, width, height, bgColor, quality, rotate } = props;
  const url = new URL(source.value, window.location.href);
  if (!(width || height)) url.searchParams.set('w', String(size < 300 ? size * 2 : size));
  if (width) url.searchParams.set('w', String(width));
  if (height) url.searchParams.set('h', String(height));
  if (type) {
    // q/r 是数值参数，不能像 f/p/c 那样发送空标记。
    url.searchParams.set(type, type === 'q' ? String(quality) : type === 'r' ? String(rotate) : '');
    if (type === 'p' && bgColor) url.searchParams.set('b', bgColor);
  }
  return url.href;
});
</script>
