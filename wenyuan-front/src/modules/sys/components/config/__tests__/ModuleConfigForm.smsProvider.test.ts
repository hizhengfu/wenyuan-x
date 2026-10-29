// @vitest-environment happy-dom
import { afterEach, describe, expect, it } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { nextTick } from 'vue';
import { NFormItem, NInput, NSelect } from 'naive-ui';
import ModuleConfigForm from '../ModuleConfigForm.vue';
import { groupRows, type ConfigRow } from '../moduleKeys';

let wrapper: VueWrapper;
const providerKey = 'NOTIFY_SMS_PROVIDER';
const aliyunKeys = ['NOTIFY_SMS_ACCESS_KEY_ID', 'NOTIFY_SMS_ACCESS_KEY_SECRET', 'NOTIFY_SMS_SIGN'];
const ctKeys = ['NOTIFY_SMS_CT_HTTP_ENDPOINT', 'NOTIFY_SMS_CT_HTTP_SECRET'];
const yunxinKeys = ['NOTIFY_SMS_YUNXIN_ENDPOINT', 'NOTIFY_SMS_YUNXIN_USERNAME'];

function rows(provider = 'ALIYUN'): ConfigRow[] {
  return [
    { name: providerKey, content: provider, fieldType: 'ENUM', fieldGroup: '短信', label: '短信服务商',
      required: true, enumValues: ['ALIYUN', 'CT_HTTP', 'YUNXIN'], enumLabels: { ALIYUN: '阿里云', CT_HTTP: '电信 HTTP', YUNXIN: '移动云信' } },
    { name: 'NOTIFY_SMS_ENABLED', content: 'TRUE', fieldType: 'BOOLEAN', fieldGroup: '短信', label: '短信通道启用' },
    ...aliyunKeys.map((name) => ({ name, content: '', fieldType: 'STRING' as const, fieldGroup: '短信-阿里云', visibleWhen: { field: providerKey, equals: 'ALIYUN' },
      label: `阿里云 ${name}`, required: true, isPrivate: name.endsWith('SECRET') })),
    ...ctKeys.map((name) => ({ name, content: 'ct-original', fieldType: 'STRING' as const, fieldGroup: '短信-电信HTTP', required: true, visibleWhen: { field: providerKey, equals: 'CT_HTTP' } })),
    ...yunxinKeys.map((name) => ({ name, content: 'yunxin-original', fieldType: 'STRING' as const, fieldGroup: '短信-移动云信', required: true, visibleWhen: { field: providerKey, equals: 'YUNXIN' } })),
    { name: 'NOTIFY_EMAIL_HOST', content: 'smtp.example.test', fieldType: 'STRING', fieldGroup: '邮件', required: true },
  ];
}
function render(configRows = rows()) {
  wrapper = mount(ModuleConfigForm, {
    props: { id: 'notify', groups: groupRows(configRows) },
    global: { stubs: { teleport: true, IconMdiCheck: true, IconMdiRestore: true } },
  });
  return wrapper;
}
function visibleKeys() { return wrapper.findAllComponents(NFormItem).map((item) => item.props('path')); }
async function select(provider: string) {
  wrapper.getComponent(NSelect).vm.$emit('update:value', provider);
  await nextTick();
}
function input(name: string) {
  return wrapper.findAllComponents(NFormItem).find((item) => item.props('path') === name)!.getComponent(NInput);
}
async function click(text: string) {
  await wrapper.findAll('button').find((button) => button.text().trim() === text)!.trigger('click');
  await flushPromises();
}
afterEach(() => { wrapper?.unmount(); });

