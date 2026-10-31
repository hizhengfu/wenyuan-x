<template>
  <NAlert v-if="messages.length" type="warning">
    <template #header>
      <CollectionFieldLabel label="归档原件待核查" :help="messages.join('\n')" />
    </template>
    详情仍可查阅；异常原件暂不可下载，请核查存储与备份。
  </NAlert>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { NAlert } from 'naive-ui';
import CollectionFieldLabel from './CollectionFieldLabel.vue';
import { collectArchiveIssues } from '../utils/archiveIssues';
const props = defineProps<{ issues?: string[]; data?: unknown }>();
const messages = computed(() => collectArchiveIssues({ evidenceErrors: props.issues, detail: props.data }));
</script>
