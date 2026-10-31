<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" :collapsed-width="15" collapse-mode="transform" show-trigger="arrow-circle" bordered>
        <n-layout-header bordered style="padding: 10px"> 系统应用</n-layout-header>
        <h-application-list v-model:value="applicationId"></h-application-list>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="list"
          :params="paramsForm"
          :fun-api-delete="del"
          :fun-api-copy="copy"
          :columns="columns"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
        >
          <template #queryForm>
            <n-form-item label="Portal名称" path="name">
              <n-input v-model:value="paramsForm.name" clearable placeholder="Portal名称"></n-input>
            </n-form-item>
            <n-form-item label="Portal类型" path="type">
              <h-dict-select
                v-model:value="paramsForm.type"
                clearable
                placeholder="请选择Portal类型"
                type="portal_type_name"
              ></h-dict-select>
            </n-form-item>
            <n-form-item label="功能分组" path="groupId">
              <h-field-portal-group
                v-model:value="paramsForm.groupId"
                clearable
                placeholder="请选择功能分组"
              ></h-field-portal-group>
            </n-form-item>
          </template>
          <h-page-form
            v-model:show="formModel"
            :rules="rules"
            :model="form"
            label-width="auto"
            :disabled="formType === 'view'"
            :title="formTitle"
            :ref-table="refPageTable"
            :handle-submit="handleSubmit"
          >
            <n-grid :x-gap="12">
              <n-gi span="16">
                <n-form-item label="名称" path="name">
                  <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
                </n-form-item>
                <n-form-item label="所属应用" path="applicationId">
                  <h-application-select
                    v-model:value="form.applicationId"
                    placeholder="请选择应用"
                  ></h-application-select>
                </n-form-item>
              </n-gi>
              <n-gi span="8" class="flex-center">
                <n-form-item label="缩略图" path="image">
                  <h-avatar v-model:value="form.image"></h-avatar>
                </n-form-item>
              </n-gi>
            </n-grid>
            <n-grid :x-gap="12">
              <n-form-item-gi :span="12" label="类型" path="type">
                <h-dict-select
                  v-model:value="form.type"
                  placeholder="请选择类型"
                  type="portal_type_name"
                ></h-dict-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="方法" path="method">
                <h-dict-select
                  v-model:value="form.method"
                  placeholder="请选择方法"
                  type="portal_method"
                ></h-dict-select>
              </n-form-item-gi>
            </n-grid>
            <n-grid :x-gap="12">
              <n-form-item-gi :span="8" label="功能分组" path="groupId">
                <h-field-portal-group
                  v-model:value="form.groupId"
                  clearable
                  placeholder="请选择功能分组"
                ></h-field-portal-group>
              </n-form-item-gi>
              <n-form-item-gi :span="8" label="文字颜色" path="color">
                <n-color-picker v-model:value="form.color" placeholder="请选择颜色"></n-color-picker>
              </n-form-item-gi>
              <n-form-item-gi :span="8" label="背景颜色" path="backgroundColor">
                <n-color-picker v-model:value="form.backgroundColor" placeholder="请选择颜色"></n-color-picker>
              </n-form-item-gi>
            </n-grid>

            <n-grid :x-gap="12">
              <n-form-item-gi :span="12" label="API地址" path="url">
                <n-input v-model:value="form.url" placeholder="请输入API地址"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="跳转链接" path="link">
                <n-input v-model:value="form.link" placeholder="请输入跳转链接"></n-input>
              </n-form-item-gi>
            </n-grid>

            <n-grid :x-gap="12">
              <n-form-item-gi :span="8" label="默认宽度" path="width">
                <n-input-number v-model:value="form.width" min="1" placeholder="请输入宽度"></n-input-number>
              </n-form-item-gi>
              <n-form-item-gi :span="8" label="默认高度" path="height">
                <n-input-number v-model:value="form.height" min="1" placeholder="请输入高度"></n-input-number>
              </n-form-item-gi>
              <n-form-item-gi :span="8" label="排序" path="sort">
                <n-input-number v-model:value="form.sort" min="0" placeholder="请输入排序"></n-input-number>
              </n-form-item-gi>
            </n-grid>
            <div class="flex justify-between">
              <n-form-item label-align="left" label="是否冻结" path="isFrozen">
                <n-switch v-model:value="form.isFrozen"></n-switch>
              </n-form-item>
              <n-form-item label-align="left" label="边框" path="isBorder">
                <n-switch v-model:value="form.isBorder"></n-switch>
              </n-form-item>
              <n-form-item label-align="left" label="容器" path="isExpose">
                <n-switch v-model:value="form.isExpose"></n-switch>
              </n-form-item>
              <n-form-item label-align="left" label="阴影" path="isShadow">
                <n-switch v-model:value="form.isShadow"></n-switch>
              </n-form-item>
              <n-form-item label-align="left" label="启用" path="enable">
                <n-switch v-model:value="form.enable"></n-switch>
              </n-form-item>
              <n-form-item label-align="left" label="日志" path="enableLog">
                <n-switch v-model:value="form.enableLog"></n-switch>
              </n-form-item>
            </div>
            <n-form-item label="版本" path="version">
              <n-input v-model:value="form.version" placeholder="请输入版本"></n-input>
            </n-form-item>
            <n-form-item label="内容" path="content">
              <n-input type="textarea" v-model:value="form.content" placeholder="请输入内容"></n-input>
            </n-form-item>
          </h-page-form>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import { add, del, edit, list, copy } from '../../api/portaldefine';
import HApplicationList from '@/modules/sys/components/ApplicationList.vue';
import HApplicationSelect from '@/modules/sys/components/form/field/ApplicationSelect.vue';
import { h, ref, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { HFieldPortalGroup } from '../../components/form/field';
import { RouterLink } from 'vue-router';

const applicationId = ref();

const columns = [
  { key: 'name', title: '名称', columnType: 'title', fixed: 'left', width: '160px', resizable: true },
  { key: 'type', title: '类型', dictType: 'portal_type_name', width: '80px' },
  { key: 'isFrozen', title: '冻结', dictType: 'sys_ok', width: '60px', align: 'center' },
  { key: 'isExpose', title: '边框', dictType: 'sys_ok', width: '60px', align: 'center' },
  { key: 'enable', title: '启用', dictType: 'sys_ok', width: '60px', align: 'center' },
  {
    key: 'field',
    title: '关联字段',
    width: '80px',
    align: 'center',
    render(row: any) {
      return h(
        RouterLink,
        {
          to: {
            path: '/portal/manage/field',
            query: { defineId: row.id, title: row.name },
          },
        },
        { default: () => '查看' },
      );
    },
  },
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
  applicationId: '1',
  groupId: '',
  name: '',
  image: '',
  type: '',
  url: '',
  method: '3',
  link: '',
  content: '',
  width: 4,
  height: 40,
  sort: 10,
  color: '#ffffff',
  backgroundColor: '#75B9E6',
  isFrozen: false,
  isExpose: true,
  isShadow: false,
  isBorder: true,
  enable: true,
  enableLog: false,
  version: '1.0',
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
  type: {
    required: true,
    message: '请选择类型',
  },
  url: {
    required: true,
    message: '请输入API地址',
  },
  method: {
    required: true,
    message: '请选择方法',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  applicationId: '',
  type: '',
  name: '',
  groupId: '',
  serialNo: '',
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
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
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

watchEffect(() => {
  if (applicationId.value) {
    paramsForm.value.applicationId = applicationId.value;
    // @ts-ignore
    refPageTable.value?.handleQuery();
  }
});
</script>
