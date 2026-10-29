<template>
  <h-page-data-table ref="refPageTable" :fetch-data="list" :scroll-x="1200" :params="paramsForm" @add="handleAdd"
                     @rowEdit="handleEdit" @rowView="handleView" :funApiDelete="del" :columns="columns">
    <template #queryForm>
      <n-form-item label="规则标题" path="title">
        <n-input v-model:value="paramsForm.title" clearable></n-input>
      </n-form-item>
      <n-form-item label="爬虫站点" path="siteId">
        <SiteSelect v-model:value="paramsForm.siteId" clearable filterable placeholder="请选择站点"></SiteSelect>
      </n-form-item>
    </template>
    <h-page-form :rules="rules" :model="form" label-width="auto" :title="formTitle" :refTable="refPageTable"
                 :disabled="formType === 'view'" :handleSubmit="handleSubmit" v-model:show="formModel">
      <n-grid x-gap="10">
        <n-gi span="24">
          <n-form-item label="规则标题" path="title">
            <n-input v-model:value="form.title"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="爬虫站点" path="siteId">
            <SiteSelect v-model:value="form.siteId" clearable filterable placeholder="请选择站点"></SiteSelect>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="内容标识" path="sourceId">
            <n-input v-model:value="form.sourceId" clearable placeholder="内容管理系统请填写栏目ID"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="链接" path="homeUrl">
            <n-input v-model:value="form.homeUrl"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="解析方式" path="parseType">
            <n-select v-model:value="form.parseType" :options="parseTypeOptions"></n-select>
          </n-form-item>
        </n-gi>
        <n-gi span="24">
          <n-alert :title="form.parseType === 'JSON' ? 'JSON 接口规则写法（JsonPath）' : 'HTML 页面规则写法（XPath）'"
                   type="info" :bordered="false" style="margin-bottom: 4px;">
            <div style="font-size: 12px; line-height: 1.9;">
              <div style="margin-bottom: 4px;">
                采集流程：从「链接」入口页开始 → 用「链接规则」提取详情地址并逐个抓取 → 在详情页用各字段规则提取内容。
                系统以「标题规则能否取到值」判断当前是列表页还是详情页。
              </div>
              <template v-if="form.parseType === 'JSON'">
                <div>· 入口与详情都填返回 JSON 的接口地址；规则用 JsonPath（以 <code>$</code> 开头）</div>
                <div>· 链接规则：取详情 URL 数组，如 <code>$.data[*].url</code></div>
                <div>· 标题 / 作者 / 时间 / 关键字等：取对应字段，如 <code>$.article.title</code></div>
                <div>· 内容规则：取正文字段，如 <code>$.article.content</code></div>
                <div style="color:#999;">· JSON 走纯 HTTP、无需浏览器；详情接口字段若含 HTML 会原样保留</div>
              </template>
              <template v-else>
                <div>· 链接规则：用正则匹配入口页里的详情链接，如 <code>/art/\d+\.html</code></div>
                <div>· 标题 / 作者 / 时间等：XPath，可省略末尾函数（默认取文本），如 <code>//h1</code> 或 <code>//div[@class='title']</code></div>
                <div>· 内容规则：XPath，默认取 html() 保留富文本，如 <code>//div[@id='content']</code>（正文中相对路径图片会自动补全为绝对地址）</div>
                <div style="color:#999;">· 页面需 JS 渲染才有数据时，打开下方「动态渲染」用无头浏览器抓取</div>
              </template>
            </div>
          </n-alert>
        </n-gi>
        <n-gi span="12">
          <n-form-item :label="form.parseType === 'JSON' ? '链接规则(JsonPath)' : '链接规则'" path="linkRule">
            <n-input v-model:value="form.linkRule"
                     :placeholder="form.parseType === 'JSON' ? '如 $.data[*].url' : '链接匹配正则'"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="标题规则" path="titleRule">
            <n-input v-model:value="form.titleRule"
                     :placeholder="form.parseType === 'JSON' ? '如 $.article.title' : '如 //h1'"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="关键字规则" path="keywordsRule">
            <n-input v-model:value="form.keywordsRule"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="描述规则" path="descriptionRule">
            <n-input v-model:value="form.descriptionRule"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="内容规则" path="contentRule">
            <n-input v-model:value="form.contentRule" clearable
                     :placeholder="form.parseType === 'JSON' ? '如 $.article.content' : '如 //article'"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="作者规则" path="authorRule">
            <n-input v-model:value="form.authorRule"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item label="发布时间规则" path="dateTimeRule">
            <n-input v-model:value="form.dateTimeRule"></n-input>
          </n-form-item>
        </n-gi>
        <n-gi span="24">
          <n-form-item path="dynamicRule" label="动态规则">
            <n-dynamic-input v-model:value="form.dynamicRule"
                             :on-create="handleDynamicRuleCreate">
              <template #default="{ value,index }">
                <div style="display: flex; align-items: center; width: 100%;">
                  <n-form-item label="字段"
                               :show-label="index===0"
                               :path="`dynamicRule[${index}].field`"
                               :rule="{required:true,message:'请输入字段'}">
                    <n-input v-model:value="value.field" class="mr-2" placeholder="请输入字段名称"/>
                  </n-form-item>
                  <n-form-item label="规则"
                               style="flex: 1;"
                               :show-label="index===0"
                               :path="`dynamicRule[${index}].rule`"
                               :rule="{required:true,message:'请输入规则'}">
                    <n-input v-model:value="value.rule"
                             :placeholder="form.parseType === 'JSON' ? '请输入 JsonPath 规则' : '请输入 xpath 规则'"/>
                  </n-form-item>
                </div>
              </template>
            </n-dynamic-input>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="status" label="发布状态">
            <h-dict-radio v-model:value="form.status" type="content_status" clearable
                          placeholder="请选择"></h-dict-radio>
          </n-form-item>
        </n-gi>
        <n-gi span="12">
          <n-form-item path="enableDriver" label="是否支持动态渲染">
            <n-switch v-model:value="form.enableDriver" :disabled="form.parseType === 'JSON'"></n-switch>
            <span v-if="form.parseType === 'JSON'" class="ml-2 text-gray-400" style="font-size:12px;">
              JSON 接口走纯 HTTP，无需浏览器渲染
            </span>
          </n-form-item>
        </n-gi>
      </n-grid>
    </h-page-form>
    <DialogViewForm v-model:show="modelFlag" :title="formTitle" @update:show="updateModelFlag">
      <div class="test-box">
        <h-inline-form submitText="测试" :handleSubmit="onSpiderTest" :model="spiderParam">
          <n-form-item path="url" style="flex:1" :rule="{
            required: true,
            message: '请输入网址',
          }">
            <n-input class="mr-2" v-model:value="spiderParam.url" type="text" placeholder="请输入网址" clearable/>
          </n-form-item>
        </h-inline-form>
        <n-divider/>
        <n-spin size="large" :show="spinFlag">
          <div class="content-box">
            <n-tabs v-if="!infoFlag && !spinFlag" type="card" placement="left" animated>
              <n-tab-pane style="max-height: 400px; overflow: auto;" name="解析结果" tab='解析结果'>
                <div v-for="(value,key) in result">
                  <template v-if="key!='html'">
                    {{ key }}:<code>{{ value || '暂无数据' }}</code>
                  </template>
                </div>
              </n-tab-pane>

              <n-tab-pane style="min-height: 400px; overflow: auto;" name="源文件" tab='源文件'>
                <textarea readonly style="min-height: 395px;width:98%;">
                {{ htmlStr || '暂无源代码数据' }}
                </textarea>
              </n-tab-pane>
            </n-tabs>
            <n-result v-if="infoFlag" class="flex-col-center wh-full" :status="resultItems.status"
                      :title="resultItems.title" :description="resultItems.description">
            </n-result>
          </div>
          <template #description>
            测试中...
          </template>
        </n-spin>
      </div>
    </DialogViewForm>
  </h-page-data-table>
