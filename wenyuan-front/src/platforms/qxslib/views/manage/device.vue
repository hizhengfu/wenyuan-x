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
      <n-form-item label="设备编号" path="serialNo">
        <n-input v-model:value="paramsForm.serialNo" placeholder="设备编号"></n-input>
      </n-form-item>
      <n-form-item label="类型" path="type" style="width: 120px">
        <h-dict-select type="mu_device_type" clearable v-model:value="paramsForm.type" placeholder="请选择类型"></h-dict-select>
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
      <n-grid x-gap="5">
        <n-form-item-gi label="名称" path="name" :span="18">
          <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="类型" path="type" :span="6">
          <h-dict-radio type="mu_device_type" v-model:value="form.type" placeholder="请选择字典"></h-dict-radio>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="展馆" path="roomId">
        <h-field-select-room v-model:value="form.roomId" placeholder="请选择展馆"></h-field-select-room>
      </n-form-item>
      <n-form-item label="设备编号" path="serialNo">
        <n-input v-model:value="form.serialNo" placeholder="请输入设备编号"></n-input>
      </n-form-item>
      <n-form-item label="设备地址" path="addr">
        <n-input v-model:value="form.addr" placeholder="请输入设备地址"></n-input>
      </n-form-item>
      <n-form-item label="描述" path="description">
        <n-input type="textarea" v-model:value="form.description" placeholder="请输入描述"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HFieldSelectRoom from '../../components/form/field/FieldSelectRoom.vue'
import {add, del, edit, list} from '../../api/device'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'room.name', title: '展馆'},
  {key: 'type', title: '类型', dictType: 'mu_device_type'},
  {key: 'serialNo', title: '设备编号'},
  {key: 'addr', title: '设备地址'},
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
  type: '',
  roomId: '',
  serialNo: '',
  addr: '',
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
  roomId: {
    required: true,
    message: '请选择展馆'
  },
  serialNo: {
    required: true,
    message: '请输入编号'
  },
  addr: {
    required: true,
    message: '请输入地址'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  serialNo: '',
  type: null,
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
