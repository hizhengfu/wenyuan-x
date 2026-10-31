<template>
  <NSpace vertical :size="16">
    <NDescriptions bordered :column="2" label-placement="left">
      <NDescriptionsItem v-for="(label, key) in scalarLabels" :key="key" :label="label">{{ format(key, details?.[key]) }}</NDescriptionsItem>
    </NDescriptions>
    <section v-for="section in sections" :key="section.key">
      <h3>{{ section.title }}</h3>
      <NDescriptions v-for="(record, index) in details?.[section.key] || []" :key="index" bordered :column="2" label-placement="left" style="margin-bottom: 12px">
        <NDescriptionsItem v-for="(label, key) in section.fields" :key="key" :label="label">{{ format(key, (record as Record<string, unknown>)[key]) }}</NDescriptionsItem>
      </NDescriptions>
      <NText v-if="!details?.[section.key]?.length" depth="3">本版本未记录</NText>
    </section>
  </NSpace>
</template>
<script setup lang="ts">
import { formatLocalDateValue } from '@/utils';
import type { CollectionRegistrationDetailsVo } from '../typings/collection';
defineProps<{ details?: CollectionRegistrationDetailsVo }>();
const scalarLabels = { paperLedgerBookNo: '纸质总账册号', paperLedgerPage: '纸质总账页码', coverIsFrontView: '正面照片已核对', eraMeaning: '年代类型', eraNotation: '年代表示方式', dimensionUnknownReason: '无法测量尺寸原因', weightUnknownReason: '无法称重原因', quantityUnknownReason: '数量待核原因', weightExcludesAccessories: '质量不含附件', measurementMethod: '测量方法', measuredOn: '测量日期', measuredBy: '测量人', appraisalInstitution: '鉴定机构', appraisers: '鉴定人员', appraisedOn: '鉴定日期' } satisfies Partial<Record<keyof CollectionRegistrationDetailsVo, string>>;
const sections: { key: 'parts' | 'provenance' | 'excavations' | 'publications' | 'manufacture'; title: string; fields: Record<string,string> }[] = [
  { key:'parts', title:'分件测量',fields:{partNo:'分件号',name:'名称',dimensions:'尺寸',dimensionUnit:'尺寸单位',weight:'质量',weightUnit:'质量单位',includesAccessories:'质量包含附件',dimensionUnknownReason:'无法测量原因',weightUnknownReason:'无法称重原因',remark:'说明'} },
  { key:'provenance', title:'流传经历',fields:{from:'开始日期',to:'结束日期',periodDescription:'时间范围',institution:'收藏机构',collector:'收藏者',originalNumber:'当时编号',evidence:'依据及原件号',remark:'说明'} },
  { key:'excavations', title:'考古信息',fields:{date:'发掘日期',periodDescription:'时间范围',site:'地点',position:'具体位置',institution:'发掘机构',evidence:'原件依据'} },
  { key:'publications', title:'著录文献',fields:{publishedOn:'出版日期',periodDescription:'时间范围',publisher:'出版单位',title:'书刊名称',author:'著者',chapter:'章节',pages:'页码',summary:'著录内容',remark:'说明'} },
  { key:'manufacture', title:'生产制造',fields:{period:'时间',place:'地点',technique:'工艺',institution:'机构',maker:'制造者',remark:'依据及说明'} },
];
const enumLabels: Record<string,string> = { MANUFACTURED:'制造年代',USED:'使用年代',FORMED:'形成年代',LIVED:'生存年代',GREGORIAN:'公历纪年',GEOLOGICAL:'地质年代',ARCHAEOLOGICAL:'考古学年代',CHINESE_HISTORY:'中国历史学年代',REGNAL:'帝王纪年',OTHER:'其他纪年' };
const dimensionLabels: Record<string,string> = {height:'通高',mouth:'口径',bottom:'底径',belly:'腹径',length:'长',width:'宽',diameter:'直径'};
function format(key: string, value: unknown): string {
  if(value == null || value === '') return '未记录';
  if(typeof value === 'boolean') return value ? '是' : '否';
  if(['from','to','date','publishedOn','measuredOn','appraisedOn'].includes(key)) return formatLocalDateValue(value as number);
  if(key === 'dimensions' && typeof value === 'object') return Object.entries(value).map(([k,v]) => `${dimensionLabels[k] || k}：${v}`).join('；');
  return enumLabels[String(value)] || String(value);
}
</script>
