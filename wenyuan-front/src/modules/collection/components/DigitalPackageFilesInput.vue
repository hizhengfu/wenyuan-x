<template>
  <div class="digital-package-files">
    <NSpace>
      <NButton :disabled="unavailable || busy" @click="folderInput?.click()">导入文件夹</NButton>
      <NButton :disabled="unavailable || busy" @click="filesInput?.click()">选择同目录文件</NButton>
      <NButton v-if="busy" @click="cancel">取消导入</NButton>
      <NButton v-else :disabled="unavailable || !value?.length" @click="clear">清空选择</NButton>
    </NSpace>
    <input ref="folderInput" aria-label="选择成果文件夹" type="file" webkitdirectory multiple hidden :disabled="unavailable || busy" @change="select" />
    <input ref="filesInput" aria-label="选择同目录成果文件" type="file" multiple hidden :disabled="unavailable || busy" @change="select" />
    <p>文件夹导入保留相对路径；同目录选择使用文件名。重新导入会在全部上传成功后替换当前清单。</p>
    <p v-if="busy" role="status">正在上传 {{ completed }} / {{ total }}：{{ currentPath }}。完成前不能登记。</p>
    <p v-if="message" role="status">{{ message }}</p>
    <ul v-if="packageFiles && Object.keys(packageFiles).length">
      <li v-for="(id, path) in packageFiles" :key="path">{{ path }} · {{ id }}</li>
    </ul>
    <p v-else-if="value?.length">当前文件编号：{{ value.join('、') }}（历史选择未提供包路径）</p>
  </div>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue';
import { useCurrentImageLifecycle } from '@/utils/currentImage';
import { uploadDigitalPackage, type DigitalPackageResult } from '../utils/digitalPackageUpload';
const props = withDefaults(defineProps<{
  value?: string[]; packageFiles?: Record<string, string> | null;
  active?: boolean; contextKey?: number; disabled?: boolean;
}>(), { active: true, disabled: false });
const emit = defineEmits<{
  (e: 'change', result: DigitalPackageResult): void;
  (e: 'busy', busy: boolean): void;
}>();
const folderInput = ref<HTMLInputElement>(), filesInput = ref<HTMLInputElement>();
const busy = ref(false), message = ref(''), completed = ref(0), total = ref(0), currentPath = ref('');
let generation = 0, controller: AbortController | undefined;
function stop() {
  ++generation;
  controller?.abort(); controller = undefined;
  if (busy.value) {
    message.value = '本次导入已取消，清单未写入表单。已上传文件不因此删除。';
    busy.value = false; emit('busy', false);
  }
}
const { eligible } = useCurrentImageLifecycle(() => [props.active, props.contextKey], stop, () => {});
const unavailable = computed(() => !props.active || !eligible.value || props.disabled);
function cancel() { stop(); }
function clear() {
  if (unavailable.value || busy.value) return;
  message.value = '';
  emit('change', { fileIds: [], packageFiles: {} });
}
async function select(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (!files.length || unavailable.value || busy.value) return;
  const current = ++generation;
  const abort = new AbortController(); controller = abort;
  busy.value = true; emit('busy', true); message.value = '';
  completed.value = 0; total.value = files.length; currentPath.value = '';
  const isCurrent = () => current === generation && props.active && eligible.value;
  try {
    const result = await uploadDigitalPackage(files, {
      signal: abort.signal, isCurrent,
      progress: (done, count, path) => {
        if (isCurrent()) { completed.value = done; total.value = count; currentPath.value = path; }
      },
    });
    if (!isCurrent()) return;
    emit('change', result);
    message.value = `已导入 ${Object.keys(result.packageFiles).length} 个文件路径，请核对后登记。`;
  } catch (error) {
    if (isCurrent()) message.value = `${error instanceof Error ? error.message : '上传失败。'} 本次清单未写入表单，请重新选择重试。`;
  } finally {
    if (current === generation) { controller = undefined; busy.value = false; emit('busy', false); }
  }
}
</script>
<style scoped>
.digital-package-files { width: 100%; min-width: 0; }
p { margin: 8px 0; }
ul { max-height: 240px; overflow: auto; padding-left: 20px; overflow-wrap: anywhere; }
</style>
