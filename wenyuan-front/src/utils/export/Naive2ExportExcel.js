import { utils } from 'xlsx';
import { write } from 'xlsx-js-style';
import { saveAs } from 'file-saver';
import { get, isArray, isFunction, isNumber } from 'lodash-es';
import { isVNode } from 'vue';

/**
 * 导出未excel
 * @param headers 标题列
 * @param data 数据
 * @param fileName 文件名
 * @param autoWidth 是否自动计算列款
 *
 * */
function exportToExcel({ headers, data, fileName, autoWidth = true }) {
  const wb = utils.book_new();
  const ws = utils.aoa_to_sheet([]);
  // 定义边框样式
  const borderStyle = {
    top: { style: 'thin' },
    bottom: { style: 'thin' },
    left: { style: 'thin' },
    right: { style: 'thin' },
  };
  //标题样式
  const fontHeaderStyle = {
    name: '仿宋', // 设置字体为小仿宋
    sz: 12, // 设置字体大小为12号
    bold: true, // 设置加粗
  };
  //正文样式
  const fontBodyStyle = {
    name: '仿宋', // 设置字体为小仿宋
    sz: 12, // 设置字体大小为12号
  };

  //正文对齐方式
  const bodyAlignment = {
    vertical: 'center',
  };

  // 处理多级别表头
  // const headers = processHeaders(columns);
  const maxLevel = Math.max(...headers.map((h) => h.level));
  // 是否存在合并内容行
  let contentGroup = false;
  // 写入表头
  for (let i = 0; i <= maxLevel; i++) {
    const row = headers.filter((h) => h.level === i);
    row.forEach((header) => {
      utils.sheet_add_aoa(ws, [[isFunction(header.title) ? header.title() : header.title]], {
        origin: {
          r: i,
          c: header.startColumn,
        },
      });

      const cell = utils.encode_cell({ r: i, c: header.startColumn });
      if (ws[cell] && ws[cell].v) {
        ws[cell].s = {
          font: fontHeaderStyle,
          alignment: {
            textRotation: header.titleTextRotation || 0,//255表示文字垂直
            // horizontal: 'center',
            horizontal: header.titleAlign || 'center',
            vertical: 'center',
          },
        };
      }
      //判断是否存在内容合并行列
      if (!contentGroup && (header.rowSpan || header.colSpan)) {
        contentGroup = true;
      }
    });
  }

  //过滤出需要显示的列
  const childColumn = headers.filter((column) => column.colspanHeader === 1);

  //构建导出数据
  let exportData = data.map((row, index) => {
    return childColumn.map((column) => {
      let value = get(row, column.key);
      if (column.columnType == 'title') {
        // 直接使用对应的字段
      } else if (column.columnType == 'index') {
        value = `${index + 1}`;
      } else if (column.render) {
        //有自定义函数的特殊处理，其中可能会产生图标等类型，故只处理字符串
        let text = getAllTextFromRender(column.render(row));
        if (text) {
          value = text;
        }
      }
      if (isArray(value)) {
        return value.join('');
      } else if (isNumber(value)) {
        return value;
      }
      if (value && value.length > 32767) {
        value = value.slice(0, 32000) + '...数据超长已被截断';
      }
      return value || '';
    });
  });

  // 写入数据
  utils.sheet_add_json(ws, exportData, { origin: -1, skipHeader: true });

  // 构建合并单元格
  ws['!merges'] = [];
  // 处理合并单元格表头
  if (maxLevel > 0) {
    headers.forEach((header) => {
      if (header.colspanHeader > 1 || header.rowspanHeader > 1) {
        ws['!merges'].push({
          s: { r: header.level, c: header.startColumn },
          e: { r: header.level + header.rowspanHeader - 1, c: header.startColumn + header.colspanHeader - 1 },
        });
      } else {
        ws['!merges'].push({
          s: { r: header.level, c: header.startColumn },
          e: { r: maxLevel, c: header.startColumn + header.colspanHeader - 1 },
        });
      }
    });
  }

  //当有合并行列的情况时，对存在合并行列数据的进行处理
  if (contentGroup) {
    //合并内容
    data.forEach((row, rowIndex) => {
      childColumn.forEach((header, colIndex) => {
        let rowSpan = header.rowSpan ? header.rowSpan(row, rowIndex) : 1;
        let colSpan = header.colSpan ? header.colSpan(row, rowIndex) : 1;
        if (rowSpan > 1 || colSpan > 1) {
          let temp = {
            s: { r: maxLevel + 1 + rowIndex, c: colIndex },
            e: {
              r: maxLevel + rowIndex + rowSpan,
              c: colIndex + colSpan - 1,
            },
          };
          ws['!merges'].push(temp);
          //找到对应列并获取位置
          const cell = utils.encode_cell({ r: maxLevel + 1 + rowIndex, c: colIndex });
          if (ws[cell] && ws[cell].v) {
            ws[cell].s = {
              font: fontBodyStyle,
              alignment: {
                horizontal: header.align || 'left',
                vertical: 'center',
              },
            };
          }
        }
      });
    });
  }

  const range = utils.decode_range(ws['!ref']);
  for (let r = range.s.r; r <= range.e.r; ++r) {
    for (let c = range.s.c; c <= range.e.c; ++c) {
      const cell = utils.encode_cell({ c, r });

      if (!ws[cell]) {
        ws[cell] = { v: '' };
      }

      // 默认正文样式
      let style = {
        font: fontBodyStyle,
        border: borderStyle,
        alignment: bodyAlignment, // 默认居中/靠左
      };

      // 如果是数据行，根据列配置覆盖对齐方式
      if (r > maxLevel) {
        const column = childColumn[c];
        if (column && column.align) {
          style.alignment = {
            horizontal: column.align || 'left',
            vertical: 'center',
          };
        }
      }

      // 如果是表头行，保持表头样式（不覆盖之前设置的）
      if (r <= maxLevel) {
        if (ws[cell].s) {
          ws[cell].s.border = borderStyle;
        } else {
          ws[cell].s = style;
        }
      } else {
        ws[cell].s = style;
      }
    }
  }

  //自动计算列宽
  if (autoWidth) {
    /*设置worksheet每列的最大宽度*/
    const colWidth = childColumn.map((column) => {
      if (column.width) {
        return {
          wpx: column.width.toString().replace('px', '').replace('%', ''),
        };
      } else {
        return {
          wpx: 100,
        };
      }
    });
    ws['!cols'] = colWidth;
  }

  utils.book_append_sheet(wb, ws);

  const wbout = write(wb, { bookType: 'xlsx', type: 'binary' });
  const blob = new Blob([s2ab(wbout)], { type: 'application/octet-stream' });
  saveAs(blob, fileName);
}

