<template>
  <div v-if="model" class="template-detail">
    <!-- 基础信息 -->
    <n-descriptions
      :column="2"
      size="small"
      bordered
      label-placement="left"
      labelAlign="right"
      label-style="width:200px;"
    >
      <n-descriptions-item label="模板编码">{{ model.code || '-' }}</n-descriptions-item>
      <n-descriptions-item label="模板名称">{{ model.name || '-' }}</n-descriptions-item>
      <n-descriptions-item label="业务分组">
        <HDictText :value="model.bizType" type="notify_biz_type" />
      </n-descriptions-item>
      <n-descriptions-item label="语言">{{ model.lang || '默认' }}</n-descriptions-item>
      <n-descriptions-item label="默认通道">
        <HDictText type="notify_channel" :value="model.defaultChannels"></HDictText>
      </n-descriptions-item>
      <n-descriptions-item label="参数 keys">{{ model.paramKeys || '-' }}</n-descriptions-item>
      <n-descriptions-item label="系统内置">
        <n-tag v-if="model.systemBuiltin" size="small" type="warning">是</n-tag>
        <span v-else>否</span>
      </n-descriptions-item>
      <n-descriptions-item label="更新时间">
        <n-time v-if="model.updateTime" :time="toMs(model.updateTime)" type="datetime" />
        <span v-else>-</span>
      </n-descriptions-item>
    </n-descriptions>

    <!-- 多通道：按通道分卡片展示，仅展示该通道有内容的字段 -->
    <n-tabs type="line" animated default-value="email" style="margin-top: 16px">
      <n-tab-pane name="email" tab="邮件">
        <n-descriptions
          :column="1"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="主题模板">{{ model.emailSubjectTpl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="正文模板（HTML）">
            <pre v-if="model.emailBodyTpl" class="tpl-pre">{{ model.emailBodyTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="sms" tab="短信">
        <n-descriptions
          :column="1"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="短信平台模板编号">{{ model.smsTemplateCode || '-' }}</n-descriptions-item>
          <n-descriptions-item label="参数映射">{{ model.smsParamMapping || '-' }}</n-descriptions-item>
          <n-descriptions-item label="短信正文模板（电信 HTTP）">
            <pre v-if="model.smsContentTpl" class="tpl-pre">{{ model.smsContentTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="inApp" tab="站内信">
        <n-descriptions
          :column="1"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="标题模板">{{ model.inAppTitleTpl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="正文模板">
            <pre v-if="model.inAppContentTpl" class="tpl-pre">{{ model.inAppContentTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="push" tab="APP 推送">
        <n-descriptions
          :column="1"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="标题模板">{{ model.pushTitleTpl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="Body 模板">{{ model.pushBodyTpl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="Payload 模板">
            <pre v-if="model.pushPayloadTpl" class="tpl-pre">{{ model.pushPayloadTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="wxMp" tab="微信公众号">
        <n-descriptions
          :column="2"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="appCode">{{ model.wxMpAppCode || '默认' }}</n-descriptions-item>
          <n-descriptions-item label="模板 ID">{{ model.wxMpTemplateId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="跳转 URL" :span="2">{{ model.wxMpUrl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="data 映射 JSON" :span="2">
            <pre v-if="model.wxMpDataMapping" class="tpl-pre">{{ model.wxMpDataMapping }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="wxMa" tab="微信小程序">
        <n-descriptions
          :column="2"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="appCode">{{ model.wxMaAppCode || '默认' }}</n-descriptions-item>
          <n-descriptions-item label="模板 ID">{{ model.wxMaTemplateId || '-' }}</n-descriptions-item>
          <n-descriptions-item label="小程序版本">{{ model.wxMaMiniprogramState || '-' }}</n-descriptions-item>
          <n-descriptions-item label="跳转页面">{{ model.wxMaPage || '-' }}</n-descriptions-item>
          <n-descriptions-item label="data 映射 JSON" :span="2">
            <pre v-if="model.wxMaDataMapping" class="tpl-pre">{{ model.wxMaDataMapping }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="wxCp" tab="企业微信">
        <n-descriptions
          :column="2"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="agentId">{{ model.wxCpAgentId ?? '默认' }}</n-descriptions-item>
          <n-descriptions-item label="msgType">{{ model.wxCpMsgType || '-' }}</n-descriptions-item>
          <n-descriptions-item label="标题（textcard）" :span="2">{{ model.wxCpTitle || '-' }}</n-descriptions-item>
          <n-descriptions-item label="跳转 URL" :span="2">{{ model.wxCpUrl || '-' }}</n-descriptions-item>
          <n-descriptions-item label="正文模板" :span="2">
            <pre v-if="model.wxCpContentTpl" class="tpl-pre">{{ model.wxCpContentTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>

      <n-tab-pane name="wxBot" tab="群机器人">
        <n-descriptions
          :column="2"
          size="small"
          bordered
          label-placement="left"
          labelAlign="right"
          label-style="width:200px;"
        >
          <n-descriptions-item label="msgType">{{ model.wxBotMsgType || '-' }}</n-descriptions-item>
          <n-descriptions-item label="@用户">{{ model.wxBotMentionUserIds || '-' }}</n-descriptions-item>
          <n-descriptions-item label="正文模板" :span="2">
            <pre v-if="model.wxBotContentTpl" class="tpl-pre">{{ model.wxBotContentTpl }}</pre>
            <span v-else>-</span>
          </n-descriptions-item>
        </n-descriptions>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script lang="ts" setup>
import type { MessageTemplateVo } from '../typings/message';

const props = defineProps<{
  model: MessageTemplateVo | null;
}>();

function toMs(s: string | undefined): number {
  if (!s) return 0;
  const t = new Date(s).getTime();
  return Number.isNaN(t) ? 0 : t;
}
</script>

<style scoped>
.template-detail :deep(.n-descriptions-table-wrapper) {
  max-width: 100%;
}
.tpl-pre {
  margin: 0;
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
