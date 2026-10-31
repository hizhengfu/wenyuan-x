<template>
  <template v-if="targetPath">
    <CurrentDocumentPreview
      v-if="['pdf', 'ppt', 'pptx', 'doc', 'docx', 'xls', 'xlsx', 'caj'].includes(fileType)"
      v-bind="$attrs"
      :src="targetPath"
      :kind="fileType"
      :revision="revision"
    />
    <template v-else-if="fileType === 'image'">
      <div v-bind="$attrs" class="file-preview-image" style="text-align: center">
        <CurrentFileImage :src="targetPath" :width="800" :revision="revision" />
      </div>
    </template>
    <template v-else-if="fileType === 'video'">
      <MediaPlayer :url="targetPath" :key="targetPath" v-bind="$attrs"></MediaPlayer>
    </template>
    <template v-else-if="fileType === 'audio'">
      <AudioPlayer v-bind="$attrs" :url="targetPath" :key="targetPath"></AudioPlayer>
    </template>
    <CurrentScenePreview
      v-else-if="fileType === 'model' || fileType === 'tileset' || fileType === 'panorama'"
      v-bind="$attrs"
      class="file-preview-wrap"
      :src="targetPath"
      :kind="fileType"
      :revision="revision"
    />
    <template v-else>
      <div v-bind="$attrs" style="height: 300px">
        <n-element tag="a" :href="targetPath" v-download="targetPath">点击下载</n-element>
      </div>
    </template>
  </template>
  <div v-else v-bind="$attrs" class="wh-full flex-center">
    <n-empty description="暂无数据"></n-empty>
  </div>
</template>
<script lang="ts" setup>
import CurrentDocumentPreview from './CurrentDocumentPreview.vue';
import CurrentFileImage from './CurrentFileImage.vue';
import MediaPlayer from '@/components/basic/player/MediaPlayer.vue';
import AudioPlayer from '@/components/basic/player/AudioPlayer.vue';
import CurrentScenePreview from './CurrentScenePreview.vue';
import { computed } from 'vue';
import { apiUrl, fileUrl } from '@/utils';

defineOptions({ inheritAttrs: false });

const props = defineProps({
  path: { type: String }, //文件内容
  revision: { type: [String, Number] }, //同地址原件更新的业务版本
  previewType: { type: [String, Number] }, //预览类型
});

function isTilesetPath(path: string) {
  return /(^|\/)tileset\.json(?:$|[?#])/i.test(path);
}

/**
 * 文件类型检测
 * */
const fileType = computed(() => {
  const { previewType } = props;
  const path = props.path?.split(/[?#]/, 1)[0];
  if (!path) return 'link';
  if (/\.pdf$/i.test(path)) {
    return 'pdf';
  } else if (/\.(xlsx)$/i.test(path)) {
    return 'xlsx';
  } else if (/\.(docx)$/i.test(path)) {
    return 'docx';
  } else if (/\.(ppt|pptx|doc|xls|caj)$/i.test(path)) {
    return path.split('.').pop()!.toLowerCase();
  } else if (/\.(jpg|jpeg|png|gif|bmp|webp)$/i.test(path)) {
    return 'image';
  } else if (isTilesetPath(path)) {
    return 'tileset';
  } else if (/\.(dae|fbx|gltf|glb|obj|ply|stl)$/i.test(path)) {
    return 'model';
  } else if (/\.(mp4)$/i.test(path)) {
    return 'video';
  } else if (/\.(mp3|3gp|ogg|webm)$/i.test(path)) {
    return 'audio';
  } else {
    if (previewType) {
      return `${previewType}`.toLowerCase();
    }
    return 'link';
  }
});

/**
 * 生成目标路径
 * */
const targetPath = computed(() => {
  if (!props.path) return null;
  const path = props.path as string;
  return /^\/?file\/preview(?:[/?]|$)/.test(path) ? apiUrl(path) : fileUrl(path);
});
</script>
<style lang="css" scoped>
.file-preview-wrap {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  right: 0;
  overflow: hidden;
  box-sizing: border-box;
}

.file-preview-document {
  display: block;
  width: 100%;
  min-height: 600px;
  height: 100%;
  border: 0;
}

.file-preview-image :deep(img) {
  width: 100%;
}
</style>
