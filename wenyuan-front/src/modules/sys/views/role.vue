<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="角色名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="角色名称" clearable></n-input>
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
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="编码" path="code">
        <n-input v-model:value="form.code" placeholder="请输入编码"></n-input>
      </n-form-item>
      <n-form-item label="默认应用" path="applicationId">
        <h-application-select v-model:value="form.applicationId" placeholder="请选择应用"></h-application-select>
      </n-form-item>
      <n-form-item label="数据范围" path="dataScope">
        <n-select v-model:value="form.dataScope" :options="dataScopeOptions" placeholder="请选择数据范围" />
      </n-form-item>
      <n-form-item label="描述" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" placeholder="请输入描述"></n-input>
      </n-form-item>
      <n-form-item label="启用" path="enabled">
        <n-switch v-model:value="form.enabled"></n-switch>
      </n-form-item>
    </h-page-form>
    <HRoleAssociate
      v-model:show="roleVisibilityAssociate"
      :title="visibilityDialogTitle"
      :application-id="applicationId"
      :role-id="roleId"
    ></HRoleAssociate>
    <HRolePermissionCatalog
      v-model:show="rolePermissionCatalog"
      :title="permissionDialogTitle"
      :application-id="applicationId"
      :role-id="roleId"
    />
    <HRoleDataPermission
      v-model:show="roleDataPermissionDialog"
      :title="dataPermissionDialogTitle"
      :application-id="applicationId"
      :role-id="roleId"
    />
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HApplicationSelect from '../components/form/field/ApplicationSelect.vue';
import HRoleAssociate from '../components/form/roleAssociate.vue';
import HRolePermissionCatalog from '../components/form/rolePermissionCatalog.vue';
import HRoleDataPermission from '../components/form/roleDataPermission.vue';
import { add, del, edit, list } from '../api/role';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NSpace } from 'naive-ui';

const columns = [
  { key: 'name', title: '名称', columnType: 'title', fixed: 'left', width: '220px', resizable: true },
  { key: 'code', title: '编码', fixed: 'left', width: '180px', resizable: true, ellipsis: true },
  { key: 'remarks', title: '描述', minWidth: '220px', resizable: true, ellipsis: true },
  { key: 'application.name', title: '默认应用', width: '220px', resizable: true },
  {
    key: 'dataScope',
    title: '数据范围',
    width: '150px',
    render(row: any) {
      return dataScopeLabel(row.dataScope);
    },
  },
  {
    key: 'config',
    title: '配置',
    align: 'center',
    width: '310px',
    render(row: any) {
      return h(
        NSpace,
        { justify: 'center', wrap: false },
        {
          default: () => [
            h(
              NButton,
              { size: 'small', type: 'primary', secondary: true, onClick: () => handlePermissionConfig(row) },
              { default: () => '功能权限' },
            ),
            h(
              NButton,
              { size: 'small', type: 'warning', secondary: true, onClick: () => handleDataPermissionConfig(row) },
              { default: () => '数据权限' },
            ),
            h(NButton, { size: 'small', onClick: () => handleVisibilityConfig(row) }, { default: () => '界面可见性' }),
          ],
        },
      );
    },
  },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', align: 'center', width: '80px' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const rolePermissionCatalog = ref(false);
const roleDataPermissionDialog = ref(false);
const roleVisibilityAssociate = ref(false);
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
  remarks: '',
  applicationId: '',
  dataScope: 'SELF',
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
  code: {
    required: true,
    message: '请输入编码',
  },
  remarks: {
    required: true,
    message: '请选择类型',
  },
  applicationId: {
    required: true,
    message: '请选择应用',
  },
  dataScope: {
    required: true,
    message: '请选择数据范围',
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

const applicationId = ref('0');
const roleId = ref('');
const permissionDialogTitle = ref();
const dataPermissionDialogTitle = ref();
const visibilityDialogTitle = ref();

const dataScopeOptions = [
  { label: '所有数据', value: 'ALL' },
  { label: '机构及以下数据', value: 'GROUPS' },
  { label: '本机构数据', value: 'GROUP' },
  { label: '部门及以下数据', value: 'DEPARTMENTS' },
  { label: '本部门数据', value: 'DEPARTMENT' },
  { label: '本人数据', value: 'SELF' },
  { label: '无数据', value: 'NONE' },
];

function dataScopeLabel(value?: string) {
  return dataScopeOptions.find((option) => option.value === value)?.label || '未配置';
}

/**
 * 配置角色功能权限
 * */
function handlePermissionConfig(row: any) {
  applicationId.value = row.applicationId;
  roleId.value = row.id;
  rolePermissionCatalog.value = true;

  permissionDialogTitle.value = `配置「${row.name}」功能权限`;
}

/**
 * 配置角色可访问的数据类型
 * */
function handleDataPermissionConfig(row: any) {
  applicationId.value = row.applicationId;
  roleId.value = row.id;
  roleDataPermissionDialog.value = true;
  dataPermissionDialogTitle.value = `配置「${row.name}」数据权限`;
}

/**
 * 配置角色界面入口
 * */
function handleVisibilityConfig(row: any) {
  applicationId.value = row.applicationId;
  roleId.value = row.id;
  roleVisibilityAssociate.value = true;

  visibilityDialogTitle.value = `配置「${row.name}」界面可见性`;
}
</script>
