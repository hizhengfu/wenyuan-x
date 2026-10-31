<template>
  <div class="flex row flex-center py-3" v-if="breadcrumbs.length>0">
    <n-breadcrumb>
      <n-breadcrumb-item v-for="breadcrumb in breadcrumbs">
        {{ breadcrumb.title }}
      </n-breadcrumb-item>
    </n-breadcrumb>
    <div class="flex-1"></div>
    <n-button :loading="appStore.reloadFlag" @click="reloadPage()" circle>
      <icon-mdi-refresh class="text-24px"></icon-mdi-refresh>
    </n-button>
  </div>
</template>
<script lang="ts" setup>
import {useAppStore, useAuthStore} from '@/store';
import {useCmsStore} from '../store/cms';
import {getAllParentNodeByParam} from '@/utils/tree';
import {useRoute} from 'vue-router'
import {computed, onMounted} from 'vue';

const appStore = useAppStore()
const authStore = useAuthStore()
const cmsStore = useCmsStore()

function reloadPage() {
  appStore.reloadPage(100)
}

let route = useRoute()

const breadcrumbs = computed(() => {
  if (!route) return []
  let matched = route.matched
  let categoryId = route.query.categoryId
  let breadcrumbs = []
  if (categoryId) {
    let menus = getAllParentNodeByParam(cmsStore.categoryList, categoryId)
    if (menus) {
      menus.forEach((item, index) => {
        let path
        if (categoryId === item.id) {
          path = ''
        } else {
          path = {path: '/publish/' + item.module.toLowerCase(), query: {categoryId: item.id, name: item.name}}
        }

        breadcrumbs.push(
            {
              title: item.name,
              to: path,
              disabled: true,
              clickable: false
            }
        )
      })
    }
  } else {
    let path = route.path
    if (!/^\/error\//.test(path)) {
      matched.forEach((item) => {
        let title = item.meta.title
        let path = item.path.replace(/\//, '')

        if (title) {
          breadcrumbs.push({
            title: title,
            to: item.path,
            clickable: path === item.path
          })
        }
      })
      if (route.path !== '/') {
        breadcrumbs.unshift({title: '首页', icon: 'mdi:home', to: '/'})
      }
    }
  }
  return breadcrumbs

})

</script>
