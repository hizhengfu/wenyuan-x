<template>
  <n-time v-if="hasValue" :time="normalizedTime" v-bind="$attrs" />
  <span v-else>--</span>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  time?: string | number | Date | null;
}>();

const hasValue = computed(() => {
  if (props.time === null || props.time === undefined || props.time === '') return false;
  const d = props.time instanceof Date ? props.time : new Date(props.time);
  return !isNaN(d.getTime());
});

const normalizedTime = computed(() => {
  if (props.time instanceof Date) return props.time;
  if (typeof props.time === 'number') return props.time;
  return new Date(props.time as string);
});
</script>
