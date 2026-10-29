<template>
  <n-card class="h-full" content-style="padding:0">
    <h-page-data-table
      ref="refPageTable"
      :fetch-data="fetchData"
      :params="paramsForm"
      :columns="columns"
      :isEdit="false"
      @add="handleAdd"
      @rowView="handleView"
    >
      <template #queryForm>
        <n-form-item label="标题" path="title">
          <n-input v-model:value="paramsForm.title" placeholder="模糊匹配" clearable />
        </n-form-item>
        <n-form-item label="业务类型" path="bizType">
          <HDictSelect v-model:value="paramsForm.bizType" type="notify_biz_type" />
        </n-form-item>
      </template>

      <!-- 发起通知 -->
      <h-page-form
        v-model:show="formModel"
        :model="form"
        :rules="rules"
        :title="formTitle"
        label-placement="top"
        :handleSubmit="handleSubmit"
        :refTable="refPageTable"
      >
        <n-grid x-gap="12" :cols="2">
          <n-grid-item :span="2">
            <n-form-item label="标题" path="title">
              <n-input v-model:value="form.title" placeholder="通知标题" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="正文" path="content">
              <!-- 富文本编辑器（TinyMCE）：与模板正文/邮件正文同款编辑体验；
                   后端落库前 jsoup 会过滤危险标签，前端不需要额外清洗。 -->
              <HEditor v-model:value="form.content" placeholder="请输入通知正文" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="附件" path="files">
              <!-- HUpload 上传后把文件 ID 累积到 form.files；提交时一并发到后端。
                   后端 MessageNotifyService.send() 按 ID 反查文件元数据，
                   转 MessageNotifyAttachment 后并入 attachments 持久化。 -->
              <HUpload :form="form" label="上传附件" listType="text" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="启用通道" path="channels">
              <ChannelSelector v-model="form.channels" />
            </n-form-item>
          </n-grid-item>

          <!-- 选了短信 / 微信公众号 / 小程序等"模板强制"通道时，让用户选一个已配置的模板，
               提交时调 template/preview 渲染出 smsTemplateCode / 微信模板ID 等并入 payload.rendered。 -->
          <n-grid-item v-if="needsTemplate" :span="2">
            <n-form-item label="通知模板" path="templateId">
              <div style="width: 100%">
                <TemplateSelect
                  :model-value="form.templateId"
                  :require-channel-field="templateChannelFilter"
                  @update:model-value="form.templateId = $event || ''"
                  @update:template="onTemplateChange"
                />
                <n-text depth="3" style="font-size: 12px">
                  已选通道 [{{ mandatoryChannelLabel }}] 需走预审模板（运营商 / 微信平台限制，不支持自由文本）。
                  选模板后填写下方变量，发送时自动渲染。
                </n-text>
                <n-alert
                  v-if="templateOverridesFreeText"
                  type="warning"
                  :show-icon="true"
                  :bordered="false"
                  style="margin-top: 8px"
                >
                  所选模板自身定义了 [{{ overriddenChannelLabel }}] 的文案，发送时将<b>覆盖</b>上面填写的标题 / 正文。
                  如需用自己填的内容，请改用未定义这些通道的模板。
                </n-alert>
              </div>
            </n-form-item>
          </n-grid-item>
          <n-grid-item v-if="needsTemplate && templateParamKeys.length" :span="2">
            <n-form-item label="模板变量">
              <n-space vertical style="width: 100%">
                <div
                  v-for="key in templateParamKeys"
                  :key="key"
                  style="display: flex; align-items: center; gap: 8px"
                >
                  <n-text style="min-width: 120px">{{ key }}</n-text>
                  <n-input v-model:value="form.templateParams[key]" :placeholder="`变量 ${key} 的值`" />
                </div>
              </n-space>
            </n-form-item>
          </n-grid-item>

          <n-grid-item :span="2">
            <n-form-item label="接收范围" path="scope">
              <!-- 把已选通道传给 ScopePicker：根据通道动态显示裸地址 / 群机器人 tab -->
              <ScopePicker v-model="form.scope" :channels="form.channels" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="业务类型" path="bizType">
              <HDictSelect v-model:value="form.bizType" type="notify_biz_type" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item path="bizId">
              <template #label>
                业务 ID
                <n-tooltip trigger="hover" placement="top">
                  <template #trigger>
                    <n-icon size="14" style="vertical-align: -2px; margin-left: 2px; color: #999">
                      <IconMdiInformationOutline />
                    </n-icon>
                  </template>
                  关联的业务对象 ID（如订单号、用户 ID）。<br />
                  与"业务类型"组合用于幂等去重和排错；<br />
                  手填广播时可留空。
                </n-tooltip>
              </template>
              <n-input v-model:value="form.bizId" placeholder="可选，关联业务对象的 ID" />
            </n-form-item>
          </n-grid-item>
          <n-grid-item :span="2">
            <n-form-item label="定时发送" path="scheduledAt">
              <n-date-picker v-model:value="form.scheduledAtTs" type="datetime" clearable style="width: 100%" />
            </n-form-item>
          </n-grid-item>
          <!-- 公告语义字段：仅在 bizType=SYSTEM_ANNOUNCEMENT 时建议填写，普通业务通知留空 -->
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
                  公告类通知的醒目级别；普通业务通知留空。<br />
                  收件箱列表会按级别加颜色标签。
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
                  开启后本通知不会被定期清理任务删除，<br />
                  适合系统公告等需要长期可查的内容。
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
                  填写后用户在收件箱点击该通知会直接打开此链接，<br />
                  否则展示通知详情对话框。
                </n-tooltip>
              </template>
              <n-input v-model:value="form.clickUrl" placeholder="可选，例如 /sys/order/123 或 https://..." />
            </n-form-item>
          </n-grid-item>
        </n-grid>
      </h-page-form>
    </h-page-data-table>

    <!-- 详情 -->
    <h-page-form
      v-model:show="detailShow"
      :title="`通知详情：${detailData?.title || ''}`"
      :show-footer="false"
      label-placement="top"
    >
      <n-descriptions :column="2" size="small" bordered>
        <n-descriptions-item label="状态">
          <HDictText type="notify_status" :value="detailData?.notifyStatus"></HDictText>
        </n-descriptions-item>
        <n-descriptions-item label="接收人数">
          {{ detailData?.recipientCount }}
        </n-descriptions-item>
        <n-descriptions-item label="通道">
          <HDictText type="notify_channel" :value="detailData?.channels"></HDictText>
        </n-descriptions-item>
        <n-descriptions-item label="业务类型">
          <HDictText type="notify_biz_type" :value="detailData?.bizType"></HDictText>
        </n-descriptions-item>
        <n-descriptions-item v-if="detailData?.level" label="重要级别">
          <HDictText type="notify_level" :value="detailData?.level"></HDictText>
        </n-descriptions-item>
        <n-descriptions-item v-if="detailData?.noExpire" label="永久保留">
          <n-tag size="small" type="warning">是</n-tag>
        </n-descriptions-item>
        <n-descriptions-item v-if="detailData?.clickUrl" label="跳转 URL" :span="2">
          <n-text code style="word-break: break-all">{{ detailData.clickUrl }}</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="创建时间" :span="2">
          <n-time v-if="detailData?.createTime" :time="toMs(detailData.createTime)" />
          <span v-else>-</span>
        </n-descriptions-item>
        <n-descriptions-item label="正文" :span="2">
          <div v-html="detailData?.content || ''" />
        </n-descriptions-item>
        <!-- 附件区：HFileList 按 contentId 内部查 sys_file_content。后端 send() 把上传时
             collected 的 file ID 在创建 notify 后绑到 (fileId, notifyId)，这里就能拉出来。
             无附件时 HFileList 自身渲染为空，不影响布局 —— 但为了避免一条空行干扰阅读，
             仍按 attachments 是否有值条件渲染。 -->
        <n-descriptions-item v-if="detailData?.attachments?.length" label="附件" :span="2">
          <HFileList :content-id="detailData!.id" :show-download-btn="true" />
        </n-descriptions-item>
      </n-descriptions>
      <n-divider title-placement="left">投递审计</n-divider>
      <DispatchTable
        v-if="detailData"
        :dispatches="detailData.dispatches || []"
        :summary="detailData.dispatchSummary || { total: 0, success: 0, fail: 0, skipped: 0, wait: 0, retrying: 0 }"
      />
    </h-page-form>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, h, ref, type VNode } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NSpace, useMessage } from 'naive-ui';
