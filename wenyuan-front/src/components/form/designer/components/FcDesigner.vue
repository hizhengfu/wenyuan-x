<template>
  <n-layout
    class="_fc-designer"
    position="absolute"
    has-sider
    @dragenter="handleDragenter"
    @dragleave="handleDragleave"
    @drop="handleDrop"
  >
    <n-layout-sider bordered class="_fc-l" style="width: 266px">
      <n-layout-header bordered style="padding: 10px">
        <n-tabs v-model:value="activeMenuTab">
          <n-tab name="menu">{{ t('menu.component') }}</n-tab>
          <n-tab name="tree">{{ t('menu.tree') }}</n-tab>
        </n-tabs>
      </n-layout-header>
      <n-layout-content position="absolute" style="top: 60px">
        <div v-show="activeMenuTab === 'menu'">
          <template v-for="(item, index) in menuList">
            <div class="_fc-l-group" :key="index" v-if="hiddenMenu.indexOf(item.name) === -1">
              <h4 class="_fc-l-title" @click="item.hidden = !item.hidden">
                {{ t('menu.' + item.name) || item.title }}
                <i class="fc-icon icon-arrow" :class="{ down: !item.hidden }" />
              </h4>
              <fcDraggable
                :group="{ name: 'default', pull: 'clone', put: false }"
                :sort="false"
                itemKey="name"
                class="_fc-l-list"
                :list="item.list"
                v-show="!item.hidden"
              >
                <template #item="{ element }">
                  <div class="_fc-l-item" v-if="hiddenItem.indexOf(element.name) === -1" @click="clickMenu(element)">
                    <div class="_fc-l-icon">
                      <i class="fc-icon" :class="element.icon || 'icon-input'"></i>
                    </div>
                    <span class="_fc-l-name">{{ t('com.' + element.name + '.name') || element.label }}</span>
                  </div>
                </template>
              </fcDraggable>
            </div>
          </template>
        </div>
        <div v-if="activeMenuTab === 'tree'">
          <n-tree
            block-line
            :data="treeInfo"
            key-field="id"
            :render-prefix="renderOutlinePrefix"
            :render-label="renderOutlineLabel"
            :node-props="nodeOutlineProps"
          />
        </div>
      </n-layout-content>
    </n-layout-sider>
    <n-layout class="_fc-m" bordered>
      <n-layout-header class="_fc-m-tools" style="height: 45px">
        <div class="_fc-m-tools-l">
          <template v-if="!inputForm.state">
            <template v-if="getConfig('showDevice') !== false">
              <svg @click="device = 'pc'" class="icon" viewBox="0 0 1024 1024">
                <path
                  d="M15.04 783.36h986.88v49.28H15.04zM163.2 191.36h690.88c27.2 0 49.28 22.08 49.28 49.28v542.72H113.92V240.64c0-27.2 22.08-49.28 49.28-49.28z"
                  :fill="device !== 'pc' ? '#7F7F7F' : '#2E73FF'"
                ></path>
                <path d="M163.2 240.64h690.88v493.44H163.2z" :fill="device !== 'pc' ? '#E5E5E5' : '#E0EAFF'"></path>
              </svg>
              <svg @click="device = 'pad'" class="icon" viewBox="0 0 1024 1024">
                <path
                  d="M217.28 29.76h589.44c29.44 0 53.44 24 53.44 53.44v857.28c0 29.44-24 53.44-53.44 53.44H217.28c-29.44 0-53.44-24-53.44-53.44V83.52c0-29.76 24-53.76 53.44-53.76z"
                  :fill="device !== 'pad' ? '#7F7F7F' : '#2E73FF'"
                ></path>
                <path d="M217.28 136.96h589.44v750.08H217.28z" :fill="device !== 'pad' ? '#E5E5E5' : '#E0EAFF'"></path>
              </svg>
              <svg @click="device = 'mobile'" class="icon" viewBox="0 0 1024 1024">
                <path
                  d="M271.68 31.04h480.96c29.44 0 53.44 24 53.44 53.44v854.72c0 29.44-24 53.44-53.44 53.44H271.68c-29.44 0-53.44-24-53.44-53.44V84.48c0-29.44 23.68-53.44 53.44-53.44z"
                  :fill="device !== 'mobile' ? '#7F7F7F' : '#2E73FF'"
                ></path>
                <path
                  d="M271.68 137.92h480.96v747.84H271.68z"
                  :fill="device !== 'mobile' ? '#E5E5E5' : '#E0EAFF'"
                ></path>
              </svg>
              <div class="line"></div>
            </template>
            <div>
              <i
                class="fc-icon icon-pre-step"
                :class="{ disabled: !operation.list[operation.idx - 1] }"
                @click="prevOperationRecord"
              ></i>
              <i
                class="fc-icon icon-next-step"
                :class="{ disabled: !operation.list[operation.idx + 1] }"
                @click="nextOperationRecord"
              ></i>
            </div>
          </template>
        </div>
        <div class="_fc-m-tools-r">
          <template v-if="!inputForm.state">
            <slot name="handle"></slot>
            <n-button
              class="mr-3"
              v-if="getConfig('showSaveBtn', false)"
              type="success"
              size="small"
              @click="handleSave"
              ><i class="fc-icon icon-save-online"></i> {{ t('props.save') }}
            </n-button>
            <n-button type="primary" size="small" class="mr-3" @click="openPreview"
              ><i class="fc-icon icon-preview"></i> {{ t('props.preview') }}
            </n-button>
            <n-popconfirm
              :positive-text="t('props.clear')"
              :negative-text="t('props.cancel')"
              @positive-click="clearDragRule"
            >
              <template #trigger>
                <n-button type="error" size="small"
                  ><i class="fc-icon icon-delete"></i>{{ t('props.clear') }}
                </n-button>
              </template>
              {{ t('designer.clearWarn') }}
            </n-popconfirm>
            <n-dropdown trigger="click" :options="handle" v-if="handle && handle.length" @select="triggerHandle">
              <n-button size="small">
                <i class="fc-icon icon-more"></i>
              </n-button>
            </n-dropdown>
          </template>
          <div class="line"></div>
          <div class="_fd-input-btn">
            <i class="fc-icon icon-check" v-if="inputCheckStatus"></i><span>{{ t('props.inputData') }}：</span>
            <n-switch size="small" :model-value="inputForm.state" inline-prompt @update:model-value="openInputData" />
          </div>
        </div>
      </n-layout-header>
      <n-layout-content position="absolute" class="_fc-m-con" style="top: 45px">
        <div
          class="_fc-m-drag"
          :class="device"
          ref="dragCon"
          :style="{
            '--fc-drag-empty': `'${t('designer.dragEmpty')}'`,
            '--fc-child-empty': `'${t('designer.childEmpty')}'`,
          }"
        >
          <div class="_fc-m-input" v-if="inputForm.state">
            <ViewForm
              :key="inputForm.key"
              :rule="inputForm.rule"
              :option="inputForm.option"
              v-model:api="inputForm.api"
              :disabled="false"
            ></ViewForm>
          </div>
          <DragForm v-else :rule="dragForm.rule" :option="formOptions" v-model:api="dragForm.api"></DragForm>
        </div>
        <div class="_fc-m-input-handle" v-if="inputForm.state">
          <n-button plain @click="inputClear()">{{ t('props.clear') }}</n-button>
          <n-button plain @click="inputReset()">{{ t('props.reset') }}</n-button>
          <n-button type="primary" plain @click="inputSave()">{{ t('props.save') }}</n-button>
        </div>
      </n-layout-content>
    </n-layout>
    <n-layout-sider
      bordered
      class="_fc-r"
      style="width: 320px; max-width: 320px"
      v-if="!config || config.showConfig !== false"
    >
      <n-layout-header style="height: 40px" class="_fc-r-tabs">
        <div
          class="_fc-r-tab"
          :class="{ active: activeTab === 'props' }"
          v-if="!!activeRule || customForm.isShow || (config && config.showFormConfig === false)"
          @click="activeTab = 'props'"
        >
          {{ t('designer.component') }}
        </div>
        <div
          class="_fc-r-tab"
          v-if="!config || config.showFormConfig !== false"
          :class="{ active: activeTab === 'form' && (!!activeRule || customForm.isShow) }"
          @click="activeTab = 'form'"
        >
          {{ t('designer.form') }}
        </div>
      </n-layout-header>
      <n-layout-content position="absolute" style="top: 40px; padding: 6px">
        <div class="_fc-r-tab-form" v-show="activeTab === 'form'" v-if="!config || config.showFormConfig !== false">
          <DragForm
            :rule="form.rule"
            :option="form.option"
            :modelValue="form.value"
            @change="formOptChange"
            v-model:api="form.api"
          ></DragForm>
        </div>
        <div
          class="_fc-r-tab-props"
          v-show="activeTab === 'props'"
          :key="activeRule ? activeRule._fc_id : customForm.config ? customForm.key : ''"
        >
          <template v-if="activeRule || (customForm.config && (customForm.config.name || customForm.config.label))">
            <p class="_fc-r-title">{{ t('designer.type') }}</p>
            <TypeSelect></TypeSelect>
            <template v-if="activeRule && activeRule.name">
              <p class="_fc-r-title">{{ t('designer.name') }}</p>
              <n-input size="small" class="_fc-r-name-input" :model-value="activeRule.name" readonly>
                <template #prefix>
                  <i class="fc-icon icon-auto" @click="updateName"></i>
                </template>
              </n-input>
            </template>
          </template>
          <n-divider v-if="baseForm.isShow">{{ t('designer.rule') }}</n-divider>
          <DragForm
            v-show="baseForm.isShow"
            v-model:api="baseForm.api"
            :rule="baseForm.rule"
            :option="baseForm.options"
            :modelValue="baseForm.value"
            @change="baseChange"
          ></DragForm>
          <n-divider v-if="propsForm.isShow">{{ t('designer.props') }}</n-divider>
          <DragForm
            v-show="propsForm.isShow"
            v-model:api="propsForm.api"
            :rule="propsForm.rule"
            :option="propsForm.options"
            :modelValue="propsForm.value"
            @change="propChange"
            @removeField="propRemoveField"
          ></DragForm>
          <n-divider v-if="customForm.isShow && customForm.propsShow">
            {{ t('designer.props') }}
          </n-divider>
          <n-divider v-if="styleForm.isShow" id="_fd-config-style">{{ t('designer.style') }}</n-divider>
          <DragForm
            v-show="styleForm.isShow"
            :rule="styleForm.rule"
            :option="styleForm.options"
            :modelValue="styleForm.value"
            @change="styleChange"
            v-model:api="styleForm.api"
          ></DragForm>
          <DragForm
            v-if="customForm.isShow && customForm.propsShow"
            v-model:api="customForm.api"
            :rule="customForm.rule"
            :option="customForm.options"
            :key="customForm.key"
            @change="customFormChange"
          ></DragForm>
          <n-divider v-if="eventShow">
            {{ t('designer.event') }}
          </n-divider>
          <EventConfig
            v-if="eventShow"
            :event-name="(activeRule && activeRule._menu.event) || []"
            :component-name="(activeRule && activeRule._menu.name) || ''"
            :model-value="(activeRule && activeRule._on) || {}"
            @update:modelValue="changeEvent"
          ></EventConfig>
          <template v-if="activeRule">
            <n-divider v-if="validateForm.isShow">{{ t('designer.validate') }}</n-divider>
            <DragForm
              v-if="validateForm.isShow"
              v-model:api="validateForm.api"
              :rule="validateForm.rule"
              :option="validateForm.options"
              :modelValue="validateForm.value"
              @change="validateChange"
              :key="activeRule._fc_id"
            ></DragForm>
          </template>
        </div>
      </n-layout-content>
    </n-layout-sider>
    <n-modal
      preset="card"
      v-model:show="preview.state"
      :segmented="{
        content: 'soft',
        footer: 'soft',
      }"
      size="small"
      bordered
      style="width: 800px; height: 600px"
      class="_fd-preview-dialog"
    >
      <template #header>
        <n-tabs class="_fd-preview-tabs" v-model:value="previewStatus">
          <n-tab name="form">
            {{ t('form.formMode') }}
          </n-tab>
          <n-tab name="component">
            {{ t('form.componentMode') }}
          </n-tab>
        </n-tabs>
      </template>
      <template v-if="previewStatus === 'form'">
        <ViewForm
          :rule="preview.rule"
          :option="preview.option"
          v-model:api="preview.api"
          v-if="preview.state"
        ></ViewForm>
      </template>
      <pre class="_fd-preview-code" v-else><code v-html='preview.html'></code></pre>
    </n-modal>
    <slot></slot>
  </n-layout>
