<template>
  <NFormItem ref="item" v-bind="$attrs" :label="label" :feedback="hasValidationFeedback || labelHelp ? undefined : hint">
    <template v-if="labelHelp" #label><CollectionFieldLabel :label="label" :help="hint" /></template>
    <slot />
  </NFormItem>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { NFormItem, type FormItemInst } from 'naive-ui';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
defineOptions({ inheritAttrs: false });
const props = defineProps<{ label?: string; hint?: string }>();
// Long or multiline guidance belongs beside the label; keep brief input help below the control.
const labelHelp = computed(() => !!props.label && !!props.hint && (props.hint.length > 32 || props.hint.includes('\n')));
// NFormItem exposes its reactive mergedValidationStatus. Never mask its error/warning explains with a hint.
const item = ref<(FormItemInst & { mergedValidationStatus?: string }) | null>(null);
const hasValidationFeedback = computed(() => ['error', 'warning'].includes(item.value?.mergedValidationStatus || ''));
</script>
