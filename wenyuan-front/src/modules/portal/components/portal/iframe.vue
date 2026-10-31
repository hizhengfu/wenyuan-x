<template>
  <iframe v-if="iframeUrl"
          class="iframe"
          :src="iframeUrl"
          style="border: 0;"
          @load="iframeLoad"></iframe>
</template>
<script lang="ts" setup>
import {computed} from "vue";

const props = defineProps({
  url: {type: String}
})

const emits = defineEmits(['load'])


const iframeUrl = computed(() => {
  return props.url
})

function iframeLoad() {
  emits('load')
}
</script>
