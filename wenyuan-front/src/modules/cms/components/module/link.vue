<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :default-expanded-row-keys="['1']"
    is-tree
    :fun-api-delete="del"
    :columns="columns"
    permission="LINK"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @updateSort="handleUpdateSort"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="链接标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="链接标题" clearable></n-input>
      </n-form-item>
      <template v-if="!cmsStore.isPublish">
        <n-form-item label="所属栏目" path="categoryId">
          <HCascaderCategory clearable v-model:value="paramsForm.categoryId" placeholder="请选择所属栏目" module="LINK">
          </HCascaderCategory>
        </n-form-item>
      </template>
      <n-form-item label="内容状态" path="status">
        <h-dict-select
          v-model:value="paramsForm.status"
          type="content_status"
          clearable
          placeholder="请选择状态"
        ></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      width="80%"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-layout has-sider sider-placement="right">
        <n-layout-content content-style="padding-right: 24px;display:flex;flex-direction: column">
          <n-form-item label="链接标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入标题"></n-input>
          </n-form-item>
          <n-form-item label="链接地址" path="href">
            <h-upload-input v-model:value="form.href" placeholder="请输入链接或上传文件"></h-upload-input>
          </n-form-item>
          <n-form-item path="content" style="flex: 1">
            <template #label>
              内容
              <span class="ml-3">
                （是否远程抓图
                <n-switch v-model:value="form.saveRemoteImage"></n-switch>）
              </span>
            </template>
            <HEditor :disabled="formType === 'view'" v-model:value="form.content" placeholder="请输入内容"></HEditor>
          </n-form-item>
          <HUpload :contentId="form.id" :form="form" label="上传附件" :disabled="formType === 'view'"></HUpload>
        </n-layout-content>
        <n-layout-sider
          collapse-mode="transform"
          :collapsed-width="15"
          :width="360"
          :native-scrollbar="true"
          show-trigger="arrow-circle"
          content-style="padding: 24px;"
          bordered
        >
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
          <template v-if="!cmsStore.isPublish">
            <n-form-item label="所属栏目" path="categoryId">
              <HCascaderCategory v-model:value="form.categoryId" clearable placeholder="请选择所属栏目" module="LINK">
              </HCascaderCategory>
            </n-form-item>
          </template>
          <n-form-item label="关键字" path="keywords">
            <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
          </n-form-item>
          <n-grid x-gap="10">
            <n-form-item-gi :span="12" path="weight">
              <template #label>
                排序权重
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <IconMdiHelpCircle></IconMdiHelpCircle>
                  </template>
                  请输入权重，值越大，排序越靠前。
                </n-tooltip>
              </template>
              <n-input-number v-model:value="form.weight" :min="0" placeholder="请输入排序权重"></n-input-number>
            </n-form-item-gi>

            <!-- <n-form-item-gi :span="12" path="weightDate">
              <template #label>
                权重过期时间
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <IconMdiHelpCircle></IconMdiHelpCircle>
                  </template>
                  如果设置过期时间，将在设定时间后重设权重。
                </n-tooltip>
              </template>
              <n-date-picker v-model:value="form.weightDate"></n-date-picker>
            </n-form-item-gi> -->
          </n-grid>
          <n-form-item label="内容日期" path="createTime">
            <n-date-picker v-model:value="form.createTime"></n-date-picker>
          </n-form-item>
          <n-form-item label="内容状态" path="status">
            <h-dict-radio
              v-model:value="form.status"
              clearable
              type="content_status"
              placeholder="请选择状态"
            ></h-dict-radio>
          </n-form-item>
        </n-layout-sider>
      </n-layout>

      <CmsPublicationPanel v-if="form.id" module="LINK" :content-id="form.id" :disabled="formType === 'view'" @changed="state => { form.status = state === 'PUBLISHED' ? 'NORMAL' : state === 'TRASH' ? 'DELETE' : 'AUDIT'; get(form.id).then(response => Object.assign(form, response.data)); refPageTable?.handleQuery(); }" />
    </h-page-form>
    <h-page-form
      v-model:show="detailModel"
      class="project"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :enable-cancel="false"
    >
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>链接标题</td>
            <td colSpan="3">{{ form.title || '--' }}</td>
            <td rowspan="2">缩略图</td>
            <td rowspan="2" colspan="3">
              <h-image v-if="form.image" :src="form.image" alt="" :size="50"></h-image>
            </td>
          </tr>
          <tr>
            <td>所属栏目</td>
            <td colSpan="3" style="border-right: 1px solid var(--n-border-color)">
              {{ form.category.name || '--' }}
            </td>
          </tr>
          <tr>
            <td>链接地址</td>
            <td colSpan="7">{{ form.href || '--' }}</td>
          </tr>
          <tr>
            <td>关键字</td>
            <td colSpan="7">{{ form.keywords || '--' }}</td>
          </tr>
          <tr>
            <td>描述</td>
            <td colSpan="7">{{ form.description || '--' }}</td>
          </tr>
          <tr>
            <td>排序权重</td>
            <td colSpan="3">{{ form.weight || '--' }}</td>
            <td>发布时间</td>
            <td colSpan="3">{{ form.createTime ? dayjs(form.createTime).format('YYYY-MM-DD') : '--' }}</td>
          </tr>
          <tr>
            <td>内容</td>
            <td colSpan="7">
              <div v-html="form.content"></div>
            </td>
          </tr>
          <tr>
            <td>查看附件</td>
            <td colSpan="7">
              <h-file-list :contentId="form.id"></h-file-list>
            </td>
          </tr>
        </n-table>
      </div>
    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import CmsPublicationPanel from '../CmsPublicationPanel.vue';
