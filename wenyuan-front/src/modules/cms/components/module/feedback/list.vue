<template>
    <h-page-data-table ref="refPageTable" title="问卷管理" :fetch-data="list" :scroll-x="1200" :params="paramsForm"
    @add="handleAdd" @rowEdit="handleEdit" 
     :default-expanded-row-keys="['1']" is-tree :fun-api-delete="del" :columns="columns" permission="CMS_FEEDBACK">
    <template #queryForm>
        <n-form-item label="问卷标题" path="title">
            <n-input v-model:value="paramsForm.title" placeholder="请输入问卷标题" clearable></n-input>
        </n-form-item>
         <n-form-item label="状态" path="status">
            <h-dict-select v-model:value="paramsForm.status" placeholder="请选择状态" clearable type="content_status"></h-dict-select>
        </n-form-item>
    </template> 
 <h-page-form v-model:show="formModel" :rules="rules" :model="form" label-width="auto" :title="formTitle" width="80%"
      :disabled="formType === 'view'" :ref-table="refPageTable" :handle-submit="handleSubmit">
     <n-grid x-gap="12" :cols="3">
      <n-gi>
        <n-form-item label="问卷标题" path="title">
          <n-input v-model:value="form.title" placeholder="请输入问卷标题" clearable></n-input>
        </n-form-item>
        <n-form-item label="开始时间" path="beginTime">
           <n-date-picker v-model:value="form.beginTime"></n-date-picker>
        </n-form-item>
      </n-gi>
      <n-gi>
        <n-form-item label="所属栏目" path="categoryId">
      <HCascaderCategory v-model:value="form.categoryId" placeholder="请选择所属栏目" module="FEEDBACK" clearable></HCascaderCategory>
        </n-form-item>
         <n-form-item label="结束时间" path="endTime">
           <n-date-picker v-model:value="form.endTime"></n-date-picker>
        </n-form-item>
      </n-gi>
      <n-gi>
        <n-form-item label="关键字" path="keywords">
     <n-input v-model:value="form.keywords" placeholder="请输入关键字" clearable></n-input>
        </n-form-item>
         <n-form-item label="排序" path="sort">
          <n-input-number v-model:value="form.sort" placeholder="请输入排序" clearable ></n-input-number>
        </n-form-item>
      </n-gi>
     </n-grid>
     <n-form-item label="问卷简介" path="description">
          <n-input v-model:value="form.description" placeholder="请输入问卷简介" clearable  type="textarea"></n-input>
        </n-form-item>
        
         <n-form-item path="content" style="flex: 1">
            <template #label>
              调查说明
              <span class="ml-3">
                （是否远程抓图
                <n-switch v-model:value="form.saveRemoteImage"></n-switch>）
              </span>
            </template>
            <h-editor :disabled="formType === 'view'" v-model:value="form.content" placeholder="请输入调查说明"></h-editor>
          </n-form-item>
          <n-form-item label="内容状态" path="status">
            <h-dict-radio v-model:value="form.status" clearable type="content_status"
              placeholder="请选择状态"></h-dict-radio>
          </n-form-item>
      <CmsPublicationPanel v-if="form.id" module="FEEDBACK" :content-id="form.id" :disabled="formType === 'view'" @changed="state => { form.status = state === 'PUBLISHED' ? 'NORMAL' : state === 'TRASH' ? 'DELETE' : 'AUDIT'; get(form.id).then(response => Object.assign(form, response.data)); refPageTable?.handleQuery(); }" />
    </h-page-form>

 </h-page-data-table>

</template>
<script lang="ts" setup>
import CmsPublicationPanel from '../../CmsPublicationPanel.vue';
import { list,del,get,add,edit} from '../../../api/feedback'
import { ref ,h} from 'vue'
import { cloneDeep } from 'lodash-es';
import HCascaderCategory from '../../form/field/CascaderCategory.vue';
import { RouterLink} from 'vue-router'

const columns = [
  {key:'title',title:'问卷标题'},
    {key:'bjbd',title:'题目配置',
      render(row:any){
        return[
           h(
          RouterLink,
          {
            to:{path:'/cms/feedback/design',query:{id:row.id,title:'题目配置'}}
          },
          {default:()=>'题目配置'},
        ),
         
        
        ]
       
      }
    },
    {key:'ylbd',title:'问卷预览',
       render(row:any){
         return[
            h(
          RouterLink,
          {
            to:{path:'/cms/feedback/preview',query:{id:row.id,title:'问卷预览'}}
          },
          {default:()=>'问卷预览'},
        ),]
       }
    },
    {key:'fkjg',title:'答卷管理',
       render(row:any){
         return[
           h(
          RouterLink,
          {
            to:{path:'/cms/feedback/answerList',query:{id:row.id,title:'答卷管理'}}
          },
          {default:()=>'查看答卷'},
        ),]
       }
    }
]
const defaultForm = {
 
  title: '',
  categoryId: null,
  keywords: '',
  description: '',
  content: '',
  status: 0,
  sort:0,
  beginTime:null,
  endTime:null
};
const formType = ref('add');
const form: any = ref(cloneDeep(defaultForm));
/**
 * 题目配置相关
 * */
const formModel = ref(false);
const detailModel = ref(false);

/**
 * 表格ref
 * */
const refPageTable = ref();

const formTitle = ref();
const paramsForm = ref({
  title: '',
 
  status: null,
});

/**
 * 验证规则
 * */
const rules = {
 
};

/**
 * 增加事件
 */
function handleAdd() {
    formType.value = 'add'
    form.value = cloneDeep(defaultForm)
    formTitle.value = '添加问卷'
    formModel.value = true
}
  /**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit';
  form.value = cloneDeep(defaultForm);
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data);
    formModel.value = true;
  });
  // Object.assign(form.value, row)
  formTitle.value = `编辑问卷「${row.title}」`;
}
/**
 * 提交
 * */
function handleSubmit(data: any) {
  
  if (formType.value === 'add') {
    return add(data);
  } else {
    return edit(data);
  }
}
</script>
<style lang="scss" scoped></style>