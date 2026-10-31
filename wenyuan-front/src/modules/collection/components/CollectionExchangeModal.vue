<template>
  <NModal v-model:show="visible" preset="card" title="馆藏文物规范交换" style="width: min(1000px, 96vw)">
    <NAlert type="info" :bordered="false" class="mb-3">按当前检索条件核查已正式登记且未退出的藏品。交换包含著录数据、CSV、照片清单和原图，下载时会再次核查。</NAlert>
    <NSpace class="mb-3" align="center">
      <NButton :loading="checking" :disabled="downloading" @click="check">重新检查</NButton>
      <NButton type="primary" :loading="downloading" :disabled="!result?.valid || checking" @click="download">下载交换包</NButton>
      <span v-if="result">{{ result.items.length }} 条记录，{{ result.issueCount }} 项待补充</span>
    </NSpace>
    <NAlert v-if="error" type="error" class="mb-3">{{ error }}</NAlert>
    <NEmpty v-if="result && !result.items.length" description="当前条件没有可交换的正式登记记录" />
    <NDataTable v-else :columns="columns" :data="rows" :loading="checking" :max-height="520" :pagination="{ pageSize: 15 }" :row-key="row => row.collectionId" />
  </NModal>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue';
import { NAlert, NButton, NDataTable, NEmpty, NModal, NSpace } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { exchangeApi, type ExchangeCheck } from '../api/exchange';
const auth=useAuthStore();
const visible=ref(false), checking=ref(false), downloading=ref(false), error=ref('');
const result=ref<ExchangeCheck>();
let query: Record<string, unknown>={}, controller: AbortController | undefined;
const rows=computed(() => result.value?.items.map(item => ({
  collectionId: item.collectionId, totalRegNo: item.data.totalRegNo, name: item.data.name,
  photos: (item.photos || []).length,
  issues: [...(item.issues || []), ...(item.photos || []).flatMap((p, index) => (p.issues || []).map(issue => `照片 ${index + 1}（${p.originalName || '未命名'}）：${issue}`))].join('；') || '检查通过',
})) || []);
const columns=[{ title: '总登记号', key: 'totalRegNo', width: 140 }, { title: '名称', key: 'name', width: 170 }, { title: '照片数', key: 'photos', width: 75 }, { title: '检查结果', key: 'issues' }];
function begin() { controller?.abort(); controller=new AbortController(); return controller.signal; }
async function check() {
  const signal=begin(); checking.value=true; result.value=undefined; error.value='';
  try { const response=await exchangeApi.check(query, signal); if(!signal.aborted) result.value={...response.data,items:response.data?.items || []}; }
  catch(e) { if(!signal.aborted) error.value=e instanceof Error ? e.message : '检查失败，请重试'; }
  finally { if(!signal.aborted) checking.value=false; }
}
async function download() {
  if(!result.value?.valid) return;
  const signal=begin(); downloading.value=true; error.value='';
  try { await exchangeApi.download(query, signal); }
  catch(e) { if(!signal.aborted) { error.value=e instanceof Error ? e.message : '下载失败，请重新检查'; result.value=undefined; } }
  finally { if(!signal.aborted) downloading.value=false; }
}
function open(value: Record<string, unknown>) { query=JSON.parse(JSON.stringify(value)); visible.value=true; downloading.value=false; void check(); }
watch(visible, show => { if(!show) { controller?.abort(); result.value=undefined; checking.value=false; downloading.value=false; } });
function close() {controller?.abort();visible.value=false;result.value=undefined;checking.value=false;downloading.value=false;}
onBeforeUnmount(close);onDeactivated(close);
watch(()=>JSON.stringify(auth.userInfo),close,{flush:'sync'});
defineExpose({ open });
</script>
