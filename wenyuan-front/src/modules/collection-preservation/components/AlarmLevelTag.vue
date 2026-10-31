<template>
  <NTag :type="tagType" :bordered="false" size="small">{{ label }}</NTag>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NTag } from 'naive-ui';
import { useDictLabel } from '@/utils/dict';
import type { PvAlarmLevel } from '../typings';

const props = defineProps<{ level?: PvAlarmLevel | string | null }>();

const TYPE: Record<string, 'error' | 'warning' | 'info' | 'success' | 'default'> = {
  RED: 'error', ORANGE: 'warning', YELLOW: 'warning', BLUE: 'info', NORMAL: 'success',
};

const { label } = useDictLabel('pv_alarm_level', computed(() => props.level ?? undefined));
const tagType = computed(() => TYPE[props.level ?? ''] ?? 'default');
</script>
