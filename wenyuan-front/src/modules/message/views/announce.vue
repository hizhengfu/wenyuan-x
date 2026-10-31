<template>
  <n-card class="h-full" content-style="overflow:auto" :title="'发布站内信 / 公告'">
    <template #header-extra>
      <n-text depth="3" style="font-size: 12px">
        面向站内信 / 系统公告：支持长富文本正文；短信、微信公众号等模板通道请用「发起通知」。
      </n-text>
    </template>

    <n-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-placement="top"
      style="max-width: 960px; margin: 0 auto"
    >
      <n-form-item label="标题" path="title">
        <n-input v-model:value="form.title" placeholder="公告 / 站内信标题" />
      </n-form-item>

      <n-form-item label="正文" path="content">
        <!-- 整页布局给富文本更大空间，解决站内信内容很长的录入体验。
             HEditor 自身高度撑满父容器（height:100% + flex:1），这里用固定高度容器约束。 -->
        <div class="announce-editor">
          <HEditor v-model:value="form.content" placeholder="请输入正文，支持富文本" />
        </div>
      </n-form-item>

      <n-form-item path="summary">
        <template #label>
          摘要
          <n-tooltip trigger="hover" placement="top">
            <template #trigger>
              <n-icon size="14" style="vertical-align: -2px; margin-left: 2px; color: #999">
                <IconMdiInformationOutline />
              </n-icon>
            </template>
            收件箱列表展示的短摘要；留空时后端自动从正文截取前 120 字。
          </n-tooltip>
        </template>
        <n-input
          v-model:value="form.summary"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 3 }"
          placeholder="可选，收件箱列表展示用；留空自动从正文截取"
        />
      </n-form-item>

      <n-form-item label="附件" path="files">
        <HUpload :form="form" label="上传附件" listType="text" />
      </n-form-item>

      <n-form-item v-if="emailEnabled" label="同时发送邮件" path="alsoEmail">
        <n-switch v-model:value="form.alsoEmail" />
        <n-text depth="3" style="font-size: 12px; margin-left: 8px">
          开启后正文同步以邮件发送（邮件与站内信共用此富文本内容）。
        </n-text>
      </n-form-item>

      <n-form-item label="接收范围" path="scope">
        <ScopePicker v-model="form.scope" :channels="effectiveChannels" />
      </n-form-item>

      <n-grid x-gap="12" :cols="2">
        <n-grid-item>
          <n-form-item path="level">
            <template #label>
              重要级别
              <n-tooltip trigger="hover" placement="top">
                <template #trigger>
                  <n-icon size="14" style="vertical-align: -2px; margin-left: 2px; color: #999">
                    <IconMdiInformationOutline />
                  </n-icon>
                </template>
                收件箱列表会按级别加颜色标签；普通通知可留空。
              </n-tooltip>
            </template>
            <HDictSelect v-model:value="form.level" type="notify_level" clearable />
          </n-form-item>
        </n-grid-item>
        <n-grid-item>
          <n-form-item path="noExpire">
            <template #label>
              永久保留
              <n-tooltip trigger="hover" placement="top">
                <template #trigger>
                  <n-icon size="14" style="vertical-align: -2px; margin-left: 2px; color: #999">
                    <IconMdiInformationOutline />
                  </n-icon>
                </template>
                开启后本公告不会被定期清理任务删除，适合长期可查的系统公告。
              </n-tooltip>
            </template>
            <n-switch v-model:value="form.noExpire" />
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="2">
          <n-form-item path="clickUrl">
            <template #label>
              点击跳转 URL
              <n-tooltip trigger="hover" placement="top">
                <template #trigger>
                  <n-icon size="14" style="vertical-align: -2px; margin-left: 2px; color: #999">
                    <IconMdiInformationOutline />
                  </n-icon>
                </template>
                填写后用户在收件箱点击该公告会直接打开此链接，否则展示详情。
              </n-tooltip>
            </template>
            <n-input v-model:value="form.clickUrl" placeholder="可选，例如 /sys/order/123 或 https://..." />
          </n-form-item>
        </n-grid-item>
        <n-grid-item :span="2">
          <n-form-item label="定时发送" path="scheduledAt">
            <n-date-picker v-model:value="form.scheduledAtTs" type="datetime" clearable style="width: 100%" />
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-space>
        <n-button type="primary" :loading="submitting" @click="handleSubmit">发布</n-button>
        <n-button @click="reset">重置</n-button>
      </n-space>
    </n-form>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, onBeforeMount, ref } from 'vue';
