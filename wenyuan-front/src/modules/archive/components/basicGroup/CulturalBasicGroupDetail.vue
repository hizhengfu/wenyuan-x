<template>
  <NDescriptions bordered :column="2" label-placement="left" size="small">
    <NDescriptionsItem label="公布名称">{{ display(data.name) }}</NDescriptionsItem>
    <NDescriptionsItem label="公布编号">{{ display(data.code) }}</NDescriptionsItem>
    <NDescriptionsItem label="公布级别">
      <HDictText :value="data.publishLevel" type="four_cultural_relic_level" />
    </NDescriptionsItem>
    <NDescriptionsItem label="公布类别">
      <HDictText :value="data.publishCategory" type="four_cultural_relic_type" />
    </NDescriptionsItem>
    <NDescriptionsItem label="公布批次">
      <HDictText :value="data.publishBatch" type="basic_public_batch" />
    </NDescriptionsItem>
    <NDescriptionsItem label="所属地区">{{ display(data.areaName || data.regionName) }}</NDescriptionsItem>
    <NDescriptionsItem label="所属机构">{{ display(data.groupName || data.groupId) }}</NDescriptionsItem>
    <NDescriptionsItem label="关联数">{{ Number(data.relationCount || 0) || '' }}</NDescriptionsItem>
    <NDescriptionsItem label="标签" :span="2">{{ display(data.tag) }}</NDescriptionsItem>
    <NDescriptionsItem label="备注" :span="2">{{ display(data.remarks) }}</NDescriptionsItem>
  </NDescriptions>
</template>

<script lang="ts" setup>
defineProps<{ data: Record<string, any> }>()

function display(value: unknown) {
  return String(value ?? '').trim() || '--'
}
</script>
