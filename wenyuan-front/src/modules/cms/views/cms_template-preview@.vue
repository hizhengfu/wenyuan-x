<template>
  <CmsTemplatePreview
    class="standalone-preview"
    :template-name="templateName"
    :html="html"
    :entry="entry"
    :error="error"
    :busy="busy"
    v-model:width="width"
    :rendered-at="renderedAt"
    @refresh="refresh"
  />
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { templateFiles } from "../api/templateFiles";
import { parseTemplatePreviewQuery } from "../utils/templatePreviewContext";
import CmsTemplatePreview from "../components/CmsTemplatePreview.vue";

const route = useRoute();
const templateName = ref(""),
  html = ref(""),
  entry = ref(""),
  error = ref("");
const busy = ref(false),
  width = ref(0),
  renderedAt = ref<number>();
let requestId = 0;
async function refresh() {
  const ticket = ++requestId;
  busy.value = true;
  error.value = "";
  try {
    const request = parseTemplatePreviewQuery(route.query);
    // 每次明确刷新读取最新真实文件；独立窗口不能保存编辑器内尚未提交的修改。
    const result = (await templateFiles.preview(request.context)).data;
    if (ticket !== requestId) return;
    templateName.value = request.context.templateCode;
    document.title = `${request.context.templateCode} · 模板预览`;
    html.value = result.html;
    entry.value = result.entry;
    renderedAt.value = Date.now();
  } catch (failure: any) {
    if (ticket === requestId)
      error.value =
        failure?.response?.data?.message || failure?.message || "预览渲染失败";
  } finally {
    if (ticket === requestId) busy.value = false;
  }
}
watch(
  () => route.query,
  () => {
    html.value = "";
    entry.value = "";
    renderedAt.value = undefined;
    try {
      width.value = parseTemplatePreviewQuery(route.query).width;
    } catch {
      /* refresh 统一显示无效链接的具体原因。 */
    }
    void refresh();
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  ++requestId;
});
</script>
<style scoped>
.standalone-preview {
  width: 100%;
  height: 100dvh;
  border: 0;
  border-radius: 0;
}
</style>
