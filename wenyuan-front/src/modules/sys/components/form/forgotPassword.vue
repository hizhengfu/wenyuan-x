<template>
  <div class="common-password-reset w-500px <md:(w-auto) p-10 <md:(w-full p-5)">
    <n-card title="找回密码">
      <h-form
        label-placement="top"
        :enableTeleport="false"
        :enableCancel="false"
        :submitText="submitText"
        :model="form"
        :rules="formRules"
        :handleSubmit="handleSubmit"
      >
        <template v-if="step === 0">
          <p>验证码将会发送至你账号设置的邮箱或手机</p>
          <n-form-item label="手机号或邮箱" path="value">
            <n-input v-model:value="form.value" placeholder="请输入手机号或邮箱"></n-input>
          </n-form-item>
          <n-form-item label="验证码" path="code">
            <h-field-captcha ref="refFieldCaptcha" v-model:value="form.code" :form="form"></h-field-captcha>
          </n-form-item>
        </template>
        <template v-else>
          <p>请检查您的{{ form.value }}中的信息并填写验证码</p>
          <n-form-item label="验证码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入你手机或邮箱收到的验证码"></n-input>
          </n-form-item>
          <n-form-item label="新密码" path="password">
            <n-input
              type="password"
              v-model:value="form.password"
              :input-props="{ autocomplete: 'new-password' }"
              show-password-on="mousedown"
              placeholder="请输入新密码"
            ></n-input>
          </n-form-item>
          <n-form-item label="确认密码" path="confirmPassword">
            <n-input
              type="password"
              v-model:value="form.confirmPassword"
              :disabled="!form.password"
              :input-props="{ autocomplete: 'new-password' }"
              show-password-on="mousedown"
              placeholder="请再次输入新密码"
            ></n-input>
          </n-form-item>
        </template>
        <template #form-action>
          <RouterLink class="mr-1" to="/login">返回登录</RouterLink>
        </template>
      </h-form>
    </n-card>
  </div>
</template>
<script setup lang="ts">
import HFieldCaptcha from '@/modules/sys/components/form/field/captcha/index.vue';
import { computed, ref } from 'vue';
import { forgotPassword, resetPassword } from '../../api/auth.ts';
import { FormItemRule } from 'naive-ui';
import { pick } from 'lodash-es';
import { globalRouter } from '@/router';

const step = ref(0);
const submitText = computed(() => {
  if (step.value === 0) {
    return '获取验证码';
  } else {
    return '重设密码';
  }
});

const form = ref({
  value: '',
  uuid: '',
  code: '',
  password: '',
  confirmPassword: '',
});

/**
 * 表单验证规则
 * */
const formRules = {
  code: {
    required: true,
    message: '验证码不能为空',
  },
  value: {
    required: true,
    message: '手机或邮箱不能为空',
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
        if (!/^(?=.*[A-Z])(?=.*[a-z])(?=.*\d).{8,}$/.test(value)) {
          return new Error('输入的密码太简单，请输入超过8位的数字、大小写字符、特殊字符等组合。');
        }
        return true;
      },
    },
  ],
  confirmPassword: [
    {
      required: true,
      message: '请再次输入新密码',
    },
    {
      validator(rule: FormItemRule, value: string) {
        return value === form.value.password;
      },
      message: '两次密码输入不一致',
      trigger: ['blur', 'password-input'],
    },
  ],
};

function handleSubmit() {
  if (step.value === 0) {
    return handleSendCode();
  } else {
    return handleResetPassword();
  }
}

/**
 * 发送验证信息
 * */
function handleSendCode() {
  return forgotPassword(pick(form.value, ['value', 'uuid', 'code'])).then((resp: any) => {
    step.value = 1;
    form.value.code = '';
  });
}

/**
 * 重设密码
 * */
function handleResetPassword() {
  return resetPassword(pick(form.value, ['value', 'password', 'confirmPassword', 'code'])).then(() => {
    globalRouter.replace('/login');
  });
}
</script>

<style lang="scss" scoped>
.common-password-reset:deep(.n-form-item-label__text) {
  text-align: justify;
  text-align-last: justify;
}
</style>
