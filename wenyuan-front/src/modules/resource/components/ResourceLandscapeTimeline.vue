<template>
  <n-space vertical
    ><n-space justify="space-between"
      ><n-text>按采集时间串联景观记录，可选择两次采集对照画面与标记。</n-text
      ><n-button @click="load">刷新时间线</n-button></n-space
    ><n-alert v-if="error" type="error">{{ error }}</n-alert>
    <n-space v-if="data?.analysis"
      ><n-tag>采集 {{ data.analysis.captureCount }} 次</n-tag><n-tag>已审核 {{ data.analysis.approvedCount }} 次</n-tag
      ><n-tag>平均质量 {{ data.analysis.averageQuality?.toFixed(1) || '未评分' }}</n-tag></n-space
    >
    <n-timeline
      ><n-timeline-item
        v-for="c in data?.captures || []"
        :key="c.id"
        :title="c.title"
        :time="formatDateTime(c.data.capturedAt)"
        :type="c.flowStatus === 'PASS' ? 'success' : 'default'"
        :content="[c.data.source, c.data.annotation].filter(Boolean).join(' · ')"
    /></n-timeline>
    <n-empty v-if="!loading && !data?.captures?.length" description="暂无采集记录，请先新增采集并关联图片或视频" />
    <n-grid v-if="data?.captures?.length" cols="1 m:2" responsive="screen" :x-gap="16" :y-gap="16"
      ><n-gi v-for="(_, index) in comparisons" :key="index"
        ><n-space vertical
          ><n-select
            v-model:value="comparisons[index]"
            :options="options"
            placeholder="选择采集记录对照"
          /><ResourceMediaCard
            v-if="capture(index)?.data.resourceIds[0]"
            :id="capture(index).data.resourceIds[0]"
            :title="capture(index).title"
          /><n-text>{{ capture(index)?.data.annotation }}</n-text></n-space
        ></n-gi
      ></n-grid
    >
  </n-space>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { formatDateTime } from '@/utils/datetime';
import { timeline } from '../api/business';
import ResourceMediaCard from './ResourceMediaCard.vue';
const props = defineProps<{ sceneId: string; revision?: number }>();
const data = ref<any>(),
  loading = ref(false),
  error = ref(''),
  comparisons = ref<(string | null)[]>([null, null]);
let generation = 0;
const options = computed(() =>
  (data.value?.captures || []).map((c: any) => ({ label: `${formatDateTime(c.data.capturedAt)} · ${c.title}`, value: c.id })),
);
function capture(i: number) {
  return data.value?.captures.find((c: any) => c.id === comparisons.value[i]);
}
async function load() {
  const own = ++generation;
  loading.value = true;
  error.value = '';
  try {
    const r: any = await timeline(props.sceneId);
    if (own !== generation) return;
    data.value = r.data;
    const c = data.value.captures || [];
    comparisons.value = [c[0]?.id || null, c.at(-1)?.id || null];
  } catch {
    if (own === generation) error.value = '时间线读取失败';
  } finally {
    if (own === generation) loading.value = false;
  }
}
watch(() => [props.sceneId, props.revision], load, { immediate: true });
</script>
