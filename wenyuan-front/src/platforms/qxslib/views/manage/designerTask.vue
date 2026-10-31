<template>
  <n-layout has-sider>
    <n-layout-sider
      :width="240"
      bordered>
      <n-layout-header bordered style="padding: 15px">
        <n-space justify="space-between" align="center">
          任务列表
          <n-button @click="addTask" circle>
            <template #icon>
              <n-icon>
                <Icon icon="mdi:plus"></Icon>
              </n-icon>
            </template>
          </n-button>
        </n-space>
      </n-layout-header>
      <n-list style="padding: 15px">
        <draggable
          v-model="taskList"
          group="task"
          handle=".handle"
          @end="dragEnd"
          item-key="id">
          <template #item="{element,index}">
            <n-list-item>
              <template #prefix>
                <n-button quaternary circle class="handle">
                  <template #icon>
                    <n-icon>
                      <Icon icon="mdi:cursor-move"></Icon>
                    </n-icon>
                  </template>
                </n-button>
              </template>
              #{{ element.sort }}{{ getTypeName(element.type + "") }}
              <template #suffix>
                <div style="display: flex;flex-direction: row">
                  <n-button quaternary circle @click="editTask(index)">
                    <template #icon>
                      <n-icon>
                        <Icon icon="mdi:edit"></Icon>
                      </n-icon>
                    </template>
                  </n-button>
                  <n-button quaternary circle @click="removeTask(index)">
                    <template #icon>
                      <n-icon>
                        <Icon icon="mdi:delete"></Icon>
                      </n-icon>
                    </template>
                  </n-button>
                </div>
              </template>
            </n-list-item>
          </template>
        </draggable>
      </n-list>
      <h-dialog-form
        :rules="rules"
        :model="form"
        :handleSubmit="handleSubmit"
        v-model:show="formModel">
        <n-form-item label="任务类型"
                     path="type">
          <n-select v-model:value="form.type"
                    :options="taskTypeOption"
                    placeholder="请选择类型"/>
        </n-form-item>
        <template v-if="form.type==='1'">
          <n-grid>
            <n-form-item-gi span="8" label="x" path="x">
              <n-input-number v-model:value="form.x" placeholder="请输入x坐标"/>
            </n-form-item-gi>
            <n-form-item-gi span="8" label="y" path="y">
              <n-input-number v-model:value="form.y" placeholder="请输入y坐标w"/>
            </n-form-item-gi>
            <n-form-item-gi span="8" label="角度" path="r">
              <n-input-number v-model:value="form.r" min="-360" max="360" placeholder="请输入角度"/>
            </n-form-item-gi>
          </n-grid>
        </template>
        <template v-else-if="form.type==='2'">
          <n-form-item label="播报内容" path="text">
            <n-input type="textarea" v-model:value="form.text" placeholder="请输入播报内容"></n-input>
          </n-form-item>
        </template>
        <template v-else-if="form.type==='3'">
          <n-form-item label="导览" path="tourId">
            <h-field-select-tour :room-id="roomId" v-model:value="form.tourId"></h-field-select-tour>
          </n-form-item>
        </template>
      </h-dialog-form>
    </n-layout-sider>
    <n-layout-content>
      <leaflet-image :url="roomImage" :min-zoom="-3" :max-zoom="5">
        <l-marker v-for="(marker,index) in markers"
                  :options="{id:marker.id,type:marker.type}"
                  @moveend="moveMarker"
                  :lat-lng="[marker.x,marker.y]"
                  draggable>
          <l-tooltip>#{{ index }}</l-tooltip>
        </l-marker>
        <l-polyline :lat-lngs="polylines"></l-polyline>
      </leaflet-image>
    </n-layout-content>
  </n-layout>
