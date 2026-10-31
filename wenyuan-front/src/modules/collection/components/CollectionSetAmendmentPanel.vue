<template>
  <NSpace v-if="value" vertical :size="12">
    <NAlert type="info">套件订正须经批准并完成纸质账订正和签署后生效。解除关系会保留原关系和关联藏品当时身份。</NAlert>
    <NText strong>原关系（{{ value.before.length }} 项）</NText>
    <div class="set-amendment-table">
      <NTable size="small" bordered>
        <thead>
          <tr>
            <th>分件号</th>
            <th>分件名称</th>
            <th>关系</th>
            <th>数量</th>
            <th>单位</th>
            <th>独立藏品原身份</th>
            <th>排序</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in value.before" :key="row.id || index">
            <td>{{ row.partNo }}</td>
            <td>{{ row.partName }}</td>
            <td>{{ relationLabel(row.relation) }}</td>
            <td>{{ row.quantity }}</td>
            <td>{{ row.unit }}</td>
            <td>{{ identity(row.childCollectionId) }}</td>
            <td>{{ row.sort }}</td>
          </tr>
          <tr v-if="!value.before.length">
            <td colspan="7">原来没有分件关系</td>
          </tr>
        </tbody>
      </NTable>
    </div>
    <NSpace align="center"
      ><NText strong>拟变更关系（{{ value.after.length }} 项）</NText>
      <template v-if="editable"
        ><NButton :disabled="disabled" @click="add">新增分件关系</NButton
        ><NButton :disabled="disabled" @click="reset">恢复原关系</NButton></template
      >
    </NSpace>
    <NAlert v-if="value.before.length && !value.after.length" type="warning"
      >本申请拟解除全部分件关系，原记录在批准和签署生效前保持不变。</NAlert
    >
    <div class="set-amendment-table">
      <NTable size="small" bordered>
        <thead>
          <tr>
            <th>分件号</th>
            <th>分件名称</th>
            <th>关系</th>
            <th>数量</th>
            <th>单位</th>
            <th>独立藏品</th>
            <th>排序</th>
            <th v-if="editable">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, index) in value.after" :key="row.id || `new-${index}`">
            <template v-if="editable">
              <td>
                <NInput
                  :aria-label="`第${index + 1}项分件号`"
                  :value="row.partNo"
                  :disabled="disabled"
                  :maxlength="32"
                  @update:value="update(index, { partNo: $event })"
                />
              </td>
              <td>
                <NInput
                  :aria-label="`第${index + 1}项分件名称`"
                  :value="row.partName"
                  :disabled="disabled"
                  :maxlength="200"
                  @update:value="update(index, { partName: $event })"
                />
              </td>
              <td>
                <NSelect
                  :aria-label="`第${index + 1}项关系`"
                  :value="row.relation"
                  :disabled="disabled"
                  :options="relations"
                  @update:value="update(index, { relation: $event })"
                />
              </td>
              <td>
                <NInputNumber
                  :aria-label="`第${index + 1}项数量`"
                  :value="row.quantity"
                  :disabled="disabled"
                  :min="1"
                  :precision="0"
                  @update:value="update(index, { quantity: $event ?? 0 })"
                />
              </td>
              <td>
                <NInput
                  :aria-label="`第${index + 1}项计量单位`"
                  :value="row.unit"
                  :disabled="disabled"
                  :maxlength="16"
                  @update:value="update(index, { unit: $event })"
                />
              </td>
              <td>
                <CollectionSelect
                  :value="row.childCollectionId"
                  :org-id="orgId"
                  :disabled="disabled"
                  placeholder="可选择本馆独立藏品"
                  @update:value="update(index, { childCollectionId: $event || null })"
                /><NText depth="3">{{ identity(row.childCollectionId) }}</NText>
              </td>
              <td>
                <NInputNumber
                  :aria-label="`第${index + 1}项排序`"
                  :value="row.sort"
                  :disabled="disabled"
                  :precision="0"
                  @update:value="update(index, { sort: $event ?? 0 })"
                />
              </td>
              <td><NButton :disabled="disabled" type="warning" @click="remove(index)">拟解除</NButton></td>
            </template>
            <template v-else
              ><td>{{ row.partNo }}</td>
              <td>{{ row.partName }}</td>
              <td>{{ relationLabel(row.relation) }}</td>
              <td>{{ row.quantity }}</td>
              <td>{{ row.unit }}</td>
              <td>{{ identity(row.childCollectionId) }}</td>
              <td>{{ row.sort }}</td></template
            >
          </tr>
          <tr v-if="!value.after.length">
            <td :colspan="editable ? 8 : 7">拟变更后没有分件关系</td>
          </tr>
        </tbody>
      </NTable>
    </div>
    <NText v-if="editable" depth="3">新增独立藏品的原身份在保存后由系统核定。请保存并核对后，再提交审批。</NText>
    <NText strong>申请记录的关联藏品身份</NText>
    <ul>
      <li v-for="item in value.identities" :key="item.collectionId">
        {{ item.totalRegNo }} · {{ item.name }}（原馆：{{ item.orgId }}）
      </li>
    </ul>
  </NSpace>
</template>
<script setup lang="ts">
import { cloneDeep } from 'lodash-es';
import CollectionSelect from './CollectionSelect.vue';
import type { SetAmendment, SetRelationSnapshot } from '../api/change';
const props = defineProps<{ value?: SetAmendment; editable?: boolean; disabled?: boolean; orgId?: string }>();
const emit = defineEmits<{ (e: 'update:value', value: SetAmendment): void }>();
const relations = [
  { label: '分件', value: 'PART' },
  { label: '附件', value: 'ATTACH' },
];
function relationLabel(value: string) {
  return { PART: '分件', ATTACH: '附件', MAIN: '主件' }[value] || value;
}
function identity(id?: string | null) {
  if (!id) return '未关联独立藏品';
  const item = props.value?.identities.find((i) => i.collectionId === id);
  return item ? `${item.totalRegNo} · ${item.name}（原馆：${item.orgId}）` : `已选择 ${id}，保存后核对原身份`;
}
function change(action: (value: SetAmendment) => void) {
  if (!props.value || !props.editable || props.disabled) return;
  const next = cloneDeep(props.value);
  action(next);
  emit('update:value', next);
}
function update(index: number, fields: Partial<SetRelationSnapshot>) {
  change((v) => {
    if (v.after[index]) Object.assign(v.after[index], fields);
  });
}
function add() {
  change((v) =>
    v.after.push({ partNo: '', partName: '', relation: 'PART', quantity: 1, unit: '件', sort: v.after.length }),
  );
}
function remove(index: number) {
  change((v) => v.after.splice(index, 1));
}
function reset() {
  change((v) => {
    v.after = cloneDeep(v.before);
  });
}
</script>
<style scoped>
.set-amendment-table {
  overflow-x: auto;
}
.set-amendment-table table {
  min-width: 900px;
}
.set-amendment-table td {
  min-width: 85px;
  vertical-align: top;
}
.set-amendment-table td:nth-child(2),
.set-amendment-table td:nth-child(6) {
  min-width: 190px;
}
</style>
