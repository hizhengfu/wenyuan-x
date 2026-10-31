<template>
  <n-drawer
    v-model:show="dialog"
    resizable
    :auto-focus="false"
    :trap-focus="false"
    placement="right"
    :to="useApp.fullscreen ? '.pageTable' : 'body'"
    :min-width="300"
    :default-width="drawerWidth"
    :width="drawerWidth"
    @after-leave="handleAfterLeave"
    v-bind="$attrs"
  >
    <n-drawer-content closable ref="contentEl" :footer-style="footerStyle">
      <template #header>
        <slot name="header">
          {{ title }}
        </slot>
      </template>
      <slot name="query-form"></slot>
      <n-form v-if="!contentOnly" class="page-form" ref="formRef" v-bind="$attrs" :disabled="disabled">
        <slot></slot>
      </n-form>
      <div v-else class="page-form page-form--content-only">
        <slot></slot>
      </div>
      <template #footer v-if="enableCancel || handleSubmit || $slots['form-action']">
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
    </n-drawer-content>
  </n-drawer>
</template>
<script lang="ts" setup>
import { computed, onDeactivated, PropType, ref, watch } from 'vue';
import type { CSSProperties } from 'vue';
import { notification, uuid } from '@/utils';
import { FormInst } from 'naive-ui';
import { useAppStore } from '@/store';

import { useFormStore } from '../store/form.ts';

const useApp = useAppStore();
const useForm = useFormStore();

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
const emits = defineEmits(['form:cancel', 'form:submitSuccess', 'update:show', 'after-leave']);

const props = defineProps({
  type: { type: String, default: 'page' }, //弹出层模式
  show: { type: Boolean, default: false },
  autoReload: { type: Boolean, default: false }, //提交后自动刷新
  width: { type: String, default: '80%' },
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

const formDrawerId = ref<string>(uuid());
const currentLevel = ref<number>(-1); // 记录当前抽屉在 activeDrawers 中的层级索引

//抽屉宽度计算
// 根据当前层级获取抽屉宽度
const drawerWidth = computed<string>(() => {
  const config = useForm.getDrawerConfigByLevel(currentLevel.value);
  // 如果 Store 尚未计算出宽度，则使用格式化后的初始宽度作为默认值
  return config ? config : props.width;
});

watch(
  () => dialog.value,
  (show: boolean) => {
    if (show) {
      currentLevel.value = useForm.registerDrawer(formDrawerId.value, props.width);
    }
  },
);

/**
 * 抽屉关闭动画结束后回调
 * */
function handleAfterLeave() {
  // 抽屉完全关闭后，从 store 中注销
  useForm.unregisterDrawer(formDrawerId.value);
  emits('after-leave');
}

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
  })?.catch(ignoreValidateRejection);
}

watch(
  () => props.close,
  (newVal) => {
    if (newVal == true) {
      isSubmit.value = false;
    }
  },
  { deep: true, immediate: true },
);

/**
 * 失活时关闭弹出层界面
 * */
onDeactivated(() => {
  dialog.value = false;
});

defineExpose({ validateForm });
</script>
<style lang="scss" scoped>
.page-form {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;

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

.page-form--content-only :deep(img) {
  max-width: 100%;
}
</style>
