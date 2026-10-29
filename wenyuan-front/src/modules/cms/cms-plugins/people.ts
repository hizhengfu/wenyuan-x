import { defineAsyncComponent } from 'vue';
export default { key: 'people', component: defineAsyncComponent(() => import('../components/module/people.vue')) };
