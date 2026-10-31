<template>
  <!-- 任务中心容器在 Tab 栏右侧已提供全局刷新按钮，这里关闭组件内置刷新 -->
  <StatisticsPanel scope="mine" :show-filters="false" :show-refresh="false" />
</template>

<script setup lang="ts">
import StatisticsPanel from '../StatisticsPanel.vue'
</script>
