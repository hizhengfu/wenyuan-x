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
      <n-form-item label="探方名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入探方名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址名称" path="projectId">
        <WmsProjectSelect
          v-model:value="paramsForm.projectId"
          placeholder="请选择遗址名称"
          clearable
        ></WmsProjectSelect>
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
      <n-form-item label="探方名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入探方名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="遗址名称" path="projectId">
        <WmsProjectSelect v-model:value="form.projectId" placeholder="请选择遗址名称" clearable></WmsProjectSelect>
      </n-form-item>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" v-model:value="form.remarks"></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <n-table :single-line="false">
        <colgroup>
          <col style="width: 100px" />
          <col />
        </colgroup>
        <tr>
          <td>探方名称</td>
          <td colspan="3">{{ form.name }}</td>
        </tr>
        <tr>
          <td>遗址名称</td>
          <td>{{ form.project?.name }}</td>
        </tr>
        <tr>
          <td>备注:</td>
          <td>
            <div>{{ form.remarks }}</div>
          </td>
        </tr>
        <tr>
          <td colSpan="4">
            <h-file-list :contentId="form.id"></h-file-list>
          </td>
        </tr>
      </n-table>
    </h-page-view>

    <PlotCellAssociation
      v-if="form.id"
      v-model:show="formCellModel"
      :projectId="form.projectId"
      :plotId="form.id"
    ></PlotCellAssociation>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, get, list } from '../../../api/projectPlot.ts';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import PlotCellAssociation from '@/platforms/asa_wms/components/PlotCellAssociation.vue';
import { WmsProjectSelect } from '@/platforms/asa_wms/components/form/field';

const columns = [
  {
    key: 'name',
    title: '探方名称',
    columnType: 'title',
    minWidth: '260px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'project.name', title: '遗址名称', width: '200px', resizable: true, ellipsis: true },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    minWidth: '190px',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          class: 'mr-3',
          type: 'primary',
          onClick() {
            handleFormCell(row);
          },
        },
        { default: () => '遗迹关联' },
      );
    },
  },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const formViewModel = ref(false);
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
  projectId: '',
  project: { name: '' },
  image: '',
  code: '',
  remarks: '',
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
    message: '请输入探方名称',
  },
  projectId: {
    required: true,
    message: '请选择遗址名称',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  projectId: '',
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
    formViewModel.value = true;
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

const formCellModel = ref();

function handleFormCell(row: any) {
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formCellModel.value = true;
}
</script>
