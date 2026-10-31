<template>
  <div class="header">
    <HClock class="left"></HClock>
    <div class="title">田野考古库房文物可视化监管</div>
    <div class="right">
      <AppUserInfo></AppUserInfo>
    </div>
  </div>
</template>
<script setup lang="ts">
import HClock from '@/components/basic/Clock.vue';
import AppUserInfo from '@/components/app/AppUserInfo.vue';
</script>
<style lang="scss" scoped>
.header {
  width: 100%;
  height: 100px;
  background-image: url('../../assets/image/header.png');
  background-size: 100%;
  color: #ffffff;

  .title {
    margin-top: 25px;
    text-align: center;
    font-size: 32px;
    font-family: 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif;
  }

  .left {
    position: absolute;
    left: 160px;
    top: 30px;
    font-size: 18px;
  }

  .right {
    position: absolute;
    right: 160px;
    top: 30px;
    font-size: 18px;
  }
}
</style>
