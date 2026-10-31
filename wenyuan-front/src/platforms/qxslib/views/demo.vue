<template>
  <n-card>
    <h2>机器人位置控制：</h2>
    <n-divider></n-divider>
    <n-form label-placement="top"
            :model="form"
            ref="formRef"
            :rules="rules">
      <n-grid x-gap="10px">
        <n-form-item-gi label="机器人" path="robot_sn" :span="4">
          <n-select v-model:value="form.robot_sn" :options="robotOption" placeholder="请选择机器人"/>
        </n-form-item-gi>
        <n-form-item-gi label="任务时间" path="task_time" :span="4">
          <n-date-picker v-model:value="form.task_time"
                         time-zone="Asia/Shanghai"
                         type="datetime" clearable/>
        </n-form-item-gi>
      </n-grid>
      <n-dynamic-input v-model:value="form.task_info" :on-create="onCreate" show-sort-button>
        <template #create-button-default>
          点击创建任务
        </template>
        <template #default="{ value,index }">
          <n-grid :x-gap="4">
            <n-form-item-gi label="任务类型" style="width: 220px" span="4"
                            :path="`task_info[${index}].task_type`"
                            :rule="{required:true,message:'请选择类型'}">
              <n-select v-model:value="value.task_type"
                        @update:value="()=>{form.task_info[index]=createTaskInfo(value.task_type)}"
                        :options="taskTypeOption" placeholder="请选择类型"/>
            </n-form-item-gi>

            <template v-if="value.task_type==='force'">
              <n-form-item-gi span="8" label="任务"
                              :path="`task_info[${index}].task_order.force`"
                              :rule="{required:true,message:'请选择任务'}">
                <n-radio-group v-model:value="value.task_order.force">
                  <n-space>
                    <n-radio value="stop">停止所有任务</n-radio>
                    <n-radio value="pause">暂停所有任务</n-radio>
                    <n-radio value="continue">继续所有任务</n-radio>
                  </n-space>
                </n-radio-group>
              </n-form-item-gi>
            </template>
            <template v-else-if="value.task_type==='position'">
              <n-form-item-gi span="4" label="x" :path="`task_info[${index}].task_order.x`"
                              :rule="{required:true,message:'请输入x坐标'}">
                <n-input-number v-model:value="value.task_order.x" placeholder="请输入x坐标"/>
              </n-form-item-gi>
              <n-form-item-gi span="4" label="y" :path="`task_info[${index}].task_order.y`"
                              :rule="{required:true,message:'请输入y坐标'}">
                <n-input-number v-model:value="value.task_order.y" placeholder="请输入y坐标"/>
              </n-form-item-gi>
              <n-form-item-gi span="4" label="角度" :path="`task_info[${index}].task_order.rotation`"
                              :rule="{required:true,message:'请输入角度'}">
                <n-input-number v-model:value="value.task_order.rotation" placeholder="请输入角度"/>
              </n-form-item-gi>
            </template>
            <template v-else-if="value.task_type==='speech'">
              <n-form-item-gi span="8" label="播报内容" :path="`task_info[${index}].task_order.text`"
                              :rule="{required:true,message:'请输入播报内容'}">
                <n-input v-model:value="value.task_order.text" placeholder="请输入播报内容"></n-input>
              </n-form-item-gi>
            </template>
            <template v-else-if="value.task_type==='guide'">
              <n-form-item-gi span="4" label="x" :path="`task_info[${index}].task_order.x`"
                              :rule="{required:true,message:'请输入x坐标'}">
                <n-input-number v-model:value="value.task_order.x" placeholder="请输入x坐标"/>
              </n-form-item-gi>
              <n-form-item-gi span="4" label="y" :path="`task_info[${index}].task_order.y`"
                              :rule="{required:true,message:'请输入y坐标'}">
                <n-input-number v-model:value="value.task_order.y" placeholder="请输入y坐标"/>
              </n-form-item-gi>
              <n-form-item-gi span="4" label="角度" :path="`task_info[${index}].task_order.rotation`"
                              :rule="{required:true,message:'请输入角度'}">
                <n-input-number v-model:value="value.task_order.rotation" placeholder="请输入角度"/>
              </n-form-item-gi>
              <n-form-item-gi span="12" label="出发前提示" path="task_order.before_hint">
                <n-input v-model:value="value.task_order.before_hint" placeholder="出发前提示"/>
              </n-form-item-gi>
              <n-form-item-gi span="12" label="途中讲解" path="task_order.explain_way">
                <n-input v-model:value="value.task_order.explain_way" placeholder="途中讲解"/>
              </n-form-item-gi>
              <n-form-item-gi span="12" label="到达后提示" path="task_order.aftet_hint">
                <n-input v-model:value="value.task_order.aftet_hint" placeholder="到达后提示"/>
              </n-form-item-gi>
              <n-form-item-gi span="12" label="到达后讲解" path="task_order.explain">
                <n-input v-model:value="value.task_order.explain" placeholder="到达后讲解"/>
              </n-form-item-gi>
            </template>
          </n-grid>
        </template>
      </n-dynamic-input>
      <n-form-item class="ml-3">
        <n-button type="success" @click="handeSubmit" :loading="isSubmit">发送</n-button>
      </n-form-item>
    </n-form>
    <template v-for="robot in robots">
      <h2>机器人「{{ robot.robot_sn }}」状态：</h2>
      <n-divider></n-divider>
      <div>机器人位置：<span>x={{ robot.position?.x }},y={{ robot.position?.y }},z={{ robot.position?.z }},rotation={{
          robot.position?.rotation
        }},更新时间：{{ fromNow(robot.position?.created) }}</span>
      </div>
      <div>机器人状态：<span>{{ robot.state?.state_msg }}({{ robot.state?.state }}),更新时间：{{
          fromNow(robot.state?.created)
        }}</span></div>
      <div>机器人电量：<span>{{ robot.battery?.value }}%,更新时间：{{ fromNow(robot.battery?.created) }}</span></div>
      <n-collapse>
        <n-collapse-item title="任务信息" name="1">
          <n-table>
            <thead>
            <tr>
              <th>任务ID</th>
              <th>类型</th>
              <th>详情</th>
              <th>状态</th>
              <th>执行时间</th>
            </tr>
            </thead>
            <tbody>
            <template v-for="task in robot.task_list">
              <tr>
                <td :rowspan="task.task_info?.length||1">{{ task.task_id }}</td>
                <td>{{ task.task_info&&task.task_info[0]?.task_type || '--' }}</td>
                <td>{{ task.task_info&&formatValue(task?.task_info[0]?.task_type, task?.task_info[0]?.task_order) }}</td>
                <td :rowspan="task.task_info?.length||1">{{ task.task_state_msg }}({{ task.task_state }})</td>
                <td :rowspan="task.task_info?.length||1">{{ format(task.task_time) }}</td>
              </tr>
              <template v-for="(info,index) in task.task_info">
                <tr v-if="index>0">
                  <td>{{ info.task_type }}</td>
                  <td>{{ formatValue(info.task_type, info.task_order) }}</td>
                </tr>
              </template>

            </template>
            </tbody>
          </n-table>
        </n-collapse-item>
      </n-collapse>
    </template>
    <n-divider></n-divider>
    <n-table>
      <thead>
      <tr>
        <th>光栅ID</th>
        <th>光栅状态</th>
        <th>光栅状态码</th>
        <th>执行时间</th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="raster in rasters">
        <td>{{ raster.raster_id }}</td>
        <td>{{ raster.raster_state?.state_msg }}</td>
        <td>{{ raster.raster_state?.state }}</td>
        <td>{{ format(raster.created) }}</td>
      </tr>
      </tbody>
    </n-table>
    <n-divider></n-divider>
    <n-form-item label="请输入测试视频源(flv格式)">
      <n-input v-model:value="videoUrl"></n-input>
    </n-form-item>
    <h-flv-player v-if="videoUrl" :url="videoUrl"></h-flv-player>
  </n-card>
