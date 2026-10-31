<template>
  <n-layout has-sider class="h-full">
    <n-layout-sider
        :width="240"
        :show-collapsed-content="false"
        content-style="padding: 24px;"
        bordered>
      <n-layout-header bordered>
        功能区
      </n-layout-header>
      <n-tree
          block-node
          show-line
          :data="treeData"
          label-field="name"
          key-field="id"
          :onLoad="handleTreeLoad"
          :node-props="nodeProps"
          expand-on-click
      />
    </n-layout-sider>
    <n-layout-content content-style="padding: 24px;">
      <n-layout-header bordered>
        控制区
      </n-layout-header>
      <n-layout-content>
        <n-table :single-line="false" class="mt-5">
          <tr>
            <td style="width: 80px;"></td>
            <td class="text-center">
              <n-button @click="handleDirection('UP')">向上</n-button>
            </td>
            <td style="width: 80px;"></td>
          </tr>
          <tr>
            <td>
              <n-button @click="handleDirection('LEFT')">向左</n-button>
            </td>
            <td style="height: 300px;">
              <n-result description="手势识别区">
              </n-result>
            </td>
            <td>
              <n-button @click="handleDirection('RIGHT')">向右</n-button>
            </td>
          </tr>
          <tr>
            <td></td>
            <td class="text-center">
              <n-button @click="handleDirection('BOTTOM')">向下</n-button>
            </td>
            <td></td>
          </tr>
        </n-table>
      </n-layout-content>
    </n-layout-content>
  </n-layout>
</template>
<script lang="ts" setup>
import {ref} from 'vue'
import {TreeOption} from 'naive-ui'
import {list} from '../../api/define'
import {list as menuList} from '../../api/menu'
import {transformToTreeFormat, notification} from '@/utils';
import Websocket from '@/utils/websocket'
import {SmartControlType} from '@/modules/smart/typings/smart';

const treeData = ref([])

function nodeProps({option}: { option: TreeOption }) {
  return {
    onClick() {
      let type = option.type;
      let code = option.code;
      sendData('CLICK', option.code as string)
    }
  }
}

/**
 * 方向函数
 * */
function handleDirection(direction: string) {
  sendData('DIRECTION', direction)
}

/**
 * 加载界面列表
 * */
function loadControlList() {
  list({pageSize: '0', status: '0'}).then((resp: any) => {
    if (resp.data && resp.data.list) {
      treeData.value = resp.data.list.map((item: any) => {
        item['isLeaf'] = false
        return item;
      })
    }
  })
}

function handleTreeLoad(node: TreeOption) {
  return new Promise<void>((resolve) => {
    menuList({smartControlId: node.id}).then((resp: any) => {
      node.children = transformToTreeFormat(resp.data.list || [], {pIdKey: 'parentId'})
      resolve()
    })
  })
}


/**
 * 初始化websocket
 * */
const ws = new Websocket({destinationPath: '/app/smart/control', subscribePath: '/topic/smart/control/operate'})
ws.connection((response:any) => {
  if (response.state === 'data') {
    if (response.data && response.data.data) {
      let data = response.data.data
      notification({content: data.message})
    }
  }
})

/**
 * @
 * */
function sendData(type: SmartControlType, value: string) {
  console.log(11,type,value)
  ws.send({
    type: 'SMART_CONTROL',
    data: {type: type, value: value}
  })
}

loadControlList()
</script>