describe('短信服务商配置联动', () => {
  it('即时切换三家服务商字段和空分组，保留公共开关及邮件配置', async () => {
    render();
    expect(visibleKeys()).toEqual([providerKey, 'NOTIFY_SMS_ENABLED', ...aliyunKeys, 'NOTIFY_EMAIL_HOST']);
    expect(wrapper.text()).toContain('短信-阿里云');
    expect(wrapper.text()).not.toContain('短信-电信HTTP');
    expect(wrapper.text()).not.toContain('短信-移动云信');
    await select('CT_HTTP');
    expect(visibleKeys()).toEqual([providerKey, 'NOTIFY_SMS_ENABLED', ...ctKeys, 'NOTIFY_EMAIL_HOST']);
    expect(wrapper.text()).not.toContain('短信-阿里云');
    expect(wrapper.text()).not.toContain('阿里云 NOTIFY_SMS_ACCESS_KEY_ID');
    await select('YUNXIN');
    expect(visibleKeys()).toEqual([providerKey, 'NOTIFY_SMS_ENABLED', ...yunxinKeys, 'NOTIFY_EMAIL_HOST']);
    expect(wrapper.text()).not.toContain('短信-电信HTTP');
  });


  it('直接使用后端分组，同时保留管理员自定义分组', () => {
    const configRows = rows();
    const grouped = groupRows(configRows);
    expect(grouped.find((group) => group.title === '短信')!.fields.map((row) => row.name))
      .toEqual([providerKey, 'NOTIFY_SMS_ENABLED']);
    expect(grouped.find((group) => group.title === '短信-阿里云')!.fields.map((row) => row.name)).toEqual(aliyunKeys);
    expect(configRows.find((row) => row.name === aliyunKeys[0])!.fieldGroup).toBe('短信-阿里云');
    configRows.find((row) => row.name === aliyunKeys[0])!.fieldGroup = '本馆自定义短信分组';
    expect(groupRows(configRows).find((group) => group.title === '本馆自定义短信分组')!.fields[0].name).toBe(aliyunKeys[0]);
  });

  it('按已保存的服务商初始化，字段标签和分组改名不影响联动', () => {
    const configRows = rows('YUNXIN');
    configRows.find((row) => row.name === aliyunKeys[0])!.label = '自定义密钥名称';
    configRows.find((row) => row.name === yunxinKeys[0])!.fieldGroup = '短信-云信';
    render(configRows);
    expect(visibleKeys()).toContain(yunxinKeys[0]);
    expect(visibleKeys()).not.toContain(aliyunKeys[0]);
    expect(visibleKeys()).not.toContain(ctKeys[0]);
  });

  it('只校验并提交当前可见字段，隐藏的必填空值和凭据不阻碍保存', async () => {
    render(rows('YUNXIN'));
    await click('保存');
    expect(wrapper.emitted('save')).toHaveLength(1);
    expect(wrapper.emitted('save')![0][0]).toEqual({
      NOTIFY_SMS_PROVIDER: 'YUNXIN', NOTIFY_SMS_ENABLED: 'TRUE',
      NOTIFY_SMS_YUNXIN_ENDPOINT: 'yunxin-original', NOTIFY_SMS_YUNXIN_USERNAME: 'yunxin-original',
      NOTIFY_EMAIL_HOST: 'smtp.example.test',
    });
  });

  it('当前服务商缺少必填值仍阻止保存', async () => {
    render(rows('YUNXIN'));
    input(yunxinKeys[1]).vm.$emit('update:value', '');
    await nextTick();
    await click('保存');
    expect(wrapper.emitted('save')).toBeUndefined();
    input(yunxinKeys[1]).vm.$emit('update:value', 'updated-account');
    await nextTick();
    await click('保存');
    expect(wrapper.emitted('save')).toHaveLength(1);
  });

  it('来回切换保留未保存输入，重置恢复已保存的服务商和字段值', async () => {
    render();
    input(aliyunKeys[1]).vm.$emit('update:value', 'draft-secret');
    await nextTick();
    await select('YUNXIN');
    await select('ALIYUN');
    expect(input(aliyunKeys[1]).props('value')).toBe('draft-secret');
    await select('YUNXIN');
    await click('重置');
    expect(wrapper.getComponent(NSelect).props('value')).toBe('ALIYUN');
    expect(input(aliyunKeys[1]).props('value')).toBe('');
  });

  it('没有显示条件的旧配置保持常显', () => {
    const configRows = rows().filter((row) => row.name !== providerKey).map((row) => ({ ...row, visibleWhen: null }));
    render(configRows);
    expect(visibleKeys()).toEqual(configRows.map((row) => row.name));
  });
});
