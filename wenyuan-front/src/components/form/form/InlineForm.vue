<template>
  <n-form
      ref="formRef"
      v-bind="$attrs"
      inline
      label-placement="left"
      :show-label="true"
      :show-feedback="false"
      :disabled="disabled">
    <slot></slot>
    <n-form-item>
      <n-button
          v-if="handleSubmit" type="primary" :disabled="disabled" :loading="isSubmit"
          @click="handeSave">
        <template #icon>
          <IconMdiCheck/>
        </template>
        {{ submitText }}
      </n-button>
    </n-form-item>
  </n-form>
</template>
<script lang="ts" setup>
import {ref} from 'vue';
import {notification} from '@/utils';
import {FormInst} from 'naive-ui';
import {useAppStore} from '@/store'

const useApp = useAppStore()

const props = defineProps({
  //分页表引用，方便保存是处理刷新状态
  refTable: {},
  submitText: {type: String, default: '提交'},
  disabled: {type: Boolean, default: false},
  //表单提交事件
  handleSubmit: {type: Function}
})
/**
 * form:cancel 点击取消按钮事件
 * form:submit 点击提交按钮事件
 * form:submitSuccess 提交成功事件
 *
 * */
const emits = defineEmits(['form:cancel', 'form:submitSuccess'])

/**
 * 是否提交状态
 * */
const isSubmit = ref(false)
/**
 * 表单引用
 * */
const formRef = ref<(HTMLElement & FormInst) | null>(null);


/**
 * 保存
 * */
function handeSave(e: MouseEvent) {
  e.preventDefault()
  formRef.value?.validate((errors) => {
    if (!errors) {
      if (useApp.uploading) {
        notification({
          type: 'warning',
          title: '请注意',
          content: '文件正在上传，请待文件上传完毕后再保存。'
        })
        return
      }
      const {handleSubmit, refTable} = props
      isSubmit.value = true
      // @ts-ignore
      handleSubmit && handleSubmit(formRef.value['model']).then(() => {
        emits('form:submitSuccess', formRef.value)
        if (refTable) {
          // @ts-ignore
          refTable.handleRefresh()
        }
      }).finally(() => {
        isSubmit.value = false
      })
    } else {
      let content: string
      if (errors.length <= 3) {
        content = `表单「${errors.map((item) => {
          return item[0]['message']
        }).join(',')}」填写有误`
      } else {
        content = `表单「${errors[0][0]['message']}」等${errors.length}项内容填写有误`
      }
      notification({
        type: 'error',
        title: '请注意',
        content: content
      })
    }
  })
}
</script>
