<template>
  <n-card class="h-full" content-style="padding:0">
    <h-page-data-table
      ref="refPageTable"
      :fetch-data="fetchData"
      :params="paramsForm"
      :columns="columns"
      :funApiDelete="del"
      @add="handleAdd"
      @rowEdit="handleEdit"
      @rowView="handleView"
    >
      <template #queryForm>
        <n-form-item label="模板名称" path="name">
          <n-input v-model:value="paramsForm.name" placeholder="模糊匹配" clearable />
        </n-form-item>
        <n-form-item label="业务类型" path="bizType">
          <HDictSelect v-model:value="paramsForm.bizType" type="notify_biz_type" />
        </n-form-item>
      </template>

      <h-page-form
        v-model:show="formModel"
        :title="formTitle"
        :model="form"
        :handleSubmit="handleSubmit"
        label-placement="top"
      >
        <TemplateForm ref="refTemplateForm" v-model="form" />
      </h-page-form>
    </h-page-data-table>

    <!-- 查看详情：表格化只读展示，独立于编辑表单 -->
    <h-page-form
      v-model:show="detailShow"
      :title="`模板详情：${detailModel?.name || ''}`"
      :show-footer="false"
      label-placement="top"
    >
      <TemplateDetail :model="detailModel" />
    </h-page-form>

    <!-- 预览 -->
    <h-page-form
      v-model:show="previewShow"
      title="模板预览"
      :show-footer="false"
      label-placement="top"
    >
      <n-alert v-if="previewTarget" type="info" :show-icon="false" style="margin-bottom: 12px">
        模板：<n-text strong>{{ previewTarget.name }}</n-text>
        <n-text depth="3"> ({{ previewTarget.code }})</n-text>
        <template v-if="previewTarget.paramKeys">
          ｜参数：<n-text code>{{ previewTarget.paramKeys }}</n-text>
        </template>
      </n-alert>
      <n-form-item label="渲染参数 (JSON)">
        <n-input
          v-model:value="previewParamsJson"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 8 }"
          placeholder='{"userName":"张三","verifyCode":"123456"}'
        />
      </n-form-item>
      <n-button type="primary" @click="runPreview">执行预览</n-button>
      <n-divider />

      <PreviewResult :result="previewResult" :result-json="previewResultJson" empty-text="— 点击「执行预览」查看渲染后的各通道内容 —" />
    </h-page-form>

    <!-- 试发：发送对象、通道、参数都在表单里明示，提交前可二次确认 -->
    <h-page-form
      v-model:show="testSendShow"
      title="试发到当前用户"
      label-placement="top"
      :handleSubmit="runTestSend"
    >
      <n-alert type="warning" :show-icon="false" style="margin-bottom: 12px">
        <div><strong>这是真实发送</strong>，将占用通道额度并产生 dispatch 审计行。建议先「预览渲染结果」确认后再提交。</div>
      </n-alert>

      <n-descriptions :column="1" size="small" bordered label-placement="left" style="margin-bottom: 12px">
        <n-descriptions-item label="模板">
          <n-text strong>{{ testSendTarget?.name || '-' }}</n-text>
          <n-text depth="3"> ({{ testSendTarget?.code || '-' }})</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="业务类型">
          <HDictText type="notify_biz_type" :value="testSendTarget?.bizType" />
        </n-descriptions-item>
        <n-descriptions-item label="接收人（当前登录用户）">
          <n-text strong>{{ currentUserName || '-' }}</n-text>
          <n-text v-if="currentUserEmail" depth="3"> · 邮箱 {{ currentUserEmail }}</n-text>
          <n-text v-else depth="3" style="color: #d97706"> · 邮箱未配置（EMAIL 通道会被 SKIPPED）</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="模板默认通道">
          <HDictText type="notify_channel" :value="testSendTarget?.defaultChannels" />
          <n-text v-if="!testSendTarget?.defaultChannels" depth="3">未配置（按系统默认通道）</n-text>
        </n-descriptions-item>
        <n-descriptions-item label="本次试发使用的通道">
          <ChannelSelector v-model="testSendChannels" />
          <n-text depth="3" style="font-size: 12px">
            留空则用模板默认通道；只勾 EMAIL 可仅测邮件而不影响其他通道额度。
          </n-text>
        </n-descriptions-item>
      </n-descriptions>

      <n-form-item :label="`渲染参数 (JSON) — 模板要求 keys: ${testSendTarget?.paramKeys || '无'}`">
        <n-input
          v-model:value="testSendParamsJson"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 10 }"
          placeholder='例如：{"userName":"张三","verifyCode":"123456"}'
        />
      </n-form-item>

      <n-space style="margin-bottom: 8px">
        <n-button size="small" @click="runTestSendPreview">预览渲染结果</n-button>
        <n-text v-if="testSendPreviewResult" depth="3" style="font-size: 12px; line-height: 28px">
          已预览(下方为渲染后内容，提交才真发)
        </n-text>
      </n-space>
      <PreviewResult v-if="testSendPreviewResult" :result="testSendPreviewResult" :result-json="testSendPreviewJson" />
    </h-page-form>
  </n-card>
