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
      <n-form-item label="标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="请输入标题"></n-input>
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
      <n-form-item label="标题" path="title">
        <n-input v-model:value="form.title" placeholder="请输入标题"></n-input>
      </n-form-item>
      <n-form-item label="关键字" path="keywords">
        <h-field-keywords v-model:value="form.keywords"/>
      </n-form-item>
      <n-form-item label="内容" path="content">
        <n-input type="textarea" v-model:value="form.content" placeholder="请输入内容"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {HFieldKeywords} from '@/components/form/field'
import {add, del, edit, list} from '../../api/question'
import {h, ref} from "vue";
import {cloneDeep} from 'lodash-es'
import {NTag} from 'naive-ui';

const columns = [
  {key: 'title', title: '标题', columnType: 'title'},
  {title: '关键字', key: 'keywords', columnType: 'tag'},
  {key: 'content', title: '内容', ellipsis: {tooltip: true}},
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
  title: '',
  keywords: '',
  content: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入标题'
  },
  keywords: {
    required: true,
    message: '请输入关键字'
  },
  content: {
    required: true,
    message: '请输入内容'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
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
  formTitle.value = `编辑「${row.title}」`
  formModel.value = true
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.title}」`
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
