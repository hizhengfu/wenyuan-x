<template>
  <div style="position: absolute;top: 0;right: 0;bottom: 0;left: 0;">
    <div class="player-wrap">
      <template v-for="(items,index) in playerWindows">
        <div class="player-row">
          <div class="player-col" v-for="(item,innerIndex) in items"
               @contextmenu="(e)=>{handleContextMenu(e,index,innerIndex)}">
            <flv-player v-if="item.addr" :url="item.addr"></flv-player>
            <div v-else>右键点击选择视频</div>
          </div>
        </div>
      </template>
    </div>
    <div style="z-index: 1000;position: absolute;bottom: 20px;left: 20px">
      <n-button-group>
        <n-button @click="windowsType=1" text-color="#ffffff">
          <template #icon>
            <n-icon>
              <icon-mdi-checkbox-blank-outline></icon-mdi-checkbox-blank-outline>
            </n-icon>
          </template>
        </n-button>
        <n-button @click="windowsType=2" text-color="#ffffff">
          <template #icon>
            <n-icon>
              <icon-mdi-table></icon-mdi-table>
            </n-icon>
          </template>
        </n-button>
        <n-button @click="windowsType=3" text-color="#ffffff">
          <template #icon>
            <n-icon>
              <icon-mdi-grid-on></icon-mdi-grid-on>
            </n-icon>
          </template>
        </n-button>
      </n-button-group>
    </div>
    <n-dropdown
      placement="bottom-start"
      trigger="manual"
      :x="x"
      :y="y"
      :options="playList"
      key-field="addr"
      label-field="name"
      :show="showDropdown"
      :on-clickoutside="onClickoutside"
      @select="handleSelect"
    />
  </div>
</template>
<script lang="ts" setup>
import {times, chunk} from 'lodash-es'
import {list} from '../../api/device'
import FlvPlayer from '@/components/basic/player/FlvPlayer.vue'

import {computed, nextTick, ref} from "vue";
import {DropdownOption} from 'naive-ui';

const showDropdown = ref(false)
const x = ref(0)
const y = ref(0)

const windowsType = ref(2)

const playList = ref([])

interface IWindowType {
  addr: string,
  name: string
}

const playerWindows = computed(() => {
  let type = windowsType.value
  let num = 1
  if (type === 2) {
    num = 4
  } else if (type === 3) {
    num = 9
  }

  let list: IWindowType[] = []

  if (playList.value.length >= num) {
    list = playList.value.slice(0, num)
  } else {
    list = list.concat(playList.value.slice(0, num))
    num -= playList.value.length
    if (num > 0) {
      times(num, () => {
        list.push({addr: '', name: ''})
      })
    }
  }
  return chunk(list, type)
})

let rootIndex: number = 0
let innerIndex: number = 0

function handleContextMenu(e: MouseEvent, idx: number, idx1: number) {
  e.preventDefault()
  showDropdown.value = false
  rootIndex = idx
  innerIndex = idx1
  nextTick().then(() => {
    showDropdown.value = true
    x.value = e.clientX
    y.value = e.clientY
  })
}

function handleSelect(key: string | number, option: DropdownOption) {
  showDropdown.value = false
  playerWindows.value[rootIndex][innerIndex] = {name: option.name as string, addr: option.addr as string}
}

function onClickoutside() {
  showDropdown.value = false
}

/**
 * 选择播放器
 * */
function choosePlayer(item: IWindowType) {

}

function fetchData() {
  list({pageSize: -1, type: 0, online: true}).then((res) => {
    if (res.data) {
      playList.value = res.data.list || []
    }
  })
}

fetchData()

</script>

<style scoped>
.player-wrap {
  display: flex;
  flex-wrap: nowrap;
  flex-direction: column;
  justify-content: space-around;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 15px;
  user-select: none;
}

.player-row {
  display: flex;
  flex: 1;
  justify-content: space-around !important;
  overflow: hidden;
}

.player-col {
  background-color: #000000;
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  border: 1px solid #cccccc;
  overflow: hidden;
}
</style>

