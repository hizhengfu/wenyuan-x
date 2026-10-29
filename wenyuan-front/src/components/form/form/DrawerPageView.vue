<template>
  <n-drawer
    v-model:show="dialog"
    resizable
    :auto-focus="false"
    :trap-focus="false"
    :to="useApp.fullscreen ? '.pageTable' : 'body'"
    placement="right"
    :min-width="300"
    :default-width="drawerWidth"
    :width="drawerWidth"
    :style="{ maxWidth: '100%' }"
    @after-leave="handleAfterLeave"
    v-bind="$attrs"
  >
    <n-drawer-content closable>
      <template #header>
        <slot name="header">
          {{ title }}
        </slot>
      </template>
      <div ref="refContent" class="page-view-content">
        <slot></slot>
      </div>
      <template v-if="showAction" #footer>
        <slot name="footer">
          <div class="page-view-action"></div>
        </slot>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>
<script lang="ts" setup>
import { computed, onDeactivated, ref, watch } from 'vue';
import { uuid } from '@/utils';
import { useFormStore } from '../store/form';
import { useAppStore } from '@/store';

const useApp = useAppStore();
const useForm = useFormStore();

const props = defineProps({
  type: { type: String, default: 'page' }, //弹出层模式
  show: { type: Boolean, default: false },
  width: { type: String, default: '80%' },
  showAction: { type: Boolean, default: true },
  title: { type: String, default: '编辑' },
});

/**
 * form:cancel 点击取消按钮事件
 *
 * */
const emits = defineEmits(['update:show', 'after-leave']);

/**
 * 内容区域引用
 * */
const refContent = ref();

const dialog = computed<boolean>({
  get() {
    let value = props.show;
    return value;
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
 * 监听cancel变量的变化
 * */
watch(
  () => useForm.closeForm,
  () => {
    dialog.value = false;
    //关闭窗口后恢复状态，否则下一次不能触发
    useForm.closeForm = false;
  },
);

/**
 * 取消
 * */
function handleCancel() {
  dialog.value = false;
}

/**
 * 失活时关闭弹出层界面
 * */
onDeactivated(() => {
  dialog.value = false;
});
</script>
<style lang="scss" scoped>
.page-view-content {
  height: 100%;
  display: flex;
  flex-direction: column;

  image {
    max-width: 100%;
  }

  :deep(.n-table) {
    table-layout: fixed;
  }
}
</style>
