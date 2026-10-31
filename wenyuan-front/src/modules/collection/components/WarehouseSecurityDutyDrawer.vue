<template>
 <HPageForm content-only :enable-cancel="false" v-model:show="show" :trap-focus="false" title="库区安全值班">
  <NSpace vertical>
      <CollectionArchiveIssues :data="data" />
   <NAlert type="info">排班与实际值守分别核对。进行中的记录不能证明历史日已完成；交接或值守更正会保留原件。请按实际履职情况登记。</NAlert>
   <WarehouseTreeSelect :value="warehouseId" scene-type="STORAGE" :allowed-types="['WAREHOUSE','AREA']" @update:value="selectWarehouse" />
   <NSpace><NDatePicker v-model:value="day" type="date" :clearable="false" @update:value="refresh"/><NButton v-if="canManage" :disabled="!ready" @click="edit('schedule')">新增排班</NButton><NButton :disabled="!warehouseId || loading" @click="refresh">刷新核对</NButton></NSpace>
   <NAlert v-if="error" type="error">{{error}}</NAlert>
   <NSpin :show="loading">
    <template v-if="ready && data.coverage">
     <NAlert :type="data.coverage.actualDayComplete?'success':'warning'">{{data.coverage.actualDayComplete?'已结束值守记录覆盖全天':'尚不能确认全天实际值守完成'}}。实际核对截至 {{time(data.coverage.assessedThrough)}}。</NAlert>
     <NCard title="覆盖空档"><p>排班：{{gaps(data.coverage.plannedGaps)}}</p><p>实际值守（含进行中）：{{gaps(data.coverage.actualGaps)}}</p></NCard>
     <NEmpty v-if="!data.shifts.length" description="尚无排班记录"/>
     <NCard v-for="row in data.shifts" :key="row.shift.id" :title="`${time(row.shift.value.planned.from)} — ${time(row.shift.value.planned.to)}`">
      <CollectionEvidenceReadView :fields="scheduleReadFields.filter(f=>f.type!=='file')" :data="row.shift.value"/>
      <WarehouseSecurityDutyOriginals :warehouse-id="warehouseId!" :file-ids="row.shift.value.fileIds"/>
      <WarehouseSecurityDutyOriginals v-if="row.cancellation" :warehouse-id="warehouseId!" :file-ids="row.cancellation.value.fileIds"/>
      <NText v-if="row.cancellation" type="warning">已取消：{{row.cancellation.value.reason}}</NText>
      <NSpace v-else><NButton v-if="canAttend(row)" @click="edit('attend',row)">{{row.attendance.length?'补充或更正值守':'登记实际值守'}}</NButton><NButton v-if="canAttend(row) && row.attendance.length" @click="edit('handover',row)">本人核对接班</NButton><NButton v-if="canManage && !row.attendance.length" @click="edit('cancel',row)">取消排班</NButton></NSpace>
      <NCollapse>
       <NCollapseItem v-for="(record,index) in row.attendance" :key="record.id" :name="record.id" :title="`值守记录 ${Number(index)+1}${index===row.attendance.length-1?'（当前）':'（历史保留）'}`"><CollectionEvidenceReadView :fields="attendanceReadFields.filter(f=>f.type!=='file')" :data="record.value"/><WarehouseSecurityDutyOriginals :warehouse-id="warehouseId!" :file-ids="record.value.fileIds"/></NCollapseItem>
       <NCollapseItem v-for="record in row.handovers" :key="record.id" :name="record.id" :title="data.currentHandoverIds.includes(record.id)?'接班核对（当前依据）':'接班核对（依据已更正，须重新核对）'"><CollectionEvidenceReadView :fields="handoverReadFields.filter(f=>f.type!=='file')" :data="record.value"/><WarehouseSecurityDutyOriginals :warehouse-id="warehouseId!" :file-ids="record.value.fileIds"/></NCollapseItem>
      </NCollapse>
     </NCard>
    </template>
   </NSpin>
  </NSpace>
 </HPageForm>
 <CollectionEvidenceForm :key="formEpoch" ref="formRef" :title="formTitle" :fields="fields" :save="save"/>
