<template>
  <div>
    {{ data }}
  </div>
</template>

<script lang="ts" setup>
import Websocket from '@/utils/websocket';
import {onMounted, onUnmounted, ref} from 'vue';

const ws = new Websocket({destinationPath: '', subscribePath: '/topic/voice'})

const data = ref()

onMounted(() => {
  ws.connection((response: any) => {
    console.log('ws', response)
    data.value = response
  })
})
onUnmounted(() => {
  ws.disconnect()
})
</script>
