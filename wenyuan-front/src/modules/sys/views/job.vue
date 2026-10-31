<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    scroll-x="100%"
    :params="paramsForm"
    :fun-api-delete="del"
    :fun-api-copy="copy"
    :columns="columns"
    @add="handleAdd"
    @row-edit="handleEdit"
    @row-view="handleView"
  >
    <template #queryForm>
      <n-form-item label="任务名称" path="jobName">
        <n-input v-model:value="paramsForm.jobName" placeholder="任务名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="任务类型" path="type">
        <h-dict-select
          v-model:value="paramsForm.type"
          type="sys_job_type"
          placeholder="请选择类型"
          clearable
        ></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-grid x-gap="10">
        <n-form-item-gi :span="14" label="任务名称" path="jobName">
          <n-input v-model:value="form.jobName" placeholder="请输入任务名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="8" label="任务类型" path="type">
          <HDictRadio v-model:value="form.type" type="sys_job_type"></HDictRadio>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="Bean名称" path="beanName">
          <n-input v-model:value="form.beanName" placeholder="请输入Bean名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="执行方法" path="methodName">
          <n-input v-model:value="form.methodName" placeholder="请输入执行方法"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="12" label="服务器任务编号" path="serverId">
          <template #label> 服务器任务编号(服务器端配置app.taskId)</template>
          <n-input v-model:value="form.serverId" placeholder="请输入任务编号"></n-input>
        </n-form-item-gi>
        <n-form-item-gi :span="24" label="参数" path="params">
          <n-input v-model:value="form.params" type="textarea" placeholder="请输入参数"></n-input>
        </n-form-item-gi>
        <n-form-item-gi
          :span="12"
          :label="String(form.type) === 'CRON' ? 'CRON表达式' : '延时执行时间'"
          path="cronExpression"
        >
          <n-input
            v-if="String(form.type) === 'CRON'"
            v-model:value="form.cronExpression"
            placeholder="请输入CRON"
          ></n-input>
          <h-date-picker
            v-else
            type="datetime"
            v-model:value="form.cronExpression"
            placeholder="请输入延时执行时间"
          ></h-date-picker>
        </n-form-item-gi>

        <n-grid-item span="12">
          <n-flex>
            <n-form-item label="记录日志" path="isLog">
              <n-switch v-model:value="form.isLog"></n-switch>
            </n-form-item>
            <n-form-item label="失败邮件通知" path="enableNotice" feedback="如需通知需要在系统配置中启用系统邮件通知">
              <n-switch v-model:value="form.enableNotice"></n-switch>
            </n-form-item>
          </n-flex>
        </n-grid-item>
      </n-grid>
      <n-form-item label="备注" path="remarks">
        <n-input v-model:value="form.remarks" type="textarea" placeholder="请输入备注"></n-input>
      </n-form-item>
    </h-page-form>
    <h-dialog v-model:show="logModel" size="small" :title="logModelTile" style="height: 600px; width: 800px">
      <h-page-data-table
        ref="refPageDetailTable"
        :fetch-data="logs"
        :show-operate="false"
        :show-header="false"
        :showToolbar="false"
        :is-add="false"
        :params="logParams"
        :columns="columnLogs"
      >
      </h-page-data-table>
    </h-dialog>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, copy, del, edit, exec, list, logs, state } from '../api/job';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton } from 'naive-ui';
import dayjs from 'dayjs';
import { confirm } from '@/utils';

const columns = [
  {
    key: 'jobName',
    title: '任务名称',
    columnType: 'title',
    fixed: 'left',
    resizable: true,
    ellipsis: {
      tooltip: true,
    },
    width: '260px',
  },
  { key: 'type', title: '任务类型', dictType: 'sys_job_type', width: '120px' },
  { key: 'beanName', title: 'Bean名称', width: '200px' },
  { key: 'methodName', title: '执行方法', align: 'left', width: '120px' },
  {
    key: 'cronExpression',
    title: '执行时间',
    align: 'left',
    width: '200px',
    render(row: any, index: number) {
      return row.type === 'CRON' ? row['cronExpression'] : dayjs(row['cronExpression'] * 1).format('YYYY-MM-DD HH:mm:ss');
    },
  },
  { key: 'params', title: '参数', ellipsis: { tooltip: { style: { maxWidth: '600px' } } } },
  { key: 'status', title: '执行状态', width: '120px', align: 'center', dictType: 'sys_job_status' },
  { key: 'isPause', title: '启用状态', dictType: 'sys_job_pause', align: 'center', width: '100px' },
  { key: 'isLog', title: '启用日志', dictType: 'sys_ok', align: 'center', width: '100px' },
  { key: 'enableNotice', title: '邮件通知', dictType: 'sys_ok', align: 'center', width: '100px' },
  {
    key: 'pauseControl',
    title: '控制',
    align: 'center',
    width: '300px',
    render(row: any, index: number) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'success',
            class: 'mr-3',
            onClick: () => {
              logModel.value = true;
              logParams.value = { jobId: row.id, beanName: row.beanName, methodName: row.methodName };
              logModelTile.value = '查看' + row.jobName + '日志';
            },
          },
          {
            default: () => '查看日志',
          },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'warning',
            class: 'mr-3',
            onClick: () => {
              confirm({
                content: `即将手工执行「${row.jobName}」任务，请确认后执行。`,
                onPositiveClick() {
                  exec(row['id']);
                },
              });
            },
          },
          {
            default: () => '手工执行',
          },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'error',
            onClick: () => {
              confirm({
                content: `即将切换「${row.jobName}」任务，请确认后执行。`,
                onPositiveClick() {
                  state(row['id']).then(() => {
                    refPageTable.value.handleRefresh();
                  });
                },
              });
            },
          },
          {
            default: () => '切换执行状态',
          },
        ),
      ];
    },
  },
  { key: 'updateTime', title: '更新时间', columnType: 'dateTime', width: '200px', align: 'center' },
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
  type: '0',
  jobName: '',
  beanName: '',
  methodName: '',
  params: '',
  serverId: null,
  isLog: false,
  enableNotice: false,
  cronExpression: null,
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
    message: '请输入名称',
  },
  type: {
    required: true,
    message: '请选择任务类型',
  },
  groupId: {
    required: true,
    message: '请选择机构',
  },
  code: {
    required: true,
    message: '请输入代码',
  },
  enabled: {
    required: true,
    message: '请选择状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  jobName: '',
  type: '',
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
  formTitle.value = `编辑「${row.jobName}」`;
  formModel.value = true;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.jobName}」`;
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

/**
 * 任务执行日志
 * */
const columnLogs = [
  {
    key: 'jobName',
    title: '任务名称',
    ellipsis: {
      width: '30%',
    },
  },
  { key: 'isSuccess', title: '执行状态', dictType: 'sys_job_success', width: '120', align: 'center' },
  {
    key: 'exceptionDetail',
    title: '异常信息',
    ellipsis: {
      width: '30%',
    },
  },
  { key: 'createTime', title: '执行时间', columnType: 'dateTime' },
];

const logParams = ref({ jobId: '', beanName: '', methodName: '' });
const logModel = ref(false);
const logModelTile = ref('查看日志');
</script>
