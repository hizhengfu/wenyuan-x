<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button @click="show=true">
        <n-icon size="20px">
          <IconMdiMicrosoftExcel/>
        </n-icon>
      </n-button>
    </template>
    点击导入
  </n-tooltip>
  <h-dialog-form v-model:show="show" title="Excel转换坐标">
    <n-input type="textarea" v-model:value="content" rows="20"
             placeholder="请复制Excel内容，不要复制表头数据。"></n-input>
    <template #form-action>
      <!--      <n-input-group>-->
      <!--        <n-input-group-label>交换坐标</n-input-group-label>-->
      <!--        <n-switch :round="false" v-model:value="switchXY"></n-switch>-->
      <!--      </n-input-group>-->
      <n-input-group>
        <n-input-group-label>条带号</n-input-group-label>
        <n-input-number v-model:value="bandNum" min="0"></n-input-number>
      </n-input-group>
      <n-button type="primary" class="ml-2" @click="handleConvert">
        <template #icon>
          <IconMdiCheck/>
        </template>
        转换
      </n-button>
    </template>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import {ref} from 'vue';
import {notification} from '@/utils'

// const props = defineProps({});

const show = ref(false)

const emits = defineEmits<{ 'update:value': [{ x: number, y: number }[]] }>()

const content = ref()
//带号
const bandNum = ref()
const switchXY = ref(false)

function handleConvert() {
  try {
    let arr: { x: number, y: number }[] = []
    content.value.split('\n').forEach((line: string) => {
      let temp = line.trim().split(/s+|\t/).map((l, i) => {
        let t = l.trim()
        if (bandNum.value && i === 0) {
          t = bandNum.value.toString() + t
        }
        return parseFloat(t)
      })
      if (temp.length === 2) {
        arr.push({x: temp[0], y: temp[1]})
      }
    })
    emits('update:value', arr)
    show.value = false
  } catch (e) {
    notification({content: '数据转换失败，请检查复制内容是否符合要求。'})
  }
}

</script>
