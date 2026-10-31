<template>
  <n-checkbox-group :value="modelValue" @update:value="emit('update:modelValue', $event as string[])">
    <n-space v-if="options.length">
      <n-checkbox v-for="opt in options" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </n-checkbox>
    </n-space>
    <n-text v-else depth="3" style="font-size: 12px">
      暂无启用通道（请先在「系统参数 → 通知中心」启用至少一个通道）
    </n-text>
  </n-checkbox-group>
</template>

<script lang="ts" setup>
import { onBeforeMount, ref } from 'vue';
import { enabledChannelOptions } from '../api/notify';
import type { EnumMessageNotifyChannel } from '../typings/message';

defineProps<{
  /** 已勾选的通道（string[]，与字典 value 对齐） */
  modelValue: EnumMessageNotifyChannel[] | string[];
}>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
}>();

interface Option {
  label: string;
  value: string;
}

const options = ref<Option[]>([]);

/**
 * 仅渲染**当前启用**的通道。
 *
 * 后端 [GET /api/notify-center/enabled-channel-options] 已合并 `notify_channel` 字典 label
 * 与 `NOTIFY_<CHANNEL>_ENABLED` 启用配置，前端不再做"字典+启用"的双查询过滤。
 * 模块未装载或所有通道未启用时返回空数组，组件显示提示文案。
 */
async function load() {
  const resp = await enabledChannelOptions().catch(() => ({ data: [] }));
  options.value = ((resp as any)?.data || []) as Option[];
}

onBeforeMount(load);
</script>
