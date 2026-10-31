<template>
  <t-input :placeholder="placeholder" v-model:value="value" v-bind="$attrs">
    <template #prefix-icon>
      <IconMdiShieldCheck class="captcha-icon"></IconMdiShieldCheck>
    </template>
    <template #suffix>
      <img :src="captchaUrl" alt="点击刷新验证码" title="点击刷新验证码" @click="handleRefresh" @load="handleLoading" />
    </template>
  </t-input>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { uuid } from '@/utils';

const props = defineProps({
  value: { type: String },
  placeholder: {
    type: String,
    default: '请输入验证码',
  },
  form: {
    //表单对象
    type: Object,
  },
});
/**
 * 传递值逻辑
 * */
const emit = defineEmits(['update:value']);
const value = computed({
  get() {
    return props.value;
  },
  set(value) {
    emit('update:value', value);
    if (props.form) {
      // eslint-disable-next-line vue/no-mutating-props
      props.form['uuid'] = captchaUuid.value;
    }
  },
});

/**
 * 刷新验证码逻辑
 * */
let refreshCount = ref(1);

/**
 * 验证码加载状态
 * */
const loading = ref(true);

/**
 * 刷新验证码
 * */
function handleRefresh() {
  loading.value = true;
  captchaUuid.value = uuid();
  refreshCount.value++;
}

/**
 * 验证码uuid
 * */
const captchaUuid = ref(uuid());

/**
 * 验证码路径
 * */
const captchaUrl = computed(() => {
  return `${APP_BASE_URL}${APP_API_PATH}/auth/captcha?uuid=${captchaUuid.value}&t=${refreshCount.value}`;
});

/**
 * 处理验证码加载状态
 * */
function handleLoading() {
  if (loading.value) return;
  loading.value = false;
}

defineExpose({ handleRefresh });
</script>

<style scoped>
.captcha-icon {
  color: #54707a;
  font-size: 20px;
}

img {
  display: block;
  width: 92px;
  height: 34px;
  border-radius: 6px;
  cursor: pointer;
  object-fit: cover;
}
</style>
