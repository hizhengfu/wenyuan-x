<template>
  <BorderBox7 style="padding: 5px; box-sizing: border-box" :color="['#3181F6']">
    <LeafletTianditu :center="center" :zoom="6">
      <l-control-layers position="topright"></l-control-layers>
      <l-control-zoom position="bottomright"></l-control-zoom>
      <LMarkerClusterGroup>
        <l-marker v-for="marker in markers" :lat-lng="[marker.lat, marker.lng]" @click="handleClickMarker(marker)">
          <l-tooltip>
            <div>
              <p>名称: {{ marker.name }}</p>
              <p>编号: {{ marker.code }}</p>
              <p v-if="marker.address">地址: {{ marker.address }}</p>
            </div>
          </l-tooltip>
        </l-marker>
      </LMarkerClusterGroup>
      <HDialogViewForm v-model:show="dialog" :title="dialogTitle" width="60%" size="small">
        <ProjectDetail :id="activeProjectId"></ProjectDetail>
      </HDialogViewForm>
    </LeafletTianditu>
  </BorderBox7>
</template>
<script lang="ts" setup>
import { ref } from 'vue';
import { BorderBox7 } from '@kjgl77/datav-vue3';
import LeafletTianditu from '@/components/basic/leaflet/LeafletTianditu.vue';
import { LControlLayers, LControlZoom, LMarker, LTooltip } from '@vue-leaflet/vue-leaflet';
import { useLeafletStore } from '@/components/basic/leaflet/useLeaflet.ts';

import { list } from '../../api/project.ts';
import 'vue-leaflet-markercluster/dist/style.css';
import { LMarkerClusterGroup } from 'vue-leaflet-markercluster';
import ProjectDetail from '../ProjectDetail.vue';

const useLeaflet = useLeafletStore();
const center = [34.340913010016614, 108.86798225629616];

const markers = ref<any[]>([]);

const dialog = ref(false);
const activeProjectId = ref();
const dialogTitle = ref();

/**
 * 数据加载
 * */
function fetchData() {
  markers.value = [];
  list({ pageSize: -1 }).then((resp: any) => {
    const bounds: any[] = [];
    if (resp.data.list) {
      markers.value = resp.data.list.filter((item: any) => {
        let latlng = item.latlng;

        if (latlng) {
          bounds.push([item.lat, item.lng]);
        }

        return !!latlng;
      });
      useLeaflet.fitBounds(bounds);
    }
  });
}

function handleClickMarker(marker: any) {
  activeProjectId.value = marker.id;
  dialogTitle.value = marker.name;
  dialog.value = true;
}

fetchData();
</script>
