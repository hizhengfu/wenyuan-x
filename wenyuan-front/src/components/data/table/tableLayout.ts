type TableColumnWidth = {
  width?: number | string;
  minWidth?: number | string;
  type?: string;
  show?: boolean;
  children?: TableColumnWidth[];
};

function pixelWidth(width: number | string | undefined) {
  if (typeof width === 'number') return Number.isFinite(width) ? Math.max(0, width) : 0;
  if (typeof width === 'string' && /^\d+(?:\.\d+)?(?:px)?$/.test(width.trim())) return parseFloat(width);
  return 0;
}

export function tableColumnsWidth(columns: readonly TableColumnWidth[], minimumWidth?: number | string): number {
  const width = columns.reduce((total, column) => {
    if (column.show === false) return total;
    if (column.children?.length) return total + tableColumnsWidth(column.children);
    const columnWidth = Math.max(pixelWidth(column.width), pixelWidth(column.minWidth));
    return total + (columnWidth || (column.type === 'selection' || column.type === 'expand' ? 48 : 120));
  }, 0);
  return Math.max(width, pixelWidth(minimumWidth));
}
