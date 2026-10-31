<template>
  <component :is="renderIcon"></component>
</template>

<script setup>
import { computed, h } from 'vue';
import { Icon as IconifyIcon } from '@iconify/vue';
import SvgIcon from './SvgIcon.vue';

const props = defineProps({
  icon: {
    type: String,
  },
});

const customRegExp = /^[hcm]:/;

// 判断是否为自定义图标（假设自定义图标的前缀为 h-,c-）
const isCustomIcon = computed(() => customRegExp.test(props.icon));

const renderIcon = computed(() => {
  if (!props.icon) return null;

  if (isCustomIcon.value) {

    // 对于自定义图标，直接使用 <svg> 标签并引用对应的符号 ID
    const iconNameWithoutPrefix = props.icon.replace(customRegExp, '');
    return () => h(SvgIcon, { name: iconNameWithoutPrefix });
  } else {
    // 使用 Iconify 图标
    return () => h(IconifyIcon, { icon: props.icon });
  }
});
</script>
