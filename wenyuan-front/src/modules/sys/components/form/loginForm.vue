<template>
  <n-card v-if="oauth2Login" class="common-login-form w-500px <md:w-full" :title="title">
    <n-button type="primary" block @click="authStore.beginOAuth2Login()">统一身份登录</n-button>
  </n-card>
  <n-form v-else
    size="large"
    class="common-login-form w-500px <md:(w-auto) p-10 <md:(w-full p-5)"
    ref="formRef"
    autocomplete="off"
    :label-placement="labelPlacement"
    label-width="80px"
    :disabled="loading"
    :model="loginForm"
    :rules="formRules"
  >
    <n-card v-if="loginStep === 2" title="二次认证" header-style="text-align: center">
      <template #header-extra>
        <n-button type="info" text @click="handleReturn"> 返回登录</n-button>
      </template>
      <n-tabs v-model:value="loginForm.mfaValidType" @update-value="handleChangeMfaType" type="segment" animated>
        <n-tab-pane v-if="mfaInfo.phone" name="SMS" tab="短信验证">
          <n-button type="info" text @click="handleSend" :disabled="mfaCodeSending">
            <span v-if="mfaCodeSending">
              <n-countdown
                ref="countdown"
                :duration="300000"
                @finish="
                  () => {
                    mfaCodeSending = false;
                  }
                "
              />
              秒后可以重新发送
            </span>
            <span v-else>请点击发送</span>
          </n-button>
          向您的手机尾号{{ mfaInfo.phone }}发送验证信息
        </n-tab-pane>
        <n-tab-pane v-if="mfaInfo.email" name="EMAIL" tab="邮箱验证">
          <n-button type="info" text @click="handleSend" :disabled="mfaCodeSending">
            <span v-if="mfaCodeSending">
              <n-countdown
                ref="countdown"
                :duration="300000"
                @finish="
                  () => {
                    mfaCodeSending = false;
                  }
                "
              />
              秒后可以重新发送
            </span>
            <span v-else>请点击发送</span>
          </n-button>
          向您的邮箱{{ mfaInfo.email }}发送验证信息
        </n-tab-pane>
      </n-tabs>
      <n-form-item label="二次认证" :show-label="false" :show-feedback="false" path="mfaCode">
        <n-input-otp block v-model:value="loginForm.mfaCode" />
      </n-form-item>
      <p>{{ loginForm.message || '为了保护您的账户安全，请完成身份验证。' }}</p>
      <template #action>
        <div class="text-right">
          <n-button type="primary" :disabled="loading" size="large" icon-placement="right" block @click="handleSubmit">
            <template #icon>
              <n-icon>
                <IconMdiShieldCheckOutline></IconMdiShieldCheckOutline>
              </n-icon>
            </template>
            验证并登录
          </n-button>
        </div>
      </template>
    </n-card>
    <n-card v-else v-bind="$attrs" header-style="text-align: center" :segmented="{ content: 'soft', footer: 'soft' }">
      <template v-if="showHeader" #header>
        {{ title }}
      </template>
      <n-form-item label="用户名" path="username">
        <n-input
          v-model:value="loginForm.username"
          :input-props="{ autocomplete: 'current-username' }"
          placeholder="请输入用户名"
          clearable
        />
      </n-form-item>
      <n-form-item label="密码" path="password">
        <n-input
          type="password"
          show-password-on="mousedown"
          :input-props="{ autocomplete: 'current-password' }"
          v-model:value="loginForm.password"
          placeholder="请输入密码"
          clearable
        />
      </n-form-item>
      <n-form-item
        label="验证码"
        path="code"
        v-if="
          !configRef.captchaMode || loginForm.captchaRequired === true || configRef.captchaMode.content === 'ALWAYS'
        "
      >
        <h-field-captcha ref="refFieldCaptcha" v-model:value="loginForm.code" :form="loginForm"></h-field-captcha>
      </n-form-item>

      <div class="flex justify-between" style="flex: 1">
        <!--        <n-form-item :show-label="false" :show-feedback="false" label="记住登录" path="rememberMe">-->
        <!--          <span class="mr-1">记住登录</span>-->
        <!--          <n-switch v-model:value="loginForm.rememberMe" />-->
        <!--        </n-form-item>-->
        <n-form-item
          v-if="configRef.privacyPolicy"
          :show-label="false"
          :show-feedback="false"
          label="隐私条款"
          path="agreePrivacyPolicy"
        >
          <n-checkbox :round="false" v-model:checked="loginForm.agreePrivacyPolicy">阅读并接受隐私条款</n-checkbox>
          <slot name="privacyPolicy">
            <n-button type="warning" size="tiny" text @click="showPrivacyPolicy = true" class="ml-1">查看</n-button>
            <n-modal
              title="隐私条款"
              v-model:show="showPrivacyPolicy"
              preset="card"
              size="small"
              :content-style="{ flex: 1, display: 'flex', overflow: 'auto' }"
              style="width: 800px; height: 600px"
            >
              <div v-html="configRef.privacyPolicy.content"></div>
            </n-modal>
          </slot>
        </n-form-item>
        <RouterLink class="ml-1 flex-center" to="/auth/forgotPassword">忘记密码</RouterLink>
        <slot name="register"></slot>
      </div>
      <template #action>
        <div class="text-right">
          <n-button type="primary" :disabled="loading" size="large" icon-placement="right" block @click="handleSubmit">
            <template #icon>
              <n-icon>
                <IconMdiShieldLockOpenOutline></IconMdiShieldLockOpenOutline>
              </n-icon>
            </template>
            登录
          </n-button>
        </div>
      </template>
    </n-card>
  </n-form>
