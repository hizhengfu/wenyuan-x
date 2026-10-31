import type {RestorationVo,RestorationProcessVo,RestorationDamageVo,RestorationContractVo,RestorationAcceptanceVo,RestorationArchiveVo} from '../typings';
const record=(v:unknown):v is Record<string,any>=>!!v && typeof v==='object' && !Array.isArray(v);
const text=(v:unknown):v is string=>typeof v==='string' && !!v.trim();
function body(response:unknown,label:string):unknown {
  if(!record(response) || (response.status!==undefined && response.status!==200))throw Error(`${label}读取失败或回执不完整`);
  if(Object.prototype.hasOwnProperty.call(response,'data') && response.data!==undefined)return response.data;
  // ApiData 的空值可能被序列化省略，仅明确成功回执可解释为没有记录。
  if(response.status===200)return undefined;
  throw Error(`${label}回执缺少数据，请重新读取`);
}
type Linked={id?:string;orgId?:string;restorationId?:string;collectionId?:string};
export function restorationDetailResponse(id:string,responses:unknown[]) {
  if(responses.length!==6)throw Error('修复资料响应不完整');
  const p=body(responses[0],'项目');
  if(!record(p) || p.id!==id || !text(p.orgId) || !text(p.collectionId))throw Error('修复项目编号、馆别或实物不一致，请重新读取');
  const orgId=p.orgId,collectionId=p.collectionId;
  function linked(value:unknown,label:string):asserts value is Linked {
    if(!record(value) || !text(value.id) || value.restorationId!==id || value.orgId!==orgId || (value.collectionId!=null && value.collectionId!==collectionId))throw Error(`${label}不属于本馆当前项目及实物，请核对原始关联`);
  }
  function list<T extends Linked>(response:unknown,label:string):T[] {
    const v=body(response,label);if(v===undefined)return [];
    if(!Array.isArray(v))throw Error(`${label}列表格式不正确`);
    const ids=new Set<string>();for(const row of v){linked(row,label);if(ids.has(row.id!))throw Error(`${label}存在重复编号`);ids.add(row.id!);}
    return v as T[];
  }
  function optional<T extends Linked>(response:unknown,label:string):T|null {
    const v=body(response,label);if(v==null)return null;linked(v,label);return v as T;
  }
  return {project:p as RestorationVo,processes:list<RestorationProcessVo>(responses[1],'工序'),damages:list<RestorationDamageVo>(responses[2],'病害'),
    contract:optional<RestorationContractVo>(responses[3],'合同'),acceptance:optional<RestorationAcceptanceVo>(responses[4],'验收'),archive:optional<RestorationArchiveVo>(responses[5],'档案')};
}
