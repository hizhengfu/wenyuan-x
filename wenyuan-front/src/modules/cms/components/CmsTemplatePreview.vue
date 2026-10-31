<template>
  <section
    class="cms-template-preview"
    :style="previewTheme"
    :role="closable ? 'dialog' : undefined"
    :aria-modal="closable ? true : undefined"
    :aria-label="closable ? '模板真实预览' : undefined"
  >
    <header class="preview-header">
      <div class="preview-title">
        <NIcon :size="20"><EyeIcon /></NIcon>
        <div>
          <strong>{{ templateName || "模板" }} · 真实预览</strong
          ><span>后端渲染快照，修改后需手动刷新</span>
        </div>
      </div>
      <div class="preview-actions">
        <div class="device-switch" role="group" aria-label="预览尺寸">
          <CmsTemplateActionButton
            v-for="device in devices"
            :key="device.value"
            :label="device.label"
            :icon="device.icon"
            :aria-pressed="width === device.value"
            :class="{ 'device-active': width === device.value }"
            @click="emit('update:width', device.value)"
          />
        </div>
        <CmsTemplateActionButton
          label="刷新真实预览"
          :icon="RefreshIcon"
          type="primary"
          :loading="busy"
          :disabled="busy"
          @click="emit('refresh')"
          >刷新预览</CmsTemplateActionButton
        >
        <CmsTemplateActionButton
          v-if="allowNewWindow"
          label="新窗口预览"
          :icon="OpenIcon"
          :disabled="busy"
          @click="emit('open-window')"
        />
        <CmsTemplateActionButton
          v-if="closable"
          label="关闭预览"
          :icon="CloseIcon"
          @click="emit('close')"
        />
      </div>
    </header>
    <div class="preview-status">
      <span>{{ entry ? "渲染文件：" + entry : "正在准备预览" }}</span
      ><span
        >{{
          renderedAt
            ? "渲染于 " + new Date(renderedAt).toLocaleTimeString()
            : ""
        }}<span v-if="stale" class="stale-note">
          · 文件或预览条件已变化，请刷新</span
        ></span
      >
    </div>
    <NAlert
      v-if="error"
      type="error"
      title="预览渲染失败"
      class="preview-error"
      >{{ error }}</NAlert
    >
    <div class="preview-canvas" :aria-busy="busy">
      <iframe
        v-if="html"
        :srcdoc="html"
        sandbox="allow-scripts"
        referrerpolicy="no-referrer"
        title="模板真实预览"
        :style="{ width: width ? width + 'px' : '100%' }"
      />
      <NEmpty
        v-else
        :description="
          busy ? '正在使用真实内容渲染…' : '点击刷新预览查看文件效果'
        "
        class="preview-empty"
        ><template #icon
          ><NIcon><MonitorIcon /></NIcon></template
      ></NEmpty>
    </div>
    <footer class="preview-footer">
      <span
        ><NIcon :size="14"><ShieldIcon /></NIcon
        >预览读取真实文件，保存修改会影响正式页面</span
      ><span>{{
        devices.find((device) => device.value === width)?.label || width + "px"
      }}</span>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { NAlert, NEmpty, NIcon, useThemeVars } from "naive-ui";
import EyeIcon from "~icons/mdi/eye-outline";
import RefreshIcon from "~icons/mdi/refresh";
import OpenIcon from "~icons/mdi/open-in-new";
import CloseIcon from "~icons/mdi/close";
import MonitorIcon from "~icons/mdi/monitor";
import TabletIcon from "~icons/mdi/tablet";
import PhoneIcon from "~icons/mdi/cellphone";
import ShieldIcon from "~icons/mdi/shield-check-outline";
import CmsTemplateActionButton from "./CmsTemplateActionButton.vue";

defineProps<{
  templateName?: string;
  html: string;
  entry: string;
  error: string;
  busy: boolean;
  width: number;
  renderedAt?: number;
  stale?: boolean;
  closable?: boolean;
  allowNewWindow?: boolean;
}>();
const emit = defineEmits<{
  refresh: [];
  "update:width": [value: number];
  "open-window": [];
  close: [];
}>();
const theme = useThemeVars();
const previewTheme = computed(() => ({
  "--preview-surface": theme.value.cardColor,
  "--preview-header": theme.value.actionColor,
  "--preview-border": theme.value.borderColor,
  "--preview-text": theme.value.textColor1,
  "--preview-muted": theme.value.textColor3,
  "--preview-primary": theme.value.primaryColor,
  "--preview-primary-soft": `color-mix(in srgb, ${theme.value.primaryColor} 9%, transparent)`,
  "--preview-warning": theme.value.warningColor,
}));
const devices = [
  { label: "桌面自适应", value: 0, icon: MonitorIcon },
  { label: "平板 768px", value: 768, icon: TabletIcon },
  { label: "手机 390px", value: 390, icon: PhoneIcon },
];
</script>
<style scoped>
.cms-template-preview {
  display: flex;
  flex-direction: column;
  min-height: 0;
  color: var(--preview-text);
  background: var(--preview-surface);
  border: 1px solid var(--preview-border);
  border-radius: 8px;
  overflow: hidden;
}
.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--preview-border);
}
.preview-title,
.preview-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.preview-title > .n-icon {
  color: var(--preview-primary);
}
.preview-title > div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.preview-title strong {
  font-size: 14px;
  font-weight: 600;
}
.preview-title span {
  font-size: 11px;
  color: var(--preview-muted);
}
.preview-actions {
  margin-left: auto;
  gap: 8px;
}
.device-switch {
  display: flex;
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--preview-border);
  border-radius: 6px;
  background: var(--preview-header);
}
.device-switch :deep(.n-button.device-active) {
  color: var(--preview-primary);
  background: var(--preview-primary-soft);
}
.preview-status {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  padding: 9px 16px;
  font-size: 11px;
  color: var(--preview-muted);
  border-bottom: 1px solid var(--preview-border);
}
.stale-note {
  color: var(--preview-warning);
}
.preview-canvas {
  display: flex;
  flex: 1;
  min-height: 0;
  justify-content: center;
  align-items: stretch;
  overflow: auto;
  padding: 18px;
  background: var(--preview-header);
}
.preview-canvas iframe {
  border: 1px solid var(--preview-border);
  border-radius: 4px;
  min-height: 320px;
  flex-shrink: 0;
  background: white;
  box-shadow: 0 3px 14px rgb(0 0 0 / 4%);
}
.preview-empty {
  align-self: center;
  padding: 30px;
}
.preview-error {
  margin: 12px 16px;
  flex-shrink: 0;
}
.preview-footer {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 9px 16px;
  border-top: 1px solid var(--preview-border);
  color: var(--preview-muted);
  font-size: 11px;
}
.preview-footer > span:first-child {
  display: flex;
  align-items: center;
  gap: 6px;
}
@media (max-width: 650px) {
  .preview-header {
    padding: 12px;
  }
  .preview-canvas {
    padding: 10px;
  }
  .preview-actions {
    flex-wrap: wrap;
  }
}
</style>
