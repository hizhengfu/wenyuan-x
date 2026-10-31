// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import LoginMobileForm from '../loginMobileForm.vue';

const mocks = vi.hoisted(() => ({ login: vi.fn(), send: vi.fn(), refresh: vi.fn() }));
vi.mock('@/store', () => ({ useAuthStore: () => ({ login: mocks.login }) }));
vi.mock('../../../api/auth', () => ({ sendMfaCode: mocks.send }));
vi.mock('../field/captcha/mobile.vue', () => ({
  default: defineComponent({
    setup(_, { expose }) {
      expose({ handleRefresh: mocks.refresh });
      return () => h('div');
    },
  }),
}));
const Form = defineComponent({
  props: ['data', 'rules'],
  emits: ['submit'],
  setup(_, { slots }) {
    return () => h('div', slots.default?.());
  },
});
const Button = defineComponent({
  props: ['disabled', 'loading'],
  emits: ['click'],
  setup(props, { emit, slots }) {
    return () => h('button', { disabled: props.disabled, onClick: () => emit('click') }, slots.default?.());
  },
});
let wrapper: ReturnType<typeof mount>;
const form = () => wrapper.findComponent(Form);
async function submit() {
  form().vm.$emit('submit');
  await flushPromises();
}
function button(text: string) {
  return wrapper.findAll('button').find((item) => item.text().includes(text))!;
}
async function challenge(extra = {}) {
  mocks.login.mockResolvedValueOnce({ step: 2, token: 'challenge-token', phone: '1234', ...extra });
  await submit();
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  wrapper = mount(LoginMobileForm, {
    global: {
      stubs: {
        HMobileForm: Form,
        TButton: Button,
        TFormItem: { template: '<div><slot /></div>' },
        TInput: true,
        IconMdiAccount: true,
        IconMdiKey: true,
      },
    },
  });
});
afterEach(() => {
  wrapper.unmount();
  vi.useRealTimers();
});

describe('mobile login MFA', () => {
  it('does not leave a timer when a pending send finishes after unmount', async () => {
    await challenge();
    let resolve!: (value: string) => void;
    mocks.send.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    await button('请点击发送').trigger('click');
    wrapper.unmount();
    const interval = vi.spyOn(globalThis, 'setInterval');
    resolve('发送成功');
    await flushPromises();
    expect(interval).not.toHaveBeenCalled();
    interval.mockRestore();
  });
  it('returns to captcha entry if the server requests it again', async () => {
    await challenge();
    mocks.login.mockResolvedValueOnce({ captchaRequired: true });
    await submit();
    expect(form().props('data').mfaToken).toBe('');
    expect(Object.keys(form().props('rules'))).toContain('code');
  });

  it('enters SMS verification and submits the challenge token and OTP array', async () => {
    await challenge();
    expect(wrapper.text()).toContain('手机尾号1234');
    expect(wrapper.text()).toContain('验证并登录');
    expect(Object.keys(form().props('rules'))).toEqual(['mfaCode']);
    form().props('data').mfaCode = '012345';
    mocks.login.mockResolvedValueOnce({ token: 'session-token' });
    await submit();
    expect(mocks.login).toHaveBeenLastCalledWith(
      expect.objectContaining({
        mfaToken: 'challenge-token',
        mfaValidType: 'SMS',
        mfaCode: ['0', '1', '2', '3', '4', '5'],
      }),
    );
  });
  it('sends SMS with only the MFA credentials and allows resending after 300 seconds', async () => {
    await challenge();
    mocks.send.mockResolvedValueOnce('发送成功');
    await button('请点击发送').trigger('click');
    await flushPromises();
    expect(mocks.send).toHaveBeenCalledWith({ mfaValidType: 'SMS', mfaToken: 'challenge-token' });
    expect(button('300 秒').attributes('disabled')).toBeDefined();
    await vi.advanceTimersByTimeAsync(300000);
    expect(button('请点击发送').attributes('disabled')).toBeUndefined();
  });
  it('allows retrying failed sends and keeps MFA active after an invalid code', async () => {
    await challenge();
    mocks.send.mockRejectedValueOnce(new Error('send failed'));
    await button('请点击发送').trigger('click');
    await flushPromises();
    expect(button('请点击发送').attributes('disabled')).toBeUndefined();
    mocks.login.mockRejectedValueOnce(new Error('invalid code'));
    await submit();
    expect(wrapper.text()).toContain('验证并登录');
    expect(form().props('data').mfaToken).toBe('challenge-token');
  });
  it('clears MFA state and timer when returning to password login', async () => {
    await challenge();
    mocks.send.mockResolvedValueOnce('发送成功');
    await button('请点击发送').trigger('click');
    await flushPromises();
    await button('返回登录').trigger('click');
    expect(form().props('data').mfaToken).toBe('');
    expect(form().props('data').mfaCode).toBe('');
    expect(Object.keys(form().props('rules'))).toEqual(['username', 'password', 'code']);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('supports email-only challenges', async () => {
    await challenge({ phone: '', email: 'a***@example.com' });
    mocks.send.mockResolvedValueOnce('发送成功');
    await button('请点击发送').trigger('click');
    await flushPromises();
    expect(mocks.send).toHaveBeenCalledWith({ mfaValidType: 'EMAIL', mfaToken: 'challenge-token' });
  });
  it('refreshes the captcha after password login failure', async () => {
    form().props('data').code = 'old-code';
    mocks.login.mockRejectedValueOnce(new Error('invalid password'));
    await submit();
    expect(mocks.refresh).toHaveBeenCalledOnce();
    expect(form().props('data').code).toBe('');
  });
  it('keeps ordinary login working and prevents duplicate submissions', async () => {
    let resolve!: (value: any) => void;
    mocks.login.mockReturnValueOnce(
      new Promise((done) => {
        resolve = done;
      }),
    );
    form().vm.$emit('submit');
    form().vm.$emit('submit');
    expect(mocks.login).toHaveBeenCalledOnce();
    resolve({ token: 'session-token' });
    await flushPromises();
    expect(wrapper.text()).not.toContain('二次认证');
  });
});
