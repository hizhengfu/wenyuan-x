<template>
  <h-dialog-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 title="修改邮箱"
                 :handleSubmit="handleSubmit"
                 v-bind="$attrs">
    <n-form-item label="新邮箱" path="email">
      <n-input-group>
        <n-input v-model:value="form.email"
                 placeholder="请输入新邮箱"></n-input>
        <n-button type="primary" :disabled="!form.email" @click="fetchCode">
          发送验证码
        </n-button>
      </n-input-group>
    </n-form-item>
    <n-form-item label="验证码" path="code">
      <n-input v-model:value="form.code"
               placeholder="请输入验证码"></n-input>
    </n-form-item>
    <n-form-item label="当前密码" path="password">
      <n-input type="password" v-model:value="form.password" show-password-on="mousedown"
               placeholder="请输入当前密码"></n-input>
    </n-form-item>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import {ref} from "vue";
import {cloneDeep} from "lodash-es";
import {resetEmail, updateEmail} from '../../api/user'

/**
 * 表单默认值
 * */
const defaultForm = {
  password: '',
  code: '',
  email: '',
}
/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  email: {
    required: true,
    message: '请输入邮箱'
  },
  password: {
    required: true,
    message: '请输入当前密码'
  },
  code: {
    required: true,
    message: '请输入验证码'
  },
}

const loadingCode = ref(false)

/**
 * 获取验证码
 * */
function fetchCode() {
  loadingCode.value = true
  resetEmail({email: form.value.email}).then((res) => {
  }).finally(() => {
    loadingCode.value = false
  })
}

function handleSubmit() {
  return updateEmail(form.value)
}
</script>
