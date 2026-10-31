<template>
  <span>{{ text }}</span>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef, watch } from 'vue';
import { frontType } from '@/modules/sys/api/dict';
import { collectionDictFallbackLabel } from '../utils/collectionDictFallback';

const props = defineProps<{ type: string; value?: unknown }>();
type DictOption = { value: string | number; label?: string | number };
const options = shallowRef<DictOption[]>([]);
let requestVersion = 0;

// Historic/imported wording is part of the catalog record, even if the current dictionary lacks it.
const text = computed(() => {
  const raw = props.value == null ? '' : String(props.value);
  if (!raw.trim()) return '--';
  return raw.split(',').map(part => {
    const item = options.value.find(option => String(option.value) === part.trim());
    return item?.label == null || String(item.label).trim() === '' ? collectionDictFallbackLabel(props.type, part) || part : String(item.label);
  }).join(',');
});

watch(() => props.type, async type => {
  const current = ++requestVersion;
  options.value = [];
  try {
    const response = await frontType(type, true);
    if (current === requestVersion) options.value = Array.isArray(response?.data) ? response.data : [];
  } catch {
    // Dictionary lookup cannot erase the preserved wording.
  }
}, { immediate: true });
onBeforeUnmount(() => { requestVersion++; });
</script>
