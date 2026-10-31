import { defineAsyncComponent } from 'vue';
export default { key: 'project', component: defineAsyncComponent(() => import('../components/module/project.vue')) };
