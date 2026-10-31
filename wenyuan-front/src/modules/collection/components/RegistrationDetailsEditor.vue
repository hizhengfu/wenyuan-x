<template>
  <NSpace vertical :size="16">
    <NAlert type="info">尺寸与质量须明确单位；无法测量时记录具体原因。成套文物大小不同的分件逐件测量；附件质量另列。流传、考古和著录可多次记录，并注明依据。</NAlert>
    <NGrid :cols="cols" :x-gap="16">
      <NFormItemGi label="鉴定机构"><NInput v-model:value="model.appraisalInstitution" /></NFormItemGi>
      <NFormItemGi label="鉴定人员"><NInput v-model:value="model.appraisers" /></NFormItemGi>
      <NFormItemGi label="鉴定日期"><NDatePicker v-model:value="model.appraisedOn" type="date" clearable /></NFormItemGi>
      <NFormItemGi label="正面照片" :span="cols"><NCheckbox v-model:checked="model.coverIsFrontView">已核对封面为实物正面照片，附件同框或另有对应照片</NCheckbox></NFormItemGi>
      <NFormItemGi v-if="nature !== 'OTHER'" label="质量口径" :span="cols"><NCheckbox v-model:checked="model.weightExcludesAccessories">所填质量不含附件；同号文物已逐件称重后合计</NCheckbox></NFormItemGi>
      <NFormItemGi label="无法测量尺寸原因"><NInput v-model:value="model.dimensionUnknownReason" type="textarea" /></NFormItemGi>
      <NFormItemGi label="纸质总账册号"><NInput v-model:value="model.paperLedgerBookNo" /></NFormItemGi>
      <NFormItemGi label="纸质总账页码"><NInput v-model:value="model.paperLedgerPage" /></NFormItemGi>
      <NFormItemGi label="无法称重原因"><NInput v-model:value="model.weightUnknownReason" type="textarea" /></NFormItemGi>
      <NFormItemGi label="测量方法与依据"><NInput v-model:value="model.measurementMethod" type="textarea" /></NFormItemGi>
      <NFormItemGi label="测量人"><NInput v-model:value="model.measuredBy" /></NFormItemGi>
      <NFormItemGi label="测量日期"><NDatePicker v-model:value="model.measuredOn" type="date" clearable /></NFormItemGi>
    </NGrid>
    <NCollapse>
      <NCollapseItem title="分件测量" name="parts">
        <NAlert type="info" :bordered="false" style="margin-bottom:12px">分件号隶属于本总登记号，仅用于组套内部著录和影像关联，不另计新增藏品。登记件套数与整套实际数量仍分别填写。</NAlert>
        <NCard v-for="(part, index) in model.parts" :key="index" :title="`分件 ${index + 1}`" style="margin-bottom: 12px">
          <NGrid :cols="cols" :x-gap="12">
            <NFormItemGi label="分件号"><NInput v-model:value="part.partNo" /></NFormItemGi><NFormItemGi label="分件名称"><NInput v-model:value="part.name" /></NFormItemGi>
            <template v-if="part.dimensions"><NFormItemGi v-for="(label, key) in lengths" :key="key" :label="label"><NInput v-model:value="part.dimensions[key]" /></NFormItemGi></template>
            <NFormItemGi label="尺寸单位"><NSelect v-model:value="part.dimensionUnit" :options="lengthUnits" clearable /></NFormItemGi>
            <NFormItemGi label="质量数值"><NInput v-model:value="part.weight" /></NFormItemGi><NFormItemGi label="质量单位"><NSelect v-model:value="part.weightUnit" :options="massUnits" clearable /></NFormItemGi>
            <NFormItemGi label="分件无法测量原因"><NInput v-model:value="part.dimensionUnknownReason" /></NFormItemGi>
            <NFormItemGi label="分件无法称重原因"><NInput v-model:value="part.weightUnknownReason" /></NFormItemGi>
            <NFormItemGi label="排除附件"><NCheckbox :checked="part.includesAccessories === false" @update:checked="part.includesAccessories = $event ? false : undefined">已排除附件质量</NCheckbox></NFormItemGi>
            <NFormItemGi label="说明"><NInput v-model:value="part.remark" type="textarea" /></NFormItemGi>
          </NGrid><NButton type="error" @click="model.parts!.splice(index, 1)">移除此分件测量</NButton>
        </NCard>
        <NButton @click="(model.parts ||= []).push({ dimensions: {} })">添加分件测量</NButton>
      </NCollapseItem>
      <NCollapseItem v-for="section in sections" :key="section.key" :title="section.title" :name="section.key">
        <NCard v-for="(record, index) in records(section.key)" :key="index" :title="`${section.title} ${index + 1}`" style="margin-bottom: 12px">
          <WarehouseRecordFields :model-value="record" @update:model-value="records(section.key)[index] = $event" :fields="section.fields" />
          <NButton type="error" @click="records(section.key).splice(index, 1)">移除此条</NButton>
        </NCard>
        <NButton @click="addRecord(section.key)">添加{{ section.title }}</NButton>
      </NCollapseItem>
    </NCollapse>
  </NSpace>
</template>
<script setup lang="ts">
import WarehouseRecordFields from './WarehouseRecordFields.vue';
import type { CollectionRegistrationDetailsVo } from '../typings/collection';
import type { EvidenceField } from '../utils/evidenceFields';
withDefaults(defineProps<{ cols?: number; nature?: string | null }>(), { cols: 2 });
const model = defineModel<CollectionRegistrationDetailsVo>({ required: true });
const options = (rows: [string, string][]) => rows.map(([value, label]) => ({ value, label }));
const lengthUnits = options([['mm', 'mm'], ['cm', 'cm'], ['m', 'm']]), massUnits = options([['g', 'g'], ['kg', 'kg']]);
const lengths = { height: '通高', length: '长 / 纵', width: '宽 / 横', diameter: '直径', mouth: '口径', bottom: '底径', belly: '腹径' };
type RecordKey = 'provenance' | 'excavations' | 'publications' | 'manufacture';
const f = (key: string, label: string, type: EvidenceField['type'] = 'text'): EvidenceField => ({ key, label, type });
const sections: { key: RecordKey; title: string; fields: EvidenceField[] }[] = [
  { key: 'provenance', title: '流传经历', fields: [f('from','开始日期','date'),f('to','结束日期','date'),f('periodDescription','年代或时间范围'),f('institution','收藏机构'),f('collector','收藏者'),f('originalNumber','当时编号'),f('evidence','依据及相关业务或原件号','textarea'),f('remark','说明','textarea')] },
  { key: 'excavations', title: '考古信息', fields: [f('date','发现或发掘日期','date'),f('periodDescription','时间范围'),f('site','地点'),f('position','探方层位等具体位置'),f('institution','发掘机构'),f('evidence','报告与原件依据','textarea')] },
  { key: 'publications', title: '著录文献', fields: [f('publishedOn','出版日期','date'),f('periodDescription','出版时间范围'),f('publisher','出版单位'),f('title','书刊名称'),f('author','著者'),f('chapter','章节'),f('pages','页码'),f('summary','著录内容','textarea'),f('remark','说明','textarea')] },
  { key: 'manufacture', title: '生产制造', fields: [f('period','时间'),f('place','地点'),f('technique','工艺'),f('institution','机构'),f('maker','制造者'),f('remark','依据及说明','textarea')] },
];
function records(key: RecordKey): Record<string, any>[] { return model.value[key] || []; }
function addRecord(key: RecordKey) { (model.value[key] ||= []).push({}); }
</script>
