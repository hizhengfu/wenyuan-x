<template>
  <ResourcePage fill title="文化景观"
    ><ResourceBusinessRecords kind="SCENE"
      ><template #detail="{ record }"
        ><n-tabs type="line"
          ><n-tab-pane name="captures" tab="采集记录"
            ><ResourceBusinessRecords kind="CAPTURE" :parent-id="record.id" @changed="revision++" /></n-tab-pane
          ><n-tab-pane name="timeline" tab="时间线"
            ><ResourceLandscapeTimeline
              :scene-id="record.id"
              :revision="revision" /></n-tab-pane></n-tabs></template></ResourceBusinessRecords
  ></ResourcePage>
</template>
<script setup lang="ts">
import ResourcePage from '../components/ResourcePage.vue';
import { ref } from 'vue';
import ResourceBusinessRecords from '../components/ResourceBusinessRecords.vue';
import ResourceLandscapeTimeline from '../components/ResourceLandscapeTimeline.vue';
const revision = ref(0);
</script>
