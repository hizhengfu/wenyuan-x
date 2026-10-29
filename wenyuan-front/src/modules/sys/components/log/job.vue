<template>
  <h-page-data-table
    ref="refPageDetailTable"
    :fetch-data="logs"
    :show-operate="false"
    :show-header="false"
    :is-add="false"
    :params="logParams"
    :columns="columnLogs"
  >
    <template #queryForm>
      <n-form-item label="任务名称" path="jobName">
        <n-input v-model:value="logParams.jobName" placeholder="任务名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="方法名称" path="methodName">
        <n-input v-model:value="logParams.methodName" placeholder="方法名称" clearable></n-input>
      </n-form-item>
    </template>
  </h-page-data-table>
</template>
<script setup lang="ts">
import { ref } from 'vue';
import { logs } from '@/modules/sys/api/job.ts';
import JobSelect from '../form/field/JobSelect.vue';
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
      tooltip: { style: { maxWidth: '600px', overflow: 'auto' } },
    },
  },
  { key: 'createTime', title: '执行时间', columnType: 'dateTime' },
];

const logParams = ref({ jobId: '', beanName: '', methodName: '', jobName: '' });
</script>
