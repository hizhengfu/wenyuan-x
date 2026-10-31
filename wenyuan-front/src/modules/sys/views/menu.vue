<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px">
          <n-form-item label="应用" label-placement="left" :show-feedback="false">
            <h-application-select v-model:value="menuParams.applicationId"></h-application-select>
          </n-form-item>
          <n-form-item label="菜单类型" label-placement="left" :show-feedback="false">
            <h-dict-radio
              v-model:value="menuParams.front"
              type="menu_type"
              @updateValue="handleChangeFront"
            ></h-dict-radio>
          </n-form-item>
        </n-layout-header>
        <n-layout-content position="absolute" style="top: 90px">
          <h-menu-tree
            v-model:value="menuId"
            ref="refMenuTree"
            :params="menuParams"
            accordion
            @edit="handleEdit"
            @delete="handleRemove"
            style="padding: 10px"
          ></h-menu-tree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <h-page-data-table
          ref="refPageTable"
          :fetch-data="list"
          :params="paramsForm"
          is-tree
          :fun-api-copy="copy"
          :fun-api-delete="del"
          :columns="columns"
          @updateSort="handleUpdateSort"
          @add="handleAdd"
          @rowEdit="handleEdit"
          @rowView="handleView"
        >
          <template #queryForm>
            <n-form-item label="菜单名称" path="name">
              <n-input v-model:value="paramsForm.name" placeholder="菜单名称" clearable></n-input>
            </n-form-item>
          </template>
          <template #toolbar-extra>
            <h-dialog-import-excel-form
              export-label="导出菜单(模版)"
              export-template-url="menu/export"
              file-upload-url="menu/import"
            ></h-dialog-import-excel-form>
          </template>
          <h-page-form
            v-model:show="formModel"
            :rules="rules"
            :model="form"
            :ref-table="refPageTable"
            label-width="auto"
            :title="formTitle"
            :disabled="formType === 'view'"
            :handle-submit="handleSubmit"
          >
            <n-grid x-gap="10">
              <n-form-item-gi :span="12" label="名称" path="name">
                <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="父级" path="parentId">
                <h-menu-select
                  v-model:value="form.parentId"
                  all-menu
                  :application-id="menuParams.applicationId"
                  placeholder="请选择父级菜单"
                  clearable
                ></h-menu-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="所属应用" path="applicationId">
                <h-application-select
                  v-model:value="form.applicationId"
                  placeholder="请选择所属应用"
                ></h-application-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="图标" path="sort">
                <h-icon-select v-model:value="form.icon" placeholder="请选择图标"></h-icon-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="链接地址" path="path">
                <n-input v-model:value="form.path" placeholder="请输入链接地址"></n-input>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="打开方式" path="target">
                <h-dict-select
                  v-model:value="form.target"
                  type="href_target"
                  placeholder="请选择打开方式"
                ></h-dict-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="类型" path="front">
                <h-dict-select v-model:value="form.front" type="menu_type" placeholder="请选择类型"></h-dict-select>
              </n-form-item-gi>
              <n-form-item-gi :span="12" label="排序" path="sort">
                <n-input-number v-model:value="form.sort" min="0" placeholder="请输入排序序号"></n-input-number>
              </n-form-item-gi>
              <n-form-item-gi :span="6" label="启用Portal" path="enablePortal">
                <n-switch v-model:value="form.enablePortal"></n-switch>
              </n-form-item-gi>
              <n-form-item-gi :span="6" label="启用" path="enabled">
                <n-switch v-model:value="form.enabled"></n-switch>
              </n-form-item-gi>
            </n-grid>
          </h-page-form>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import HMenuTree from '../components/MenuTree.vue';
import { HIconSelect } from '@/components/form/field/icon';
import HApplicationSelect from '../components/form/field/ApplicationSelect.vue';
import HMenuSelect from '../components/form/field/MenuSelect.vue';
import { add, copy, del, edit, list, updateSort } from '../api/menu';
import { h, ref, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { RouterLink } from 'vue-router';

import { confirm } from '@/utils';

const columns = [
  { key: 'name', title: '名称', rowDrag: true, fixed: 'left', resizable: true, width: '160px' },
  { key: 'icon', title: '图标', columnType: 'icon', align: 'center', width: '60px' },
  {
    key: 'path',
    title: '链接地址',
    minWidth: '220px',
    ellipsis: true,
    resizable: true,
    render(row: any) {
      if (!row.path) {
        return '--';
      } else {
        return h(
          RouterLink,
          {
            to: {
              path: row.path,
              query: { menuId: row.id, title: row.name },
            },
          },
          { default: () => row.path },
        );
      }
    },
  },
  { key: 'sort', title: '排序', align: 'center', width: '120px' },
  { key: 'enabled', title: '启用', dictType: 'sys_enabled', align: 'center', width: '120px' },
  { key: 'front', title: '前后端', dictType: 'menu_type', align: 'center', width: '120px' },
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

const menuId = ref();

const menuParams = ref({
  applicationId: '1',
  front: '0',
});

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  parentId: undefined,
  applicationId: undefined,
  name: '',
  icon: '',
  path: '',
  sort: 0,
  target: '_self',
  front: '0',
  enablePortal: false,
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
  type: {
    required: true,
    message: '请选择类型',
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
  id: '',
  applicationId: '',
  front: '0',
});

watchEffect(() => {
  paramsForm.value.id = menuId.value;
  paramsForm.value.applicationId = menuParams.value.applicationId;
  paramsForm.value.front = menuParams.value.front;
  formModel.value = false;
  refPageTable.value?.handleQuery();
});

const formType = ref('add');
const refMenuTree = ref();

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
 * 删除
 * */
function handleRemove(row: any) {
  confirm({
    content: `清注意，即将删除「${row.name}」`,
    onPositiveClick: () => {
      del(row.id).finally(() => {
        refMenuTree.value?.handleReload();
        refPageTable.value.handleRefresh();
      });
    },
  });
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
  if (!data.applicationId) {
    data.applicationId = menuParams.value.applicationId || '1';
  }
  if (formType.value === 'add') {
    return add(data).finally(() => {
      refMenuTree.value?.handleReload();
    });
  } else {
    return edit(data).finally(() => {
      refMenuTree.value?.handleReload();
    });
  }
}

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}

function handleChangeFront() {
  // paramsForm.value.id = ''
  menuId.value = '';
}
</script>
