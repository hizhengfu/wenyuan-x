<template>
  <div class="login-container flex-col-center">
    <h3 v-if="showTitle" class="title">{{ title }}</h3>
    <HMobileForm
      class="login-form md:w-600px"
      label-align="top"
      :key="loginStep"
      :data="loginForm"
      :rules="formRules"
      :disabled="loading"
      :needConfirm="false"
      @submit="handleSubmit"
      label-width="120px"
    >
      <template v-if="loginStep === 2">
        <div class="mfa-header">
          <h3>二次认证</h3>
          <t-button
            class="mfa-return"
            variant="text"
            size="small"
            :disabled="loading || mfaSending"
            @click="handleReturn"
            >返回登录</t-button
          >
        </div>
        <div class="mfa-channels" v-if="mfaInfo.phone && mfaInfo.email">
          <t-button
            size="small"
            :theme="loginForm.mfaValidType === 'SMS' ? 'primary' : 'default'"
            :disabled="loading || mfaSending"
            @click="handleChangeMfaType('SMS')"
            >短信验证</t-button
          >
          <t-button
            size="small"
            :theme="loginForm.mfaValidType === 'EMAIL' ? 'primary' : 'default'"
            :disabled="loading || mfaSending"
            @click="handleChangeMfaType('EMAIL')"
            >邮箱验证</t-button
          >
        </div>
        <p class="mfa-recipient" v-if="loginForm.mfaValidType === 'SMS'">
          向您的手机尾号{{ mfaInfo.phone }}发送验证信息
        </p>
        <p class="mfa-recipient" v-else>向您的邮箱{{ mfaInfo.email }}发送验证信息</p>
        <t-button
          size="small"
          class="mfa-send"
          :loading="mfaSending"
          :disabled="loading || mfaSending || resendSeconds > 0"
          @click="handleSend"
          >{{ resendSeconds > 0 ? resendSeconds + ' 秒后重新发送' : '请点击发送' }}</t-button
        >
        <t-form-item class="mfa-code" label="认证码" name="mfaCode">
          <t-input
            v-model:value="loginForm.mfaCode"
            placeholder="请输入认证码"
            autocomplete="one-time-code"
            class="login-input"
            clearable
          >
            <template #prefix-icon>
              <IconMdiShieldCheck class="login-input__icon" />
            </template>
          </t-input>
        </t-form-item>
        <p class="mfa-message">{{ loginForm.message || '为了保护您的账户安全，请完成身份验证。' }}</p>
      </template>
      <template v-else>
        <t-form-item label="用户名" name="username">
          <t-input v-model:value="loginForm.username" class="login-input" placeholder="请输入用户名" clearable>
            <template #prefix-icon>
              <IconMdiAccount class="login-input__icon"></IconMdiAccount>
            </template>
          </t-input>
        </t-form-item>
        <t-form-item label="密码" name="password">
          <t-input
            v-model:value="loginForm.password"
            class="login-input"
            type="password"
            placeholder="请输入密码"
            clearable
          >
            <template #prefix-icon>
              <IconMdiKey class="login-input__icon"></IconMdiKey>
            </template>
          </t-input>
        </t-form-item>
        <t-form-item label="验证码" name="code">
          <HMobileCaptcha
            ref="refFieldCaptcha"
            v-model:value="loginForm.code"
            class="login-input"
            :form="loginForm"
            clearable
          ></HMobileCaptcha>
        </t-form-item>
      </template>
      <div class="login-btn my-6">
        <t-button
          theme="primary"
          type="submit"
          size="medium"
          block
          :loading="loading"
          :disabled="loading || mfaSending"
          >{{ loginStep === 2 ? '验证并登录' : '登录' }}</t-button
        >
      </div>
    </HMobileForm>
  </div>
</template>

<script lang="ts" setup>
import HMobileCaptcha from './field/captcha/mobile.vue';

import { useAuthStore } from '@/store';
import { computed, onBeforeUnmount, ref } from 'vue';
import { sendMfaCode } from '../../api/auth';

const loading = ref(false);
const refFieldCaptcha = ref();

const props = defineProps({
  title: { type: String, default: '登录' },
  showTitle: { type: Boolean, default: true },
});

/**
 * 表单验证规则
 * */
