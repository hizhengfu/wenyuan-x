<template>
  <svg aria-hidden="true" fill="currentColor">
    <use :xlink:href="symbolId" fill="currentColor" />
  </svg>
</template>

<script>
import { defineComponent, computed } from 'vue';

export default defineComponent({
  props: {
    prefix: {
      type: String,
      default: 'icon',
    },
    name: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const symbolId = computed(() => `#${props.prefix}-${props.name}`);
    return { symbolId };
  },
});
</script>
