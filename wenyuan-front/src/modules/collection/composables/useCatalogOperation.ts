import { computed, onActivated, onBeforeUnmount, onDeactivated, ref } from 'vue';

/** A page/form owns its async results until it closes, changes target or leaves KeepAlive. */
export function useCatalogOperation(clear: () => void) {
  const active = ref(true);
  const operation = ref('');
  let generation = 0;
  let disposed = false;
  function reset() { generation++; operation.value = ''; clear(); }
  function current(ticket: number) { return !disposed && active.value && generation === ticket; }
  function begin(kind: string) {
    if (disposed || !active.value || operation.value) return null;
    operation.value = kind;
    return ++generation;
  }
  function finish(ticket: number) { if (current(ticket)) operation.value = ''; }
  onDeactivated(() => { active.value = false; reset(); });
  onBeforeUnmount(() => { disposed = true; active.value = false; reset(); });
  onActivated(() => { if (!disposed) active.value = true; });
  return { active, operation, busy: computed(() => !!operation.value), reset, current, begin, finish };
}
