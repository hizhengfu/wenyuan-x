<template>
  <main class="kiosk-shell">
    <header class="client-header">
      <div class="brand-mark">RF</div>
      <div class="header-title">
        <div class="brand-title">{{ headerTitle }}</div>
        <div v-if="binding" class="brand-subtitle">{{ binding.workstationCode }}</div>
      </div>
      <div class="header-spacer" />
      <NTag v-if="binding" :type="binding.enabled ? 'success' : 'warning'" round>
        {{ binding.enabled ? '本机已绑定' : '工作站停用' }}
      </NTag>
      <NTag v-else type="warning" round>等待部署</NTag>
    </header>

    <section v-if="initializing" class="loading-panel">
      <NSpin size="large" />
      <span>正在验证本机工作站绑定…</span>
    </section>

    <section v-else class="login-layout">
      <div class="intro-panel">
        <div class="intro-kicker"><span class="kicker-dot"></span>藏品管理 · 现场工作站</div>
        <h1>借还作业<br /><span>从这里开始</span></h1>
        <p class="intro-copy">
          {{ enrolled ? '刷人员卡快速进入，或使用有权限的账号密码登录。' : '首次使用时，请先将这台电脑绑定到指定工作站。' }}
        </p>
        <div class="workflow-steps" aria-label="借还台作业步骤">
          <div class="workflow-step"><span>01</span><strong>验证身份</strong></div>
          <div class="workflow-step"><span>02</span><strong>读取标签</strong></div>
          <div class="workflow-step"><span>03</span><strong>确认交接</strong></div>
        </div>
      </div>
      <NCard class="login-card" :bordered="false">
        <template #header>
          <div class="card-heading">
            <span class="card-icon">{{ enrolled ? 'RF' : '01' }}</span>
            <div>
              <span class="card-eyebrow">{{ enrolled ? '现场工作站' : '首次配置' }}</span>
              <h2>{{ enrolled ? '操作员登录' : '绑定这台电脑' }}</h2>
              <p>{{ enrolled ? '请选择适合您的登录方式' : '使用管理端生成的一次性注册令牌' }}</p>
            </div>
          </div>
        </template>

        <NAlert v-if="bindingMismatch" type="error" show-icon class="form-alert">
          本机已绑定“{{ binding?.workstationName }}（{{
            binding?.workstationCode
          }}）”，与当前快捷方式参数不一致。请使用正确的桌面快捷方式，或由管理员重新绑定。
        </NAlert>

        <template v-if="enrolled">
          <NAlert v-if="!binding?.enabled" type="warning" show-icon class="form-alert">
            本机绑定已完成，但工作站尚未启用。请由管理员完成外设配置并启用工作站后再登录。
          </NAlert>
          <NForm label-placement="top" size="large">
            <NFormItem label="人员卡">
              <NInput
                ref="cardInputRef"
                v-model:value="form.cardUid"
                type="password"
                autocomplete="off"
                autofocus
                :disabled="bindingMismatch || !binding?.enabled"
                placeholder="请刷人员卡"
                @keyup.enter="loginByCard"
              />
            </NFormItem>
          </NForm>
          <NSpace vertical :size="14">
            <NButton
              type="primary"
              color="#0d748d"
              class="primary-action"
              block
              size="large"
              :disabled="bindingMismatch || !binding?.enabled"
              :loading="loading"
              @click="loginByCard"
            >
              刷卡进入工作台
            </NButton>
            <NButton block size="large" class="password-action" :disabled="bindingMismatch || !binding?.enabled" @click="passwordLogin">
              账号密码进入工作台
            </NButton>
            <NButton v-if="setupMode" text block @click="beginRebind">管理员重新绑定本机</NButton>
          </NSpace>
          <p class="session-note">无活动作业且 15 分钟未操作时，工作台会自动退出。</p>
        </template>

        <template v-else>
          <NAlert type="info" :bordered="false" class="form-alert">
            请使用管理端生成的一次性注册令牌。注册成功后，此浏览器只能进入绑定的工作站。
          </NAlert>
          <NForm label-placement="top" size="large">
            <NFormItem label="工作站编码">
              <NInput
                v-model:value="form.stationCode"
                :readonly="Boolean(requestedStationCode)"
                placeholder="由管理端生成，例如 RFID-WS-..."
              />
            </NFormItem>
            <NFormItem label="一次性注册令牌">
              <NInput
                v-model:value="form.enrollmentToken"
                type="password"
                show-password-on="click"
                placeholder="粘贴管理端生成的注册令牌"
                @keyup.enter="enroll"
              />
            </NFormItem>
          </NForm>
          <NButton type="primary" color="#0d748d" class="primary-action" block size="large" :loading="loading" @click="enroll">绑定本机并继续</NButton>
          <NButton v-if="rebindMode" text block class="cancel-rebind" @click="cancelRebind">取消重新绑定</NButton>
        </template>
      </NCard>
    </section>
  </main>
