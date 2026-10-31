<template>
  <h-page-data-table ref="refPageTable"
                     :fetch-data="list"
                     :scroll-x="1200"
                     :params="paramsForm"
                     @add="handleAdd"
                     @rowEdit="handleEdit"
                     @rowView="handleView"
                     :funApiDelete="del"
                     :columns="columns">
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="名称"></n-input>
      </n-form-item>
      <n-form-item label="事件类型" path="eventType">
        <h-dict-select type="mu_task_type" v-model:value="paramsForm.eventType"
                       placeholder="请选择事件类型"></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 :title="formTitle"
                 :disabled="formType==='view'"
                 :refTable="refPageTable"
                 :handleSubmit="handleSubmit"
                 v-model:show="formModel">
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-grid x-gap="5">
        <n-form-item-gi label="事件类型" path="eventType" :span="8">
          <h-dict-select type="mu_task_type" v-model:value="form.eventType"
                         placeholder="请选择事件类型"></h-dict-select>
        </n-form-item-gi>
        <n-form-item-gi v-if="form.eventType==1" label="执行时间" path="dateTime" :span="8">
          <n-date-picker v-model:value="form.dateTime"
                         :is-date-disabled="disableNextDate"
                         type="datetime" clearable/>
        </n-form-item-gi>
        <n-form-item-gi v-if="form.eventType==0" label="处理方式" path="type" :span="8">
          <h-dict-select type="mu_task_process_type" v-model:value="form.type" placeholder="请选择类型"></h-dict-select>
        </n-form-item-gi>
      </n-grid>
      <n-form-item v-if="form.eventType==0" label="代码" path="code">
        <n-input v-model:value="form.code" placeholder="请输入代码"></n-input>
      </n-form-item>
      <n-form-item label="任务" path="taskId">
        <h-field-select-task v-model:value="form.taskId" placeholder="请选择任务"></h-field-select-task>
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input type="textarea" v-model:value="form.description" placeholder="请输入描述"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HFieldSelectTask from '../../components/form/field/FieldSelectTask.vue'
import {add, del, edit, list, process} from '../../api/event'
import {h, ref} from "vue";
import {cloneDeep} from 'lodash-es'
import {RouterLink} from "vue-router";
import {NButton} from 'naive-ui';

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'eventType', title: '类型', dictType: 'mu_task_type'},
  // {key: 'type', title: '处置类型', dictType: 'mu_task_process_type'},
  // {key: 'code', title: '代码'},
  {
    key: 'task.name', title: '任务',
    render(row: any) {
      return h(
        RouterLink,
        {
          to: {
            path: '/manage/task',
            query: {taskId: row.task.id, title: row.task.name}
          }
        },
        {default: () => row.task.name}
      )
    }
  },
  {
    key: 'process', title: '任务', width: '120px', align: 'center',
    render(row: any) {
      return h(NButton, {
          size: 'tiny',
          type: 'success',
          class: 'mr-3',
          onClick: () => handleProcess(row)
        },
        {
          default: () => '手工执行'
        }
      )
    }
  },
  // {key: 'dateTime', title: '计划执行时间', columnType: 'date', width: '220px', align: 'center'},
  {key: 'createTime', title: '创建时间', columnType: 'date', width: '220px', align: 'center'},
]


/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref(null)

const formTitle = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  type: undefined,
  eventType: 1,
  taskId: '',
  code: '',
  dateTime: undefined,
  description: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称'
  },
  type: {
    required: true,
    message: '请选择类型'
  },
  eventType: {
    required: true,
    message: '请选择事件类型'
  },
  dateTime: {
    required: true,
    message: '请选择执行时间'
  },
  taskId: {
    required: true,
    message: '请选择任务'
  },
  code: {
    required: true,
    message: '请输入编号'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  eventType: undefined,
})


const formType = ref("add")

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = "添加"
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  Object.assign(form.value, row)
  formTitle.value = `编辑「${row.name}」`
  formModel.value = true
}


/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.name}」`
  formModel.value = true
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === "add") {
    return add(data)
  } else {
    return edit(data)
  }
}

function disableNextDate(ts: number) {
  return ts < (Date.now() - 1000 * 24 * 60 * 60)
}

function handleProcess(row: any) {
  process(row.id)
}
</script>
