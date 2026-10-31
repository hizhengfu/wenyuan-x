import { App } from 'vue/dist/vue';
import FcDesigner from './form-designer.js';
import FormCreate from '@form-create/naive-ui';
import install from './auto-import';

FormCreate.use(install);

export function setupFormCreate(app: App) {
  app.use(FormCreate);
  //@ts-ignore
  app.use(FcDesigner);
  // app.use(FcDesigner.formCreate);
}