</template>
<script>
import form from '../config/base/form';
import field from '../config/base/field';
import style from '../config/base/style';
import validate from '../config/base/validate';
import { deepCopy } from '@form-create/utils/lib/deepextend';
import is, { hasProperty } from '@form-create/utils/lib/type';
import { lower } from '@form-create/utils/lib/tocase';
import Mitt from '@form-create/utils/lib/mitt';
import ruleList, { defaultDrag } from '../config';
import fcDraggable from 'vuedraggable/src/vuedraggable';
import createMenu from '../config/menu';
import {
  getRuleTree,
  getRuleDescription,
  getFormRuleDescription,
  upper,
  useLocale,
  isNull,
  formTemplate,
  formTemplateV3,
  uniqueArray,
  copyTextToClipboard,
} from '../utils/index';
import viewForm, { designerForm } from '../utils/form';
import { t as globalT } from '../utils/locale';
import EventConfig from './EventConfig.vue';
import {
  computed,
  defineComponent,
  getCurrentInstance,
  markRaw,
  nextTick,
  provide,
  reactive,
  ref,
  toRef,
  toRefs,
  watch,
  h,
} from 'vue';
import uniqueId from '@form-create/utils/lib/unique';
import debounce from '@form-create/utils/lib/debounce';
import errorMessage from '../utils/message';
import hljs from '../utils/highlight/highlight.min';
import xml from '../utils/highlight/xml.min';
import javascript from '../utils/highlight/javascript.min';
import TypeSelect from './TypeSelect.vue';

hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('xml', xml);

