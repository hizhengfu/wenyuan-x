import { defineAsyncComponent } from 'vue';
export default { key: 'page', component: defineAsyncComponent(() => import('../components/module/page.vue')) };
