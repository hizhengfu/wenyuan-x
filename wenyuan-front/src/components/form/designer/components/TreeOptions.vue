<template>
  <div class="_fd-tree-opt">
    <n-tree :data="value" :render-label="renderLabel" :render-suffix="renderSuffix"></n-tree>
  </div>
</template>

<script>
import { defineComponent, h } from 'vue';
import { deepCopy } from '@form-create/utils/lib/deepextend';
import { NInput, NIcon, NButton } from 'naive-ui';
import IconPlus from '~icons/mdi/plus';
import IconFileTree from '~icons/mdi/file-tree';
import IconDelete from '~icons/mdi/delete';

export default defineComponent({
  name: 'TreeOptions',
  emits: ['update:modelValue'],
  props: {
    modelValue: Array,
    columns: Object,
  },
  inject: ['designer'],
  data() {
    return {
      value: [...deepCopy(this.modelValue || [])],
    };
  },
  computed: {
    t() {
      return this.designer.setupState.t;
    },
    overColumns() {
      if (!this.columns) {
        return {
          label: 'label',
          value: 'value',
        };
      }
      return {
        label: this.columns.label || 'label',
        value: this.columns.value || 'value',
      };
    },
  },
  created() {
    if (!this.value.length) {
      this.value = [{}];
    }
  },
  methods: {
    tidyValue() {
      return deepCopy(this.value);
    },
    change() {
      this.$emit('update:modelValue', this.tidyValue());
    },
    add(node) {
      if (node.children) {
        node.children.push({});
      } else {
        node.children = [{}];
      }
    },
    append(data) {
      if (!data.children) {
        data.children = [];
      }
      data.children.push({});
    },
    renderLabel({ option }) {
      return h(NInput, {
        size: 'tiny',
        style: { width: '60px', marginRight: '3px' },
        value: option[this.overColumns.label],
      });
    },
    renderSuffix({ option }) {
      return [
        h(NInput, {
          size: 'tiny',
          style: { width: '110px', marginRight: '3px' },
          value: option[this.overColumns.value],
        }),
        h(
          NButton,
          {
            size: 'tiny',
            onClick: () => {
              this.add(option);
            },
          },
          () => h(NIcon, {}, () => h(IconPlus)),
        ),
        h(
          NButton,
          {
            size: 'tiny',
            onClick: () => {
              this.append(option);
            },
          },
          () => h(NIcon, {}, () => h(IconFileTree)),
        ),
        h(
          NButton,
          {
            size: 'tiny',
            onClick: () => {
              this.remove(option);
            },
          },
          () => h(NIcon, {}, () => h(IconDelete)),
        ),
      ];
    },
    remove(node, data) {
      const parent = node.parent;
      if (parent.data.children) {
        parent.data.children.splice(parent.data.children.indexOf(data), 1);
        if (!parent.data.children.length) {
          delete parent.data.children;
        }
      } else {
        parent.data.splice(parent.data.indexOf(data), 1);
      }
      this.change();
    },
  },
});
</script>

<style>
._fd-tree-opt ._fd-tree-opt-btn {
  height: 19px;
  width: 18px;
  color: #fff;
  text-align: center;
  line-height: 20px;
  padding-bottom: 1px;
  float: left;
  cursor: pointer;
  justify-content: center;
  background-color: #2f73ff;
}

._fd-tree-opt-node {
  display: flex;
  align-items: center;
}

._fd-tree-opt-first {
  width: 60px;
  margin-right: 5px;
}

._fd-tree-opt-last {
  width: 110px;
}

._fd-tree-opt ._fd-tree-opt-danger {
  background-color: #ff2d2e;
  border-radius: 0 2px 2px 0;
}

._fd-tree-opt .el-tree-node__content {
  margin-bottom: 3px;
  height: 28px;
}

._fd-tree-opt .el-input__inner {
  border-right: 0 none;
}

._fd-tree-opt .el-input-group__append {
  width: 90px;
  padding-right: 2px;
  padding-left: 1px;
  background: #fff;
}
</style>
