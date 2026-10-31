import type { App } from 'vue';
import { useAuthStore } from '@/store';

/**
 *
 * 角色指令
 * 使用示例 <div v-role="admin">xxx</div>
 *
 * */
export default {
  install(app: App) {
    app.directive('role', {
      mounted(el, binding) {
        const authStore = useAuthStore();
        const { value } = binding;

        // 检查权限
        const hasRoleId = authStore.hasRoleId(value);

        if (!hasRoleId) {
          // 没有权限，从DOM中移除元素
          el.parentNode && el.parentNode.removeChild(el);
        }
      },
    });
  },
};
