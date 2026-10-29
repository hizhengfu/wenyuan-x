<template>
  <n-list style="width: 360px" hoverable>
    <template #header>
      <n-space justify="space-between" align="center">
        <span class="font-800">
          <n-icon size="16" style="vertical-align: -3px"><HIcon icon="c:AlarmLight" /></n-icon>
          我的站内信
        </span>
        <n-button v-if="items.length > 0" text size="tiny" @click="$emit('readAll')">全部已读</n-button>
      </n-space>
    </template>

    <n-list-item v-if="items.length === 0">
      <n-empty description="暂无新消息" size="small" />
    </n-list-item>

    <n-list-item v-for="item in items" :key="item.id" @click="$emit('itemClick', item)">
      <n-space vertical :size="2">
        <n-ellipsis class="title">{{ item.title }}</n-ellipsis>
        <n-space :size="6" align="center" style="font-size: 12px; color: #999">
          <n-icon size="12"><IconMdiClockTimeTwelveOutline /></n-icon>
          <n-time :time="toDate(item.createTime)" type="relative" />
          <n-tag v-if="item.readStatus === 'UNREAD'" size="tiny" type="error" round>未读</n-tag>
        </n-space>
      </n-space>
    </n-list-item>

    <template #footer>
      <div style="text-align: right">
        <router-link :to="{ path: '/message/inbox', query: { title: '我的站内信' } }">
          查看更多
        </router-link>
      </div>
    </template>
  </n-list>
</template>

<script lang="ts" setup>
import type { MessageInboxItem } from '../typings/message';

defineProps<{
  items: MessageInboxItem[];
}>();
defineEmits<{
  (e: 'itemClick', item: MessageInboxItem): void;
  (e: 'readAll'): void;
}>();

function toDate(s: string | undefined): number {
  if (!s) return Date.now();
  const t = new Date(s).getTime();
  return Number.isNaN(t) ? Date.now() : t;
}
</script>

<style scoped>
.title {
  max-width: 320px;
  cursor: pointer;
  color: #2279fa;
}
</style>