</template>
<script setup lang="ts">
import { useAuthStore } from '@/store';
import { PropType, ref, onMounted } from 'vue';
import { isOAuth2Authentication } from '@/utils/auth';
import HFieldCaptcha from '../../components/form/field/captcha/index.vue';
import { FormInst, FormRules, FormItemRule } from 'naive-ui';
import { notification } from '@/utils/message';
import { config, sendMfaCode } from '../../api/auth.ts';
import { pick } from 'lodash-es';

const loading = ref(false);
const refFieldCaptcha = ref();

const props = defineProps({
  title: { type: String, default: '登录' },
  showHeader: { type: Boolean, default: true },
  labelPlacement: { type: String as PropType<'top' | 'left'>, default: 'top' },
});

const loginStep = ref(1);

/**
 * 表单验证规则
 * */
const formRules: FormRules = {
  username: {
    required: true,
    message: '用户名不能为空',
  },
  password: [
    {
      required: true,
      message: '密码不能为空',
    },
    {
      level: 'warning',
      trigger: ['input', 'blur'],
      validator(_rule: FormItemRule, value: string) {
        if (!/(?=.*[A-Z])(?=.*[a-z])/.test(value)) {
          return new Error('请注意，密码区分大小写');
        }
        return true;
      },
    },
  ],
  code: {
    required: true,
    message: '验证码不能为空',
  },
  mfaCode: {
    required: true,
    message: '二次认证码不能为空',
  },
  agreePrivacyPolicy: {
    required: true,
    trigger: 'change',
    validator(rule: FormItemRule, value: boolean) {
      if (!value) {
        return new Error('必须同意隐私条款');
      }
      return true;
    },
    message: '请阅读隐私条款，同意隐私条款是系统正常使用的基础，如不同意将不能继续使用系统。',
  },
};
/**
 * 表单
 * */
const loginForm = ref({
  username: '',
  password: '',
  code: '',
  uuid: '',
  mfaValidType: 'SMS',
  mfaCode: null,
  mfaToken: '',
  message: '',
  rememberMe: true,
  captchaRequired: false,
  agreePrivacyPolicy: true,
});

const formRef = ref<(HTMLElement & FormInst) | null>(null);
const authStore = useAuthStore();

const configRef = ref({
  privacyPolicy: { content: '', hash: '' },
  captchaMode: { content: null },
});
/**
 * mfa提示信息
 * */
const mfaInfo = ref({ phone: '', email: '' });
const showPrivacyPolicy = ref(false);

function loadConfig() {
  config().then((resp: any) => {
    configRef.value = resp || {};
    if (resp && resp['enableCookie'] && resp['enableCookie']['content'] == true) {

    }
  });
}

const oauth2Login = isOAuth2Authentication();
if (!oauth2Login) loadConfig();
onMounted(() => { if (oauth2Login) authStore.beginOAuth2Login(); });

/**
 * 返回到登录入口
 * */
function handleReturn() {
  loginStep.value = 1;
  loginForm.value.code = '';
  loginForm.value.message = '';
  loginForm.value.mfaCode = null;
  loginForm.value.mfaToken = '';
}

const mfaCodeSending = ref(false);

function handleChangeMfaType() {
  mfaCodeSending.value = false;
  loginForm.value.message = '';
}

/**
 * 发送验证码
 * */
function handleSend() {
  mfaCodeSending.value = true;
  sendMfaCode(pick(loginForm.value, ['mfaValidType', 'mfaToken']))
    .then((resp: any) => {
      loginForm.value.message = resp;
      setTimeout(
        () => {
          mfaCodeSending.value = false;
        },
        5 * 60 * 1000,
      );
    })
    .catch(() => {
      mfaCodeSending.value = false;
    });
}

/**
 * 更新验证码
 * */
function handleRefreshCaptcha() {
  loginForm.value.code = '';
  refFieldCaptcha.value?.handleRefresh();
}

/**
 * 提交
 * */
function handleSubmit(e: MouseEvent) {
  e.preventDefault();
  formRef.value?.validate((errors) => {
    if (!errors) {
      loading.value = true;
      authStore
        .login(loginForm.value)
        .then((resp: any) => {
          //需要显示验证码
          loginForm.value.captchaRequired = resp.captchaRequired;
          if (resp.captchaRequired) {
            handleRefreshCaptcha();
          }
          //未完成全部登录时继续操作
          if (resp.step === 2) {
            loginStep.value = resp.step;
            loginForm.value.mfaToken = resp.token;
            loginForm.value.message = resp.message;

            mfaInfo.value.phone = resp.phone;
            mfaInfo.value.email = resp.email;

            if (resp.phone) {
              loginForm.value.mfaValidType = 'SMS';
            } else if (resp.email) {
              loginForm.value.mfaValidType = 'EMAIL';
            }
          }
          // loading.value = false
        })
        .catch(() => {
          //登录异常情况下刷新验证码
          handleRefreshCaptcha();
        })
        .finally(() => {
          loading.value = false;
        });
    } else {
      let content = errors
        .map((item) => {
          return item[0]['message'];
        })
        .join(',');
      notification({
        type: 'error',
        title: '请注意',
        content: content,
      });
    }
  });
}
</script>
<style lang="scss" scoped>
.common-login-form :deep(.n-form-item-label__text) {
  text-align: justify;
  text-align-last: justify;
}
</style>
