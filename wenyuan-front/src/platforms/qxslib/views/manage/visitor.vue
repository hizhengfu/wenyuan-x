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
      <n-form-item label="身份证编号" path="idCard">
        <n-input v-model:value="paramsForm.idCard" placeholder="身份证编号"></n-input>
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
      <n-grid x-gap="10">
        <n-gi :span="16">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
          </n-form-item>
          <n-form-item label="性别" path="gender">
            <h-dict-radio type="sex_type" v-model:value="form.gender" placeholder="请选择性别"></h-dict-radio>
          </n-form-item>
        </n-gi>
        <n-gi :span="8" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid x-gap="10">
        <n-gi :span="12">
          <n-form-item label="身份证" path="idCard">
            <n-input v-model:value="form.idCard" placeholder="请输入身份证"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi :span="12">
          <n-form-item label="VIP" path="vipLevel">
            <n-input-number type="number" v-model:value="form.vipLevel" placeholder="请输入VIP等级"></n-input-number>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-form-item label="描述" path="description">
        <n-input type="textarea" v-model:value="form.description" placeholder="请输入描述"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {HAvatar} from '@/components/form/field/upload'
import HFieldSelectRoom from '../../components/form/field/FieldSelectRoom.vue'
import {add, del, edit, list} from '../../api/visitor'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'image', title: '头像', columnType: 'image', align: 'center'},
  {key: 'idCard', title: '身份证'},
  {key: 'vipLevel', title: 'VIP等级'},
  {key: 'gender', title: '性别', dictType: 'sex_type', align: 'center'},
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
  idCard: '',
  vipLevel: 1,
  description: '',
  gender: '',
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
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  idCard: '',
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
