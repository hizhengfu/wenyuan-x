<template>
  <n-card :title="form.name" :segmented="{
      content: true,

    }">
    <template #header-extra>
      <n-tabs type="bar" v-model:value="tabValue">
        <template v-for="item in tabs" :key="item.name">
          <n-tab :name="item.name"> {{ item.title }} </n-tab>
        </template>
      </n-tabs>
    </template>

    <component :is="activeComponent" :form="form"></component>
  </n-card>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue'
import description from './description.vue'
import evolution from './evolution.vue'
import outstandingValue from './outstandingValue.vue'
import constitute from './constitute.vue'

const tabValue = ref('description')

const props = defineProps({
  form: { type: Object, default: () => ({}) }
})

const tabs = [
  { name: 'description', title: '遗产描述' },
  { name: 'evolution', title: '历史沿革' },
  { name: 'outstandingValue', title: '突出普遍价值' },
  { name: 'constitute', title: '遗产构成' }
]

const componentMap: Record<string, any> = {
  description,
  evolution,
  outstandingValue,
  constitute
}


const activeComponent = computed(() => componentMap[tabValue.value])
</script>
