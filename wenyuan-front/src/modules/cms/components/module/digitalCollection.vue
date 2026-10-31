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
    permission="DIGITAL_COLLECTION"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="藏品名称" path="title">
        <n-input v-model:value="paramsForm.title" placeholder="藏品名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="藏品分类" path="mainType">
        <h-dict-select
          v-model:value="paramsForm.mainType"
          type="cms_digital_collection_category"
          clearable
          filterable
          placeholder="请选择藏品分类"
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="藏品类型" path="type" v-if="paramsForm.mainType != null">
        <h-dict-select
          v-model:value="paramsForm.type"
          :type="
           paramsFormdictType
          "
          clearable
          filterable
          placeholder="请选择藏品分类"
        ></h-dict-select>
      </n-form-item>
      <n-form-item label="所属地区" path="areaId">
        <HAreaSelect v-model:value="paramsForm.areaId" placeholder="请选择区域" clearable filterable></HAreaSelect>
      </n-form-item>
      <template v-if="!cmsStore.isPublish">
        <n-form-item label="所属栏目" path="categoryId">
          <HCascaderCategory
            v-model:value="paramsForm.categoryId"
            placeholder="请选择所属栏目"
            module="PRODUCT"
            clearable
          >
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
      width="80%"
      :title="formTitle"
      :disabled="formType === 'view'"
      :ref-table="refPageTable"
      :handle-submit="handleSubmit"
    >
      <n-layout has-sider sider-placement="right">
        <n-layout-content content-style="padding-right: 24px;display:flex;flex-direction: column">
          <n-form-item label="藏品名称" path="title">
            <n-input v-model:value="form.title" placeholder="请输入藏品名称"></n-input>
          </n-form-item>
          <n-grid x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="藏品分类" path="mainType">
                <h-dict-select
                  v-model:value="form.mainType"
                  type="cms_digital_collection_category"
                  clearable
                  placeholder="请选择藏品分类"
                ></h-dict-select>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="所属地区" path="areaId">
                <HAreaSelect v-model:value="form.areaId" placeholder="请选择区域" clearable filterable></HAreaSelect>
              </n-form-item>
            </n-grid-item>
          </n-grid>
          <n-grid x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="藏品类型" path="type">
                <h-dict-select
                  v-model:value="form.type"
                  :type="
                   dictType
                  "
                  
                  clearable
                  filterable
                  placeholder="请选择藏品分类"
                ></h-dict-select>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="藏品年代" path="year">
                <n-input v-model:value="form.year" placeholder="请输入藏品年代"></n-input>
              </n-form-item>
            </n-grid-item>
          </n-grid>
          <n-grid x-gap="6" v-if="form.mainType != 1">
            <n-grid-item span="12">
              <n-form-item label="藏品尺寸" path="size">
                <n-input v-model:value="form.size" placeholder="请输入尺寸"></n-input>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="收藏单位" path="groupName">
                <n-input v-model:value="form.groupName" placeholder="请输入收藏单位"></n-input>
              </n-form-item>
            </n-grid-item>
          </n-grid>
          <n-grid x-gap="6">
            <n-gi span="12">
                <n-form-item label="关联藏品" path="size">
               <AssociationIdSelect v-model:value="form.sourceId" :fetchdata="list" labelField="title"></AssociationIdSelect>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
                <n-form-item label="藏品格式" path="size">
                <h-dict-select
                  v-model:value="form.layout"
                  type="cms_digital_layout"
                  clearable
                  placeholder="请选择藏品格式"
                ></h-dict-select>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-grid x-gap="6">
            <n-gi span="12">
                <n-form-item label="贴图精度" path="size">
                 <h-dict-select
                  v-model:value="form.ttjd"
                  type="cms_digital_ttjd"
                  clearable
                  placeholder="请选择贴图精度"
                ></h-dict-select>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
                <n-form-item label="文物风格" path="size">
                <h-dict-select
                  v-model:value="form.style"
                  type="cms_digital_style"
                  clearable
                  placeholder="请选择文物格式"
                ></h-dict-select>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-grid x-gap="6">
            <n-gi span="12">
                <n-form-item label="是否文物" path="size">
                  <h-dict-select
                  v-model:value="form.culturalFlag"
                  type="sys_ok"
                  clearable
                  placeholder="是否是文物"
                ></h-dict-select>
              </n-form-item>
            </n-gi>
            <n-gi span="12">
              <n-form-item label="所属机构" path="groupId">
                <h-group-select v-model:value="form.groupId" placeholder="请选择机构"></h-group-select>
              </n-form-item>
            </n-gi>
          </n-grid>
          <n-form-item label="资产路径" path="path">
            <HUploadInput v-model:value="form.path" placeholder="请上传数字资产"></HUploadInput>
          </n-form-item>
          <n-form-item label="贴图文件" path="imagePath">
            <HUploadInput v-model:value="form.imagePath" placeholder="请上传贴图文件"></HUploadInput>
          </n-form-item>
          <n-form-item label="视频文件" path="videoPath">
            <HUploadInput v-model:value="form.videoPath" placeholder="请上传视频文件"></HUploadInput>
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
            <HAvatar v-model:value="form.image"></HAvatar>
          </n-form-item>
          <template v-if="!cmsStore.isPublish">
            <n-form-item label="所属栏目" path="categoryId">
              <HCascaderCategory
                v-model:value="form.categoryId"
                module="DIGITAL_COLLECTION"
                clearable
                placeholder="请选择所属栏目"
              ></HCascaderCategory>
            </n-form-item>
          </template>
          <n-form-item label="关键字" path="keywords">
            <h-field-keywords v-model:value="form.keywords" placeholder="请输入关键字"></h-field-keywords>
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入描述"></n-input>
          </n-form-item>
          <n-form-item label="价格" path="price">
            <n-input-group>
              <n-input-number
                v-model:value="form.price"
                min="0"
                :precision="2"
                placeholder="请输入价格"
              ></n-input-number>
              <n-input-group-label>元</n-input-group-label>
            </n-input-group>
          </n-form-item>
          <n-form-item label="标签" path="tagIds">
            <TagSelect v-model:value="form.tagIds" type="CMS_DIGITAL_COLLECTION"></TagSelect>
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

      <CmsPublicationPanel v-if="form.id" module="DIGITAL_COLLECTION" :content-id="form.id" :disabled="formType === 'view'" @changed="state => { form.status = state === 'PUBLISHED' ? 'NORMAL' : state === 'TRASH' ? 'DELETE' : 'AUDIT'; get(form.id).then(response => Object.assign(form, response.data)); refPageTable?.handleQuery(); }" />
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
            <td>藏品名称</td>
            <td colSpan="5">{{ form.title || '--' }}</td>
            <td rowspan="2" colspan="2">
              <h-image v-if="form.image" :src="form.image" alt="" :size="50"></h-image>
            </td>
          </tr>
          <tr>
            <td>所属栏目</td>
            <td colSpan="5" style="border-right: 1px solid var(--n-border-color)">
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
            <td>藏品分类</td>
            <td colSpan="3">
                 {{ form.mainType || '--' }}
              <!-- <h-dict-text v-model:value="form.mainType" type="cms_digital_collection_category"></h-dict-text> -->
            </td>
            <td>所属地区</td>
            <td colSpan="3">{{ form.area?.friendlyName || '--' }}</td>
          </tr>
          <tr>
            <td>藏品类型</td>
            <td colSpan="3">
              {{ form.type || '--' }}
            </td>
            <td>藏品年代</td>
            <td colSpan="3">{{ form.year || '--' }}</td>
          </tr>
          <tr v-if="form.mainType != 1">
            <td>藏品尺寸</td>
            <td colSpan="3">{{ form.size || '--' }}</td>
            <td>收藏单位</td>
            <td colSpan="3">{{ form.groupName || '--' }}</td>
          </tr>
          <tr>
            <td>藏品格式</td>
            <td colSpan="3"> <h-dict-text v-model:value="form.layout" type="cms_digital_layout"></h-dict-text></td>
            <td>贴图精度</td>
           <td colSpan="3"> <h-dict-text v-model:value="form.ttjd" type="cms_digital_ttjd"></h-dict-text></td>
          </tr>
          <tr>
            <td>文物风格</td>
            <td colSpan="3"> <h-dict-text v-model:value="form.style" type="cms_digital_style"></h-dict-text></td>
            <td>是否文物</td>
           <td colSpan="3"><h-dict-text v-model:value="form.culturalFlag" type="sys_ok"></h-dict-text> </td>
          </tr>
          <tr>
            <td>关联藏品</td>
            <td colSpan="3"> <AssociationIdLabel :value="form.sourceId" :fetchdata="list" labelField="title" ></AssociationIdLabel></td>
            <td></td>
           <td colSpan="3"> </td>
          </tr>
          <tr>
            <td>资产价格</td>
            <td colSpan="7">{{ form.price || '--' }}</td>
          </tr>
          <tr>
            <td>资产路径</td>
            <td colSpan="7">{{ form.path || '--' }}</td>
          </tr>
          <tr>
            <td>贴图文件</td>
            <td colSpan="7">{{ form.imagePath || '--' }}</td>
          </tr>
          <tr>
            <td>视频文件</td>
            <td colSpan="7">{{ form.videoPath || '--' }}</td>
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
  </h-page-data-table>
