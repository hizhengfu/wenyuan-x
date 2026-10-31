<template>
  <n-form
    style="display: flex; flex-direction: column; flex: 1; overflow: auto"
    ref="formRef"
    v-bind="$attrs"
    :disabled="disabled"
  >
    <div style="overflow: auto; flex: 1">
      <slot></slot>
    </div>
    <n-divider></n-divider>
    <template v-if="enableTeleport">
      <Teleport to=".page-view-action" v-if="loading">
        <n-form-item class="flex justify-end mt-2" v-if="!hiddenAction">
          <slot name="form-action"></slot>
          <n-button v-if="enableCancel" type="error" @click="handleCancel">
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
      </Teleport>
    </template>
    <template v-else>
      <n-form-item class="flex justify-end mt-2" v-if="!hiddenAction">
        <slot name="form-action"></slot>
        <n-button v-if="enableCancel" type="error" @click="handleCancel">
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
  </n-form>
</template>
<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { notification } from '@/utils';
import { FormInst } from 'naive-ui';
import { useFormStore } from '../store/form';
import { useAppStore } from '@/store';

const useApp = useAppStore();
const useForm = useFormStore();
const props = defineProps({
  //分页表引用，方便保存是处理刷新状态
  refTable: {},
  autoClose: { type: Boolean, default: true },
  cancelText: { type: String, default: '取消' },
  submitText: { type: String, default: '提交' },
  enableTeleport: { type: Boolean, default: true },
  hiddenAction: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  enableCancel: { type: Boolean, default: true },
  //表单提交事件
  handleSubmit: { type: Function },
});
/**
 * form:cancel 点击取消按钮事件
 * form:submit 点击提交按钮事件
 * form:submitSuccess 提交成功事件
 *
 * */
const emits = defineEmits(['form:cancel', 'form:submitSuccess']);

/**
 * 是否提交状态
 * */
const isSubmit = ref(false);
/**
 * 表单引用
 * */
const formRef = ref<(HTMLElement & FormInst) | null>(null);

/**
 * 取消
 * */
function handleCancel() {
  emits('form:cancel', false);
  if (props.autoClose) {
    useForm.handleCancel();
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
      }
      reject(false);
    });
  });
}

/**
 * 保存
 * */
function handeSave(e: MouseEvent) {
  e.preventDefault();
  formRef.value?.validate((errors) => {
    if (!errors) {
      if (useApp.uploading) {
        notification({
          type: 'warning',
          title: '请注意',
          content: '文件正在上传，请待文件上传完毕后再保存。',
        });
        return;
      }

      const { handleSubmit, refTable } = props;
      if (handleSubmit) {
        isSubmit.value = true;
      } else {
        return;
      }
      // @ts-ignore
      handleSubmit(formRef.value['model'])
        .then(() => {
          emits('form:submitSuccess', formRef.value);
          if (refTable) {
            // @ts-ignore
            refTable.handleRefresh();
          }
          //设置sore表单关闭状态
          if (props.autoClose) {
            useForm.handleCancel();
          }
        })
        .finally(() => {
          isSubmit.value = false;
        });
    } else {
      let content = errors
        .map((item) => {
          return item[0]['message'];
        })
        .join(',');
      notification({
        type: 'error',
        title: '请注意',
        content: content,
      });
    }
  });
}

defineExpose({ handeSave, validateForm });

const loading = ref(false);
onMounted(() => {
  loading.value = true;
});
</script>
