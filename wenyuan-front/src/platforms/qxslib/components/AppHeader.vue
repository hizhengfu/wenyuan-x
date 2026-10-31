<template>
  <div class="header_warp">
    <div class="header">
      <div class="header_left">
        <slot name="header_left"></slot>
      </div>
      <div class="header_title">
        <!--        钱学森图书馆智慧现场服务监控管理系统-->
        <img src="../assets/image/title.png" height="50">
      </div>
      <div class="header_right">
        <slot name="header_right"></slot>
      </div>
    </div>
  </div>
</template>
<style>
.header_warp {
  /*background-image: url("../assets/image/header_bg.png");*/
  background-repeat: repeat-x;
  background-position: top;
  user-select: none;
}

.header {
  height: 72px;
  background-image: url("../assets/image/header_center.png");
  background-position: center;
  background-repeat: no-repeat;
  display: flex;
  flex-direction: row;
  color: #ffffff;
  justify-content: center;
  font-size: 18px;
}

.header_left, .header_right {
  position: absolute;
  display: flex;
  align-items: center;
  height: 53px;
}

.header_left {
  padding-left: 12px;
  left: 0;
  top: 0;
}

.header_right {
  padding-right: 12px;
  right: 0;
  top: 0;
}

.header_title {
  display: flex;
  flex: 1;
  justify-content: center;
}

.header_title img {
  display: inline-block;
  margin-top: 12px;
}
</style>
