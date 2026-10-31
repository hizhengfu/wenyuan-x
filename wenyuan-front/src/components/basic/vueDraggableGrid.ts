import { GridItem as RawGridItem, GridLayout as RawGridLayout } from '@noction/vue-draggable-grid';
import type { DefineComponent, SlotsType } from 'vue';

type GridItemData = Record<string, any> & {
  id?: string | number;
};

type GridLayoutComponent = DefineComponent<
  {
    layout?: GridItemData[];
    colNum?: number;
    rowHeight?: number;
    isDraggable?: boolean;
    isResizable?: boolean;
    autoSize?: boolean;
    verticalCompact?: boolean;
    margin?: number[];
    maxRows?: number;
    preventCollision?: boolean;
  },
  {},
  any,
  {},
  {},
  any,
  any,
  {},
  string,
  any,
  any,
  any,
  SlotsType<{
    default: (props: { gridItemProps: Record<string, any> }) => any;
    gridItemContent: (props: { item: GridItemData }) => any;
  }>
>;

export const GridLayout = RawGridLayout as unknown as GridLayoutComponent;
export const GridItem = RawGridItem as unknown as DefineComponent<any, {}, any>;
