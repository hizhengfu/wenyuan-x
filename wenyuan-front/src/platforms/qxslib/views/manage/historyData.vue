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
      <n-form-item label="数据日期" path="dateTimeRange">
        <n-date-picker v-model:value="paramsForm.dateTimeRange" type="datetimerange" clearable/>
      </n-form-item>
    </template>
    <h-dialog-view-form :model="form"
                        label-width="auto"
                        :title="formTitle"
                        :refTable="refPageTable"
                        v-model:show="formModel">
      <n-descriptions label-placement="left" bordered :column="1">
        <n-descriptions-item label="名称" label-style="{width: 120px}">
          {{ form.device?.name }}
        </n-descriptions-item>
        <n-descriptions-item label="示数">
          {{ form.dataValue0 }}
        </n-descriptions-item>
        <n-descriptions-item label="采集时间">
          <n-time :time="form.dateTime"></n-time>
        </n-descriptions-item>
      </n-descriptions>
    </h-dialog-view-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {del, list} from '../../api/historyData'
import HFieldSelectDevice from '../../components/form/field/FieldSelectDevice.vue'
import {ref} from "vue";
import {cloneDeep} from 'lodash-es'

const columns = [
  {key: 'device.name', title: '设备名称', columnType: 'title'},
  {key: 'dataValue0', title: '示数'},
  {key: 'dateTime', title: '数据时间', columnType: 'dateTime', width: '220px', align: 'center'},
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
  deviceId: '',
  device: {name: ''},
  dataValue0: null,
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
  dateTimeRange: null,
})

const formType = ref("view")

/**
 * 查看事件
 * */
function handleView(row: any) {
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.device.name}」`
  formModel.value = true
}

</script>
