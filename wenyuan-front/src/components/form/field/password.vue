<template>
  <n-input-group>
    <n-input
      v-model:value="value"
      type="password"
      show-password-on="click"
      :input-props="{ autocomplete: 'new-password' }"
      v-bind="$attrs"
      :placeholder="placeholder"
    ></n-input>
    <n-popover trigger="hover">
      <template #trigger>
        <n-button @click="handleGeneratePassword">随机密码</n-button>
      </template>
      <span>点击后将刷新密码并自动复制密码剪贴板</span>
    </n-popover>
  </n-input-group>
</template>
<script lang="ts" setup>
import { generatePassword } from '@/utils';
import { useClipboard } from '@vueuse/core';

const props = defineProps({
  placeholder: { type: String, default: '请输入确认密码' },
  len: { type: Number, default: 10 },
});

const value = defineModel<string | null>('value');

const { copy } = useClipboard();
function handleGeneratePassword() {
  let password = generatePassword(props.len);
  value.value = password;
  copy(password);
}
</script>