</template>
<script lang="ts" setup>
import {list, edit, add, del, test} from '../api/rule'
import {ref, h} from "vue";
import {cloneDeep} from 'lodash-es'
import {NButton} from 'naive-ui';
import SiteSelect from '../components/form/SiteSelect.vue'
import "@/components/form/form/index"

const columns = [
  {"key": "title", "title": "规则标题", "columnType": "title", "ellipsis": true},
  {"key": "siteTitle", "title": "爬虫站点"},
  {
    "key": "parseType", "title": "解析方式", width: '110px',
    render: (row: any) => row.parseType === 'JSON' ? 'JSON接口' : 'HTML页面'
  },
  {"key": "status", "title": "发布状态", dictType: 'content_status'},
  {"key": "enableDriver", "title": "是否支持动态渲染", dictType: 'sys_enabled'},
  {
    key: 'operate', type: 'operate', title: '操作', align: 'center', width: '220px',
    render(row: any) {
      return h(NButton, {
        size: 'tiny',
        type: 'success',
        class: 'mr-3',
        onClick() {
          modelFlag.value = true
          formTitle.value = `测试「${row.title}」规则`
          spiderParam.value.ruleId = String(row.id)
        },
      }, {default: () => '测试爬虫规则'})
    }
  },
]

function handleDynamicRuleCreate() {
  return {
    field: '',
    rule: '',
  }
}

