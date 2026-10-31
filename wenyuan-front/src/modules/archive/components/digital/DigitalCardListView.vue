<template>
  <HDataIterator title="数字成果" size="large" :fetch-data="list" :params="params" :show-header="false">
    <template #queryForm>
      <n-form-item label="成果类型">
        <HDictCheckbox v-model:value="params.fileType" type="archive_digital_file_type"></HDictCheckbox>
      </n-form-item>
    </template>
    <template #default="{ list }">
      <n-flex style="gap: 15px">
        <n-card v-for="row in list" :key="row.id" size="small" style="width: 32%" content-style="padding: 0"
          @click="handleViewFile(row)">
          <div style="cursor: pointer">
            <div class="flex" style="position: relative">
              <div style="width: 120px">
                <h-image preview-disabled :src="row.image" type="p" bgColor="cccccc" :height="140" :width="120"
                  :size="120" />
              </div>
              <div style="flex: 1; margin-left: 8px" class="flex-col">
                <n-ellipsis line-clamp="1" class="box-title">
                  {{ row.name }}
                  <template #tooltip>
                    <div style="max-width: 300px">
                      {{ row.name }}
                    </div>
                  </template>
                </n-ellipsis>
                <div class="box-main">
                  {{ row.content }}
                </div>
              </div>
            </div>
            <div class="digital-type">
              <n-element class="box-type"></n-element>
              <n-icon size="22px" color="#fff" class="box-icon">
                <Icon :icon="fileIcon(row.type)" />
              </n-icon>
            </div>
          </div>
        </n-card>
      </n-flex>
    </template>
  </HDataIterator>
  <n-modal v-model:show="dialogFilePreview" size="small" style="width: 1024px" :content-style="{
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    overflow: 'auto',
    position: 'relative',
    padding: 0,
  }" :title="digitalRow?.name" :segmented="{
    content: true,
    action: true,
  }" preset="card">
    <template #header-extra>
      <HFullScreen :target="refContent" quaternary size="tiny" iconSize="20px"></HFullScreen>
    </template>
    <div ref="refContent" class="content" :class="['content-' + fileType]">
      <n-tabs justify-content="space-evenly" style="position: absolute; top: 0; bottom: 0; left: 0; right: 0">
        <n-tab-pane name="basic" tab="基础信息" style="box-sizing: border-box; padding: 10px">
          <n-table :single-line="false" style="table-layout: auto" :size="size">
            <tr>
              <td class="w-120px">成果名称</td>
              <td colspan="3">{{ digitalRow.name }}</td>
            </tr>
            <tr>
              <td class="w-120px">监测对象</td>
              <td>{{ digitalRow.sourceName }}</td>
              <td class="w-120px">档案类型</td>
              <td>
                <HDictText type="archive_digital_type" :value="digitalRow.type"></HDictText>
              </td>
            </tr>
            <tr>
              <td class="w-120px">成果时间</td>
              <td colspan="3">
                <n-time :time="digitalRow.dateTime" format="yyyy-MM-dd" />
              </td>
            </tr>
            <tr>
              <td class="w-120px">设备型号</td>
              <td colspan="3">
                {{ digitalRow.manufacturer || '--' }}
              </td>
            </tr>
            <tr>
              <td colspan="4">制作参数</td>
            </tr>
            <tr>
              <td colspan="4">
                {{ digitalRow.parameter || '--' }}
              </td>
            </tr>
            <tr>
              <td colspan="4">采集内容</td>
            </tr>
            <tr>
              <td colspan="4">
                <div v-html="digitalRow.content || '--'"></div>
              </td>
            </tr>
            <tr>
              <td colspan="4">
                <HFileList :contentId="digitalRow.id"></HFileList>
              </td>
            </tr>
          </n-table>
        </n-tab-pane>
        <n-tab-pane name="pathPreview" tab="内容预览" v-if="digitalRow.path" style="padding: 0; text-align: center">
          <HFilePreview :previewType="digitalRow.fileType" :path="digitalRow.path" v-bind="$attrs"></HFilePreview>
        </n-tab-pane>
      </n-tabs>
    </div>
  </n-modal>
</template>
<script lang="ts" setup>
import { list } from '../../api/digital';
import Icon from '@/components/basic/DynamicIcon.vue';
import { computed, PropType, ref, watch } from 'vue';

const props = defineProps({
  type: { type: String, default: '' },
  showDateTime: { type: Boolean, default: true }, //是否显示时间查询
  size: { type: String as PropType<'small' | 'medium' | 'large' | undefined>, default: 'large' },
  title: { type: String, default: '数字成果' },
  sourceId: { type: String },
  queryType: { type: String },
});

const params = ref({ status: '0', fileType: props.queryType ? props.queryType : '', sourceId: props.sourceId ? props.sourceId : '' });

const refContent = ref();

/**
 * 文件预览控制
 * */
const dialogFilePreview = ref(false);
const filePreviewTitle = ref();

const digitalRow = ref<any>();

function handleViewFile(row: any) {
  dialogFilePreview.value = true;
  digitalRow.value = row;
}

const fileIcon = (type: string) => {
  if (/DRAWING$/i.test(type)) {
    return 'ic:baseline-insert-drive-file';
  } else if (/IMAGE$/i.test(type)) {
    return 'ic:baseline-image';
  } else if (/DOCUMENT$/i.test(type)) {
    return 'mdi:file-word';
  } else if (/VIDEO$/i.test(type)) {
    return 'mdi:file-video';
  } else if (/AUDIO$/i.test(type)) {
    return 'ic:baseline-audio-file';
  } else if (/MODEL$/i.test(type)) {
    return 'ic:baseline-3d-rotation';
  } else if (/PANORAMA$/i.test(type)) {
    return 'ic:baseline-panorama-photosphere';
  } else {
    return 'ic:round-dataset-linked';
  }
};

const fileType = computed(() => {
  if (!digitalRow.value.path) return 'link';
  if (/\.(dae|fbx|gltf|glb|obj|ply|stl)$/i.test(digitalRow.value.value)) {
    return 'model';
  } else {
    if (digitalRow.value.type) {
      return digitalRow.value.type.toLowerCase();
    } else {
      return 'link';
    }
  }
});

watch(
  () => props.queryType,
  (newValue, oldValue) => {
    if (newValue) {
      params.value.fileType = newValue
    }
  },
);
</script>

<style lang="scss" scoped>
:deep(.box-title) {
  font-weight: bold;
  font-size: 18px;
}

.box-main {
  margin-top: 10px;
  overflow: hidden; //溢出隐藏
  text-overflow: ellipsis; //溢出用省略号显示
  display: -webkit-box; //作为弹性伸缩合作模型显示
  -webkit-box-orient: vertical; //设置伸缩盒子的子元素排列方式：从上到下垂直排列
  -webkit-line-clamp: 4; //显示的行数
}

.digital-type {
  position: absolute;
  right: 0;
}

.box-type {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 0;
  height: 0;
  border-bottom: 50px solid var(--primary-color);
  border-left: 50px solid transparent;
}

.box-icon {
  position: absolute;
  right: 3px;
  bottom: 3px;
}

.content {
  min-height: 600px;
  max-height: 800px;
  box-sizing: border-box;
  overflow: hidden;

  :deep(.n-tab-pane) {
    position: relative;
    overflow: auto;
    flex: 1;
  }
}

.content-model,
.content-panorama {
  min-height: 600px;
}
</style>
