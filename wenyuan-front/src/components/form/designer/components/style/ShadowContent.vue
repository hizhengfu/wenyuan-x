<template>
  <div class="_fd-shadow-content">
    <n-form label-placement="top" inline class="_fd-sc-form" size="small">
      <n-grid cols="24" x-gap="6">
        <n-grid-item span="12">
          <n-form-item :label="t('style.shadow.mode')">
            <n-radio-group v-model:value="form.type" @change="onInput" size="small" class="_fd-sc-radio">
              <template v-for="item in options" :key="item.key">
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-radio-button :label="item.key" :value="item.key">
                      <i class="fc-icon" :class="'icon-' + item.icon"></i>
                    </n-radio-button>
                  </template>
                  {{ t('style.shadow.' + item.key) }}
                </n-tooltip>
              </template>
            </n-radio-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item :label="t('style.color')">
            <ColorInput v-model:value="form.color" @change="onInput"></ColorInput>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item :label="t('style.shadow.x')">
            <n-input-group>
              <n-input-number :show-button="false" v-model:value="form.x" @update:value="onInput"></n-input-number>
              <n-select v-model:value="form.x_unit" :options="units" @change="onInput"></n-select>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item :label="t('style.shadow.y')">
            <n-input-group>
              <n-input-number v-model:value="form.y" :show-button="false" @update:value="onInput"></n-input-number>
              <n-select v-model:value="form.y_unit" :options="units" @change="onInput"></n-select>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item :label="t('style.shadow.vague')">
            <n-input-group>
              <n-input-number v-model:value="form.vague" :show-button="false" @update:value="onInput"></n-input-number>
              <n-select v-model:value="form.vague_unit" :options="units" @change="onInput"></n-select>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
        <n-grid-item span="12">
          <n-form-item :label="t('style.shadow.extend')">
            <n-input-group>
              <n-input-number v-model:value="form.extend" :show-button="false" @update:value="onInput"></n-input-number>
              <n-select v-model:value="form.extend_unit" :options="units" @change="onInput"></n-select>
            </n-input-group>
          </n-form-item>
        </n-grid-item>
      </n-grid>
    </n-form>
    <div class="_fd-sc-right">
      <div
        ref="box"
        class="_fd-sc-box"
        :class="down ? 'down' : ''"
        @click="getMouseXY($event, 1)"
        @mousedown="onMousedown"
        @mouseup="onMouseup"
        @mousemove="getMouseXY($event, 0)"
      >
        <span class="spot" :style="spotStyle">
          <i class="spot-id" />
        </span>
        <span class="center-spot" />
        <div class="x-hr" />
        <div class="y-hr" />
      </div>
    </div>
  </div>
</template>

<script>
import { defineComponent } from 'vue';
import ColorInput from './ColorInput.vue';

