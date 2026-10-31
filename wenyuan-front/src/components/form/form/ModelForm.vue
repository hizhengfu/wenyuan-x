<template>
  <n-modal
      v-model:show="dialog"
      :style="{width:'800px'}"
      :content-style="{flex:1,display:'flex',overflow: 'auto'}"
      :title="props.title"
      :segmented="{
                         content: 'soft',
                         footer: 'soft'
                 }"
      preset="card">
    <h-form
        v-bind="$attrs"
        :handle-submit="handleSubmit"
        @form:submitSuccess="handleSubmitSuccess"
        @form:cancel="handleCancel">
      <slot/>
    </h-form>
  </n-modal>
</template>
<script lang="ts" setup>
import {computed} from 'vue';
import HForm from './Form.vue'
import {useWindowSize} from '@vueuse/core'

const {width, height} = useWindowSize()

const emits = defineEmits(['update:show'])

const props = defineProps({
  show: {type: Boolean, default: false},
  //提交后自动关闭
  autoClose: {type: Boolean, default: true},
  title: {type: String, default: '编辑'},
  handleSubmit: {type: Function}
})

const dialog = computed<boolean>({
  get() {
    return props.show
  },
  set(value) {
    emits('update:show', value)
  }
})

/**
 * 取消
 * */
function handleCancel() {
  dialog.value = false
}

function handleSubmitSuccess() {
  if (props.autoClose) {
    dialog.value = false
  }
}
</script>
