// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import { NFormItem, NInput, NSelect, NSwitch } from 'naive-ui';
import ModuleConfigForm from '../ModuleConfigForm.vue';
import { groupRows, visibleConfigNames, type ConfigRow } from '../moduleKeys';

let wrapper: VueWrapper;
function render(rows: ConfigRow[]) {
  wrapper = mount(ModuleConfigForm, {
    props: { id: 'example', groups: groupRows(rows) },
    global: { stubs: { teleport: true, IconMdiCheck: true, IconMdiRestore: true } },
  });
}
function visibleKeys() { return wrapper.findAllComponents(NFormItem).map((item) => item.props('path')); }
async function choose(value: string) {
  wrapper.getComponent(NSelect).vm.$emit('update:value', value);
  await nextTick();
}
async function click(text: string) {
  await wrapper.findAll('button').find((button) => button.text().trim() === text)!.trigger('click');
  await flushPromises();
}
afterEach(() => { wrapper?.unmount(); });

describe('后端显示条件驱动公共表单', () => {
  it.each(['FILE', 'RESOURCE', 'CUSTOM'])('%s 配置根据元数据联动、验证和提交，保留隐藏输入', async (prefix) => {
    const key = `${prefix}_STORAGE_TYPE`;
    const endpoint = `${prefix}_ENDPOINT`;
    const rows: ConfigRow[] = [
      { name: key, module: 'example', content: 'LOCAL', fieldType: 'ENUM', fieldGroup: '通用',
        enumValues: ['LOCAL', 'RUSTFS'], enumLabels: { LOCAL: '本地', RUSTFS: '对象存储' } },
      { name: endpoint, module: 'example', content: '', fieldType: 'STRING', fieldGroup: '连接参数',
        required: true, visibleWhen: { field: key, equals: 'RUSTFS' } },
      { name: 'COMMON', module: 'example', content: 'keep', fieldType: 'STRING', fieldGroup: '公共参数' },
    ];
    render(rows);
    expect(visibleKeys()).toEqual([key, 'COMMON']);
    expect(wrapper.text()).not.toContain('连接参数');
    expect(wrapper.getComponent(NSelect).props('options')).toEqual([
      { value: 'LOCAL', label: '本地' }, { value: 'RUSTFS', label: '对象存储' },
    ]);
    await click('保存');
    expect(wrapper.emitted('save')![0][0]).toEqual({ [key]: 'LOCAL', COMMON: 'keep' });
    await choose('RUSTFS');
    expect(visibleKeys()).toEqual([key, endpoint, 'COMMON']);
    await click('保存');
    expect(wrapper.emitted('save')).toHaveLength(1);
    const endpointInput = () => wrapper.findAllComponents(NFormItem).find((item) => item.props('path') === endpoint)!.getComponent(NInput);
    endpointInput().vm.$emit('update:value', 'https://draft.example.test');
    await nextTick();
    await choose('LOCAL');
    await click('保存');
    expect(wrapper.emitted('save')![1][0]).toEqual({ [key]: 'LOCAL', COMMON: 'keep' });
    await choose('RUSTFS');
    expect(endpointInput().props('value')).toBe('https://draft.example.test');
    await click('保存');
    expect(wrapper.emitted('save')![2][0]).toEqual({ [key]: 'RUSTFS', [endpoint]: 'https://draft.example.test', COMMON: 'keep' });
    await click('重置');
    expect(wrapper.getComponent(NSelect).props('value')).toBe('LOCAL');
    await choose('RUSTFS');
    expect(endpointInput().props('value')).toBe('');
  });

  it('布尔开关和数字条件可以级联，隐藏父项的旧值不会激活子项', async () => {
    render([
      { name: 'ENABLED', content: 'FALSE', fieldType: 'BOOLEAN' },
      { name: 'COUNT', content: '2', fieldType: 'INT', visibleWhen: { field: 'ENABLED', equals: 'TRUE' } },
      { name: 'DETAIL', content: '', fieldType: 'STRING', required: true, visibleWhen: { field: 'COUNT', equals: '02' } },
    ]);
    expect(visibleKeys()).toEqual(['ENABLED']);
    wrapper.getComponent(NSwitch).vm.$emit('update:value', true);
    await nextTick();
    expect(visibleKeys()).toEqual(['ENABLED', 'COUNT', 'DETAIL']);
    await click('保存');
    expect(wrapper.emitted('save')).toBeUndefined();
    wrapper.getComponent(NSwitch).vm.$emit('update:value', false);
    await nextTick();
    await click('保存');
    expect(wrapper.emitted('save')![0][0]).toEqual({ ENABLED: 'FALSE' });
  });

  it('缺失依赖、跨模块和循环规则不显示，常规字符串保持大小写敏感', () => {
    const rows: ConfigRow[] = [
      { name: 'MODE', fieldType: 'STRING', module: 'one' },
      { name: 'MATCH', module: 'one', visibleWhen: { field: 'MODE', equals: 'Code' } },
      { name: 'CASE', module: 'one', visibleWhen: { field: 'MODE', equals: 'code' } },
      { name: 'CROSS', module: 'two', visibleWhen: { field: 'MODE', equals: 'Code' } },
      { name: 'MISSING', visibleWhen: { field: 'ABSENT', equals: 'x' } },
      { name: 'A', visibleWhen: { field: 'B', equals: 'x' } },
      { name: 'B', visibleWhen: { field: 'A', equals: 'x' } },
    ];
    expect([...visibleConfigNames(rows, { MODE: 'Code', A: 'x', B: 'x' })]).toEqual(['MODE', 'MATCH']);
  });

  it('新加载的元数据改变显示规则，无条件旧配置保持常显', async () => {
    const rows: ConfigRow[] = [
      { name: 'NOTIFY_SMS_PROVIDER', content: 'ALIYUN', fieldType: 'ENUM', enumValues: ['ALIYUN', 'YUNXIN'] },
      { name: 'NOTIFY_SMS_YUNXIN_USERNAME', content: 'account', fieldType: 'STRING' },
    ];
    render(rows);
    expect(visibleKeys()).toHaveLength(2);
    expect(wrapper.getComponent(NSelect).props('options')).toEqual([
      { label: 'ALIYUN', value: 'ALIYUN' }, { label: 'YUNXIN', value: 'YUNXIN' },
    ]);
    await wrapper.setProps({ groups: groupRows([rows[0], { ...rows[1], visibleWhen: { field: rows[0].name, equals: 'YUNXIN' } }]) });
    expect(visibleKeys()).toEqual(['NOTIFY_SMS_PROVIDER']);
    await choose('YUNXIN');
    expect(visibleKeys()).toHaveLength(2);
  });
});
