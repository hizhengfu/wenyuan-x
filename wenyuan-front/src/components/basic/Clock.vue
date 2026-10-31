<template>
  <div class="clock" v-bind="$attrs">
    <span class="date">
      {{ clock.format(format) }}
    </span>
    <!-- <span class="hour">
      {{ clock.format('HH:mm:ss') }}
    </span> -->
  </div>
</template>
<script lang="ts" setup>
import dayjs from 'dayjs';
import { onUnmounted, ref } from 'vue';

const props = defineProps({
  format: { type: String, default: 'YYYY年MM月DD日 dddd HH:mm:ss' },
});

const clock = ref(dayjs());

const clockHandle = window.setInterval(() => {
  clock.value = dayjs();
}, 1000);

onUnmounted(() => {
  window.clearInterval(clockHandle);
});
</script>
<style lang="scss" scoped>
.clock {
  display: flex;
  align-items: center;
  font-variant-numeric: tabular-nums;
  font-feature-settings: 'tnum';

  .dateTime {
    display: inline-block;
    margin-right: 5px;
  }
}
</style>
