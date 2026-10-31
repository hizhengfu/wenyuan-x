<template>

</template>
<script lang="ts" setup>
import {watchEffect} from 'vue';

const props = defineProps(['text'])

const instance = new SpeechSynthesisUtterance();

watchEffect(() => {
  instance.text = props.text
})

/**
 * 播放
 * */
function play() {
  speechSynthesis.speak(instance)
}

/**
 * 暂停
 * */
function pause() {
  speechSynthesis.pause()
}

/**
 * 取消
 * */
function cancel() {
  speechSynthesis.cancel()
}

</script>
