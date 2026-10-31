export interface EvidenceField {
  key:string; label:string; type?:'text'|'textarea'|'date'|'file'|'user'|'select'|'number'|'datetime'|'group'|'records'|'decimal'|'collection'; required?:boolean; multiple?:boolean; filterable?:boolean;
  packageFilesKey?:string; children?:EvidenceField[]; options?:{label:string;value:string}[]; hint?:string; /** 只读展示时在标签旁显示问号悬停说明 */ labelHelp?:string; min?:number; max?:number;
  unit?:'yuan';
}