import { confirm } from '@/utils';
import { useAuthStore } from '@/store';
import { findPage, create, cancel, retry, detail } from '../api/notify';
import { preview as templatePreview } from '../api/template';
import ChannelSelector from '../components/ChannelSelector.vue';
import ScopePicker from '../components/ScopePicker.vue';
import TemplateSelect from '../components/TemplateSelect.vue';
import DispatchTable from '../components/DispatchTable.vue';
import type { MessageTemplateVo } from '../typings/message';
import { renderTimeCell } from '../components/timeCell';
import type {
  MessageNotifyDao,
  MessageNotifyDetail,
  MessageNotifyTargetSpec,
  EnumMessageNotifyChannel,
} from '../typings/message';

const refPageTable = ref();
const formModel = ref(false);
const formTitle = ref('发起通知');
const authStore = useAuthStore();
const message = useMessage();

/** 模板强制通道：必须走预审模板（运营商/微信平台限制），自由文本无法发送 */
const TEMPLATE_REQUIRED_CHANNELS = ['SMS', 'WX_MP', 'WX_MA'];
const CHANNEL_LABELS: Record<string, string> = {
  EMAIL: '邮件',
  IN_APP: '站内信',
  SMS: '短信',
  WX_MP: '微信公众号',
  WX_MA: '微信小程序',
};