export default defineComponent({
  name: 'FcDesigner',
  components: {
    TypeSelect,
    fcDraggable,
    DragForm: designerForm.$form(),
    ViewForm: viewForm.$form(),
    EventConfig,
  },
  props: {
    menu: Array,
    height: [String, Number],
    config: {
      type: Object,
      default: () => ({}),
    },
    mask: {
      type: Boolean,
      default: undefined,
    },
    locale: Object,
    handle: Array,
  },
  emits: ['active', 'create', 'copy', 'delete', 'drag', 'inputData', 'save', 'clear'],
  setup(props) {
    const { menu, height, mask, locale, handle } = toRefs(props);
    const vm = getCurrentInstance();
    const fcx = reactive({ active: null });
    provide('fcx', fcx);
    provide('designer', vm);

    const configRef = toRef(props, 'config', {});
    const baseRule = toRef(configRef.value, 'baseRule', null);
    const componentRule = toRef(configRef.value, 'componentRule', {});
    const validateRule = toRef(configRef.value, 'validateRule', null);
    const formRule = toRef(configRef.value, 'formRule', null);
    const dragHeight = computed(() => {
      const h = height.value;
      if (!h) return '100%';
      return is.Number(h) ? `${h}px` : h;
    });
    const fieldReadonly = computed(() => {
      return configRef.value.fieldReadonly !== false;
    });
    const hiddenMenu = computed(() => {
      return configRef.value.hiddenMenu || [];
    });
    const hiddenItem = computed(() => {
      return configRef.value.hiddenItem || [];
    });
    const hiddenDragMenu = computed(() => {
      return configRef.value.hiddenDragMenu === true;
    });
    const hiddenDragBtn = computed(() => {
      return configRef.value.hiddenDragBtn === true;
    });
    let _t = globalT;
    if (locale.value) {
      _t = useLocale(locale).t;
    }
    const t = (...args) => _t(...args);

    const tidyRuleConfig = (orgRule, configRule, ...args) => {
      if (configRule) {
        if (is.Function(configRule)) {
          return configRule(...args);
        }
        if (configRule.rule) {
          let rule = configRule.rule(...args);
          if (configRule.prepend) {
            rule = [...rule, ...orgRule(...args)];
          } else if (configRule.append) {
            rule = [...orgRule(...args), ...rule];
          }
          return rule;
        }
      }
      return orgRule(...args);
    };

    const data = reactive({
      cacheProps: {},
      operation: {
        idx: -1,
        list: [],
      },
      moveRule: null,
      addRule: null,
      added: null,
      bus: Mitt(),
      device: 'pc',
      activeTab: 'form',
      activeMenuTab: 'menu',
      activeRule: null,
      children: ref([]),
      treeInfo: [],
      menuList: menu.value || createMenu({ t }),
      dragRuleList: {},
      eventShow: false,
      unloadStatus: false,
      previewStatus: 'form',
      t,
      preview: {
        state: false,
        rule: [],
        option: {},
        api: {},
      },
      inputForm: {
        state: false,
        rule: [],
        option: {},
        api: {},
        data: {},
        key: '',
      },
      dragForm: ref({
        rule: [],
        api: {},
      }),
      formOptions: {},
      oldOptionsKeys: [],
      form: {
        rule: tidyRuleConfig(form, formRule.value, { t }),
        api: {},
        option: {
          global: {
            input: {
              modelEmit: 'blur',
            },
            select: {
              props: {
                clearable: true,
              },
            },
          },
          form: {
            labelPlacement: 'top',
            size: 'small',
          },
          submitBtn: false,
        },
        value: {
          form: {},
          submitBtn: false,
        },
      },
      baseForm: {
        isShow: false,
        rule: tidyRuleConfig(field, baseRule.value, { t }),
        api: {},
        value: {},
        options: {
          global: {
            input: {
              modelEmit: 'blur',
            },
          },
          form: {
            labelPlacement: 'top',
            size: 'small',
          },
          submitBtn: false,
          mounted: (fapi) => {
            fapi.activeRule = data.activeRule;
            fapi.setValue(fapi.options.formData || {});
          },
        },
      },
      styleForm: {
        isShow: false,
        rule: style({ t }),
        api: {},
        value: {},
        options: {
          form: {
            labelPlacement: 'left',
            size: 'small',
          },
          submitBtn: false,
          mounted: (fapi) => {
            fapi.activeRule = data.activeRule;
            fapi.setValue(fapi.options.formData || {});
          },
        },
      },
      validateForm: {
        isShow: false,
        rule: tidyRuleConfig(validate, validateRule.value, { t }),
        api: {},
        value: [],
        options: {
          global: {
            input: {
              modelEmit: 'blur',
            },
          },
          form: {
            labelPlacement: 'top',
            size: 'small',
          },
          submitBtn: false,
          mounted: (fapi) => {
            fapi.activeRule = data.activeRule;
            fapi.setValue(fapi.options.formData || {});
          },
        },
      },
      propsForm: {
        isShow: false,
        rule: [],
        api: {},
        value: {},
        options: {
          global: {
            input: {
              modelEmit: 'blur',
            },
            inputNumber: {
              props: {
                controlsPosition: 'right',
              },
            },
          },
          form: {
            labelPlacement: 'top',
            size: 'small',
          },
          submitBtn: false,
          mounted: (fapi) => {
            fapi.activeRule = data.activeRule;
            fapi.setValue(fapi.options.formData || {});
          },
        },
      },
      customForm: {
        isShow: false,
        config: null,
        key: '',
        rule: [],
        api: {},
        options: {
          global: {
            input: {
              modelEmit: 'blur',
            },
          },
          form: {
            labelPlacement: 'top',
            size: 'small',
          },
          submitBtn: false,
        },
      },
    });

    watch(
      () => data.preview.state,
      function (n) {
        if (!n) {
          nextTick(() => {
            data.previewStatus = 'form';
            data.preview.rule = data.preview.option = null;
          });
        }
      },
    );

    let unWatchActiveRule = null;

    const propFieldDeepFn = (field, call) => {
      if (field[10] !== '>') {
        field = field.replace('formCreate', '');
        if (!field) return;
        field = lower(field);
      } else {
        field = field.replace('formCreate>', '');
      }
      const split = field.split('>');
      const lastField = split.pop();
      let source = data.activeRule;
      split.forEach((id, idx) => {
        if (!idx) {
          id = lower(id);
        }
        if (!source[id]) {
          source[id] = {};
        }
        source = source[id];
      });
      call({ source, field: lastField });
    };

    watch(
      () => locale.value,
      (n) => {
        _t = n ? useLocale(locale).t : globalT;
        const formVal = data.form.api.formData && data.form.api.formData();
        const baseFormVal = data.baseForm.api.formData && data.baseForm.api.formData();
        const validateFormVal = data.validateForm.api.formData && data.validateForm.api.formData();
        data.validateForm.rule = tidyRuleConfig(validate, validateRule.value, { t });
        data.baseForm.rule = tidyRuleConfig(field, baseRule.value, { t });
        data.form.rule = tidyRuleConfig(form, formRule.value, { t });
        data.cacheProps = {};
        const rule = data.activeRule;
        let propsVal = null;
        if (rule) {
          propsVal = data.propsForm.api.formData && data.propsForm.api.formData();
          data.propsForm.rule = data.cacheProps[rule._fc_id] = methods.getPropsRule(rule);
        }
        nextTick(() => {
          formVal && data.form.api.setValue(formVal);
          baseFormVal && data.baseForm.api.setValue(baseFormVal);
          validateFormVal && data.validateForm.api.setValue(validateFormVal);
          propsVal && data.propsForm.api.setValue(propsVal);
        });
      },
    );

    const methods = {
      unWatchActiveRule() {
        unWatchActiveRule && unWatchActiveRule();
        unWatchActiveRule = null;
      },
      watchActiveRule() {
        methods.unWatchActiveRule();
        unWatchActiveRule = watch(
          () => data.activeRule,
          function (n) {
            n && methods.updateRuleFormData();
          },
          { deep: true, flush: 'post' },
        );
      },
      makeChildren(children) {
        return reactive({ children }).children;
      },
      addMenu(config) {
        if (!config.name || !config.list) return;
        let flag = true;
        data.menuList.forEach((v, i) => {
          if (v.name === config.name) {
            data.menuList[i] = config;
            flag = false;
          }
        });
        if (flag) {
          data.menuList.push(config);
        }
      },
      removeMenu(name) {
        [...data.menuList].forEach((v, i) => {
          if (v.name === name) {
            data.menuList.splice(i, 1);
          }
        });
      },
      setMenuItem(name, list) {
        data.menuList.forEach((v) => {
          if (v.name === name) {
            v.list = list;
          }
        });
      },
      appendMenuItem(name, item) {
        data.menuList.forEach((v) => {
          if (v.name === name) {
            v.list.push(...(Array.isArray(item) ? item : [item]));
          }
        });
      },
      removeMenuItem(item) {
        data.menuList.forEach((v) => {
          let idx;
          if (is.String(item)) {
            [...v.list].forEach((menu, idx) => {
              if (menu.name === item) {
                v.list.splice(idx, 1);
              }
            });
          } else {
            if ((idx = v.list.indexOf(item)) > -1) {
              v.list.splice(idx, 1);
            }
          }
        });
      },
      addComponent(component) {
        if (Array.isArray(component)) {
          component.forEach((v) => {
            data.dragRuleList[v.name] = v;
            v.menu && methods.appendMenuItem(v.menu, v);
          });
        } else {
          data.dragRuleList[component.name] = component;
          component.menu && methods.appendMenuItem(component.menu, component);
        }
      },
      openInputData(state) {
        data.inputForm.state = state === undefined ? !data.inputForm.state : !!state;
        if (data.inputForm.state) {
          data.inputForm.rule = designerForm.parseJson(methods.getJson());
          data.inputForm.option = designerForm.parseJson(methods.getOptionsJson());
          data.inputForm.option.formData = deepCopy(data.inputForm.data);
          data.inputForm.option.submitBtn.show = false;
          data.inputForm.option.resetBtn.show = false;
          methods.clearActiveRule();
        }
      },
      inputSave() {
        const formData = data.inputForm.api.formData();
        Object.keys(formData).forEach((k) => {
          if (isNull(formData[k])) {
            delete formData[k];
          }
        });
        const flag = JSON.stringify(data.inputForm.data) !== JSON.stringify(formData);
        data.inputForm.data = formData;
        data.inputForm.state = false;
        vm.emit('inputData', formData);
        flag && methods.addOperationRecord();
      },
      inputClear() {
        methods.inputReset({});
      },
      inputReset(formData) {
        data.inputForm.rule = designerForm.parseJson(methods.getJson());
        data.inputForm.option.formData = formData || deepCopy(data.inputForm.data);
        data.inputForm.key = uniqueId();
      },
      setFormData(formData) {
        data.inputForm.data = formData || {};
      },
      getFormData() {
        return data.inputForm.data;
      },
      getParent(rule) {
        let parent = rule.__fc__.parent.rule;
        const config = parent._menu;
        if (config && config.inside) {
          rule = parent;
          parent = parent.__fc__.parent.rule;
        }
        return { root: parent, parent: rule };
      },
      copyName() {
        copyTextToClipboard(data.activeRule.name);
      },
      updateName() {
        this.activeRule.name = 'ref_' + uniqueId();
      },
      makeDrag(group, tag, children, on, slot) {
        return {
          type: 'DragBox',
          wrap: {
            show: false,
          },
          col: {
            show: false,
          },
          inject: true,
          props: {
            rule: {
              props: {
                tag: 'n-col',
                componentData: { span: 24 },
                group: group === true ? 'default' : group,
                ghostClass: 'ghost',
                animation: 150,
                handle: '._fd-drag-btn',
                emptyInsertThreshold: 0,
                direction: 'vertical',
                itemKey: 'type',
              },
            },
            tag,
          },
          children,
          slot,
          on,
        };
      },
      clearDragRule() {
        methods.setRule([]);
        methods.addOperationRecord();
        data.unloadStatus = false;
        vm.emit('clear');
      },
      makeDragRule(children) {
        return methods.makeChildren([
          methods.makeDrag(true, 'draggable', children, {
            add: (inject, evt) => methods.dragAdd(children, evt),
            end: (inject, evt) => methods.dragEnd(children, evt),
            start: (inject, evt) => methods.dragStart(children, evt),
            unchoose: (inject, evt) => methods.dragUnchoose(children, evt),
          }),
        ]);
      },
      handleSave() {
        // vm.emit('save', {
        //   rule: methods.getJson(),
        //   options: methods.getOptionsJson(),
        // });
        vm.emit('save', {
          rule: methods.getRule(),
          options: methods.getOption(),
        });
      },
      openPreview() {
        data.preview.state = true;
        const rule = methods.getJson();
        const options = methods.getOptionsJson();
        data.preview.rule = designerForm.parseJson(rule);
        data.preview.option = designerForm.parseJson(options);
        const useV2 = methods.getConfig('useTemplate', false);
        data.preview.html = hljs.highlight(useV2 ? formTemplate(rule, options) : formTemplateV3(rule, options), {
          language: 'xml',
        }).value;
      },
      getRule() {
        return methods.parseRule(deepCopy(data.dragForm.rule[0].children));
      },
      getJson() {
        return designerForm.toJson(methods.getRule());
      },
      getOption() {
        const options = deepCopy(data.formOptions);
        Object.keys(options._event || {}).forEach((k) => {
          if (options._event[k]) {
            options[k] = options._event[k];
          }
        });
        delete options._event;
        options.submitBtn = options._submitBtn;
        options.resetBtn = options._resetBtn;
        options.resetBtn.innerText = t('props.reset');
        options.submitBtn.innerText = t('props.submit');
        const formData = deepCopy(data.inputForm.data);
        if (Object.keys(formData).length > 0) {
          options.formData = formData;
        }
        delete options._submitBtn;
        delete options._resetBtn;
        return options;
      },
      getOptions() {
        methods.getOption();
      },
      getOptionsJson() {
        return designerForm.toJson([this.getOption()]).slice(1).slice(0, -1);
      },
      setRule(rules) {
        if (!rules) {
          rules = [];
        }
        data.children = ref(methods.loadRule(is.String(rules) ? designerForm.parseJson(rules) : deepCopy(rules)));
        methods.clearActiveRule();
        data.dragForm.rule = methods.makeDragRule(methods.makeChildren(data.children));
        methods.updateTree();
      },
      setBaseRuleConfig(rule, append) {
        baseRule.value = { rule, append };
        data.baseForm.rule = tidyRuleConfig(field, baseRule.value, { t });
      },
      setComponentRuleConfig(name, rule, append) {
        componentRule.value[name] = { rule, append };
        data.cacheProps = {};
        const activeRule = data.activeRule;
        if (activeRule) {
          const propsVal = data.propsForm.api.formData && data.propsForm.api.formData();
          data.propsForm.rule = data.cacheProps[activeRule._fc_id] = tidyRuleConfig(
            activeRule._menu.props,
            componentRule.value && componentRule.value[activeRule._menu.name],
            activeRule,
            {
              t,
              api: data.dragForm.api,
            },
          );
          nextTick(() => {
            propsVal && data.propsForm.api.setValue(propsVal);
          });
        }
      },
      setValidateRuleConfig(rule, append) {
        validateRule.value = { rule, append };
        data.validateForm.rule = tidyRuleConfig(field, validateRule.value, { t });
      },
      setFormRuleConfig(rule, append) {
        formRule.value = { rule, append };
        data.form.rule = tidyRuleConfig(field, formRule.value, { t });
      },
      clearActiveRule() {
        data.activeRule = null;
        data.customForm.config = null;
        data.activeTab = 'form';
        fcx.active = '';
      },
      setOption(opt) {
        const defOptions = deepCopy(methods.getConfig('formOptions', {}));
        const defForm = defOptions.form || {};
        delete defOptions.form;
        let options = { ...defOptions, ...(is.String(opt) ? JSON.parse(opt) : deepCopy(opt || {})) };
        options.form = {
          inline: false,
          hideRequiredAsterisk: false,
          labelPosition: 'right',
          size: 'default',
          labelWidth: '125px',
          ...defForm,
          ...(options.form || {}),
        };
        options._event = {
          onSubmit: options.onSubmit || '',
          onCreated: options.onCreated || '',
          onMounted: options.onMounted || '',
          onChange: options.onChange || '',
          beforeFetch: options.beforeFetch || '',
        };
        options._resetBtn =
          typeof options.resetBtn === 'object' ? options.resetBtn : { show: options.resetBtn === true };
        options._submitBtn =
          typeof options.submitBtn === 'object' ? options.submitBtn : { show: options.submitBtn !== false };
        options.submitBtn = options.resetBtn = false;
        data.inputForm.data = options.formData || {};
        data.oldOptionsKeys = Object.keys(data.form.value);
        delete options.formData;
        data.formOptions = options;
        methods.updateOptionsValue();
      },
      setOptions(opt) {
        methods.setOption(opt);
      },
      updateOptionsValue() {
        const old = {};
        data.oldOptionsKeys.forEach((k) => {
          old[k] = undefined;
        });
        const value = { ...old, ...data.formOptions.form };
        Object.keys(data.formOptions).forEach((key) => {
          const item = data.formOptions[key];
          value['>' + key] = item;
          if (typeof item === 'object') {
            Object.keys(item).forEach((k) => {
              value[key + '>' + k] = item[k];
            });
          }
        });
        data.form.value = value;
      },
      loadRule(rules, pConfig, template) {
        const loadRule = [];
        rules.forEach((rule) => {
          if (is.String(rule)) {
            return loadRule.push(rule);
          }
          let config = data.dragRuleList[rule._fc_drag_tag] || data.dragRuleList[rule.type];
          if (!config) {
            config = defaultDrag(rule);
            rule._fc_drag_tag = '_';
          }
          if (template) {
            rule._fc_template = template;
          }
          config && config.loadRule && config.loadRule(rule);
          rule.children = methods.loadRule(rule.children || [], config, template);
          if (rule.control) {
            rule._control = rule.control;
            delete rule.control;
          }
          if (rule.computed) {
            rule._computed = rule.computed;
            delete rule.computed;
          }
          if (rule.on) {
            rule._on = rule.on;
            delete rule.on;
          }
          if (config) {
            const slot = rule.slot;
            let _config;
            if (pConfig && pConfig.slot && slot && slot !== 'default') {
              _config = methods.getSlotConfig(pConfig, slot, config);
            }
            delete rule.slot;
            rule = methods.makeRule(_config || config, rule);
            if (slot) {
              rule.slot = slot;
            }
          }
          loadRule.push(rule);
        });
        return loadRule;
      },
      parseRule(children, pSlot) {
        return [...children].reduce((initial, rule) => {
          let slot = pSlot;
          if (is.String(rule)) {
            initial.push(rule);
            return initial;
          } else if (rule.type === 'DragBox') {
            initial.push(...methods.parseRule(rule.children, slot || rule.slot));
            return initial;
          } else if (rule.type === 'DragTool') {
            slot = rule.slot || pSlot;
            rule = rule.children[0];
            if (is.String(rule)) {
              initial.push(rule);
              return initial;
            }
            if (rule.type === 'DragBox') {
              initial.push(...methods.parseRule(rule.children, slot || rule.slot));
              return initial;
            }
          }
          if (!rule) return initial;
          rule = { ...rule };
          if (slot && slot !== 'default') {
            rule.slot = slot;
          }
          if (rule.children && rule.children.length) {
            rule.children = methods.parseRule(rule.children);
          }

          delete rule.key;
          delete rule.component;
          if (rule._menu) {
            rule._menu.parseRule && rule._menu.parseRule(rule);
            delete rule._menu;
          }
          if (rule._fc_drag_tag === '_') {
            delete rule._fc_drag_tag;
          }
          if (rule._control) {
            rule.control = rule._control;
            delete rule._control;
          }
          if (rule._computed) {
            rule.computed = rule._computed;
            delete rule._computed;
          }
          if (!rule.slot) {
            delete rule.slot;
          }
          if (rule._on) {
            rule.on = rule._on;
            delete rule._on;
          }
          rule.props &&
            Object.keys(rule.props).forEach((k) => {
              const v = rule.props[k];
              if (isNull(v)) {
                delete rule.props[k];
              }
            });
          Object.keys(rule)
            .filter(
              (k) =>
                k.indexOf('__') === 0 ||
                (Array.isArray(rule[k]) && rule[k].length === 0) ||
                (is.Object(rule[k]) && Object.keys(rule[k]).length === 0),
            )
            .forEach((k) => {
              delete rule[k];
            });
          initial.push(rule);
          return initial;
        }, []);
      },
      fields() {
        return data.dragForm.api
          .all()
          .map((rule) => rule.field)
          .filter((id) => !!id);
      },
      baseChange(field, value, _, fapi) {
        methods.handleChange('', field, value, _, fapi);
      },
      formOptChange(field, value) {
        data.form.value[field] = value;
        if (field.indexOf('>') === -1) {
          field = 'form>' + field;
        }
        let source = data.formOptions;
        const split = field.split('>');
        const lastField = split.pop();
        split.forEach((k) => {
          if (k) {
            if (!source[k]) {
              source[k] = {};
            }
            source = source[k];
          }
        });
        source[lastField] = value;
      },
      propRemoveField(field, _, fapi) {
        if (data.activeRule && fapi[data.activeRule._fc_id] === data.activeRule) {
          methods.unWatchActiveRule();
          const org = field;
          data.dragForm.api.sync(data.activeRule);
          if (field.indexOf('__') !== 0) {
            if (field === 'formCreateChild') {
              delete data.activeRule.children[0];
            } else if (field.indexOf('formCreate') === 0 || field.indexOf('>') > 0) {
              if (field.indexOf('formCreate') < 0) {
                field = 'props>' + field;
              }
              propFieldDeepFn(field, ({ source, field }) => {
                delete source[field];
              });
            } else {
              delete data.activeRule.props[field];
            }
          }
          methods.watchActiveRule();
          data.activeRule._menu?.watch?.[org]?.({
            field: org,
            value: undefined,
            api: fapi,
            rule: data.activeRule,
            ctx: vm,
          });
        }
      },
      propChange(field, value, _, fapi) {
        methods.handleChange('props', field, value, _, fapi);
      },
      styleChange(field, value, _, fapi) {
        if (data.customForm.config) {
          return data.customForm.config.style.change(field, value);
        }
        methods.handleChange('', field, value, _, fapi);
      },
      handleChange(key, field, value, _, fapi) {
        if (data.activeRule && fapi[data.activeRule._fc_id] === data.activeRule) {
          methods.unWatchActiveRule();
          const org = field;
          if (field.indexOf('__') !== 0) {
            if (field === 'formCreateChild') {
              data.activeRule.children[0] = value;
            } else if (field.indexOf('formCreate') === 0 || field.indexOf('>') > 0) {
              if (field.indexOf('formCreate') < 0) {
                field = (key ? key + '>' : '') + field;
              }
              propFieldDeepFn(field, ({ source, field }) => {
                if (isNull(value)) {
                  delete source[field];
                } else {
                  source[field] = value;
                }
              });
            } else {
              if (key && isNull(value)) {
                delete data.activeRule[key][field];
              } else {
                (key ? data.activeRule[key] : data.activeRule)[field] = value;
              }
            }
          }
          methods.watchActiveRule();
          data.activeRule._menu?.watch?.[org]?.({
            field: org,
            value,
            api: fapi,
            rule: data.activeRule,
            ctx: vm,
          });
        }
      },
      validateChange(field, value, _, fapi) {
        if (!data.activeRule || data.validateForm.api[data.activeRule._fc_id] !== data.activeRule) return;
        methods.handleChange('', field, value, _, fapi);
        data.dragForm.api.refreshValidate();
        data.dragForm.api.nextTick(() => {
          data.dragForm.api.clearValidateState(data.activeRule.__fc__.id);
        });
      },
      triggerActive(rule) {
        let dragTool;
        if (is.String(rule)) {
          rule = methods.findRule(rule);
        }
        if (!rule) {
          return;
        }
        if (rule._menu.inside) {
          dragTool = rule.children[0];
        } else {
          dragTool = rule.__fc__.parent.rule;
        }
        if (dragTool && dragTool.type === 'DragTool') {
          const el = data.dragForm.api.el(dragTool.__fc__.id);
          if (el) {
            fcx.active = el.id;
            vm.emit('active', rule);
            methods.toolActive(rule);
          }
        }
      },
      customFormChange(field, value) {
        if (data.customForm.config) {
          data.customForm.config.change(field, value);
        }
      },
      customActive(config) {
        data.baseForm.isShow = false;
        data.propsForm.isShow = false;
        data.eventShow = false;
        data.validateForm.isShow = false;

        data.styleForm.isShow = !!config.style && methods.getConfig('showStyleForm') !== false;

        data.activeRule = null;

        data.customForm.config = config;
        data.customForm.isShow = true;
        data.customForm.propsShow = config.props && methods.getConfig('showPropsForm') !== false;
        data.customForm.key = uniqueId();
        data.customForm.rule = data.customForm.propsShow ? config.props({ t }) : [];
        data.customForm.options.formData = config.formData;
        if (config.style) {
          data.styleForm.value = config.style.formData || {};
        }
        nextTick(() => {
          data.activeTab = 'props';
        });
      },
      getPropsRule(rule) {
        let propsRule = tidyRuleConfig(
          rule._menu.props,
          componentRule.value && componentRule.value[rule._menu.name],
          rule,
          {
            t,
            api: data.dragForm.api,
          },
        );
        if (componentRule.value && componentRule.value.default) {
          const def = componentRule.value.default;
          propsRule = tidyRuleConfig(
            () => propsRule,
            is.Function(def)
              ? {
                  rule: def,
                  append: true,
                }
              : def,
            rule,
            {
              t,
              api: data.dragForm.api,
            },
          );
        }
        return propsRule;
      },
      findRule(id) {
        let rule = undefined;
        const findTree = (children) => {
          children.forEach((item) => {
            if ([item.rule.field, item.rule.name, item.rule._fc_id].indexOf(id) > -1) {
              rule = item.rule;
            } else if (item.children) {
              findTree(item.children);
            }
          });
        };
        findTree(data.treeInfo);
        return rule;
      },
      toolActive(rule) {
        methods.unWatchActiveRule();
        data.customForm.isShow = false;
        data.customForm.config = null;
        if (data.activeRule) {
          delete data.propsForm.api[data.activeRule._fc_id];
          delete data.baseForm.api[data.activeRule._fc_id];
          delete data.validateForm.api[data.activeRule._fc_id];
          delete data.styleForm.api[data.activeRule._fc_id];
          delete data.dragForm.api.activeRule;
        }
        data.activeRule = rule;
        data.dragForm.api.activeRule = rule;

        nextTick(() => {
          data.activeTab = 'props';
          nextTick(() => {
            data.propsForm.api[data.activeRule._fc_id] = data.activeRule;
            data.baseForm.api[data.activeRule._fc_id] = data.activeRule;
            data.validateForm.api[data.activeRule._fc_id] = data.activeRule;
            data.styleForm.api[data.activeRule._fc_id] = data.activeRule;
          });
        });
        if (!data.cacheProps[rule._fc_id]) {
          data.cacheProps[rule._fc_id] = methods.getPropsRule(rule);
        }
        const hiddenItemConfig = methods.getConfig('hiddenItemConfig', {});
        const disabledItemConfig = methods.getConfig('disabledItemConfig', {});
        const hiddenField = uniqueArray([
          ...(hiddenItemConfig?.default || []),
          ...(hiddenItemConfig?.[rule._menu.name] || []),
          ...(rule._menu.hiddenBaseField || []),
        ]);
        const disabledField = uniqueArray([
          ...(disabledItemConfig?.default || []),
          ...(disabledItemConfig?.[rule._menu.name] || []),
        ]);
        data.baseForm.api.hidden(false);
        data.baseForm.api.disabled(false);
        if (hiddenField.length) {
          data.baseForm.api.hidden(true, hiddenField);
          nextTick(() => {
            data.propsForm.api.hidden(true, hiddenField);
          });
        }
        if (disabledField.length) {
          data.baseForm.api.disabled(true, disabledField);
          nextTick(() => {
            data.propsForm.api.disabled(true, disabledField);
          });
        }
        if (!this.getConfig('showControl', true)) {
          data.baseForm.api.hidden(true, '_control');
        }
        const input = hasProperty(rule, 'field');
        data.baseForm.isShow = input && rule.input !== false && methods.getConfig('showBaseForm') !== false;
        data.propsForm.isShow = data.cacheProps[rule._fc_id].length > 0 && methods.getConfig('showPropsForm') !== false;
        data.eventShow =
          rule._menu.event && rule._menu.event.length > 0 && methods.getConfig('showEventForm') !== false;
        data.styleForm.isShow = rule._menu.style !== false && methods.getConfig('showStyleForm') !== false;
        data.validateForm.isShow =
          data.baseForm.isShow && rule._menu.validate !== false && methods.getConfig('showValidateForm') !== false;
        data.propsForm.rule = data.cacheProps[rule._fc_id];
        methods.updateRuleFormData();
        methods.watchActiveRule();
      },
      getConfig(key, def) {
        return configRef.value ? (hasProperty(configRef.value, key) ? configRef.value[key] : def) : def;
      },
      updateRuleFormData() {
        const rule = data.activeRule;
        let formData = {
          formCreateChild: '' + rule.children[0],
          'formCreateWrap>labelWidth': '',
        };
        const appendConfigData = configRef.value.appendConfigData;
        if (is.Function(appendConfigData)) {
          formData = { ...formData, ...appendConfigData(rule) };
        } else if (Array.isArray(appendConfigData)) {
          appendConfigData.forEach((v) => {
            formData[v] = undefined;
          });
        }
        Object.keys(rule).forEach((k) => {
          if (['effect', 'config', 'payload', 'id', 'type', '_menu'].indexOf(k) < 0)
            formData['formCreate' + upper(k)] = deepCopy(rule[k]);
        });
        Object.keys(rule.props).forEach((k) => {
          const item = rule.props[k];
          formData[k] = deepCopy(item);
          if (is.Object(item)) {
            Object.keys(item).forEach((sub) => {
              formData[k + '>' + sub] = deepCopy(item[sub]);
            });
          }
        });
        ['props', 'effect', 'attrs', 'style', 'wrap'].forEach((name) => {
          rule[name] &&
            typeof rule[name] === 'object' &&
            Object.keys(rule[name]).forEach((k) => {
              formData['formCreate' + upper(name) + '>' + k] = deepCopy(rule[name][k]);
            });
        });
        const configAttrs = rule._menu.attrs || {};
        Object.keys(configAttrs).forEach((k) => {
          formData['__' + k] = configAttrs[k]({ rule });
        });
        data.propsForm.value = formData;
        data.styleForm.value = {
          style: rule.style,
          class: rule.class,
          id: rule.id,
        };

        if (data.baseForm.isShow) {
          data.baseForm.value = {
            field: rule.field,
            title: rule.title || '',
            info: rule.info,
            _control: rule._control,
            ...formData,
          };
          data.validateForm.value = {
            validate: rule.validate ? [...rule.validate] : [],
            $required: formData.formCreate$required,
          };
          data.dragForm.api.refreshValidate();
          data.dragForm.api.nextTick(() => {
            data.dragForm.api.clearValidateState(rule.__fc__.id);
          });
        }
      },
      dragStart(children) {
        // console.log('top dragStart')
        data.moveRule = children;
        data.added = false;
      },
      dragUnchoose(children, evt) {
        // console.log('top dragUnchoose')
        data.addRule = {
          children,
          oldIndex: evt.oldIndex,
        };
      },
      clickMenu(menu) {
        methods.dragMenu({ menu, children: data.children, index: data.children.length });
      },
      checkOnly(menu) {
        let flag = false;
        data.dragForm.api.all().forEach((rule) => {
          flag = flag || rule._fc_template === menu.name || (rule._menu && rule._menu.name === menu.name);
        });
        if (flag) {
          errorMessage(data.t('struct.only', { label: t('com.' + menu.name + '.name') || menu.label }));
        }
        return flag;
      },
      dragMenu({ menu, children, index, slot }) {
        if (data.inputForm.state) {
          return;
        }
        if (menu.only && methods.checkOnly(menu)) {
          return;
        }
        methods.handleAddBefore();
        const dragRule = data.dragRuleList[menu.name];
        vm.emit('drag', {
          item: menu,
          dragRule,
        });
        const rule = methods.makeRule(data.dragRuleList[dragRule.name]);
        if (slot) {
          rule.slot = slot;
        }
        children.splice(index, 0, rule);
        const firstRule = rule.type === 'DragTool' ? rule.children[0] : rule;
        methods.handleAddAfter({ rule });
        if (firstRule && methods.getConfig('autoActive', true)) {
          nextTick(() => {
            methods.triggerActive(firstRule);
          });
        }
      },
      replaceField(rule) {
        const flag = ['array', 'object'].indexOf(rule._menu.subForm) > -1;
        let temp = methods.parseRule(deepCopy([rule]))[0];
        if (flag) {
          temp.field = uniqueId();
        }
        temp = designerForm.toJson(temp);
        if (flag) {
          temp = methods.batchReplaceUni(temp);
        } else {
          temp = methods.batchReplaceField(temp);
        }
        return methods.loadRule([designerForm.parseJson(temp)])[0];
      },
      batchReplaceField(json) {
        const regex = /"field"\s*:\s*"(\w[\w\d]+)"/g;
        const matches = [];
        json = json.replace(regex, (match, p1) => {
          const key = uniqueId();
          matches.push({ old: p1, key: key });
          return `"field":"${key}"`;
        });
        return methods.batchReplaceUni(json);
      },
      batchReplaceUni(json) {
        const regex = /"_fc_id"\s*:\s*"(\w[\w\d]+)"/g;
        json = json.replace(regex, () => {
          return `"_fc_id":"id_${uniqueId()}"`;
        });
        return json;
      },
      dragAdd(children, evt, slot) {
        // console.log('top dragAdd')
        const newIndex = evt.newIndex;
        const menu = evt.item._underlying_vm_;
        if (menu && menu.__fc__) {
          if (data.addRule) {
            methods.handleSortBefore();
            const rule = data.addRule.children.splice(data.addRule.children.indexOf(menu), 1)[0];
            if (slot) {
              rule.slot = slot;
            } else {
              delete rule.slot;
            }
            children.splice(newIndex, 0, rule);
            methods.handleSortAfter({ rule: rule });
          }
        } else {
          methods.dragMenu({ menu, children, index: newIndex, slot });
        }
        data.added = true;
        // data.dragForm.api.refresh();
      },
      dragEnd(children, { newIndex, oldIndex }, slot) {
        // console.log('top dragEnd')
        if (!data.added && !(data.moveRule === children && newIndex === oldIndex)) {
          methods.handleSortBefore();
          const rule = data.moveRule.splice(oldIndex, 1);
          if (slot) {
            rule.slot = slot;
          }
          children.splice(newIndex, 0, rule[0]);
          methods.handleSortAfter({ rule: rule[0] });
        }
        data.moveRule = null;
        data.addRule = null;
        data.added = false;
        // data.dragForm.api.refresh();
      },
      getSlotConfig(pConfig, slot, config) {
        let slotConfig = {};
        (pConfig.slot || []).forEach((item) => {
          if (item.name === slot) {
            slotConfig = item.config || {};
          }
        });
        return { ...config, dragBtn: false, handleBtn: config.children ? ['addChild'] : false, ...slotConfig };
      },
      makeRule(config, _rule) {
        const rule = _rule || config.rule({ t });
        rule._menu = markRaw(config);
        if (!rule._fc_id) {
          rule._fc_id = 'id_' + uniqueId();
        }
        if (!rule.name) {
          rule.name = 'ref_' + uniqueId();
        }
        if (config.component) {
          rule.component = markRaw(config.component);
        }
        if (!rule._computed) {
          rule._computed = {};
        }
        if (!rule.effect) {
          rule.effect = {};
        }
        if (!hasProperty(rule, 'display')) {
          rule.display = true;
        }
        if (!hasProperty(rule, 'hidden')) {
          rule.hidden = false;
        }
        rule._fc_drag_tag = config.name;
        let only = config.only === true;
        let drag;

        const children = rule.children || [];
        if (config.drag) {
          rule.children = [
            (drag = methods.makeDrag(config.drag, rule._menu ? rule._menu.name : rule.type, children, {
              end: (inject, evt) => methods.dragEnd(inject.self.children, evt),
              add: (inject, evt) => methods.dragAdd(inject.self.children, evt),
              start: (inject, evt) => methods.dragStart(inject.self.children, evt),
              unchoose: (inject, evt) => methods.dragUnchoose(inject.self.children, evt),
            })),
          ];
        }

        if (config.children && !_rule && !children.length) {
          for (let i = 0; i < (config.childrenLen || 1); i++) {
            const child = methods.makeRule(data.dragRuleList[config.children]);
            (drag || rule).children.push(child);
          }
        }
        const dragMask = mask.value !== undefined ? mask.value !== false : config.mask !== false;
        if (config.tool === false) {
          return rule;
        }
        if (config.inside) {
          rule.children = methods.makeChildren([
            {
              type: 'DragTool',
              props: {
                dragBtn: config.dragBtn !== false,
                children: config.children,
                mask: dragMask,
                handleBtn: config.handleBtn,
                only,
              },
              inject: true,
              on: {
                delete: ({ self }) => {
                  const parent = methods.getParent(self).parent;
                  if (methods.handleRemoveBefore({ parent, rule: parent }) !== false) {
                    parent.__fc__.rm();
                    vm.emit('delete', parent);
                    if (data.activeRule === parent) {
                      methods.clearActiveRule();
                    }
                    methods.handleRemoveAfter({ rule: parent });
                  }
                },
                create: ({ self }) => {
                  methods.handleAddBefore();
                  const top = methods.getParent(self);
                  vm.emit('create', top.parent);
                  const rule = methods.makeRule(top.parent._menu);
                  if (top.parent.slot) {
                    rule.slot = top.parent.slot;
                  }
                  top.root.children.splice(top.root.children.indexOf(top.parent) + 1, 0, rule);
                  methods.handleAddAfter({ rule: top.parent });
                },
                addChild: ({ self }) => {
                  methods.handleAddBefore();
                  const top = methods.getParent(self);
                  const config = top.parent._menu;
                  const item = data.dragRuleList[config.children];
                  if (!item) return;
                  const rule = methods.makeRule(item);
                  (!config.drag ? top.parent : top.parent.children[0]).children[0].children.push(rule);
                  methods.handleAddAfter({ rule });
                },
                copy: ({ self }) => {
                  methods.handleCopyBefore();
                  const top = methods.getParent(self);
                  vm.emit('copy', top.parent);
                  const temp = methods.replaceField(top.parent);
                  top.root.children.splice(top.root.children.indexOf(top.parent) + 1, 0, temp);
                  methods.handleCopyAfter({ rule: top.parent });
                },
                active: ({ self }) => {
                  const top = methods.getParent(self);
                  vm.emit('active', top.parent);
                  setTimeout(() => {
                    methods.toolActive(top.parent);
                  }, 10);
                },
              },
              children: rule.children,
            },
          ]);
          return rule;
        } else {
          return {
            type: 'DragTool',
            props: {
              dragBtn: config.dragBtn !== false,
              children: config.children,
              mask: dragMask,
              handleBtn: config.handleBtn,
              only,
            },
            inject: true,
            display: !!rule.display,
            on: {
              delete: ({ self }) => {
                if (methods.handleRemoveBefore({ parent: self, rule: self.children[0] }) !== false) {
                  vm.emit('delete', self.children[0]);
                  self.__fc__.rm();
                  if (data.activeRule === self.children[0]) {
                    methods.clearActiveRule();
                  }
                  methods.handleRemoveAfter({ rule: self.children[0] });
                }
              },
              create: ({ self }) => {
                methods.handleAddBefore();
                vm.emit('create', self.children[0]);
                const top = methods.getParent(self);
                const rule = methods.makeRule(self.children[0]._menu);
                if (top.parent.slot) {
                  rule.slot = top.parent.slot;
                }
                top.root.children.splice(top.root.children.indexOf(top.parent) + 1, 0, rule);
                methods.handleAddAfter({ rule });
              },
              addChild: ({ self }) => {
                methods.handleAddBefore();
                const config = self.children[0]._menu;
                const item = data.dragRuleList[config.children];
                if (!item) return;
                const rule = methods.makeRule(item);
                (!config.drag ? self : self.children[0]).children[0].children.push(rule);
                methods.handleAddAfter({ rule });
              },
              copy: ({ self }) => {
                methods.handleCopyBefore();
                vm.emit('copy', self.children[0]);
                const top = methods.getParent(self);
                const temp = methods.replaceField(self.children[0]);
                if (self.slot) {
                  temp.slot = self.slot;
                }
                top.root.children.splice(top.root.children.indexOf(top.parent) + 1, 0, temp);
                methods.handleCopyAfter({ rule: self.children[0] });
              },
              active: ({ self }) => {
                vm.emit('active', self.children[0]);
                setTimeout(() => {
                  methods.toolActive(self.children[0]);
                }, 10);
              },
            },
            children: methods.makeChildren([rule]),
          };
        }
      },
      toolHandle(rule, event) {
        if (!rule._fc_drag_tag || rule._menu.tool === false) {
          rule.__fc__.rm();
          return;
        }
        let toolVm;
        if (rule._menu.inside) {
          toolVm = rule.children[0].__fc__.exportEl;
        } else {
          toolVm = rule.__fc__.parent.exportEl;
        }
        toolVm.$emit(event);
      },
      handleAddBefore() {},
      handleRemoveBefore() {},
      handleCopyBefore() {},
      handleSortBefore() {},
      addOperationRecord() {
        const rule = methods.getJson();
        const formData = deepCopy(data.inputForm.data);
        const list = data.operation.list.slice(0, data.operation.idx + 1);
        list.push({ rule, formData });
        data.operation.list = list;
        data.operation.idx = list.length - 1;
        data.unloadStatus = list.length !== 1;
      },
      prevOperationRecord() {
        if (!data.operation.list[data.operation.idx - 1]) {
          return;
        }
        const item = data.operation.list[--data.operation.idx];
        methods.useOperationRecord(item);
        methods.clearActiveRule();
      },
      nextOperationRecord() {
        if (!data.operation.list[data.operation.idx + 1]) {
          return;
        }
        const item = data.operation.list[++data.operation.idx];
        methods.useOperationRecord(item);
        methods.clearActiveRule();
      },
      useOperationRecord(item) {
        data.inputForm.data = item.formData;
        methods.setRule(item.rule);
      },
      handleAddAfter() {
        methods.addOperationRecord();
        methods.updateTree();
      },
      handleRemoveAfter() {
        methods.addOperationRecord();
        methods.updateTree();
      },
      handleCopyAfter() {
        methods.addOperationRecord();
        methods.updateTree();
      },
      handleSortAfter() {
        methods.addOperationRecord();
        methods.updateTree();
      },
      renderOutlinePrefix({ option }) {
        return h('i', { class: `fc-icon ${(option.rule._menu && option.rule._menu.icon) || 'icon-cell'}` });
      },
      renderOutlineLabel({ option }) {
        return (
          (option.rule.title || '').trim() ||
          (option.rule.props && option.rule.props.label) ||
          t('com.' + (option.rule._menu && option.rule._menu.name) + '.name') ||
          option.rule.type
        );
      },
      renderOutlineSuffix({ option }) {


      },
      nodeOutlineProps({ option }) {
        return {
          onClick() {
            methods.triggerActive(option.rule);
          },
        };
      },
      treeChange(data) {
        methods.triggerActive(data.rule);
      },
      getFormDescription() {
        return getFormRuleDescription(methods.getDescription());
      },
      getDescription() {
        return getRuleDescription(data.dragForm.rule[0].children);
      },
      getSubFormDescription(rule) {
        let ctx = rule.__fc__ && rule.__fc__.parent;
        while (ctx) {
          if (ctx.rule._menu && ['array', 'object'].indexOf(ctx.rule._menu.subForm) > -1) {
            return getFormRuleDescription(getRuleDescription(ctx.rule.children));
          } else {
            ctx = ctx.parent;
          }
        }
        return null;
      },
      getSubFormChildren(rule) {
        let ctx = rule.__fc__ && rule.__fc__.parent;
        while (ctx) {
          if (ctx.rule._menu && ['array', 'object'].indexOf(ctx.rule._menu.subForm) > -1) {
            return ctx.rule.children || [];
          } else {
            ctx = ctx.parent;
          }
        }
        return null;
      },
      updateTree: debounce(function () {
        nextTick(() => {
          data.treeInfo = getRuleTree(data.dragForm.rule[0].children);
        });
      }, 300),
      findTree(field) {
        let tree = undefined;
        const findTree = (children) => {
          children.forEach((item) => {
            if (item.rule.field === field) {
              tree = item.children;
            } else if (item.children) {
              findTree(item.children);
            }
          });
        };
        findTree(data.treeInfo);
        return tree || [];
      },
      handleDragenter(e) {
        data.bus.$emit('dragenter', e);
      },
      handleDragleave(e) {
        data.bus.$emit('dragleave', e);
      },
      handleDrop(e) {
        data.bus.$emit('drop', e);
      },
      changeEvent(on) {
        data.activeRule._on = on;
      },
      triggerHandle(key, item) {
        item.handle();
      },
    };
    data.dragForm.rule = methods.makeDragRule(methods.makeChildren(data.children));
    methods.setOption({});
    methods.addComponent(ruleList);

    const inputCheckStatus = computed(() => {
      return Object.keys(data.inputForm.data).length > 0;
    });

    return {
      ...toRefs(data),
      ...methods,
      dragHeight,
      t,
      handle,
      inputCheckStatus,
      fieldReadonly,
      hiddenMenu,
      hiddenItem,
      hiddenDragMenu,
      hiddenDragBtn,
    };
  },
  created() {
    document.body.ondrop = (e) => {
      e.preventDefault();
      e.stopPropagation();
    };
    window.onbeforeunload = (e) => {
      if (this.unloadStatus) {
        e.returnValue = this.t('designer.unload');
      }
    };
  },
});
</script>
