<template>
  <div>
    <n-popover trigger="hover">
      <template #trigger>
        <n-float-button :right="36" :top="80" shape="square" type="primary" rounded>
          <n-icon size="30">
            <IconMdiqrcode></IconMdiqrcode>
          </n-icon>
        </n-float-button>
      </template>
      <n-qr-code :value="form.id" :size="256" />
    </n-popover>
    <n-table :single-line="false">
      <colgroup>
        <col style="width: 100px" />
        <col />
        <col style="width: 100px" />
        <col />
      </colgroup>
      <tr>
        <td>文物名称</td>
        <td colspan="2">
          {{ form.name }}
        </td>
        <td rowspan="3">
          <h-image v-if="form.image" :src="form.image" :size="60"></h-image>
        </td>
      </tr>
      <tr>
        <td>编号</td>
        <td colspan="2" style="border-right-width: 1px">{{ form.code }}</td>
      </tr>
      <tr>
        <td>遗址名称</td>
        <td colspan="2" style="border-right-width: 1px">
          {{ form.project?.name }}
        </td>
      </tr>
      <td>遗迹单位</td>
      <td colspan="3">
        {{ form.projectCellName }}
      </td>
      <tr>
        <td>质地</td>
        <td>
          <HDictText :value="form.material" type="wms_artifact_material_type"></HDictText>
        </td>
        <td>数量</td>
        <td>{{ form.quantity }}{{ form.quantityUnit }}</td>
      </tr>
      <tr>
        <td>出土坐标</td>
        <td colspan="3">{{ form.coordinate }}</td>
      </tr>
      <td>出土日期</td>
      <td colspan="3">
        <n-time :time="form.unearthDate" format="yyyy-MM-dd" />
      </td>
      <tr>
        <td>出土状况</td>
        <td colspan="3">{{ form.preservation }}</td>
      </tr>
      <tr>
        <td>记录人</td>
        <td colspan="3">{{ form.recorderBy }}</td>
      </tr>
      <tr>
        <td>备注</td>
        <td colspan="3">{{ form.remarks }}</td>
      </tr>
      <tr>
        <td>附件</td>
        <td colSpan="3">
          <HFileList listType="image-card" :contentId="form.id"></HFileList>
          <!--          <ArtifactImagesView :contentId="form.id"></ArtifactImagesView>-->
        </td>
      </tr>
    </n-table>
  </div>
</template>
<script lang="ts" setup>
import { get } from '@/platforms/asa_wms/api/artifact.ts';
import { PropType, ref, watch } from 'vue';
import { cloneDeep } from 'lodash-es';

const props = defineProps({
  id: { type: String as PropType<string | undefined | null>, required: true },
});

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  image: '',
  name: '',
  projectId: undefined,
  project: { name: '' },
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

/**
 * 表单
 * */
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
