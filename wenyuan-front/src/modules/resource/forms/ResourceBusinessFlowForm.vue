<template>
  <n-space v-if="record" vertical
    ><n-descriptions bordered :column="2"
      ><n-descriptions-item label="业务类型">{{ KIND_NAMES[record.kind] }}</n-descriptions-item
      ><n-descriptions-item label="名称">{{ record.title }}</n-descriptions-item
      ><n-descriptions-item v-for="(value, key) in record.data" :key="key" :label="labels[key] || String(key)">{{
        Array.isArray(value) ? value.join('、') : value
      }}</n-descriptions-item></n-descriptions
    ><n-button
      v-for="(id, index) in record.data.resourceIds"
      :key="id"
      @click="
        previewId = id;
        show = true;
      "
      >预览关联资源 {{ Number(index) + 1 }}</n-button
    ><ResourcePreview v-if="show" v-model:show="show" :resource-id="previewId"
  /></n-space>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue';
import type { FlowFormProps } from '../../flow/typings/flow';
import { KIND_NAMES } from '../api/business';
const props = defineProps<FlowFormProps>();
const record = computed(() => props.formData?.record);
const show = ref(false),
  previewId = ref('');
const ResourcePreview = defineAsyncComponent(async () => (await import('../components/ResourcePreview.vue')).default);
const labels: Record<string, string> = {
  resourceIds: '关联资源',
  description: '说明',
  location: '地点',
  capturedAt: '采集时间',
  source: '来源',
  annotation: '标记与处理说明',
  quality: '质量评分',
  category: '类别',
  background: '创作背景',
  startsAt: '领取开始',
  endsAt: '领取结束',
  capacity: '发证限额',
  vendor: '厂商',
  licenseNo: '授权编号',
  rights: '权利范围',
  validFrom: '有效期起',
  validTo: '有效期止',
};
defineExpose({ validate: async () => true });
</script>
