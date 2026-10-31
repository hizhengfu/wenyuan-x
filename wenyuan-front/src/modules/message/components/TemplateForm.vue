<template>
  <n-form ref="formRef" :model="model" :rules="rules" label-placement="top">
    <n-grid x-gap="12" :cols="2">
      <n-grid-item>
        <n-form-item label="模板编码" path="code">
          <n-input v-model:value="model.code" :disabled="model.systemBuiltin" placeholder="如 USER_REGISTER" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item>
        <n-form-item label="模板名称" path="name">
          <n-input v-model:value="model.name" placeholder="管理台展示用" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item>
        <n-form-item label="业务分组" path="bizType">
          <HDictSelect v-model:value="model.bizType" type="notify_biz_type" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item>
        <n-form-item label="语言" path="lang">
          <n-input v-model:value="model.lang" placeholder="zh-CN / en-US，留空匹配默认" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="2">
        <n-form-item label="默认通道" path="defaultChannels">
          <ChannelSelector :model-value="defaultChannelsArray" @update:model-value="onDefaultChannelsChange" />
        </n-form-item>
      </n-grid-item>
      <n-grid-item :span="2">
        <n-form-item label="必需参数 keys（逗号分隔，发送时校验）" path="paramKeys">
          <n-input v-model:value="model.paramKeys" placeholder="如 userName,verifyCode" />
        </n-form-item>
      </n-grid-item>
    </n-grid>

    <n-tabs type="line" animated default-value="email">
      <n-tab-pane name="email" tab="邮件">
        <n-form-item label="主题模板" path="emailSubjectTpl">
          <n-input v-model:value="model.emailSubjectTpl" placeholder="支持 ${var} 占位" />
        </n-form-item>
        <n-form-item label="正文模板（HTML）" path="emailBodyTpl">
          <n-input
            v-model:value="model.emailBodyTpl"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 14 }"
            placeholder="保存时会过滤 <script> 等危险标签"
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="sms" tab="短信">
        <n-alert type="info" :show-icon="false">
          阿里云、移动云信使用平台已审核的模板编号；电信 HTTP 使用下方正文模板。移动云信变量映射示例：code:p1，每个变量最多 20 个字符。
        </n-alert>
        <n-form-item label="模板编号" path="smsTemplateCode">
          <n-input v-model:value="model.smsTemplateCode" placeholder="阿里云 SMS_xxx 或移动云信已审核的模板编号" />
        </n-form-item>
        <n-form-item label="参数映射（业务字段 → 平台变量）" path="smsParamMapping">
          <n-input
            v-model:value="model.smsParamMapping"
            placeholder="阿里云：code:verifyCode；移动云信：code:p1,name:p2"
          />
        </n-form-item>
        <n-form-item label="短信正文模板（电信 HTTP）" path="smsContentTpl">
          <n-input
            v-model:value="model.smsContentTpl"
            type="textarea"
            :autosize="{ minRows: 3, maxRows: 8 }"
            placeholder="例如：您的验证码为${code}。报备签名由系统参数配置自动添加。"
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="inApp" tab="站内信">
        <n-form-item label="标题模板" path="inAppTitleTpl">
          <n-input v-model:value="model.inAppTitleTpl" />
        </n-form-item>
        <n-form-item label="正文模板（富文本）" path="inAppContentTpl">
          <n-input
            v-model:value="model.inAppContentTpl"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 14 }"
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="push" tab="APP 推送">
        <n-form-item label="标题模板" path="pushTitleTpl">
          <n-input v-model:value="model.pushTitleTpl" />
        </n-form-item>
        <n-form-item label="Body 模板" path="pushBodyTpl">
          <n-input v-model:value="model.pushBodyTpl" />
        </n-form-item>
        <n-form-item label="Payload 模板（JSON 字符串）" path="pushPayloadTpl">
          <n-input
            v-model:value="model.pushPayloadTpl"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 8 }"
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="wxMp" tab="微信公众号">
        <n-grid x-gap="12" :cols="2">
          <n-grid-item>
            <n-form-item label="appCode" path="wxMpAppCode">
              <n-input v-model:value="model.wxMpAppCode" placeholder="留空用 NOTIFY_WX_MP_DEFAULT_APP_CODE" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="模板 ID" path="wxMpTemplateId">
              <n-input v-model:value="model.wxMpTemplateId" />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="跳转 URL" path="wxMpUrl">
          <n-input v-model:value="model.wxMpUrl" />
        </n-form-item>
        <n-form-item label="data 映射 JSON" path="wxMpDataMapping">
          <n-input
            v-model:value="model.wxMpDataMapping"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
            placeholder='{"first":{"value":"${name}","color":"#173177"}}'
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="wxMa" tab="微信小程序">
        <n-grid x-gap="12" :cols="3">
          <n-grid-item>
            <n-form-item label="appCode" path="wxMaAppCode">
              <n-input v-model:value="model.wxMaAppCode" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="模板 ID" path="wxMaTemplateId">
              <n-input v-model:value="model.wxMaTemplateId" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="小程序版本" path="wxMaMiniprogramState">
              <n-select
                v-model:value="model.wxMaMiniprogramState"
                clearable
                :options="[
                  { label: '正式版', value: 'formal' },
                  { label: '体验版', value: 'trial' },
                  { label: '开发版', value: 'developer' },
                ]"
              />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="跳转页面" path="wxMaPage">
          <n-input v-model:value="model.wxMaPage" placeholder="pages/order/detail?id=${id}" />
        </n-form-item>
        <n-form-item label="data 映射 JSON" path="wxMaDataMapping">
          <n-input
            v-model:value="model.wxMaDataMapping"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
          />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="wxCp" tab="企业微信">
        <n-grid x-gap="12" :cols="2">
          <n-grid-item>
            <n-form-item label="agentId" path="wxCpAgentId">
              <n-input-number v-model:value="model.wxCpAgentId" :show-button="false" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="msgType" path="wxCpMsgType">
              <n-select
                v-model:value="model.wxCpMsgType"
                clearable
                :options="[
                  { label: 'text', value: 'text' },
                  { label: 'markdown', value: 'markdown' },
                  { label: 'textcard', value: 'textcard' },
                ]"
              />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="标题（textcard 必填）" path="wxCpTitle">
          <n-input v-model:value="model.wxCpTitle" />
        </n-form-item>
        <n-form-item label="正文模板" path="wxCpContentTpl">
          <n-input
            v-model:value="model.wxCpContentTpl"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
          />
        </n-form-item>
        <n-form-item label="跳转 URL" path="wxCpUrl">
          <n-input v-model:value="model.wxCpUrl" />
        </n-form-item>
      </n-tab-pane>

      <n-tab-pane name="wxBot" tab="群机器人">
        <n-grid x-gap="12" :cols="2">
          <n-grid-item>
            <n-form-item label="msgType" path="wxBotMsgType">
              <n-select
                v-model:value="model.wxBotMsgType"
                clearable
                :options="[
                  { label: 'text', value: 'text' },
                  { label: 'markdown', value: 'markdown' },
                  { label: 'news', value: 'news' },
                ]"
              />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="@用户（逗号分隔的系统 userId 或 @all）" path="wxBotMentionUserIds">
              <n-input v-model:value="model.wxBotMentionUserIds" />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="正文模板" path="wxBotContentTpl">
          <n-input
            v-model:value="model.wxBotContentTpl"
            type="textarea"
            :autosize="{ minRows: 4, maxRows: 10 }"
          />
        </n-form-item>
      </n-tab-pane>
    </n-tabs>
  </n-form>
