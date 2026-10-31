<template>
  <div v-if="dialog" class="page-view">
    <n-card
      :title="title"
      hoverable
      closable
      size="small"
      style="width: 100%;"
      :content-style="{flex:1,display:'flex',overflow: 'auto',flexDirection: 'column'}"
      :segmented="{content: true,action: true}"
      @close="handleCancel">
      <template #header-extra>
        <h-full-screen :target="refContent" quaternary icon-size="20px"></h-full-screen>
      </template>
      <div ref="refContent" class="content">
        <slot></slot>
      </div>
      <template v-if="showAction" #action>
        <div class="page-view-action"></div>
      </template>
    </n-card>
  </div>
</template>
<script lang="ts" setup>
import {computed, onDeactivated, watch, ref} from 'vue';
import {useFormStore} from '../store/form'

const useForm = useFormStore()

const props = defineProps({
  type: {type: String, default: 'page'},//弹出层模式
  show: {type: Boolean, default: false},
  showAction: {type: Boolean, default: true},
  title: {type: String, default: '编辑'},
})

/**
 * form:cancel 点击取消按钮事件
 *
 * */
const emits = defineEmits(['update:show'])

/**
 * 内容区域引用
 * */
const refContent = ref()

const dialog = computed<boolean>({
  get() {
    return props.show
  },
  set(value) {
    emits('update:show', value)
  }
})

/**
 * 监听cancel变量的变化
 * */
watch(() => useForm.closeForm, () => {
  dialog.value = false
  //关闭窗口后恢复状态，否则下一次不能触发
  useForm.closeForm = false
})

/**
 * 取消
 * */
function handleCancel() {
  dialog.value = false
}

/**
 * 失活时关闭弹出层界面
 * */
onDeactivated(() => {
  dialog.value = false
})
</script>
<style scoped>
.page-view {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  padding: 15px;
  overflow: hidden;
  display: flex;

  .content {
    overflow: auto;
    flex: 1;
    display: flex;
    flex-direction: column;
    position: relative;

    image {
      max-width: 100%;
    }
  }

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
</style>
