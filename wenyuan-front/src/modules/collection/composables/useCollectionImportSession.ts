import { computed, onActivated, onBeforeUnmount, onDeactivated, ref } from 'vue';

/** One import window owns its pending reads and writes, including KeepAlive exits. */
export function useCollectionImportSession(clear: () => void) {
  let generation = 0;
  let active = true;
  const operation = ref('');
  const busy = computed(() => operation.value !== '');
  function invalidate() { generation += 1; operation.value = ''; }
  function current(ticket: number) { return active && generation === ticket; }
  function begin(kind: string) {
    if (!active || busy.value) return null;
    const ticket = ++generation;
    operation.value = kind;
    return ticket;
  }
  function finish(ticket: number) { if (current(ticket)) operation.value = ''; }
  function leave() { active = false; invalidate(); clear(); }
  onDeactivated(leave);
  onBeforeUnmount(leave);
  onActivated(() => { active = true; });
  return { begin, current, finish, invalidate, busy, operation };
}