</template>
<script lang="ts" setup>
import {ref} from "vue";
import {rasterGroup, robotState, sendTAsk} from '../api/demo'
import {notification} from "@/utils";
import {FormInst} from 'naive-ui';
import dayjs from 'dayjs';
import zhCn from 'dayjs/locale/zh-cn'
import relativeTime from 'dayjs/plugin/relativeTime'
import HFlvPlayer from '@/components/basic/player/FlvPlayer.vue'

dayjs.locale(zhCn)
dayjs.extend(relativeTime)

const formRef = ref<(HTMLElement & FormInst) | null>(null);

const videoUrl = ref('')

const robots = ref<Results[]>([])


const position = ref({
  x: 0, y: 0, z: 0, rotation: 0, updated: ''
})


const rasters = ref<RasterResults[]>([])


const statMap = {
  0: '暂停',
  1: '导航中',
  2: '咨询问答'
}

const robotOption = [
  {
    label: "机器人1",
    value: 'RSN_0001',
  },
  {
    label: "机器人2",
    value: 'RSN_0002',
  },
  {
    label: "机器人3",
    value: 'RSN_0003',
  }
]
const taskTypeOption = [
  {
    label: "强制",
    value: 'force',
  },
  {
    label: "位置",
    value: 'position',
  },
  {
    label: "语音播报",
    value: 'speech',
  },
  {
    label: "导览",
    value: 'guide',
  }
]

