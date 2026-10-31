<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px"> 系统应用 </n-layout-header>
        <h-application-list v-model:value="applicationId"></h-application-list>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
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
              <n-input v-model:value="paramsForm.name" placeholder="名称"></n-input>
            </n-form-item>
          </template>
          <h-dialog-form
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
            <n-grid x-gap="12">
              <n-form-item-gi :span="8" label="所属应用" path="applicationId">
                <h-application-select
                  v-model:value="form.applicationId"
                  placeholder="请选择应用"
                ></h-application-select>
              </n-form-item-gi>
              <n-form-item-gi label="颜色" path="color" :span="4">
                <n-color-picker v-model:value="form.color"></n-color-picker>
              </n-form-item-gi>
              <n-form-item-gi label="排序" path="sort" :span="4">
                <n-input-number v-model:value="form.sort" placeholder="请输入排序"></n-input-number>
              </n-form-item-gi>
            </n-grid>
            <n-form-item label="备注" path="remarks">
              <n-input type="textarea" v-model:value="form.remarks" placeholder="请输入备注"></n-input>
            </n-form-item>
          </h-dialog-form>
        </h-page-data-table>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import { add, del, edit, list } from '../../api/portalgroup';
import HApplicationList from '@/modules/sys/components/ApplicationList.vue';
import HApplicationSelect from '@/modules/sys/components/form/field/ApplicationSelect.vue';
import { ref, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';

const applicationId = ref();

const columns = [
  { key: 'name', title: '名称', columnType: 'title' },
  { key: 'color', title: '颜色' },
  { key: 'remarks', title: '备注' },
  { key: 'sort', title: '排序', width: '100px' },
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
  applicationId: '',
  name: '',
  remarks: '',
  sort: 0,
  color: '',
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
  value: {
    required: true,
    message: '请输入值',
  },
  applicationId: {
    required: true,
    message: '请选择应用',
  },
  sort: {
    required: true,
    message: '请输入排序',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  applicationId: '',
  name: '',
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
