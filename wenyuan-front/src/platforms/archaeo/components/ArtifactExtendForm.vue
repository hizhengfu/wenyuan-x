<template>
  <n-form-item label="时代" path="timePeriod">
    <HDictTreeSelect
      v-model:value="form.timePeriod"
      type="wms_artifact_year"
      placeholder="请选择时代"
      clearable
      filterable
    ></HDictTreeSelect>
  </n-form-item>
  <n-form-item label="文化因素" path="culturalAttribute">
    <HDictSelect
      v-model:value="form.culturalAttribute"
      type="wms_artifact_cultural_attribute"
      placeholder="请选择文化因素"
      clearable
      filterable
      showAdd
    ></HDictSelect>
  </n-form-item>
  <n-form-item label="形制" path="shape">
    <n-input type="textarea" rows="2" v-model:value="form.shape" placeholder="请输入形制" clearable></n-input>
  </n-form-item>
  <n-form-item label="纹饰" path="decoration">
    <n-input type="textarea" rows="2" v-model:value="form.decoration" placeholder="请输入纹饰" clearable></n-input>
  </n-form-item>
  <n-form-item label="用途" path="purpose">
    <n-input type="textarea" rows="2" v-model:value="form.purpose" placeholder="请输入用途" clearable></n-input>
  </n-form-item>
  <n-grid x-gap="15" cols="3">
    <n-grid-item>
      <n-form-item label="器型" path="artifactType">
        <n-input v-model:value="form.artifactType" placeholder="请输入器型" clearable></n-input>
      </n-form-item>
    </n-grid-item>
    <n-grid-item>
      <n-form-item label="型" path="artifactStyle">
        <n-input v-model:value="form.artifactStyle" placeholder="如：Ⅰ型" clearable></n-input>
      </n-form-item>
    </n-grid-item>
    <n-grid-item>
      <n-form-item label="亚型" path="artifactSubStyle">
        <n-input v-model:value="form.artifactSubStyle" placeholder="如：A亚型" clearable></n-input>
      </n-form-item>
    </n-grid-item>
  </n-grid>
  <n-grid x-gap="15" cols="3">
    <n-grid-item>
      <n-form-item label="式" path="artifactStage">
        <n-input v-model:value="form.artifactStage" placeholder="如：1式" clearable></n-input>
      </n-form-item>
    </n-grid-item>
    <n-grid-item>
      <n-form-item label="期" path="phaseName">
        <n-input v-model:value="form.phaseName" placeholder="请输入期" clearable></n-input>
      </n-form-item>
    </n-grid-item>
    <n-grid-item>
      <n-form-item label="段" path="sectionName">
        <n-input v-model:value="form.sectionName" placeholder="请输入段" clearable></n-input>
      </n-form-item>
    </n-grid-item>
  </n-grid>
  <n-form-item label="制作工艺" path="craftingTechnique">
    <n-input type="textarea" v-model:value="form.craftingTechnique" placeholder="请输入制作工艺" clearable></n-input>
  </n-form-item>
  <n-grid x-gap="15">
    <n-grid-item span="12">
      <n-form-item label="尺寸" path="dimensions">
        <n-input-group>
          <n-input v-model:value="form.dimensions" placeholder="请输入长×宽×高" clearable></n-input>
          <n-input-group-label>CM</n-input-group-label>
        </n-input-group>
      </n-form-item>
    </n-grid-item>
    <n-grid-item span="12">
      <n-form-item label="请输入重量" path="weight">
        <n-input-group>
          <n-input-number min="0" v-model:value="form.weight" placeholder="重量" clearable></n-input-number>
          <n-input-group-label>KG</n-input-group-label>
        </n-input-group>
      </n-form-item>
    </n-grid-item>
  </n-grid>
<!--  <n-form-item label="文物线图" v-if="form.artifactId" path="lineDiagram">-->
<!--    <HUpload :contentId="'lineDiagram' + form.artifactId" label="上传文物线图"></HUpload>-->
<!--  </n-form-item>-->
</template>
<script lang="ts" setup>
const props = defineProps({
  form: { type: Object, required: true },
});
</script>
