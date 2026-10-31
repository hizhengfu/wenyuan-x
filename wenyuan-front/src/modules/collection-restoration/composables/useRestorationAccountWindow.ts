import { watch } from 'vue';
import { useAuthStore } from '@/store/modules/auth';

/** Close the original account's records and callbacks before another account can use them. */
export function useRestorationAccountWindow(clear: () => void) {
  const auth = useAuthStore();
  watch(() => auth.userInfo?.id, clear, { flush: 'sync' });
}
