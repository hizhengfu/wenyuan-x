import { defineAsyncComponent } from 'vue';
export default { key: 'product', component: defineAsyncComponent(() => import('../components/module/product.vue')) };
