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
    <template #toolbar-extra>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button @click="handleSync">
            <n-icon size="20px">
              <icon-mdi-database-import-outline></icon-mdi-database-import-outline>
            </n-icon>
          </n-button>
        </template>
        点击同步
      </n-tooltip>
    </template>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :disabled="formType==='view'"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
      <n-grid :x-gap="12">
        <n-gi span="16">
          <n-form-item label="名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
          </n-form-item>
          <n-form-item label="序号" path="serialNo">
            <n-input v-model:value="form.serialNo" placeholder="请输入序号"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="8" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="场馆" path="roomId" :span="12">
          <h-field-select-room v-model:value="form.roomId" placeholder="请选择展馆"></h-field-select-room>
        </n-form-item-gi>
        <n-form-item-gi label="等待时间" path="wait" :span="12">
          <n-input-number v-model:value="form.wait" min="0" placeholder="请输入等待时间"></n-input-number>
        </n-form-item-gi>
      </n-grid>
      <n-grid :x-gap="12">
        <n-form-item-gi label="出发前提示" path="preHint" :span="12">
          <n-input v-model:value="form.preHint" placeholder="请输入出发前提示"></n-input>
        </n-form-item-gi>
        <n-form-item-gi label="到达提示" path="finalHint" :span="12">
          <n-input v-model:value="form.finalHint" placeholder="请输入到达提示"></n-input>
        </n-form-item-gi>
      </n-grid>
      <n-form-item label="途中讲解" path="inTheWay">
        <n-input type="textarea" v-model:value="form.inTheWay" placeholder="请输入途中讲解"></n-input>
      </n-form-item>
      <n-form-item label="讲解词" path="commentary">
        <n-input type="textarea" v-model:value="form.commentary" placeholder="请输入讲解词"></n-input>
      </n-form-item>
      <n-grid :x-gap="12">
        <n-gi span="12">
          <n-form-item label="音频文件" path="audioPath">
            <h-upload-input v-model:value="form.audioPath" placeholder="请输入音频文件"></h-upload-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="视频文件" path="videoPath">
            <h-upload-input v-model:value="form.videoPath" placeholder="请输入视频文件"></h-upload-input>
          </n-form-item>
        </n-gi>
      </n-grid>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HFieldSelectRoom from '../../components/form/field/FieldSelectRoom.vue'
import {HAvatar, HUploadInput} from '@/components/form/field/upload'
import {add, del, edit, list, syncData} from '../../api/tour'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'
import {confirm} from "@/utils";

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'image', title: '缩略图', columnType: 'image', width: '80px', align: 'center'},
  {key: 'serialNo', title: '序号'},
  {key: 'room.name', title: '场馆'},
  {key: 'preHint', title: '出发前提示', ellipsis: {tooltip: true}},
  {key: 'inTheWay', title: '途中讲解', ellipsis: {tooltip: true}},
  {key: 'finalHint', title: '到达提示', ellipsis: {tooltip: true}},
  {key: 'commentary', title: '讲解词', ellipsis: {tooltip: true}},
  {key: 'wait', title: '等待时间'},
  // {key: 'createTime', title: '创建时间', columnType: 'date', width: '220px', align: 'center'},
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
  roomId: null,
  serialNo: '',
  preHint: '',
  inTheWay: '',
  finalHint: '',
  commentary: '',
  wait: 0,
  audioPath: '',
  videoPath: '',
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
    message: '请上传图像'
  },
  serialNo: {
    required: true,
    message: '请输入序号'
  },
  roomId: {
    required: true,
    message: '请选择场馆'
  },
  commentary: {
    required: true,
    message: '请输入讲解词'
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
 * 数据同步
 * */
function handleSync() {
  confirm({
    title: '导览同步！',
    content: `确认后将同步一期导览点到系统，并清除已有数据，请确认。`,
    onPositiveClick() {
      syncData().then(() => {
        refPageTable?.value.handleRefresh()
      })
    }
  })
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
