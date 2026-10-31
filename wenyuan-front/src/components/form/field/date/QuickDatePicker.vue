<template>
  <div style="display: flex;flex-direction: row;align-items: center;">
    <template v-if="!modeltime">
      <template v-if="model === 'picker'">
        <n-button @click="handleDatePicker" size="small" class="mr-2">
          <n-icon class="date-btn-icon">
            <IconMdiCalendarRangeOutline />
          </n-icon>
        </n-button>
        <n-popover trigger="manual" class="mr-1 date-popover" placement="bottom" v-model:show="showDatePicker"
          v-bind="$attrs" :x="datePickerX" :y="datePickerY" :show-arrow="false">
          <n-date-picker v-model:value="dateValue" :type="dateType" panel input-readonly :onConfirm="datePickerChange"
            :is-date-disabled="disablePreviousDate" clearable @update-value="handleChangeDataValue" />
        </n-popover>
      </template>
      <n-date-picker v-else class="mr-1" v-model:value="dateValue" :type="dateType" v-bind="$attrs" input-readonly
        :is-date-disabled="disablePreviousDate" @update-value="handleChangeDataValue" clearable>
      </n-date-picker>
    </template>
    <n-date-picker v-else class="mr-1" v-model:value="dateValue[0]" type="date" v-bind="$attrs" input-readonly
      :is-date-disabled="disablePreviousDate" @update-value="handleChangeDataValue" format="yyyy-MM-dd">
    </n-date-picker>
    <div v-if="showtimerange && modeltime" style="display: flex;justify-content: center;align-items: center;">
      &nbsp;<n-time-picker v-model:value="starttimevalue" />
      &nbsp;~&nbsp;<n-time-picker v-model:value="endtimevalue" />&nbsp;&nbsp;
    </div>
    <n-radio-group v-model:value="selectDateType" @change="handleInitDateType" size="small">
      <n-radio-button v-if="showTime" value="time">
        时
      </n-radio-button>
      <n-radio-button v-if="showDate" value="date">
        日
      </n-radio-button>
      <n-radio-button v-if="showMonth" value="month">
        月
      </n-radio-button>
      <n-radio-button v-if="showYear" value="year">
        年
      </n-radio-button>
    </n-radio-group>
    <n-checkbox v-if="modeltime" v-model:checked="showtimerange" style="margin-left: 12px;">
      根据时间段筛选
    </n-checkbox>
  </div>
</template>
<script lang="ts" setup>
import { inject, PropType, ref, watch } from "vue";
import dayjs from 'dayjs';
import { DatePickerType } from "naive-ui/es/date-picker/src/config";

/**
 * 快速时间选择器参数
 * */
const props = defineProps({
  // model: { type: String as PropType<'picker' | 'panel'>, default: 'picker' },//是否显示时间选择 picker panel 面板
  model: { type: String, default: 'picker' },//是否显示时间选择 picker panel 面板
  type: { type: String as PropType<'range' | 'single'>, default: 'range' },//日期类型//range 区间 single 单选
  // defaultType: { type: String as PropType<'time' | 'date' | 'month' | 'year'>, default: 'date' },//默认选择类型
  defaultType: { type: String, default: 'date' },//默认选择类型
  showDate: { type: Boolean, default: true },//显示日选择
  showMonth: { type: Boolean, default: true },//显示月选择
  showYear: { type: Boolean, default: true },//显示年选择
  showTime: { type: Boolean, default: false },//显示小时分钟
  showWeek: { type: Boolean, default: false },//显示小时分钟
})

const modeltime = ref(false)
const showtimerange = ref(true)
const starttimevalue = ref(dayjs().startOf('day').add(9, 'hour').valueOf())
const endtimevalue = ref(dayjs().startOf('day').add(17, 'hour').valueOf())

watch(showtimerange, (newVal) => {
  if (newVal) {
    dateValue.value = [starttimevalue.value, endtimevalue.value]
  } else {
    // 取消时间段筛选后，默认转变为选择的该天开始至结束
    dateValue.value = [dayjs(starttimevalue.value).startOf('day').valueOf(), dayjs(endtimevalue.value).endOf('day').valueOf()]
  }
  emits("updateDate", { dateValue: dateValue.value, dateType: dateType.value, dataType: 0 })
})

/**
 * updateDate 时间选择更新调用
 * */
const emits = defineEmits(["updateDate"])

/**
 * 时间类型
 * */
// const dateType = ref<DatePickerType>()
const dateType = ref()

/**
 * 时间选择
 * */
