<template>
  <div class="_fc_table_opt">
    <n-data-table
      :columns="column"
      :data="modelValue"
      size="small"
      :single-line="false"
      bordered
    ></n-data-table>
    <n-button link type="primary" @click="add">
      <i class="fc-icon icon-add"></i> {{ t('tableOptions.add') }}
    </n-button>
  </div>
</template>

<script>
import {defineComponent, h} from 'vue';
import {NInput, NIcon} from 'naive-ui';
import Delete from '~icons/mdi/delete'

export default defineComponent({
  name: 'TableOptions',
  inheritAttrs: false,
  props: {
    modelValue: [Object, Array, String]
  },
  inject: ['designer'],
  data() {
    let self = this
    return {
      column: [
        {
          title: 'label', key: 'label', align: 'center',
          render(row, index) {
            return h(NInput, {
              value: row.label,
              size: 'small',
              onUpdateValue(v) {
                row.label = v
              }
            })
          }
        },
        {
          title: 'value', key: 'value', align: 'center',
          render(row, index) {
            return h(NInput, {
              value: row.value + '',
              size: 'small',
              onUpdateValue(v) {
                row.value = v
              }
            })
          }
        },
        {
          title: '', key: 'handle', align: 'center',
          render(row, index) {
            return h(NIcon, {
              onClick(v) {
                self.del(index)
              }
            }, {
              default: () => h(Delete)
            })
          }
        },
      ],
      t: this.designer.setupState.t,
    };
  },
  created() {
    if (!Array.isArray(this.modelValue)) {
      this.$emit('input', []);
    }
  },
  methods: {
    onInput(item) {
      if (item.label && item.value) {
        this.input();
      }
    },
    input() {
      this.$emit('update:modelValue', this.modelValue);
    },
    add() {
      this.modelValue.push(this.column.reduce((initial, v) => {
        initial[v.key] = '';
        return initial;
      }, {}));
    },
    del(idx) {
      this.modelValue.splice(idx, 1);
      this.input(this.modelValue);
    }
  }
});
</script>

<style scoped>
._fc_table_opt {
  width: 100%;
}
</style>
