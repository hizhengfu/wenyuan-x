<template>
  <n-data-table
    v-bind="$attrs"
    ref="refPageTable"
    class="data-table wh-full flex flex-col"
    remote
    v-model:expanded-row-keys="expandedRowKeys"
    striped
    :size="size"
    :min-height="minHeight"
    flex-height
    :single-line="false"
    :data="tableData"
    :loading="tableLoading"
    :pagination="cPagination"
    :row-key="rowKey"
    :columns="tableColumns"
    @update:sorter="handleSorterChange"
    @update:filters="handleFiltersChange"
    @update:page="handlePageChange"
    @update:pageSize="handlePageSizeChange"
  />
</template>
<script lang="ts" setup>
import Sortable from 'sortablejs';
import { computed, h, onMounted, PropType, reactive, ref, triggerRef } from 'vue';
import {
  DataTableColumn,
  DataTableFilterState,
  DataTableSortState,
  NA,
  NButton,
  NIcon,
  NTag,
  PaginationInfo,
  PaginationProps,
} from 'naive-ui';
import { debounce, get, isArray, isBoolean } from 'lodash-es';
import { type } from '@/modules/sys/api/dict';
import { confirm, fileUrl, formatDateTime, formatDateValue, transformToTreeFormat } from '@/utils';
import Icon from '@/components/basic/DynamicIcon.vue';
import { useAppStore } from '@/store';

const appStore = useAppStore();

/**
 * 导出函数
 * */
defineExpose({
  handleQuery,
  handleRefresh,
  updateTableData,
});

/**
 * 数据更新事件
 * */
const emits = defineEmits([
  'add',
  'loadData',
  'update:data',
  'rowEdit',
  'rowCopy',
  'rowView',
  'rowDelete',
  'updateSort',
]);

/**
 * 表格容器
 * */
const refPageTableContainer = ref();
const refPageTable = ref();

const expandedRowKeys = ref<string[]>([]);

/**
 * prop
 * */
const props = defineProps({
  rowKeyField: { type: String, default: 'id' },
  slim: { type: Boolean, default: false },
  size: {
    type: String as PropType<'small' | 'medium' | 'large'>,
    default: 'medium',
  },
  minHeight: { type: String, default: '300px' },
  striped: { type: Boolean, default: true },
  //是否允许添加
  isAdd: { type: Boolean, default: true },
  //是否允许编辑
  isEdit: { type: Boolean, default: true },
  //是否允许复制
  isCopy: { type: Boolean, default: true },
  //是否允许删除
  isDelete: { type: Boolean, default: true },
  //是否允许导出
  isExport: { type: Boolean, default: true },
  //删除数据函数
  funApiDelete: { type: Function },
  //复制数据函数
  funApiCopy: { type: Function },
  //展示操作列，当编辑或删除有启用才有效
  showOperate: { type: Boolean, default: true },
  //是否允许添加
  isTree: { type: Boolean, default: false },
  //初始化查询
  initQuery: { type: Boolean, default: true },
  pagination: { type: [Object, Boolean], default: true },
  //树字段配置
  treeConfig: {
    type: Object,
    default() {
      return { idKey: 'id', pIdKey: 'parentId' };
    },
  },
  //列数据
  columns: {
    type: Array,
    default() {
      return [];
    },
  },
  //查询参数
  params: { type: Object },
  defaultParams: { type: Object },
  //获取数据异步函数
  fetchData: { type: Function },
});

/**
 * 加载状态
 * */
const tableLoading = ref(false);
/**
 * 导出状态
 * */
const exportLoading = ref(false);

/**
 * 表格数据对象
 * */
// const tableData = shallowRef<any[]>([])
const tableData = ref<any[]>([]);

/**
 * 列数组
 * */
const tableColumns = ref<DataTableColumn[]>();

/**
 * 默认分页对象
 * */
const tablePagination = reactive({
  page: 1,
  pageCount: 1,
  itemCount: 0,
  pageSize: 30,
  pageSizes: [10, 30, 50, 150],
  startRow: 0,
  showQuickJumper: true,
  showSizePicker: true,
  prefix(info: PaginationInfo) {
    return `共计${info.itemCount} `;
  },
});

