<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="楼层名称" path="name">
        <n-input v-model:value="paramsForm.name" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属场所" path="venueId">
        <HCascaderDevice
          type="venue"
          clearable
          v-model:value="paramsForm.venueId"
          placeholder="请选择场所"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="状态" path="status">
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
      <n-grid cols="24" x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name"></n-input>
          </n-form-item>
          <n-form-item label="场馆" path="venueId">
            <HCascaderDevice
              type="venue"
              clearable
              v-model:value="form.venueId"
              placeholder="请选择场馆"
            ></HCascaderDevice>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="描述信息" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, updateSort } from '../../api/floor';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import HVenueSelect from '../../components/form/field/VenueSelect.vue';
import HCascaderDevice from '../../components/form/field/CascaderDevice.vue';

const columns = [
  {
    key: 'name',
    title: '楼层名称',
    columnType: 'title',
    width: '320px',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    resizable: true,
    rowDrag: true,
    fixed: 'left',
  },
  { key: 'venue.name', title: '所属场所', width: '220px' },
  { key: 'image', title: '缩略图', columnType: 'image', align: 'center', width: '80px' },
  { key: 'status', title: '内容状态', dictType: 'content_status', align: 'center', width: '80px' },
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
  content: '',
  venueId: '',
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
  venueId: {
    required: true,
    message: '请输入场馆',
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
  venueId: '',
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

function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
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
