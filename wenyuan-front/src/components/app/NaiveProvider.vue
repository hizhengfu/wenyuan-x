<template>
  <n-config-provider :abstract="true" :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <AppRouteView/>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>
<script lang="ts" setup>
import { dateZhCN, zhCN } from 'naive-ui';
import AppRouteView from './AppRouteView.vue'

const props = defineProps({
  themeOverrides: {
    type: Object,
  },
});

</script>
