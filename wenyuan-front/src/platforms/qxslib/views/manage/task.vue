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
      <n-form-item label="名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入名称"></n-input>
      </n-form-item>
      <n-grid x-gap="5">
        <n-form-item-gi label="展馆" path="roomId" span="12">
          <h-field-select-room v-model:value="form.roomId" placeholder="请选择展馆"></h-field-select-room>
        </n-form-item-gi>
<!--        <n-form-item-gi label="机器人" path="robotId" span="12">-->
<!--          <h-field-select-robot v-model:value="form.robotId" placeholder="请选择机器人"></h-field-select-robot>-->
<!--        </n-form-item-gi>-->
      </n-grid>
      <n-form-item label="描述" path="description">
        <n-input type="textarea" v-model:value="form.description" placeholder="请输入描述"></n-input>
      </n-form-item>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import HFieldSelectRoom from '../../components/form/field/FieldSelectRoom.vue'
import HFieldSelectRobot from '../../components/form/field/FieldSelectRobot.vue'
import {add, del, edit, list} from '../../api/task'
import {h, ref} from "vue";
import {cloneDeep} from 'lodash-es'
import {RouterLink} from "vue-router";

const columns = [
  {key: 'name', title: '名称', columnType: 'title'},
  {key: 'room.name', title: '展馆'},
  // {key: 'robot.name', title: '机器人'},
  {
    key: 'name', title: '任务配置',
    render(row: any) {
      return h(
        RouterLink,
        {
          to: {
            path: '/manage/designerTask',
            query: {taskId: row.id, roomId: row.roomId, title: row.name}
          }
        },
        {default: () => '任务配置'}
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
  type: '',
  roomId: '',
  robotId: '',
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
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  serialNo: '',
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
