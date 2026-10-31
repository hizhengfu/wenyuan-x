import type {App} from 'vue';
import {createPinia} from 'pinia';
import { useAuthStore } from './modules/auth';

export function setupStore(app: App) {
  const store = createPinia();
  app.use(store);
  const stopSharedIdentitySync = useAuthStore(store).startSharedIdentitySynchronization();
  app.onUnmount(stopSharedIdentitySync);
}

export * from './modules';
