<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @add="handleAdd"
    @row-edit="handleEdit"
    @row-view="handleView">
    <template #queryForm>
      <n-form-item label="名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-select
          v-model:value="paramsForm.status"
          type="content_status"
          clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType==='view'"
      :handle-submit="handleSubmit">
      <n-grid x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="关键字" path="keywords">
            <HKeywords v-model:value="form.keywords" placeholder="请输入关键字" clearable></HKeywords>
          </n-form-item>
          <n-grid x-gap="6">
            <n-grid-item span="8">
              <n-form-item label="分类" path="categoryId">
                <CategorySelect v-model:value="form.categoryId" placeholder="请选择分类" clearable></CategorySelect>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="8">
              <n-form-item label="权重" path="weight">
                <n-input-number v-model:value="form.weight" placeholder="请输入权重" clearable></n-input-number>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="8">
              <n-form-item label="监测对象" path="sourceId">
                <StationSelect v-model:value="form.sourceId" placeholder="请选择监测对象" clearable></StationSelect>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <HAvatar v-model:value="form.image"></HAvatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-form-item label="内容" path="content">
        <n-input v-model:value="form.content" type="textarea" rows="10" :disabled="formType==='view'"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status">
        <h-dict-radio
          v-model:value="form.status"
          type="content_status"
          clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {add, del, edit, get, list} from '../api/property'
import HKeywords from '@/components/form/field/keywords.vue'
import CategorySelect from '../components/form/field/CategorySelect.vue'
import StationSelect from '@/modules/monitor/components/form/field/StationSelect.vue'
import {ref, h} from 'vue';
import {cloneDeep} from 'lodash-es'
import {RouterLink} from 'vue-router';

const columns = [
  {'key': 'name', 'title': '名称', 'columnType': 'title', 'ellipsis': {tooltip: {style: {maxWidth: '600px'}}}},
  {'key': 'image', 'title': '缩略图', 'columnType': 'image', width: '100px'},
  {'key': 'weight', 'title': '权重', width: '100px', align: 'right'},
  {'key': 'keywords', 'title': '关键字', ellipsis: {tooltip: true}},
  {
    key: 'config', title: '查看', align: 'center', width: '100px',
    render(row: any) {
      return h(RouterLink,
        {to: {path: '/knowledge/detail', query: {nodeId: row.id, title: `知识图谱「${row.name}」查看`}}},
        {default: () => '点击查看'})
    }
  },
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
  sourceId: '',
  categoryId: '',
  name: '',
  image: '',
  weight: 0,
  keywords: '',
  content: '',
  status: 'NORMAL',
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
  weight: {
    required: true,
    message: '请输入权重'
  },
  categoryId: {
    required: true,
    message: '请选择分类'
  },
  status: {
    required: true,
    message: '请选择类型'
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