const showDatePicker = ref(false)
/**
 * 时间值存储
 * */
const dateValue = ref()


/**
 * 禁用今日之前时间
 * */
function disablePreviousDate(ts: number) {
  return ts > dayjs().endOf("day").valueOf()
}

/**
 * 时间选择事件
 * */
function datePickerChange() {
  showDatePicker.value = false
}

/**
 * 时间选择后触发
 * */
function handleChangeDataValue(value: number | [number, number] | null | any, formattedValue: string | [string, string] | null) {
  /**
   * dateValue 选择的时间
   * dateType 选择的时间类型 DatePickerType
   * */
  let dataType = 0
  if (dateType.value === 'date' || dateType.value === 'daterange') {
    dataType = 1
  } else if (dateType.value === 'month' || dateType.value === 'monthrange') {
    dataType = 2
  } else if (dateType.value === 'year' || dateType.value === 'yearrange') {
    dataType = 3
  } else {
    if (showtimerange.value && modeltime.value) {
      starttimevalue.value = dayjs(Array.isArray(value) ? value[0] : value).startOf('day').valueOf() + (starttimevalue.value - dayjs(starttimevalue.value).startOf('day').valueOf())
      endtimevalue.value = dayjs(Array.isArray(value) ? value[1] : value).startOf('day').valueOf() + (endtimevalue.value - dayjs(endtimevalue.value).startOf('day').valueOf())
    } else {
      starttimevalue.value = dayjs(Array.isArray(value) ? value[0] : value).startOf('day').valueOf()
      endtimevalue.value = dayjs(Array.isArray(value) ? value[1] : value).endOf('day').valueOf()
    }
    value = dateValue.value = [starttimevalue.value, endtimevalue.value]
    dataType = 0
  }
  emits("updateDate", { dateValue: value, dateType: dateType.value, dataType: dataType })
}

watch(() => [starttimevalue.value, endtimevalue.value], (newStarttime, newEndtime) => {
  dateValue.value = [starttimevalue.value, endtimevalue.value]
  emits("updateDate", { dateValue: dateValue.value, dateType: dateType.value, dataType: 0 })
})

/**
 * 选择的时间类型
 * */
const selectDateType = ref(props.defaultType)

const datePickerX = ref(0)
const datePickerY = ref(0)
const pageScale = inject("PAGE-SCALE", ref(1))

function handleDatePicker(e: MouseEvent) {
  datePickerX.value = e.x / pageScale.value
  datePickerY.value = e.y / pageScale.value

  showDatePicker.value = !showDatePicker.value
}


/**
 * 切换日期类型并初始化数据
 * */
function handleInitDateType() {
  let now = dayjs()
  dateValue.value = null
  modeltime.value = selectDateType.value === 'time' ? true : false

  if (props.type === 'range') {
    if (selectDateType.value === 'time') {
      dateValue.value = [starttimevalue.value, endtimevalue.value]
      // console.log('起止2', dateValue.value, dayjs(starttimevalue.value).format('YYYY-MM-DD HH:mm:ss'), dayjs(endtimevalue.value).format('YYYY-MM-DD HH:mm:ss'));
      dateType.value = "datetimerange"
    } else if (selectDateType.value === 'date') {
      if (props.showWeek) {
        dateValue.value = [now.startOf('date').subtract(6, 'day').valueOf(), now.endOf('date').valueOf()]
      } else {
        dateValue.value = [now.startOf('date').valueOf(), now.endOf('date').valueOf()]
      }
      dateType.value = "daterange"
    } else if (selectDateType.value === 'month') {
      dateValue.value = [now.startOf('month').valueOf(), now.endOf('month').valueOf()]
      dateType.value = "monthrange"
    } else if (selectDateType.value === 'year') {
      dateValue.value = [now.startOf('year').valueOf(), now.endOf('year').valueOf()]
      dateType.value = "yearrange"
    }
  } else {
    if (selectDateType.value === 'time') {
      dateValue.value = now.startOf('date').valueOf()
      dateType.value = "date"
    } else if (selectDateType.value === 'date') {
      dateValue.value = now.startOf('date').valueOf()
      dateType.value = "date"
    } else if (selectDateType.value === 'month') {
      dateValue.value = now.startOf('date').valueOf()
      dateType.value = "month"
    } else if (selectDateType.value === 'year') {
      dateValue.value = now.startOf('date').valueOf()
      dateType.value = "year"
    }
  }
  handleChangeDataValue(dateValue.value, null)
}

handleInitDateType()
</script>
