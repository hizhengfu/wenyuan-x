<template>
  <n-popover :show-arrow="false">
    <template #trigger>
      <n-icon class="mr-3" :class="{'flicker':noticeList.length>0}" size="30px"
              :color="noticeList.length>0?'#ff0000':'#ffffff'" v-bind="$attrs">
        <Icon :icon="noticeList.length>0?'mdi:alarm-light':'mdi:alarm-light-off'"></Icon>
      </n-icon>
    </template>
    <n-list style="width: 320px">
      <template #header>
        <span class="font-800">最新异常事件</span>
      </template>
      <n-list-item v-for="(item,index) in noticeList" @click="handleSelect(item)">
        #{{ index }}:{{ item.device.name }}-
        <n-ellipsis style="max-width: 240px">{{ item.content }}</n-ellipsis>
        ,
        发生时间：
        <n-time :time="item.createTime" type="relative"></n-time>
      </n-list-item>
      <n-list-item v-if="noticeList.length===0">
        暂无异常事件
      </n-list-item>
      <template #footer>
        <div style="text-align: right">
          <router-link :to="{path:'/manage/alarmEvent',query:{title:'预警查询',menuId:'mu-1'}}">查看更多</router-link>
        </div>
      </template>
    </n-list>
  </n-popover>
  <h-dialog-form v-model:show="dialogShow"
                 :rules="rules"
                 :model="form"
                 :handleSubmit="handleSubmit"
                 :title="formTitle">
    <n-descriptions label-placement="top" :column="2">
      <n-descriptions-item label="设备">
        {{ activeAlarmEvent.device.name }}
      </n-descriptions-item>
      <n-descriptions-item label="异常时间">
        <n-time :time="activeAlarmEvent.createTime"></n-time>
      </n-descriptions-item>
      <n-descriptions-item label="异常原因">
        {{ activeAlarmEvent.content }}
      </n-descriptions-item>
    </n-descriptions>
    <n-divider></n-divider>
    <n-form-item label="处理意见" path="reason">
      <n-input type="textarea" v-model:value="form.reason" placeholder="请输入处理意见"></n-input>
    </n-form-item>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import {useAuthStore} from "@/store";
import {Icon} from '@iconify/vue'
import {useRouter} from "vue-router";
import {list, dispose} from '../api/alarmEvent'
import {ref, watchEffect} from "vue";
import {cloneDeep} from "lodash-es";
import {useAlarmStatusStore} from '../store/alarmStatus'

const authStore = useAuthStore()
const router = useRouter()
const useAlarm = useAlarmStatusStore()

const formTitle = ref('')
const noticeList = ref<any[]>([])
const dialogShow = ref(false)
const activeAlarmEvent = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  reason: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

const rules = {
  reason: {
    required: true,
    message: '请输入处置意见'
  },
}

function handleSelect(item: any) {
  form.value = cloneDeep(defaultForm)
  dialogShow.value = true
  formTitle.value = `编辑「${item.content}」`
  form.value.id = item.id
  activeAlarmEvent.value = item
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  return dispose(data).then(() => {
    fetchData()
  })
}

function fetchData() {
  list({status: 0, pageSize: 5}).then((res) => {
    noticeList.value = res.data.list || []
  })
}

fetchData()
let count = useAlarm.count
watchEffect(() => {
  if (useAlarm.count > count) {
    fetchData()
    count = useAlarm.count
  }
})
</script>
<style>
.flicker {
  -webkit-animation-name: flicker;
  -webkit-animation-duration: 1s;
  -webkit-animation-iteration-count: infinite;
  -webkit-animation-direction: alternate;
  -webkit-animation-timing-function: ease;
  -webkit-animation-play-state: running;
}

@-webkit-keyframes flicker {
  0% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
  100% {
    opacity: 0;
  }
}
</style>
