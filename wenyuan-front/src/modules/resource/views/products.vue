<template>
  <ResourcePage fill title="文创产品"
    ><n-tabs class="resource-fill-tabs" type="line"
      ><n-tab-pane class="resource-fill-pane" name="products" tab="产品台账"
        ><ResourceBusinessRecords kind="PRODUCT" @changed="revision++"
          ><template #detail="{ record }"
            ><n-tabs type="line"
              ><n-tab-pane name="sales" tab="销售记录"
                ><ResourceSalesPanel :product-id="record.id" @changed="revision++" /></n-tab-pane
              ><n-tab-pane name="rights" tab="厂商授权与知识产权"
                ><ResourceBusinessRecords
                  kind="LICENSE"
                  :parent-id="record.id" /></n-tab-pane></n-tabs></template></ResourceBusinessRecords></n-tab-pane
      ><n-tab-pane class="resource-scroll-pane" name="insights" tab="销售分析"
        ><ResourceProductInsights :revision="revision" /></n-tab-pane></n-tabs
  ></ResourcePage>
</template>
<script setup lang="ts">
import ResourcePage from '../components/ResourcePage.vue';
import { ref } from 'vue';
import ResourceBusinessRecords from '../components/ResourceBusinessRecords.vue';
import ResourceSalesPanel from '../components/ResourceSalesPanel.vue';
import ResourceProductInsights from '../components/ResourceProductInsights.vue';
const revision = ref(0);
</script>
