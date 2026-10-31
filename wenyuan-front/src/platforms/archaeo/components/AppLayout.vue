<template>
  <AppLayout :inverted="true" :showTab="true">
    <template #sidebar-logo>
      <img src="../assets/image/logo.png" style="height: 80%" :alt="appStore.title" :title="appStore.title" />
    </template>
    <template #header_right>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button href="#/visual" text tag="a" class="mr-3" target="_blank">
            <n-icon size="26px">
              <IconCVisual></IconCVisual>
            </n-icon>
          </n-button>
        </template>
        可视化
      </n-tooltip>
    </template>
  </AppLayout>
</template>
<script lang="ts" setup>
import AppLayout from '@/components/app/AppLayout.vue';
import { useAppStore } from '@/store';

const appStore = useAppStore();
</script>