</template>

<script lang="ts" setup>
import { computed, h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NSpace } from 'naive-ui';
import { useAuthStore } from '@/store';
import { findPage, add, edit, del, get, preview, testSend } from '../api/template';
import TemplateForm from '../components/TemplateForm.vue';
import TemplateDetail from '../components/TemplateDetail.vue';
import ChannelSelector from '../components/ChannelSelector.vue';
import PreviewResult from '../components/PreviewResult.vue';
import { renderTimeCell } from '../components/timeCell';
import type {
  EnumMessageNotifyChannel,
  MessageTemplateVo,
  MessageTemplatePreview,
} from '../typings/message';

const refPageTable = ref();
const refTemplateForm = ref();
const authStore = useAuthStore();

const paramsForm = ref({ name: '', bizType: '' });

/**
 * 行内"预览 / 试发"按钮：h-page-data-table 没有 row-actions 插槽，
 * 通过 columns 末尾的 actions 列 + render 函数承载（参考 user.vue 行内开关的写法）。
 */
function renderRowActions(row: MessageTemplateVo) {
  const buttons = [
    h(
      NButton,
      { text: true, size: 'small', type: 'primary', onClick: () => openPreview(row) },
      () => '预览',
    ),
  ];
  if (authStore.hasPermission('NOTIFY_TEMPLATE_ALL,NOTIFY_TEMPLATE_EDIT')) {
    buttons.push(
      h(
        NButton,
        { text: true, size: 'small', onClick: () => openTestSend(row) },
        () => '试发',
      ),
    );
  }
  return h(NSpace, { size: 8 }, () => buttons);
}

