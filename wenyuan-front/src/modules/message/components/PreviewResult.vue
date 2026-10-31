<template>
  <div v-if="!result" class="preview-empty">{{ emptyText }}</div>
  <n-tabs v-else type="line" animated default-value="rendered" size="small">
    <!-- 渲染视图：还原各通道实际效果 —— EMAIL/InApp 用 v-html，其余按文本 -->
    <n-tab-pane name="rendered" tab="渲染结果">
      <n-descriptions :column="1" size="small" bordered label-placement="left" label-style='width:200px;' labelAlign='right'>
        <n-descriptions-item label="EMAIL 主题">{{ result.emailSubject || '-' }}</n-descriptions-item>
        <n-descriptions-item label="EMAIL 正文">
          <div v-if="result.emailBody" v-html="result.emailBody" class="render-html" />
          <span v-else>-</span>
        </n-descriptions-item>
        <n-descriptions-item label="SMS 模板编号 / 参数">
          {{ result.smsTemplateCode || '-' }} / {{ result.smsTemplateParam || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="SMS 正文（电信 HTTP）">{{ result.smsContent || '-' }}</n-descriptions-item>
        <n-descriptions-item label="站内信标题">{{ result.inAppTitle || '-' }}</n-descriptions-item>
        <n-descriptions-item label="站内信正文">
          <div v-if="result.inAppContent" v-html="result.inAppContent" class="render-html" />
          <span v-else>-</span>
        </n-descriptions-item>
        <n-descriptions-item label="推送标题 / Body">
          {{ result.pushTitle || '-' }} / {{ result.pushBody || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="推送 Payload">{{ result.pushPayload || '-' }}</n-descriptions-item>
        <n-descriptions-item label="公众号模板">
          {{ result.wxMpAppCode || '-' }} / {{ result.wxMpTemplateId || '-' }} / {{ result.wxMpUrl || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="公众号数据">{{ result.wxMpData || '-' }}</n-descriptions-item>
        <n-descriptions-item label="小程序模板">
          {{ result.wxMaAppCode || '-' }} / {{ result.wxMaTemplateId || '-' }} / {{ result.wxMaPage || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="小程序数据">
          {{ result.wxMaData || '-' }} / {{ result.wxMaMiniprogramState || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="企业微信">
          {{ result.wxCpAgentId ?? '-' }} / {{ result.wxCpMsgType || '-' }} / {{ result.wxCpTitle || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="企业微信正文">{{ result.wxCpContent || '-' }}</n-descriptions-item>
        <n-descriptions-item label="企业微信 URL">{{ result.wxCpUrl || '-' }}</n-descriptions-item>
        <n-descriptions-item label="群机器人">
          {{ result.wxBotMsgType || '-' }} / {{ result.wxBotMentionUserIds || '-' }}
        </n-descriptions-item>
        <n-descriptions-item label="群机器人正文">{{ result.wxBotContent || '-' }}</n-descriptions-item>
      </n-descriptions>
    </n-tab-pane>

    <!-- JSON 视图：raw 字符串，方便运维比对占位符是否被替换、转义是否正确 -->
    <n-tab-pane name="json" tab="JSON">
      <pre class="result-pre">{{ resultJson }}</pre>
    </n-tab-pane>
  </n-tabs>
</template>

<script lang="ts" setup>
import type { MessageTemplatePreview } from '../typings/message';

/**
 * 模板预览结果的双视图展示组件。
 *
 * - 渲染视图：HTML 字段用 v-html 还原成所见即所得，便于人眼校对样式
 * - JSON 视图：raw 字符串，便于排查"占位符是否替换""转义是否正确"等运维问题
 *
 * 同时被「模板预览」对话框和「试发」对话框内嵌的预览段消费，避免两处重复样式。
 */
defineProps<{
  result: MessageTemplatePreview | null;
  /** 由父组件预计算好的 JSON 字符串（与 result 同步） */
  resultJson: string;
  /** result 为空时的占位文案（可省略） */
  emptyText?: string;
}>();
</script>

<style scoped>
.preview-empty {
  color: #999;
  font-size: 12px;
  padding: 12px 0;
}
.render-html {
  max-height: 320px;
  overflow: auto;
  padding: 8px;
  background: var(--n-card-color, #f6f8fa);
  border-radius: 4px;
}
.result-pre {
  margin: 0;
  width: 100%;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  background: var(--n-card-color, #f6f8fa);
  padding: 8px 10px;
  border-radius: 4px;
}
</style>