/**
 * 表单是否换行
 * */
const queryOverflow = ref(false);

/**
 * 排序相关数据
 * */
const sortInfo = new Map();

/**
 * 分页计算信息
 * */
const cPagination = computed<false | PaginationProps | undefined>(() => {
  if (false === props.pagination) {
    return props.pagination;
  } else {
    Object.assign(tablePagination, props.pagination);
    return tablePagination;
  }
});

/**
 * 行数据key
 * */
function rowKey(rowData: any) {
  return rowData[props.rowKeyField];
}

/**
 * 标题列
 * */
const columnTitle = computed<string>(() => {
  let column: any = props.columns?.find((item: any) => {
    return item.columnType === 'title';
  });
  return column?.key || 'name' || 'title' || props.rowKeyField;
});

/**
 * 初始化列，加载使用到的字典数据
 *
 * */
async function initColumn() {
  let promises: any[] = [];
  let indexs: number[] = [];
  const { showOperate, isDelete, isEdit, isCopy, funApiDelete, funApiCopy } = props;

  //根据字典类型构建字典类
  let columns = props.columns.map((column, index) => {
    //获取字典数据
    // @ts-ignore
    if (column.dictType) {
      // @ts-ignore
      promises.push(type(column.dictType));
      indexs.push(index);
    }
    defaultRenderColumn(column);
    return column;
  });

  //@ts-ignore
  if (showOperate && (isDelete || isEdit)) {
    //获取配置的自定义操作列
    //@ts-ignore
    let columnOperate = columns.find((column) => column.key === 'operate' && column.type === 'operate');

    //@ts-ignore
    let columnOperateRender = null;
    if (!columnOperate) {
      columnOperate = {
        key: 'operate',
        type: 'operate',
        title: '操作',
        align: 'center',
        fixed: 'right',
        width: '220px',
      };
      columns?.push(columnOperate);
    } else {
      //@ts-ignore
      columnOperateRender = columnOperate.render;
    }

    //防止重复添加内容到操作列
    //@ts-ignore
    if (!columnOperate._init) {
      //合并自定义操作列
      //@ts-ignore
      columnOperate.render = (row: any, index: number) => {
        return [
          //@ts-ignore
          columnOperateRender && columnOperateRender(row, index),
          isCopy &&
            funApiCopy &&
            h(
              NButton,
              {
                size: 'tiny',
                type: 'success',
                class: 'mr-3',
                onClick: () => handleRowCopy(row, index),
              },
              {
                default: () => '复制',
              },
            ),
          isEdit &&
            h(
              NButton,
              {
                size: 'tiny',
                type: 'warning',
                class: 'mr-3',
                onClick: () => handleRowEdit(row, index),
              },
              {
                default: () => '编辑',
              },
            ),
          isDelete &&
            funApiDelete &&
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                onClick: () => handleRowDelete(row, index),
              },
              {
                default: () => '删除',
              },
            ),
        ];
      };
      //@ts-ignore
      columnOperate._init = true;
    }
  }

  //获取字典值
  let promiseDict = await Promise.all(promises);
  promiseDict.forEach((item, index) => {
    // @ts-ignore
    columns[indexs[index]].dictOptions = item.data;
  });
  tableColumns.value = columns as DataTableColumn[];
  return promiseDict;
}

/**
 * 列默认渲染函数
 * */