</template>
<script setup lang="ts">
import CollectionArchiveIssues from './CollectionArchiveIssues.vue';
import {computed,nextTick,onBeforeUnmount,onDeactivated,ref,watch} from 'vue';
import request from '@/utils/request';
import {cloneDeep} from 'lodash-es';
import {useAuthStore} from '@/store/modules/auth';
import {formatLocalDateValue} from '@/utils';
import type {EvidenceField} from '../utils/evidenceFields';
import WarehouseTreeSelect from './WarehouseTreeSelect.vue';
import WarehouseSecurityDutyOriginals from './WarehouseSecurityDutyOriginals.vue';
import CollectionEvidenceForm from './CollectionEvidenceForm.vue';
import CollectionEvidenceReadView from './CollectionEvidenceReadView.vue';
type Kind='schedule'|'attend'|'handover'|'cancel';
const show=ref(false),warehouseId=ref<string>(),day=ref<number>(Date.now()),loading=ref(false),error=ref(''),data=ref<any>({shifts:[],currentHandoverIds:[]}),formRef=ref<any>(),fields=ref<EvidenceField[]>([]),formTitle=ref('');
const auth=useAuthStore(),readActor=ref<string>(),formEpoch=ref(0);
let alive=true,generation=0;
type Context={warehouse:string;generation:number;epoch:number;actor:string;kind:Kind;requestId:string;row?:any;outgoing:any[]};
let editing:Context|undefined;
const save=computed(()=>{const epoch=formEpoch.value,context=cloneDeep(editing);return (value:Record<string,any>)=>context && epoch===formEpoch.value ? submit(value,context) : Promise.resolve(false);});
const ready=computed(()=>alive && !!warehouseId.value && !!readActor.value && readActor.value===auth.userInfo?.id && !loading.value && !error.value && show.value);
const canManage=computed(()=>auth.hasPermission('COLLECTION_ADMIN,COLLECTION_WAREHOUSE_MANAGE'));
const currentRow=(row:any)=>data.value.shifts.find((item:any)=>item.shift.id===row?.shift?.id);
const canAttend=(row:any)=>ready.value && (canManage.value || auth.hasRoleCode('COLLECTION_SECURITY')) && !!currentRow(row) && !currentRow(row).cancellation && currentRow(row).shift.value.officerId===auth.userInfo?.id;
const file:EvidenceField={key:'fileIds',label:'实际签署原件',type:'file',required:true};
const scheduleReadFields:EvidenceField[]=[{key:'officerId',label:'安全保卫值班人员',type:'user',required:true},{key:'responsibility',label:'负责库区与事项',type:'textarea',required:true},file];
const attendanceReadFields:EvidenceField[]=[{key:'startedAt',label:'实际开始时间',type:'datetime',required:true},{key:'endedAt',label:'实际结束时间（进行中留空）',type:'datetime'},{key:'situation',label:'安全情况',type:'textarea',required:true},{key:'handoverNotes',label:'交接事项与遗留问题',type:'textarea',required:true},{key:'correctionReason',label:'补充或更正原因',type:'textarea'},file];
const handoverReadFields:EvidenceField[]=[{key:'outgoingShiftId',label:'交班班次'},{key:'handedOverAt',label:'实际交接时间',type:'datetime',required:true},{key:'opinion',label:'本人核对情况',type:'textarea',required:true},file];
const time=(value:any)=>formatLocalDateValue(value,'YYYY-MM-DD HH:mm:ss');
const gaps=(values:any[])=>values.length?values.map(v=>`${time(v.from)} — ${time(v.to)}`).join('；'):'无已识别空档';
function invalidate(){++generation;++formEpoch.value;editing=undefined;readActor.value=undefined;formRef.value?.close();loading.value=false;data.value={shifts:[],currentHandoverIds:[]};error.value='';fields.value=[];}
function close(){show.value=false;warehouseId.value=undefined;invalidate();}
watch(show,v=>{if(!v)invalidate();},{flush:'sync'});
watch(()=>auth.userInfo?.id,close,{flush:'sync'});
onBeforeUnmount(()=>{alive=false;close();});onDeactivated(close);
async function refresh(){invalidate();if(!alive || !show.value || !warehouseId.value || !auth.userInfo?.id)return false;
 const ticket=generation,id=warehouseId.value,actor=auth.userInfo.id;loading.value=true;
 const current=()=>alive && ticket===generation && show.value && id===warehouseId.value && actor===auth.userInfo?.id;
 try{const r:any=await request({url:`/collection/warehouse/${id}/security-duty`,method:'get',params:{day:formatLocalDateValue(day.value)}});if(!current())return false;if(!Array.isArray(r.data?.shifts)||!r.data?.coverage||!Array.isArray(r.data.currentHandoverIds))throw new Error('值班记录格式异常');data.value=r.data;readActor.value=actor;return true;}
 catch(e:any){if(current())error.value=e?.message||'读取值班记录失败';return false;}
 finally{if(current())loading.value=false;}}
