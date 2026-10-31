<template>
  <div class="main">
    <ProjectGis></ProjectGis>
  </div>
  <ChartCard class="left1 left" title="遗址分析"></ChartCard>
  <ChartCard class="left2 left" title="保存状态分析" dataType="5"></ChartCard>
  <ChartCard class="left3 left" title="出库分析" dataType="7"></ChartCard>
  <ChartCard class="right1 right" title="时代分布" dataType="6"></ChartCard>
  <ChartCard class="right2 right" title="材质分析" dataType="3"></ChartCard>
  <ChartCard class="right3 right" title="出土年度" dataType="4"></ChartCard>
</template>
<script lang="ts" setup>
import 'vue-leaflet-markercluster/dist/style.css';
import ChartCard from '../../components/widgets/ChartCard.vue';
import ProjectGis from '../../components/widgets/ProjectGis.vue';
</script>
<style lang="scss" scoped>
.main {
  top: 120px;
  left: 540px;
  right: 540px;
  bottom: 30px;
}

.left {
  left: 30px;
  width: 500px;
}

.left1 {
  top: 120px;
  height: 300px;
}

.left2 {
  top: 435px;
  height: 300px;
}

.left3 {
  top: 750px;
  height: 300px;
}

.right {
  right: 30px;
  width: 500px;
}

.right1 {
  top: 120px;
  height: 300px;
}

.right2 {
  top: 435px;
  height: 300px;
}

.right3 {
  top: 750px;
  height: 300px;
}
</style>