/**
 * 获取全部的文本内容
 * */
function getAllTextFromRender(node) {
  let text = [];

  if (isArray(node)) {
    node.forEach((n) => {
      text.push(getAllTextFromRender(n));
    });
    return text.join('\r\n');
  }

  if (isVNode(node)) {
    if (typeof node.children === 'string') {
      // 处理文本节点
      text.push(node.children);
    } else if (Array.isArray(node.children)) {
      // 处理数组子节点
      node.children.forEach((child) => {
        if (typeof child === 'string') {
          text.push(child);
        } else if (isVNode(child)) {
          text.push(getAllTextFromRender(child));
        }
      });
    } else if (typeof node.children === 'object' && node.children !== null) {
      // 处理对象类型的子节点（如插槽）
      Object.values(node.children).forEach((child) => {
        if (typeof child === 'function') {
          // 如果是渲染函数，调用它并递归处理结果
          const renderResult = child();
          text.push(getAllTextFromRender(renderResult));
        } else if (isVNode(child)) {
          text.push(getAllTextFromRender(child));
        }
      });
    }
  } else {
    return node;
  }

  return text.join('\r\n');
}

// 处理多级别表头
function processHeaders(columns, level = 0, startColumn = 0) {
  let headers = [];
  let currentColumn = startColumn;

  columns.forEach((column) => {
    if (column.children) {
      const childHeaders = processHeaders(column.children, level + 1, currentColumn);
      headers.push({
        title: column.title,
        level: level,
        startColumn: currentColumn,
        colspan: childHeaders.length,
        rowspan: 1,
      });
      headers = headers.concat(childHeaders);
      currentColumn += childHeaders.length;
    } else {
      headers.push({
        title: column.title,
        level: level,
        startColumn: currentColumn,
        colspan: 1,
        rowspan: 1,
      });
      currentColumn += 1;
    }
  });

  return headers;
}

// 辅助函数：将字符串转换为 ArrayBuffer
function s2ab(s) {
  const buf = new ArrayBuffer(s.length);
  const view = new Uint8Array(buf);
  for (let i = 0; i < s.length; i++) view[i] = s.charCodeAt(i) & 0xff;
  return buf;
}

export { exportToExcel };
