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
      <n-form-item label="场所名称" path="name">
        <n-input v-model:value="paramsForm.name" clearable></n-input>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
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
      <n-grid x-gap="6" :cols="24">
        <n-grid-item :span="18">
          <n-form-item label="场所名称" path="name">
            <n-input v-model:value="form.name"></n-input>
          </n-form-item>
          <n-form-item label="所属机构" path="groupId">
            <group-select v-model:value="form.groupId"></group-select>
          </n-form-item>
          <n-form-item label="所属区域" path="areaId">
            <CascadeArea v-model:value="form.areaId"></CascadeArea>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image" size="120"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="地址" path="address">
        <n-input v-model:value="form.address"></n-input>
      </n-form-item>
      <n-form-item path="authentication">
        <template #label>
          本地认证
          <n-tooltip trigger="hover">
            <template #trigger>
              <n-button text>
                <n-icon>
                  <IconMdiHelpCircle></IconMdiHelpCircle>
                </n-icon>
              </n-button>
            </template>
            在中控系统中某些特殊操作需要输入此密钥用于二次认证。
          </n-tooltip>
        </template>
        <n-input v-model:value="form.authentication" placeholder="请输入本地认证密码"></n-input>
      </n-form-item>
      <n-form-item label="场馆描述" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
      <h-upload :contentId="form.id" label="上传附件" :disabled="formType === 'view'"></h-upload>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del } from '../../api/venue';
import CascadeArea from '@/modules/sys/components/form/field/CascadeArea.vue';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '场所名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    resizable: true,
    fixed: 'left',
  },
  { key: 'image', title: '缩略图', columnType: 'image', align: 'center', width: '80px' },
  { key: 'area.name', title: '所属区域' },
  { key: 'group.name', title: '所属机构' },
  { key: 'address', title: '场所地址' },
  { key: 'status', title: '内容状态', dictType: 'content_status', align: 'center', width: '100px' },
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
  name: '',
  image: '',
  areaId: '',
  groupId: '',
  content: '',
  authentication: '',
  address: '',
  status: '0',
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
  name: {
    required: true,
    message: '请输入名称',
  },
  groupId: {
    required: true,
    message: '请选择归属机构',
  },
  areaId: {
    required: true,
    message: '请选择归属区域',
  },
  authentication: {
    required: true,
    message: '请输入本地认证密钥',
  },
  status: {
    required: true,
    message: '请输入状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  status: '',
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
