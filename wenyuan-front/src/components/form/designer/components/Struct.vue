<template>
  <div class="_fd-struct">
    <n-badge type="warning" is-dot :hidden="!configured">
      <n-button @click="visible = true" size="small">{{ title || t('struct.title') }}</n-button>
    </n-badge>
    <n-modal
      class="_fd-struct-con"
      :title="title || t('struct.title')"
      v-model:show="visible"
      preset="card"
      size="small"
      :segmented="{
        content: true,
        action: true,
      }"
      style="width: 800px; height: 600px"
      content-class="_fd-struct"
      content-style="padding:0"
      :mask-closable="false"
    >
      <div style="height: 100%" ref="editor" v-if="visible"></div>
      <template #action>
        <n-flex justify="end">
          <n-button @click="visible = false">{{ t('props.cancel') }}</n-button>
          <n-button type="primary" @click="onOk">{{ t('props.ok') }}</n-button>
        </n-flex>
      </template>
    </n-modal>
  </div>
</template>

<script>
import 'codemirror/lib/codemirror.css';
import CodeMirror from 'codemirror/lib/codemirror';
import 'codemirror/mode/javascript/javascript';
import { deepParseFn, toJSON } from '../utils/index';
import { deepCopy } from '@form-create/utils/lib/deepextend';
import { defineComponent, markRaw } from 'vue';
import is from '@form-create/utils/lib/type';
import errorMessage from '../utils/message';

export default defineComponent({
  name: 'Struct',
  emits: ['update:modelValue'],
  props: {
    modelValue: [Object, Array, Function],
    title: String,
    defaultValue: {
      require: false,
    },
    validate: Function,
  },
  inject: ['designer'],
  computed: {
    t() {
      return this.designer.setupState.t;
    },
    configured() {
      return !is.empty(this.modelValue);
    },
  },
  data() {
    return {
      editor: null,
      visible: false,
      oldVal: null,
    };
  },
  watch: {
    modelValue() {
      this.load();
    },
    visible(n) {
      if (n) {
        this.load();
      }
    },
  },
  methods: {
    load() {
      const val = toJSON(deepParseFn(this.modelValue ? deepCopy(this.modelValue) : this.defaultValue));
      this.oldVal = val;
      this.$nextTick(() => {
        this.editor = markRaw(
          CodeMirror(this.$refs.editor, {
            lineNumbers: true,
            mode: 'javascript',
            lint: true,
            line: true,
            tabSize: 2,
            lineWrapping: true,
            value: val || '',
          }),
        );
      });
    },
    onOk() {
      const str = this.editor.getValue();
      let val;
      try {
        val = new Function('return ' + str)();
      } catch (e) {
        console.error(e);
        errorMessage(this.t('struct.errorMsg'));
        return false;
      }
      if (this.validate && false === this.validate(val)) {
        errorMessage(this.t('struct.errorMsg'));
        return false;
      }
      this.visible = false;
      if (toJSON(val, null, 2) !== this.oldVal) {
        this.$emit('update:modelValue', val);
      }
      return true;
    },
  },
});
</script>

<style>
._fd-struct {
  width: 100%;
}

._fd-struct .CodeMirror {
  height: 100%;
}

._fd-struct .CodeMirror-line {
  line-height: 16px !important;
  font-size: 13px !important;
}

._fd-struct-con .CodeMirror-lint-tooltip {
  z-index: 2021 !important;
}

._fd-struct-con .el-dialog__body {
  padding: 0px 20px;
}
</style>
