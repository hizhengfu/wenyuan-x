<template>
  <div class="app-route-page min-w-0">
    <component :is="component" />
  </div>
</template>
<script setup lang="ts">
import type { Component, VNode } from 'vue';

// 布局属性及 Transition 落在实体根节点；原路由 VNode 保留 props、ref 和路由钩子。
defineProps<{ component?: Component | VNode }>();
</script>
