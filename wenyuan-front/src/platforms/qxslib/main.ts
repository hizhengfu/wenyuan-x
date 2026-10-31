import {setupAssets, setupComponents} from '@/plugins';
import {setupRouter} from '@/router';
import {createApp} from 'vue'
import provider from '@/components/app/NaiveProvider.vue'
import route from './views/route'
import {setupStore} from '@/store';
import './assets/css/style.css'

async function setupApp() {
  // import assets: js、css
  setupAssets();

  const app = createApp(provider);

  //注册通用组件
  setupComponents(app)
  //注册状态管理
  setupStore(app)
  //注册路由
  // @ts-ignore
  await setupRouter(app, route)

  // mount app
  app.mount('#app');
}

setupApp();
