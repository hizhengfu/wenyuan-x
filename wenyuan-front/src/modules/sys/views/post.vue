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
      <n-form-item label="职务名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="职务名称" clearable></n-input>
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
      <n-form-item label="职务名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入职务名称"></n-input>
      </n-form-item>
      <n-form-item label="职务代码" path="code">
        <n-input v-model:value="form.code" placeholder="请输入职务代码"></n-input>
      </n-form-item>
      <n-form-item label="职务类型" path="type">
        <h-dict-radio type="post_type" v-model:value="form.type" placeholder="请选择职务类型"></h-dict-radio>
      </n-form-item>
      <n-form-item label="启用状态" path="enabled">
        <n-switch v-model:value="form.enabled"></n-switch>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {add, del, edit, list} from '../api/post'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'code', title: '代码'},
  {key: 'type', title: '类型', dictType: 'post_type', align: 'center', width: '120px'},
  {key: 'enabled', title: '启用', dictType: 'sys_enabled', align: 'center', width: '120px'},
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
  code: '',
  type: '2',
  enabled: true,
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
  code: {
    required: true,
    message: '请输入代码'
  },
  enabled: {
    required: true,
    message: '请选择状态'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
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
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  formTitle.value = `编辑「${row.name}」`
  formModel.value = true
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
</script>
