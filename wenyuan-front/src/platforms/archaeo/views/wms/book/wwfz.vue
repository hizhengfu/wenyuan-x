<template>
  <n-card class="h-full" content-style="padding:0">
    <n-layout class="h-full" has-sider>
      <n-layout-sider :width="220" bordered>
        <n-layout-header bordered style="padding: 10px"> 请选择分类帐条件</n-layout-header>
        <n-layout-content position="absolute" style="top: 60px">
          <ProjectTree @itemClick="handleClick"></ProjectTree>
        </n-layout-content>
      </n-layout-sider>
      <n-layout-content content-style="padding:15px;">
        <ArtifactList
          queryHidden
          :project-id="paramsForm.projectId"
          :project-cell-id="paramsForm.projectCellId"
          :project-plot-id="paramsForm.projectFlotId"
          :projectCellType="paramsForm.projectCellType"
        ></ArtifactList>
      </n-layout-content>
    </n-layout>
  </n-card>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { cloneDeep } from 'lodash-es';
import ProjectTree from '../../../components/ProjectTree.vue';
import ArtifactList from '../../../components/ArtifactList.vue';

const defaultForm = {
  projectId: undefined,
  projectCellId: undefined,
  projectCellType: undefined,
  projectFlotId: undefined,
};

/**
 * 查询表单
 * */
const paramsForm = ref(cloneDeep(defaultForm));

function handleClick(node: any) {
  paramsForm.value = cloneDeep(defaultForm);
  if (node.type === 'PROJECT') {
    paramsForm.value.projectId = node.id;
  } else if (node.type === 'PLOT') {
    paramsForm.value.projectId = node.projectId;
    paramsForm.value.projectFlotId = node.id;
  } else if (node.type === 'CELL_TYPE') {
    paramsForm.value.projectCellType = node.id;
  } else {
    paramsForm.value.projectCellId = node.id;
  }
}
</script>