const paramsForm = ref({ title: '', bizType: '' });

/**
 * 行内"取消 / 重发"按钮：h-page-data-table 没有 row-actions 插槽，
 * 项目约定通过 columns 末尾的 actions 列 + render 函数承载。
 * 权限：NOTIFY_CENTER_ALL 或 NOTIFY_CENTER_EDIT。
 */
function renderRowActions(row: MessageNotifyDao) {
  const canEdit = authStore.hasPermission('NOTIFY_CENTER_ALL,NOTIFY_CENTER_EDIT');
  if (!canEdit) return '-';
  const buttons: VNode[] = [];
  if (canCancel(row)) {
    buttons.push(
      h(
        NButton,
        { text: true, size: 'small', type: 'warning', onClick: () => handleCancel(row) },
        () => '取消',
      ),
    );
  }
  if (canRetry(row)) {
    buttons.push(
      h(
        NButton,
        { text: true, size: 'small', type: 'primary', onClick: () => handleRetry(row) },
        () => '重发',
      ),
    );
  }
  return buttons.length ? h(NSpace, { size: 8 }, () => buttons) : '-';
}

const columns = [
  { key: 'title', title: '标题', minWidth: 200, ellipsis: true, columnType: 'title' },
  { key: 'level', title: '级别', width: 80, align: 'center', dictType: 'notify_level' },
  { key: 'bizType', title: '业务类型', width: 140, ellipsis: true, dictType: 'notify_biz_type' },
  { key: 'channels', title: '通道', dictType: 'notify_channel', width: 200, ellipsis: true },
  { key: 'recipientCount', title: '接收人数', width: 100, align: 'right' },
  { key: 'notifyStatus', title: '状态', width: 100, dictType: 'notify_status', align: 'center' },
  { key: 'createBy', title: '创建人', width: 120 },
  {
    key: 'createTime',
    title: '创建时间',
    width: 180,
    render: (row: any) => renderTimeCell(row.createTime),
  },
  {
    key: 'actions',
    title: '操作',
    width: 130,
    fixed: 'right',
    align: 'center',
    render: renderRowActions,
  },
];

