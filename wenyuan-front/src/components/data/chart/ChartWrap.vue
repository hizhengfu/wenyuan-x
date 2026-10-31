<template>
  <n-card v-show="chartWrapShow" v-bind="$attrs" class="chart-wrap" :content-style="{paddingTop:0}">
    <template #header-extra>
      <slot name="headerToolbar"></slot>
      <template v-if="showToolbar">
        <template v-if="dateModel === 'picker'">
          <n-button :size="size" class="mr-2" @click="handleDatePicker">
            <n-icon class="date-btn-icon">
              <IconMdiCalendarRangeOutline/>
            </n-icon>
          </n-button>
          <!-- icon式时间选择器 -->
          <n-popover
              v-model:show="showDatePicker" trigger="manual" class="mr-1 date-popover"
              placement="bottom"
              :x="datePickerX"
              :y="datePickerY"
              :on-clickoutside="()=>showDatePicker=false"
              :show-arrow="false"
          >
            <n-date-picker
                v-model:value="dateValue"
                :type="dateType"
                panel
                input-readonly
                :style="{'--date-picker':'translateX(0px);translateY(52px)'}"
                :on-confirm="datePickerChange"
                :is-date-disabled="disablePreviousDate" clearable @update-value="handleChangeDataValue"/>
          </n-popover>
        </template>
        <!-- range式时间选择器 -->
        <n-date-picker
            v-else v-model:value="dateValue"
            class="mr-1"
            :type="dateType"
            :to="false"
            input-readonly
            :is-date-disabled="disablePreviousDate"
            clearable
            @update-value="handleChangeDataValue"/>
        <n-radio-group v-model:value="selectDateType" :size="size" @change="handleInitDateType">
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
      </template>
    </template>
    <template v-if="$slots.header" #header>
      <slot name="header"></slot>
    </template>
    <div class="chart-wrap-content">
      <slot name="default" :data="data"></slot>
    </div>
    <template #footer>
      <slot name="footer"></slot>
    </template>
  </n-card>
</template>
<script lang="ts" setup>
import {inject, onActivated, onDeactivated, PropType, provide, ref, watchEffect} from 'vue';
import {DatePickerType} from 'naive-ui/es/date-picker/src/config';
import {LOADING_OPTIONS_KEY} from 'vue-echarts';
import dayjs from 'dayjs';
import request from '@/utils/request';
import {useEventBus} from '@vueuse/core';

const pageScale = inject('PAGE-SCALE', ref(1))

const chartWrapShow = inject('CHART_WRAP_SHOW', true)

const props = defineProps({
  dateModel: {type: String as PropType<'picker' | 'panel'>, default: 'picker'},//是否显示时间选择 picker panel 面板
  type: {type: String as PropType<'range' | 'single'>, default: 'range'},//日期类型//range 区间 single 单选
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium'
  },
  showDate: {type: Boolean, default: true},//显示日选择
  showMonth: {type: Boolean, default: true},//显示月选择
  showYear: {type: Boolean, default: true},//显示年选择
  defaultYMD: {type: String as PropType<DatePickerType>, default: 'daterange'},//默认显示年月日高亮
  showToolbar: {type: Boolean, default: true},//显示工具栏
  dataAutoUpdate: {type: Boolean, default: false},//数据自动更新
  option: {//默认数据
    type: Object, default() {
      return undefined
    }
  },
  params: {//参数对象
    type: Object
  },
  // 默认数据获取api
  fetchData: {
    type: Function, default(params: object) {
      return request({
        url: 'analyse/chart',
        params: params,
        method: 'get'
      })
    }
  }
})

/**
 * loadData 数据加载完成调用
 * updateDate 时间选择更新调用
 * refreshData 数据自动刷新调用 对未提供更新函数
 * */
const emits = defineEmits(['loadData', 'updateDate', 'refreshData'])
/**
 * 时间选择
 * */
const showDatePicker = ref(false)
/**
 * 时间类型
 * */
const dateType = ref<DatePickerType>()
/**
 * 选择的时间类型
 * */
const selectDateType = ref('date')
/**
 * 时间数据
 * */
const dateValue = ref()
/**
 * 数据值类型
 * */
const dataType = ref()


/**
 * 禁用之前时间
 * */
