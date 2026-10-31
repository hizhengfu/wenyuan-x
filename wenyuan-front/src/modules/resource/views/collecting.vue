<template>
  <ResourcePage fill title="数字收藏"
    ><n-tabs class="resource-fill-tabs" type="line"
      ><n-tab-pane class="resource-fill-pane" name="plans" tab="征集计划"
        ><ResourceBusinessRecords kind="PLAN"
          ><template #detail="{ record, refresh }"
            ><n-divider>领取与发证</n-divider
            ><n-space
              ><n-tag>已发 {{ record.issuedCount }} / {{ record.data.capacity }}</n-tag></n-space
            ><ResourceCertificatesPanel
              :plan-id="record.id"
              :resource-ids="record.data.resourceIds"
              @issued="refresh" /></template></ResourceBusinessRecords></n-tab-pane
      ><n-tab-pane class="resource-fill-pane" name="mine" tab="我的证书"><ResourceCertificatesPanel /></n-tab-pane
      ><n-tab-pane class="resource-fill-pane" name="research" tab="研究图谱"
        ><ResourceCollectingResearch /></n-tab-pane></n-tabs
  ></ResourcePage>
</template>
<script setup lang="ts">
import ResourcePage from '../components/ResourcePage.vue';
import ResourceBusinessRecords from '../components/ResourceBusinessRecords.vue';
import ResourceCertificatesPanel from '../components/ResourceCertificatesPanel.vue';
import ResourceCollectingResearch from '../components/ResourceCollectingResearch.vue';
</script>
