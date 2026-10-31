<template>
  <div v-if="dialog" class="page-form">
    <n-card
      :title="title"
      hoverable
      :size="size"
      :closable="closable"
      @close="handleCancel"
      style="width: 100%"
      :content-style="{ flex: 1, display: 'flex', overflow: 'auto', flexDirection: 'column' }"
      :segmented="{ content: true, action: true }"
      :action-style="footerStyle"
    >
      <slot name="query-form"></slot>
      <n-form v-if="!contentOnly" ref="formRef" v-bind="$attrs" style="position: relative; height: 100%; width: 100%" :disabled="disabled">
        <slot></slot>
      </n-form>
      <div v-else class="page-form-content-only">
        <slot></slot>
      </div>
      <template #action v-if="enableCancel || handleSubmit || $slots['form-action']">
        <n-form-item class="flex justify-end" :show-feedback="false" :show-label="false">
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
    </n-card>
  </div>
</template>
<script lang="ts" setup>
import { computed, onDeactivated, onMounted, PropType, ref, watch, getCurrentInstance } from 'vue';
import type { CSSProperties } from 'vue';
import { notification } from '@/utils';
import { FormInst } from 'naive-ui';
import { useAppStore } from '@/store';

const useApp = useAppStore();
/**
 * 表单引用
 * */
const formRef = ref<(HTMLElement & FormInst) | null>(null);

/**
 * form:cancel 点击取消按钮事件
 * form:submit 点击提交按钮事件
 * form:submitSuccess 提交成功事件
 *
 * */
const emits = defineEmits(['form:cancel', 'form:submitSuccess', 'update:show']);

const props = defineProps({
  type: { type: String, default: 'page' }, //弹出层模式
  show: { type: Boolean, default: false },
  size: { type: String as PropType<'small' | 'medium' | 'large' | 'huge'>, default: 'small' },
  //分页表引用，方便保存是处理刷新状态
  refTable: {},
  //提交后自动关闭
  autoClose: { type: Boolean, default: true },
  closable: { type: Boolean, default: true }, //是否允许关闭
  title: { type: String, default: '编辑' },
  handleSubmit: { type: Function },

  cancelText: { type: String, default: '取消' },
  submitText: { type: String, default: '提交' },
  disabled: { type: Boolean, default: false },
  contentOnly: { type: Boolean, default: false },
  footerStyle: { type: [String, Object] as PropType<string | CSSProperties> },
  enableCancel: { type: Boolean, default: true },
  close: { type: Boolean },
});

const dialog = computed<boolean>({
  get() {
    return props.show;
  },
  set(value) {
    emits('update:show', value);
  },
});

/**
 * 取消
 * */
function handleCancel() {
  emits('form:cancel', false);
  dialog.value = false;
}

/**
 * 是否提交状态
 * */
const isSubmit = ref(false);

/** naive-ui 的 validate(callback) 在校验失败时还会 reject 返回的 Promise；错误已在回调里提示，这里吞掉，避免控制台未捕获的 Promise。 */
const ignoreValidateRejection = () => undefined;

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
    })?.catch(ignoreValidateRejection);
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
      }

      // @ts-ignore
      handleSubmit && handleSubmit(formRef.value['model'])
          .then(() => {
            emits('form:submitSuccess', formRef.value);

            if (props.autoClose) {
              dialog.value = false;
            }
            if (refTable) {
              // @ts-ignore
              refTable.handleRefresh();
            }
          })
          .finally(() => {
            isSubmit.value = false;
          });
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
  })?.catch(ignoreValidateRejection);
}

/**
 * 失活时关闭弹出层界面
 * */
onDeactivated(() => {
  dialog.value = false;
});
watch(
  () => props.close,
  (newVal) => {
    if (newVal == true) {
      isSubmit.value = false;
    }
  },
  { deep: true, immediate: true },
);

defineExpose({ validateForm });
</script>
<style lang="scss" scoped>
.page-form {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: 15px;
  overflow: hidden;
  display: flex;

  :deep(.n-input-number),
  :deep(.n-date-picker) {
    width: 100%;
  }

  :deep(.n-dynamic-input .n-dynamic-input-item .n-dynamic-input-item__action) {
    align-self: center;
  }

  :deep(.n-table) {
    table-layout: fixed;
  }
}

.page-form-content-only {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;

  :deep(img) {
    max-width: 100%;
  }
}
</style>
