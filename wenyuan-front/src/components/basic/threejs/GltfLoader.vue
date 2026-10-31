<script setup lang="ts">
//@ts-ignore
import { getAuthenticationHeaders } from '@/utils/auth';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';
//@ts-ignore
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader';
import { fileUrl } from '@/utils';

if (!ENABLE_DRACO) {
  throw Error('请在config配置文件中设置enable_draco为true');
}
const dracoLoader = new DRACOLoader();
dracoLoader.setDecoderPath('./draco/gltf/');

function loadGLTF(path: string): Promise<any> {
  const loader = new GLTFLoader();
  loader.setRequestHeader(getAuthenticationHeaders());
  loader.setWithCredentials(true);
  loader.setDRACOLoader(dracoLoader);
  return new Promise((resolve, reject) => {
    loader.load(path, resolve, undefined, (error:any) => {
      console.error('加载GLTF模型失败:', path, error);
      reject(error);
    });
  });
}

const props = defineProps({
  path: { type: String, required: true },
});

const { scene } = await loadGLTF(fileUrl(props.path));
</script>

<template>
  <primitive :object="scene" />
</template>
