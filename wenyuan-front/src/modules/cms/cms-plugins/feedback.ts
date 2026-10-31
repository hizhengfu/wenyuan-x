import { defineAsyncComponent } from 'vue';
export default { key: 'feedback', component: defineAsyncComponent(() => import('../components/module/feedback/list.vue')) };