interface NotifyForm {
  id?: string;
  title: string;
  content: string;
  channels: string[];
  scope: MessageNotifyTargetSpec;
  bizType?: string;
  bizId?: string;
  scheduledAtTs?: number | null;
  // 公告语义字段：替代老 sys/notify 的 level / path / 永久保留
  level?: string;
  clickUrl?: string;
  noExpire?: boolean;
  // 附件：HUpload 上传后把 sys/file 的文件 ID 累积到这里，提交时直发后端，
  // 由 MessageNotifyService.send() 反查并入 attachments 落库。
  files: string[];
  // 模板强制通道（短信/微信）用：选中的模板 id 与其变量取值。
  templateId: string;
  templateParams: Record<string, any>;
}

const defaultForm: NotifyForm = {
  title: '',
  content: '',
  channels: [],
  scope: { userIds: [], roleIds: [], groupIds: [], botNames: [], emails: [], phones: [] },
  bizType: '',
  bizId: '',
  scheduledAtTs: null,
  level: '',
  clickUrl: '',
  noExpire: false,
  files: [],
  templateId: '',
  templateParams: {},
};
const form = ref<NotifyForm>(cloneDeep(defaultForm));

/** 当前选中通道里属于"模板强制"的子集 */
const mandatoryChannels = computed(() =>
  (form.value.channels || []).filter((c) => TEMPLATE_REQUIRED_CHANNELS.includes(c)),
);
const needsTemplate = computed(() => mandatoryChannels.value.length > 0);
const mandatoryChannelLabel = computed(() =>
  mandatoryChannels.value.map((c) => CHANNEL_LABELS[c] || c).join('、'),
);
/** 仅当只有一种模板强制通道时按字段过滤模板；多种混选则不过滤，由用户自行选覆盖全部的模板 */
const templateChannelFilter = computed<'SMS' | 'WX_MP' | 'WX_MA' | null>(() =>
  mandatoryChannels.value.length === 1 ? (mandatoryChannels.value[0] as any) : null,
);
const selectedTemplate = ref<MessageTemplateVo | null>(null);
const templateParamKeys = computed<string[]>(() =>
  (selectedTemplate.value?.paramKeys || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean),
);

function onTemplateChange(tpl: MessageTemplateVo | null) {
  selectedTemplate.value = tpl;
  // 切换模板时按新模板的变量重建 params，丢弃旧 key
  const next: Record<string, any> = {};
  templateParamKeys.value.forEach((k) => (next[k] = form.value.templateParams[k] ?? ''));
  form.value.templateParams = next;
}

/**
 * 模板若自身定义了邮件 / 站内信文案，提交时 rendered 会覆盖表单里填的标题正文（同名字段）。
 * 仅当用户同时勾选了对应自由文本通道时才提示——否则覆盖无影响。
 */
const overriddenChannels = computed<string[]>(() => {
  const tpl = selectedTemplate.value;
  if (!tpl) return [];
  const out: string[] = [];
  const sel = form.value.channels || [];
  if (sel.includes('EMAIL') && (tpl.emailSubjectTpl || tpl.emailBodyTpl)) out.push('EMAIL');
  if (sel.includes('IN_APP') && (tpl.inAppTitleTpl || tpl.inAppContentTpl)) out.push('IN_APP');
  return out;
});
const templateOverridesFreeText = computed(() => overriddenChannels.value.length > 0);
const overriddenChannelLabel = computed(() =>
  overriddenChannels.value.map((c) => CHANNEL_LABELS[c] || c).join('、'),
);

