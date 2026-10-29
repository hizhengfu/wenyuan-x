<template>
 <NSpace vertical :size="18">
  <NAlert type="warning">征集估价和谈判资料保密，请仅用于获授权的征集调查、审核与管理。</NAlert>
  <template v-if="dossier"><NText strong>入藏标准、来源调查及回避</NText><CollectionEvidenceReadView :fields="acquisitionDossierFields(source==='PURCHASE')" :data="dossier" :archive-files="stageFiles('PROPOSAL')" :original-url="urlFor('PROPOSAL')" /></template>
  <NCollapse v-if="experts?.length"><NCollapseItem v-for="expert in experts" :key="expert.id || expert.expertKey" :name="expert.id || expert.expertKey" :title="`${expert.expertName} · ${expert.agree?'同意征集':'不同意征集，终止程序'}`"><CollectionEvidenceReadView :fields="acquisitionExpertFields(source==='PURCHASE')" :data="expert" :archive-files="stageFiles(expert.id ? `EXPERT_${expert.id}` : '')" :original-url="urlFor(expert.id ? `EXPERT_${expert.id}` : '')" /></NCollapseItem></NCollapse>
  <template v-if="completion"><NText strong>估价、集体决策、协议及实际取得</NText><CollectionEvidenceReadView :fields="acquisitionCompletionFields(source)" :data="completion" :archive-files="stageFiles('AGREEMENT')" :original-url="urlFor('AGREEMENT')" /></template>
 </NSpace>
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
import {acquisitionDossierFields,acquisitionExpertFields,acquisitionCompletionFields} from '../utils/acquisitionEvidenceFields';
const props=defineProps<{source?:string;dossier?:Record<string,any>;experts?:Record<string,any>[];completion?:Record<string,any>;archives?:{phase:string;files?:CollectionReadonlyFile[]}[];originalUrl?:(phase:string,fileId:string)=>string}>();
const stageFiles=(phase:string)=>(props.archives || []).filter(archive=>archive.phase===phase).flatMap(archive=>archive.files || []);
const urlFor=(phase:string)=>props.originalUrl ? (fileId:string)=>phase ? props.originalUrl?.(phase,fileId) || '' : '' : undefined;
</script>
