import { defineAsyncComponent } from 'vue';
import { registerFlowForm } from '../flow/formRegistryCore';

// 流程注册表会在应用启动时扫描所有业务模块。这里使用异步组件，避免卫星遥感等
// 非资源页面提前加载 ResourcePreview 及其 Office 预览依赖。
const ResourceFlowForm = defineAsyncComponent(() => import('./forms/ResourceFlowForm.vue'));

registerFlowForm('RESOURCE_IMPORT', ResourceFlowForm);
registerFlowForm('RESOURCE_ACCESS_GRANT', ResourceFlowForm);

registerFlowForm('RESOURCE_BUSINESS_REVIEW', defineAsyncComponent(async () => (await import('./forms/ResourceBusinessFlowForm.vue')).default));
