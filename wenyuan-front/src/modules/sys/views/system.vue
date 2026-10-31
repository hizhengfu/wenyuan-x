<template>
  <n-card style="padding: 15px" title="系统参数配置" size="small">
    <h-form
      :enableTeleport="false"
      label-placement="left"
      label-width="200px"
      :rules="rules"
      :model="form"
      :handleSubmit="handleSubmit"
    >
      <n-form-item
        label="IP白名单"
        path="IpWhiteList"
        feedback="格式如demo:127.0.0.1/32，多个用,分割，demo表示白名单用户"
      >
        <n-input
          v-model:value="form.IpWhiteList"
          type="textarea"
          placeholder="请输入需要放行的白名单"
          rows="3"
        ></n-input>
      </n-form-item>
      <n-form-item label="隐私条款" path="PrivacyPolicy">
        <n-input v-model:value="form.PrivacyPolicy" type="textarea" placeholder="请输入隐私条款内容" rows="5"></n-input>
      </n-form-item>
      <n-form-item label="双因子认证" path="EnableMfa">
        <n-switch v-model:value="form.EnableMfa"></n-switch>
      </n-form-item>
      <n-form-item v-if="form.EnableMfa" label="双因子认证类型" path="LoginMfaType">
        {{ form.LoginMfaType }}
        <h-dict-checkbox
          v-model:value="form.LoginMfaType"
          type="sys_verification_type"
          placeholder="请选择认证类型"
          clearable
        ></h-dict-checkbox>
      </n-form-item>
      <n-form-item label="短信签名" path="SmsSign">
        <n-input v-model:value="form.SmsSign" placeholder="请输入短信签名"></n-input>
      </n-form-item>
      <n-form-item
        label="短信验证码模版"
        path="SmsVerificationTemplateCode"
        feedback="短信模版需要咨询服务商，变量需要包括code字符串，建议模版为:验证码:{{code}},5分钟有效内有效，请妥善保管！"
      >
        <n-input v-model:value="form.SmsVerificationTemplateCode" placeholder="请输入短信验证码模版"></n-input>
      </n-form-item>
    </h-form>
  </n-card>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { save } from '../api/config';

/**
 * 表单默认值
 * */
const defaultForm = {
  IpWhiteList: '',
  EnableMfa: false,
  LoginMfaType: null,
  PrivacyPolicy: '',
  SmsSign: '',
  SmsVerificationTemplateCode: '',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  EnableMfa: {
    required: true,
    message: '请选择是否启用双因子认证',
  },
  LoginMfaType: {
    required: true,
    message: '双因子认证方式不能为空',
  },
  PrivacyPolicy: {
    required: true,
    message: '隐私条款不能为空',
  },
  SmsSign: {
    required: true,
    message: '请配置短信签名',
  },
  SmsVerificationTemplateCode: {
    required: true,
    message: '请配置短信验证码模版',
  },
};

/**
 * 提交
 * */
function handleSubmit(data: any) {
  return save(data);
}
</script>
