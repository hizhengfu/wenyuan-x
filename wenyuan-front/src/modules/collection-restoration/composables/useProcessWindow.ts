import { useRestorationAccountWindow } from './useRestorationAccountWindow';
import { onActivated, onDeactivated, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { cloneDeep } from 'lodash-es';
import { restorationProcessApi as api } from '../api/process';
import type { RestorationProcessVo } from '../typings';
import { errorText } from '../utils/restorationError';

type Context = { restorationId:string; orgId:string; orgName?:string; revision:string; canOperate:boolean; issue?:string; processes:RestorationProcessVo[] };
const text=(v:unknown):v is string=>typeof v==='string' && !!v.trim();
/** noun：窗口用词，工序页用「工序」，修复日志页签用「日志」（标题、提示统一）。 */
export function useProcessWindow(refresh:()=>unknown,noun='工序') {
  const formShow=ref(false),viewShow=ref(false),loading=ref(false),error=ref(''),title=ref(`新增${noun}`);
  const editVo=ref<RestorationProcessVo>({sort:0}),viewRow=ref<RestorationProcessVo|null>(null);
  const mode=ref<'add'|'edit'|'delete'>('add'),formKey=ref(0);
  const handleSubmit=shallowRef<(()=>Promise<unknown>)>();
  const updateImageFiles=shallowRef<((value:unknown)=>void)>(),uploadFinished=shallowRef<((value:any)=>void)>();
  let active=true,epoch=0,inFlight=false;
  const valid=(token:number)=>active && token===epoch && formShow.value;
  function invalidate() {epoch++;formKey.value++;handleSubmit.value=undefined;updateImageFiles.value=undefined;uploadFinished.value=undefined;loading.value=false;inFlight=false;}
  function close() {invalidate();formShow.value=false;viewShow.value=false;viewRow.value=null;error.value='';}
  useRestorationAccountWindow(() => { close(); editVo.value = { sort: 0 }; });
  watch(formShow,v=>{if(!v)invalidate();},{flush:'sync'});
  watch(viewShow,v=>{if(!v){epoch++;viewRow.value=null;}},{flush:'sync'});
  onActivated(()=>{active=true;});
  onDeactivated(()=>{active=false;close();});onBeforeUnmount(()=>{active=false;close();});
  function parse(value:any,id:string):Context {
    if(!value || value.restorationId!==id || !text(value.orgId) || !text(value.revision) || typeof value.canOperate!=='boolean' || !Array.isArray(value.processes) || value.processes.some((r:any)=>!r || !text(r.id) || r.orgId!==value.orgId || r.restorationId!==id))throw Error(`${noun}核对资料不完整或项目不一致，请重新读取`);
    return value;
  }
  async function prepare(id:string,oldRow?:RestorationProcessVo) {
    invalidate();const token=epoch;loading.value=true;error.value='';
    try {
      const c=parse((await api.context(id))?.data,id);
      if(!valid(token))return;
      if(oldRow && oldRow.orgId!==c.orgId)throw Error(`${noun}所属馆已变化，请重新读取列表`);
      if(oldRow) {
        const row=c.processes.find(r=>r.id===oldRow.id);
        if(!row)throw Error(`${noun}不在本次项目中，请重新读取`);
        editVo.value=cloneDeep(row);
      } else { editVo.value.orgId=c.orgId; }
      title.value=`${mode.value==='delete'?'确认删除':mode.value==='edit'?'编辑':'新增'}${noun} · ${c.orgName||c.orgId}（${c.orgId}）`;
      if(!c.canOperate){error.value=c.issue||'当前仅可查阅或尚不具备办理条件';return;}
      const model=editVo.value,kind=mode.value,rowId=oldRow?.id;
      updateImageFiles.value=(value)=>{if(valid(token) && kind!=='delete')model.imageFileIds=typeof value==='string'?value:undefined;};
      const update=updateImageFiles.value;
      uploadFinished.value=(value)=>{if(valid(token) && kind!=='delete' && text(value?.id))update(value.id);};
      handleSubmit.value=async()=>{
        if(!valid(token) || model.restorationId!==c.restorationId || model.orgId!==c.orgId || (rowId && model.id!==rowId))throw Error(`${noun}窗口已关闭或切换，请重新核对`);
        if(inFlight)throw Error('正在提交，请勿重复办理');
        inFlight=true;
        try {
          const response=kind==='delete'
            ?await api.delete(rowId!,c.orgId,c.revision)
            :await api.save(cloneDeep(model),c.orgId,c.revision);
          if(!text(response?.data) || (rowId && response.data!==rowId))throw Error(`未收到有效${noun}编号，请回原项目核实保存状态`);
          if(!valid(token))throw Error(`${noun}窗口已关闭或切换，请回原项目核实结果`);
          close();refresh();return response;
        } finally {if(token===epoch)inFlight=false;}
      };
    } catch(e) {if(valid(token))error.value=errorText(e, `读取${noun}依据失败`);}
    finally {if(token===epoch)loading.value=false;}
  }
  watch(()=>editVo.value.restorationId,id=>{
    if(active && formShow.value && mode.value==='add') {
      editVo.value.orgId=undefined; editVo.value.operatorId=undefined; editVo.value.assistantIds=undefined; editVo.value.methodId=undefined;
      title.value=`新增${noun}`; error.value='';
      if(text(id))void prepare(id);else invalidate();
    }
  },{flush:'sync'});
  function onAdd(projectId?:string|null) {
    if(!active)return;close();mode.value='add';title.value=`新增${noun}`;editVo.value={sort:0};formShow.value=true;
    if(projectId)editVo.value.restorationId=projectId;
  }
  async function openRow(row:RestorationProcessVo,kind:'edit'|'delete') {
    if(!active)return;close();mode.value=kind;title.value=`${kind==='delete'?'确认删除':'编辑'}${noun}`;formShow.value=true;editVo.value={};
    if(!text(row.id)||!text(row.restorationId)||!text(row.orgId)){error.value=`${noun}来源不完整，请重新读取列表`;return;}
    await prepare(row.restorationId,cloneDeep(row));
  }
  async function onView(row:RestorationProcessVo) {
    if(!active)return;close();viewShow.value=true;const token=++epoch;loading.value=true;
    try {
      if(!text(row.id))throw Error(`${noun}编号缺失`);
      const data=(await api.getById(row.id))?.data;
      if(!active || token!==epoch || !viewShow.value)return;
      if(!data || data.id!==row.id || data.restorationId!==row.restorationId || data.orgId!==row.orgId)throw Error(`${noun}来源已变化，请重新读取列表`);
      viewRow.value=data;
    } catch(e){if(active && token===epoch)error.value=errorText(e, `${noun}读取失败`);}
    finally {if(token===epoch)loading.value=false;}
  }
  return {formShow,viewShow,loading,error,title,editVo,viewRow,mode,formKey,handleSubmit,updateImageFiles,uploadFinished,onAdd,onEdit:(r:RestorationProcessVo)=>openRow(r,'edit'),onDelete:(r:RestorationProcessVo)=>openRow(r,'delete'),onView};
}
