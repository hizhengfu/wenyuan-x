<template>
  <n-grid x-gap="12" :cols="data.length">
    <n-gi v-for="item in data">
      <n-statistic :label="item.label">
        <template #prefix v-if="item.icon">
          <n-icon>
            <Icon :icon="item.icon"></Icon>
          </n-icon>
        </template>
        {{ item.value }}
      </n-statistic>
    </n-gi>
  </n-grid>
</template>
<script lang="ts" setup>
import { Icon } from '@iconify/vue';

/**
 *
 * */
const props = defineProps({
  data: {
    type: Array as () => Array<any>,
    default() {
      return [
        { label: '客流', icon: 'mdi:chart-line', value: 8000 },
        { label: '人流', icon: 'mdi:chart-line', value: 9000 },
      ];
    },
  },
});
</script>
