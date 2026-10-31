import { useRestorationAccountWindow } from './useRestorationAccountWindow';
import { onActivated, onDeactivated, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { restorationArchiveApi as api } from '../api/archive';
import type { RestorationArchiveVo, RestorationDamageVo } from '../typings';
import type { CollectionPrintVo } from '@/modules/collection/typings/collection';
import { errorText } from '../utils/restorationError';
export type ArchiveRecordContext = { restorationId:string;orgId:string;orgName?:string;collectionId:string;revision:string;
  canEditArchive:boolean;canEditDamage:boolean;archiveIssue?:string;damageIssue?:string;archive?:RestorationArchiveVo|null;damages:RestorationDamageVo[] };
const text=(v:unknown):v is string=>typeof v==='string' && !!v.trim();
function parse(response:any,id:string):ArchiveRecordContext {
  const c=response?.data;
  if((response?.status!==undefined && response.status!==200) || !c || c.restorationId!==id || !text(c.orgId) || !text(c.collectionId) || !text(c.revision) ||
    typeof c.canEditArchive!=='boolean' || typeof c.canEditDamage!=='boolean' || !Array.isArray(c.damages))throw Error('档案病害核对资料不完整，请重新读取');
  const linked=(r:any)=>r && text(r.id) && r.orgId===c.orgId && r.restorationId===id;
  if((c.archive && !linked(c.archive)) || c.damages.some((r:any)=>!linked(r)||r.collectionId!==c.collectionId) || new Set(c.damages.map((r:any)=>r.id)).size!==c.damages.length)throw Error('档案病害不属于本次原馆项目及实物');
  return cloneDeep(c);
}
export function useArchiveRecordWindow<T extends RestorationArchiveVo|RestorationDamageVo>(kind:'archive'|'damage',refresh:()=>unknown,toPayload?:(row:T)=>T) {
  const label=kind==='archive'?'档案主件':'病害记录';
  const formShow=ref(false),viewShow=ref(false),loading=ref(false),error=ref(''),title=ref('新增'+label),formKey=ref(0);
  const editVo=ref<T>({} as T),viewRow=ref<T|null>(null),context=shallowRef<ArchiveRecordContext>();
  const mode=ref<'add'|'edit'|'delete'>('add'),handleSubmit=shallowRef<()=>Promise<unknown>>();
  const updateImageFiles=shallowRef<(v:unknown)=>void>(),uploadFinished=shallowRef<(v:any)=>void>();
  const printShow=ref(false),printData=ref<CollectionPrintVo|null>(null);
  let active=true,epoch=0,printing=0,related=0;
  const valid=(token:number)=>active && epoch===token && formShow.value;
  function invalidate(){++epoch;++formKey.value;++printing;++related;handleSubmit.value=undefined;updateImageFiles.value=undefined;uploadFinished.value=undefined;context.value=undefined;loading.value=false;printShow.value=false;printData.value=null;}
  function close(){invalidate();formShow.value=false;viewShow.value=false;viewRow.value=null;error.value='';}
  useRestorationAccountWindow(() => { close(); editVo.value = {} as T; });
  watch(formShow,v=>{if(!v)invalidate();},{flush:'sync'});
  watch(viewShow,v=>{if(!v){++epoch;viewRow.value=null;context.value=undefined;}},{flush:'sync'});
  watch(printShow,v=>{if(!v){++printing;printData.value=null;}},{flush:'sync'});
  onActivated(()=>{active=true;});onDeactivated(()=>{active=false;close();});onBeforeUnmount(()=>{active=false;close();});
  async function prepare(id:string,old?:T){
    invalidate();const token=epoch;loading.value=true;error.value='';
    try {
      const response=await api.context(id);if(!valid(token))return;
      const c=parse(response,id);
      if(old && old.orgId!==c.orgId)throw Error('记录原馆已变化，请重新读取列表');
      const current=kind==='archive'?c.archive:c.damages.find(r=>r.id===old?.id);
      if(old && current?.id!==old.id)throw Error('记录已不存在或不属于本次项目');
      if(kind==='archive' && !old && current)throw Error('该项目已有档案，请从现有档案进入编辑');
      if(kind==='archive' && current)for(const raw of [(current as RestorationArchiveVo).materialsUsed,(current as RestorationArchiveVo).restorerSignatures]){
        if(raw){const values=JSON.parse(raw);if(!Array.isArray(values)||values.some(v=>!v||typeof v!=='object'||Array.isArray(v)))throw Error('材料或签名清单格式不正确，请核实原件后再编辑');}
      }
      editVo.value=cloneDeep(old?current:{restorationId:id,orgId:c.orgId,...(kind==='damage'?{collectionId:c.collectionId,sort:0}:{})}) as T;
      context.value=c;
      title.value=`${mode.value==='delete'?'确认删除':mode.value==='edit'?'编辑':'新增'}${label} · ${c.orgName||c.orgId}（${c.orgId}） · ${id}`;
      if(!(kind==='archive'?c.canEditArchive:c.canEditDamage)){error.value=(kind==='archive'?c.archiveIssue:c.damageIssue)||'当前仅可查阅，不能办理';return;}
      const model=editVo.value as T,operation=mode.value,rowId=old?.id;let inFlight=false;
      updateImageFiles.value=(v)=>{if(valid(token)&&operation!=='delete')(model as RestorationDamageVo).imageFileIds=typeof v==='string'?v:undefined;};
      const update=updateImageFiles.value;uploadFinished.value=v=>{if(valid(token)&&text(v?.id))update(v.id);};
      handleSubmit.value=async()=>{
        if(!valid(token)||model.restorationId!==id||model.orgId!==c.orgId||model.id!==rowId)throw Error('窗口已关闭或来源已变化，请重新核对');
        if(inFlight)throw Error('正在提交，请勿重复办理');inFlight=true;
        try {
          const payload=toPayload?toPayload(cloneDeep(model)):cloneDeep(model);
          const result=operation==='delete' ? await (kind==='archive'?api.delete:api.damageDelete)(rowId!,c.orgId,c.revision)
            : kind==='archive'?await api.save(payload as RestorationArchiveVo,c.orgId,c.revision):await api.damageSave(payload as RestorationDamageVo,c.orgId,c.revision);
          if(!valid(token))throw Error('窗口已关闭或切换，请回原项目核实结果');
          if((result?.status!==undefined&&result.status!==200)||!text(result?.data)||(rowId&&result.data!==rowId))throw Error('登记响应无法确认，请重新读取实际记录');
          close();refresh();return result;
        } finally {inFlight=false;}
      };
    }catch(e){if(valid(token))error.value=errorText(e, '读取档案病害失败');}
    finally{if(token===epoch)loading.value=false;}
  }
  watch(()=>editVo.value.restorationId,id=>{
    if(active&&formShow.value&&mode.value==='add'){
      if(text(id)){void prepare(id);}else {invalidate();error.value='请先选择实际修复项目';}
    }
  },{flush:'sync'});
  function onAdd(id?:string|null){if(!active)return;close();mode.value='add';title.value='新增'+label;editVo.value={} as T;formShow.value=true;if(id)editVo.value.restorationId=id;}
  async function openRow(row:T,op:'edit'|'delete'){
    if(!active)return;close();mode.value=op;formShow.value=true;editVo.value={} as T;
    if(!text(row.id)||!text(row.orgId)||!text(row.restorationId)){error.value='原馆、记录或项目编号缺失，请重新读取列表';return;}
    await prepare(row.restorationId,cloneDeep(row));
  }
  async function onView(row:T){
    if(!active)return;close();viewShow.value=true;const token=++epoch;loading.value=true;
    try{
      if(!text(row.id)||!text(row.restorationId)||!text(row.orgId))throw Error('记录来源不完整');
      const response=await api.context(row.restorationId);if(!active||token!==epoch||!viewShow.value)return;
      const c=parse(response,row.restorationId),current=kind==='archive'?c.archive:c.damages.find(d=>d.id===row.id);
      if(c.orgId!==row.orgId||current?.id!==row.id)throw Error('记录来源已变化，请重新读取列表');
      viewRow.value=cloneDeep(current) as T;context.value=c;
    }catch(e){if(active&&token===epoch)error.value=errorText(e, '读取记录失败');}
    finally{if(token===epoch)loading.value=false;}
  }
  async function onPrint(source?: T){
    const token=epoch,row=cloneDeep(source || editVo.value);
    if(!valid(token)||!text(row.id)||!text(row.orgId))return;
    printData.value=null;printShow.value=false;
    const seq=++printing;
    try{
      const result:any=await api.print(row.id);if(!valid(token)||seq!==printing)return;
      const d=result?.data;
      if((result?.status!==undefined&&result.status!==200)||!d||d.businessId!==row.id||d.orgId!==row.orgId||!Array.isArray(d.fields)||!Array.isArray(d.collections))throw Error('打印资料与本次档案不一致');
      printData.value=d;printShow.value=true;
    }catch(e){if(valid(token)&&seq===printing)error.value=errorText(e, '读取打印资料失败');}
  }
  async function reloadRelated(){
    const token=epoch,c=context.value,seq=++related;if(!c||!valid(token))return;
    try{const response=await api.context(c.restorationId);if(!valid(token)||seq!==related)return;const next=parse(response,c.restorationId);if(next.orgId!==c.orgId)throw Error('原馆已变化');context.value={...c,damages:next.damages};}
    catch(e){if(valid(token)&&seq===related){error.value=errorText(e, '读取病害失败');handleSubmit.value=undefined;}}
  }
  return {formShow,viewShow,loading,error,title,formKey,mode,editVo,viewRow,context,handleSubmit,updateImageFiles,uploadFinished,printShow,printData,onPrint,close,reloadRelated,
    onAdd,onEdit:(r:T)=>openRow(r,'edit'),onDelete:(r:T)=>openRow(r,'delete'),onView};
}
