<template>
  <div v-if="position" class="app-visual-operate" :style="position" ref="el">
    <div style="position: absolute;right: -5px;top: -5px">
      <n-button circle color="#ffffff" small @click="closeDialog">
        <template #icon>
          <n-icon color="#ff0000">
            <Icon icon="mdi:close"></Icon>
          </n-icon>
        </template>
      </n-button>
    </div>
    <n-radio-group v-model:value="selectRobot">
      <n-space>
        <n-radio v-for="robot in robotList" :key="robot.robot_sn" :value="robot.robot_sn">
          <span style="color: #FFFFFF">{{ robot.robot_sn }}</span>
          <n-tag :type="robot.idleTask?'success':'error'">
            {{ robot.idleTask ? '空闲' : '繁忙' }}
          </n-tag>
        </n-radio>
      </n-space>
    </n-radio-group>

    <div style="display: flex;flex-direction: column">
      <n-button-group vertical class="mb-5">
        <n-button type="warning" @click="handleControlRobot('position')">派到这里</n-button>
        <n-button color="#8a2be2" @click="handleControlRobot('home')">回充电桩</n-button>
        <n-button type="success" @click="handleFun('speech')">语音播报</n-button>
        <n-button type="error" @click="clearTask()">清除待执行任务</n-button>
        <!--        <n-button type="info" @click="handleFun('tour')">展厅导览</n-button>-->
        <!--        <n-button type="warning" @click="handleFun('task')">任务执行</n-button>-->
      </n-button-group>

      任务处理
      <n-button-group size="small">
        <n-button type="success" @click="handleControlRobot('continue')">继续所有任务</n-button>
        <n-button type="warning" @click="handleControlRobot('pause')">暂停所有任务</n-button>
        <n-button type="error" @click="handleControlRobot('stop')">取消所有任务</n-button>
      </n-button-group>
    </div>
    <h-dialog-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
      <n-form-item label="机器人" path="serialNo">
        <h-field-select-robot v-model:value="form.serialNo" value-field="serialNo"
                              placeholder="请选择机器人"></h-field-select-robot>
      </n-form-item>
      <n-form-item label="选择任务" path="taskId" v-if="funCmd==='task'">
        <h-field-select-task v-model:value="form.taskId" placeholder="请选择任务"></h-field-select-task>
      </n-form-item>
      <n-form-item label="选择导览" path="tourId" v-if="funCmd==='tour'">
        <h-field-select-tour v-model:value="form.tourId" placeholder="请选择导览"></h-field-select-tour>
      </n-form-item>
      <n-form-item label="语音播报内容" path="speech" v-if="funCmd==='speech'">
        <n-input type="textarea" v-model:value="form.speech" placeholder="请输入播报内容"></n-input>
      </n-form-item>
    </h-dialog-form>
  </div>
</template>
<script lang="ts" setup>
import {useEventPointStore} from "../store/visualOperate";
import {useRoomStatusStore} from '../store/roomStatus'
import {computed, ref} from "vue";
import {useElementSize} from '@vueuse/core'
import {clearRobotTask, controlRobot} from "@/platforms/qxslib/api/controlRobot";
import {cloneDeep} from "lodash-es";
import {listPoint} from "../api/layer";
import HFieldSelectRobot from './form/field/FieldSelectRobot.vue'
import HFieldSelectTask from './form/field/FieldSelectTask.vue'
import HFieldSelectTour from './form/field/FieldSelectTour.vue'
import {Icon} from '@iconify/vue';
import {px2physics} from '../utils'

const el = ref()
const {width, height} = useElementSize(el)

const point = useEventPointStore()
const roomStatusStore = useRoomStatusStore()

const position = computed(() => {
  if (!point.pagePoint) return null

  let top = point.pagePoint.y
  if (top + height.value > window.innerHeight) {
    top -= height.value
  }
  let left = point.pagePoint.x
  if (left + width.value > window.innerWidth) {
    left -= width.value
  }
  return {top: top + 'px', left: left + 'px'}
})

const selectRobot = ref()

const robotList = computed(() => {
  return roomStatusStore.robot?.filter((item) => {
    if (item.position && !selectRobot.value) {
      selectRobot.value = item.robot_sn
    }
    return item.position
  })
})

let point360List: any[] = []

/**
 * 获取图层点
 * */
function fetchPoint(layerId: string) {
  listPoint(layerId).then((resp) => {
    point360List = resp.data || []
  })
}

function closeDialog() {
  point.pagePoint = undefined
}

function clearTask() {
  clearRobotTask(selectRobot.value)
}

function handleControlRobot(cmd: string) {
  let command = {robot_sn: selectRobot.value}
  if (cmd === 'position') {
    if (point.mapPoint?.x && point.mapPoint?.y) {
      let p
      if (point.type === 'PANORAMA') {
        let currentScene = point360List.find((item) => {
          return item.scene === point.scene
        })
        p = px2physics(currentScene.y, currentScene.x)
      } else {
        p = px2physics(point.mapPoint?.x, point.mapPoint?.y)
      }
      Object.assign(command, {task_info: [{task_type: 'position', task_order: {x: p.x, y: p.y, rotation: 0}}]})
    }
  } else if (cmd === 'continue' || cmd === 'pause' || cmd === 'stop' || cmd === 'home') {
    Object.assign(command, {task_info: [{task_type: 'force', task_order: cmd}]})
  }

  console.log(111, JSON.stringify(command))
  controlRobot(command)
}


function handleFun(cmd: string) {
  if (cmd === 'tour') {
    formTitle.value = '展厅导览'
  } else if (cmd === 'task') {
    formTitle.value = '任务执行'
  } else if (cmd === 'speech') {
    formTitle.value = '语音播报'
  }
  funCmd.value = cmd
  formModel.value = true
}

const formModel = ref(false)
const formTitle = ref()
const funCmd = ref()
const rules = {
  robotId: {
    required: true,
    message: '请选择机器人'
  },
  taskId: {
    required: true,
    message: '请选择任务'
  },
  tourId: {
    required: true,
    message: '请选择导览点'
  },
  speech: {
    required: true,
    message: '请输入语音文字'
  },
}
/**
 * 表单默认值
 * */
const defaultForm = {
  serialNo: '',
  taskId: '',
  tourId: '',
  speech: '',
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

function handleSubmit(data: any) {
  let command = {robot_sn: data.serialNo}
  if (funCmd.value === 'speech') {
    Object.assign(command, {task_info: [{task_type: 'speech', task_order: {text: data.speech}}]})
  } else if (funCmd.value === 'task') {

  }
  // console.log(1122, command)
  return controlRobot(command)
}

//获取全景坐标点
fetchPoint('1578414889890066432')

</script>
<style>
.app-visual-operate {
  position: absolute;
  z-index: 999;
  max-width: 300px;
  padding: 15px;
  background-color: #00000088;
  user-select: none;
}
</style>

