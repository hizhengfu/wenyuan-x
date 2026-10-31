import type { RecordFilterLocationVo } from '../../museum-device/typings';

const COLLECTION_CONTAINER_TYPES = new Set(['CABINET', 'LAYER', 'BOX', 'SHOWCASE']);

/** 场馆安全只选择建筑空间；柜架、层格、囊匣和展柜属于藏品保存边界。 */
export function filterSafetyLocations(locations: RecordFilterLocationVo[]) {
  return locations.filter((item) => !COLLECTION_CONTAINER_TYPES.has(item.type?.toUpperCase()));
}

export function safetyLocationSubtreeIds(id: string, locations: RecordFilterLocationVo[]) {
  const allowed = filterSafetyLocations(locations);
  const result = new Set([id]);
  let changed = true;
  while (changed) {
    changed = false;
    allowed.forEach((item) => {
      if (item.parentId && result.has(item.parentId) && !result.has(item.id)) {
        result.add(item.id);
        changed = true;
      }
    });
  }
  return result;
}
