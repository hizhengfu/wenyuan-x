<template>
  <div style="width: 100%;height:100%;flex: 1;display:flex;flex-direction: row;">
    <div style="display: flex;flex-direction: column;flex:1;margin-right: 5px;">
      <h3>{{ codeContent?.apiFileName }}</h3>
      <textarea style="height: 80%;width: 100%;">
      {{codeContent?.apiContent}}
    </textarea>
    </div>
    <div style="display: flex;flex-direction: column;flex:1;margin-left: 5px;">
      <h3>{{ codeContent?.viewFileName }}</h3>
      <textarea style="height: 80%;width: 100%;">
      {{codeContent?.viewContent}}
    </textarea>
    </div>
  </div>
</template>
<script lang="ts" setup>
import {code} from '../../api/moduleTable'
import {useRoute} from "vue-router";
import {computed, onActivated, onDeactivated, ref, watchEffect} from "vue";
import {ModuleGenCode} from "@/modules/module/typings/module";

const route = useRoute()

const tableId = computed<string>(() => {
  return route.query.id as string
})

const codeContent = ref<ModuleGenCode>()

function load() {
  if (tableId.value) {
    code(tableId.value, {}).then((res) => {
      codeContent.value = res.data
    })
  }
}

onActivated(() => {
  load()
})


</script>
