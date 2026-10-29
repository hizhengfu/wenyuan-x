<template>
  <div class="wechat-collect-page">
    <h-page-data-table
      title="采集配置"
      permission="SPIDER_RULE"
      class="collect-table"
      ref="refPageTable"
      :fetch-data="list"
      :scroll-x="1100"
      :params="paramsForm"
      @add="handleAdd"
      @rowEdit="handleEdit"
      @rowView="handleView"
      :funApiDelete="del"
      :columns="columns"
    >
      <template #queryForm>
        <n-form-item label="名称" path="title">
          <n-input v-model:value="paramsForm.title" clearable></n-input>
        </n-form-item>
      </template>
      <h-page-form
        :rules="rules"
        :model="form"
        label-width="auto"
        :title="formTitle"
        :refTable="refPageTable"
        :disabled="formType === 'view'"
        :handleSubmit="handleSubmit"
        v-model:show="formModel"
      >
        <n-grid x-gap="10">
          <n-gi span="12">
            <n-form-item label="名称" path="title">
              <n-input v-model:value="form.title"></n-input>
            </n-form-item>
          </n-gi>
          <n-gi span="12">
            <n-form-item label="内容标识" path="sourceId">
              <n-input v-model:value="form.sourceId" clearable placeholder="内容管理系统请填写栏目ID"></n-input>
            </n-form-item>
          </n-gi>
          <n-gi span="24">
            <n-form-item label="列表来源" path="source">
              <n-select v-model:value="form.source" :options="sourceOptions"></n-select>
            </n-form-item>
          </n-gi>
          <n-gi span="24">
            <n-alert :title="sourceTip.title" type="info" :bordered="false" style="margin-bottom: 4px">
              <div style="font-size: 12px; line-height: 1.9" v-html="sourceTip.body"></div>
            </n-alert>
          </n-gi>

          <!-- 手动贴链接 -->
          <n-gi span="24" v-if="form.source === 'URL_LIST'">
            <n-form-item label="文章链接" path="articleUrls">
              <n-input
                v-model:value="form.articleUrls"
                type="textarea"
                :rows="5"
                placeholder="每行一个 https://mp.weixin.qq.com/s/... 链接"
              ></n-input>
            </n-form-item>
          </n-gi>

          <!-- RSS 订阅源 -->
          <template v-if="form.source === 'RSS'">
            <n-gi span="16">
              <n-form-item label="订阅源地址" path="feedUrl">
                <n-input v-model:value="form.feedUrl" placeholder="如 https://你的rss桥/feed/某公众号"></n-input>
              </n-form-item>
            </n-gi>
            <n-gi span="8">
              <n-form-item label="条目选择器" path="itemSelector">
                <n-input v-model:value="form.itemSelector" placeholder="默认 item, entry"></n-input>
              </n-form-item>
            </n-gi>
          </template>

          <!-- 仅保留历史配置的维护入口，新建使用扫码连接。 -->
          <n-gi v-if="form.source === 'MP_BACKEND'" span="24">
            <n-button v-if="automaticAvailable" @click="openAutomatic">改用扫码采集</n-button>
            <n-collapse style="margin-top: 12px">
              <n-collapse-item title="维护历史认证信息" name="legacy">
                <n-grid x-gap="10">
                  <n-gi span="12">
                    <n-form-item label="公众号 fakeid" path="mpFakeid">
                      <n-input v-model:value="form.mpFakeid"></n-input>
                    </n-form-item>
                  </n-gi>
                  <n-gi span="12">
                    <n-form-item label="token" path="mpToken">
                      <n-input v-model:value="form.mpToken"></n-input>
                    </n-form-item>
                  </n-gi>
                  <n-gi span="18">
                    <n-form-item label="cookie" path="mpCookie">
                      <n-input
                        v-model:value="form.mpCookie"
                        type="textarea"
                        :rows="3"
                        placeholder="登录 mp.weixin.qq.com 后台后从浏览器复制的 Cookie"
                      ></n-input>
                    </n-form-item>
                  </n-gi>
                  <n-gi span="6">
                    <n-form-item label="拉取条数" path="mpCount">
                      <n-input-number
                        v-model:value="form.mpCount"
                        :min="1"
                        :max="100"
                        style="width: 100%"
                      ></n-input-number>
                    </n-form-item>
                  </n-gi>
                </n-grid>
              </n-collapse-item>
            </n-collapse>
          </n-gi>

          <n-gi span="8">
            <n-form-item label="抓取间隔(ms)" path="sleepTime">
              <n-input-number v-model:value="form.sleepTime" :min="0" :step="500" style="width: 100%"></n-input-number>
            </n-form-item>
          </n-gi>
          <n-gi span="8">
            <n-form-item label="入库模块" path="module">
              <n-input v-model:value="form.module" placeholder="默认 ARTICLE"></n-input>
            </n-form-item>
          </n-gi>
          <n-gi span="8">
            <n-form-item path="status" label="采集后发布">
              <n-switch v-model:value="publishAfterCollect" :disabled="!canPublish && !publishAfterCollect">
                <template #checked>正式发布</template>
                <template #unchecked>草稿</template>
              </n-switch>
            </n-form-item>
          </n-gi>
          <n-gi span="24"><n-text depth="3">新建配置默认进入草稿，开启正式发布需要文章审核发布权限。</n-text></n-gi>
        </n-grid>
      </h-page-form>

      <!-- 预览结果 -->
      <DialogViewForm v-model:show="previewFlag" title="预览解析结果" @update:show="onPreviewClose">
        <n-spin :show="previewLoading">
          <div style="height: 600px; overflow: auto; padding: 8px">
            <n-result v-if="previewError" status="error" title="预览失败" :description="previewError"></n-result>
            <template v-else>
              <h3>{{ previewResult.title }}</h3>
              <n-text depth="3">{{ previewResult.author }} {{ previewResult.dateTime }}</n-text>
              <iframe
                v-if="previewResult.content"
                :srcdoc="previewDocument"
                sandbox=""
                referrerpolicy="no-referrer"
                title="文章正文预览"
                class="preview-frame"
              />
            </template>
          </div>
          <template #description>解析中...</template>
        </n-spin>
      </DialogViewForm>
    </h-page-data-table>
  </div>
