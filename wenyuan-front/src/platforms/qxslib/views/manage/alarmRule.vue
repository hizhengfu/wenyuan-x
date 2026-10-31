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
      <n-form-item label="类型" path="type" style="width: 120px">
        <h-dict-select type="mu_alarm_rule_type" clearable v-model:value="paramsForm.type"
                       placeholder="请选择类型"></h-dict-select>
      </n-form-item>
      <n-form-item label="状态" path="status" style="width: 120px">
        <h-dict-select type="content_status" clearable v-model:value="paramsForm.status"
                       placeholder="请选择类型"></h-dict-select>
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button @click="formPeopleModel=true">
            <n-icon size="20px">
              <icon-mdi-cogs></icon-mdi-cogs>
            </n-icon>
          </n-button>
        </template>
        设置设备人流阈值
      </n-tooltip>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button @click="formRasterModel=true">
            <n-icon size="20px">
              <IconMdiTransitTransfer></IconMdiTransitTransfer>
            </n-icon>
          </n-button>
        </template>
        设置设备人流阈值
      </n-tooltip>
    </template>
    <h-page-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 :title="formTitle"
                 :disabled="formType==='view'"
                 :refTable="refPageTable"
                 :handleSubmit="handleSubmit"
                 v-model:show="formModel">
      <n-form-item label="类型" path="type">
        <h-dict-radio type="mu_alarm_rule_type" v-model:value="form.type" placeholder="请选择字典"></h-dict-radio>
      </n-form-item>
      <n-form-item label="阈值" path="value">
        <n-input-number v-model:value="form.value" placeholder="请输入阈值"></n-input-number>
      </n-form-item>
      <n-form-item label="描述" path="content">
        <n-input type="textarea" v-model:value="form.content" placeholder="请输入规则描述"></n-input>
      </n-form-item>
      <n-form-item label="状态" path="status" :span="6">
        <h-dict-radio type="content_status" v-model:value="form.status" placeholder="请选择状态"></h-dict-radio>
      </n-form-item>
    </h-page-form>

    <h-dialog-form :rules="rulesPeople"
                   :model="formPeople"
                   label-width="auto"
                   title="设置场地客流阈值"
                   :handleSubmit="handleSetting"
                   v-model:show="formPeopleModel">
      <n-form-item label="场地" path="place">
        <n-input v-model:value="formPeople.place" placeholder="请输入场地"></n-input>
      </n-form-item>
      <n-form-item label="阈值" path="number">
        <n-input-number v-model:value="formPeople.number" placeholder="请输入人流阈值"></n-input-number>
      </n-form-item>
      <n-form-item label="停留时长" path="timeout">
        <n-input-number v-model:value="formPeople.timeout" placeholder="请输入停留时长"></n-input-number>
      </n-form-item>
    </h-dialog-form>

    <h-dialog-form :rules="rulesRaster"
                   :model="formRaster"
                   label-width="auto"
                   title="设置光栅参数"
                   :handleSubmit="handleRasterConfig"
                   v-model:show="formRasterModel">
      <n-form-item label="连续触发时间" path="continuous_trigger_time">
        <n-input-number v-model:value="formRaster.continuous_trigger_time" placeholder="请输入连续触发时间">
          <template #suffix>
            /秒
          </template>
        </n-input-number>
      </n-form-item>
      <n-form-item label="周期时间" path="cycle_time">
        <n-input-number v-model:value="formRaster.cycle_time" placeholder="请输入周期时间">
          <template #suffix>
            /分钟
          </template>
        </n-input-number>
      </n-form-item>
      <n-form-item label="周期累积触发时长" path="cumulative_trigger_time">
        <n-input-number v-model:value="formRaster.cumulative_trigger_time" placeholder="请输入周期内累积触发时长">
          <template #suffix>
            /次
          </template>
        </n-input-number>
      </n-form-item>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {add, del, edit, list} from '../../api/alarmRule'
import {peopleNumber, rasterConfig} from '../../api/controlRobot'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'type', title: '类型', dictType: 'mu_alarm_rule_type'},
  {key: 'value', title: '阈值', columnType: 'title'},
  {key: 'status', title: '类型', dictType: 'content_status'},
  {key: 'createTime', title: '创建时间', columnType: 'date', width: '220px', align: 'center'},
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
const formPeopleModel = ref(false)
const formRasterModel = ref(false)
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
  value: 5,
  type: '0',
  status: '0',
  content: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))


/**
 * 表单默认值
 * */
const defaultPeopleForm = {
  place: "大厅",
  number: 5,
  timeout: 60,
}

/**
 * 表单
 * */
const formPeople = ref(cloneDeep(defaultPeopleForm))
const rulesPeople = {
  place: {
    required: true,
    message: '请输入场所地址'
  },
  number: {
    required: true,
    message: '请输入阈值'
  },
  timeout: {
    required: true,
    message: '请输入停留时长'
  }
}


/**
 * 表单默认值
 * */
const defaultRasterForm = {
  continuous_trigger_time: 5,
  cycle_time: 1,
  cumulative_trigger_time: 20,
}

/**
 * 表单
 * */
const formRaster = ref(cloneDeep(defaultRasterForm))
const rulesRaster = {
  continuous_trigger_time: {
    required: true,
    message: '请输入连续触发时间'
  },
  cycle_time: {
    required: true,
    message: '请输入周期时间'
  },
  cumulative_trigger_time: {
    required: true,
    message: '请输入周期内累积触发时长'
  }
}

/**
 * 验证规则
 * */
const rules = {
  value: {
    required: true,
    message: '请输入阈值'
  },
  type: {
    required: true,
    message: '请选择类型'
  },
  status: {
    required: true,
    message: '请选择状态'
  }
}
/**
 * 查询表单
 * */
const paramsForm = ref({
  status: null,
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
  formTitle.value = `编辑规则`
  formModel.value = true
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  formTitle.value = `查看规则`
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

function handleSetting(data: any) {
  return peopleNumber(data)
}

function handleRasterConfig(data: any) {
  return rasterConfig(data)
}
</script>
