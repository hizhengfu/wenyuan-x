<template>
  <div>
    <header style="margin-top: 50px">
      <div style="display: flex; justify-content: space-between; margin-bottom: 20px">
        <div style="font-size: 20px; font-weight: bold">
          档案编号：<span>{{ fourCoverData.code }}</span>
        </div>
        <div style="font-size: 20px; font-weight: bold">
          <h-dict-radio :value="fourCoverData.category" type="fourth_cultural_archives_type"></h-dict-radio>
        </div>
      </div>

      <div class="text-center" style="font-size: 25px; font-weight: bold">第四次全国文物普查不可移动文物登记表</div>
      <div class="text-center" style="font-size: 25px; font-weight: bold; margin-top: 10px">
        <h-dict-text v-model:value="fourCoverData.type" type="fourth_cultural_relics_type_one"></h-dict-text>
      </div>
    </header>
    <div class="text-center" style="margin-top: 160px"></div>
    <main class="flex-center">
      <div style="margin-top: 100px">
        <div class="form-row">
          <span class="label">名称</span>

          <span class="underlines">{{ fourCoverData.name }}</span>
        </div>
        <div class="form-row">
          <span class="label">省(自治区、直辖市)</span>
          <span class="underlines">
            <template v-if="isNumber(fourCoverData.province)">
              <CascadeAreaName v-model:value="fourCoverData.province"></CascadeAreaName>
            </template>
            <template v-else>
              {{ fourCoverData.province }}
            </template>
          </span>
        </div>
        <div class="form-row">
          <span class="label">市(地区、州、盟)</span>
          <span class="underlines">
            <template v-if="isNumber(fourCoverData.city)">
              <CascadeAreaName v-model:value="fourCoverData.city"></CascadeAreaName>
            </template>
            <template v-else>
              {{ fourCoverData.city }}
            </template>
          </span>
        </div>
        <div class="form-row">
          <span class="label">县(区、市、旗)</span>
          <span class="underlines">
            <template v-if="isNumber(fourCoverData.county)">
              <CascadeAreaName v-model:value="fourCoverData.county"></CascadeAreaName>
            </template>
            <template v-else>
              {{ fourCoverData.county }}
            </template>
          </span>
        </div>
        <div class="form-row">
          <span class="label">调查人(签字)</span>

          <div class="dynamic-row">
            <span class="underlines small">{{ fourCoverData.investigaBy }}</span>
            <span class="label">日期</span>
            <span class="underlines small">{{
              fourCoverData.investigaTime ? dayjs(fourCoverData.investigaTime).format('YYYY-MM-DD') : ''
            }}</span>
          </div>
        </div>
        <div class="form-row">
          <span class="label">审定人(签字)</span>

          <div class="dynamic-row">
            <span class="underlines small">{{ fourCoverData.reviewBy }}</span>
            <span class="label" style="text-align: left">日期</span>
            <span class="underlines small">{{
              fourCoverData.reviewTime ? dayjs(fourCoverData.reviewTime).format('YYYY-MM-DD') : ''
            }}</span>
          </div>
        </div>
        <div class="form-row">
          <span class="label">抽查人(签字)</span>

          <div class="dynamic-row">
            <span class="underlines small">{{ fourCoverData.checkBy }}</span>
            <span class="label" style="text-align: left">日期</span>
            <span class="underlines small">{{
              fourCoverData.checkTime ? dayjs(fourCoverData.checkTime).format('YYYY-MM-DD') : ''
            }}</span>
          </div>
        </div>
      </div>
    </main>
    <footer style="display: flex; margin-top: 150px; margin-bottom: 25px; justify-content: center">
      <span style="font-size: 25px; font-weight: bold; margin-right: 30px">第四次全国文物普查领导小组办公室</span>
      <span style="font-size: 25px; font-weight: bold">制</span>
    </footer>
  </div>
</template>
<script lang="ts" setup>
import { get } from '../../../api/fourth/fourthCulturalRelicCover';
import CascadeAreaName from '@/modules/sys/components/form/field/CascadeAreaName.vue';
import { ref, onMounted, watch } from 'vue';
import dayjs from 'dayjs';

const emits = defineEmits(['heritageType']);
const props = defineProps({
  fourthCulturalRecordId: { type: String },
});

const fourCoverData = ref({
  name: '',
  category: '',
  code: '',
  investigaBy: '',
  reviewBy: '',
  checkBy: '',
  investigaTime: '',
  reviewTime: '',
  checkTime: '',
  type: '',
  province: '',
  county: '',
  city: '',
});

function isNumber(value: any) {
  return !isNaN(value) && !isNaN(parseFloat(value));
}

function handleCoverData(id: string) {
  get(id).then((res) => {
    if (res.data) {
      fourCoverData.value = res.data;
      emits('heritageType', fourCoverData.value.type);
    }
  });
}

watch(
  () => props.fourthCulturalRecordId,
  (newValue) => {
    if (newValue) {
      console.log(newValue, '000000000000000000');
      handleCoverData(newValue);
    }
  },
  { deep: true, immediate: true },
);
</script>
<style scoped lang="scss">
.form-row {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
  width: 100%;
}

.label {
  font-size: 22px;
  font-weight: bold;
  flex-shrink: 0;
  padding-right: 20px;
  white-space: nowrap;
}

.underlines {
  flex: 1 0 200px;
  position: relative;
  padding: 0 10px;
  min-width: 200px;
  height: 20px;
  font-size: 18px;
  text-align: center;
}

.underlines::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -5px;
  width: 100%;
  height: 2px;
  background: #000;
}

/* 短下划线特殊处理 */
.underlines.small {
  flex-basis: auto;
  min-width: 180px;
  margin-top: 6px;
  // height: 35px;
}

.underlines.small::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -8px;
  width: 100%;
  height: 2px;
  background: #000;
}

/* 处理多字段行 */
.dynamic-row {
  display: flex;
  gap: 15px;
  width: 100%;

  > * {
    flex: 1;

    &:last-child {
      flex: 0.8;
    }
  }
}
</style>
