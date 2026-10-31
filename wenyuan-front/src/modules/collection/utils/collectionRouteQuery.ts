export type CollectionRouteQueryValue = string | string[] | null | undefined;
export type CollectionRouteQuery = Record<string, CollectionRouteQueryValue>;
export type CollectionQueryParamValue = string | number | null;

export type CollectionRouteFilterOption = {
  value: string;
  params: string[];
};

type DateRangeRef = {
  value: [number, number] | null;
};

type DateRangeRefMap = Record<string, DateRangeRef | undefined>;

const collectionQueryParamKeys = [
  'collectionId',
  'orgId',
  'keyword',
  'totalRegNo',
  'classNo',
  'categoryId',
  'era',
  'level',
  'source',
  'lifeStatus',
  'catalogStatus',
  'registrationScope',
  'nature',
  'relicLevelScope',
  'texture',
  'completeness',
  'warehouseId',
  'registeredDateStart',
  'registeredDateEnd',
  'acquireDateStart',
  'acquireDateEnd',
  'createTimeStart',
  'createTimeEnd',
  'hasImage',
  'hasAttachment',
] as const;

export type CollectionQueryParamKey = (typeof collectionQueryParamKeys)[number];
const timestampQueryParamKeys = new Set<CollectionQueryParamKey>([
  'registeredDateStart',
  'registeredDateEnd',
  'acquireDateStart',
  'acquireDateEnd',
  'createTimeStart',
  'createTimeEnd',
]);

function firstQueryValue(value: CollectionRouteQueryValue) {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' ? raw.trim() || null : null;
}

function queryParamValue(key: CollectionQueryParamKey, value: CollectionRouteQueryValue): CollectionQueryParamValue {
  const text = firstQueryValue(value);
  if (!text) return null;
  if (!timestampQueryParamKeys.has(key)) return text;
  const timestamp = Number(text);
  return Number.isFinite(timestamp) ? timestamp : null;
}

function syncDateRange(
  target: Record<string, CollectionQueryParamValue>,
  startKey: CollectionQueryParamKey,
  endKey: CollectionQueryParamKey,
  rangeRef: DateRangeRef,
) {
  const start = target[startKey];
  const end = target[endKey];
  rangeRef.value = typeof start === 'number' && typeof end === 'number' ? [start, end] : null;
}

export function applyCollectionRouteQuery(
  routeQuery: CollectionRouteQuery,
  target: Record<string, CollectionQueryParamValue>,
  acquireDateRange: DateRangeRef,
  createTimeRange: DateRangeRef,
  registeredDateRange?: DateRangeRef,
) {
  let changed = false;
  collectionQueryParamKeys.forEach((key) => {
    if (!(key in target)) return;
    const next = queryParamValue(key, routeQuery[key]);
    if (target[key] !== next) {
      target[key] = next;
      changed = true;
    }
  });
  syncDateRange(target, 'acquireDateStart', 'acquireDateEnd', acquireDateRange);
  syncDateRange(target, 'createTimeStart', 'createTimeEnd', createTimeRange);
  if (registeredDateRange) syncDateRange(target, 'registeredDateStart', 'registeredDateEnd', registeredDateRange);
  return changed;
}

export function ensureRouteFilterKeys(
  currentKeys: readonly string[],
  filterOptions: readonly CollectionRouteFilterOption[],
  routeQuery: CollectionRouteQuery,
) {
  const visible = new Set(currentKeys);
  filterOptions.forEach((option) => {
    if (option.params.some((param) => firstQueryValue(routeQuery[param]))) {
      visible.add(option.value);
    }
  });
  return filterOptions.filter((option) => visible.has(option.value)).map((option) => option.value);
}

export function clearHiddenCollectionRouteFilters(
  target: Record<string, CollectionQueryParamValue>,
  filterOptions: readonly CollectionRouteFilterOption[],
  visibleKeys: readonly string[],
  dateRanges: DateRangeRefMap = {},
) {
  const visible = new Set(visibleKeys);
  filterOptions
    .filter((option) => !visible.has(option.value))
    .forEach((option) => {
      option.params.forEach((param) => {
        if (param in target) target[param] = null;
      });
      const rangeRef = dateRanges[option.value];
      if (rangeRef) rangeRef.value = null;
    });
}
