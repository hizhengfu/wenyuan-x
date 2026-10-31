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
      <n-form-item label="类型" path="type" style="width: 120px">
        <h-dict-select type="mu_layer_type" clearable v-model:value="paramsForm.type"
                       placeholder="请选择类型"></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :disabled="formType==='view'"
                   :title="formTitle"
                   :refTable="refPageTable"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-form-item label="类型" path="type">
        <h-dict-select type="mu_layer_type" clearable v-model:value="form.type"
                       placeholder="请选择类型"></h-dict-select>
      </n-form-item>
      <n-form-item label="场馆" path="roomId">
        <field-select-room v-model:value="form.roomId" placeholder="请选择场馆"></field-select-room>
      </n-form-item>
      <n-form-item label="资源路径" path="path">
        <h-upload-input v-model:value="form.path" placeholder="请上传资源"></h-upload-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {HUploadInput} from '@/components/form/field/upload'
import FieldSelectRoom from '../../components/form/field/FieldSelectRoom.vue'
import {add, del, edit, list} from '../../api/layer'
import {h, ref} from "vue";
import {cloneDeep} from 'lodash-es'
import {RouterLink} from "vue-router";

const paths = ['/manage/designer2d', '/manage/designer360', '/manage/designer360Position']
const names = ['平面标记', '全景标记', '全景拍摄位置']

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'room.name', title: '展厅'},
  {key: 'type', title: '类型', dictType: 'mu_layer_type'},
  {
    key: 'path', title: '可视化编辑',
    render(row: any) {
      return h(
        RouterLink,
        {
          to: {
            path: paths[row.type],
            query: {layerId: row.id, roomId: row.roomId, title: row.name}
          }
        },
        {default: () => names[row.type]}
      )
    }
  },
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
  roomId: '',
  type: '',
  path: '',
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
  path: {
    required: true,
    message: '请输入全景地图'
  },
  roomId: {
    required: true,
    message: '请输入选择展馆'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
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
