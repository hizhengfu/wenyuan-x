<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm"
    :default-expanded-row-keys="['1']" is-tree :fun-api-delete="del" permission="PROJECT" :columns="columns"
    @updateSort="handleUpdateSort" @add="handleAdd" @rowEdit="handleEdit" @rowView="handleView">
    <template #queryForm>
      <n-form-item label="项目标题" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="项目标题" clearable></n-input>
      </n-form-item>
      <template v-if="!cmsStore.isPublish">
        <n-form-item label="所属栏目" path="categoryId">
          <HCascaderCategory v-model:value="paramsForm.categoryId" placeholder="请选择所属栏目" module="PROJECT" clearable>
          </HCascaderCategory>
        </n-form-item>
      </template>
      <n-form-item label="所属地区" path="areaId">
        <HAreaSelect v-model:value="paramsForm.areaId" placeholder="请选择区域" clearable filterable></HAreaSelect>
      </n-form-item>
      <n-form-item label="主营类别" path="type">
        <HDictSelect v-model:value="paramsForm.type" type="cms_project_type" placeholder="请选择项目类型" clearable filterable>
        </HDictSelect>
      </n-form-item>
      <n-form-item label="服务内容" path="projectService">
        <HDictSelect v-model:value="paramsForm.projectService" type="cms_project_service" placeholder="请选择服务内容"
          clearable filterable></HDictSelect>
      </n-form-item>
      <n-form-item label="保护级别" path="projectLevel">
        <HDictSelect v-model:value="paramsForm.projectLevel" type="cms_project_level" placeholder="请选择保护级别" clearable
          filterable></HDictSelect>
      </n-form-item>
      <n-form-item label="统计年代" path="projectYear">
        <n-input v-model:value="paramsForm.projectYear" placeholder="请输入统计年代" clearable></n-input>
      </n-form-item>
      <n-form-item label="保护分类" path="projectSort">
        <HDictSelect v-model:value="paramsForm.projectSort" type="cms_project_sort" clearable filterable></HDictSelect>
      </n-form-item>
      <n-form-item label="保护批次" path="projectSort">
        <HDictSelect v-model:value="paramsForm.projectBatch" type="cms_project_batch" clearable filterable>
        </HDictSelect>
      </n-form-item>
      <n-form-item label="关键字" path="keywords">
        <n-input v-model:value="paramsForm.keywords" placeholder="请输入关键字" clearable></n-input>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable
          placeholder="请选择状态"></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form v-model:show="formModel" :rules="rules" :model="form" label-width="auto" :title="formTitle"
      :disabled="formType === 'view'" width="80%" :ref-table="refPageTable" :handle-submit="handleSubmit">
      <n-layout position="absolute" has-sider sider-placement="right">
        <n-layout-content content-style="padding-right: 24px;display:flex;flex-direction: column">
          <n-form-item label="项目标题" path="title">
            <n-input v-model:value="form.title" placeholder="请输入项目标题"></n-input>
          </n-form-item>
          <n-form-item label="主营类别" path="type">
            <HDictSelect v-model:value="form.type" multiple type="cms_project_type" placeholder="请选择项目类型" clearable
              filterable>
            </HDictSelect>
          </n-form-item>
          <n-form-item label="所属地区" path="areaId">
            <HAreaSelect v-model:value="form.areaId" placeholder="请选择区域" clearable filterable></HAreaSelect>
          </n-form-item>
          <n-grid x-gap="6">
            <n-grid-item span="16">
              <n-form-item label="项目地址" path="address">
                <n-input v-model:value="form.address" placeholder="请输入项目地址" clearable></n-input>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="8">
              <n-form-item label="经纬度" path="latlng">
                <HFieldLatlng v-model:value="form.latlng"></HFieldLatlng>
              </n-form-item>
            </n-grid-item>
          </n-grid>
          <n-grid x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="保护类别" path="projectLevel">
                <HDictSelect v-model:value="form.projectLevel" type="cms_project_level" clearable filterable>
                </HDictSelect>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="遗产类别" path="projectSort">
                <HDictSelect v-model:value="form.projectSort" type="cms_project_sort" clearable filterable>
                </HDictSelect>
              </n-form-item>
            </n-grid-item>
          </n-grid>
          <n-grid x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="统计年代" path="projectYear">
                <n-input v-model:value="form.projectYear" placeholder="请输入统计年代"></n-input>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="项目规模" path="projectSize">
                <n-input v-model:value="form.projectSize" placeholder="请输入项目规模"></n-input>
              </n-form-item>
              <!--              <n-form-item label="保护批次" path="projectBatch">-->
              <!--                <HDictSelect-->
              <!--                  v-model:value="form.projectBatch"-->
              <!--                  type="cms_project_batch"-->
              <!--                  clearable-->
              <!--                  filterable-->
              <!--                ></HDictSelect>-->
              <!--              </n-form-item>-->
            </n-grid-item>
          </n-grid>
          <n-form-item label="专题类别" path="projectSpecial">
            <HDictSelect v-model:value="form.projectSpecial" type="cms_project_special" multiple clearable filterable>
            </HDictSelect>
          </n-form-item>
          <n-form-item path="content" style="flex: 1">
            <template #label>
              项目内容
              <span class="ml-3">
                （是否远程抓图
                <n-switch v-model:value="form.saveRemoteImage"></n-switch>）
              </span>
            </template>
            <h-editor :disabled="formType === 'view'" v-model:value="form.content" placeholder="请输入内容"></h-editor>
          </n-form-item>
          <n-form-item label="服务类别" path="projectServiceType">
            <HDictSelect v-model:value="form.projectServiceType" type="cms_project_service" multiple clearable
              filterable placeholder="请输入服务内容"></HDictSelect>
          </n-form-item>
          <n-form-item label="服务内容" path="projectService">
            <n-input type="textarea" v-model:value="form.projectService" placeholder="请输入服务内容"></n-input>
          </n-form-item>
          <n-form-item label="项目荣誉" path="projectHonor">
            <n-input type="textarea" v-model:value="form.projectHonor" placeholder="请输入项目荣誉"></n-input>
          </n-form-item>
          <n-form-item label="关联链接" path="url">
            <n-input v-model:value="form.url" placeholder="请输入关联链接"></n-input>
          </n-form-item>
          <HUpload :contentId="form.id" :form="form" label="上传附件" :disabled="formType === 'view'"></HUpload>
        </n-layout-content>
        <n-layout-sider collapse-mode="transform" :collapsed-width="15" :width="360" :native-scrollbar="true"
          show-trigger="arrow-circle" content-style="padding: 24px;" bordered>
          <n-form-item label="缩略图" path="image">
            <HAvatar v-model:value="form.image"></HAvatar>
          </n-form-item>
          <template v-if="!cmsStore.isPublish">
            <n-form-item label="所属栏目" path="categoryId">
              <HCascaderCategory v-model:value="form.categoryId" module="PROJECT" clearable placeholder="请选择所属栏目">
              </HCascaderCategory>
            </n-form-item>
          </template>
          <n-form-item label="关键字" path="keywords">
            <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
          </n-form-item>
          <n-form-item label="标签" path="tagIds">
            <TagSelect v-model:value="form.tagIds" type="CMS_PROJECT"></TagSelect>
          </n-form-item>

          <n-form-item label="内容状态" path="status">
            <h-dict-radio v-model:value="form.status" clearable type="content_status"
              placeholder="请选择状态"></h-dict-radio>
          </n-form-item>
        </n-layout-sider>
      </n-layout>

      <CmsPublicationPanel v-if="form.id" module="PROJECT" :content-id="form.id" :disabled="formType === 'view'" @changed="state => { form.status = state === 'PUBLISHED' ? 'NORMAL' : state === 'TRASH' ? 'DELETE' : 'AUDIT'; get(form.id).then(response => Object.assign(form, response.data)); refPageTable?.handleQuery(); }" />
    </h-page-form>
    <h-page-form v-model:show="detailModel" class="project" :model="form" label-width="auto" :title="formTitle"
      :ref-table="refPageTable" :disabled="formType === 'view'" :enable-cancel="false">
      <div class="page-detail" ref="refDetail">
        <n-table class="info-table" :single-line="false">
          <tr>
            <td>项目标题</td>
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
            <td>关键字</td>
            <td colSpan="7">{{ form.keywords || '--' }}</td>
          </tr>
          <tr>
            <td>描述</td>
            <td colSpan="7">{{ form.description || '--' }}</td>
          </tr>
          <tr>
            <td>主营类别</td>
            <td colSpan="3">
              <h-dict-text v-model:value="form.type" type="cms_project_type"></h-dict-text>
            </td>
            <td>所属地区</td>
            <td colSpan="3">{{ form.area.friendlyName || '--' }}</td>
          </tr>
          <tr>
            <td>项目地址</td>
            <td colSpan="3">{{ form.address || '--' }}</td>
            <td>经纬度</td>
            <td colSpan="3">{{ form.latlng || '--' }}</td>
          </tr>
          <tr>
            <td>保护类别</td>
            <td colSpan="3">
              <h-dict-text v-model:value="form.projectLevel" type="cms_project_level"></h-dict-text>
            </td>
            <td>遗产类别</td>
            <td colSpan="3">
              <h-dict-text v-model:value="form.projectSort" type="cms_project_sort"></h-dict-text>
            </td>
          </tr>
          <tr>
            <td>统计年代</td>
            <td colSpan="3">{{ form.projectYear || '--' }}</td>
            <td>项目规模</td>
            <td colSpan="3">{{ form.projectSize || '--' }}</td>
          </tr>
          <tr>
            <td>专题类别</td>
            <td colSpan="7">
              <h-dict-text v-model:value="form.projectSpecial" type="cms_project_special"></h-dict-text>
            </td>
          </tr>
          <tr>
            <td>项目内容</td>
            <td colSpan="7">
              <div v-html="form.content"></div>
            </td>
          </tr>
          <tr>
            <td>服务类别</td>
            <td colSpan="7">
              <h-dict-text v-model:value="form.projectServiceType" type="cms_project_service"></h-dict-text>
            </td>
          </tr>
          <tr>
            <td>服务内容</td>
            <td colSpan="7">{{ form.projectService || '--' }}</td>
          </tr>
          <tr>
            <td>项目荣誉</td>
            <td colSpan="7">{{ form.projectHonor || '--' }}</td>
          </tr>
          <tr>
            <td>关联链接</td>
            <td colSpan="7">{{ form.url || '--' }}</td>
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
  </h-page-data-table>
