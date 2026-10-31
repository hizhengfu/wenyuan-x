<template>
  <section v-if="actions.length" class="entry-panel" :style="themeStyle" aria-labelledby="collection-entry-title">
    <h2 id="collection-entry-title">常用办理</h2>
    <div class="entry-grid">
      <NButton v-for="action in actions" :key="action.key" secondary :title="action.note"
        :disabled="busy" @click="open(action)"><span>{{ action.label }}</span></NButton>
    </div>
    <HPageForm content-only :enable-cancel="false" v-model:show="selectionVisible" :title="selectionTitle">
      <NAlert v-if="error" type="error">{{ error }}</NAlert>
      <template v-if="activeTool === 'care'">
        <NText depth="3">选择需要检查或养护的藏品，进入其检查、方案与实施记录。</NText>
        <CollectionSelect v-model:value="collectionId" />
      </template>
      <template v-else>
        <NText depth="3">办理馆别由后端设置及当前岗位范围确定。</NText>
        <NSelect v-model:value="orgId" :options="orgs" :loading="busy" :disabled="busy"
          placeholder="选择本次办理的收藏单位" />
        <NButton v-if="error" secondary :loading="busy" @click="loadOrgs">重新读取馆别</NButton>
      </template>
      <NButton type="primary" :disabled="busy || !selectionReady" @click="enter">进入办理</NButton>
    </HPageForm>
    <CollectionManagementRulesDrawer v-if="activeTool === 'rules'" :key="drawerEpoch" ref="rulesRef" />
    <PaperLedgerDrawer v-if="activeTool === 'paper'" :key="drawerEpoch" ref="paperRef" />
    <CollectionCareDrawer v-if="activeTool === 'care'" :key="drawerEpoch" ref="careRef" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useThemeVars } from 'naive-ui';
import { useAuthStore } from '@/store';
import { orgApi } from '../api/org';
import { useCatalogOperation } from '../composables/useCatalogOperation';
import { collectionEntryActions, collectionMenuTarget, readCollectionBusinessContext,
  type CollectionEntryAction } from '../utils/collectionNavigation';
import CollectionManagementRulesDrawer from './CollectionManagementRulesDrawer.vue';
import PaperLedgerDrawer from './PaperLedgerDrawer.vue';
import CollectionCareDrawer from './CollectionCareDrawer.vue';
import CollectionSelect from './CollectionSelect.vue';

const auth = useAuthStore(), router = useRouter(), theme = useThemeVars();
const themeStyle = computed(() => ({ '--entry-background': theme.value.cardColor,
  '--entry-border': theme.value.borderColor, '--entry-title': theme.value.textColor1 }));
const permitted = (codes: string) => auth.hasPermission?.(codes) === true;
const actions = computed(() => collectionEntryActions.filter(action => permitted(action.permissions) &&
  (action.key !== 'care' || permitted('COLLECTION_ADMIN,COLLECTION_SELECT')) &&
  (!('path' in action) || collectionMenuTarget(auth.menus || [], action.path))));
const activeTool = ref<'rules' | 'paper' | 'care'>(), drawerEpoch = ref(0),
  selectionVisible = ref(false), orgId = ref<string | null>(null), collectionId = ref<string | null>(null),
  orgs = ref<{ value: string; label: string }[]>([]), error = ref('');
const rulesRef = ref<InstanceType<typeof CollectionManagementRulesDrawer>>(),
  paperRef = ref<InstanceType<typeof PaperLedgerDrawer>>(), careRef = ref<InstanceType<typeof CollectionCareDrawer>>();
function clear() {
  selectionVisible.value = false; activeTool.value = undefined; drawerEpoch.value++;
  orgs.value = []; orgId.value = collectionId.value = null; error.value = '';
}
const session = useCatalogOperation(clear), busy = session.busy;
watch(() => [auth.userInfo?.id, auth.permissions], () => session.reset(), { deep: true, flush: 'sync' });
const currentAction = computed(() => actions.value.find(action => action.key === activeTool.value));
const selectionTitle = computed(() => currentAction.value?.label || '选择办理对象');
const selectionReady = computed(() => !!currentAction.value && (activeTool.value === 'care'
  ? !!collectionId.value : orgs.value.some(org => org.value === orgId.value)));
async function open(action: CollectionEntryAction) {
  if (!session.active.value || busy.value || !actions.value.some(item => item.key === action.key)) return;
  if ('path' in action) {
    const target = collectionMenuTarget(auth.menus || [], action.path);
    if (target) await router.push(target);
    return;
  }
  session.reset(); activeTool.value = action.key; selectionVisible.value = true;
  if (action.key !== 'care') await loadOrgs();
}
async function loadOrgs() {
  if (!session.active.value || !currentAction.value || activeTool.value === 'care') return;
  const ticket = session.begin('context'); if (ticket === null) return;
  orgs.value = []; orgId.value = null; error.value = '';
  try {
    const response = await orgApi.businessContext();
    if (!session.current(ticket) || !currentAction.value) return;
    const context = readCollectionBusinessContext(response.data);
    orgs.value = context.orgs; orgId.value = context.defaultOrgId;
    if (!orgs.value.length) error.value = '当前没有可办理的收藏单位，请核对岗位及后端馆别设置';
  } catch (cause: any) {
    if (session.current(ticket)) error.value = cause?.message || '办理馆别读取失败，请重试';
  } finally { session.finish(ticket); }
  if (session.current(ticket) && orgId.value) await enter();
}
async function enter() {
  if (!session.active.value || busy.value || !selectionReady.value || !currentAction.value) return;
  const tool = activeTool.value, org = orgs.value.find(item => item.value === orgId.value), id = collectionId.value;
  const actor = auth.userInfo?.id;
  selectionVisible.value = false;
  await nextTick();
  if (!session.active.value || actor !== auth.userInfo?.id || tool !== activeTool.value || !currentAction.value) return;
  if (tool === 'rules' && org) void rulesRef.value?.open({ id: org.value, name: org.label });
  if (tool === 'paper' && org) void paperRef.value?.open({ id: org.value, name: org.label });
  if (tool === 'care' && id) void careRef.value?.open(id);
}
</script>

<style scoped>
.entry-panel { min-width: 0; padding: 16px; border: 1px solid var(--entry-border); border-radius: 10px; background: var(--entry-background); }
h2 { margin: 0 0 12px; color: var(--entry-title); font-size: 14px; font-weight: 700; }
.entry-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.entry-grid :deep(.n-button) { width: 100%; min-width: 0; }
.entry-grid :deep(.n-button__content) { min-width: 0; }
.entry-grid span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@container collection-workbench (max-width: 460px) { .entry-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
