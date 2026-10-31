import { useRestorationAccountWindow } from './useRestorationAccountWindow';
import { onActivated, onDeactivated, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { restorationContractApi as api } from '../api/contract';
import type { RestorationContractVo } from '../typings';
import type { CollectionPrintVo } from '@/modules/collection/typings/collection';
import { errorText } from '../utils/restorationError';

type Context = { restorationId:string; orgId:string; orgName?:string; revision:string; canOperate:boolean; issue?:string; collectionLevel?:string; contract?:RestorationContractVo|null };
const text=(v:unknown):v is string=>typeof v==='string' && !!v.trim();
function body(response:any) {
  if(!response || Array.isArray(response) || (response.status!==undefined && response.status!==200) || response.data==null)throw Error('合同回执不完整或读取失败，请重新核实');
  return response.data;
}
export function useContractWindow(refresh:()=>unknown) {
  const formShow=ref(false),viewShow=ref(false),printShow=ref(false),loading=ref(false),error=ref(''),title=ref('新增合同');
  const vo=ref<RestorationContractVo>({}),viewRow=ref<RestorationContractVo|null>(null),printData=ref<CollectionPrintVo|null>(null);
  const mode=ref<'add'|'edit'|'delete'>('add'),formKey=ref(0);
  const handleSubmit=shallowRef<(()=>Promise<unknown>)>();
  const updateFiles=shallowRef<((value:unknown)=>void)>(),uploadFinished=shallowRef<((value:any)=>void)>();
  let active=true,epoch=0,printEpoch=0,inFlight=false;
  const valid=(token:number)=>active && token===epoch && formShow.value;
  function invalidate() {epoch++;formKey.value++;handleSubmit.value=undefined;updateFiles.value=undefined;uploadFinished.value=undefined;loading.value=false;inFlight=false;printEpoch++;printShow.value=false;printData.value=null;}
  function close() {invalidate();formShow.value=false;viewShow.value=false;viewRow.value=null;vo.value={};error.value='';}
  useRestorationAccountWindow(() => { close(); });
  watch(formShow,v=>{if(!v){invalidate();vo.value={};}},{flush:'sync'});
  watch(viewShow,v=>{if(!v){epoch++;viewRow.value=null;}},{flush:'sync'});
  watch(printShow,v=>{if(!v){printEpoch++;printData.value=null;}},{flush:'sync'});
  onActivated(()=>{active=true;});
  onDeactivated(()=>{active=false;close();});onBeforeUnmount(()=>{active=false;close();});
  function parse(value:any,id:string):Context {
    if(!value || Array.isArray(value) || value.restorationId!==id || !text(value.orgId) || !text(value.revision) || typeof value.canOperate!=='boolean')throw Error('合同核对资料不完整或项目不一致，请重新读取');
    const r=value.contract;
    if(r!=null && (!text(r.id) || r.orgId!==value.orgId || r.restorationId!==id))throw Error('合同关联不一致，请核对原记录');
    return value;
  }
  async function prepare(id:string,oldRow?:RestorationContractVo) {
    invalidate();const token=epoch;loading.value=true;error.value='';
    vo.value=oldRow?{}:{restorationId:id};
    try {
      const response=await api.context(id);
      if(!valid(token))return;
      const c=parse(body(response),id);
      if(oldRow && oldRow.orgId!==c.orgId)throw Error('合同所属馆已变化，请重新读取列表');
      if(oldRow) {
        if(!c.contract || c.contract.id!==oldRow.id)throw Error('合同已变化或不在本次项目中，请重新读取');
        vo.value=cloneDeep(c.contract);
      } else {
        if(c.contract)throw Error('该项目已有合同，请从列表读取后编辑');
        vo.value={restorationId:id,orgId:c.orgId,collectionLevel:c.collectionLevel};
      }
      title.value=`${mode.value==='delete'?'确认删除':mode.value==='edit'?'编辑':'新增'}合同 · ${c.orgName||c.orgId}（${c.orgId}）`;
      if(!c.canOperate){error.value=c.issue||'当前仅可查阅或尚不具备办理条件';return;}
      const model=vo.value,kind=mode.value,rowId=oldRow?.id;
      updateFiles.value=(value)=>{if(valid(token) && kind!=='delete')model.attachmentFileIds=typeof value==='string'?value:undefined;};
      const update=updateFiles.value;
      uploadFinished.value=(value)=>{if(valid(token) && kind!=='delete' && text(value?.id))update(value.id);};
      handleSubmit.value=async()=>{
        if(!valid(token) || model.restorationId!==c.restorationId || model.orgId!==c.orgId || model.id!==rowId)throw Error('合同窗口已关闭或切换，请重新核对');
        if(inFlight)throw Error('正在提交，请勿重复办理');
        inFlight=true;
        try {
          const response=kind==='delete'?await api.deleteById(rowId!,c.orgId,c.revision):await api.save(cloneDeep(model),c.orgId,c.revision);
          const result=body(response);
          if(!text(result) || (rowId && result!==rowId))throw Error('未收到有效合同编号，请回原项目核实保存状态');
          if(!valid(token))throw Error('合同窗口已关闭或切换，请回原项目核实结果');
          close();refresh();return response;
        } finally {if(token===epoch)inFlight=false;}
      };
    } catch(e) {if(valid(token))error.value=errorText(e, '读取合同依据失败');}
    finally {if(token===epoch)loading.value=false;}
  }
  watch(()=>vo.value.restorationId,id=>{
    if(active && formShow.value && mode.value==='add') {
      if(text(id))void prepare(id);else {invalidate();vo.value={};}
    }
  },{flush:'sync'});
  function onAdd(projectId?:string|null) {
    if(!active)return;close();mode.value='add';title.value='新增合同';formShow.value=true;
    if(projectId)vo.value.restorationId=projectId;
  }
  async function openRow(row:RestorationContractVo,kind:'edit'|'delete') {
    if(!active)return;close();mode.value=kind;formShow.value=true;
    if(!text(row.id)||!text(row.restorationId)||!text(row.orgId)){error.value='合同来源不完整，请重新读取列表';return;}
    await prepare(row.restorationId,cloneDeep(row));
  }
  async function onView(row:RestorationContractVo) {
    if(!active)return;close();viewShow.value=true;const token=++epoch;loading.value=true;
    try {
      if(!text(row.id)||!text(row.restorationId)||!text(row.orgId))throw Error('合同来源缺失');
      const response=await api.getById(row.id);
      if(!active || token!==epoch || !viewShow.value)return;
      const data=body(response);
      if(data.id!==row.id || data.restorationId!==row.restorationId || data.orgId!==row.orgId)throw Error('合同来源已变化，请重新读取列表');
      viewRow.value=cloneDeep(data);
    } catch(e){if(active && token===epoch)error.value=errorText(e, '合同读取失败');}
    finally {if(token===epoch)loading.value=false;}
  }
  async function onPrint(source?: RestorationContractVo) {
    const row=cloneDeep(source || (formShow.value ? vo.value : null));
    if(!active || !text(row?.id) || !text(row?.orgId))return;
    printShow.value=false;printData.value=null;const token=epoch,p=++printEpoch;error.value='';
    try {
      const response=await api.print(row.id!);
      if(!valid(token) || p!==printEpoch)return;
      const data=body(response);
      if(data.businessId!==row.id || data.orgId!==row.orgId || !Array.isArray(data.fields) || !Array.isArray(data.collections))throw Error('打印资料不完整或所属合同不符');
      printData.value=data;printShow.value=true;
    } catch(e){if(valid(token) && p===printEpoch)error.value=errorText(e, '合同打印读取失败');}
  }
  return {formShow,viewShow,printShow,loading,error,title,vo,viewRow,printData,mode,formKey,handleSubmit,updateFiles,uploadFinished,onAdd,onEdit:(r:RestorationContractVo)=>openRow(r,'edit'),onDelete:(r:RestorationContractVo)=>openRow(r,'delete'),onView,onPrint};
}