</template>
<script lang="ts" setup>
import { list, edit, add, del, preview, collect } from '../api/wechat';
import { ref, h, computed } from 'vue';
import { useAuthStore } from '@/store';
import { collectPreviewDocument } from '../utils/collectPreview';
import { cloneDeep } from 'lodash-es';
import { NButton, useMessage, useDialog } from 'naive-ui';
import '@/components/form/form/index';

const message = useMessage();
const dialog = useDialog();
const auth = useAuthStore();
const props = defineProps<{ automaticAvailable?: boolean }>();
const emit = defineEmits<{ automatic: [] }>();
defineOptions({ name: 'WechatLinkCollect' });
const canPublish = computed(() => auth.hasPermission(['ARTICLE_ALL', 'ARTICLE_AUDIT']));
const canCollect = computed(() => auth.hasPermission(['SPIDER_RULE_ALL', 'SPIDER_RULE_EDIT']));
const sourceOptions = computed(() => [
  { label: '手动贴链接', value: 'URL_LIST' },
  { label: 'RSS订阅源', value: 'RSS' },
  ...(formType.value !== 'add' && form.value.source === 'MP_BACKEND'
    ? [{ label: '后台接口（历史配置）', value: 'MP_BACKEND', disabled: true }]
    : []),
]);

const sourceLabel: Record<string, string> = {
  URL_LIST: '手动贴链接',
  MP_BACKEND: '后台接口（历史配置）',
  RSS: 'RSS订阅源',
};

const sourceTip = computed(() => {
  if (form.value.source === 'URL_LIST') {
    return {
      title: '手动贴链接',
      body: '把公众号文章的分享链接逐行粘贴进来，系统逐篇抓取正文入库。适合"把已发文章导进系统、免重复录入"。',
    };
  }
  if (form.value.source === 'MP_BACKEND') {
    return {
      title: '后台接口（历史配置）',
      body: props.automaticAvailable
        ? '此处保留原有配置。建议改用扫码采集，无需手填认证信息；原配置不会自动删除。'
        : '此处仅用于维护已有后台接口配置。新增采集请使用文章链接或 RSS。',
    };
  }
  return {
    title: '已有 RSS 订阅源',
    body: props.automaticAvailable
      ? '填写已有的 RSS/Atom 地址即可导入文章。没有订阅源时，可使用文章链接或扫码采集。条目选择器通常保持默认。'
      : '填写已有的 RSS/Atom 地址即可导入文章。没有订阅源时，可使用文章链接。条目选择器通常保持默认。',
  };
});

