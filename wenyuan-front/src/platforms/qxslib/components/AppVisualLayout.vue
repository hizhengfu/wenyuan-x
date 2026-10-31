<template>
  <div class="main-wrap">
    <app-header>
      <template #header_left>
        <clock></clock>
      </template>
      <template #header_right>
        <n-popover :show-arrow="false">
          <template #trigger>
            <n-icon class="mr-3" size="30px" @click="reload">
              <IconMdiRefresh></IconMdiRefresh>
            </n-icon>
          </template>
          点击刷新
        </n-popover>
        <app-notice></app-notice>
        <app-user-info></app-user-info>
      </template>
    </app-header>
    <div class="main">
      <div class="info-left">
        <n-icon size="20px" class="mr-2">
          <icon-mdi-account-supervisor></icon-mdi-account-supervisor>
        </n-icon>
        当前总人数：{{ roomStatus?.people?.length || 0 }}人
      </div>
      <div class="info-right">
        <n-popover trigger="hover">
          <template #trigger>
            <div class="flex-center">
              <n-icon size="20px" class="mr-2">
                <icon-mdi-robot-outline></icon-mdi-robot-outline>
              </n-icon>
              伺服机器人：{{ roomStatus?.robot?.length }}台
            </div>
          </template>
          <n-table size="small">
            <thead>
            <tr>
              <th>编号</th>
              <th>状态</th>
              <th>电量</th>
              <th>待执行任务</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="robot in roomStatus.robot">
              <td>{{ robot['robot_sn'] }}</td>
              <td>{{ robot['state']?.state_msg || '--' }}</td>
              <td>{{ robot['battery']?.value || '--' }}</td>
              <td>{{ robot['task_list']?.length || '0' }}</td>
            </tr>
            </tbody>
          </n-table>
        </n-popover>
      </div>
      <app-control></app-control>
      <router-view v-slot="{ Component }">
        <keep-alive>
          <component :is="Component"/>
        </keep-alive>
      </router-view>
    </div>
    <visual-operate></visual-operate>
    <!--    <popup-control></popup-control>-->
  </div>
</template>
<script lang="ts" setup>
import Clock from '@/components/basic/Clock.vue'
import AppUserInfo from './AppUserInfo.vue'
import AppNotice from './AppNotice.vue'
import AppHeader from './AppHeader.vue'
import AppControl from './AppControl.vue'
import VisualOperate from './VisualOperate.vue'
import PopupControl from './PopupControl.vue'
import {NIcon} from 'naive-ui';
import WS from '@/utils/websocket.js'
import {ref} from "vue";
import {RoomStatus, useRoomStatusStore} from '../store/roomStatus'
import {AlarmStatus, useAlarmStatusStore} from '../store/alarmStatus'
import {physics2px, pointDistance, px2physics} from "@/platforms/qxslib/utils";

const roomStatus = ref<RoomStatus>({people: [], robot: [], raster: []})

const roomStore = useRoomStatusStore()
const alarmStore = useAlarmStatusStore()
let ws = new WS({subscribePath: '/topic/robot', destinationPath: '/app/info'})
ws.connection((response: any) => {
  if (response.state === 'data') {
    let command = response.data
    if (command.type === 0) {
      roomStatus.value = command.data
      roomStore.raster = command.data.raster
      roomStore.robot = command.data.robot
      roomStore.people = command.data.people
    } else {
      alarmStore.count++
    }
  }
})
let t = pointDistance({x: 4, y: 5}, {x: 1, y: 2})
console.log(112233, t)

function reload() {
  location.reload()
}
</script>
<style scoped>
.info-left, .info-right {
  position: absolute;
  font-size: 18px;
  display: flex;
  align-items: center;
  top: -18px;
  user-select: none;
  z-index: 10;
}

.info-left {
  left: 12px;
}

.info-right {
  right: 12px;
}

</style>