const columns = [
  { key: 'name', title: '模板名称', minWidth: 180, columnType: 'title', ellipsis: true },
  { key: 'code', title: '编码', width: 200, ellipsis: true },
  { key: 'bizType', title: '业务类型', width: 140, dictType: 'notify_biz_type' },
  { key: 'defaultChannels', title: '默认通道', width: 200, ellipsis: true },
  { key: 'lang', title: '语言', width: 80 },
  { key: 'systemBuiltin', title: '内置', width: 80, dictType: 'sys_ok', align: 'center' },
  {
    key: 'updateTime',
    title: '更新时间',
    width: 180,
    render: (row: any) => renderTimeCell(row.updateTime || row.createTime),
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

const formModel = ref(false);
const formTitle = ref('新建模板');
const formType = ref<'add' | 'edit'>('add');

// 查看详情：与编辑表单分离，避免只读复用编辑控件造成"看着是输入框"的体验
const detailShow = ref(false);
const detailModel = ref<MessageTemplateVo | null>(null);

const defaultForm: MessageTemplateVo = {
  code: '',
  name: '',
  bizType: '',
  defaultChannels: '',
  paramKeys: '',
  systemBuiltin: false,
  lang: '',
};
const form = ref<MessageTemplateVo>(cloneDeep(defaultForm));

function fetchData(params: object) {
  return findPage(params);
}

function handleAdd() {
  formType.value = 'add';
  formTitle.value = '新建模板';
  form.value = cloneDeep(defaultForm);
  formModel.value = true;
}

async function handleEdit(row: MessageTemplateVo) {
  formType.value = 'edit';
  formTitle.value = '编辑模板';
  const resp = await get(row.id!);
  form.value = (resp.data as MessageTemplateVo) || cloneDeep(defaultForm);
  formModel.value = true;
}

async function handleView(row: MessageTemplateVo) {
  const resp = await get(row.id!);
  detailModel.value = (resp.data as MessageTemplateVo) || null;
  detailShow.value = true;
}

async function handleSubmit() {
  await refTemplateForm.value?.validate?.();
  if (formType.value === 'add') {
    await add(form.value);
  } else if (formType.value === 'edit') {
    await edit(form.value);
  }
  formModel.value = false;
  refPageTable.value?.handleRefresh?.();
}

/**
 * 把模板的 paramKeys（逗号分隔的字符串，如 "userName,verifyCode"）变成空值 JSON 文本：
 *
 *   "userName,verifyCode"  →  {"userName":"","verifyCode":""}（两空格缩进）
 *
 * 调用方拿这个文本直接塞进 textarea，运维只要把空字符串改成实际值即可。
 * paramKeys 为空 / 解析失败时返回 "{}"，避免破坏 JSON 合法性。
 */
function buildInitialParamsJson(paramKeysCsv?: string | null): string {
  if (!paramKeysCsv) return '{}';
  const keys = paramKeysCsv.split(',').map((s) => s.trim()).filter(Boolean);
  if (keys.length === 0) return '{}';
  const init: Record<string, string> = {};
  keys.forEach((k) => {
    init[k] = '';
  });
  return JSON.stringify(init, null, 2);
}

/** 安全解析 textarea 里的 JSON；失败时返回 null（调用方可决定是中止还是兜底空对象）。 */
function parseParamsJson(text: string): Record<string, any> | null {
  try {
    const v = JSON.parse(text || '{}');
    return v && typeof v === 'object' ? v : {};
  } catch {
    return null;
  }
}

/** 渲染结果 → JSON 字符串（两空格缩进，<pre> 直接展示）；空值返回空串。 */
function previewToJson(p: MessageTemplatePreview | null): string {
  if (!p) return '';
  // 过滤掉全 null/空的字段，避免一坨 null 喧宾夺主
  const compact: Record<string, any> = {};
  Object.entries(p).forEach(([k, v]) => {
    if (v != null && v !== '') compact[k] = v;
  });
  return JSON.stringify(compact, null, 2);
}

// 预览
const previewShow = ref(false);
const previewParamsJson = ref('{}');
const previewResult = ref<MessageTemplatePreview | null>(null);
const previewTarget = ref<MessageTemplateVo | null>(null);
const previewResultJson = computed(() => previewToJson(previewResult.value));

async function openPreview(row: MessageTemplateVo) {
  // 拉完整模板拿 paramKeys（列表行带 paramKeys 但不一定最新）
  const resp = await get(row.id!);
  previewTarget.value = (resp.data as MessageTemplateVo) || row;
  previewResult.value = null;
  previewParamsJson.value = buildInitialParamsJson(previewTarget.value?.paramKeys);
  previewShow.value = true;
}

async function runPreview() {
  const params = parseParamsJson(previewParamsJson.value);
  if (!params) return;
  const resp = await preview({ id: previewTarget.value?.id, params });
  previewResult.value = resp.data as MessageTemplatePreview;
}

// 试发
const testSendShow = ref(false);
const testSendParamsJson = ref('{}');
const testSendTarget = ref<MessageTemplateVo | null>(null);
const testSendChannels = ref<EnumMessageNotifyChannel[]>([]);
const testSendPreviewResult = ref<MessageTemplatePreview | null>(null);
const testSendPreviewJson = computed(() => previewToJson(testSendPreviewResult.value));

const currentUserName = computed(
  () => authStore.userInfo?.name || authStore.userInfo?.username || authStore.userInfo?.id || '',
);
const currentUserEmail = computed(() => authStore.userInfo?.email || '');

async function openTestSend(row: MessageTemplateVo) {
  // 与 openPreview 同样拉完整模板，确保 paramKeys/defaultChannels/bizType 都是最新
  const resp = await get(row.id!);
  testSendTarget.value = (resp.data as MessageTemplateVo) || row;
  testSendParamsJson.value = buildInitialParamsJson(testSendTarget.value?.paramKeys);
  testSendChannels.value = []; // 留空 = 用模板默认通道，运维勾选则覆盖
  testSendPreviewResult.value = null;
  testSendShow.value = true;
}

async function runTestSendPreview() {
  const params = parseParamsJson(testSendParamsJson.value);
  if (!params) return;
  // 同一个 preview 接口，channels 字段后端目前不影响渲染（按模板全部通道渲染），仅在真发时才裁剪。
  const resp = await preview({ id: testSendTarget.value?.id, params });
  testSendPreviewResult.value = resp.data as MessageTemplatePreview;
}

async function runTestSend() {
  const params = parseParamsJson(testSendParamsJson.value);
  if (!params) return;
  const payload: any = { id: testSendTarget.value?.id, params };
  if (testSendChannels.value.length > 0) {
    payload.channels = testSendChannels.value;
  }
  await testSend(payload);
  testSendShow.value = false;
}
</script>

