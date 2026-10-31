/**
 * 导出Excel
 * */
export function export2Excel(callback: Function) {
  //异步加载excel生成方法
  // @ts-ignore
  import('./Export2Excel').then(
    excel => {
      callback && callback(excel)
    }
  )
}

/**
 * 导出Excel
 * */
export function exportNaive2Excel(callback: Function) {
  //异步加载excel生成方法
  // @ts-ignore
  import('./Naive2ExportExcel').then(
    excel => {
      callback && callback(excel)
    }
  )
}
