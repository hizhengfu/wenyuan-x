import { defineAsyncComponent } from 'vue';
export default { key: 'link', component: defineAsyncComponent(() => import('../components/module/link.vue')) };
