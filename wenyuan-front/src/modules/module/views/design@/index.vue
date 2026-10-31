<template>
  <n-card class="wh-full"
          :content-style="{padding:'0px'}"
          :segmented="{
      content: true,
      footer: 'soft'
    }">
    <template #header>
      <n-tabs v-model:value="activeTab" type="segment" justify-content="space-evenly">
        <n-tab name="form">
          <n-icon size="22">
            <icon-mdi-form-select></icon-mdi-form-select>
          </n-icon>
          表单设计
        </n-tab>
        <n-tab name="flow">
          <n-icon size="22">
            <icon-mdi-arrow-decision></icon-mdi-arrow-decision>
          </n-icon>
          流程设计
        </n-tab>
        <n-tab name="list">
          <n-icon size="22">
            <icon-mdi-format-list-bulleted></icon-mdi-format-list-bulleted>
          </n-icon>
          列表设计
        </n-tab>
        <n-tab name="config">
          <n-icon size="22">
            <icon-mdi-cogs></icon-mdi-cogs>
          </n-icon>
          表单配置
        </n-tab>
      </n-tabs>
    </template>
    <div style="position: relative;display: flex;flex-direction: column;flex: 1;height: 100%;width: 100%;">
      <transition name="fade" mode="out-in" appear>
        <keep-alive>
          <div>
            <component v-if="activeWorkspace" :is="activeWorkspace"></component>
            <n-empty v-else class="flex-center wh-full" description="暂无内容">
            </n-empty>
          </div>
        </keep-alive>
      </transition>
    </div>
  </n-card>
</template>
<script lang="ts" setup>
import FormDesign from '../../components/design/FormDesign.vue'
import FlowDesign from '../../components/design/FlowDesign.vue'
import ListDesign from '../../components/design/ListDesign.vue'
import FormConfig from '../../components/design/FormConfig.vue'
import {computed, ref} from 'vue';

const activeTab = ref('form')

const activeWorkspace = computed(() => {
  if (activeTab.value == 'form') {
    return FormDesign
  } else if (activeTab.value == 'flow') {
    return FlowDesign
  } else if (activeTab.value == 'list') {
    return ListDesign
  } else if (activeTab.value == 'config') {
    return FormConfig
  } else {
    return null
  }
})
</script>
