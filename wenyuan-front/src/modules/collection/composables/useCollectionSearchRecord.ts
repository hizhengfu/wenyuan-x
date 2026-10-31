import { nextTick, onActivated, onBeforeUnmount, onDeactivated, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { useAuthStore } from '@/store';

/** Consume a search result identifier after mounted refs can reread the formal record. */
export function useCollectionSearchRecord(
  path: string,
  type: string,
  open: (id: string, current: () => boolean) => void | Promise<unknown>,
) {
  const route = useRoute(),
    router = useRouter(),
    auth = useAuthStore(),
    message = useMessage();
  let active = true,
    generation = 0;
  const identity = () => `${auth.userInfo?.id}:${auth.identityRevision}:${auth.identitySyncing}`;
  watch(identity, () => { generation++; }, { flush: 'sync' });
  async function apply() {
    const ticket = ++generation, account = identity();
    const id = typeof route.query.recordId === 'string' ? route.query.recordId.trim() : '';
    if (!active || route.path !== path || route.query.recordType !== type || !id) return;
    await nextTick();
    if (!active || ticket !== generation || route.path !== path || route.query.recordId !== id) return;
    if (auth.hasPermission('COLLECTION_SELECT')) {
      const current = () => active && ticket === generation && account === identity() && !auth.identitySyncing && auth.hasPermission('COLLECTION_SELECT') && route.path === path && route.query.recordId === id;
      try {
        await open(id, current);
      } catch (error: any) {
        if (current()) message.error(error?.message || '记录读取失败，请重新检索');
      }
    }
    if (!active || ticket !== generation || route.query.recordId !== id) return;
    const { recordId: _, recordType: __, ...query } = route.query;
    await router.replace({ path, query, hash: route.hash });
  }
  watch(() => route.fullPath, apply, { immediate: true, flush: 'post' });
  onActivated(() => {
    active = true;
    void apply();
  });
  onDeactivated(() => {
    active = false;
    generation++;
  });
  onBeforeUnmount(() => {
    active = false;
    generation++;
  });
}
