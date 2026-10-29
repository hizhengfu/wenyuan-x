<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :refTable="refPageTable"
      :disabled="formType === 'view'"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="编号" path="no">
            <n-input v-model:value="form.no" placeholder="请输入编号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
        </n-gi>
       
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="年代" path="years">
            <n-input v-model:value="form.years" placeholder="请输入年代" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="质地" path="texture">
            <n-input v-model:value="form.texture" placeholder="请输入质地" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi span="12">
          <n-form-item label="照片编号" path="zpNo">
            <n-input v-model:value="form.zpNo" placeholder="请输入照片编号" clearable></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="保存地点" path="address">
            <n-input v-model:value="form.address" placeholder="请输入保存地点" clearable></n-input>
          </n-form-item>
        </n-gi>
      </n-grid>

      <n-form-item label="备注" path="remark">
        <n-input type="textarea" v-model:value="form.remark" placeholder="请输入备注" clearable></n-input>
      </n-form-item>
      <h-upload :contentId="form.id" label="上传文件" :disabled="formType === 'view'"></h-upload>
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
      <td>编号</td>
      <td colspan="2">{{form.no || '--'}}</td>
      <td>名称</td>
      <td colspan="2">{{form.name || '--'}}</td>
      <td>年代</td>
      <td colspan="2">{{form.years || '--'}}</td>
    </tr>
    <tr>
      <td>质地</td>
      <td colspan="2">{{form.texture || '--'}}</td>
      <td>照片编号</td>
      <td colspan="2">{{form.zpNo || '--'}}</td>
      <td>保存地点</td>
      <td colspan="2">{{form.address || '--'}}</td>
    </tr>
    <tr>
      <td>备注</td>
      <td colspan="8">{{form.remark || '--'}}</td>
     
    </tr>
   </n-table>
  </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/fourth/fourthCulturalRelicSpecimen';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import {useRoute} from  'vue-router'
const route = useRoute()
const props = defineProps({
  fourthCulturalRecordId: {
    type: String,
    required: true,
  },
  title: {
    type: String,
  },
  culturalType: {
    type: Number,
  },
});
const columns = [
  { title: '序号', key: 'index', columnType: 'index', width: '80px' ,align:'center'},
  { key: 'no', title: '编号' },
  { key: 'name', title: '名称',  width: '200px', resizable: true, ellipsis: { tooltip: true } },

  { key: 'texture', title: '质地' },
  { key: 'years', title: '年代' },
  { key: 'zpNo', title: '照片编号' },
  { key: 'address', title: '保存地点', titleAlign: 'center', ellipsis: { tooltip: true } },
  { key: 'remark', title: '备注', titleAlign: 'center', ellipsis: { tooltip: true } },
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
  id:'',
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,
  name: '',
  no: '',
  texture: '',
  years: '',
  zpNo: '',
  address: '',
  remark: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  culturalRelicId: {
    required: true,
    message: '请输入cultural_relic_id',
  },
  name:{
    required:true,
    message:'请输入名称'
  },
  no:{
    required:true,
    message:'请输入编号'
  }
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  culturalRelicId: route.query.fourthCulturalRecordId || props.fourthCulturalRecordId,
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