</template>
<script lang="ts" setup>
import {computed, ref, watchEffect} from "vue";
import {useRoute} from "vue-router";
// @ts-ignore
import {LMarker, LPolyline, LTooltip} from "@vue-leaflet/vue-leaflet";
import {LeafletImage} from '@/components/basic/leaflet'
import HFieldSelectTour from '../../components/form/field/FieldSelectTour.vue'
import {deleteTaskDetail, listTaskDetail, saveTaskDetail, updateTaskSort} from '../../api/task'
import {get} from '../../api/room'
import {Icon} from '@iconify/vue';
import {cloneDeep, isNumber} from "lodash-es";
import draggable from 'vuedraggable'
// @ts-ignore
import x from 'leaflet-heatmap'

const route = useRoute()

const taskId = computed<string>(() => {
  return route.query.taskId as string
})
const roomId = computed<string>(() => {
  return route.query.roomId as string
})

const roomImage = ref()

const formModel = ref(false)
const taskList = ref([])

const markers = computed<any[]>(() => {
  return taskList.value.filter((item: any) => {
    return isNumber(item.x) && isNumber(item.y)
  })
})

const polylines = computed<any[]>(() => {
  return markers.value.map((item: any) => {
    return [item.x, item.y]
  })
})

const defaultForm = {
  type: '1',
  x: 0,
  y: 0,
  r: 0,
  text: '',
  tourId: null,
}
/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  type: {
    required: true,
    message: '请选择类型'
  },
  tourId: {
    required: true,
    message: '请选择导览点'
  },
  x: {
    required: true,
    message: '请输入x坐标'
  },
  y: {
    required: true,
    message: '请输入y坐标'
  },
  r: {
    required: true,
    message: '请输入角度'
  },
  text: {
    required: true,
    message: '请输入语音播报'
  }
}

const taskTypeMap = {
  '1': '位置',
  '2': '语音播报',
  '3': '导览',
}

function getTypeName(index: string) {
  // @ts-ignore
  return taskTypeMap[index]
}

const taskTypeOption = [
  {
    label: "位置",
    value: '1',
  },
  {
    label: "语音播报",
    value: '2',
  },
  {
    label: "导览",
    value: '3',
  }
]


function handleSubmit(data: any) {
  let type = data.type
  let temp: any = {
    taskId: taskId.value,
    type: type,
    id: data.id,
    sort: taskList.value.length,
  }

  if (type === '1') {
    temp.x = data.x
    temp.y = data.y
    temp.r = data.r
  } else if (type === '2') {
    temp.text = data.text
  } else if (type === '3') {
    temp.tourId = data.tourId
  }

  return saveTaskDetail([temp]).then(() => {
    loadTask()
  })
}


function moveMarker(event: any) {
  let option = event.target.options.options
  let latLng = event.target.getLatLng()
  let temp: any = markers.value.find((item) => {
    return item["id"] === option.id
  })
  temp.x = latLng.lat
  temp.y = latLng.lng

  saveTaskDetail([{
    id: option.id,
    taskId: taskId.value,
    type: option.type,
    x: latLng.lat, y: latLng.lng
  }])
}

function addTask() {
  form.value = cloneDeep(defaultForm)
  formModel.value = true
}

function editTask(index: number) {
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, taskList.value[index])
  form.value.type = form.value.type + ''

  formModel.value = true
}

function removeTask(index: number) {
// @ts-ignore
  let id = taskList.value.splice(index, 1)[0].id
  if (id) {
    deleteTaskDetail(id)
  }
}

function dragEnd() {
  let temp = taskList.value.map((item: any, index: number) => {
    return {id: item.id, sort: index}
  })
  updateTaskSort(temp)
}


function loadTask() {
  listTaskDetail({taskId: taskId.value}).then((resp) => {
    taskList.value = resp.data || []
  })
}

/**
 * 获取展馆信息
 * */
function loadRoom() {
  get(roomId.value).then((resp) => {
    if (resp.data) {
      roomImage.value = resp.data.image
    } else {
      roomImage.value = undefined
    }
  })
}

watchEffect(() => {
  if (taskId.value) {
    loadTask()
  }
})

watchEffect(() => {
  if (roomId.value) {
    loadRoom()
  }
})
</script>
