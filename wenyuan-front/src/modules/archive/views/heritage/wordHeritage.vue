  <template>
  <h-page-data-table ref="refPageTable"
                     :fetch-data="list"
                     :scroll-x="1200"
                     :params="paramsForm"
                     @add="handleAdd"
                     @rowEdit="handleEdit"
                     @rowView="handleView"
                     :funApiDelete="del"
                     :columns="columns">
    <template #queryForm>
      <n-form-item label="名称" path="name">  
                                <n-input v-model:value="paramsForm.name" placeholder="请输入名称" clearable></n-input>                  
</n-form-item>  

    </template>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :refTable="refPageTable"
                   :disabled="formType==='view'"
                   :handleSubmit="handleSubmit"
                   v-model:show="formModel">
                   <n-grid x-gap="10">
                    <n-gi span="12">
                        <n-form-item label="遗产名称" path="name">
                            <n-input v-model:value="form.name" placeholder="请输入遗产名称" clearable></n-input>
                        </n-form-item> 
                        <n-form-item label="所在省份" path="areaId">
                            <n-input v-model:value="form.areaId" placeholder="请输入所在省份" clearable></n-input>
                        </n-form-item> 
                    </n-gi>
                    <n-gi span="12">
                        <n-space justify="center">
                            <n-form-item label="缩略图" path="image">
                                <h-avatar v-model:value="form.image" placeholder="请上传缩略图" clearable></h-avatar>
                            </n-form-item>
                        </n-space>
                    </n-gi>
                   </n-grid>
                   <n-grid x-gap="10">
                    <n-gi span="12">
                        <n-form-item label="列入时间" path="year">
                            <n-input v-model:value="form.year" placeholder="请输入列入时间" clearable></n-input>
                        </n-form-item>
                          <n-form-item label="经度" path="lng">
                            <n-input v-model:value="form.lng" placeholder="请输入经度" clearable></n-input>
                        </n-form-item>
                          <n-form-item label="东经" path="longitude">
                            <n-input v-model:value="form.longitude" placeholder="请输入东经" clearable></n-input>
                        </n-form-item>

                    </n-gi>
                     <n-gi span="12">
                        <n-form-item label="扩展时间" path="extendYear">
                            <n-input v-model:value="form.extendYear" placeholder="请输入列入时间" clearable></n-input>
                        </n-form-item>
                        <n-form-item label="纬度" path="lat">
                            <n-input v-model:value="form.lat" placeholder="请输入纬度" clearable></n-input>
                        </n-form-item>
                        <n-form-item label="北纬" path="latitude">
                            <n-input v-model:value="form.latitude" placeholder="请输入北纬" clearable></n-input>
                        </n-form-item>
                    </n-gi>
                   </n-grid>
                   <n-grid x-gap="10">
                    <n-gi span="12">
                        <n-form-item label="遗产区面积" path="heritageArea">
                            <n-input v-model:value="form.heritageArea" placeholder="请输入遗产区面积" clearable></n-input>
                        </n-form-item>
                          <n-form-item label="标准" path="criterion">
                            <n-input v-model:value="form.criterion" placeholder="请输入标准" clearable></n-input>
                        </n-form-item>
                         

                    </n-gi>
                     <n-gi span="12">
                        <n-form-item label="缓冲区面积" path="bufferArea">
                            <n-input v-model:value="form.bufferArea" placeholder="请输入缓冲区面积" clearable></n-input>
                        </n-form-item>
                        
                    </n-gi>
                   </n-grid>
                 
                    <n-form-item label="遗产区" path="protectArea">
                        <n-input v-model:value="form.protectArea" placeholder="请输入遗产区" clearable type="textarea"></n-input>
                      </n-form-item>
                        <n-form-item label="缓冲区" path="controlArea">
                        <n-input v-model:value="form.controlArea" placeholder="请输入缓冲区" clearable type="textarea"></n-input>
                      </n-form-item>
               <n-form-item label="遗产简介" path="description">
                <n-input v-model:value="form.description" placeholder="请输入遗产简介" clearable type="textarea"></n-input>
               </n-form-item>
               <n-form-item label="突出普遍价值" path="outstandingUniversal">
                <n-input v-model:value="form.outstandingUniversal" placeholder="请输入突出普遍价值" clearable type="textarea"></n-input>
               </n-form-item>
               <n-form-item label="完整性" path="integrity">
                <n-input v-model:value="form.integrity" placeholder="请输入完整性" clearable type="textarea"></n-input>
               </n-form-item>
               <n-form-item label="真实性" path="authenticity">
                <n-input v-model:value="form.authenticity" placeholder="请输入真实性" clearable type="textarea"></n-input>
               </n-form-item>
               <n-form-item label="保护和管理要求" path="protectionManagement">
                <n-input v-model:value="form.protectionManagement" placeholder="请输入保护和管理要求" clearable type="textarea"></n-input>
               </n-form-item>
    </h-page-form>
    <h-page-form :rules="rules"
                   :model="form"
                   label-width="auto"
                   :title="formTitle"
                   :refTable="refPageTable"
                   :disabled="formType==='view'"
                :enableCancel="false"
                   v-model:show="detailformModel">
              <worldHeritageView :form="form"></worldHeritageView>
                   </h-page-form>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import { list, edit, add, del, get } from '../../api/heritage/wordHeritage';
