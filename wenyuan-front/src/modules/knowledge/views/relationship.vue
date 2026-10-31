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
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="分类" path="categoryId">
        <n-input v-model:value="paramsForm.categoryId" placeholder="请输入分类" clearable></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-select v-model:value="paramsForm.status"
                       type="content_status"
                       clearable filterable></h-dict-select>
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
        <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-grid x-gap="6">
        <n-grid-item span="8">
          <n-form-item label="来源" path="sourceId">
            <PropertySelect v-model:value="form.sourceId" placeholder="请输入来源" clearable></PropertySelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="分类" path="direction">
            <h-dict-select v-model:value="form.direction"
                           type="knowledge_direction_type"
                           clearable filterable></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="8">
          <n-form-item label="目标" path="targetId">
            <PropertySelect v-model:value="form.targetId" placeholder="请输入目标" clearable></PropertySelect>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="地点" path="location">
        <n-input v-model:value="form.location" placeholder="请输入地点" clearable></n-input>
      </n-form-item>
      <n-form-item label="内容" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio v-model:value="form.status"
                      type="content_status"
                      clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {add, del, edit, get, list} from '../api/relationship'

import PropertySelect from '../components/form/field/PropertySelect.vue'
import {ref} from 'vue';
import {cloneDeep} from 'lodash-es'

const columns = [
  {'key': 'name', 'title': '名称', 'columnType': 'title', 'ellipsis': {tooltip: {style: {maxWidth: '600px'}}}},
  {'key': 'direction', 'title': '类型', 'dictType': 'knowledge_direction_type', width: '100px', align: 'center'},
  {'key': 'sourceKnowledgeNode.name', 'title': '来源', ellipsis: {tooltip: true}, width: '200px'},
  {'key': 'targetKnowledgeNode.name', 'title': '目标', ellipsis: {tooltip: true}, width: '200px'},
  {'key': 'status', 'title': '状态', 'dictType': 'content_status', width: '100px', align: 'center'}
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref()

const formTitle = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  direction: '0',
  content: '',
  location: '',
  targetId: '',
  sourceId: '',
  status: 'NORMAL',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null
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
  direction: {
    required: true,
    message: '请选择关系类型'
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
  name: '',
  categoryId: '',
  targetId: '',
  sourceId: '',
  status: ''
})

const formType = ref('add')

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = '添加'
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  form.value = cloneDeep(defaultForm)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    formModel.value = true
  })
  formTitle.value = `编辑「${row.name}」`
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    formModel.value = true
  })
  formTitle.value = `查看「${row.name}」`
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data)
  } else {
    return edit(data)
  }
}
</script>

