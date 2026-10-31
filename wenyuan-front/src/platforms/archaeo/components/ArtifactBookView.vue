<template>
  <div>
    <n-table :single-line="false">
      <colgroup>
        <col style="width: 100px" />
        <col />
        <col style="width: 100px" />
        <col />
      </colgroup>
      <tr>
        <td>时代</td>
        <td colspan="3">{{ form.timePeriod || '--' }}</td>
      </tr>
      <tr>
        <td>文化属性</td>
        <td colspan="3">{{ form.culturalAttribute || '--' }}</td>
      </tr>
      <tr>
        <td>用途</td>
        <td colspan="3">{{ form.purpose || '--' }}</td>
      </tr>
      <tr>
        <td>形制</td>
        <td>
          {{ form.shape || '--' }}
        </td>
        <td>纹饰</td>
        <td>{{ form.decoration || '--' }}</td>
      </tr>
      <tr>
        <td>制作工艺</td>
        <td colspan="3">{{ form.craftingTechnique || '--' }}</td>
      </tr>
      <tr>
        <td>尺寸</td>
        <td>{{ form.dimensions || '--' }}CM</td>
        <td>重量</td>
        <td>{{ form.weight || '--' }}KG</td>
      </tr>
      <tr>
        <td>文物图像</td>
        <td colspan="3">
          <ArtifactImagesView v-if="id" :contentId="id"></ArtifactImagesView>
        </td>
      </tr>
      <tr>
        <td>文物线图</td>
        <td colspan="3">
          <h-file-list :contentId="'lineDiagram_' + id"></h-file-list>
        </td>
      </tr>
    </n-table>
  </div>
</template>
<script lang="ts" setup>
import { getExtend } from '@/platforms/asa_wms/api/artifact.ts';
import { ref, watch, PropType } from 'vue';
import { cloneDeep } from 'lodash-es';
import ArtifactImagesView from './ArtifactImagesView.vue';

const props = defineProps({
  id: { type: String as PropType<string | undefined | null>, required: true },
});

/**
 * 表单默认值
 * */
const defaultForm = {
  timePeriod: undefined, //时代
  culturalAttribute: '', //文化属性
  purpose: '', //用途
  shape: '', //形制
  decoration: '', //纹饰
  craftingTechnique: '', //制作工艺
  lineDiagram: '', //文物线图
  dimensions: '', //尺寸（长×宽×高/cm）
  weight: 0, //重量（kg）
};

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm));

function fetchData(id: string) {
  form.value = cloneDeep(defaultForm);
  getExtend(id).then((resp) => {
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
