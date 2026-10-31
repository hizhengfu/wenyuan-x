<template>
  <div class="digital-package-preview">
    <p v-if="!entries.length">本批尚无可预览的 glTF / GLB / 瓦片清单路径；可下载原件核对。</p>
    <template v-else>
      <NSpace><NSelect v-model:value="entry" :options="entries" :disabled="!!display || loading" style="min-width:260px" aria-label="模型包入口" />
        <NButton :disabled="!eligible || loading || !!display" @click="open">预览已核验模型</NButton>
        <NButton v-if="loading || display" @click="clear">关闭预览</NButton></NSpace>
      <p>按冻结清单核验整包原件后预览 glTF 2.0 / GLB / 瓦片包（整包不超过 256 MiB）。支持 Draco、Meshopt、KTX2 及显式瓦片包；隐式瓦片须先展开依赖。</p>
    </template>
    <p v-if="loading" role="status">{{ display ? '正在解析模型…' : `正在核验原件 ${progress}…` }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div v-if="display" class="scene">
      <component :is="display.fileType === 'tileset' ? TilesModel : display.compressed ? CompressedModel : Model" :key="display.id" :file-path="display.url" :file-type="display.fileType" :enable-draco="false" :background-color="0xf3f5f7"
        :height="420" @load="display.loaded" @error="display.failed" />
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, defineAsyncComponent, onErrorCaptured, ref, watch } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { prepareDigitalPackagePreview, type DigitalPreviewResource } from '../utils/digitalPackagePreview';
const props=defineProps<{projectId:string;planId:string;resource:DigitalPreviewResource}>();
const TilesModel=defineAsyncComponent(()=>import('./DigitalTilesModel.vue'));
const CompressedModel=defineAsyncComponent(()=>import('./DigitalCompressedModel.vue'));
const Model=defineAsyncComponent(()=>import('vue-3d-loader').then(m=>m.vue3dLoader));
const entries=computed(()=>Object.keys(props.resource.content.packageFiles || {}).filter(p=>/\.(gltf|glb|json|tileset)$/i.test(p)).map(value=>({value,label:value})));
const entry=ref(''),loading=ref(false),error=ref(''),progress=ref('0');
const display=ref<{id:number;url:string;fileType:string;compressed:boolean;loaded:()=>void;failed:(reason:unknown)=>void}>();
let generation=0,abort:AbortController|undefined,release:(()=>void)|undefined,timer:ReturnType<typeof setTimeout>|undefined;
function clear(){++generation;display.value=undefined;abort?.abort();abort=undefined;release?.();release=undefined;clearTimeout(timer);timer=undefined;loading.value=false;error.value='';}
const {eligible}=useCurrentImageLifecycle(()=>[props.projectId,props.planId,props.resource],clear,()=>{});
watch(entries,list=>{if(!list.some(e=>e.value===entry.value))entry.value=list[0]?.value || '';},{immediate:true});
async function open(){
  if(!eligible.value || loading.value || display.value || !entry.value)return;
  clear();const ticket=generation;const controller=new AbortController();abort=controller;loading.value=true;
  const fail=(reason:unknown)=>{if(ticket!==generation)return;clear();error.value=reason instanceof Error?reason.message:'模型预览失败，请核对格式及依赖。';};
  try{
    const result=await prepareDigitalPackagePreview(props.projectId,props.planId,props.resource,entry.value,{
      signal:controller.signal,isCurrent:()=>ticket===generation && eligible.value,
      progress:(done,total)=>{if(ticket===generation)progress.value=`${done} / ${total}`;},
    });
    if(ticket!==generation){result.dispose();return;}
    release=result.dispose;
    timer=setTimeout(()=>fail(new Error('模型解析超时，请下载原件核对。')),60000);
    display.value={id:ticket,url:result.url,fileType:result.fileType,compressed:result.compressed,failed:fail,loaded:()=>{if(ticket===generation){clearTimeout(timer);timer=undefined;loading.value=false;}}};
  }catch(reason){fail(reason);}
}
onErrorCaptured(reason=>{display.value?.failed(reason);return false;});
</script>
<style scoped>.digital-package-preview{margin:12px 0}.scene{height:420px;overflow:hidden;border:1px solid #ddd}p{margin:8px 0}</style>