const formModel = ref(false);
const refPageTable = ref();
const formTitle = ref();
const formType = ref('add');

// 预览
const previewFlag = ref(false);
const previewLoading = ref(false);
const previewResult = ref<any>({});
const previewError = ref('');
const previewDocument = computed(() => collectPreviewDocument(previewResult.value.content ?? ''));

const columns = [
  { key: 'title', title: '名称', columnType: 'title', ellipsis: true },
  {
    key: 'source',
    title: '列表来源',
    width: '130px',
    render: (row: any) => sourceLabel[row.source] || row.source,
  },
  { key: 'sourceId', title: '内容标识' },
  {
    key: 'status',
    title: '采集后发布',
    render: (row: any) => (row.status === 0 ? '正式发布' : row.status === 2 ? '草稿' : '已删除'),
  },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    align: 'center',
    width: '220px',
    render(row: any) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'info',
            class: 'mr-2',
            disabled: !canCollect.value,
            onClick: () => onPreview(row),
          },
          { default: () => '预览' },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'success',
            class: 'mr-2',
            disabled: !canCollect.value,
            onClick: () => onCollect(row),
          },
          { default: () => '立即采集' },
        ),
      ];
    },
  },
];

const defaultForm = {
  id: '',
  title: '',
  module: 'ARTICLE',
  sourceId: '',
  source: 'URL_LIST',
  articleUrls: '',
  feedUrl: '',
  itemSelector: '',
  mpFakeid: '',
  mpToken: '',
  mpCookie: '',
  mpCount: 20,
  sleepTime: 3000,
  status: 2,
};

const form = ref(cloneDeep(defaultForm));
const publishAfterCollect = computed({
  get: () => form.value.status === 0,
  set: (publish: boolean) => {
    form.value.status = publish ? 0 : 2;
  },
});
function openAutomatic() {
  if (!props.automaticAvailable) return;
  formModel.value = false;
  emit('automatic');
}

const rules = {
  title: { required: true, message: '请输入名称' },
  source: { required: true, message: '请选择列表来源' },
};

const paramsForm = ref({ title: '' });

function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `编辑「${row.title}」`;
  formModel.value = true;
}

function handleView(row: any) {
  formType.value = 'view';
  form.value = cloneDeep(defaultForm);
  Object.assign(form.value, row);
  formTitle.value = `查看「${row.title}」`;
  formModel.value = true;
}

function handleSubmit(data: any) {
  return formType.value === 'add' ? add(data) : edit(data);
}

// 预览
function onPreview(row: any) {
  previewFlag.value = true;
  previewLoading.value = true;
  previewError.value = '';
  previewResult.value = {};
  preview(String(row.id))
    .then((res: any) => {
      previewResult.value = res.data || {};
    })
    .catch((e: any) => {
      previewError.value = e?.message || '预览失败，请检查来源配置';
    })
    .finally(() => {
      previewLoading.value = false;
    });
}

function onPreviewClose(v: boolean) {
  if (!v) {
    previewResult.value = {};
    previewError.value = '';
    previewLoading.value = false;
  }
}

// 立即采集
function onCollect(row: any) {
  dialog.warning({
    title: '立即采集',
    content: `确认按「${row.title}」立即采集并入库？`,
    positiveText: '采集',
    negativeText: '取消',
    onPositiveClick: () => {
      const loading = message.loading('采集中...', { duration: 0 });
      collect(String(row.id))
        .then((res: any) => {
          // message.success(res.message || `已采集 ${res.data} 篇`)
        })
        .catch((e: any) => {
          // message.error(e?.message || '采集失败')
        })
        .finally(() => loading.destroy());
    },
  });
}
</script>

<style scoped>
.wechat-collect-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;
}
.collect-table {
  flex: 1;
  min-height: 0;
  height: auto;
}
.preview-frame {
  width: 100%;
  height: 500px;
  margin-top: 12px;
  border: 1px solid #ddd;
}
</style>
