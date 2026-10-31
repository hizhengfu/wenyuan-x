<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1500"
    :params="paramsForm"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="视频名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入视频名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="视频编码" path="code">
        <n-input v-model:value="paramsForm.code" placeholder="请输入视频编码" clearable></n-input>
      </n-form-item>
      <n-form-item label="视频群组" path="groupId">
        <VideoGroupSelect
          v-model:value="paramsForm.groupId"
          placeholder="请选择视频群组"
          clearable
          filterable
        ></VideoGroupSelect>
      </n-form-item>
      <n-form-item label="归属服务" path="serverId">
        <VideoServeSelect
          v-model:value="paramsForm.serverId"
          placeholder="请选择视频群组"
          clearable
          filterable
        ></VideoServeSelect>
      </n-form-item>
      <n-form-item label="是否显示" path="show">
        <h-dict-select v-model:value="paramsForm.show" type="sys_ok" clearable filterable></h-dict-select>
      </n-form-item>
      <n-form-item label="在线状态" path="online">
        <h-dict-select v-model:value="paramsForm.online" type="sys_ok" clearable filterable></h-dict-select>
      </n-form-item>
      <slot name="relationQuery" :form="paramsForm">
        <n-form-item label="关联内容" path="relationSourceName">
          <n-input v-model:value="paramsForm.relationSourceName" placeholder="请输入关联名称或ID" clearable></n-input>
        </n-form-item>
      </slot>
      <n-form-item label="关联状态" path="hasRelation">
        <n-select
          v-model:value="paramsForm.hasRelation"
          :options="relationStatusOptions"
          placeholder="请选择关联状态"
          clearable
        ></n-select>
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
      <n-form-item label="视频名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入视频名称" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="6">
        <n-grid-item :span="12">
          <n-form-item label="视频群组" path="groupId">
            <VideoGroupSelect v-model:value="form.groupId" placeholder="请选择视频群组" clearable></VideoGroupSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="12">
          <n-form-item label="服务名称" path="serverId">
            <VideoServeSelect v-model:value="form.serverId" placeholder="请选择服务名称" clearable></VideoServeSelect>
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-form-item label="视频编码" path="code">
        <n-input v-model:value="form.code" placeholder="请输入视频编码" clearable></n-input>
      </n-form-item>
      <n-form-item label="安装地址" path="address">
        <n-input v-model:value="form.address" placeholder="请输入安装地址" clearable></n-input>
      </n-form-item>

      <n-form-item label="备注" path="content">
        <n-input type="textarea" v-model:value="form.content" placeholder="请输入视频备注"></n-input>
      </n-form-item>
      <n-form-item label="是否显示" path="show">
        <h-dict-radio v-model:value="form.show" type="sys_ok" clearable filterable></h-dict-radio>
      </n-form-item>
      <n-form-item label="数据状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
    <CameraRelationDrawer v-model:show="relationModel" :camera="relationCamera" @changed="handleRelationChanged">
      <template v-if="$slots.relationSourceId" #sourceId="slotProps">
        <slot name="relationSourceId" :form="slotProps.form"></slot>
      </template>
    </CameraRelationDrawer>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../api/camera';
import { h, ref, useSlots } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NTag } from 'naive-ui';
import VideoGroupSelect from '../components/form/field/VideoGroupSelect.vue';
import VideoServeSelect from '../components/form/field/VideoServeSelect.vue';
import CameraRelationDrawer from '../components/CameraRelationDrawer.vue';

const columns = [
  {
    key: 'name',
    title: '视频名称',
    columnType: 'title',
    width: '260px',
    fixed: 'left',
    ellipsis: { tooltip: true },
    resizable: true,
  },
  { key: 'serverConfig.name', title: '服务名称', width: '220px', resizable: true, ellipsis: true },
  { key: 'group.name', title: '群组名称', ellipsis: { tooltip: true }, resizable: true },
  { key: 'code', title: '视频编码', width: '320px', resizable: true },
  { key: 'address', title: '安装地址', width: '220px', ellipsis: { tooltip: true }, resizable: true },
  { key: 'online', title: '在线状态', dictType: 'sys_ok', width: '100px', align: 'center' },
  {
    key: 'relationSummary',
    title: '关联内容',
    width: '220px',
    render(row: any) {
      return slots.relationSummary?.({ row }) || renderRelationSummary(row);
    },
  },
  { key: 'status', title: '数据状态', dictType: 'content_status', width: '100px', align: 'center' },
  {
    key: 'operate',
    type: 'operate',
    title: '关联数据',
    width: '220px',
    align: 'center',
    fixed: 'right',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'primary',
          class: 'mr-3',
          onClick: () => handleRelation(row),
        },
        { default: () => '维护关联' },
      );
    },
  },
];

const slots = useSlots();

function renderRelationSummary(row: any) {
  const relation = row.primaryRelation;
  const relationCount = Number(row.relationCount || 0);
  if (!relation || relationCount === 0) {
    return h(NTag, { size: 'small', type: 'default' }, () => '未关联');
  }

  const label = relation.sourceName || relation.sourceId;
  return h('div', { style: 'display:flex;align-items:center;gap:6px;min-width:0;' }, [
    h(
      'span',
      {
        title: label,
        style: 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;',
      },
      label,
    ),
    relationCount > 1 ? h(NTag, { size: 'small', type: 'info' }, () => `+${relationCount - 1}`) : null,
  ]);
}

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
  serverId: '',
  groupId: '',
  code: '',
  address: '',
  content: '',
  show: '1',
  status: '',
  createTime: null,
  createBy: '',
  updateTime: null,
  updateBy: '',
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
    message: '请输入视频名称',
  },
  serverId: {
    required: true,
    message: '请输入服务id',
  },
  groupId: {
    required: true,
    message: '请输入机构id',
  },
  code: {
    required: true,
    message: '请输入编码',
  },
  show: {
    required: true,
    message: '请选择是否显示',
  },
  status: {
    required: true,
    message: '请选择状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  code: '',
  groupId: '',
  serverId: '',
  show: '',
  online: '',
  relationSourceType: null,
  relationSourceId: '',
  relationSourceName: '',
  hasRelation: '',
});

const relationStatusOptions = [
  { label: '已关联', value: 'true' },
  { label: '未关联', value: 'false' },
];

const formType = ref('add');
const relationModel = ref(false);
const relationCamera = ref<any>(null);

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

function handleRelation(row: any) {
  get(row.id).then((resp) => {
    relationCamera.value = resp.data || row;
    relationModel.value = true;
  });
}

function handleRelationChanged() {
  refPageTable.value?.handleRefresh?.();
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
