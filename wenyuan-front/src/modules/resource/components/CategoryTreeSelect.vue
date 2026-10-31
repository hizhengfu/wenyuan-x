<template>
  <n-tree-select
    v-model:value="innerValue"
    :options="treeOptions"
    :default-expand-all="false"
    key-field="id"
    label-field="name"
    children-field="children"
    :loading="loading"
    :placeholder="placeholder"
    :clearable="clearable"
    :filterable="filterable"
    check-strategy="all"
    :disabled="disabled"
    @update:value="onUpdate"
  />
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue';
import { tree as fetchTree } from '../api/category';

const props = defineProps({
  value: { type: String, default: '' },
  placeholder: { type: String, default: '请选择分类' },
  clearable: { type: Boolean, default: true },
  filterable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
});

const emit = defineEmits(['update:value']);

const innerValue = ref<string | null>(props.value || null);
const treeOptions = ref<any[]>([]);
const loading = ref(false);

watch(
  () => props.value,
  (v) => {
    innerValue.value = v || null;
  },
);

function onUpdate(v: any) {
  emit('update:value', v || '');
}

async function loadTree() {
  loading.value = true;
  try {
    const resp: any = await fetchTree({});
    treeOptions.value = resp.data || [];
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadTree();
});

defineExpose({ reload: loadTree });
</script>