async function selectWarehouse(id?:string){if(!alive || !show.value)return;warehouseId.value=id;await refresh();}
async function open(){if(!alive || !auth.userInfo?.id)return;invalidate();warehouseId.value=undefined;show.value=true;}
async function edit(kind:Kind,row?:any){const actor=auth.userInfo?.id;if(!ready.value || !actor)return;
 if((kind==='schedule' || kind==='cancel') && !canManage.value)return;
 const selected=currentRow(row);
 if(kind!=='schedule' && (!selected || selected.cancellation))return;
 if((kind==='attend' || kind==='handover') && !canAttend(selected))return;
 if(kind==='handover' && !selected.attendance.length)return;
 if(kind==='cancel' && selected.attendance.length)return;
 formRef.value?.close();
 const context:Context={warehouse:warehouseId.value!,generation,epoch:++formEpoch.value,actor,kind,requestId:crypto.randomUUID(),row:cloneDeep(selected),outgoing:cloneDeep(data.value.shifts.filter((s:any)=>s.shift.id!==selected?.shift.id && !s.cancellation && s.shift.value.officerId!==selected?.shift.value.officerId && s.attendance.at(-1)?.value.endedAt))};editing=context;
 const latest=context.row?.attendance.at(-1);let initial:Record<string,any>={};
 if(kind==='schedule'){formTitle.value='登记安全值班排班';fields.value=[...scheduleReadFields.slice(0,2),{key:'from',label:'计划开始',type:'datetime',required:true},{key:'to',label:'计划结束',type:'datetime',required:true},file];}
 if(kind==='attend'){formTitle.value='本人登记实际值守';fields.value=attendanceReadFields.map(f=>({...f,required:f.key==='correctionReason'?!!latest:f.required}));initial=cloneDeep(latest?.value || {});for(const k of ['startedAt','endedAt'])if(initial[k])initial[k]=new Date(initial[k]).getTime();delete initial.fileIds;delete initial.correctionReason;}
 if(kind==='cancel'){formTitle.value='取消尚未发生的排班';fields.value=[{key:'reason',label:'取消原因',type:'textarea',required:true},file];}
 if(kind==='handover'){formTitle.value='接班人员本人核对';fields.value=[{key:'outgoingShiftId',label:'交班班次',type:'select',required:true,options:context.outgoing.map((s:any)=>({value:s.shift.id,label:`${time(s.shift.value.planned.from)} — ${time(s.shift.value.planned.to)} · ${s.shift.id}`}))},...handoverReadFields.slice(1)];}
 await nextTick();if(current(context))formRef.value?.open(initial);
}
function current(c:Context){return alive && c.generation===generation && c.epoch===formEpoch.value && c.actor===auth.userInfo?.id && editing===c && show.value && c.warehouse===warehouseId.value;}
async function submit(value:Record<string,any>,c:Context){
 // The callback owns a cloned record, while the current editor owns the same request identity.
 if(!alive || c.generation!==generation || c.epoch!==formEpoch.value || c.actor!==auth.userInfo?.id || editing?.requestId!==c.requestId || !show.value || c.warehouse!==warehouseId.value)return false;
 if((c.kind==='schedule' || c.kind==='cancel') ? !canManage.value : !canAttend(c.row))return false;
 const body:any={...cloneDeep(value),requestId:c.requestId};let suffix='';
 const date=(v:any)=>v==null?null:Number(v);
 if(c.kind==='schedule'){body.planned={from:date(body.from),to:date(body.to)};delete body.from;delete body.to;}
 if(c.kind==='attend'){suffix=`/${c.row.shift.id}/attendance`;body.startedAt=date(body.startedAt);body.endedAt=date(body.endedAt);body.previousId=c.row.attendance.at(-1)?.id;}
 if(c.kind==='cancel')suffix=`/${c.row.shift.id}/cancel`;
 if(c.kind==='handover'){suffix=`/${c.row.shift.id}/handover`;const outgoing=c.outgoing.find(s=>s.shift.id===body.outgoingShiftId);if(!outgoing)throw new Error('请选择实际交班班次');body.outgoingAttendanceId=outgoing.attendance.at(-1).id;body.incomingAttendanceId=c.row.attendance.at(-1).id;body.handedOverAt=date(body.handedOverAt);}
 const result=await request({url:`/collection/warehouse/${c.warehouse}/security-duty${suffix}`,method:'post',data:body});
 if(!alive || c.generation!==generation || c.epoch!==formEpoch.value || c.actor!==auth.userInfo?.id || !show.value)return false;
 return await refresh() ? result : false;
}
defineExpose({open,close});
</script>
