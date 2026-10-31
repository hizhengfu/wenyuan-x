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
import { achievementApi } from '../api/achievement';
import type { ResAchievementVo } from '../typings';

const props = withDefaults(
  defineProps<{
    value?: string | null;
    /** 仅评审通过的成果（转化/评价场景） */
    passedOnly?: boolean;
    placeholder?: string;
    disabled?: boolean;
  }>(),
  { passedOnly: true, placeholder: '选择成果', disabled: false },
);

defineEmits<{ (e: 'update:value', val: string | null): void }>();

const loading = ref(false);
const options = ref<{ label: string; value: string }[]>([]);

onMounted(async () => {
  loading.value = true;
  try {
    const params: any = { pageNum: 1, pageSize: 0 };
    if (props.passedOnly) params.flowStatus = 'PASS';
    const resp: any = await achievementApi.page(params);
    const list: ResAchievementVo[] = resp?.data?.list ?? [];
    options.value = list.map((a) => ({
      label: a.achievementNo ? `${a.achievementNo} ${a.title}` : a.title || a.id!,
      value: a.id!,
    }));
  } finally {
    loading.value = false;
  }
});
</script>
