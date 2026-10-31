<template><div ref="container" class="cms-source-editor" :style="editorTheme" /></template>
<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { useThemeVars } from 'naive-ui';
// @ts-ignore CodeMirror 5 沿用项目现有编辑器依赖。
import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/mode/htmlmixed/htmlmixed';
import 'codemirror/mode/css/css';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/addon/edit/matchbrackets';
import 'codemirror/addon/selection/active-line';
const props = defineProps<{ modelValue: string; path: string; disabled?: boolean }>();
const emit = defineEmits<{ 'update:modelValue': [value: string]; save: [] }>();
const container = ref<HTMLDivElement>();
const theme = useThemeVars();
const editorTheme = computed(() => ({
  '--editor-surface': theme.value.cardColor,
  '--editor-text': theme.value.textColor1,
  '--editor-muted': theme.value.textColor3,
  '--editor-border': theme.value.borderColor,
  '--editor-gutter': theme.value.actionColor,
  '--editor-active': theme.value.hoverColor,
  '--editor-primary': theme.value.primaryColor,
}));
let editor: any;
let resizeObserver: ResizeObserver | undefined;
const mode = () => props.path.endsWith('.css') ? 'css' : /\.(js|mjs|json)$/.test(props.path) ? 'javascript' : 'htmlmixed';
onMounted(() => {
  editor = CodeMirror(container.value!, { value: props.modelValue, mode: mode(), lineNumbers: true, lineWrapping: true, indentUnit: 2, autoCloseBrackets: true, matchBrackets: true, styleActiveLine: true,
    extraKeys: { 'Ctrl-S': () => emit('save'), 'Cmd-S': () => emit('save') }, readOnly: props.disabled });
  // 面板宽高变化时刷新行号和换行，避免响应式布局下编辑区域残留旧尺寸。
  resizeObserver = new ResizeObserver(() => editor?.refresh());
  resizeObserver.observe(container.value!);
  editor.on('change', () => { const value = editor.getValue(); if (value !== props.modelValue) emit('update:modelValue', value); });
});
watch(() => props.modelValue, value => { if (editor && editor.getValue() !== value) editor.setValue(value); });
watch(() => props.path, () => { editor?.setOption('mode', mode()); editor?.clearHistory(); });
watch(() => props.disabled, value => editor?.setOption('readOnly', value));
onBeforeUnmount(() => { resizeObserver?.disconnect(); editor?.getWrapperElement().remove(); editor = undefined; });
defineExpose({ cursor: () => editor?.indexFromPos(editor.getCursor()) ?? 0, insert: (value: string) => editor?.replaceSelection(value), replace: (start: number, end: number, value: string) => editor?.replaceRange(value, editor.posFromIndex(start), editor.posFromIndex(end)) });
</script>
<style scoped>
.cms-source-editor { height: 100%; min-height: 320px; overflow: hidden; }
.cms-source-editor :deep(.CodeMirror) {
  height: 100%;
  color: var(--editor-text);
  background: var(--editor-surface);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.8;
}
.cms-source-editor :deep(.CodeMirror-gutters) { background: var(--editor-gutter); border-right: 1px solid var(--editor-border); }
.cms-source-editor :deep(.CodeMirror-linenumber) { color: var(--editor-muted); padding: 0 10px 0 8px; }
.cms-source-editor :deep(.CodeMirror-lines) { padding: 12px 0; }
.cms-source-editor :deep(.CodeMirror pre.CodeMirror-line),
.cms-source-editor :deep(.CodeMirror pre.CodeMirror-line-like) { padding: 0 12px; }
.cms-source-editor :deep(.CodeMirror-activeline-background) { background: var(--editor-active); }
.cms-source-editor :deep(.CodeMirror-cursor) { border-left-color: var(--editor-primary); }
</style>
