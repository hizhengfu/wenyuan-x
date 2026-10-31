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
    </template>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :disabled="formType==='view'"
                   :refTable="refPageTable"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
      <n-grid :x-gap="12">
        <n-gi span="16">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
          </n-form-item>
          <n-form-item label="地址" path="address">
            <n-input v-model:value="form.address" placeholder="请输入地址"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="经度" path="lng" :span="12">
          <n-input v-model:value="form.lng" placeholder="请输入经度"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="纬度" path="lat" :span="12">
          <n-input v-model:value="form.lat" placeholder="请输入纬度"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="描述" path="description">
        <n-input type="textarea" v-model:value="form.description" placeholder="请输入描述"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {HAvatar} from '@/components/form/field/upload'
import {add, del, edit, list} from '../../api/place'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'image', title: '缩略图', columnType: 'image', width: '80px', align: 'center'},
  {key: 'address', title: '地址'},
  {key: 'lng', title: '经度', width: '120px'},
  {key: 'lat', title: '纬度', width: '120px'},
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
  image: '',
  roomId: '',
  address: '',
  serialNo: '',
  lat: '',
  lng: '',
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
  image: {
    required: true,
    message: '请输入名称'
  },
  address: {
    required: true,
    message: '请输入地址'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  address: '',
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
