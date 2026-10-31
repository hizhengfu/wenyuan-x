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
      <n-form-item label="编号" path="serialNo">
        <n-input v-model:value="paramsForm.serialNo" placeholder="编号"></n-input>
      </n-form-item>
    </template>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :refTable="refPageTable"
                   :disabled="formType==='view'"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="编号" path="serialNo">
        <n-input v-model:value="form.serialNo" placeholder="请输入编号"></n-input>
      </n-form-item>
      <n-form-item label="生产厂家" path="factory">
        <n-input v-model:value="form.factory" placeholder="请输入生产厂家"></n-input>
      </n-form-item>
      <n-form-item label="版本号" path="version">
        <n-input v-model:value="form.version" placeholder="请输入版本号"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {add, del, edit, list} from '../../api/robot'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'serialNo', title: '编号'},
  {key: 'factory', title: '生产厂家'},
  {key: 'version', title: '版本号'},
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
  serialNo: '',
  factory: '',
  version: '',
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
  serialNo: {
    required: true,
    message: '请输入编号'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  serialNo: '',
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
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === "add") {
    return add(data)
  } else {
    return edit(data)
  }
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.name}」`
  formModel.value = true
}
</script>
