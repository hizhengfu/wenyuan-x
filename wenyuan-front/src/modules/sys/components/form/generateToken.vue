<template>
  <h-dialog-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 title="修改邮箱"
                 :handleSubmit="generateToken"
                 v-bind="$attrs">
    <n-form-item label="用户名" path="username">
      <n-input v-model:value="form.username"
               placeholder="请输入用户名"></n-input>
    </n-form-item>
    <n-form-item label="有效期" path="endTime">
      <n-input v-model:value="form.endTime"
               placeholder="请选择有效期"></n-input>
    </n-form-item>
  </h-dialog-form>
</template>
<script lang="ts" setup>
import {ref} from "vue";
import {cloneDeep} from "lodash-es";
import {generateToken} from '../../api/user'

/**
 * 表单默认值
 * */
const defaultForm = {
  username: '',
  endTime: null,
}
/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
  username: {
    required: true,
    message: '用户名'
  },
  endTime: {
    required: true,
    message: '结束时间'
  },
}

function handleSubmit() {
  return generateToken(form.value)
}
</script>
