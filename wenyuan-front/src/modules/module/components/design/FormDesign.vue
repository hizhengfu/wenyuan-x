<template>
  <fc-designer ref="designer">
    <template #toolbar-right>
      <n-button type="info" plain size="small" @click="saveForm">
        <n-icon>
          <IconMdiContentSave></IconMdiContentSave>
        </n-icon>
        保存
      </n-button>
    </template>
  </fc-designer>
</template>
<script setup>
import {ref} from 'vue'

const designer = ref()

function saveForm() {
  console.log(designer.value.getJson())
}

</script>
