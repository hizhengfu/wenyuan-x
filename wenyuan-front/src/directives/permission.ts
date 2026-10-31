import type { App } from 'vue';
import { useAuthStore } from '@/store';

/**
 *
 * 权限判断指令
 * 使用示例 <div v-permission="DICT_ALL">下载</div>
 *
 * */
export default {
  install(app: App) {
    app.directive('permission', {
      mounted(el, binding) {
        const authStore = useAuthStore();
        const { value } = binding;

        // 检查权限
        const hasPermission = authStore.hasPermission(value);

        if (!hasPermission) {
          // 没有权限，从DOM中移除元素
          el.parentNode && el.parentNode.removeChild(el);
        }
      },
    });
  },
};
