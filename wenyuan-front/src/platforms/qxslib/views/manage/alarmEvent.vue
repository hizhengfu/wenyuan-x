<template>
  <h-page-data-table ref="refPageTable"
                     :fetch-data="list"
                     :scroll-x="1200"
                     :params="paramsForm"
                     :isAdd="false"
                     :isEdit="false"
                     @rowView="handleView"
                     :funApiDelete="del"
                     :columns="columns">
    <template #queryForm>
      <n-form-item label="设备" path="deviceId" style="width: 220px">
        <h-field-select-device clearable v-model:value="paramsForm.deviceId"
                               placeholder="请选择设备"></h-field-select-device>
      </n-form-item>
      <n-form-item label="状态" path="status" style="width: 220px">
        <h-dict-select type="mu_alarm_status" clearable v-model:value="paramsForm.status"
                       placeholder="请选择状态"></h-dict-select>
      </n-form-item>
      <n-form-item label="数据日期" path="dateTimeRange">
        <n-date-picker v-model:value="paramsForm.dateTimeRange" type="datetimerange" clearable/>
      </n-form-item>
    </template>
    <template #toolbar-extra>
      <n-tooltip trigger="hover">
        <template #trigger>
          <n-button @click="handleDeleteAll" type="error">
            <n-icon size="20px">
              <icon-mdi-delete></icon-mdi-delete>
            </n-icon>
          </n-button>
        </template>
        删除全部
      </n-tooltip>
    </template>
    <h-dialog-view-form :model="form"
                        label-width="auto"
                        :disabled="formDisabled"
                        :title="formTitle"
                        v-model:show="formModel">

      <n-descriptions label-placement="top" bordered :column="2">
        <n-descriptions-item label="设备名称" label-style="{width: 100px}">
          {{ form.device?.name }}
        </n-descriptions-item>
        <n-descriptions-item label="异常时间">
          <n-time :time="form.dateTime"></n-time>
        </n-descriptions-item>
        <n-descriptions-item label="异常内容">
          {{ form.content }}
        </n-descriptions-item>
      </n-descriptions>
      <n-card class="mt-3"
              v-if="alarmEventLog.length>0"
              :segmented="{
                         content: 'soft',
                         footer: 'soft'
                 }"
              title="处置记录">
        <n-timeline>
          <n-timeline-item v-for="item in alarmEventLog"
                           :type="item.status==='处置完成'?'success':'info'"
                           :title="item.status"
                           :time="formatTime(item.createTime)"
                           :content="item.reason"/>
        </n-timeline>
      </n-card>
    </h-dialog-view-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {del, logs, list, deleteAll} from '../../api/alarmEvent'
import HFieldSelectDevice from '../../components/form/field/FieldSelectDevice.vue'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'
import dayjs from 'dayjs';
import {confirm} from "@/utils";

const columns = [
  {key: 'device.name', title: '设备名称', columnType: 'title'},
  {key: 'status', title: '处理状态', dictType: 'mu_alarm_status'},
  {key: 'code', title: '异常码'},
  {key: 'content', title: '异常内容', ellipsis: true},
  {key: 'dateTime', title: '数据时间', columnType: 'dateTime', width: '220px', align: 'center'},
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
const formDisabled = ref(true)
const alarmEventLog = ref<any[]>([])

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  device: {name: ''},
  level: 0,
  code: '',
  content: '',
  dateTime: undefined,
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 查询表单
 * */
const paramsForm = ref({
  deviceId: null,
  status: null,
  dateTimeRange: null,
})

const formType = ref("view")

/**
 * 查看事件
 * */
function handleView(row: any) {
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.device.name}」`
  formModel.value = true

  logs(row.id).then((res) => {
    alarmEventLog.value = res.data || []
  })
}

function formatTime(value: number) {
  return dayjs(value).format("YYYY-MM-DD HH:mm:ss")
}

function handleDeleteAll() {
  confirm({
    title: '即将删除！',
    content: `即将删除所有内容，请确认。`,
    onPositiveClick() {
      deleteAll().then(() => {
        refPageTable?.value.handleRefresh()
      })
    }
  })
}

</script>
