<template>
  <div>
    <div :style="{height:innerHeight}" style="overflow: hidden;">
      <slot></slot>
    </div>
    <div>
      <n-divider>
        <n-button @click="handleMore">
          <template #icon>
            <n-icon>
              <IconMdiChevronDoubleUp v-if="isExpand"></IconMdiChevronDoubleUp>
              <IconMdiChevronDoubleDown v-else></IconMdiChevronDoubleDown>
            </n-icon>
          </template>
          {{ isExpand ? '点击收缩隐藏' : '点击展开查看更多' }}
        </n-button>
      </n-divider>
    </div>
  </div>
</template>
<script lang="ts" setup>
import {ref} from 'vue'

const props = defineProps({
  height: {type: String, default: '120px'}
})

const innerHeight = ref(props.height)

const isExpand = ref(false)

function handleMore() {
  const {height} = props
  if (innerHeight.value == '100%') {
    isExpand.value = false
    innerHeight.value = height
  } else {
    isExpand.value = true
    innerHeight.value = '100%'
  }
}
</script>
