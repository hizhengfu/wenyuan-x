<template>
  <n-card title="AI 配置" :bordered="false">
    <n-alert v-if="!form.enabled" type="warning" style="margin-bottom: 16px">
      AI 当前未启用。填写下方信息、打开「启用 AI」并保存后即时生效（无需重启）。
    </n-alert>

    <h-form
      :model="form"
      :handleSubmit="onSave"
      :enableTeleport="false"
      :enableCancel="false"
      label-placement="left"
      label-width="150"
      style="max-width: 720px"
    >
      <n-form-item label="启用 AI">
        <n-switch v-model:value="form.enabled" />
      </n-form-item>

      <n-form-item label="服务地址 baseUrl">
        <n-input v-model:value="form.baseUrl" clearable placeholder="OpenAI 兼容地址，如 https://api.deepseek.com" />
      </n-form-item>

      <n-form-item label="API Key">
        <n-input
          v-model:value="form.apiKey"
          type="password"
          show-password-on="click"
          clearable
          :placeholder="form.apiKeySet ? `已配置（${form.apiKeyMasked}），留空表示不修改` : '请输入 API Key'"
        />
      </n-form-item>

      <n-form-item label="对话模型">
        <n-input v-model:value="form.chatModel" clearable placeholder="如 deepseek-chat / qwen-plus / gpt-4o-mini" />
      </n-form-item>

      <n-form-item label="回答随机性">
        <n-input
          v-model:value="form.temperature"
          clearable
          placeholder="温度 0~2：越小越严谨稳定、越大越发散有创意；建议 0.7，留空用模型默认"
        />
      </n-form-item>

      <n-form-item label="最大输出 token">
        <n-input v-model:value="form.maxTokens" clearable placeholder="留空走服务端默认" />
      </n-form-item>

      <n-form-item label="嵌入模型">
        <n-input
          v-model:value="form.embeddingModel"
          clearable
          placeholder="可选，用于向量检索，如 text-embedding-v3（DeepSeek 暂无嵌入）"
        />
      </n-form-item>
      <template #form-action>
        <n-button :loading="testing" @click="onTest">
          <template #icon>
            <IconMdiFlashOutline />
          </template>
          测试连接
        </n-button>
      </template>
    </h-form>

    <n-alert
      v-if="testResult"
      :type="testResult.success ? 'success' : 'error'"
      :title="
        testResult.success
          ? `连接成功（模型 ${testResult.model || '-'} · ${testResult.latencyMs ?? '-'}ms）`
          : '连接失败'
      "
      style="max-width: 720px; margin-top: 4px; white-space: pre-wrap"
    >
      {{ testResult.message }}
    </n-alert>
  </n-card>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';
import { getConfig, testConn, saveConfig } from '../api/ai';

const form = reactive({
  enabled: false,
  baseUrl: '',
  apiKey: '',
  apiKeySet: false,
  apiKeyMasked: '',
  chatModel: '',
  temperature: '',
  maxTokens: '',
  embeddingModel: '',
});

const saving = ref(false);
const testing = ref(false);
const testResult = ref<any>(null);

async function load() {
  const res: any = await getConfig();
  const c = res.data || {};
  form.enabled = !!c.enabled;
  form.baseUrl = c.baseUrl ?? '';
  form.apiKeySet = !!c.apiKeySet;
  form.apiKeyMasked = c.apiKeyMasked ?? '';
  form.apiKey = '';
  form.chatModel = c.chatModel ?? '';
  form.temperature = c.temperature != null ? String(c.temperature) : '';
  form.maxTokens = c.maxTokens != null ? String(c.maxTokens) : '';
  form.embeddingModel = c.embeddingModel ?? '';
}

async function onSave() {
  saving.value = true;
  try {
    // apiKey 留空 → 后端不修改已存的 key（脱敏回显不会覆盖真实值）
    await saveConfig({
      enabled: form.enabled,
      baseUrl: form.baseUrl?.trim() ?? '',
      apiKey: form.apiKey?.trim() ?? '',
      chatModel: form.chatModel?.trim() ?? '',
      temperature: form.temperature?.trim() ?? '',
      maxTokens: form.maxTokens?.trim() ?? '',
      embeddingModel: form.embeddingModel?.trim() ?? '',
    });
    // 保存成功的提示由响应拦截器统一弹出
    await load();
  } finally {
    saving.value = false;
  }
}

async function onTest() {
  testing.value = true;
  testResult.value = null;
  try {
    const res: any = await testConn({
      baseUrl: form.baseUrl?.trim() || undefined,
      apiKey: form.apiKey?.trim() || undefined,
      chatModel: form.chatModel?.trim() || undefined,
    });
    testResult.value = res.data;
  } finally {
    testing.value = false;
  }
}

onMounted(load);
</script>
