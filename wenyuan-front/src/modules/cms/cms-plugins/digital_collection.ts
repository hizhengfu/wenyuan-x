import { defineAsyncComponent } from 'vue';
export default { key: 'digital_collection', component: defineAsyncComponent(() => import('../components/module/digitalCollection.vue')) };
