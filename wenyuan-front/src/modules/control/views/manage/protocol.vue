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
      <n-form-item label="协议名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入协议名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="协议类型" path="type">
        <h-dict-select
          type="cc_protocol_type"
          v-model:value="paramsForm.type"
          placeholder="请选择规约类型"
          clearable
        ></h-dict-select>
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
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="类型" path="type">
        <h-dict-select
          type="cc_protocol_type"
          v-model:value="form.type"
          placeholder="请选择规约类型"
          clearable
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="厂家名称" path="manufacturer">
        <n-input v-model:value="form.manufacturer" placeholder="请输入厂家名称"></n-input>
      </n-form-item>
      <n-form-item label="描述信息" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del } from '../../api/protocol';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'id',
    title: 'ID',
    fixed: 'left',
    width: '120px',
    resizable: true,
  },
  {
    key: 'type',
    title: '协议类型',
    dictType: 'cc_protocol_type',
    width: '220px',
    resizable: true,
  },
  {
    key: 'name',
    title: '协议名称',
    columnType: 'title',
    width: '220px',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  { key: 'manufacturer', title: '厂家名称' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
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
  type: '',
  name: '',
  manufacturer: '',
  content: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  type: {
    required: true,
    message: '请输入协议类型 0 主控 1 设备',
  },
  name: {
    required: true,
    message: '请输入名称',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  type: '',
  name: '',
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
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
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
