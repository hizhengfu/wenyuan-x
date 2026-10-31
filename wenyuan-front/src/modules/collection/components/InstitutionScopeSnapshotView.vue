<template>
  <NDescriptions bordered :column="2">
    <NDescriptionsItem label="对象名称">{{ snapshot.name || '待核实' }}</NDescriptionsItem>
    <NDescriptionsItem label="原编号">{{ snapshot.recordNo || '未登记' }}</NDescriptionsItem>
    <NDescriptionsItem label="本条数量"
      >{{ snapshot.quantity ?? '待核实' }} {{ snapshot.unit || '' }}</NDescriptionsItem
    >
    <NDescriptionsItem label="记录状态">{{
      institutionItemStates[snapshot.state] || snapshot.state || '待核实'
    }}</NDescriptionsItem>
    <NDescriptionsItem label="记录位置">{{
      snapshot.location || snapshot.actual?.actualLocation || snapshot.warehouseId || '此来源未记载，需核实实际保管位置'
    }}</NDescriptionsItem>
    <NDescriptionsItem label="关联藏品">{{
      snapshot.originCollectionId || snapshot.linkedCollectionId || '此来源未记载'
    }}</NDescriptionsItem>
    <NDescriptionsItem v-if="snapshot.exitId" label="关联退出记录">{{ snapshot.exitId }}</NDescriptionsItem>
    <NDescriptionsItem v-if="snapshot.designatedReceptionId" label="关联指定接收">{{
      snapshot.designatedReceptionId
    }}</NDescriptionsItem>
    <NDescriptionsItem v-if="snapshot.receiptCount !== undefined" label="入馆核收原件条数"
      >{{ snapshot.receiptCount }}（须逐份核对是否实收）</NDescriptionsItem
    >
    <NDescriptionsItem v-if="snapshot.transferIds?.length" label="本机构事项逐件承接记录">{{
      snapshot.transferIds.join('、')
    }}</NDescriptionsItem>
    <NDescriptionsItem v-if="snapshot.documentNo" label="原指定文号">{{ snapshot.documentNo }}</NDescriptionsItem>
  </NDescriptions>
  <CollectionEvidenceReadView
    v-if="snapshot.actual"
    :fields="institutionScopeFields('unrecorded')"
    :data="snapshot.actual"
    :archive-files="archiveFiles" :original-url="originalUrl"
  />
</template>
<script setup lang="ts">
import type { CollectionReadonlyFile } from '../utils/readonlyFiles';
import { institutionItemStates, institutionScopeFields } from '../utils/institutionScopeFields';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
defineProps<{ snapshot: Record<string, any>; originalUrl?: (fileId: string) => string; archiveFiles?:CollectionReadonlyFile[] }>();
</script>