function defaultRenderColumn(column: any) {
  //有渲染函数的优先自定义渲染函数
  if (column.render) return;

  column.render = function (row: any, index: any) {
    let value = get(row, column.key);
    let columnType = column.columnType;
    let render = [];
    //启用行排序
    if (column.rowDrag) {
      render.push(
        h(NIcon, { class: 'mr-1 sortHandle', style: { cursor: 'move' } }, () => {
          return h(Icon, { icon: 'c:DragVertical' });
        }),
      );
    }
    if (columnType == 'date') {
      render.push(formatDateValue(value));
    } else if (columnType == 'dateTime') {
      render.push(formatDateTime(value));
    } else if (columnType == 'daterange') {
      //时间
      if (value) {
        if (isArray(value)) {
          let t = value.map((it) => {
            return formatDateValue(it);
          });
          render.push(t.join('~'));
        } else {
          render.push(formatDateValue(value));
        }
      } else {
        render.push('--');
      }
    } else if (columnType == 'index') {
      column.align = 'center';
      //索引列,根据起始列递增
      render.push(tablePagination.startRow + index + 1);
    } else if (columnType == 'title') {
      //标题栏
      if (!column.ellipsis) {
        Object.assign(column, {
          ellipsis: {
            width: '30%',
            tooltip: true,
          },
        });
      }
      // render.push(value || '--')
      render.push(
        h(
          'a',
          {
            class: 'data-table-link',
            style: { cursor: 'pointer' },
            onClick: () => handleRowView(row),
          },
          value || '--',
        ),
      );
      render.push(
        h(NIcon, { size: 'small', class: 'mr-1', style: { cursor: 'pointer', verticalAlign: 'sub' } }, () => {
          return h(Icon, { icon: 'c:OpenInNew' });
        }),
      );
    } else if (columnType == 'icon') {
      //缩略图
      render.push(h(NIcon, null, { default: () => h(Icon, { icon: value || 'c:TimerSandEmpty' }) }));
    } else if (columnType == 'path') {
      //文件
      if (value) {
        let src = value;
        if (!/https?/.test(value)) {
          src = fileUrl(value);
        }
        render.push(h(NA, { href: src, type: 'default', target: '_blank' }, { default: () => '查看链接' }));
        // render.push(h('a', {href: src, target: '_blank'}, '查看链接'))
      } else {
        render.push(h('span', null, '--'));
      }
    } else if (columnType === 'image') {
      //缩略图
      if (value) {
        let src = value;
        if (!/https?/.test(value)) {
          src = fileUrl(value);
        }
        render.push(h('img', { src: src + '?h=22' }));
      } else {
        render.push(h('span', null, ''));
      }
    } else if (columnType == 'tag') {
      //tag标签
      value.split(',').map((tagKey: string) => {
        render.push(
          h(
            NTag,
            {
              style: {
                marginRight: '6px',
              },
              type: 'info',
            },
            {
              default: () => tagKey,
            },
          ),
        );
      });
    } else if (columnType == 'dict' || column.dictType || column.dictOptions) {
      //字典
      if (isBoolean(value)) {
        value = value ? 1 : 0;
      }
      render.push((column.dictOptions && column.dictOptions[value]) || value);
    } else {
      render.push(value);
    }
    return render;
  };
}

/**
 * 排序事件
 * */
function handleSorterChange(options: DataTableSortState | DataTableSortState[] | null) {
  //@ts-ignore
  let order = options.order;
  //@ts-ignore
  let columnKey = options.columnKey;
  if (order === false) {
    //@ts-ignore
    delete sortInfo.delete(columnKey);
  } else {
    sortInfo.set(columnKey, order === 'descend');
  }
  handleQuery();
}

/**
 * 过滤事件
 * */
function handleFiltersChange(filters: DataTableFilterState, initiatorColumn: DataTableColumn) {}

/**
 * 分页事件
 * */
function handlePageChange(page: number) {
  tablePagination.page = page;
  tableFetchData();
}

/**
 * 分页大小
 * */
function handlePageSizeChange(pageSize: number) {
  tablePagination.pageSize = pageSize;
  tableFetchData();
}

/**
 * 点击查询，此时会重置分页
 * */
function handleQuery() {
  tableData.value = [];
  tableFetchData(true);
}

/**
 * 刷新,适合修改删除数据的情况下使用
 * */
function handleRefresh() {
  tableData.value = [];
  tableFetchData();
}

const tableFetchData = debounce(sourceTableFetchData, 100);

/**
 * 加载数据
 * @param reset 从第一页开始查询
 * */
