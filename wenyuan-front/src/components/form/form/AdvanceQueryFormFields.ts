import { Fragment, defineComponent, nextTick, type PropType, type VNode } from 'vue';
import type { AdvanceQueryFormDiscoveredOption } from './advanceQueryFormSettings';

function normalizeQueryFieldKey(value: unknown) {
  if (Array.isArray(value)) {
    const path = value.map(String).filter(Boolean).join('.');
    return path || null;
  }
  if (typeof value === 'string' || typeof value === 'number') {
    return String(value).trim() || null;
  }
  return null;
}

export function queryFieldDescriptor(node: VNode): AdvanceQueryFormDiscoveredOption | null {
  const props = node.props;
  if (!props) return null;

  const value = normalizeQueryFieldKey(props.queryKey ?? props['query-key'] ?? props.path);
  if (!value) return null;
  const rawLabel = props.queryLabel ?? props['query-label'] ?? props.label;
  const label = typeof rawLabel === 'string' || typeof rawLabel === 'number' ? String(rawLabel).trim() || value : value;
  return { label, value };
}

export function filterQueryFieldNodes(
  nodes: readonly VNode[],
  visibleKeys: readonly string[] | undefined,
  configurable: boolean,
) {
  const discovered: AdvanceQueryFormDiscoveredOption[] = [];
  const rendered: VNode[] = [];
  const discoveredKeys = new Set<string>();
  const visible = visibleKeys ? new Set(visibleKeys) : null;

  function visit(node: VNode) {
    if (node.type === Fragment && Array.isArray(node.children)) {
      (node.children as VNode[]).forEach(visit);
      return;
    }

    const descriptor = queryFieldDescriptor(node);
    if (descriptor && !discoveredKeys.has(descriptor.value)) {
      discoveredKeys.add(descriptor.value);
      discovered.push(descriptor);
    }
    if (!descriptor || !configurable || !visible || visible.has(descriptor.value)) {
      rendered.push(node);
    }
  }

  nodes.forEach(visit);
  return { discovered, rendered };
}

export default defineComponent({
  name: 'AdvanceQueryFormFields',
  props: {
    configurable: { type: Boolean, default: false },
    visibleKeys: { type: Array as PropType<string[] | undefined> },
  },
  emits: {
    'update:options': (_options: AdvanceQueryFormDiscoveredOption[]) => true,
  },
  setup(props, { emit, slots }) {
    let lastOptionsSignature = '';

    return () => {
      const { discovered, rendered } = filterQueryFieldNodes(
        slots.default?.() ?? [],
        props.visibleKeys,
        props.configurable,
      );
      const signature = JSON.stringify(discovered);
      if (signature !== lastOptionsSignature) {
        lastOptionsSignature = signature;
        nextTick(() => emit('update:options', discovered));
      }
      return rendered;
    };
  },
});
