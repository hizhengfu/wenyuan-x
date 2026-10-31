<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="800"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="分类名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入分类名称" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :refTable="refPageTable"
      :handleSubmit="handleSubmit"
      v-model:show="formModel"
    >
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入分类名称"></n-input>
      </n-form-item>
      <n-form-item label="排序" path="sort">
        <n-input-number v-model:value="form.sort" :min="0" placeholder="数值越小越靠前"></n-input-number>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, list } from '../api/tagCategory';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '名称',
    columnType: 'title',
    width: '240px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'sort', title: '排序', width: '100px', align: 'right' },
  { key: 'createTime', title: '创建时间', columnType: 'dateTime', width: '220px', align: 'center' },
];

const formModel = ref(false);
const refPageTable = ref(null);
const formTitle = ref();

const defaultForm = {
  id: '',
  name: '',
  sort: 0,
};

const form = ref(cloneDeep(defaultForm));

const rules = {
  name: {
    required: true,
    message: '请输入分类名称',
  },
};

const paramsForm = ref({
  name: '',
});

const formType = ref('add');

function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加分类';
  formModel.value = true;
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.name}」`;
  formModel.value = true;
}

function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.name}」`;
  formModel.value = true;
}

function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}
</script>