</template>

<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import type { FormInst, FormRules } from 'naive-ui';
import ChannelSelector from './ChannelSelector.vue';
import type { MessageTemplateVo } from '../typings/message';

const props = defineProps<{
  modelValue: MessageTemplateVo;
}>();
const emit = defineEmits<{
  (e: 'update:modelValue', value: MessageTemplateVo): void;
}>();

const formRef = ref<FormInst | null>(null);

const model = ref<MessageTemplateVo>({ ...props.modelValue });

watch(
  () => props.modelValue,
  (v) => {
    model.value = { ...v };
  },
);

watch(
  model,
  (v) => {
    emit('update:modelValue', v);
  },
  { deep: true },
);

const rules: FormRules = {
  code: { required: true, message: '请输入模板编码', trigger: 'blur' },
  name: { required: true, message: '请输入模板名称', trigger: 'blur' },
};

/**
 * defaultChannels 在后端是逗号分隔字符串（如 "EMAIL,IN_APP"），
 * 这里以数组形式喂给 ChannelSelector，再 join 回字符串保存到 model。
 */
const defaultChannelsArray = computed<string[]>(() =>
  model.value.defaultChannels
    ? model.value.defaultChannels.split(',').map((s) => s.trim()).filter(Boolean)
    : [],
);

function onDefaultChannelsChange(value: string[]) {
  model.value.defaultChannels = value.length > 0 ? value.join(',') : '';
}

function validate() {
  return formRef.value?.validate();
}

defineExpose({ validate });
</script>
