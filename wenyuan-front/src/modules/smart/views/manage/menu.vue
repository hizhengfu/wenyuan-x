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
      <n-form-item label="功能名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入功能名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属项目" path="smartControlId">
        <HDefineSelect v-model:value="paramsForm.smartControlId" placeholder="请选择功能项目" clearable></HDefineSelect>
      </n-form-item>
      <n-form-item label="功能类型" path="type">
        <h-dict-select v-model:value="paramsForm.type"
                       type="smart_menu_type"
                       placeholder="请选择功能类型"
                       clearable filterable></h-dict-select>
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
      <n-grid x-gap="6">
        <n-grid-item span="9">
          <n-form-item label="功能名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入功能名称" clearable></n-input>
          </n-form-item>
          <n-form-item label="所属项目" path="smartControlId">
            <HDefineSelect v-model:value="form.smartControlId" placeholder="请选择功能项目" clearable></HDefineSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="9">
          <n-form-item label="功能编码" path="code">
            <n-input v-model:value="form.code" placeholder="功能编码" clearable></n-input>
          </n-form-item>
          <n-form-item label="上级名称" path="parentId">
            <HMenuTreeSelect v-model:value="form.parentId" placeholder="请选择上级名称" clearable></HMenuTreeSelect>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <HAvatar v-model:value="form.image" placeholder="请上传缩略图" clearable></HAvatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="6">
        <n-grid-item span="9">
          <n-form-item label="事件类型" path="eventType">
            <h-dict-select v-model:value="form.eventType"
                           type="smart_event_type"
                           placeholder="请选择事件类型"
                           clearable filterable></h-dict-select>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <n-grid x-gap="5">
        <n-grid-item span="4">
          <n-form-item label="功能类型" path="type">
            <h-dict-select v-model:value="form.type"
                           type="smart_menu_type"
                           placeholder="请选择类型"
                           clearable filterable></h-dict-select>
          </n-form-item>
        </n-grid-item>
        <template v-if="form.type==='LIST'">
          <n-grid-item span="10">
            <n-form-item label="接口地址" path="url">
              <n-input v-model:value="form.url" placeholder="请输入接口地址" clearable></n-input>
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="5">
            <n-form-item label="列表名称字段" path="titleField">
              <n-input v-model:value="form.titleField" placeholder="列表名称字段" clearable></n-input>
            </n-form-item>
          </n-grid-item>
          <n-grid-item span="5">
            <n-form-item label="列表编码字段" path="codeField">
              <n-input v-model:value="form.codeField" placeholder="列表编码字段" clearable></n-input>
            </n-form-item>
          </n-grid-item>
        </template>
      </n-grid>
      <n-form-item label="功能状态" path="status">
        <h-dict-radio v-model:value="form.status"
                      type="content_status"
                      clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {list, edit, add, del, get} from '../../api/menu'
import {ref} from 'vue';
import {cloneDeep} from 'lodash-es'
import HDefineSelect from '../../components/form/field/DefineSelect.vue'
import HMenuTreeSelect from '../../components/form/field/MenuTreeSelect.vue'

const columns = [
  {'key': 'name', 'title': '名称', 'columnType': 'title', 'ellipsis': {tooltip: {style: {maxWidth: '600px'}}}},
  {'key': 'smartControlSmartControl.name', 'title': '归属项目'},
  {'key': 'image', 'title': '缩略图'},
  {'key': 'type', 'title': '类型', 'dictType': 'smart_menu_type'},
  {'key': 'code', 'title': '编码'},
  {'key': 'eventType', 'title': '事件类型', 'dictType': 'smart_event_type'},
  {'key': 'url', 'title': '接口地址'},
  {'key': 'status', 'title': '功能状态', 'dictType': 'content_status'},
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
  parentId: '',
  parentIds: '',
  isLeaf: '',
  image: '',
  name: '',
  type: 'BUTTON',
  code: '',
  titleField: 'title',
  codeField: 'id',
  eventType: 'CLICK',
  url: '',
  status: '0',
  smartControlId: '',
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
  code: {
    required: true,
    message: '请输入编码'
  },
  smartControlId: {
    required: true,
    message: '请控制项目'
  },
  url: {
    required: true,
    message: '请输入API地址'
  },
  titleField: {
    required: true,
    message: '请输入名称字段'
  },
  codeField: {
    required: true,
    message: '请输入编码字段'
  },
  eventType: {
    required: true,
    message: '请选择事件类型'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  type: '',
  smartControlId: '',
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
