import {DataTableColumn, Ellipsis} from 'naive-ui'

/**
 * 分也表列
 * */
export interface PageDataTableColumn extends DataTableColumn {
  key: string,
  title: string,
  align?: string,
  fixed?: string,
  width?: string,
  dictType?: string,
  columnType?: string,
  ellipsis?: boolean | Ellipsis,
}

/**
 * chart 时间格式
 * */
export interface ChartDateParams {
  dateValue: string | number[],
  dataType: string,
}
