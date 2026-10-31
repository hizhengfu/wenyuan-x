<template>
  <n-modal
    v-model:show="dialog"
    :style="{ width: width }"
    :content-style="{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }"
    :title="title"
    :segmented="{
      content: true,
      action: true,
    }"
    preset="card"
  >
    <slot name="query-form"></slot>
    <div class="dialog-form" style="overflow: auto" :style="contentStyle" ref="formContent">
      <n-form ref="formRef" class="wh-full" v-bind="$attrs" :disabled="disabled">
        <slot></slot>
      </n-form>
    </div>
    <template #action v-if="enableCancel || handleSubmit || $slots['form-action']">
      <n-form-item class="flex justify-end" :show-feedback="false" :show-label="false">
        <slot name="form-action"></slot>
        <n-button class="ml-2" v-if="enableCancel" type="error" @click="handleCancel">
          <template #icon>
            <IconMdiClose />
          </template>
          {{ cancelText }}
        </n-button>
        <n-button
          type="primary"
          class="ml-2"
          v-if="handleSubmit"
          @click="handeSave"
          :disabled="disabled"
          :loading="isSubmit"
        >
          <template #icon>
            <IconMdiCheck />
          </template>
          {{ submitText }}
        </n-button>
      </n-form-item>
    </template>
  </n-modal>
</template>
<script lang="ts" setup>
import { computed, ref, onMounted, watch, nextTick } from 'vue';
import { FormInst } from 'naive-ui';
import { notification } from '@/utils';
import { useAppStore } from '@/store';
import { useElementBounding } from '@vueuse/core';
const useApp = useAppStore();
/**
 * form:cancel 点击取消按钮事件
 * form:submit 点击提交按钮事件
 * form:submitSuccess 提交成功事件
 *
 * */
const emits = defineEmits(['form:cancel', 'form:submitSuccess', 'update:show', 'form:ready']);

const props = defineProps({
  show: { type: Boolean, default: false },
  //提交后自动关闭
  autoClose: { type: Boolean, default: true },
  autoReload: { type: Boolean, default: false }, //提交后自动刷新
  title: { type: String, default: '编辑' },
  handleSubmit: { type: Function },
  width: { type: String, default: '60%' },
  height: { type: String, default: '500px' },

  //分页表引用，方便保存是处理刷新状态
  refTable: {},
  cancelText: { type: String, default: '取消' },
  submitText: { type: String, default: '提交' },
  disabled: { type: Boolean, default: false },
  enableCancel: { type: Boolean, default: true },
});
const formContent = ref();
const dialog = computed<boolean>({
  get() {
    return props.show as boolean;
  },
  set(value) {
    emits('update:show', value);
  },
});

/**
 * 表单引用
 * */
const formRef = ref<(HTMLElement & FormInst) | null>(null);

const contentStyle = computed(() => {
  let style = { maxHeight: '800px', height: props.height };
  return style;
});

/**
 * 取消
 * */
function handleCancel() {
  dialog.value = false;
}

function handleSubmitSuccess() {
  if (props.autoClose) {
    dialog.value = false;
  }
}

/**
 * 验证表单
 * */
function validateForm() {
  return new Promise((resolve, reject) => {
    formRef.value?.validate((errors) => {
      if (!errors) {
        if (useApp.uploading) {
          notification({
            type: 'warning',
            title: '请注意',
            content: '文件正在上传，请待文件上传完毕后再保存。',
          });
          reject(false);
        } else {
          //@ts-ignore
          resolve(formRef.value['model']);
        }
      } else {
        let content: string;
        if (errors.length <= 3) {
          content = `表单「${errors
            .map((item) => {
              return item[0]['message'];
            })
            .join(',')}」填写有误`;
        } else {
          content = `表单「${errors[0][0]['message']}」等${errors.length}项内容填写有误`;
        }
        notification({
          type: 'error',
          title: '请注意',
          content: content,
        });
        reject(false);
      }
    });
  });
}

/**
 * 是否提交状态
 * */
const isSubmit = ref(false);

/**
 * 保存
 * */
function handeSave(e: MouseEvent) {
  e.preventDefault();
  formRef.value?.validate((errors) => {
    if (!errors) {
      const { handleSubmit, refTable, autoReload } = props;
      if (handleSubmit) {
        isSubmit.value = true;
      }
      if (handleSubmit) {
        // @ts-ignore
        handleSubmit(formRef.value['model'])
          .then(() => {
            emits('form:submitSuccess', formRef.value);
            if (props.autoClose) {
              dialog.value = false;
            }
            if (autoReload) {
              useApp.reloadPage(100);
            } else {
              if (refTable) {
                // @ts-ignore
                refTable.handleRefresh();
              }
            }
          })
          .finally(() => {
            isSubmit.value = false;
          });
      }
    } else {
      let content: string;
      if (errors.length <= 3) {
        content = `表单「${errors
          .map((item) => {
            return item[0]['message'];
          })
          .join(',')}」填写有误`;
      } else {
        content = `表单「${errors[0][0]['message']}」等${errors.length}项内容填写有误`;
      }
      notification({
        type: 'error',
        title: '请注意',
        content: content,
      });
    }
  });
}
watch(
  () => dialog.value,
  async (newValue) => {
    await nextTick();
    setTimeout(() => {
      const { bottom, right } = useElementBounding(formContent.value);
      emits('form:ready', { bottom, right });
    }, 300);
  },
);

defineExpose({ validateForm });
</script>
