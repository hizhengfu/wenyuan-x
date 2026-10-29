<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1800"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="库区名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入库区名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="库区编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入库区编号" clearable></n-input>
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
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="库区名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入库区名称" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="库区编号(留空自动生成)" path="code">
            <n-input v-model:value="form.code" placeholder="输入库区编号" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="库区位置" path="location">
            <n-input v-model:value="form.location" placeholder="请输入库区位置" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="库区经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng" placeholder="请输入库区经纬度" clearable></HFieldLatlng>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="库区负责人" path="manager">
            <n-input v-model:value="form.manager" placeholder="请输入库区负责人" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="联系电话" path="managerPhone">
            <n-input v-model:value="form.managerPhone" placeholder="请输入库区负责人电话" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="库区描述" path="remarks">
        <n-input type="textarea" rows="10" placeholder="请输入库区描述" v-model:value="form.remarks"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/storageArea';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '库区名称',
    columnType: 'title',
    width: '220px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'code', title: '库区编号', width: '215px', resizable: true, ellipsis: true },
  { key: 'location', title: '库区位置', width: '200px', resizable: true, ellipsis: true },
  { key: 'manager', title: '库区负责人', width: '120px', resizable: true, ellipsis: true },
  { key: 'managerPhone', title: '库区负责人电话', width: '160px' },
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
  code: '',
  image: '',
  name: '',
  manager: '',
  managerPhone: '',
  location: '',
  latlng: '',
  remarks: '',
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
    message: '请输入库区名称',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
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
