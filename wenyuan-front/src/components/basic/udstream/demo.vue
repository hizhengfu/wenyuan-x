<template>
  <n-layout class="udstream-editor h-full" position="absolute">
    <n-layout-header bordered style="height: 64px;display: flex;align-items: center;">
      <n-form style="padding: 15px;width: 50%;" :model="form" :rules="rules">
        <n-form-item path="modelUrl">
          <n-input-group>
            <n-input v-model:value="form.modelUrl" placeholder="请输入模型地址" clearable/>
            <n-button type="primary" :disabled="!form.modelUrl" @click="handleSubmit">
              查看
            </n-button>
          </n-input-group>
        </n-form-item>
      </n-form>
      <n-button-group>
        <n-popover trigger="hover" :show-arrow="false">
          <template #trigger>
            <n-button>
              <n-icon size="26">
                <IconMdiCogs/>
              </n-icon>
            </n-button>
          </template>
          <n-card style="width: 500px;">
            <n-form :model="setting" :rules="rules" label-placement="left">
              <n-form-item path="skybox.sature" label="饱和度">
                <n-slider v-model:value="setting.sature" :min="0" :max="5" :step="1"/>
              </n-form-item>
              <n-form-item path="skybox.type" label="天空盒类型">
                <n-select v-model:value="setting.skybox.type" :options="skyOptions"/>
              </n-form-item>
              <n-form-item v-if="setting.skybox.type===1" path="skybox.colour" label="背景色">
                <n-color-picker v-model:value="setting.skybox.colour"></n-color-picker>
              </n-form-item>
              <n-form-item v-if="setting.skybox.type===3" path="skybox.exposure" label="曝光度">
                <n-slider v-model:value="setting.skybox.exposure" :min="0" :max="100" :step="0.001"/>
              </n-form-item>
            </n-form>
            <template #action>
              <n-space justify="end">
                <n-button type="primary" @click="handleUpdateSetting">
                  设置
                </n-button>
              </n-space>
            </template>
          </n-card>
        </n-popover>

        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button @click="handleTakeScreenshot">
              <n-icon size="26">
                <IconMdiCamera/>
              </n-icon>
            </n-button>
          </template>
          点击拍照
        </n-tooltip>
      </n-button-group>
      <!--      <n-tooltip trigger="hover">
              <template #trigger>
                <n-button @click="handleClearAll">
                  <n-icon size="26">
                    <IconMdiDeleteCircle/>
                  </n-icon>
                </n-button>
              </template>
              清空模型
            </n-tooltip>-->
    </n-layout-header>
    <n-layout-content content-style="display: flex;flex:1;">
      <udStream ref="udStreamRef" :model-url="form.modelUrl" style="height: 100%;width: 100%;"></udStream>
    </n-layout-content>
  </n-layout>
</template>
<script lang="ts" setup>
import {ref} from 'vue'

const udStreamRef = ref()
const form = ref({
  // modelUrl: 'https://dev.wenwuyun.cn:8060/staticFiles/uds0/ZJD.uds'
  modelUrl: 'http://127.0.0.1:8080/userfiles/ZJD.uds'
  // modelUrl: 'https://dev.wenwuyun.cn:8060/staticFiles/uds/majiashan21.uds'
  // modelUrl: 'http://116.226.242.253:8082/staticFiles/uds/zhangwentian.uds'
})
const rules = {
  modelUrl: {
    required: true,
    message: '请输入模型地址',
  }
}

/**
 * 真的要设置
 * */
const setting = ref({
  'sature': 1,
  'skybox': {
    'type': 1,
    'colour': 'rgb(99,148,168)',
    'exposure': 7.5,
    'timeOfDay': 12,
    'month': 3,
    'keepSameTime': true,
    'uselivetime': false
  }
})


const skyOptions = [
  {
    label: '无',
    value: 0,
  },
  {
    label: '颜色',
    value: 1,
  },
  {
    label: '简单',
    value: 2,
  },
  {
    label: '大气',
    value: 3,
  },
]

/**
 * 地址分发
 * */
function handleSubmit() {

}

function handleTakeScreenshot() {
  udStreamRef.value.handleTakeScreenshot()
}

function handleChangeSature(step: number) {
  udStreamRef.value.handleChangeSature(step)
}

function handleUpdateSetting() {
  let temp = JSON.parse(JSON.stringify(setting.value))
  temp.skybox.colour = temp.skybox.colour.replace(/[rgba()]/g, '').split(',').map((it: string) => parseFloat(it))
  udStreamRef.value.handleUpdateSetting(temp)
}

function handleClearAll() {

}

</script>
<style scoped>
.udstream-editor .n-input {
  min-width: 220px;
}
</style>