</template>
<script lang="ts" setup>
import CmsPublicationPanel from '../CmsPublicationPanel.vue';
import { HFieldKeywords, HFieldLatlng } from '@/components/form/field';
import { add, del, edit, get, list, updateSort } from '../../api/project';
import { h, ref, watch, watchEffect } from 'vue';
import { cloneDeep } from 'lodash-es';
import { strip_html } from '@/utils';
import { useCmsStore } from '@/modules/cms/store/cms';
import { useRoute } from 'vue-router';
import { NTag, NSpace } from 'naive-ui';
import HCascaderCategory from '@/modules/cms/components/form/field/CascaderCategory.vue';
import HAreaSelect from '@/modules/sys/components/form/field/AreaSelect.vue';
import TagSelect from '@/modules/sys/components/form/field/TagSelect.vue';

const cmsStore = useCmsStore();
const columns = [
  {
    key: 'title',
    title: '项目标题',
    columnType: 'title',
    fixed: 'left',
    width: '320px',
    resizable: true,
    rowDrag: true,
    ellipsis: { width: '30%' },
  },
  {
    key: 'type',
    title: '项目类型',
    dictType: 'cms_project_type',
    resizable: true,
    width: '220px',
    ellipsis: { width: '30%' },
  },
  { key: 'area.friendlyName', title: '所属地区', resizable: true, width: '220px', ellipsis: { width: '30%' } },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px', align: 'center' },
  { key: 'category.name', title: '所属栏目', width: '180px', resizable: true },
  { key: 'projectSort', title: '分类', dictType: 'cms_project_sort', width: '160px', ellipsis: true, resizable: true },
  {
    key: 'projectLevel',
    title: '级别',
    dictType: 'cms_project_level',
    width: '220px',
    ellipsis: true,
    resizable: true,
  },
  { key: 'projectYear', title: '年代', width: '120px', ellipsis: true, resizable: true },
  {
    key: 'projectServiceType',
    title: '服务类别',
    dictType: 'cms_project_service',
    width: '120px',
    ellipsis: true,
    resizable: true,
  },
  { key: 'createTime', title: '内容日期', columnType: 'dateTime', width: '220px', align: 'center' },
  { key: 'status', title: '状态', dictType: 'content_status', width: '100px', align: 'center' },
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
  categoryId: null,
  color: '',
  image: '',
  keywords: '',
  description: '',
  content: '',
  type: null,
  areaId: '',
  url: '',
  projectSize: '',
  projectLevel: '',
  projectHonor: '',
  projectSort: '',
  projectYear: '',
  projectBatch: '',
  projectSpecial: null,
  projectServiceType: null,
  projectService: '',
  address: '',
  latlng: '',
  status: 0,
  saveRemoteImage: false, //远程存图
  area: { friendlyName: '' },
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
  siteId: {
    required: true,
    message: '请选择站点',
  },
  areaId: {
    required: true,
    message: '请选择所属地区',
  },
  type: {
    required: true,
    message: '请选择项目类型',
  },
  categoryId: {
    required: true,
    message: '请选择归属栏目',
  },
  content: {
    required: true,
    message: '请输入内容',
  },
  address: {
    required: true,
    message: '请输入项目地址',
  },
  latlng: {
    required: true,
    message: '请输入经纬度',
  },
};

/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
  type: '',
  siteId: '',
  areaId: '',
  categoryId: '',
  projectLevel: '',
  projectYear: '',
  projectSort: '',
  projectBatch: '',
  projectService: '',
  status: null,
  keywords: ''
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
    form.value.tagIds = (resp.data?.tags || []).map((t: any) => t.id);
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