import { HFieldKeywords } from '@/components/form/field';
import { add, del, edit, list, get, updateSort } from '../../api/link';
import { ref, watch, watchEffect, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import { strip_html, linkUrl } from '@/utils';
import { useCmsStore } from '../../store/cms';
import { useRoute } from 'vue-router';
import HCascaderCategory from '../form/field/CascaderCategory.vue';
import { NImage, NPopover, NQrCode } from 'naive-ui';
import dayjs from 'dayjs';

const cmsStore = useCmsStore();
const columns = [
  {
    key: 'title',
    title: '链接标题',
    columnType: 'title',
    width: '220px',
    rowDrag: true,
    resizable: true,
    ellipsis: true,
  },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px' },
  { key: 'category.name', title: '归属栏目', width: '220px', resizable: true, ellipsis: true },
  { key: 'href', title: '链接路径', width: '220px', resizable: true, ellipsis: true },
  {
    key: 'url',
    title: '二维码',
    width: '220px',
    resizable: true,
    ellipsis: true,
    render(row: any) {
      let url = linkUrl(`/view/url/${row.id}`);
      return h(
        NPopover,
        {
          trigger: 'hover',
        },
        {
          trigger: () => h('span', null, { default: () => url }),
          default: () =>
            h(NQrCode, {
              value: url,
              size: 256,
              bordered: true,
            }),
        },
      );
    },
  },
  { key: 'weight', title: '排序权重', width: '100px', align: 'right' },
  { key: 'status', title: '内容状态', dictType: 'content_status', width: '100px', align: 'center' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

const route = useRoute();
/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  title: '',
  categoryId: null,
  color: '',
  image: '',
  keywords: '',
  description: '',
  content: '',
  hits: '',
  weight: 0,
  status: 0,
  weightDate: null,
  createTime: null,
  href: '',
  saveRemoteImage: false, //远程存图
  category: { name: '' },
};

/**
 * 表单
 * */
const form: any = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入标题',
  },
  siteId: {
    required: true,
    message: '请选择站点',
  },
  categoryId: {
    required: true,
    message: '请选择归属栏目',
  },
  // content: {
  //   required: true,
  //   message: '请输入内容'
  // },
  href: {
    required: true,
    message: '请输入链接',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
  siteId: '',
  categoryId: '',
  status: null,
});

const formType = ref('add');

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add';
  form.value = cloneDeep(defaultForm);
  formTitle.value = '添加';
  formModel.value = true;
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  // Object.assign(form.value, row)
  formTitle.value = `编辑「${row.title}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    detailModel.value = true;
  });
  formTitle.value = `查看「${row.title}」`;
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (!data.categoryId) {
    data.categoryId = route.query.categoryId;
  }
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

/**
 * 更新排序
 * */
function handleUpdateSort(items: any) {
  let sorts = items.map((item: any, index: number) => {
    return { id: item.id, sort: index };
  });
  updateSort(sorts).finally(() => {
    refPageTable.value.handleRefresh();
  });
}

/**
 * 自动截取关键字
 * */
watchEffect(() => {
  if (!form.value.description && form.value.content) {
    form.value.description = strip_html(form.value.content).substring(0, 120);
  }
});

watch(
  () => route.query.categoryId,
  (newValue, oldValue) => {
    if (newValue) {
      paramsForm.value.categoryId = newValue as string;
      refPageTable.value?.handleQuery();
    }
  },
  { immediate: true },
);
</script>
