<template>
  <NSelect
    :value="value ?? null"
    :options="options"
    :loading="loading"
    filterable
    clearable
    :placeholder="placeholder"
    :disabled="disabled"
    @update:value="$emit('update:value', $event)"
  />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { NSelect } from 'naive-ui';
import { topicApi } from '../api/topic';
import type { ResTopicVo } from '../typings';

const props = withDefaults(
  defineProps<{
    value?: string | null;
    /** 限定课题阶段（如 RESEARCH 在研），空则全部 */
    stage?: string;
    placeholder?: string;
    disabled?: boolean;
  }>(),
  { placeholder: '选择课题', disabled: false },
);

defineEmits<{ (e: 'update:value', val: string | null): void }>();

const loading = ref(false);
const options = ref<{ label: string; value: string }[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    const resp: any = await topicApi.page({ pageNum: 1, pageSize: 0, stage: props.stage || undefined });
    const list: ResTopicVo[] = resp?.data?.list ?? [];
    options.value = list.map((t) => ({
      label: t.topicNo ? `${t.topicNo} ${t.name}` : t.name || t.id!,
      value: t.id!,
    }));
  } finally {
    loading.value = false;
  }
});
</script>
