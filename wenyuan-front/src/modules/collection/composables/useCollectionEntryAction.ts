import { nextTick, onActivated, onBeforeUnmount, onDeactivated, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

/** A workbench entry opens once, after refs mount, and is removed from cached page queries. */
export function useCollectionEntryAction(path: string, action: string, allowed: () => boolean, open: () => void) {
  const route = useRoute(), router = useRouter();
  let active = true, generation = 0;
  async function apply() {
    const ticket = ++generation;
    if (!active || route.path !== path || route.query.entryAction !== action) return;
    await nextTick();
    if (!active || ticket !== generation || route.path !== path || route.query.entryAction !== action) return;
    if (allowed()) open();
    const { entryAction: _, ...query } = route.query;
    await router.replace({ path: route.path, query, hash: route.hash });
  }
  watch(() => route.fullPath, apply, { immediate: true, flush: 'post' });
  onActivated(() => { active = true; void apply(); });
  onDeactivated(() => { active = false; generation++; });
  onBeforeUnmount(() => { active = false; generation++; });
}
