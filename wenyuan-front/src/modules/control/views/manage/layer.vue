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
      <n-form-item label="名图层称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属楼层" path="floorId">
        <HCascaderDevice
          type="floor"
          clearable
          v-model:value="paramsForm.floorId"
          placeholder="请选择楼层"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="图层类型" path="type">
        <h-dict-select
          type="cc_layer_type"
          v-model:value="paramsForm.type"
          placeholder="请选择图层类型"
        ></h-dict-select>
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
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name"></n-input>
          </n-form-item>
          <n-grid x-gap="6" :cols="24">
            <n-grid-item :span="12">
              <n-form-item label="类型" path="type">
                <h-dict-radio
                  type="cc_layer_type"
                  v-model:value="form.type"
                  placeholder="请选择图层类型"
                ></h-dict-radio>
              </n-form-item>
            </n-grid-item>
            <n-grid-item :span="12">
              <n-form-item label="楼层" path="floorId">
                <HCascaderDevice
                  type="floor"
                  v-model:value="form.floorId"
                  clearable
                  placeholder="请选择楼层"
                ></HCascaderDevice>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-grid-item>
        <n-grid-item :span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image" size="120"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="路径" path="path">
        <h-upload-input v-model:value="form.path" placeholder="文件路径"></h-upload-input>
      </n-form-item>
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
import { add, del, edit, list, updateSort } from '../../api/layer';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import HCascaderDevice from '../../components/form/field/CascaderDevice.vue';
import { RouterLink } from 'vue-router';

const columns = [
  {
    key: 'name',
    title: '图层名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    width: '320px',
    rowDrag: true,
    resizable: true,
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
  { key: 'type', title: '图层类型', dictType: 'cc_layer_type', align: 'center', width: '120px' },
  { key: 'image', title: '缩略图', align: 'center', width: '80px' },
  {
    key: 'path',
    title: '可视定位',
    align: 'center',
    width: '120px',
    render(row: any) {
      return h(
        RouterLink,
        { to: { path: './design2d', query: { layerId: row.id, title: '可视化定位' } }, class: 'mx-3' },
        { default: () => '可视化定位' },
      );
    },
  },
  // {'key': 'path', 'title': '路径'},
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
  type: '',
  image: '',
  path: '',
  content: '',
  floorId: '',
  status: 0,
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
  type: {
    required: true,
    message: '请选择类型',
  },
  path: {
    required: true,
    message: '请输入文件路径',
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
  type: '',
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
