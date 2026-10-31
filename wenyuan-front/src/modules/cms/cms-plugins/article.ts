import { defineAsyncComponent } from 'vue';
export default { key: 'article', component: defineAsyncComponent(() => import('../components/module/article.vue')) };
