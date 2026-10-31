<template>
  <component :is="refComponent" v-bind="$attrs" ref="childComponent">
    <template v-for="(item, key) in slots" v-slot:[key]>
      <slot :name="key"></slot>
    </template>
  </component>
</template>
<script lang="ts" setup>
import { computed, inject, useSlots ,ref} from 'vue';
import ContentPageForm from './ContentPageForm.vue';
import DrawerPageForm from './DrawerPageForm.vue';

const slots = useSlots();

const screenWrapInitInject = inject('SCREEN-PROVIDE-INIT', false);

const refComponent = computed(() => {
  if (screenWrapInitInject) {
    return ContentPageForm;
  } else {
    return DrawerPageForm;
  }
});
const childComponent = ref();
const validateForm = () => {
  if (childComponent.value && childComponent.value.validateForm) {
    return childComponent.value.validateForm();
  }
  return Promise.reject(new Error('Form component not available'));
};

defineExpose({
  validateForm
});
</script>
