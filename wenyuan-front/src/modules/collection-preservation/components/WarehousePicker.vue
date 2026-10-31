<template>
  <WarehouseTreeSelect
    v-bind="$attrs"
    :value="value"
    :scene-type="null"
    placeholder="选择保管/展陈位置"
    @update:value="emit('update:value', $event)"
  />
</template>

<script setup lang="ts">
import WarehouseTreeSelect from '@/modules/collection/components/WarehouseTreeSelect.vue';

defineOptions({ inheritAttrs: false });

defineProps<{ value?: string | null }>();
const emit = defineEmits<{ (e: 'update:value', v: string | null): void }>();
</script>
