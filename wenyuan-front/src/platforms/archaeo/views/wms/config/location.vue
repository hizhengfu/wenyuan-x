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
      <n-form-item label="位置名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入位置名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="位置编码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入位置编码" clearable></n-input>
      </n-form-item>
      <n-form-item label="使用状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="wms_storage_type" clearable filterable></h-dict-select>
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
          <n-form-item label="位置名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入位置名称" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="位置编码(留空自动生成)" path="code">
            <n-input v-model:value="form.code" placeholder="输入位置编码" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="所属库房" path="roomId">
            <WmsRoomSelect v-model:value="form.roomId" placeholder="请输入所属库房" clearable></WmsRoomSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="存储容量" path="capacity">
            <n-input-number v-model:value="form.capacity" placeholder="请输入存储容量" clearable></n-input-number>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" rows="10" v-model:value="form.remarks" placeholder="请输入备注" clearable></n-input>
      </n-form-item>
      <n-form-item label="使用状态" path="status">
        <h-dict-radio v-model:value="form.status" type="wms_storage_type" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/storageLocation';
import { WmsRoomSelect } from '../../../components/form/field';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  {
    key: 'name',
    title: '位置名称',
    columnType: 'title',
    width: '220px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'roomWmsStorageRoom.name', title: '所属库房', width: '160px', resizable: true, ellipsis: true },
  { key: 'code', title: '位置编码', width: '215px', resizable: true, ellipsis: true },
  { key: 'capacity', title: '存储容量', width: '160px', ellipsis: true },
  { key: 'currentCount', title: '存放数量', width: '160px', ellipsis: true },
  { key: 'status', title: '使用状态', dictType: 'wms_storage_type', width: '160px' },
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
  roomId: '',
  name: '',
  code: '',
  capacity: 1,
  currentCount: '',
  remarks: '',
  status: '0',
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
    message: '请输入存储名称',
  },
  roomId: {
    required: true,
    message: '请输入所属库房',
  },
  capacity: {
    required: true,
    message: '请输入存储容量',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
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
