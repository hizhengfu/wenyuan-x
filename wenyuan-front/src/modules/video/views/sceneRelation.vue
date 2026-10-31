<template>
  <h-page-data-table
    ref="refPageTable"
    :fetch-data="list"
    :params="paramsForm"
    :fun-api-delete="del"
    :columns="columns"
    @updateSort="handleUpdateSort"
    @add="handleAdd"
    @rowEdit="handleEdit"
    @rowView="handleView"
  >
    <template #queryForm>
      <n-form-item label="场景名称" path="name">
        <n-input v-model:value="paramsForm.name" placeholder="请输入场景名称" clearable></n-input>
      </n-form-item>
      <n-form-item label="场景状态" path="status">
        <h-dict-select v-model:value="paramsForm.status" type="content_status" clearable filterable></h-dict-select>
      </n-form-item>
    </template>
    <h-page-form
      v-model:show="formModel"
      :rules="rules"
      :model="form"
      label-width="auto"
      :title="formTitle"
      :ref-table="refPageTable"
      :disabled="formType === 'view'"
      :handle-submit="handleSubmit"
    >
      <n-form-item label="场景名称" path="name">
        <n-input v-model:value="form.name" placeholder="请输入场景名称" clearable></n-input>
      </n-form-item>

      <n-grid x-gap="6">
        <n-grid-item span="12">
          <n-form-item label="场景编码" path="code">
            <n-input v-model:value="form.code" placeholder="请输入场景编码" clearable></n-input>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item label="视频布局" path="layout">
            <n-input v-model:value="form.layout" placeholder="请输入视频布局" clearable></n-input>
          </n-form-item>
        </n-grid-item>
      </n-grid>
      <slot name="sourceId" :form="form">
        <n-form-item label="关联数据" path="sourceId">
          <n-input v-model:value="form.sourceId" placeholder="请输入关联数据" clearable></n-input>
        </n-form-item>
      </slot>
      <n-form-item label="场景备注" path="content">
        <n-input v-model:value="form.content" type="textarea" placeholder="请输入场景备注"></n-input>
      </n-form-item>
      <n-form-item label="场景状态" path="status">
        <h-dict-radio v-model:value="form.status" type="content_status" clearable filterable></h-dict-radio>
      </n-form-item>
    </h-page-form>

    <h-dialog-form :title="formTitle" v-model:show="formConfigModel" :handleSubmit="handleSaveScene">
      <n-form-item label="场景视频" path="scene_video">
        <n-transfer
          ref="video_transfer"
          v-model:value="sceneVideo"
          virtual-scroll
          :options="videoSourceOptions"
          :render-source-list="renderSourceList"
          :render-target-label="renderLabel"
          source-filterable
        />
      </n-form-item>
    </h-dialog-form>
    <h-dialog-view-form :title="formTitle" v-model:show="scenePreview">
      <div style="height: 600px; position: relative">
        <VideoSceneView :sceneId="sceneId"></VideoSceneView>
      </div>
    </h-dialog-view-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { add, del, edit, get, getSceneVideo, list, saveSceneVideo, updateSort } from '../api/scene';
