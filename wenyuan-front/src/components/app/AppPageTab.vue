<template>
  <n-tabs v-model:value="activeTab" type="card" closable animated @close="handleClose">
    <template #prefix>
      <div></div>
    </template>
    <n-tab
      v-for="tab in tabStore.tabs"
      :closable="true"
      @contextmenu="handleContextMenu($event, tab.path)"
      :name="tab.path"
    >
      <n-icon v-if="tab.icon" class="mr-1" size="16px">
        <HIcon :icon="tab.icon"></HIcon>
      </n-icon>
      {{ tab.query.title || tab.meta.title || tab.path }}
    </n-tab>
    <template #suffix>
      <n-button @click="reloadPage()" :loading="appStore.reloadFlag" text class="mr-5">
        <template #icon>
          <n-icon size="24px">
            <icon-mdi-refresh></icon-mdi-refresh>
          </n-icon>
        </template>
      </n-button>
    </template>
  </n-tabs>
  <n-dropdown
    placement="bottom-start"
    trigger="manual"
    :x="x"
    :y="y"
    :options="options"
    :show="showDropdown"
    @clickoutside="onClickoutside"
    @select="handleSelect"
  />
</template>
<script lang="ts" setup>
import { useAppStore, useTabStore } from '@/store';
import { computed, nextTick, ref } from 'vue';

const appStore = useAppStore();
const tabStore = useTabStore();

const activeTab = computed({
  get: () => tabStore.activeTab,
  set: (val) => {
    tabStore.handleClickTab(val);
  },
});

function handleClose(name: string) {
  tabStore.removeTab(name);
}

function reloadPage() {
  appStore.reloadPage(1000);
}

// 右键菜单
const options = [
  {
    label: '关闭其它',
    key: 'closeOther',
  },
  {
    label: '关闭左侧',
    key: 'closeLeft',
  },
  {
    label: '关闭右侧',
    key: 'closeRight',
  },
  {
    label: '关闭全部',
    key: 'closeAll',
  },
];
const x = ref(0);
const y = ref(0);
const showDropdown = ref(false);
const selectTabPath = ref();

function handleContextMenu(e: MouseEvent, path: string) {
  e.preventDefault();
  showDropdown.value = false;
  selectTabPath.value = path;
  nextTick().then(() => {
    showDropdown.value = true;
    x.value = e.clientX;
    y.value = e.clientY;
  });
}

function handleSelect(key: string | number) {
  if (key === 'closeAll') {
    tabStore.clearTab();
  } else if (key === 'closeLeft') {
    tabStore.clearLeftTab(selectTabPath.value);
  } else if (key === 'closeRight') {
    tabStore.clearRightTab(selectTabPath.value);
  } else if (key === 'closeOther') {
    tabStore.clearTab([selectTabPath.value]);
  }
  showDropdown.value = false;
}

function onClickoutside() {
  showDropdown.value = false;
}
</script>
