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
      <n-form-item label="展示名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入展示名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
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

      <n-grid x-gap="5">
        <n-grid-item span="18">
          <n-form-item label="展示名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入展示名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="组织结构" path="groupId">
            <HGroupSelect v-model:value="form.groupId" placeholder="请选择组织机构" clearable></HGroupSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <HAvatar v-model:value="form.image" placeholder="请输入缩略图" clearable></HAvatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-form-item label="访问地址" path="url">
        <n-input v-model:value="form.url" placeholder="请输入页面地址" clearable></n-input>
      </n-form-item>

      <n-form-item label="内容描述" path="content">
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
import {list, edit, add, del, get} from '../../api/define'
import HGroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue'
import {ref} from 'vue';
import {cloneDeep} from 'lodash-es'

const columns = [
  {'key': 'name', 'title': '展示名称', 'columnType': 'title', 'ellipsis': {tooltip: {style: {maxWidth: '600px'}}}},
  {'key': 'image', 'title': '缩略图', 'columnType': 'image', width: '80'},
  {'key': 'groupId', 'title': '组织机构', width: '220'},
  {'key': 'url', 'title': '展示地址', 'ellipsis': {tooltip: {style: {maxWidth: '600px'}}}, width: '260'},
  {'key': 'status', 'title': '状态', 'dictType': 'content_status', width: '80'}
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
  image: '',
  url: '',
  groupId: '',
  content: '',
  status: '0',
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
    message: '请输入name'
  },
  url: {
    required: true,
    message: '请输入url'
  },
  groupId: {
    required: true,
    message: '请输入group_id'
  },
  status: {
    required: true,
    message: '请输入status'
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
  })
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
  if (formType.value === 'add') {
    return add(data)
  } else {
    return edit(data)
  }
}
</script>