import { ref, h } from 'vue';
import {cloneDeep} from 'lodash-es'
import { RouterLink } from 'vue-router';
import worldHeritageView from '../../components/heritage/worldHeritageView.vue'
const columns = [
{'key':'name','title':'名称',fixed: 'left',
    resizable: true,
    ellipsis: { tooltip: true },
    columnType: 'title',
    titleAlign: 'center',
    width: '180px',},
 {'key':'image','title':'缩略图',columnType:'image',width:'150px',align:'center'},
{'key':'areaId','title':'所在省份'},
// {'key':'publishAge','title':'年代'},
{'key':'year','title':'列入时间'},
{'key':'extendYear','title':'扩展时间'},
{'key':'criterion','title':'标准'},
{'key':'path','title':'编辑遗产构成', align:'center',width:'220px',
render(row: any) { 
    return h(
        RouterLink,
        {
         to:{path:'/archive/heritage/compositionOfHeritage',query:{heritageId:row.id}},
        class:'mx-3'
        },
        {default:()=>'编辑遗产构成'}
    )
},

},
{'key':'path','title':'编辑历史沿革', align:'center',width:'220px',
render(row: any) { 
    return h(
        RouterLink,
        {
         to:{path:'/archive/heritage/historicalEvolution',query:{heritageId:row.id}},
        class:'mx-3'
        },
        {default:()=>'编辑历史沿革'}
    )
},

}




]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
const detailformModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref()

const formTitle = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
name:'',
image:'',
areaId:'',
lng:'',
lat:'',
latitude:'',//北纬
longitude:'',//东经
bufferArea:'',//缓冲区面积
heritageArea:'',//遗产区面积
// publishAge:'',//年代
year:'',//列入时间
extendYear:'',//扩展时间
criterion:'',//标准
description:'',//简介
protectArea:'',//保护范围
controlArea:'',//控制范围
outstandingUniversal:'',//突出普遍价值
integrity:'',//完整性
authenticity:'',//真实性
protectionManagement:'',//保护和管理要求
remarks:'',//备注
status:'',//状态

}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

/**
 * 验证规则
 * */
const rules = {
name: {
    required: true,
    message: '请输入名称'
  },

}
/**
 * 查询表单
 * */
const paramsForm = ref({
name: ''
})

const formType = ref('add')

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = '添加'
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  form.value = cloneDeep(defaultForm)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    formModel.value = true
  })  
  formTitle.value = `编辑「${row.name}」`
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
   form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  get(row.id).then((resp) => {
    Object.assign(form.value, resp.data)
    detailformModel.value = true
  }) 
  formTitle.value = `查看「${row.name}」`
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === 'add') {
    return add(data)
  } else {
    return edit(data)
  }
}
</script>           
    