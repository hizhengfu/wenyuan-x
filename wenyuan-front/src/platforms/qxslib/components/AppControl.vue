<template>
  <div class="content_control">
    <n-config-provider :theme-overrides="themeOverrides">
      <n-tabs type="segment"
              v-model:value="activeTab"
              :on-update:value="toggleTab"
              style="width: 200px">
        <n-tab name="/">全景</n-tab>
        <n-tab name="/plane">平面</n-tab>
        <n-tab name="/video">监控</n-tab>
      </n-tabs>
    </n-config-provider>
  </div>
</template>
<script lang="ts" setup>
import {GlobalThemeOverrides} from 'naive-ui';
import {ref, watchEffect} from "vue";
import {useRoute, useRouter} from "vue-router";
import {useEventPointStore} from "../store/visualOperate";

const route = useRoute()

const activeTab = ref()

watchEffect(() => {
  if (['/', '/plane', '/video'].includes(route.path)) {
    activeTab.value = route.path
  }
})

const themeOverrides: GlobalThemeOverrides = {
  Tabs: {
    colorSegment: '#22191888',
    tabTextColorSegment: '#ffffff',
    tabTextColorActiveSegment: '#ffffff',
    tabTextColorHoverSegment: '#ffffff',
    tabColorSegment: '#88b1f4',
  },
}

const router = useRouter()
const eventPointStore = useEventPointStore()

function toggleTab(value: string) {
  router.push({path: value})
  eventPointStore.clearAll()
}

</script>
<style scoped>
.content_control {
  position: absolute;
  top: 10px;
  left: 50%;
  margin-left: -100px;
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}
</style>
