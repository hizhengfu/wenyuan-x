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
      <n-form-item label="项目名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入项目名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="项目编号" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入项目编号" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属机构" path="groupId">
        <HGroupSelect v-model:value="paramsForm.groupId" placeholder="请选择所属机构" clearable></HGroupSelect>
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
          <n-form-item label="遗址名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入遗址名称" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="遗址编号(留空自动生成)" path="code">
            <n-input v-model:value="form.code" placeholder="请输入遗址编号" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="所属机构" path="groupId">
            <HGroupSelect v-model:value="form.groupId" placeholder="请选择所属机构" clearable></HGroupSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="项目负责人" path="leader">
            <n-input v-model:value="form.leader" placeholder="请输入项目负责人" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="12">
          <n-form-item label="开始日期" path="startDate">
            <n-date-picker clearable v-model:value="form.startDate" type="date" placeholder="请输入项目开始日期" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="结束日期" path="endDate">
            <n-date-picker clearable v-model:value="form.endDate" type="date" placeholder="请输入项目结束日期" />
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="15">
        <n-grid-item span="16">
          <n-form-item label="遗址位置" path="location">
            <n-input v-model:value="form.location" placeholder="请输入遗址位置" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng" placeholder="请输入经纬度" clearable></HFieldLatlng>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="备注" path="remarks">
        <n-input type="textarea" rows="10" v-model:value="form.remarks" placeholder="请输入备注" clearable></n-input>
      </n-form-item>
    </h-page-form>
    <h-page-view v-model:show="formViewModel" :title="formTitle">
      <n-table :single-line="false">
        <tr>
          <td style="width: 120px">遗址名称</td>
          <td>{{ form.name }}</td>
          <td style="width: 120px">遗址编号</td>
          <td>{{ form.code }}</td>
        </tr>
        <tr>
          <td>所属机构</td>
          <td>{{ form.group?.name }}</td>
          <td>负责人</td>
          <td>{{ form.leader }}</td>
        </tr>
        <tr>
          <td>开始日期</td>
          <td>
            <n-time :time="form.startDate" format="yyyy-MM-dd" />
          </td>
          <td>结束日期</td>
          <td>
            <n-time :time="form.endDate" format="yyyy-MM-dd" />
          </td>
        </tr>
        <tr>
          <td>位置</td>
          <td colspan="3">{{ form.location }}</td>
        </tr>
        <tr>
          <td colspan="4">
            备注:
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

    <HDialogForm v-model:show="formCellModel" title="出土单位列表" size="small" height="600px">
      <ProjectCellList :projectId="form.id" :showHeader="false" :showToolbar="false"></ProjectCellList>
    </HDialogForm>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../../api/project';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import ProjectCellList from '@/platforms/asa_wms/components/ProjectCellList.vue';

const columns = [
  {
    key: 'name',
    title: '遗址名称',
    columnType: 'title',
    width: '260px',
    fixed: 'left',
    resizable: true,
    ellipsis: true,
  },
  { key: 'code', title: '遗址编号', width: '220px', resizable: true, ellipsis: true },
  { key: 'group.name', title: '归属机构', width: '200px', resizable: true, ellipsis: true },
  { key: 'leader', title: '负责人', width: '200px', resizable: true, ellipsis: true },
  { key: 'location', title: '位置', width: '200px', resizable: true, ellipsis: true },
  { key: 'startDate', title: '开始日期', columnType: 'date', width: '120px' },
  { key: 'endDate', title: '结束日期', columnType: 'date', width: '120px' },
  {
    key: 'operate',
    type: 'operate',
    fixed: 'right',
    title: '操作',
    align: 'center',
    width: '190px',
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
        { default: () => '遗迹单位' },
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
  image: '',
  code: '',
  groupId: '',
  group: { name: '' },
  leader: '',
  location: '',
  startDate: undefined,
  endDate: undefined,
  lat: '',
  lng: '',
  remarks: '',
  latlng: '',
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
    message: '请输入项目名称',
  },
  groupId: {
    required: true,
    message: '请选择归属机构',
  },
  location: {
    required: true,
    message: '请输入项目位置',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
  groupId: '',
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
