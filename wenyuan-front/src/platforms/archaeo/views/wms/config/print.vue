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
      <n-form-item label="名称" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="IP" path="IP">
        <n-input v-model:value="paramsForm.ip" placeholder="请输入IP" clearable></n-input>
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
      <n-form-item label="名称" path="title">
        <n-input v-model:value="form.title" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-flex>
        <n-form-item label="IP地址" path="ip" style="min-width: 300px">
          <n-input v-model:value="form.ip" placeholder="请输入IP地址" clearable></n-input>
        </n-form-item>
        <n-form-item label="端口" path="port">
          <n-input-number
            :show-button="false"
            v-model:value="form.port"
            placeholder="请输入端口"
            clearable
          ></n-input-number>
        </n-form-item>
      </n-flex>
      <n-form-item label="模版" path="templates">
        <n-input type="textarea" v-model:value="form.templates"></n-input>
      </n-form-item>
      <n-form-item label="启用状态" path="enabled">
        <n-switch v-model:value="form.enabled"></n-switch>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/printClient';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  { key: 'title', title: '名称', columnType: 'title', resizable: true, ellipsis: true, minWidth: '100px' },
  { key: 'ip', title: 'IP地址', resizable: true, ellipsis: true, width: '200px' },
  { key: 'port', title: '端口', width: '100px' },
  { key: 'enabled', title: '启用状态', dictType: 'sys_ok', width: '100px', align: 'center' },
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
  title: '',
  ip: '127.0.0.1',
  port: 9012,
  templates: '',
  enabled: true,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入名称',
  },
  ip: {
    required: true,
    message: '请输入IP地址',
  },
  port: {
    required: true,
    message: '请输入端口',
  },
  templates: {
    required: true,
    message: '请输入模版',
  },
  enabled: {
    required: true,
    message: '请输入启用状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
  ip: '',
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
    formModel.value = true;
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