import { list as fetchCameraList } from '../api/camera';
import {videoRelationTree as tree,camera } from '../api/group';
import { h, ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import { NButton, NTree, NIcon, TransferRenderTargetLabel, TransferRenderSourceList } from 'naive-ui';
import { transformToTreeFormat } from '@/utils';
import Sortable from 'sortablejs';
import VideoSceneView from '../components/VideoSceneView.vue';
import Icon from '@/components/basic/DynamicIcon.vue';
import {useDialog} from 'naive-ui';

const columns = [
  {
    key: 'name',
    title: '场景名称',
    columnType: 'title',
    ellipsis: { tooltip: { style: { maxWidth: '600px' } } },
    rowDrag: true,
  },
  { key: 'code', title: '场景编码', width: '180px' },
  { key: 'layout', title: '布局方式', width: '120px' },
  {
    key: 'name',
    title: '配置',
    render(row: any) {
      return [
        h(
          NButton,
          {
            size: 'tiny',
            type: 'success',
            class: 'mr-3',
            onClick: () => {
              formTitle.value = `配置「${row.name}」场景`;
              handleLoadScene(row);
            },
          },
          {
            default: () => '配置视频',
          },
        ),
        h(
          NButton,
          {
            size: 'tiny',
            type: 'warning',
            class: 'mr-3',
            onClick: () => {
              sceneId.value = row.id;
              formTitle.value = `预览「${row.name}」场景`;
              scenePreview.value = true;
            },
          },
          {
            default: () => '场景预览',
          },
        ),
      ];
    },
  },
  { key: 'status', title: '场景状态', dictType: 'content_status', align: 'center', width: '100px' },
];

/**
 * 编辑表单相关
 * */
const formModel = ref(false);
/**
 * 表格ref
 * */
const refPageTable = ref();

const sceneId = ref();

const formTitle = ref();
const currentSourceId = ref('');
/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  name: '',
  code: '',
  content: '',
  sourceId: '',
  layout: '3*3',
  sort: 0,
  status: '0',
  createTime: null,
  createBy: '',
  updateTime: null,
  updateBy: '',
  sourceType:'1'
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
    message: '请输入场景名称',
  },
  status: {
    required: true,
    message: '请选择内容状态',
  },
};
/**
 * 查询表单
 * */