</template>
<script lang="ts" setup>
import CmsPublicationPanel from '../CmsPublicationPanel.vue';
import { HFieldKeywords } from '@/components/form/field';

import { add, del, edit, get, list } from '../../api/digitalCollection';
import { h, ref, watch, watchEffect,computed } from 'vue';
import { cloneDeep } from 'lodash-es';
import { strip_html } from '@/utils';
import { useCmsStore } from '../../store/cms';
import { useRoute } from 'vue-router';
import { NTag, NSpace } from 'naive-ui';
import HCascaderCategory from '../form/field/CascaderCategory.vue';
import HAreaSelect from '@/modules/sys/components/form/field/AreaSelect.vue';
import TagSelect from '@/modules/sys/components/form/field/TagSelect.vue';
import DictText from '@/components/form/field/dict/text.vue';
 import AssociationIdSelect from  '@/modules/archive/components/basic/AssociationIdSelect.vue'
 import AssociationIdLabel from  '@/modules/archive/components/basic/AssociationIdLabel.vue'

const mainTypeDictMap: Record<string, string> = {
  '建筑': '	cms_digital_jz_type',
  '建筑构件': 'cms_digital_jzgz_type',
  '碑刻、雕塑': 'cms_digital_bkds_type',
  '壁画、岩画': 'cms_digital_bhyh_type',
  '可移动文物': 'cms_digital_kydww_type',
  '环境': 'cms_digital_hj_type',
  '贴图纹理': 'cms_digital_ttwl_type',
}
const dictType = computed(() => {
  return mainTypeDictMap[form.value.mainType] || 'cms_digital_collection_cultural_type'
})
const paramsFormdictType = computed(() => {
  if(paramsForm.value.mainType){
  return mainTypeDictMap[paramsForm.value.mainType] || 'cms_digital_collection_cultural_type'
  }else{
    return 'cms_digital_collection_cultural_type'

  }

})
const cmsStore = useCmsStore();
const columns = [
  {
    key: 'title',
    title: '藏品名称',
    columnType: 'title',
    fixed: 'left',
    width: '320px',
    resizable: true,
    ellipsis: { width: '30%' },
  },
  { key: 'mainType', title: '藏品分类', dictType: 'cms_digital_collection_category', width: '120px' },
  { key: 'area.friendlyName', title: '所属地区', resizable: true, width: '220px', ellipsis: { width: '30%' } },
  {
    key: 'type',
    title: '藏品类型',
    resizable: true,
    width: '220px',
    ellipsis: { width: '30%' },
  },
  { key: 'image', title: '缩略图', columnType: 'image', width: '80px', align: 'center' },
  { key: 'category.name', title: '所属栏目', width: 220, resizable: true },
  { key: 'createTime', title: '内容日期', columnType: 'dateTime', width: '220px', align: 'center' },
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
  mainType: null,
  color: '',
  image: '',
  keywords: '',
  description: '',
  content: '',
  type: '',
  areaId: '',
  year: '',
  size: '',
  price: 100,
  path: '',
  status: 0,
  imagePath: '',
  videoPath: '',
  groupName: '',
  groupId: '',
  saveRemoteImage: false, //远程存图
  category: { name: '' },
  sourceId:'',
  layout:'',
  ttjd:'',
  culturalFlag:false,
  style:'',
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
  categoryId: {
    required: true,
    message: '请选择归属栏目',
  },
  mainType: {
    required: true,
    message: '请选择藏品分类',
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
  areaId: '',
  categoryId: '',
  type: null,
  mainType: null,
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