const rules = {
  title: { required: true, message: '请输入标题', trigger: 'blur' },
  content: { required: true, message: '请输入正文', trigger: 'blur' },
  channels: {
    required: true,
    type: 'array',
    min: 1,
    message: '至少勾选一个通道',
    trigger: 'change',
  },
};

function fetchData(params: object) {
  return findPage(params);
}

function handleAdd() {
  form.value = cloneDeep(defaultForm);
  selectedTemplate.value = null;
  formTitle.value = '发起通知';
  formModel.value = true;
}

async function handleSubmit() {
  const payload: any = {
    title: form.value.title,
    content: form.value.content,
    channels: form.value.channels as EnumMessageNotifyChannel[],
    scope: form.value.scope,
    bizType: form.value.bizType || undefined,
    bizId: form.value.bizId || undefined,
    level: form.value.level || undefined,
    clickUrl: form.value.clickUrl || undefined,
    noExpire: form.value.noExpire || undefined,
    // 附件文件 ID 列表；空数组留 undefined，避免后端 attachments 拼成空数组
    files: form.value.files.length ? form.value.files : undefined,
  };
  if (form.value.scheduledAtTs) {
    payload.scheduledAt = new Date(form.value.scheduledAtTs).toISOString();
  }
  // 模板强制通道：必须选模板，提交时渲染出 smsTemplateCode / 微信模板ID 等。
  // 渲染产物放进 payload.rendered，后端 applyRendered 会填充对应通道字段并通过校验；
  // 自由文本的 EMAIL / IN_APP 仍走 title / content（模板未定义这些字段时不被覆盖）。
  if (needsTemplate.value) {
    if (!form.value.templateId) {
      message.error(`已选含 [${mandatoryChannelLabel.value}] 通道，请先选择通知模板`);
      throw new Error('template required');
    }
    const resp: any = await templatePreview({
      id: form.value.templateId,
      params: form.value.templateParams,
      channels: mandatoryChannels.value,
    });
    payload.rendered = resp?.data ?? resp;
  }
  await create(payload);
  // 关弹窗 + 刷表都由 DrawerPageForm 处理：
  //   - autoClose=true（默认）→ 提交成功后自动 dialog.value=false
  //   - :refTable="refPageTable" → 提交成功后自动 refTable.handleRefresh()
  // 不要在这里手动 formModel=false 或 refPageTable.refresh()——
  // PageDataTable 暴露的方法叫 handleRefresh 不是 refresh，写错也不报错（?.() 静默 no-op）。
}

const detailShow = ref(false);
const detailData = ref<MessageNotifyDetail | null>(null);

async function handleView(row: MessageNotifyDao) {
  const resp = await detail(row.id);
  detailData.value = resp.data as MessageNotifyDetail;
  detailShow.value = true;
}

function canCancel(row: MessageNotifyDao) {
  return row.notifyStatus === 'PROCESSING' || row.notifyStatus === 'SCHEDULED';
}
function canRetry(row: MessageNotifyDao) {
  return row.notifyStatus === 'DONE';
}

function handleCancel(row: MessageNotifyDao) {
  confirm({
    title: '确认取消该通知？',
    content: `通知「${row.title}」将停止投递，已发送的不会撤回。`,
    onPositiveClick: async () => {
      await cancel(row.id);
      refPageTable.value?.handleRefresh?.();
    },
  });
}

function handleRetry(row: MessageNotifyDao) {
  confirm({
    title: '确认重发失败的投递行？',
    content: `将对通知「${row.title}」中所有 FAIL 的投递行触发重发。`,
    onPositiveClick: async () => {
      await retry(row.id);
      refPageTable.value?.handleRefresh?.();
    },
  });
}

function toMs(s: string | undefined): number {
  if (!s) return 0;
  const t = new Date(s).getTime();
  return Number.isNaN(t) ? 0 : t;
}
</script>
