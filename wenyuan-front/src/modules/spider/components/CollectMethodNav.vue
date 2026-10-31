<template>
  <n-card size="small" :bordered="false">
    <n-space align="center" justify="space-between">
      <div>
        <h2 class="method-title">公众号采集</h2>
        <n-text depth="3">{{
          method === 'links'
            ? '已有文章链接或 RSS 地址，按需导入内容，无需扫码。'
            : '扫码连接后搜索公众号，可采集一次，也可定时采集。'
        }}</n-text>
      </div>
      <nav v-if="methods.length > 1" aria-label="切换采集方式">
        <n-space>
          <n-button
            v-for="entry in methods"
            :key="entry"
            :type="entry === method ? 'primary' : 'default'"
            :aria-current="entry === method ? 'page' : undefined"
            @click="entry !== method && emit('select', entry)"
          >
            {{ entry === 'links' ? '链接 / RSS' : '扫码 / 定时' }}
          </n-button>
        </n-space>
      </nav>
    </n-space>
  </n-card>
</template>
<script setup lang="ts">
import type { CollectMethod } from '../utils/collectNavigation';
defineProps<{ method: CollectMethod; methods: CollectMethod[] }>();
const emit = defineEmits<{ select: [method: CollectMethod] }>();
</script>
<style scoped>
.method-title {
  margin: 0 0 6px;
  font-size: 18px;
}
</style>
