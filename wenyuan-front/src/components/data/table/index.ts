import PageDataTable from './PageDataTable.vue'
import DataTable from './DataTable.vue'
import DataIterator from './DataIterator.vue'

export {PageDataTable, DataTable, DataIterator}

/**
 * 计算行合并
 * */
export function calcTableRowspan(rowData: any, rowIndex: number) {
  let span = 1
  // @ts-ignore
  let all = this.value
  let i = rowIndex
  let len = all.length
  while (((i + 1) < len)) {
    i++
    if (all[i]['type'] !== rowData['type']) {
      span = i - rowIndex
      break
    } else if (len - 1 === i) {
      span = len - rowIndex
    }
  }
  return span
}
