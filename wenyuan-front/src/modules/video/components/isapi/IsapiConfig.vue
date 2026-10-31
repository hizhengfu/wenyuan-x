<template>
  <n-card title="采集配置" size="small">
    <template #header-extra>
      <n-button size="small" :loading="loading" :disabled="saving" @click="load">重新读取</n-button>
    </template>
    <n-alert v-if="error" type="error" style="margin-bottom: 16px">{{ error }}</n-alert>
    <n-spin :show="loading">
      <n-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-placement="top"
        :disabled="!canEdit || saving || loading"
      >
        <n-grid :cols="24" :x-gap="20" responsive="screen" item-responsive>
          <n-form-item-gi span="24 m:12" label="最大并发采集设备数" path="maxConnections">
            <n-input-number v-model:value="form.maxConnections" :min="1" :max="64" style="width: 100%" />
          </n-form-item-gi>
          <n-form-item-gi span="24 m:12" label="原始采集留存天数" path="retentionDays">
            <n-input-number v-model:value="form.retentionDays" :min="0" :max="3650" style="width: 100%" />
            <template #feedback
              >0 表示不自动清理；待投递或待归档的数据继续保留，不影响 monitor 历史和相机归档文件。</template
            >
          </n-form-item-gi>
          <n-form-item-gi span="24" label="数据存储目录" path="storagePath">
            <n-input
              v-model:value="form.storagePath"
              :disabled="storagePathLocked || !canEdit || saving || loading"
              placeholder="服务器私有目录，例如 /opt/app-data/video-isapi"
            />
            <template #feedback>
              {{
                storagePathLocked
                  ? '已有设备凭证或采集文件，目录已锁定。迁移目录需由运维保留完整数据和密钥。'
                  : '请在添加设备前设置服务器上的私有目录，避免使用公开静态资源目录。凭证密钥由系统自动管理。'
              }}
            </template>
          </n-form-item-gi>
        </n-grid>
        <n-divider title-placement="left">ISUP 5.0 主动注册</n-divider>
        <n-p depth="3"
          >采集服务部署在 Linux x86_64。摄像机填写下方对外服务 IP、注册对外端口及设备的注册
          ID、密钥；报警和图片地址由服务下发。维护设备后自动启动监听。</n-p
        >
        <n-grid :cols="24" :x-gap="20" responsive="screen" item-responsive>
          <n-form-item-gi span="24" label="SDK 库目录" path="isup.sdkPath">
            <n-input v-model:value="form.isup.sdkPath" placeholder="例如 /opt/hik-isup/lib" />
            <template #feedback
              >目录需包含 CMS、Alarm、SS 动态库及 HCAapSDKCom、配套依赖库。更换已加载的 SDK
              库目录后需要重启服务。</template
            >
          </n-form-item-gi>
          <n-form-item-gi span="24 m:12" label="设备可访问的服务 IP" path="isup.publicAddress">
            <n-input v-model:value="form.isup.publicAddress" placeholder="服务器对外 IPv4 地址" />
          </n-form-item-gi>
          <n-form-item-gi span="24 m:12" label="服务器监听 IP" path="isup.listenAddress">
            <n-input v-model:value="form.isup.listenAddress" placeholder="0.0.0.0" />
          </n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="注册监听端口" path="isup.cmsPort"
            ><n-input-number v-model:value="form.isup.cmsPort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="报警监听端口" path="isup.alarmPort"
            ><n-input-number v-model:value="form.isup.alarmPort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="图片监听端口" path="isup.picturePort"
            ><n-input-number v-model:value="form.isup.picturePort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="注册对外端口" path="isup.publicCmsPort"
            ><n-input-number v-model:value="form.isup.publicCmsPort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="报警对外端口" path="isup.publicAlarmPort"
            ><n-input-number v-model:value="form.isup.publicAlarmPort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:8" label="图片对外端口" path="isup.publicPicturePort"
            ><n-input-number v-model:value="form.isup.publicPicturePort" :min="1024" :max="65535"
          /></n-form-item-gi>
          <n-form-item-gi span="24 m:12" label="设备报警上报协议" path="isup.alarmProtocol">
            <n-select
              v-model:value="form.isup.alarmProtocol"
              :options="[
                { label: 'MQTT（ISUP 5.0）', value: 'MQTT' },
                { label: 'TCP', value: 'TCP' },
              ]"
            />
          </n-form-item-gi>
        </n-grid>
        <n-text depth="3"
          >有端口映射时，对外端口填写映射端口；未映射时与监听端口一致。三类服务分别使用独立端口。</n-text
        >
      </n-form>
      <n-space align="center" justify="space-between">
        <n-text depth="3">维护设备后自动采集，移除设备后停止连接。配置保存后自动应用。</n-text>
        <n-button v-if="canEdit" type="primary" :loading="saving" :disabled="loading || !loaded" @click="save"
          >保存采集配置</n-button
        >
      </n-space>
    </n-spin>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref } from 'vue';
import { useMessage } from 'naive-ui';
import type { FormInst, FormRules } from 'naive-ui';
import { useAuthStore } from '@/store';
import { defaultIsupSettings, getSettings, saveSettings } from '../../api/isapi';
import type { IsapiSettings, IsapiSettingsView } from '../../api/isapi';

const emit = defineEmits<{ changed: [] }>();
const props = defineProps<{ hasDevices: boolean }>();
const auth = useAuthStore();
const message = useMessage();
const canEdit = computed(() => auth.hasPermission('VIDEO_ISAPI_ALL,VIDEO_ISAPI_EDIT'));
const formRef = ref<FormInst>();
const form = ref<IsapiSettings>({
  maxConnections: 16,
  retentionDays: 0,
  storagePath: './data/video-isapi',
  revision: 0,
  isup: defaultIsupSettings(),
});
const loading = ref(false);
const loaded = ref(false);
const saving = ref(false);
const storedPathLocked = ref(false);
const storagePathLocked = computed(() => storedPathLocked.value || props.hasDevices);
const error = ref('');
const rules: FormRules = {
  maxConnections: {
    validator: (_rule, value) =>
      (Number.isInteger(value) && value >= 1 && value <= 64) || new Error('请输入1至64之间的整数'),
  },
  retentionDays: {
    validator: (_rule, value) =>
      (Number.isInteger(value) && value >= 0 && value <= 3650) || new Error('请输入0至3650之间的整数'),
  },
  storagePath: { required: true, message: '请填写数据存储目录' },
};
function apply(view: IsapiSettingsView) {
  form.value = { ...view.settings, isup: { ...defaultIsupSettings(), ...view.settings.isup } };
  storedPathLocked.value = view.storagePathLocked;
  loaded.value = true;
}
async function load() {
  if (loading.value || saving.value) return;
  loading.value = true;
  try {
    apply(await getSettings());
    error.value = '';
  } catch {
    error.value = '采集配置读取失败，请检查服务及访问权限';
  } finally {
    loading.value = false;
  }
}
async function save() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    apply(await saveSettings(form.value));
    error.value = '';
    message.success('采集配置已保存，将自动应用');
    emit('changed');
  } catch {
    /* 请求层显示参数校验或并发修改错误，保留尚未保存的表单。 */
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
