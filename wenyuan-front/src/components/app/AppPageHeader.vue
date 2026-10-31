<template>
  <div class="flex row flex-center print-hidden">
    <AppPageBreadcrumb></AppPageBreadcrumb>
    <div class="flex-1"></div>
    <n-button @click="reloadPage()" :loading="appStore.reloadFlag" circle>
      <template #icon>
        <icon-mdi-refresh class="text-24px"></icon-mdi-refresh>
      </template>
    </n-button>
  </div>
</template>
<script lang="ts" setup>
import AppPageBreadcrumb from './AppPageBreadcrumb.vue'
import {useAppStore} from '@/store';

const appStore = useAppStore()

function reloadPage() {
  appStore.reloadPage(100)
}

</script>
