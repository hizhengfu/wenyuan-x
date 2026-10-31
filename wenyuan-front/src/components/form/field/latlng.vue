<template>
  <n-input-group>
    <n-input ref="refInput" v-model:value="marker" :placeholder="placeholder" />
    <LeafletTiandituSearch
      v-if="!refInput?.mergedDisabled"
      :specify="specify"
      :token="token"
      v-model:value="marker"
      @mapReady="handleMapReady"
      @update:value="handleSelect"
    ></LeafletTiandituSearch>
  </n-input-group>
</template>
<script lang="ts" setup>
import LeafletTiandituSearch from '../../basic/leaflet/LeafletTiandituSearch.vue';
import { computed, ref, watchEffect } from 'vue';

const emits = defineEmits(['update:value', 'mapReady']);
const props = defineProps({
  value: { type: String },
  specify: { type: String },
  token: { type: String, default: '6a555e89f2a149a3d2158b3210f348c3' },
  placeholder: { type: String, default: '请输入经纬度' },
});

const refInput = ref();
const marker = ref();

function handleSelect(value: string) {
  emits('update:value', value);
}

/**
 * 地图加载完成
 * */
function handleMapReady(instance: any) {
  emits('mapReady', instance);
  instance.on('click', (e: any) => {
    const latlng = e.latlng;
    if (!props.value && latlng) {
      emits('update:value', `${latlng.lat},${latlng.lng}`);
    }
  });
}

watchEffect(() => {
  if (props.value) {
    marker.value = props.value;
  }
});
</script>
