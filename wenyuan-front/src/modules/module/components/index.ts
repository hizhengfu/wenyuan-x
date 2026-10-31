//注册ui库插件
import { App } from 'vue';
import FormCreate from '@/components/ui/naive';
import install from '@/components/ui/naive/auto-import';
import FcDesigner from './design/form/index';
import { NForm } from 'naive-ui';

/*const naive = create({
  components: [NInput, NForm, NRadio, NRadioGroup, NButton]
})*/

FormCreate.use(install);

/**
 * 注册表单渲染组件
 * */
export function setupFormRender(app: App) {
  app.use(FormCreate);
  //@ts-ignore
  app.use(FcDesigner);
}
