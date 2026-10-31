<template>
  <NModal v-model:show="visible" preset="card" title="采集记录详情"
    style="width: min(960px, calc(100vw - 32px))" content-style="max-height: 78vh; overflow: auto;">
    <div v-if="record" class="record-details">
      <div class="record-details__summary">{{ recordSummary(record) }}</div>
      <p class="record-details__hint">以下为该次上报的信息；事件与告警信号不代表设备当前状态。</p>
      <NDescriptions label-placement="top" :column="2" bordered size="small">
        <NDescriptionsItem v-for="item in recordDetails(record)" :key="item.label" :label="item.label">
          <span class="record-details__value">{{ item.value }}</span>
        </NDescriptionsItem>
      </NDescriptions>
      <h3>扩展属性</h3>
      <NTable v-if="attributes.length" size="small" :single-line="false">
        <thead><tr><th>字段</th><th>值</th></tr></thead>
        <tbody><tr v-for="item in attributes" :key="item.key">
          <td><div>{{ item.label }}</div><small v-if="item.label !== item.key">{{ item.key }}</small></td>
          <td class="record-details__value">{{ item.value }}</td>
        </tr></tbody>
      </NTable>
      <p v-else class="record-details__hint">该记录未提供扩展属性</p>
      <details class="record-details__raw">
        <summary>完整记录 JSON</summary>
        <pre>{{ JSON.stringify(record, null, 2) }}</pre>
      </details>
    </div>
  </NModal>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { NDescriptions, NDescriptionsItem, NModal, NTable } from 'naive-ui';
import type { IngestRecordVo } from '../typings';
import { recordAttributes, recordDetails, recordSummary } from '../utils/recordDisplay';

const props = defineProps<{ show: boolean; record?: IngestRecordVo | null }>();
const emit = defineEmits<{ (event: 'update:show', show: boolean): void }>();
const visible = computed({ get: () => props.show, set: (value) => emit('update:show', value) });
const attributes = computed(() => props.record ? recordAttributes(props.record) : []);
</script>

<style scoped>
.record-details__summary { font-size: 16px; font-weight: 600; line-height: 1.7; overflow-wrap: anywhere; }
.record-details__hint, small { color: var(--text-color-3, #767c82); }
.record-details__hint { margin: 8px 0 16px; }
.record-details__value, pre { white-space: pre-wrap; overflow-wrap: anywhere; word-break: break-word; }
.record-details :deep(table) { table-layout: fixed; width: 100%; }
.record-details h3 { margin: 20px 0 10px; font-size: 15px; }
.record-details th:first-child { width: 35%; }
.record-details td { vertical-align: top; overflow-wrap: anywhere; }
.record-details__raw { margin-top: 18px; }
.record-details__raw summary { cursor: pointer; }
.record-details__raw pre { font-size: 12px; line-height: 1.6; }
</style>