</template>

<script lang="ts">
import { computed, defineComponent, nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAlert, NButton, NCard, NForm, NFormItem, NInput, NSpace, NSpin, NTag, useMessage } from 'naive-ui';
import { rfidApi } from '../api/rfid';
import type { RfidWorkstationContext } from '../typings/rfid';
import { ensureCanonicalRfidWorkstationHost } from '../utils/rfidWorkstationUrl';
import { beginSessionAuthentication, setSessionToken, setTokenExpAt } from '@/utils/auth';
import { useAuthStore } from '@/store';

const CODE_KEY = 'collection-rfid-kiosk-code';
const KIOSK_SESSION_KEY = 'collection-rfid-kiosk-session';

export default defineComponent({
  components: { NAlert, NButton, NCard, NForm, NFormItem, NInput, NSpace, NSpin, NTag },
  setup() {
    const route = useRoute();
    const router = useRouter();
    const authStore = useAuthStore();
    const message = useMessage();
    const requestedStationCode = String(route.query.stationCode || '')
      .trim()
      .toUpperCase();
    const savedCode = localStorage.getItem(CODE_KEY) || '';
    const form = reactive({ stationCode: requestedStationCode || savedCode, enrollmentToken: '', cardUid: '' });
    const binding = ref<RfidWorkstationContext | null>(null);
    const loading = ref(false);
    const initializing = ref(true);
    const rebindMode = ref(false);
    const cardInputRef = ref<any>(null);
    const setupMode = computed(() => route.query.setup === '1');
    const enrolled = computed(() => Boolean(binding.value) && !rebindMode.value);
    const headerTitle = computed(() => `${APP_TITLE} - ${binding.value?.workstationName || 'RFID 工作台'}`);
    const bindingMismatch = computed(
      () =>
        Boolean(requestedStationCode) &&
        Boolean(binding.value) &&
        binding.value?.workstationCode !== requestedStationCode,
    );

    async function loadBinding() {
      const response: any = await rfidApi.workstationContext();
      binding.value = response?.data || null;
      if (binding.value) {
        form.stationCode = binding.value.workstationCode;
        localStorage.setItem(CODE_KEY, binding.value.workstationCode);
      }
    }

    async function enroll() {
      if (!form.stationCode || !form.enrollmentToken) return message.warning('请输入工作站编码和注册令牌');
      loading.value = true;
      try {
        await rfidApi.enroll({ stationCode: form.stationCode, enrollmentToken: form.enrollmentToken });
        form.enrollmentToken = '';
        rebindMode.value = false;
        await loadBinding();
        if (!binding.value) throw new Error('工作站绑定凭证未生效，请检查浏览器 Cookie 设置');
        message.success(
          binding.value.enabled
            ? `本机已绑定 ${binding.value.workstationName}`
            : `本机已绑定 ${binding.value.workstationName}，启用后可登录`,
        );
        void nextTick(() => cardInputRef.value?.focus?.());
      } finally {
        loading.value = false;
      }
    }

    async function loginByCard() {
      if (bindingMismatch.value) return message.warning('当前快捷方式与本机绑定不一致');
      if (!binding.value || !form.cardUid) return message.warning('请刷人员卡');
      if (!binding.value.enabled) return message.warning('工作站尚未启用');
      loading.value = true;
      try {
        const resp: any = await rfidApi.cardLogin({
          stationCode: binding.value.workstationCode,
          cardUid: form.cardUid,
        });
        const result = resp?.data;
        beginSessionAuthentication('/rfid-kiosk');
        authStore.clearUserInfo();
        setSessionToken(result.token, '/rfid-kiosk');
        setTokenExpAt(Date.now() + result.expirationMillis);
        sessionStorage.setItem(KIOSK_SESSION_KEY, 'active');
        authStore.token = result.token;
        await authStore.fetchInfo();
        await authStore.fetchMenu();
        message.success(`欢迎 ${result.userName || '操作员'}`);
        await openWorkstation(result.workstationId);
      } finally {
        loading.value = false;
        form.cardUid = '';
        void nextTick(() => cardInputRef.value?.focus?.());
      }
    }

    function passwordLogin() {
      if (bindingMismatch.value || !binding.value) return message.warning('请先完成正确的工作站绑定');
      if (!binding.value.enabled) return message.warning('工作站尚未启用');
      const redirect = router.resolve({
        path: '/rfid-workstation',
        query: { workstationId: binding.value.workstationId, client: '1' },
      }).fullPath;
      beginSessionAuthentication('/rfid-kiosk');
      authStore.clearUserInfo();
      sessionStorage.setItem(KIOSK_SESSION_KEY, 'pending');
      void router.push({ path: '/login', query: { redirect } });
    }

    function openWorkstation(workstationId: string) {
      return router.replace({ path: '/rfid-workstation', query: { workstationId, client: '1' } });
    }

    function beginRebind() {
      rebindMode.value = true;
      form.stationCode = requestedStationCode || '';
      form.cardUid = '';
    }

    function cancelRebind() {
      rebindMode.value = false;
      form.stationCode = binding.value?.workstationCode || requestedStationCode || savedCode;
    }

    async function initializeBinding() {
      if (ensureCanonicalRfidWorkstationHost()) return;
      initializing.value = true;
      try {
        await loadBinding();
      } finally {
        initializing.value = false;
        if (binding.value && !bindingMismatch.value) void nextTick(() => cardInputRef.value?.focus?.());
      }
    }

    onMounted(initializeBinding);

    return {
      meta: { requiresAuth: false, title: 'RFID 工作台登录' },
      form,
      binding,
      headerTitle,
      enrolled,
      bindingMismatch,
      loading,
      initializing,
      requestedStationCode,
      setupMode,
      rebindMode,
      cardInputRef,
      enroll,
      loginByCard,
      passwordLogin,
      beginRebind,
      cancelRebind,
    };
  },
});
</script>

