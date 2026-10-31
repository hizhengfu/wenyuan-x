<template>
  <div>
    <header class="text-center" style="margin-top: 50px">
      <div style="font-size: 35px; font-weight: bold">中华人民共和国</div>
      <span style="font-size: 35px; font-weight: bold; margin-top: 10px">全国重点文物保护单位记录档案</span>
    </header>
    <div class="text-center" style="margin-top: 160px">
      <img src="../../../assets/images/emblem.png" height="250px" width="250px" />
    </div>
    <main class="flex-center">
      <div style="margin-top: 100px">
        <div style="display: flex; margin-bottom: 5px">
          <span style="font-size: 25px; font-weight: bold; margin-right: 20px">案卷题名</span>
          <span style="font-size: 25px; font-weight: bold" class="underline">{{ fourCoverData.name }}</span>
        </div>
        <div style="display: flex; margin-bottom: 5px">
          <span style="font-size: 25px; font-weight: bold; margin-right: 20px">文物保护单位名称</span>
          <span style="font-size: 25px; font-weight: bold" class="underline">{{ fourCoverData.bhdw }}</span>
        </div>
        <div style="display: flex; margin-bottom: 5px">
          <span style="font-size: 25px; font-weight: bold; margin-right: 20px">立卷日期</span>
          <span style="font-size: 25px; font-weight: bold" class="underline">{{ fourCoverData.ljrq }}</span>
        </div>
      </div>
    </main>
    <footer style="display: flex; margin-top: 150px; margin-bottom: 25px; justify-content: center">
      <span style="font-size: 35px; font-weight: bold; margin-right: 30px">国家文物局</span>
      <span style="font-size: 35px; font-weight: bold">监制</span>
    </footer>
  </div>
</template>
<script lang="ts" setup>
import { get } from '../../../api/four/fourCulturalRecord';
import { useRoute } from 'vue-router';
import { ref, onMounted } from 'vue';

const props = defineProps({
  fourCulturalRecordId: { type: String },
  addPermission: { type: String },
  //修改权限
  editPermission: { type: String },
  //删除权限
  deletePermission: { type: String },
});

const route = useRoute();
const fourCoverData = ref({
  name: '',
  bhdw: '',
  ljrq: '',
});

onMounted(() => {
  const { fourCulturalRecordId } = props;
  if (fourCulturalRecordId) {
    handleCoverData(fourCulturalRecordId);
  }
});

function handleCoverData(id: string) {
  get(id).then((res) => {
    if (res.data) {
      fourCoverData.value = res.data;
    }
  });
}
</script>
