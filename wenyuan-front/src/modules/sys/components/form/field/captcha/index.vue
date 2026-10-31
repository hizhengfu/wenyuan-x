<template>
  <n-input-group class="captcha-group">
    <n-input :placeholder="placeholder" v-model:value="value" v-bind="$attrs" />
    <img
      :src="captchaUrl"
      class="captcha-image"
      alt="点击刷新验证码"
      title="点击刷新验证码"
      @click="handleRefresh"
      @load="handleLoad"
      @error="handleError"
    />
  </n-input-group>
</template>
<script lang="ts" setup>
import { computed, ref } from 'vue';
import { uuid } from '@/utils';
import { useTimeoutFn } from '@vueuse/core';

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
  return `${APP_BASE_URL}${APP_API_PATH}/auth/captcha?uuid=${captchaUuid.value}&t=${refreshCount.value}&w=240&h=80`;
});

/**
 * 加载完成
 * */
function handleLoad() {
  loading.value = false;
  //当验证码刷新100次后就不再刷新
  if (refreshCount.value < 100) {
    useTimeoutFn(() => {
      handleRefresh();
    }, 290000);
  }
}

/**
 * 加载出错，出错的情况下间隔5秒刷新10次就不再刷新
 * */
function handleError() {
  if (refreshCount.value < 10) {
    useTimeoutFn(() => {
      handleRefresh();
    }, 5000);
  }
}

defineExpose({ handleRefresh });
</script>

<style scoped>
.captcha-image {
  width: 120px;
  max-width: 60%;
  height: auto;
  object-fit: contain;
  cursor: pointer;
}

@media (max-width: 600px) {
  .captcha-group {
    flex-direction: column;
  }

  .captcha-image {
    max-width: 100%;
  }
}
</style>
