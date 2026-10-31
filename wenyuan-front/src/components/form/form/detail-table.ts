import type { CSSProperties } from 'vue';

export type DetailCell = {
  key?: string | number;
  tag?: 'td' | 'th';
  content?: unknown;
  slot?: string;
  colspan?: number | string;
  rowspan?: number | string;
  class?: string | string[] | Record<string, boolean>;
  style?: string | CSSProperties;
  html?: boolean;
};

export type DetailRow = {
  key?: string | number;
  cells: DetailCell[];
  class?: string | string[] | Record<string, boolean>;
  style?: string | CSSProperties;
};
