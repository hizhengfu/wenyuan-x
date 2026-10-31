<template>
  <div class="artifact-view">
    <n-grid x-gap="16" y-gap="16" cols="1 900:2" class="artifact-grid">
      <!-- 左侧：字段 -->
      <n-grid-item style="display: flex">
        <n-table :single-line="false" size="small">
          <colgroup>
            <col style="width: 100px" />
            <col />
            <col style="width: 100px" />
            <col />
          </colgroup>
          <tr>
            <td>文物名称</td>
            <td colspan="3">
              {{ form.name }}
            </td>
          </tr>
          <tr>
            <td>编号</td>
            <td colspan="3">{{ form.code }}</td>
          </tr>
          <tr>
            <td>遗址名称</td>
            <td colspan="3">{{ form.project?.name }}</td>
          </tr>
          <tr>
            <td>遗迹单位</td>
            <td colspan="3">{{ form.projectCellName }}</td>
          </tr>
          <tr>
            <td>质地</td>
            <td>
              <HDictText :value="form.material" type="wms_artifact_material_type"></HDictText>
            </td>
            <td>数量</td>
            <td>{{ form.quantity }}{{ form.quantityUnit }}</td>
          </tr>
          <tr>
            <td>出土日期</td>
            <td>
              <n-time v-if="form.unearthDate" :time="form.unearthDate" format="yyyy-MM-dd" />
              <template v-else>--</template>
            </td>
            <td>保存状况</td>
            <td>{{ form.saveStatus || '--' }}</td>
          </tr>
          <tr>
            <td>出土状况</td>
            <td colspan="3">{{ form.preservation || '--' }}</td>
          </tr>
          <tr>
            <td>出土坐标</td>
            <td colspan="3">{{ form.coordinate || '--' }}</td>
          </tr>
          <tr>
            <td>库房</td>
            <td>{{ form.storageRoom?.name || '--' }}</td>
            <td>位置</td>
            <td>{{ form.storageLocation?.name || '--' }}</td>
          </tr>
          <tr>
            <td>记录人</td>
            <td colspan="3">{{ form.recorderBy || '--' }}</td>
          </tr>
          <tr>
            <td>备注</td>
            <td colspan="3">{{ form.remarks || '--' }}</td>
          </tr>
          <tr>
            <td>二维码</td>
            <td colspan="3">
              <n-popover trigger="hover">
                <template #trigger>
                  <n-button shape="square" text>
                    <n-icon size="64">
                      <IconMdiqrcode></IconMdiqrcode>
                    </n-icon>
                  </n-button>
                </template>
                <n-qr-code :value="form.id" :size="256" />
              </n-popover>
            </td>
          </tr>
        </n-table>
      </n-grid-item>

      <!-- 右侧：图片（封面/多角度/线图/平剖面图） -->
      <n-grid-item style="display: flex">
        <n-card size="small" :bordered="true" style="flex: 1" content-style="padding:8px;">
          <template #header>
            <n-space size="small" align="center">
              <n-icon size="18"><IconMdiImage /></n-icon>
              <span style="font-weight: 500">文物图像</span>
            </n-space>
          </template>
          <n-tabs
            v-model:value="imgTab"
            type="line"
            style="flex: 1"
            pane-style="padding:5px;display:flex;flex-direction: column;"
            size="small"
            animated
          >
            <n-tab-pane name="cover" tab="封面">
              <div class="image-wrap">
                <h-image v-if="form.image" :src="form.image" :size="280" object-fit="contain"></h-image>
                <n-empty v-else description="暂无封面图" size="small" />
              </div>
            </n-tab-pane>
            <n-tab-pane name="photos" tab="多角度照片">
              <ArtifactImagesView v-if="form.id" :contentId="form.id"></ArtifactImagesView>
            </n-tab-pane>
            <n-tab-pane name="line" tab="线图">
              <h-file-list listType="image-card" :contentId="'lineDiagram_' + form.id"></h-file-list>
            </n-tab-pane>
            <n-tab-pane name="profile" tab="线图">
              <h-file-list listType="image-card" :contentId="'profile_' + form.id"></h-file-list>
            </n-tab-pane>
            <n-tab-pane name="files" tab="附件">
              <h-file-list :contentId="form.id"></h-file-list>
            </n-tab-pane>
          </n-tabs>
        </n-card>
      </n-grid-item>
    </n-grid>
  </div>
</template>
<script lang="ts" setup>
import { get } from '@/platforms/asa_wms/api/artifactStock.ts';
import { ref, watch, PropType } from 'vue';
import { cloneDeep } from 'lodash-es';
import ArtifactImagesView from './ArtifactImagesView.vue';

const props = defineProps({
  id: { type: String as PropType<string | undefined | null>, required: true },
});

const imgTab = ref('cover');

const defaultForm = {
  id: '',
  image: '',
  name: '',
  projectId: undefined,
  project: { name: '' },
  storageRoom: { name: '' },
  storageLocation: { name: '' },
  projectCell: '',
  projectCellName: '',
  code: '',
  categoryId: '',
  material: '',
  quantity: 1,
  quantityUnit: '件',
  year: '',
  dimensions: '',
  weight: null,
  preservation: '',
  saveStatus: '',
  coordinate: '',
  unearthDate: undefined,
  location: '',
  lat: '',
  lng: '',
  latlng: '',
  remarks: '',
  recorderBy: '',
  createBy: '',
  createTime: null,
  updateBy: '',
  updateTime: null,
};

const form = ref(cloneDeep(defaultForm));

function fetchData(id: string) {
  form.value = cloneDeep(defaultForm);
  get(id).then((resp) => {
    Object.assign(form.value, resp.data);
  });
}

watch(
  () => props.id,
  (newId) => {
    if (newId) {
      fetchData(newId);
    }
  },
  { immediate: true },
);
</script>
<style scoped>
.artifact-view {
  width: 100%;
  height: 100%;
  display: flex;
  position: relative;
}
.artifact-grid {
  width: 100%;
  flex: 1;
}
.image-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 280px;
  background: var(--n-color-modal, #fafafa);
  border-radius: 4px;
}
</style>
