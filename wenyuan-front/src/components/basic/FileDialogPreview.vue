<template>
  <n-modal
    v-model:show="dialog"
    :style="{ width: width }"
    size="small"
    :trap-focus="false"
    :content-style="{
      flex: 1,
      display: 'flex',
      boxSizing:'border-box',
      flexDirection: 'column',
      ...(overFlowShow ? {} : { overflow: 'auto' }),
      position: 'relative',
    }"
    :title="title"
    :segmented="{
      content: true,
      action: true,
    }"
    preset="card"
    :mask-closable="maskShow"
  >
    <template #header-extra>
      <HFullScreen v-if="showFull" :target="refContent" quaternary size="tiny" icon-size="20px"></HFullScreen>
    </template>
    <div ref="refContent" class="content" :class="['content-' + fileType]">
      <HFilePreview :preview-type="previewType" :path="path" v-bind="$attrs"></HFilePreview>
    </div>
    <div v-if="$slots.footer" style='position: absolute;bottom: 0;left:0;right:0;padding: 5px;box-sizing: border-box;background-color: #00000009'>
      <slot name="footer"></slot>
    </div>
  </n-modal>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';

const emits = defineEmits(['update:show']);
const props = defineProps({
  showFull: { type: Boolean, default: true },
  show: { type: Boolean, default: false },
  //提交后自动关闭
  autoClose: { type: Boolean, default: true },
  title: { type: String, default: '文件预览' },
  width: { type: String, default: '800px' },
  height: { type: String, default: '600px' },
  previewType: { type: String }, //预览类型
  path: { type: String }, //文件内容
  maskShow: { type: Boolean, default: true },
  overFlowShow: { type: Boolean, default: false },
});

const dialog = computed<boolean>({
  get() {
    return props.show as boolean;
  },
  set(value) {
    emits('update:show', value);
  },
});

const fileType = computed(() => {
  const { path, previewType } = props;
  if (!path) return 'link';
  if (/\.(dae|fbx|gltf|glb|obj|ply|stl)$/i.test(path)) {
    return 'model';
  } else {
    if (previewType) {
      return previewType.toLowerCase();
    } else {
      return 'link';
    }
  }
});

/**
 * 内容区域引用
 * */
const refContent = ref();
</script>
<style lang="scss" scoped>
.content {
  min-height: 300px;
  max-height: 600px;
  overflow: auto;
  position: relative;
  box-sizing: border-box;
}

.content-model,
.content-panorama {
  min-height: 600px;
}
</style>