// 解析方式选项
const parseTypeOptions = [
  {label: 'HTML页面(XPath)', value: 'HTML'},
  {label: 'JSON接口(JsonPath)', value: 'JSON'},
]

/**
 * 编辑表单相关
 * */
const formModel = ref(false)
/**
 * 表格ref
 * */
const refPageTable = ref()

const formTitle = ref()

// 模态框显隐控制
const modelFlag = ref(false)

// 爬虫测试传参
const spiderParam = ref({
  ruleId: "",
  url: ""
})

//加载状态
const spinFlag = ref(false)

//控制提示信息显隐的参数
const infoFlag = ref(true)

//解析结果默认对象
const defaultResult = {
  title: '',
  keywords: '',
  description: '',
  content: '',
  author: '',
  dateTime: '',
  url: ''
}

// 视图上的解析结果对象
const result = ref({})

// html字符串
const htmlStr = ref()

/**
 * 表单默认值
 * */
const defaultForm = {
  id: '',
  title: '',  //规则标题 必填
  siteId: undefined, //关联爬虫ID 必填
  sourceId: '', //关联来源ID --对应cms则是栏目ID
  homeUrl: '', // 路径  必填
  keywordsRule: '', //关键字规则
  descriptionRule: '', //描述规则
  contentRule: '', //内容规则
  linkRule: '',  //链接规则 必填
  titleRule: '', //标题规则
  authorRule: '',  // 作者规则
  dateTimeRule: '',  //发布时间规则
  dynamicRule: [],  //发布时间规则
  parseType: 'HTML', //解析方式 HTML(XPath)/JSON(JsonPath)
  enableDriver: false, //是否支持动态渲染
  createBy: '',
  createTime: '',
  updateBy: '',
  updateTime: '',
  status: 0,   //发布状态
}

/**
 * 表单
 * */
const form = ref(cloneDeep(defaultForm))

// 空状态提示信息
const resultItems = ref<any>({
  status: 'info',
  description: '请输入链接后点击测试按钮进行测试',
  title: '提示'
})


/**
 * 验证规则
 * */
const rules = {
  title: {
    required: true,
    message: '请输入标题'
  },
  siteId: {
    required: true,
    message: '请输入关联爬虫ID'
  },
  homeUrl: {
    required: true,
    message: '请输入路径'
  },
  linkRule: {
    required: true,
    message: '请输入路径规则'
  },

}
/**
 * 查询表单
 * */
const paramsForm = ref({
  title: '',
  siteId: undefined
})

const formType = ref("add")

/**
 * 添加
 * */
function handleAdd() {
  formType.value = 'add'
  form.value = cloneDeep(defaultForm)
  formTitle.value = "添加"
  formModel.value = true
}

/**
 * 编辑事件
 * */
function handleEdit(row: any) {
  formType.value = 'edit'
  form.value = cloneDeep(defaultForm)
  Object.assign(form.value, row)
  formTitle.value = `编辑「${row.title}」规则`
  formModel.value = true
}

/**
 * 查看事件
 * */
function handleView(row: any) {
  formType.value = 'view'
  Object.assign(form.value, row)
  formTitle.value = `查看「${row.title}」规则`
  formModel.value = true
}

/**
 * 提交
 * */
function handleSubmit(data: any) {
  if (formType.value === "add") {
    return add(data)
  } else {
    return edit(data)
  }
}

/**
 * 测试爬虫是否生效
 */
async function onSpiderTest() {
  spinFlag.value = true
  infoFlag.value = false
  test(spiderParam.value).then((res) => {
    if (res.data && res.data[0]) {
      handleRes(res.data[0]);
      spinFlag.value = false;
    } else {
      infoFlag.value = true;
      spinFlag.value = false;
      resultItems.value.status = 'error';
      resultItems.value.description = '请输入正确的链接或者检查规则是否正确';
    }
  }).catch(() => {
    infoFlag.value = true;
    spinFlag.value = false;
    resultItems.value.status = 'error';
    resultItems.value.description = '爬虫监测超时或者其它错误';
  })
}

// 模态框组件的show属性变动事件
function updateModelFlag(v: any) {
  if (!v) {
    spiderParam.value.url = ""
    infoFlag.value = true
    htmlStr.value = ""
    result.value = {}
    spinFlag.value = false
    resultItems.value.status = 'info'
    resultItems.value.description = '请输入链接后点击测试按钮进行测试'
  }
}

//测试返回结果数据格式处理
function handleRes(data: any) {
  htmlStr.value = data['html']
  Object.assign(result.value, data)
}

</script>
<style lang="scss" scoped>
.test-box {
  width: 100%;
  box-sizing: border-box;
  padding: 8px;

  .content-box {
    width: 100%;
    height: 400px;
  }
}
</style>
