<template>
  <h-dialog-form
    :rules="rules"
    :model="form"
    size="small"
    width="500px"
    height="250px"
    label-width="auto"
    title="修改密码"
    :handleSubmit="handleSubmit"
    v-bind="$attrs"
  >
    <n-form-item label="当前密码" path="password">
      <n-input
        type="password"
        v-model:value="form.password"
        :input-props="{ autocomplete: 'current-password' }"
        show-password-on="mousedown"
        placeholder="请输入当前密码"
      ></n-input>
    </n-form-item>
    <n-form-item label="新密码" path="newPassword">
      <n-input
        type="password"
        v-model:value="form.newPassword"
        :input-props="{ autocomplete: 'new-password' }"
        show-password-on="mousedown"
        placeholder="请输入新密码"
      ></n-input>
    </n-form-item>
    <n-form-item label="确认密码" path="password_confirmation">
      <n-input
        type="password"
        v-model:value="form.password_confirmation"
        :disabled="!form.newPassword"
        :input-props="{ autocomplete: 'new-password' }"
        show-password-on="mousedown"
        placeholder="请再次输入新密码"
      ></n-input>
    </n-form-item>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { updatePass } from '../../api/user';
import { FormItemRule } from 'naive-ui';
import { useAuthStore } from '@/store';

/**
 * 表单默认值
 * */
const defaultForm = {
  password: '',
  newPassword: '',
  password_confirmation: '',
};
/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  password: {
    required: true,
    message: '请输入当前密码',
  },
  newPassword: {
    required: true,
    message: '请输入新密码',
  },
  password_confirmation: [
    {
      required: true,
      message: '请再次输入新密码',
    },
    {
      validator(rule: FormItemRule, value: string) {
        return value === form.value.newPassword;
      },
      message: '两次密码输入不一致',
      trigger: ['blur', 'newPassword-input'],
    },
  ],
};

const authStore = useAuthStore();
function handleSubmit() {
  return updatePass(form.value).then(() => {
    authStore.logout();
  });
}
</script>
