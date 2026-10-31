<template>
  <n-select v-bind="$attrs" :options="options" label-field="name" value-field="id"/>
</template>
<script lang="ts" setup>
import {ref, watchEffect} from "vue";
import {list} from '../../../api/tour'

const props = defineProps({
  roomId: {type: String}//展馆id
})

const options = ref()

watchEffect(() => {
  list({pageSize: -1, roomId: props.roomId}).then((res) => {
    if (res.data) {
      options.value = res.data.list || []
    }
  })
})
</script>
