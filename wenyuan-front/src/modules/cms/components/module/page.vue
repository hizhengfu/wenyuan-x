<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    :default-expanded-row-keys="['1']"
    is-tree
    :fun-api-delete="del"
    :columns="columns"
    permission="PAGE"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="内容标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="内容标题" clearable></n-input>
      </n-form-item>
      <template v-if="!cmsStore.isPublish">
        <n-form-item label="所属栏目" path="categoryId">
          <HCascaderCategory v-model:value="paramsForm.categoryId" placeholder="请选择所属栏目" module="PAGE" clearable>
          </HCascaderCategory>
        </n-form-item>
      </template>
      <n-form-item label="内容状态" path="status">
        <h-dict-select
          v-model:value="paramsForm.status"
          type="content_status"
          clearable
          placeholder="请选择内容状态"
        ></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :disabled="formType === 'view'"
      width="80%"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-layout has-sider sider-placement="right">
        <n-layout-content content-style="padding-right: 24px;display:flex;flex-direction: column">
          <n-form-item label="内容标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入内容标题"></n-input>
          </n-form-item>
          <n-form-item path="content" style="flex: 1">
            <template #label>
              内容
              <span class="ml-3">
                （是否远程抓图
                <n-switch v-model:value="form.saveRemoteImage"></n-switch>）
              </span>
            </template>
            <h-editor :disabled="formType === 'view'" v-model:value="form.content" placeholder="请输入内容"></h-editor>
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
              <HCascaderCategory v-model:value="form.categoryId" clearable placeholder="请选择所属栏目" module="PAGE">
              </HCascaderCategory>
            </n-form-item>
          </template>
          <n-form-item label="页面编码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入编码"></n-input>
          </n-form-item>
          <n-form-item label="关键字" path="keywords">
            <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
          </n-form-item>
          <n-form-item label="自定义模板" path="customContentView">
            <h-template-list-select
              v-model:value="form.customContentView"
              :site-id="form.siteId"
              module="PAGE"
              prefix=""
            ></h-template-list-select>
          </n-form-item>
          <n-form-item label="内容日期" path="createTime">
            <n-date-picker v-model:value="form.createTime"></n-date-picker>
          </n-form-item>
          <n-form-item label="标签" path="tagIds">
            <TagSelect v-model:value="form.tagIds" type="CMS_PAGE"></TagSelect>
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

      <CmsPublicationPanel v-if="form.id" module="PAGE" :content-id="form.id" :disabled="formType === 'view'" @changed="state => { form.status = state === 'PUBLISHED' ? 'NORMAL' : state === 'TRASH' ? 'DELETE' : 'AUDIT'; get(form.id).then(response => Object.assign(form, response.data)); refPageTable?.handleQuery(); }" />
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
            <td>内容标题</td>
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
            <td>页面编码</td>
            <td colSpan="7">{{ form.code || '--' }}</td>
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
            <td>自定义模板</td>
            <td colSpan="3">{{ form.customContentView || '--' }}</td>
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
            <td>标签</td>
            <td colSpan="7">
              <n-space v-if="form.tags && form.tags.length" :size="[4, 4]">
                <n-tag
                  v-for="t in form.tags"
                  :key="t.id"
                  size="small"
                  :color="t.color ? { color: t.color, textColor: '#fff', borderColor: t.color } : undefined"
                >
                  {{ t.name }}
                </n-tag>
              </n-space>
              <span v-else>--</span>
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
    <h-comment v-model:show="commentDialog" :title="commentTitle" :content-id="commentContentId"></h-comment>
  </h-page-data-table>
</template>

<script lang="ts" setup>
import CmsPublicationPanel from '../CmsPublicationPanel.vue';
import { HFieldKeywords } from '@/components/form/field';
import HTemplateListSelect from '../form/field/TemplateListSelect.vue';
import { add, del, edit, get, list, updateSort } from '../../api/page';
import { h, ref, watch, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { strip_html } from '@/utils';
import { useRoute } from 'vue-router';
import { useCmsStore } from '../../store/cms';
import HComment from '../comment.vue';
import { NButton, NTag, NSpace } from 'naive-ui';
import HCascaderCategory from '../form/field/CascaderCategory.vue';
import TagSelect from '@/modules/sys/components/form/field/TagSelect.vue';
import dayjs from 'dayjs';

const cmsStore = useCmsStore();
const columns = [
  {
    key: 'title',
    title: '内容标题',
    columnType: 'title',
    width: 320,
    fixed: 'left',
    rowDrag: true,
    resizable: true,
  },
  { key: 'image', title: '缩略图', columnType: 'image', width: '100px' },
  { key: 'category.name', title: '所属栏目', width: 220, resizable: true },
  { key: 'createTime', title: '内容日期', columnType: 'dateTime' },
  { key: 'status', title: '内容状态', dictType: 'content_status', width: '100px', align: 'center' },
  {
    key: 'tags',
    title: '标签',
    width: 200,
    render(row: any) {
      const tags = row.tags || [];
      if (!tags.length) return '--';
      return h(
        NSpace,
        { size: [4, 4] as any },
        () =>
          tags.map((t: any) =>
            h(
              NTag,
              {
                size: 'small',
                color: t.color
                  ? { color: t.color, textColor: '#fff', borderColor: t.color }
                  : undefined,
              },
              { default: () => t.name },
            ),
          ),
      );
    },
  },
  {
    key: 'operate',
    type: 'operate',
    title: '操作',
    align: 'center',
    width: '220px',
    render(row: any) {
      return h(
        NButton,
        {
          size: 'tiny',
          type: 'success',
          class: 'mr-3',
          onClick() {
            handleComment(row);
          },
        },
        { default: () => '查看留言' },
      );
    },
  },
];

//评论相关变量
const commentDialog = ref(false);
const commentTitle = ref('查看留言');
const commentContentId = ref();

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
  code: '',
  color: '',
  image: '',
  keywords: '',
  description: '',
  content: '',
  hits: '',
  status: 0,
  createTime: null,
  allowComment: '',
  customContentView: '',
  saveRemoteImage: false, //远程存图
  category: { name: '' },
  tagIds: [] as string[],
  tags: [] as any[],
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
  code: {
    required: true,
    message: '请输入编码',
  },
  siteId: {
    required: true,
    message: '请选择站点',
  },
  categoryId: {
    required: true,
    message: '请选择归属栏目',
  },
  content: {
    required: true,
    message: '请输入内容',
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
  // Object.assign(form.value, row)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    form.value.tagIds = (resp.data?.tags || []).map((t: any) => t.id);
    formModel.value = true;
  });
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
 * 查看留言
 * */
function handleComment(row: any) {
  commentContentId.value = row.id;
  commentDialog.value = true;
  commentTitle.value = '查看 「' + row.title + '」 的留言';
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