function disablePreviousDate(ts: number) {
  return ts > Date.now()
}

function datePickerChange() {
  showDatePicker.value = false
}


/**
 * 切换日期类型并初始化数据
 * */
function handleInitDateType() {
  let now = dayjs()
  dateValue.value = null
  if (props.type === 'range') {
    if (selectDateType.value === 'date') {
      dateValue.value = [now.startOf('date').valueOf(), now.endOf('date').valueOf()]
      dataType.value = 2
      dateType.value = 'daterange'
    } else if (selectDateType.value === 'month') {
      dateValue.value = [now.startOf('month').valueOf(), now.endOf('month').valueOf()]
      dataType.value = 3
      dateType.value = 'monthrange'
    } else if (selectDateType.value === 'year') {
      dateValue.value = [now.startOf('year').valueOf(), now.endOf('year').valueOf()]
      dataType.value = 4
      dateType.value = 'yearrange'
    }
  } else {
    if (selectDateType.value === 'date') {
      dateValue.value = now.startOf('date').valueOf()
      dataType.value = 2
      dateType.value = 'date'
    } else if (selectDateType.value === 'month') {
      dateValue.value = now.startOf('date').valueOf()
      dataType.value = 3
      dateType.value = 'month'
    } else if (selectDateType.value === 'year') {
      dateValue.value = now.startOf('date').valueOf()
      dataType.value = 4
      dateType.value = 'year'
    }
  }
  handleChangeDataValue()
}

/**
 * 初始化值
 * */
handleInitDateType()

/**
 * 时间选择后触发
 * */
function handleChangeDataValue() {
  /**
   * dateValue 选择的时间
   * dataType 选择的时间类型 1 日 2月 3 年
   * */
  emits('updateDate', {dateValue: dateValue.value, dataType: dataType.value})
}

const datePickerX = ref(0)
const datePickerY = ref(0)

function handleDatePicker(e: MouseEvent) {
  datePickerX.value = e.x / pageScale.value
  datePickerY.value = e.y / pageScale.value + 30

  showDatePicker.value = !showDatePicker.value
}


const activating = ref(true)
onActivated(() => {
  activating.value = true
})
onDeactivated(() => {
  activating.value = false
})

/**
 * 全屏定时器
 * */
const screenIntervalBus = useEventBus<Date>('SCREEN-INTERVAL')

/**
 * 接受全屏中自动更新事件
 *
 * */
screenIntervalBus.on((now: Date) => {
  const {dataAutoUpdate} = props
  if (dataAutoUpdate && activating.value) {
    chartWrapFetchData()
    /**
     * 触发数据更新事件
     * */
    emits('refreshData', now)
  }
})


const loadStatus = ref({text: '加载中'})
provide(LOADING_OPTIONS_KEY, loadStatus)

/**
 * 组件内获取数据
 * */
function chartWrapFetchData() {
  const {fetchData, params} = props
  if (fetchData && params) {
    loading.value = true
    data.value = null
    let p = params || {}
    //启用工具栏的情况才考虑日期查询
    if (props.showToolbar) {
      let dv = dateValue.value
      if (props.type === 'range') {
        dv = dateValue.value?.join(',')
      }
      p = Object.assign({}, params, {dateTime: dv, type: dataType.value})
    }
    fetchData(p).then((resp: any) => {
      if (resp.data) {
        data.value = resp.data
      } else {
        loadStatus.value = {text: '暂无数据'}
      }
    }).catch(() => {
      loadStatus.value = {text: '接口异常'}
    }).finally(() => {
      emits('loadData', data.value, {dateValue: dateValue.value, dateType: dateType.value, dataType: dataType.value})
      loading.value = false
    })
  }
}

const data = ref()
const loading = ref(true)
watchEffect(() => {
  const {fetchData, params} = props
  if (fetchData && params) {
    chartWrapFetchData()
  }
})
</script>
<style scoped>
.chart-wrap {
  height: 100%;
  width: 100%;
  overflow: hidden;
  position: absolute;
}

.chart-wrap .n-card-header__extra {
  align-items: center;
  justify-content: flex-end;
  z-index: 999;
}

.chart-wrap .chart-wrap-content, .chart-wrap .chart-wrap-content > div {
  width: 100%;
  height: 100%;
}

</style>