<style scoped>
.kiosk-shell {
  min-height: 100vh;
  color: #163447;
  background:
    radial-gradient(circle at 15% 54%, rgb(24 139 156 / 15%), transparent 32%),
    radial-gradient(circle at 94% 92%, rgb(32 105 142 / 10%), transparent 28%),
    linear-gradient(120deg, #eaf4f5 0%, #f5f8f8 48%, #eaf1f3 100%);
}
.client-header {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 13px;
  height: 80px;
  padding: 0 clamp(20px, 5vw, 72px);
  border-bottom: 1px solid rgb(37 72 91 / 8%);
  background: rgb(255 255 255 / 72%);
  backdrop-filter: blur(14px);
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 13px;
  color: white;
  font-weight: 800;
  letter-spacing: -1px;
  background: linear-gradient(145deg, #15869a, #0c566d);
  box-shadow: 0 8px 22px rgb(15 102 132 / 18%);
}
.brand-title {
  overflow: hidden;
  font-size: 17px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.brand-subtitle {
  overflow: hidden;
  margin-top: 2px;
  color: #78909c;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.header-title {
  min-width: 0;
  flex: 0 1 auto;
}
.header-spacer {
  flex: 1;
}
.loading-panel {
  box-sizing: border-box;
  min-height: calc(100vh - 80px);
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 18px;
  color: #607d8b;
}
.login-layout {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(380px, 480px);
  align-items: center;
  gap: clamp(48px, 7vw, 112px);
  width: min(1180px, calc(100% - 96px));
  min-height: calc(100vh - 80px);
  margin: 0 auto;
  padding: 64px 0;
}
.intro-panel {
  min-width: 0;
  padding-bottom: 8px;
}
.intro-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #0d748d;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.kicker-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #20a4a8;
  box-shadow: 0 0 0 5px rgb(32 164 168 / 12%);
}
.intro-panel h1 {
  margin: 25px 0 20px;
  color: #12384b;
  font-size: clamp(42px, 4.8vw, 72px);
  font-weight: 800;
  letter-spacing: -0.055em;
  line-height: 1.18;
}
.intro-panel h1 span {
  color: #0c788f;
}
.intro-copy {
  max-width: 440px;
  margin: 0;
  color: #5f7886;
  font-size: 16px;
  line-height: 1.9;
}
.workflow-steps {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-top: 72px;
}
.workflow-step {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #58717e;
  font-size: 13px;
  white-space: nowrap;
}
.workflow-step + .workflow-step::before {
  width: 22px;
  height: 1px;
  margin-right: 9px;
  background: #c6d8dc;
  content: '';
}
.workflow-step span {
  color: #0d8294;
  font-size: 12px;
  font-weight: 800;
}
.workflow-step strong {
  font-weight: 600;
}
.login-card {
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 24px;
  background: rgb(255 255 255 / 96%);
  box-shadow: 0 25px 68px rgb(32 78 91 / 11%);
}
.login-card :deep(.n-card-header) {
  padding: 35px 38px 16px;
}
.login-card :deep(.n-card-content) {
  padding: 14px 38px 38px;
}
.login-card :deep(.n-button) {
  min-height: 46px;
  border-radius: 9px;
}
.login-card :deep(.n-input) {
  border-radius: 9px;
}
.primary-action {
  font-weight: 700;
}
.password-action {
  color: #286179;
  font-weight: 600;
}
.card-heading {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.card-icon {
  display: grid;
  flex: 0 0 45px;
  place-items: center;
  height: 45px;
  border-radius: 13px;
  color: #0c7188;
  background: #e8f5f5;
  font-size: 15px;
  font-weight: 800;
}
.card-eyebrow {
  color: #2d8798;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.13em;
}
.card-heading h2 {
  margin: 5px 0 4px;
  color: #1b3748;
  font-size: 22px;
  font-weight: 750;
  line-height: 1.4;
}
.card-heading p {
  margin: 0;
  color: #7b909a;
  font-size: 12px;
  line-height: 1.7;
}
.session-note {
  margin: 17px 0 0;
  color: #81939d;
  font-size: 12px;
  line-height: 1.6;
  text-align: center;
}
.form-alert {
  margin-bottom: 20px;
}
.cancel-rebind {
  margin-top: 10px;
}
@media (max-width: 900px) {
  .login-layout {
    grid-template-columns: 1fr;
    width: min(560px, calc(100% - 40px));
    gap: 28px;
    padding: 56px 0 64px;
  }
  .intro-panel h1 {
    margin: 15px 0 10px;
    font-size: clamp(36px, 7vw, 50px);
  }
  .intro-copy {
    font-size: 14px;
  }
  .workflow-steps {
    display: none;
  }
  .client-header {
    padding: 0 18px;
  }
}
@media (max-width: 520px) {
  .login-layout {
    width: calc(100% - 28px);
    gap: 22px;
    padding: 38px 0 42px;
  }
  .intro-kicker {
    font-size: 11px;
  }
  .intro-panel h1 {
    font-size: 36px;
  }
  .login-card :deep(.n-card-header) {
    padding: 28px 24px 12px;
  }
  .login-card :deep(.n-card-content) {
    padding: 14px 24px 28px;
  }
  .brand-title {
    font-size: 14px;
  }
}
</style>
