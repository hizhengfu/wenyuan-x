<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm" @add="handleAdd"
    @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns">
    <template #queryForm>
      <n-form-item label="反馈名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入反馈名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="反馈类型" path="type">
        <h-dict-select v-model:value="paramsForm.type" placeholder="请输入反馈类型" clearable
          type="sys_feedback_type"></h-dict-select>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
      :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="12" :cols="2">
        <n-gi>
          <n-form-item label="反馈名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入反馈名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="反馈类型" path="type">
            <h-dict-select v-model:value="form.type" placeholder="请输入反馈类型" clearable
              type="sys_feedback_type"></h-dict-select>
          </n-form-item>
        </n-gi>
        <n-gi>
          <n-form-item label="经纬度" path="latlng">
            <HFieldLatlng v-model:value="form.latlng"></HFieldLatlng>
          </n-form-item>
          <n-form-item label="内容状态" path="status">
            <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="反馈内容" path="content">
        <h-editor v-model:value="form.content"></h-editor>
      </n-form-item>
    </h-page-form>
    <h-page-form v-model:show="detailModel" class="project" :model="form" label-width="auto" :title="formTitle"
      :ref-table="refPageTable" :disabled="formType === 'view'" :enable-cancel="false">
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>反馈名称</td>
            <td colSpan="3">{{ form.name || '--' }}</td>
            <td>经纬度</td>
            <td colSpan="3">{{ form.latlng || '--' }}
            </td>
          </tr>
          <tr>
            <td>反馈类型</td>
            <td colSpan="7"><h-dict-text v-model:value="form.type" type="sys_feedback_type"></h-dict-text></td>
          </tr>
          <tr>
            <td>反馈内容</td>
            <td colSpan="7">
              <div v-html="form.content"></div>
            </td>
          </tr>
          <tr>
            <td>内容状态</td>
            <td colSpan="7"><h-dict-text v-model:value="form.status" type="content_status"></h-dict-text></td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
    <h-page-form :rules="replayRules" :model="replayForm" label-width="auto" :title="formTitle"
      :handleSubmit="updateFeedback" v-model:show="handleModel">
      <n-table class="mb-5" :single-line="false">
        <tr>
          <td>反馈名称</td>
          <td colSpan="3">{{ form.name || '--' }}</td>
          <td>反馈类型</td>
          <td colSpan="3"> <h-dict-text v-model:value="form.type" type="sys_feedback_type"></h-dict-text>
          </td>
        </tr>
        <tr>
          <td>反馈时间</td>
          <td colspan="7">
            {{ form.createTime ? dayjs(form.createTime).format('YYYY-MM-DD') : '' }}
          </td>
        </tr>
        <tr>
          <td>反馈内容</td>
          <td colspan="7">
            <div v-html="form.content"></div>
          </td>
        </tr>
      </n-table>
      <n-form-item label="回复内容" path="content">
        <n-input v-model:value="replayForm.content" type="textarea"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get, replay } from '../api/feedback';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import dayjs from 'dayjs';

const columns = [
  {
    key: 'name',
    title: '反馈名称',
    columnType: 'title', width: '150px',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  { key: 'type', title: '反馈类型', 'dictType': 'sys_feedback_type', 'width': '150px', align: 'center' },
  { key: 'createBy', title: '反馈人员', 'width': '150px' },
  {
    key: 'createTime', title: '反馈时间', 'width': '150px',
    columnType: 'dateTime', align: 'center'
  },
  { key: 'latlng', title: '经纬度', 'width': '150px' },
  { key: 'status', title: '内容状态', dictType: 'content_status', 'width': '150px', align: 'center' },

  {
    key: 'operate', title: '操作', 'width': '120px',
    type: 'operate', render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'success',
          class: 'mr-3',
          style: { display: row.state != '3' ? '' : 'none' },
          onClick() {
            handleFeedback(row);
          },
        },
        { default: () => '回复' },
      );
    },
  },

];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);
const handleModel = ref(false)
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
  content: '',
  status: 0,
  lat: '',
  lng: '',
  createBy: '',
  createTime: null,
  latlng: ''
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

const defaultReplayForm = {
  id: '',
  content: ''
}

const replayForm = ref(cloneDeep(defaultReplayForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入反馈名称',
  },
  type: {
    required: true,
    message: '请输入反馈类型',
  },
  content: {
    required: true,
    message: '请输入反馈内容',
  },
};

const replayRules = {
  content: {
    required: true,
    message: '请输入回复内容',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  status: '',
});

const formType = ref('add');

function updateFeedback(data: any) {
  return replay(data)
}

function handleFeedback(row: any) {
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    replayForm.value.id = row.id
    handleModel.value = true
    formTitle.value = `回复「${row.name}」`
  });
}

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
