import { h } from 'vue';
import HDictText from '@/components/form/field/dict/text.vue';
import CollectionCatalogDictText from '../components/CollectionCatalogDictText.vue';

export function dictText(type: string, value: unknown) {
  const normalized = value === undefined || value === null ? undefined : String(value);
  return h(type === 'col_texture' ? CollectionCatalogDictText : HDictText, { type, value: normalized });
}

function boolDictValue(value: unknown) {
  if (value === true || value === 'true') return true;
  if (value === false || value === 'false') return false;
  return Boolean(value);
}

export function presenceText(value: unknown) {
  return dictText('col_presence', boolDictValue(value));
}

export function returnedText(value: unknown) {
  return dictText('col_returned', boolDictValue(value));
}

export function registeredText(value: unknown) {
  return dictText('col_registered', boolDictValue(value));
}
