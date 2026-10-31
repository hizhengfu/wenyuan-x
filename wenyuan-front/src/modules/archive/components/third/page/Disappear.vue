<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1250" :params="paramsForm" @add="handleAdd"
    @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns">
    <template #queryForm v-if="props.queryShow">
      <!-- <n-form-item label="不可移动文物" path="culturalRelicId">
        <CulturalRelicSelect
          v-model:value="paramsForm.culturalRelicId"
          placeholder="请选择不可移动文物"
          clearable
        ></CulturalRelicSelect>
      </n-form-item> -->
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="类别" path="type">
        <h-dict-select v-model:value="paramsForm.type" type="third_cultural_relic_type"
          placeholder="请选择类别"></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
      :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="10">
        <n-gi span="8">
          <n-form-item label="序号" path="serialNumber">
            <n-input v-model:value="form.serialNumber" placeholder="请输入序号" clearable></n-input>
          </n-form-item>
          <n-form-item label="级别" path="level">
            <h-dict-select type="third_cultural_relic_level" v-model:value="form.level"
              placeholder="请选择级别"></h-dict-select>
          </n-form-item>
          <n-form-item label="消失时间" path="disappearTime">
            <n-date-picker clearable v-model:value="form.disappearTime" type="datetime" placeholder="请选择消失时间" />
          </n-form-item>
          <!-- <n-form-item label="原登记文件" path="culturalRelicId">
            <CulturalRelicSelect v-model:value="form.culturalRelicId" placeholder="请选择原登记文件" clearable>
            </CulturalRelicSelect>
          </n-form-item> -->
        </n-gi>
        <n-gi span="8">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="类别" path="type">
            <h-dict-select v-model:value="form.type" type="third_cultural_relic_type"
              placeholder="请选择类别"></h-dict-select>
          </n-form-item>
          <n-form-item label="调查时间" path="investigatorTime">
            <n-date-picker clearable v-model:value="form.investigatorTime" type="datetime" placeholder="请选择调查时间" />
          </n-form-item>
        </n-gi>
        <n-gi span="8">
          <n-form-item label="年代" path="year">
            <n-input v-model:value="form.year" placeholder="请输入年代" clearable></n-input>
          </n-form-item>
          <n-form-item label="地址及位置" path="address">
            <n-input v-model:value="form.address" placeholder="请输入保存地点" clearable></n-input>
          </n-form-item>
          <n-form-item label="调查人" path="investigator">
            <n-input v-model:value="form.investigator" placeholder="请输入调查人" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="消失原因" path="disappearReason">
        <n-input v-model:value="form.disappearReason" placeholder="请输入消失原因" clearable type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
     <h-page-form
      v-model:show="detailModel"
      class="project"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
   <n-table class="info-table" :single-line="false">
    <tr>
      <td>序号</td>
      <td colspan="2">{{form.serialNumber || '--'}}</td>
      <td>名称</td>
      <td colspan="2">{{form.name || '--'}}</td>
      <td>年代</td>
      <td colspan="2">{{form.year || '--'}}</td>
     
    </tr>
    <tr>
      <td>级别</td>
      <td colspan="2"><h-dict-text type="third_cultural_relic_level" v-model:value="form.level"></h-dict-text></td>
      <td>类别</td>
      <td colspan="2"><h-dict-text type="third_cultural_relic_type" v-model:value="form.type"></h-dict-text></td>
      <td>地址及位置</td>
      <td colspan="2">{{form.address || '--'}}</td>
    </tr>
    <tr>
      <td>消失时间</td>
      <td colspan="2">{{form.disappearTime ? dayjs(form.disappearTime).format('YYYY-MM-DD HH:mm:ss') : '--'}}</td>
      <td>调查时间</td>
      <td colspan="2">{{form.investigatorTime ? dayjs(form.investigatorTime).format('YYYY-MM-DD HH:mm:ss') : '--'}}</td>
      <td>调查人</td>
      <td colspan="2">{{form.investigator || '--'}}</td>
    </tr>
    <tr>
      <td>消失原因</td>
      <td colspan="8">{{form.disappearReason || '--'}}</td>
    </tr>
   
   </n-table>
  </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/third/thirdCulturalRelicDisappear';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import CulturalRelicSelect from '../CulturalRelicSelect.vue';
import dayjs from 'dayjs';

const props = defineProps({
  queryShow: {
    type: Boolean,
    default: true
  },
  thirdCulturalRecordId: { type: String, required: true }
});

const columns = [
  { key: 'serialNumber', title: '序号', titleAlign: 'center' },
  {
    key: 'name',
    title: '名称',
   
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    titleAlign: 'center',
  },
  { key: 'year', title: '年代', align: 'center' },
  { key: 'level', title: '级别', dictType: 'third_cultural_relic_level', align: 'center', ellipsis: { tooltip: true } },
  { key: 'type', title: '类别', dictType: 'third_cultural_relic_type', align: 'center', ellipsis: { tooltip: true } },
  { key: 'address', title: '地址及位置', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'disappearTime', title: '消失时间', columnType: 'date', align: 'center' },
  { key: 'culturalRelicThirdCulturalRelicBasic.name', title: '原先登记文件', align: 'center' },
  { key: 'investigator', title: '调查人', align: 'center', ellipsis: { tooltip: true } },
  { key: 'investigatorTime', title: '调查时间', columnType: 'date', align: 'center' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel =ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  culturalRelicId: props.thirdCulturalRecordId,
  serialNumber: '',
  name: '',
  year: '',
  level: '',
  type: '',
  address: '',
  disappearTime: null,
  disappearReason: '',
  investigator: '',
  investigatorTime: null,
  createTime: null,
  createBy: '',
  updateTime: null,
  updateBy: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {};

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  culturalRelicId: props.thirdCulturalRecordId,
});

const formType = ref('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}
</script>
