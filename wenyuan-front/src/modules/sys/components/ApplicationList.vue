<template>
  <n-menu
    ref="menuInstRef"
    label-field="name"
    key-field="id"
    v-bind="$attrs"
    accordion
    :options="menus"
  />
</template>
<script lang="ts" setup>
import {list} from '../api/application'
import {ref} from "vue";

const menus = ref()

function fetchList() {
  list({pageSize: -1}).then((resp) => {
    if (resp.data && resp.data) {
      menus.value = resp.data.list || []
    }
  })
}

fetchList()
</script>
