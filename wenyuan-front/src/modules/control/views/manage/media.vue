<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :scroll-x="1200"
    :params="paramsForm"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
    :funApiDelete="del"
    :columns="columns"
  >
    <template #queryForm>
      <n-form-item label="媒体名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入媒体名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="所属场馆" path="venueId">
        <HCascaderDevice
          type="venue"
          clearable
          v-model:value="paramsForm.venueId"
          placeholder="请选择所属场馆"
        ></HCascaderDevice>
      </n-form-item>
      <n-form-item label="内容状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
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
      <n-grid cols="24" x-gap="6">
        <n-grid-item span="18">
          <n-form-item label="媒体名称" path="name">
            <n-input v-model:value="form.name"></n-input>
          </n-form-item>
          <n-grid cols="24" x-gap="6">
            <n-grid-item span="12">
              <n-form-item label="编码" path="code">
                <n-input v-model:value="form.code" placeholder="请输入编码" clearable></n-input>
              </n-form-item>
            </n-grid-item>
            <n-grid-item span="12">
              <n-form-item label="所属场馆" path="venueId">
                <HCascaderDevice
                  type="venue"
                  clearable
                  v-model:value="form.venueId"
                  placeholder="请选择所属场馆场馆"
                ></HCascaderDevice>
              </n-form-item>
            </n-grid-item>
          </n-grid>
        </n-grid-item>
        <n-grid-item span="6" class="flex-center">
          <n-form-item label="缩略图" path="image">
            <h-avatar v-model:value="form.image"></h-avatar>
          </n-form-item>
        </n-grid-item>
      </n-grid>

      <n-form-item label="媒体时长" path="time">
        <n-input v-model:value="form.time" placeholder="请输入媒体时长xx分xx秒" clearable></n-input>
      </n-form-item>
      <n-form-item label="文件路径" path="path">
        <h-upload-input v-model:value="form.path" placeholder="请输入文件路径" clearable></h-upload-input>
      </n-form-item>

      <n-form-item label="内容描述" path="content">
        <n-input type="textarea" v-model:value="form.content"></n-input>
      </n-form-item>

      <n-form-item label="内容状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>

    <h-dialog-form v-model:show="formConfigModel" :title="formMediaTitle" :handleSubmit="handleSaveMedia">
      <n-form-item label="媒体设备配置" :show-label="false" :show-feedback="false">
        <n-transfer
          ref="media_transfer"
          v-model:value="mediaDevice"
          virtual-scroll
          :options="mediaOptions"
          source-filterable
        />
      </n-form-item>
    </h-dialog-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {
  add,
  del,
  edit,
  get,
  list,
  updateSort,
  saveMediaDevice,
  getMediaDevice,
  getAllVenueMediaDevice,
} from '../../api/media.ts';
import { ref, h } from 'vue';
import { cloneDeep } from 'lodash-es';
import HCascaderDevice from '@/modules/control/components/form/field/CascaderDevice.vue';
import { NIcon } from 'naive-ui';
import Icon from '@/components/basic/DynamicIcon.vue';

const columns = [
  {
    key: 'name',
    title: '媒体名称',
    columnType: 'title',
    resizable: true,
    rowDrag: true,
    width: '260px',
    fixed: 'left',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
  },
  { key: 'code', title: '媒体编码', width: '200px', resizable: true },
  { key: 'venue.name', title: '所属场馆', width: '200px', resizable: true },
  { key: 'image', title: '缩略图', width: '100px', columnType: 'image' },
  { key: 'time', title: '媒体时长', width: '200px', resizable: true },
  { key: 'path', title: '文件路径', width: '200px', columnType: 'preview', resizable: true, ellipsis: true },
   { key: 'status', title: '内容状态', dictType: 'content_status', width: '100px' },
  {
    key: 'config',
    title: '配置指令',
    align: 'center',
    fixed: 'right',
    width: '100px',
    render(row: any) {
      return h(
        NIcon,
        {
          size: '22px',
          onClick() {
            handleLoadMedia(row);
          },
        },
        { default: () => h(Icon, { icon: 'c:Cogs' }) },
      );
    },
  },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  code: '',
  venueId: '',
  image: '',
  time: '',
  path: '',
  content: '',
  status: '0',
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

/**
 * 验证规则
 * */
const rules = {
  name: {
    required: true,
    message: '请输入名称',
  },
  code: {
    required: true,
    message: '请输入编码',
  },
  venueId: {
    required: true,
    message: '请选择场馆',
  },
  status: {
    required: true,
    message: '请输入status',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
  venueId: '',
  status: '',
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
  formTitle.value = `编辑「${row.name}」`;
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view';
  Object.assign(form.value, row);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  formTitle.value = `查看「${row.name}」`;
}

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
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

// 下面代码为媒体关联配置相关
const formConfigModel = ref(false);
// 已选媒体设备
const mediaDevice = ref();
const formMediaTitle = ref();
// 媒体id
const mediaId = ref();
//
const mediaOptions = ref();

/**
 * 加载场景
 * */
function handleLoadMedia(row: any) {
  formConfigModel.value = true;
  mediaId.value = row.id;
  formMediaTitle.value = `配置「${row.name}」关联设备`;

  getAllVenueMediaDevice(row.venueId).then((resp) => {
    mediaOptions.value = (resp.data || []).map((item: any) => {
      return { label: item.name, value: item.id, ...item };
    });
  });

  // 根据当前场景获取已勾选的视频
  getMediaDevice(row.id).then((res: any) => {
    mediaDevice.value = res.data?.map((item: any) => {
      return item.id;
    });
  });
}

function handleSaveMedia() {
  return saveMediaDevice(mediaId.value, mediaDevice.value).then((res: any) => {
    // console.log(res);
  });
}
</script>