function fromNow(dateTime?: string) {
  if (!dateTime) return '--'
  return dayjs(dateTime).fromNow()
}

function format(dateTime: string) {
  if (!dateTime) return '--'
  return dayjs(dateTime).format("YYYY-MM-DD HH:mm:ss")
}

//force/position/speech/guide
function formatValue(type: string, taskOrder: any) {
  if (!type || !taskOrder) return '--'
  if (type === 'position') {
    return `x=${taskOrder.x},y=${taskOrder.y},z=${taskOrder.z},rotation=${taskOrder.rotation}`
  } else if (type === 'speech') {
    return taskOrder.text
  } else if (type === 'force') {
    return taskOrder + ""
  } else if (type === 'guide') {
    return `x=${taskOrder.x},y=${taskOrder.y},z=${taskOrder.z},rotation=${taskOrder.rotation},
    before_hint=${taskOrder.before_hint},explain_way=${taskOrder.explain_way},aftet_hint=${taskOrder.aftet_hint},explain=${taskOrder.explain}`
  }
}

const isSubmit = ref(false)


const rules = {
  robot_sn: {
    required: true,
  }
}

const form = ref<any>({
  robot_sn: 'RSN_0001',
  task_time: null,
  task_info: [],
})

function handeSubmit() {
  formRef.value?.validate((errors) => {
    if (!errors) {
      let task_info = form.value.task_info.map((item: any) => {
        if (item.task_type === 'force') {
          return {task_type: item.task_type, task_order: item.task_order.force}
        }
        return item
      })
      let temp = {
        robot_sn: form.value.robot_sn,
        task_time: form.value.task_time ? new Date(form.value.task_time).toISOString() : null,
        // task_type: form.value.task_type,
        // @ts-ignore
        task_info: task_info
      }
      isSubmit.value = true
      // @ts-ignore
      sendTAsk(temp).then((resp) => {

      }).finally(() => {
        isSubmit.value = false
      })
    } else {
      let content = errors.map((item) => {
        return item[0]['message']
      }).join(',')
      notification({
        type: 'error',
        title: '请注意',
        content: content
      })
    }
  })
}

function onCreate() {
  return {task_order: {}}
}

function createTaskInfo(type: string) {
  /*if (type === 'force') {
    return {task_type: type, task_order: ''}
  } else {
    return {task_type: type, task_order: {}}
  }*/
  return {task_type: type, task_order: {}}
}

function syncData() {
  robotState().then((resp: any) => {
    robots.value = resp.results
  })

  rasterGroup().then((resp: any) => {
    rasters.value = resp.results
  })
}

setInterval(() => {
  syncData()
}, 1000)

</script>
