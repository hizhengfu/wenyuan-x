<template>
  <HPageForm ref="pageRef" v-model:show="show" :title="title" :model="form" :rules="rules" label-width="170">
    <slot name="context" />
    <CollectionFeedbackItem v-for="field in fields" :key="field.key" :label="field.label" :hint="field.hint" :path="field.key" :required="field.required">
      <CollectionEvidenceRecordsInput v-if="field.type==='records'" v-model:value="form[field.key]" :fields="field.children || []" :original-url="originalUrl" />
      <CollectionSelect v-else-if="field.type==='collection'" v-model:value="form[field.key]" clearable />
      <DigitalPackageFilesInput v-else-if="field.packageFilesKey" :key="`${field.key}-${generation}`"
        :value="form[field.key]" :package-files="form[field.packageFilesKey]" :active="show" :context-key="generation" :disabled="saving"
        @change="setPackage(field, $event)" @busy="uploading[field.key]=$event" />
      <CollectionEvidenceFilesInput v-else-if="field.type==='file' && field.multiple" v-model:value="form[field.key]" :original-url="originalUrl" />
      <HUploadInput v-else-if="field.type==='file'" v-model:value="form[field.key]" :original-url="originalUrl" @upload-finish="form[field.key]=$event.id" clearable />
      <UserSelect v-else-if="field.type==='user'" v-model:value="form[field.key]" :multiple="field.multiple" clearable />
      <GroupSelect v-else-if="field.type==='group'" v-model:value="form[field.key]" />
      <NDatePicker v-else-if="field.type==='datetime'" v-model:value="form[field.key]" type="datetime" clearable />
      <NDatePicker v-else-if="field.type==='date'" v-model:value="form[field.key]" type="date" clearable />
      <NSelect v-else-if="field.type==='select'" v-model:value="form[field.key]" :options="field.options" :multiple="field.multiple" :filterable="field.filterable" clearable />
      <NInputNumber v-else-if="field.type==='number'" :value="field.unit==='yuan'?centsToYuan(form[field.key]):form[field.key]" @update:value="form[field.key]=field.unit==='yuan'?yuanFormToCents($event,form[field.key]):$event" :min="field.min ?? 0" :max="field.unit==='yuan'?Math.floor(Number.MAX_SAFE_INTEGER/100):(field.max ?? Number.MAX_SAFE_INTEGER)" :precision="field.unit==='yuan'?yuanInputPrecision(form[field.key]):0" clearable />
      <NInput v-else v-model:value="form[field.key]" :type="field.type==='textarea'?'textarea':'text'" :placeholder="field.label" />
    </CollectionFeedbackItem>
    <template #form-action><NButton type="primary" class='mr-3' :loading="saving" :disabled="hasUploads" @click="submit">核对原件并登记</NButton></template>
  </HPageForm>
</template>
<script setup lang="ts">
import CollectionFeedbackItem from './CollectionFeedbackItem.vue';
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue';
const DigitalPackageFilesInput=defineAsyncComponent(()=>import('./DigitalPackageFilesInput.vue'));
import type { DigitalPackageResult } from '../utils/digitalPackageUpload';
import CollectionEvidenceFilesInput from './CollectionEvidenceFilesInput.vue';
import CollectionEvidenceRecordsInput from './CollectionEvidenceRecordsInput.vue';
import CollectionSelect from './CollectionSelect.vue';
import { cloneDeep, isEqual } from 'lodash-es';
import type { FormRules } from 'naive-ui';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import UserSelect from '@/modules/sys/components/form/field/UserSelect.vue';
import type { EvidenceField } from '../utils/evidenceFields';
import { centsToYuan, yuanFormToCents, yuanInputPrecision } from '../utils/moneyYuan';
const props=withDefaults(defineProps<{title?:string;fields:EvidenceField[];save:(value:Record<string,any>)=>Promise<unknown>;originalUrl?:(fileId:string)=>string|undefined}>(),{title:'核对原件并登记'});
const emit=defineEmits<{(e:'saved'):void;(e:'change',value:Record<string,any>):void}>();
const show=ref(false), saving=ref(false), pageRef=ref<any>();
const form=ref<Record<string,any>>({});
watch(form,value=>emit('change',cloneDeep(value)),{deep:true});
const rules=computed<FormRules>(() => Object.fromEntries(props.fields.filter(f=>f.required || f.type==='records').map(f=>[f.key,{required:!!f.required,type:f.multiple?'array':(f.type==='date'||f.type==='datetime'||f.type==='number')?'number':'string',message:`请填写${f.label}`,trigger:['blur','change'],...(f.type==='records'?{validator:(_rule:any,value:Record<string,any>[]|undefined)=>{
  if(!value?.length)return f.required?new Error(`请填写${f.label}`):true;
  for(const [index,row] of value.entries())for(const child of f.children || [])if(child.required && (row[child.key]===undefined || row[child.key]===null || row[child.key]==='' || (Array.isArray(row[child.key]) && row[child.key].length===0)))return new Error(`请填写第${index+1}项的${child.label}`);
  return true;
}}:{})}])));
const generation=ref(0), uploading=ref<Record<string,boolean>>({});
const hasUploads=computed(()=>Object.values(uploading.value).some(Boolean));
function setPackage(field:EvidenceField, result:DigitalPackageResult) {
  if(!show.value || saving.value || !field.packageFilesKey)return;
  form.value={...form.value,[field.key]:result.fileIds,[field.packageFilesKey]:result.packageFiles};
}
function open(initial:Record<string,any>={}) { ++generation.value; saving.value=false; form.value=cloneDeep(initial); show.value=true; }
watch(show,value=>{if(!value){++generation.value;saving.value=false;}},{flush:'sync'});
onBeforeUnmount(()=>{++generation.value;});
async function submit() {
  if(saving.value || hasUploads.value || !show.value)return;
  const current=generation.value, value=cloneDeep(form.value), save=props.save;
  saving.value=true;
  try {
    await pageRef.value?.validateForm();
    if(current!==generation.value || hasUploads.value || !show.value || !isEqual(value,form.value))return;
    const result=await save(value);
    if(current!==generation.value || !show.value || result===false)return;
    show.value=false; emit('saved');
  } catch(error) { if(current===generation.value && show.value)throw error; }
  finally { if(current===generation.value)saving.value=false; }
}
defineExpose({open,close:()=>{show.value=false;},isOpen:()=>show.value});
</script>
