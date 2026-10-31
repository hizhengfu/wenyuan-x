/** 明确单位的原测量值用于规范显示；十进制整数运算避免浮点舍入改变最后一位。 */
const aliases: Record<string,string> = {厘米:'cm',毫米:'mm',米:'m',千克:'kg',克:'g'};
function parse(value: unknown, declared: unknown, allowed: string[]) {
  const match = /^\+?([0-9]+(?:\.[0-9]+)?|\.[0-9]+)\s*(cm|mm|m|kg|g|厘米|毫米|米|千克|克)?$/i.exec(String(value ?? '').trim());
  if(!match) return null;
  const normalize = (v: unknown) => aliases[String(v ?? '').trim().toLowerCase()] || String(v ?? '').trim().toLowerCase();
  const inline = normalize(match[2]), unit = normalize(declared);
  if(inline && unit && inline !== unit) return null;
  const resolved = inline || unit;
  if(!allowed.includes(resolved)) return null;
  const [whole,fraction=''] = match[1].split('.');
  const amount = BigInt((whole || '0') + fraction);
  if(amount <= 0n) return null;
  return { numerator:amount, denominator:10n ** BigInt(fraction.length), unit:resolved };
}
function fixed(numerator: bigint, denominator: bigint, scale: number) {
  const factor=10n ** BigInt(scale), scaled=numerator*factor;
  const rounded=scaled/denominator + ((scaled%denominator)*2n >= denominator ? 1n : 0n);
  const text=rounded.toString().padStart(scale+1,'0');
  return `${text.slice(0,-scale)}.${text.slice(-scale)}`;
}
export function standardLength(value: unknown, unit?: unknown): string | null {
  const parsed=parse(value,unit,['mm','cm','m']); if(!parsed)return null;
  const n=parsed.numerator*(parsed.unit==='m'?100n:1n), d=parsed.denominator*(parsed.unit==='mm'?10n:1n);
  return n>100n*d ? `${fixed(n,d*100n,2)} m` : `${fixed(n,d,1)} cm`;
}
export function standardMass(value: unknown, unit?: unknown): string | null {
  const parsed=parse(value,unit,['g','kg']); if(!parsed)return null;
  return `${fixed(parsed.numerator,parsed.denominator*(parsed.unit==='g'?1000n:1n),3)} kg`;
}
export function displayMass(value: unknown, unit?: unknown): string | null {
  const raw = String(value ?? '').trim();
  if (!raw) return null;
  const standard = standardMass(value, unit);
  if (standard) return standard;
  const declared = String(unit ?? '').trim();
  return declared ? `${raw}（单位：${declared}，数值或单位待核实）` : `${raw}（数值或单位待核实）`;
}
export function standardDimensions(row: {dimensions?: Record<string,any> | null; dimensionUnit?: string; registrationDetails?: {dimensionUnknownReason?: string}}) {
  const labels: Record<string,string>={height:'通高',mouth:'口径',bottom:'底径',belly:'腹径',length:'长',width:'宽',diameter:'直径'};
  const values={...row.dimensions,...row.dimensions?.extra}; delete values.extra; delete values.description;
  const measured = Object.entries(values).filter(([,v]) => v != null && v !== '').map(([k,v]) => `${labels[k] || k}：${standardLength(v,row.dimensionUnit) || `${v} ${row.dimensionUnit || ''}（数值或单位待核实）`}`).join('；') || (row.registrationDetails?.dimensionUnknownReason ? `未测量：${row.registrationDetails.dimensionUnknownReason}` : '待核实');
  const description = row.dimensions?.extra?.description;
  return description?.trim() ? `${measured}；补充说明：${description}` : measured;
}
