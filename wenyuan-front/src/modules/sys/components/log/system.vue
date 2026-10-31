<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :showOperate="false"
    @rowView="handleView"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="日志类型" path="logType">
        <n-select v-model:value="paramsForm.logType" clearable :options="options" placeholder="请选择类型" />
      </n-form-item>
      <n-form-item label="登录用户" path="loginName">
        <n-input v-model:value="paramsForm.loginName" clearable placeholder="请输入登录名"></n-input>
      </n-form-item>
      <n-form-item label="路径" path="url">
        <n-input v-model:value="paramsForm.url" clearable placeholder="请输入路径"></n-input>
      </n-form-item>
      <n-form-item label="访问来源" path="requestIp">
        <n-input v-model:value="paramsForm.requestIp" clearable placeholder="请输入访问来源"></n-input>
      </n-form-item>
      <n-form-item label="参数查询" path="params">
        <n-input v-model:value="paramsForm.params" clearable placeholder="请输入参数内容"></n-input>
      </n-form-item>
      <n-form-item label="描述查询" path="description">
        <n-input v-model:value="paramsForm.description" clearable placeholder="请输入描述内容"></n-input>
      </n-form-item>
    </template>
    <h-dialog-view-form
      :model="form"
      label-width="auto"
      :title="formTitle"
      style="overflow: auto"
      :disabled="formType === 'view'"
      :refTable="refPageTable"
      v-model:show="formModel"
    >
      <n-descriptions label-placement="top" bordered :column="3">
        <n-descriptions-item label="登录名">
          {{ form.loginName }}
        </n-descriptions-item>
        <n-descriptions-item label="IP"> {{ form.requestIp }}({{ form.address }})</n-descriptions-item>
        <n-descriptions-item label="耗时">
          {{ form.time }}
        </n-descriptions-item>
        <n-descriptions-item label="描述">
          {{ form.description }}
        </n-descriptions-item>
        <n-descriptions-item label="日期" :span="2">
          <n-time :time="form.createTime"></n-time>
        </n-descriptions-item>
        <n-descriptions-item label="路径" :span="3">
          {{ form.url }}
        </n-descriptions-item>
        <n-descriptions-item label="方法" :span="3">
          {{ form.method }}
        </n-descriptions-item>
        <n-descriptions-item label="参数" :span="3">
          {{ form.params }}
        </n-descriptions-item>
        <n-descriptions-item label="UserAgent" :span="3">
          {{ form.userAgent }}
        </n-descriptions-item>
        <n-descriptions-item
          v-if="form.exceptionDetail"
          label="异常信息"
          style="overflow: auto; max-height: 300px"
          :span="3"
        >
          <n-log :log="form.exceptionDetail"></n-log>
        </n-descriptions-item>
      </n-descriptions>
    </h-dialog-view-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list } from '../../api/log';
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';

const columns = [
  { key: 'loginName', title: '登录名', columnType: 'title', width: '200px', fixed: 'left', resizable: true },
  { key: 'logType', title: '类型', width: '80px' },
  { key: 'url', title: '路径', minWidth: '120px', ellipsis: { tooltip: true }, resizable: true },
  {
    key: 'requestIp',
    title: '访问来源',
    width: '260px',
    resizable: true,
    ellipsis: { tooltip: true },
    render(row: any) {
      return `${row.requestIp}(${row.address})`;
    },
  },
  { key: 'description', title: '描述', width: '220px', resizable: true, ellipsis: true },
  { key: 'time', title: '耗时（ms）', align: 'right', width: '120px' },
  { key: 'createTime', title: '日期', columnType: 'dateTime', width: '220px', align: 'center' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref(null);

const formTitle = ref();
const options = [
  {
    label: 'INFO',
    value: 'INFO',
  },
  {
    label: 'ERROR',
    value: 'ERROR',
  },
];

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  loginName: '',
  requestIp: '',
  address: '',
  description: '',
  time: '',
  createTime: undefined,
  method: '',
  url: '',
  params: '',
  userAgent: '',
  exceptionDetail: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 查询表单
 * */
const paramsForm = ref({
  loginName: '',
  url: '',
  requestIp: '',
  description: '',
  params: '',
  logType: 'INFO',
});

const formType = ref('view');

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.loginName || '未知用户'}-${row.description}」`;
  formModel.value = true;
}
</script>
