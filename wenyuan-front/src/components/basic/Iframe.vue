<template>
  <div class="iframe-wrap">
    <iframe :src="url" frameborder="0" @load="handleLoad"></iframe>
  </div>
</template>
<script lang="ts" setup>
import {ref} from 'vue'

const props = defineProps({
  url: {type: String},//地址
})

const refShow = ref(true)

function handleLoad() {
  refShow.value = false
}
</script>
<style lang="scss" scoped>
.iframe-wrap {
  iframe {
    position: absolute;
    top: 0;
    bottom: 0;
    left: 0;
    right: 0;
    height: 100%;
    width: 100%;
  }
}

</style>
