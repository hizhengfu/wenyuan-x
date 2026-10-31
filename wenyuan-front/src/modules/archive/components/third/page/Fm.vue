<template>
  <div>
    <header>
      <div style="margin-top: 50px;display: flex;justify-content: space-between;padding: 0 50px;">
        <span style="font-size: 20px; font-weight: bold">编号：{{ thirdCoverData.code }}</span>
        <div style="font-size: 20px; font-weight: bold; margin-top: 10px">
          <span>{{ thirdCoverData.type !== '1' ? '⚫' : '⚪' }}</span>复查
          <span class="ml-10">{{ thirdCoverData.type === '1' ? '⚫' : '⚪' }}</span>新发现
        </div>
      </div>
      <div class="text-center mt-30">
        <div style="font-size: 35px; font-weight: bold">第三次全国文物普查不可移动文物登记表</div>
      </div>
    </header>
    <main class="flex-center">
      <div style="margin-top: 300px">
        <div class="text-box">
          <span class="text-title">名称</span>
          <span class="text-content">{{ thirdCoverData.name || '--' }}</span>
        </div>
        <div style="display: flex; margin-bottom: 15px;">
          <span class="text-title">省(自治区、直辖市)</span>
          <span class="text-content">{{ thirdCoverData.province || '--' }}</span>
        </div>
        <div class="text-box">
          <span class="text-title">市(地区、州、盟)</span>
          <span class="text-content">{{ thirdCoverData.city || '--' }}</span>
        </div>
        <div class="text-box">
          <span class="text-title">县(区、市、旗)</span>
          <span class="text-content">{{ thirdCoverData.county || '--' }}</span>
        </div>
        <div class="text-box">
          <span class="text-title">调查人(签字)</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.investigaBy || '--' }}</span>
          <span class="text-title">日期</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.investigaTime ? dayjs(thirdCoverData.investigaTime).format('YYYY-MM-DD') : '--' }}</span>
        </div>
        <div class="text-box">
          <span class="text-title">审定人(签字)</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.reviewBy || '--' }}</span>
          <span class="text-title">日期</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.reviewTime ? dayjs(thirdCoverData.reviewTime).format('YYYY-MM-DD') : '--' }}</span>
        </div>
        <div class="text-box">
          <span class="text-title">抽查人(签字)</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.checkBy || '--' }}</span>
          <span class="text-title">日期</span>
          <span class="text-content" style="width: 170px;margin-right: 20px;">
            {{ thirdCoverData.checkTime ? dayjs(thirdCoverData.checkTime).format('YYYY-MM-DD') : '--' }}</span>
        </div>
      </div>
    </main>
    <footer
      style="display: flex; margin-top: 150px; margin-bottom: 25px; justify-content: center;font-size: 20px;font-weight: bold;">
      <span>国家文物局&nbsp&nbsp&nbsp制</span>
    </footer>
  </div>
</template>
<script lang="ts" setup>
import { get } from '../../../api/third/thirdCulturalRelicCover';
import { useRoute } from 'vue-router';
import { ref, onMounted } from 'vue';
import dayjs from 'dayjs';

const props = defineProps({
  fourCulturalRecordId: { type: String },
  thirdCulturalRecordId: { type: String },
  addPermission: { type: String },
  //修改权限
  editPermission: { type: String },
  //删除权限
  deletePermission: { type: String },
});

const route = useRoute();
const thirdCoverData = ref({
  type: '',
  name: '',
  code: '',
  province: '',
  city: '',
  county: '',
  areaId: '',
  groupId: '',
  investigaBy: '',
  investigaTime: null,
  reviewBy: '',
  reviewTime: null,
  checkBy: '',
  checkTime: null,
});

onMounted(() => {
  handleCoverData((route.query.thirdCulturalRecordId || props.thirdCulturalRecordId) as any);
});

function handleCoverData(id: string) {
  get(id).then((res) => {
    if (res.data) {
      thirdCoverData.value = res.data;
    }
  });
}
</script>

<style lang="scss" scoped>
.text-box {
  display: flex;
  margin-bottom: 15px;
}

.text-title {
  font-size: 25px;
  font-weight: bold;
  margin-right: 15px;
}

.text-content {
  text-decoration: underline;
  font-size: 25px;
}
</style>
