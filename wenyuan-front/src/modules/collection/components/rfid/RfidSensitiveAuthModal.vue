<template>
  <NModal v-model:show="visible" preset="card" title="高风险操作二次认证" :mask-closable="false" @after-leave="finish(null)">
    <NForm label-placement="left" label-width="110px">
      <NFormItem label="当前密码">
        <NInput v-model:value="form.password" type="password" show-password-on="mousedown" autocomplete="current-password" />
      </NFormItem>
      <NFormItem label="图形验证码">
        <HFieldCaptcha ref="captchaRef" v-model:value="form.captchaCode" :form="form" />
      </NFormItem>
    </NForm>
    <NAlert type="warning" :bordered="false">授权仅绑定本次 RFID 操作，短时有效且使用一次后立即失效。</NAlert>
    <template #action>
      <NSpace justify="end">
        <NButton @click="cancel">取消</NButton>
        <NButton type="primary" :loading="loading" @click="submit">验证并继续</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { NAlert, NButton, NForm, NFormItem, NInput, NModal, NSpace, useMessage } from 'naive-ui';
import HFieldCaptcha from '../../../sys/components/form/field/captcha/index.vue';
import { rsaEncrypt } from '@/utils/rsaEncrypt';
import { rfidApi } from '../../api/rfid';

const message = useMessage();
const visible = ref(false);
const loading = ref(false);
const captchaRef = ref<any>();
const form = reactive({ password: '', captchaCode: '', captchaUuid: '', uuid: '' });
let pending: { action: string; digest: string; resolve: (token: string | null) => void } | null = null;

function authorize(action: string, digest: string): Promise<string | null> {
  finish(null);
  Object.assign(form, { password: '', captchaCode: '', captchaUuid: '', uuid: '' });
  visible.value = true;
  return new Promise((resolve) => {
    pending = { action, digest, resolve };
    window.setTimeout(() => captchaRef.value?.handleRefresh(), 0);
  });
}

async function submit() {
  if (!pending || !form.password || !form.captchaCode || !form.uuid) {
    message.warning('请输入当前密码和图形验证码');
    return;
  }
  const encrypted = rsaEncrypt(form.password);
  if (!encrypted) { message.error('密码加密失败'); return; }
  loading.value = true;
  try {
    const response: any = await rfidApi.verifySensitiveOperation({
      action: pending.action,
      resourceDigest: pending.digest,
      password: encrypted,
      captchaUuid: form.uuid,
      captchaCode: form.captchaCode,
    });
    const token = response?.data?.token as string | undefined;
    if (!token) throw new Error('二次认证未返回授权');
    const current = pending;
    pending = null;
    visible.value = false;
    current.resolve(token);
  } catch (error) {
    form.captchaCode = '';
    captchaRef.value?.handleRefresh();
    throw error;
  } finally { loading.value = false; }
}

function cancel() {
  visible.value = false;
  finish(null);
}

function finish(token: string | null) {
  const current = pending;
  pending = null;
  current?.resolve(token);
}

defineExpose({ authorize });
</script>