const paramsForm = ref({
  name: '',
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
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}

/**
 * 场景视频
 */

const sceneVideo = ref<Array<string>>([]);
const videoOptions = ref();
const videoSourceOptions = ref();
const cameraMap = ref(new Map());

//构建树形
const renderSourceList: TransferRenderSourceList = function ({ onCheck, pattern }) {
  return h(NTree, {
    style: "margin: 0 4px;",
    keyField: "value",
    labelField: "label",
    childrenField: "children",
    checkable: true,
    selectable: false,
    blockLine: true,
    checkOnClick: true,
    virtualScroll: true,
    showIrrelevantNodes: false,
    pattern,
    data: videoOptions.value,
    checkedKeys: sceneVideo.value,
    onUpdateCheckedKeys: (checkedKeys: Array<string | number>) => {
      onCheck(checkedKeys);
    },
    onLoad: onLoad,
    renderLabel: ({ option }: { option: any }) => {
      return h(
        "div",
        {
          style: {
            display: "flex",
            alignItems: "center",
            opacity: option.disabled ? 0.5 : 1,
          },
        },
        [
          h("span", option.label || option.name),
          !option.code &&
            h("span", {
              style: { marginLeft: "8px", fontSize: "12px", color: "#999" },
            }),
        ]
      );
    },
  });
};

async function createOptions(id: string) {
  currentSourceId.value = id;
  videoOptions.value = [];
  videoSourceOptions.value = [];
  cameraMap.value.clear();

  const response = await tree({ pageSize: -1, id });
  const rootNodes = response.data?.map((item: any) => {
    const isVideo = item.videoTreeType === 'VIDEO' || item.isLeaf === true;

    return {
      ...item,
      label: item.name,
      value: item.id,
      key: item.id,
      isLeaf: item.isLeaf !== false,
      disabled: false,
      checkboxDisabled: !isVideo,
    };
  }) || [];

  videoOptions.value = rootNodes;

  // 将组节点添加到 videoSourceOptions
  videoSourceOptions.value = rootNodes.map((node: any) => ({ ...node }));
}

// 加载子节点
async function onLoad(node: any) {
  if (!node) return;

  const response = await camera(node.value);
  const children = response.data?.map((item: any) => ({
    ...item,
    label: `${item.name}(${item.code?.startsWith('rtsp') ? 'rtsp' : 'hik'})`,
    value: item.id,
    key: item.id,
    isLeaf: true,
    disabled: false,
    checkboxDisabled: false
  })) || [];

  // 更新树形结构
  updateNodeChildren(videoOptions.value, node.value, children);
  children.forEach((child:any) => {
    cameraMap.value.set(child.value, child);
    if (!videoSourceOptions.value.find((item:any) => item.value === child.value)) {
      videoSourceOptions.value.push(child);
    }
  });
}

function updateNodeChildren(nodes: any[], targetId: string, children: any[]) {
  for (const node of nodes) {
    if (node.value === targetId) {
      node.children = children;
      if (!children || children.length === 0) node.isLeaf = true;
      return true;
    }
    if (node.children) {
      if (updateNodeChildren(node.children, targetId, children)) return true;
    }
  }
  return false;
}

/**
 * 配置弹出窗
 * */
const formConfigModel = ref(false);

const dialog =useDialog();
/**
 * 加载场景
 * */
async function handleLoadScene(row: any) {
  formConfigModel.value = true;
  sceneId.value = row.id;



  // 先初始化选项
  await createOptions(row.sourceId);

  // 获取场景已配置的视频
  const res = await getSceneVideo(row.id);
  const data = res.data || [];

  if (data.length > 0) {
    const cameraIds = [];
    for (const item of data) {
      const cameraId = item.id;
      cameraIds.push(cameraId);
      if (!videoSourceOptions.value.find((option:any) => option.value === cameraId)) {
        const cameraOption = {
          id: item.id,
          label: `${item.name}(${item.code?.startsWith('rtsp') ? 'rtsp' : 'hik'})`,
          value: item.id,
          key: item.id,
          name: item.name,
          code: item.code,
          groupId: item.groupId,
          serverId: item.serverId,
          isLeaf: true,
          checkboxDisabled: false
        };

        videoSourceOptions.value.push(cameraOption);
        cameraMap.value.set(cameraId, cameraOption);
      }
    }

    // 设置已选中的视频ID
    sceneVideo.value = cameraIds;
  } else {
    sceneVideo.value = [];
  }

  // 初始化排序功能
  initItemSort();

  dialog.warning({
    title:'请注意',
    content:"配置场景时请选择同源地址视频，否则只播放单个视频!!",
    positiveText:"确定",
    negativeText:'取消'

  })
}

/**
 * 场景预览逻辑
 * */
const scenePreview = ref(false);

const video_transfer = ref();

/**
 * 初始化排序
 * */
function initItemSort() {
  setTimeout(() => {
    let itemsEl = video_transfer.value?.$el?.querySelector('.v-vl-visible-items');
    const options = {
      handle: '.sortHandle',
      animation: 150,
      onUpdate: function (event: any) {
        let item = sceneVideo.value.splice(event.oldIndex, 1)[0];
        sceneVideo.value.splice(event.newIndex, 0, item);
      },
    };
    if (itemsEl) {
      Sortable.create(itemsEl, options);
    }
  }, 1000);
}

/**
 * 构建穿梭框的label
 * */
const renderLabel: TransferRenderTargetLabel = function ({ option }) {
  return h(
    'div',
    {
      style: {
        display: 'flex',
      },
    },
    {
      default: () => [
        h(
          NIcon,
          {
            class: 'mr-1 sortHandle',
            style: {
              display: 'flex',
              cursor: 'move',
              alignSelf: 'center',
            },
          },
          () => {
            return h(Icon, { icon: 'c:DragVertical' });
          },
        ),
        h(
          'div',
          {
            style: {
              display: 'flex',
              marginLeft: '6px',
              userSelect: 'none',
              alignSelf: 'center',
            },
          },
          { default: () => option.label },
        ),
      ],
    },
  );
};

/**
 * 存储场景和视频关系
 * */
function handleSaveScene() {
  // 保存上传场景视频id数组
  return saveSceneVideo(sceneId.value, sceneVideo.value).then((res: any) => {
    console.log(res, '保存结果');
  });
}
</script>