export default defineComponent({
  name: 'ShadowContent',
  components: { ColorInput },
  inject: ['designer'],
  emits: ['update:modelValue', 'change'],
  props: {
    modelValue: {
      default: '0px 0px 0px rgba(0, 0, 0, 0)',
      type: String,
    },
  },
  data() {
    return {
      max: 24,
      boxSize: 250,
      options: [
        { key: 'external', icon: 'shadow' },
        { key: 'inset', icon: 'shadow-inset' },
      ],
      form: {
        color: '',
        type: 'external',
        x: 0,
        y: 0,
        vague: 0,
        extend: 0,
        x_unit: 'px',
        y_unit: 'px',
        vague_unit: 'px',
        extend_unit: 'px',
      },
      units: ['px', '%', 'rem', 'em', 'vw', 'vh'].map((v) => {
        return { label: v, value: v };
      }),
      down: false,
      position: {
        left: 0,
        top: 0,
      },
    };
  },
  computed: {
    spotStyle() {
      return {
        left: this.position.left + 'px',
        top: this.position.top + 'px',
      };
    },
    t() {
      return this.designer.setupState.t;
    },
  },
  watch: {
    position(n) {
      this.form.x = parseInt(String(((n.left - this.boxSize / 2) / this.boxSize) * this.max));
      this.form.y = parseInt(String(((n.top - this.boxSize / 2) / this.boxSize) * this.max));
      const i = this.max / 2;
      this.form.x = this.form.x < 0 ? Math.max(this.form.x, i * -1) : Math.min(this.form.x, i);
      this.form.y = this.form.y < 0 ? Math.max(this.form.y, i * -1) : Math.min(this.form.y, i);
    },
    modelValue(n) {
      this.initStyle(n);
    },
  },
  methods: {
    getMouseXY(e, force) {
      if (this.down || force) {
        const _box = this.$refs.box.getBoundingClientRect();
        this.position = {
          left: parseInt(String(e.clientX - _box.x)),
          top: parseInt(String(e.clientY - _box.y)),
        };
      }
    },
    onMouseup() {
      this.down = false;
      this.onInput();
    },
    onMousedown(e) {
      this.getMouseXY(e, true);
      this.down = true;
    },
    onInput() {
      const n = this.form;
      let value = `${n.x}${n.x_unit} ${n.y}${n.y_unit} ${n.vague}${n.vague_unit} ${n.extend}${n.extend_unit} ${n.color}`;
      if (`${n.x}${n.y}${n.vague}${n.extend}`.replaceAll('0', '') === '') {
        value = '';
      } else if (n.type === 'inset') {
        value += ' inset';
      }
      this.$emit('update:modelValue', value);
      this.$emit('change', value);
    },
    initStyle(modelValue) {
      if ((this.modelValue || '').indexOf(' inset') > -1) {
        this.form.type = 'inset';
        modelValue = modelValue.replace(' inset', '');
      }
      const shadowData = modelValue.split('rgba');
      let color, shadowValues;

      if (shadowData.length > 1) {
        // 将颜色值和其他阴影值进行分离
        color = 'rgba' + shadowData[1].trim();
        shadowValues = shadowData[0].trim().split(' ');
      } else {
        shadowValues = shadowData[0].trim().split(' ');
        color = shadowValues.pop();
      }
      this.form.color = color || '#000';
      this.form.x = parseInt(shadowValues[0]) || 0;
      this.form.y = parseInt(shadowValues[1]) || 0;
      this.form.vague = parseInt(shadowValues[2]) || 0;
      this.form.extend = parseInt(shadowValues[3]) || 0;

      const getUnit = (value) => {
        return value?.replace(/[-\d.]/g, '') || 'px';
      };
      this.form.x_unit = getUnit(shadowValues[0]);
      this.form.y_unit = getUnit(shadowValues[1]);
      this.form.vague_unit = getUnit(shadowValues[2]);
      this.form.extend_unit = getUnit(shadowValues[3]);
      this.position.left = this.boxSize / 2 + (this.form.x / this.max) * this.boxSize || 0;
      this.position.top = this.boxSize / 2 + (this.form.y / this.max) * this.boxSize || 0;
    },
  },
  mounted() {
    this.initStyle(this.modelValue);
  },
});
</script>

<style>
._fd-shadow-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-direction: column;
}

._fd-sc-form .fc-icon {
  font-size: 12px;
}

._fd-shadow-content .n-form-item {
  width: 50%;
  padding: 0 0 0 10px;
  margin: 0 0 5px !important;
  box-sizing: border-box;
}

._fd-shadow-content .el-input__wrapper {
  flex: 1;
}

._fd-shadow-content ._fd-sc-box {
  width: 250px;
  height: 250px;
  border: 1px solid #ccc;
  border-radius: 5px;
  position: relative;
  cursor: pointer;
  overflow: hidden;
}

._fd-shadow-content ._fd-sc-box .spot {
  position: absolute;
  width: 0;
  height: 0;
  border-radius: 100%;
}

._fd-shadow-content ._fd-sc-box .spot-id {
  position: absolute;
  top: -5px;
  left: -5px;
  width: 10px;
  height: 10px;
  background: #1989fa;
  border-radius: 100%;
  z-index: 9;
}

._fd-shadow-content ._fd-sc-box.down .spot-id {
  box-shadow: 1px 1px 10px 2px #1989fa;
}

._fd-shadow-content ._fd-sc-box .center-spot {
  position: absolute;
  width: 0;
  height: 0;
  top: 125px;
  left: 125px;
  border-radius: 100%;
  background: #1989fa;
}

._fd-shadow-content ._fd-sc-box .x-hr {
  width: 100%;
  position: absolute;
  height: 1px;
  top: 125px;
  background: #ccc;
}

._fd-shadow-content ._fd-sc-box .y-hr {
  height: 100%;
  position: absolute;
  width: 1px;
  left: 125px;
  background: #ccc;
}

._fd-shadow-content .el-select__placeholder {
  text-align: center;
}

._fd-shadow-content .el-input-group__append {
  width: 55px;
  padding: 0;
}

._fd-shadow-content .el-input {
  width: 105px;
}

._fd-shadow-content ._fd-sc-right {
  margin-top: 10px;
}

._fd-shadow-content ._fd-sc-radio {
  width: 105px;
}

._fd-shadow-content ._fd-sc-radio .el-radio-button {
  display: flex;
  flex: 1;
}

._fd-shadow-content ._fd-sc-radio .el-radio-button__inner {
  width: 100%;
}
</style>