import { useRouter } from 'vue-router';
import { cloneDeep } from 'lodash-es';
import { useMessage, type FormInst } from 'naive-ui';
import { create, enabledChannels } from '../api/notify';
import ScopePicker from '../components/ScopePicker.vue';
import type { MessageNotifyTargetSpec } from '../typings/message';

const message = useMessage();
const router = useRouter();
const formRef = ref<FormInst | null>(null);
const submitting = ref(false);

// 当前系统启用的通道（sys_config 中 NOTIFY_<CHANNEL>_ENABLED）。
// 邮件是否可勾选取决于 EMAIL 是否启用；未启用则隐藏开关并强制不发邮件。
const enabled = ref<string[]>([]);
const emailEnabled = computed(() => enabled.value.includes('EMAIL'));

onBeforeMount(async () => {
  const resp: any = await enabledChannels().catch(() => ({ data: [] }));
  enabled.value = (resp?.data || resp || []) as string[];
});

interface AnnounceForm {
  title: string;
  content: string;
  summary?: string;
  alsoEmail: boolean;
  scope: MessageNotifyTargetSpec;
  level?: string;
  clickUrl?: string;
  noExpire?: boolean;
  scheduledAtTs?: number | null;
  files: string[];
}

const defaultForm: AnnounceForm = {
  title: '',
  content: '',
  summary: '',
  alsoEmail: false,
  scope: { userIds: [], roleIds: [], groupIds: [], botNames: [], emails: [], phones: [] },
  level: '',
  clickUrl: '',
  noExpire: false,
  scheduledAtTs: null,
  files: [],
};

const form = ref<AnnounceForm>(cloneDeep(defaultForm));

// 站内信固定走 IN_APP；仅当 EMAIL 已启用且用户勾选时才附加 EMAIL。ScopePicker 据此显隐邮箱 tab。
const effectiveChannels = computed<string[]>(() =>
  form.value.alsoEmail && emailEnabled.value ? ['IN_APP', 'EMAIL'] : ['IN_APP'],
);

const rules = {
  title: { required: true, message: '请输入标题', trigger: 'blur' },
  content: { required: true, message: '请输入正文', trigger: 'blur' },
};

async function handleSubmit() {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  const payload: any = {
    title: form.value.title,
    content: form.value.content,
    summary: form.value.summary || undefined,
    // 公告语义固定 bizType，便于收件箱与订阅按业务类型过滤
    bizType: 'SYSTEM_ANNOUNCEMENT',
    channels: effectiveChannels.value,
    scope: form.value.scope,
    level: form.value.level || undefined,
    clickUrl: form.value.clickUrl || undefined,
    noExpire: form.value.noExpire || undefined,
    files: form.value.files.length ? form.value.files : undefined,
  };
  if (form.value.scheduledAtTs) {
    payload.scheduledAt = new Date(form.value.scheduledAtTs).toISOString();
  }
  submitting.value = true;
  try {
    await create(payload);
    message.success('发布成功');
    reset();
    await router.push({ path: '/message/list', query: { title: '通知列表' } });
  } finally {
    submitting.value = false;
  }
}

function reset() {
  form.value = cloneDeep(defaultForm);
}
</script>

<style scoped>
/* 给富文本一个固定高度，HEditor 内部 height:100% + flex:1 会撑满 */
.announce-editor {
  width: 100%;
  height: 480px;
  display: flex;
  flex-direction: column;
}
.announce-editor :deep(.tox-tinymce) {
  flex: 1;
}
</style>
