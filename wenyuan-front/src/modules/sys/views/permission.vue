<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :scroll-x="1200"
    isTree
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="权限名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="权限名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="权限代码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="权限代码" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属应用" path="applicationId">
        <h-application-select
          v-model:value="paramsForm.applicationId"
          placeholder="请选择所属应用"
          clearable
        ></h-application-select>
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
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="父级" path="parentId">
        <h-permission-select
          v-model:value="form.parentId"
          filterable
          clearable
          placeholder="请选择父级权限"
        ></h-permission-select>
      </n-form-item>
      <n-form-item label="所属应用" path="applicationId">
        <h-application-select v-model:value="form.applicationId" placeholder="请选择所属应用"></h-application-select>
      </n-form-item>
      <n-form-item label="代码" path="code">
        <n-input v-model:value="form.code" placeholder="请输入代码"></n-input>
      </n-form-item>
      <n-form-item label="权限模块" path="moduleCode">
        <n-input
          v-model:value="form.moduleCode"
          :disabled="formType === 'view' || form.isSys"
          placeholder="请输入稳定的模块代码"
        ></n-input>
        <div v-if="form.isSys" class="mt-4px text-12px text-gray-500">系统权限的模块由代码声明维护</div>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HApplicationSelect from '../components/form/field/ApplicationSelect.vue';
import HPermissionSelect from '../components/form/field/PermissionSelect.vue';
import { add, del, edit, list } from '../api/permission';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  { key: 'name', title: '名称', columnType: 'title', width: '320px' },
  { key: 'code', title: '代码', minWidth: '200px', ellipsis: { tooltip: true } },
  { key: 'moduleCode', title: '权限模块', width: '200px', ellipsis: { tooltip: true } },
  { key: 'application.name', title: '所属应用', width: '220px' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref(null);

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  parentId: '',
  applicationId: '1',
  name: '',
  code: '',
  moduleCode: '',
  isSys: false,
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  applicationId: {
    required: true,
    message: '请选择应用',
  },
  code: {
    required: true,
    message: '请输入代码',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
  applicationId: '',
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
