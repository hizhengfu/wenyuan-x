<template>
  <component :is="refComponent" v-bind="$attrs">
    <template v-for="(item, key) in slots" v-slot:[key]>
      <slot :name="key"></slot>
    </template>
  </component>
</template>
<script lang="ts" setup>
import { computed, inject, useSlots } from 'vue';
import ContentPageView from './ContentPageView.vue';
import DrawerPageView from './DrawerPageView.vue';

const slots = useSlots();
const screenWrapInitInject = inject('SCREEN-PROVIDE-INIT', false);

const refComponent = computed(() => {
  if (screenWrapInitInject) {
    return ContentPageView;
  } else {
    return DrawerPageView;
  }
});
</script>