const loginStep = ref(1);
const formRules = computed(() =>
  loginStep.value === 2
    ? {
        mfaCode: [{ required: true, message: '二次认证码不能为空' }],
      }
    : {
        username: [
          {
            required: true,
            message: '用户名不能为空',
          },
        ],
        password: [
          {
            required: true,
            message: '密码不能为空',
          },
        ],
        code: [
          {
            required: true,
            message: '验证码不能为空',
          },
        ],
      },
);
/**
 * 表单
 * */
const loginForm = ref({
  username: '',
  password: '',
  code: '',
  uuid: '',
  rememberMe: false,
  mfaValidType: 'SMS',
  mfaCode: '',
  mfaToken: '',
  message: '',
});

const authStore = useAuthStore();
const mfaInfo = ref({ phone: '', email: '' });
const mfaSending = ref(false);
const resendSeconds = ref(0);
let disposed = false;
let resendTimer: ReturnType<typeof setInterval> | undefined;

function clearCountdown() {
  if (resendTimer !== undefined) clearInterval(resendTimer);
  resendTimer = undefined;
  resendSeconds.value = 0;
}

onBeforeUnmount(() => {
  disposed = true;
  clearCountdown();
});

function handleReturn() {
  clearCountdown();
  loginStep.value = 1;
  loginForm.value.code = '';
  loginForm.value.mfaCode = '';
  loginForm.value.mfaToken = '';
  loginForm.value.message = '';
  mfaInfo.value = { phone: '', email: '' };
}

function handleChangeMfaType(type: string) {
  if (loginForm.value.mfaValidType === type) return;
  clearCountdown();
  loginForm.value.mfaValidType = type;
  loginForm.value.mfaCode = '';
  loginForm.value.message = '';
}

async function handleSend() {
  if (loading.value || mfaSending.value || resendSeconds.value > 0 || !loginForm.value.mfaToken) return;
  mfaSending.value = true;
  try {
    const message = await sendMfaCode({
      mfaValidType: loginForm.value.mfaValidType,
      mfaToken: loginForm.value.mfaToken,
    });
    if (disposed) return;
    loginForm.value.message = typeof message === 'string' ? message : '';
    resendSeconds.value = 300;
    resendTimer = setInterval(() => {
      resendSeconds.value--;
      if (resendSeconds.value <= 0) clearCountdown();
    }, 1000);
  } catch {
    // 接口错误由统一请求层提示，允许重新发送。
    clearCountdown();
  } finally {
    mfaSending.value = false;
  }
}

function handleRefreshCaptcha() {
  loginForm.value.code = '';
  refFieldCaptcha.value?.handleRefresh();
}

async function handleSubmit() {
  if (loading.value || mfaSending.value) return;
  loading.value = true;
  try {
    const resp = await authStore.login({
      ...loginForm.value,
      // 与桌面 OTP 输入保持一致，由 authStore 拼接后提交。
      mfaCode: loginForm.value.mfaCode ? loginForm.value.mfaCode.trim().split('') : null,
    });
    if (resp.captchaRequired) {
      handleReturn();
      handleRefreshCaptcha();
    } else if (resp.step === 2) {
      if (loginForm.value.mfaToken !== resp.token) clearCountdown();
      loginStep.value = 2;
      loginForm.value.mfaToken = resp.token;
      loginForm.value.message = resp.message || '';
      loginForm.value.mfaCode = '';
      mfaInfo.value = { phone: resp.phone || '', email: resp.email || '' };
      loginForm.value.mfaValidType = resp.phone ? 'SMS' : 'EMAIL';
    }
  } catch {
    if (loginStep.value === 1) handleRefreshCaptcha();
  } finally {
    loading.value = false;
  }
}
</script>
<style lang="scss" scoped>
.mfa-header,
.mfa-channels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mfa-message {
  overflow-wrap: anywhere;
}

.login-container {
  .title {
    font-size: 30px;
    padding: 30px 0 0 0;
    text-align: center;
  }

  .login-form {
    border: 1px solid #e7e7e7;
    border-radius: 10px;

    .login-btn {
      // padding: 0 60px;
      display: flex;
      text-align: center;
    }
  }

  .login-input__icon {
    color: #54707a;
    font-size: 20px;
  }
}
</style>
