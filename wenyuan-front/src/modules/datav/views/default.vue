<template>
  <div class="flex-center wh-full">
    <n-spin description="正在进入大屏管理" />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();
onMounted(() => router.replace('/datav/manage'));
</script>
