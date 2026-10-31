<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="应用名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="应用名称" clearable></n-input>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-grid x-gap="10">
        <n-form-item-gi :span="12" label="名称" path="name">
          <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="所属机构" path="groupId">
          <h-group-select v-model:value="form.groupId" placeholder="请选择机构"></h-group-select>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="首页" path="url">
          <n-input v-model:value="form.url" placeholder="请输入首页"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="根路径" path="path">
          <n-input v-model:value="form.path" placeholder="请输入根路径"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="供应商" path="supplier">
          <n-input v-model:value="form.supplier" placeholder="请输入供应商"></n-input>
        </n-form-item-gi>
        <n-grid-item :span="12">
          <n-flex>
            <n-form-item label="启用" path="enabled">
              <n-switch v-model:value="form.enabled"></n-switch>
            </n-form-item>
            <n-form-item label="外部应用" path="external">
              <n-switch v-model:value="form.external"></n-switch>
            </n-form-item>
          </n-flex>
        </n-grid-item>
      </n-grid>
      <n-form-item label="appId" path="code">
        <n-input v-model:value="form.code" placeholder="请输入应用编码"></n-input>
      </n-form-item>
      <n-form-item label="appSecret" path="privateKey">
        <n-input readonly v-model:value="form.privateKey" type="textarea" placeholder="请输入应用密钥"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HGroupSelect from '../components/form/field/GroupSelect.vue';
import { add, del, edit, list, resetAppSecret } from '../api/application';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NIcon } from 'naive-ui';
import Icon from '@/components/basic/DynamicIcon.vue';
import { confirm } from '@/utils';

const columns = [
  { key: 'name', title: '名称', columnType: 'title', resizable: true, ellipsis: true, width: '320px' },
  { key: 'code', title: '应用编码', resizable: true, ellipsis: true, width: '200px' },
  { key: 'url', title: '网址', resizable: true, ellipsis: true, width: '300px' },
  {
    key: 'appSecret',
    title: '重置密钥',
    align: 'center',
    width: '120px',
    render(row: any) {
      return h(
        NIcon,
        {
          size: '20px',
          onClick() {
            confirm({
              type: 'warning',
              title: '请注意',
              content: '点击确认后将重置应用密钥，确认成功后已经分发的帐户信息将无效，请谨慎使用。',
              onPositiveClick: () => {
                resetAppSecret(row.id);
              },
            });
          },
        },
        { default: () => h(Icon, { icon: 'c:RestartAlert' }) },
      );
    },
  },
  { key: 'external', title: '外部应用', dictType: 'sys_ok', align: 'center', width: '120px' },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', align: 'center', width: '120px' },
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
  name: '',
  code: '',
  url: '',
  path: '',
  groupId: '',
  publicKey: '',
  privateKey: '',
  supplier: '',
  sort: 1,
  external: false,
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
  name: {
    required: true,
    message: '请输入名称',
  },
  groupId: {
    required: true,
    message: '请选择机构',
  },
  code: {
    required: true,
    message: '请输入代码',
  },
  enabled: {
    required: true,
    message: '请选择状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
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
