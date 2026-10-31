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
    :funApiCopy="copy"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="房间名称" path="name">
        <n-input v-model:value="paramsForm.name" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属楼层" path="floorId">
        <HCascaderDevice
          type="floor"
          clearable
          v-model:value="paramsForm.floorId"
          placeholder="请选择楼层"
        ></HCascaderDevice>
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
      <n-grid cols="24" x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
          </n-form-item>
          <n-form-item label="楼层" path="floorId">
            <HCascaderDevice
              type="floor"
              clearable
              v-model:value="form.floorId"
              placeholder="请选择楼层"
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
import { list, edit, add, del, copy, updateSort } from '../../api/room';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import HFloorSelect from '../../components/form/field/FloorSelect.vue';
import HCascaderDevice from '../../components/form/field/CascaderDevice.vue';

const columns = [
  {
    key: 'name',
    title: '房间名称',
    columnType: 'title',
    width: '220px',
    resizable: true,
    rowDrag: true,
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    fixed: 'left',
  },
  {
    key: 'venue.name',
    title: '场所位置',
    width: '220px',
    resizable: true,
    render(row: any) {
      return h('span', `${row.venue.name}/${row.floor.name}`);
    },
  },
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
  floorId: '',
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
  floorId: {
    required: true,
    message: '请选择楼层',
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
  floorId: '',
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
