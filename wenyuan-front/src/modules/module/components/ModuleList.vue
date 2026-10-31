<template>
  <n-space v-if="loading">
    <n-spin size="large"/>
  </n-space>
  <h-page-data-table v-else ref="refPageTable"
                     :fetch-data="fetchList"
                     :scroll-x="1800"
                     :params="paramsForm"
                     @add="handleAdd"
                     @rowEdit="handleEdit"
                     @rowView="handleView"
                     :funApiDelete="handleDelete"
                     :columns="columnsRef">
    <template #queryForm>
      <n-form-item v-for="item in paramsFormList" :label="item.fieldLabel" :key="item.name">
        <n-input v-model:value="paramsForm[item.name]"></n-input>
      </n-form-item>
    </template>
    <h-page-form :rules="rules"
                 :model="form"
                 label-width="auto"
                 :title="formTitle"
                 :disabled="formType==='view'"
                 :refTable="refPageTable"
                 :handleSubmit="handleSubmit"
                 v-model:show="formModel">

    </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {loadByTableId, list} from '../api/moduleView'
import {watchEffect, ref, onDeactivated} from "vue";
import {AxiosResponse} from "axios";
import {ModuleTable, ModuleTableColumn} from "@/modules/module/typings/module";
import {PageDataTableColumn} from "@/components/data/typings/data";

const props = defineProps({
  tableId: {type: String}//表id
});
/**
 * 加载中
 * */
const loading = ref(true);
const columnsRef = ref();
const paramsForm = ref<any>({});
//查询字段
const paramsFormList = ref<ModuleTableColumn[]>();
const defaultForm = {};
const form = ref();
const formModel = ref();
const rules = ref();
const refPageTable = ref();
const formTitle = ref();
const formType = ref();

watchEffect(() => {
  const {tableId} = props;
  initTable(tableId)
});


/**
 * 初始化表数据
 * */
function initTable(tableId?: string) {
  if (!tableId) {
    return;
  }

  loading.value = true;
  loadByTableId(tableId).then((resp: AxiosResponse<ModuleTable>) => {
    const {title, columns} = resp.data;
    formTitle.value = title;
    console.log(1, columns)
    //表头
    let tempColumns: PageDataTableColumn[] = [];
    //查询form
    let tempQueryFormList: ModuleTableColumn[] = []
    columns.forEach((column: ModuleTableColumn) => {
      //是否为列表显示列
      if (column.isList) {
        let temp: PageDataTableColumn = {key: column.name, title: column.fieldLabel};
        if (column.fieldDict) {
          temp.dictType = column.fieldDict
        }

        if ('DATE' === column.fieldType) {
          temp.columnType = 'date'
        } else if ('DATETIME' === column.fieldType) {
          temp.columnType = 'dateTime'
        } else if ('IMAGE' === column.fieldType) {
          temp.columnType = 'image'
        } else if ("FILE" === column.fieldType) {
          temp.columnType = 'file'
        } else if ("FILES" === column.fieldType) {
          temp.columnType = 'files'
        } else if ("TEXTAREA" === column.fieldType) {
          temp.ellipsis = {
            tooltip: {
              width: '300px'
            },
          }
        }

        tempColumns.push(temp)
      }
      //是否查询
      if (column.isQuery) {
        tempQueryFormList.push(column)
      }
      //数据插入或更新对象
      if (column.isInsert || column.isUpdate) {

      }
    });
    columnsRef.value = tempColumns
    paramsFormList.value = tempQueryFormList
  }).finally(() => {
    loading.value = false
  })
}

/**
 * 加载列表数据
 * */
function fetchList(params: object) {
  const {tableId} = props;
  return list(tableId || '', params)
}

/**
 * 添加数据
 * */
function handleAdd() {

}

/**
 * 编辑列表
 * */
function handleEdit() {

}

/**
 * 查看数据事件
 * */
function handleView() {

}

/**
 * 删除事件
 * */
function handleDelete() {

}

/**
 * 提交保存
 * */
function handleSubmit() {

}


</script>
