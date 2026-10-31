import { computed, ref, shallowRef, watch } from 'vue';
import { useMasterDataWindow } from './useMasterDataWindow';
import { qualificationCertApi as api } from '../api/qualificationCert';
import type { QualificationCertVo } from '../typings';
import { errorText } from '../utils/restorationError';
type Holder = { orgId: string; holderType: string; holderId: string; holderName: string; revision: string };
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim();
const type = (v: unknown): v is 'ORG' | 'PERSON' => v === 'ORG' || v === 'PERSON';
function body(r: any) {
  if (!r || (r.status !== undefined && r.status !== 200) || r.data == null) throw Error('读取或核对持有方失败，请重新选择');
  return r.data;
}
function parseHolder(h: any, record: QualificationCertVo): Holder {
  if (!h || Array.isArray(h) || !text(h.orgId) || !type(h.holderType) || !text(h.holderId) || h.orgId !== record.orgId || h.holderType !== record.holderType || h.holderId !== record.holderId || !text(h.holderName) || !text(h.revision)) {
    throw Error('持有方资料与本次原馆或选择不一致，请重新选择核对');
  }
  return { ...h };
}
export function useQualificationWindow(refresh: () => unknown) {
  const holder = shallowRef<Holder | null>(null), holderLoading = ref(false), holderError = ref(''), holderKey = ref(0);
  let contextEpoch = 0;
  let s!: ReturnType<typeof useMasterDataWindow<QualificationCertVo>>;
  function clearHolder() { holderKey.value++; holder.value = null; holderLoading.value = false; holderError.value = ''; }
  s = useMasterDataWindow<QualificationCertVo>({ label: '资质证书', empty: { enabled: true }, refresh, remove: api.delete,
    context: async (id?: string): Promise<any> => {
      const token = ++contextEpoch;
      const response: any = await api.context(id);
      if (token === contextEpoch && s.formShow.value) {
        const c = body(response);
        if (c.record && c.holder) holder.value = parseHolder(c.holder, c.record);
      }
      return response;
    },
    save: (record, orgId, revision) => {
      if (holderLoading.value || holderError.value || !holder.value) throw Error('请先核对当前证书持有方');
      const h = parseHolder(holder.value, record);
      return api.save(record, orgId, revision, h.revision);
    },
  });
  watch(s.formKey, () => { contextEpoch++; clearHolder(); }, { flush: 'sync' });
  const valid = (key: number, model: QualificationCertVo) => s.formKey.value === key && s.vo.value === model && s.formShow.value
    && !!s.handleSubmit.value && !s.loading.value && !s.submitting.value && !s.error.value && (s.mode.value === 'add' || s.mode.value === 'edit');
  const changeHolderType = computed(() => {
    const key = s.formKey.value, model = s.vo.value, update = s.updateField.value;
    return (value: unknown) => {
      if (!valid(key, model) || value === model.holderType) return;
      clearHolder(); update?.('holderType', type(value) ? value : undefined); update?.('certLevel', undefined); update?.('holderId', undefined); update?.('holderName', undefined);
    };
  });
  const changeHolder = computed(() => {
    const key = s.formKey.value, model = s.vo.value, selectedType = model.holderType, update = s.updateField.value;
    return async (value: unknown) => {
      if (!valid(key, model) || model.holderType !== selectedType || !type(selectedType)) return;
      clearHolder(); const token = holderKey.value;
      update?.('holderId', text(value) ? value : undefined); update?.('holderName', undefined);
      if (!text(value)) return;
      holderLoading.value = true;
      try {
        const response = await api.holderContext(selectedType, value, model.orgId!);
        if (!valid(key, model) || token !== holderKey.value || model.holderId !== value || model.holderType !== selectedType) return;
        holder.value = parseHolder(body(response), model); update?.('holderName', holder.value.holderName);
      } catch (e) {
        if (valid(key, model) && token === holderKey.value) holderError.value = errorText(e, '读取持有方失败');
      } finally { if (token === holderKey.value) holderLoading.value = false; }
    };
  });
  const updateAttachment = computed(() => {
    const key = s.formKey.value, model = s.vo.value, selected = holderKey.value, update = s.updateField.value;
    return (value: unknown) => {
      if (valid(key, model) && selected === holderKey.value && holder.value && !holderLoading.value && !holderError.value) {
        update?.('attachmentFileId', text(value) ? value : undefined);
      }
    };
  });
  const uploadFinished = computed(() => {
    const update = updateAttachment.value;
    return (file: any) => { if (text(file?.id)) update(file.id); };
  });
  const canUpload = computed(() => !!s.handleSubmit.value && !s.loading.value && !s.submitting.value && !s.error.value && !!holder.value && !holderLoading.value && !holderError.value);
  return { ...s, holder, holderLoading, holderError, holderKey, changeHolderType, changeHolder, updateAttachment, uploadFinished, canUpload };
}
