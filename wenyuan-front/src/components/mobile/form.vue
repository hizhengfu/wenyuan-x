<template>
  <t-form ref="form" v-bind="$attrs" @submit="handleSubmit">
    <slot></slot>
  </t-form>
</template>
<script lang="ts" setup>
import { Dialog, Toast } from 'tdesign-mobile-vue';

const props = defineProps({
  submit: { type: Function },
  needConfirm: { type: Boolean, default: true },
});

const emits = defineEmits(['submit']);

function handleSubmit(context: any) {
  if (context.validateResult === true) {
    if (props.needConfirm) {
      Dialog.confirm({
        title: '请注意',
        content: '点击确认后将提交数据，请注意操作。',
        buttonLayout: 'vertical',
        confirmBtn: '确认',
        cancelBtn: '取消',
        overlayProps: { zIndex: 99998 },
        zIndex: 99999,
        onConfirm: () => {
          emits('submit', context);
        },
      });
    } else {
      emits('submit', context);
    }
  } else {
    Toast({
      theme: 'error',
      direction: 'column',
      message: '请检查输入，修正后提交',
    });
  }
}
</script>