function sourceTableFetchData(reset: boolean = false) {
  const { fetchData, isTree, treeConfig } = props;
  if (!fetchData) {
    // triggerRef(tableData)
    return;
  }
  let params = {
    pageSize: tablePagination.pageSize,
    pageNum: reset ? 1 : tablePagination.page,
    ...props.params,
  };

  //@ts-ignore
  if (!params.sortBy) {
    if (sortInfo.size > 0) {
      //@ts-ignore
      params['sortBy'] = Array.from(sortInfo.keys());
      //@ts-ignore
      params['sortDesc'] = Array.from(sortInfo.values());
    }
  }

  tableLoading.value = true;
  fetchData(params)
    .then((resp: any) => {
      if (resp == null) return;

      const data = resp.data ? (resp.data.data ? resp.data.data : resp.data) : resp;
      const list = data.list || (isArray(data) ? data : []);

      //是否为树形表格
      if (isTree) {
        let treeList = transformToTreeFormat(list, treeConfig);
        //默认展开第一级别
        expandedRowKeys.value = treeList.map((item) => {
          return item.id;
        });
        tableData.value = treeList;
      } else {
        tableData.value = list;
      }
      // triggerRef(tableData)

      emits('loadData', resp);
      emits('update:data', tableData.value);

      tablePagination.pageCount = data.pages;

      tablePagination.itemCount = data.total || list.length;
      tableLoading.value = false;

      initSortable();
    })
    .catch((e: any) => {
      console.error('获取表格数据错误', e);
      tableLoading.value = false;
    });
}

/**
 * 更新表格数据
 * */
function updateTableData() {
  triggerRef(tableData);
}

/**
 * 删除行
 * */
function handleRowDelete(row: any, index: number) {
  const { rowKeyField, funApiDelete } = props;
  confirm({
    title: '即将删除！',
    content: `即将删除「${get(row, columnTitle.value)}」，请确认。`,
    to: appStore.fullscreen ? '.pageTable' : 'body',
    onPositiveClick() {
      //有删除函数的情况下执行删除函数
      if (funApiDelete) {
        funApiDelete(row[rowKeyField], index).then(() => {
          emits('rowDelete', row, index);
          handleRefresh();
        });
      } else {
        emits('rowDelete', row, index);
      }
    },
  });
}

/**
 * 编辑行
 * */
function handleRowEdit(row: any, index: number) {
  emits('rowEdit', row, index);
}

/**
 * 复制行
 * */
function handleRowCopy(row: any, index: number) {
  const { rowKeyField, funApiCopy } = props;

  confirm({
    title: '即将复制！',
    content: `即将复制「${row[columnTitle.value]}」，请确认。`,
    to: appStore.fullscreen ? '.pageTable' : 'body',
    onPositiveClick() {
      //有删除函数的情况下执行删除函数
      if (funApiCopy) {
        funApiCopy(row[rowKeyField]).then(() => {
          emits('rowCopy', row);
          handleRefresh();
        });
      } else {
        emits('rowCopy', row);
      }
    },
  });
}

/**
 * 查看行
 * */
function handleRowView(row: any) {
  emits('rowView', row);
}

/**
 * 初始化排序
 * */
function initSortable() {
  if (!(refPageTable.value && refPageTable.value.$el)) return;
  setTimeout(() => {
    const sortableElement = refPageTable.value.$el.querySelector('.n-data-table-tbody');
    const options = {
      handle: '.sortHandle',
      animation: 150,
      onUpdate: function (event: any) {
        const row = tableData.value.splice(event.oldIndex, 1)[0];
        tableData.value.splice(event.newIndex, 0, row);
        /*if(props.isTree){
          tableData.value.
        }else{

        }*/
        emits('updateSort', tableData.value);
      },
    };
    if (sortableElement) {
      Sortable.create(sortableElement, options);
    }
  }, 500);
}

/**
 * 初始化拉一次数据
 * */
onMounted(() => {
  initColumn().then(() => {
    if (props.initQuery) {
      tableFetchData();
    }
  });
});
//
// watchEffect(() => {
//   if (props.columns) {
//     initColumn().then(() => {
//       if (props.initQuery) {
//         tableFetchData()
//       }
//     })
//   }
// })
</script>
