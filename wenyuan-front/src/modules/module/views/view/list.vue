<template>
  <module-list :table-id="tableId"></module-list>
</template>
<script lang="ts" setup>
import ModuleList from '../../components/ModuleList.vue'
import {useRoute} from 'vue-router'
import {computed, onActivated, onDeactivated, ref} from "vue";

const $route = useRoute();
const tableId = ref();

onActivated(() => {
  tableId.value = $route.query.tableId
})
</script>
