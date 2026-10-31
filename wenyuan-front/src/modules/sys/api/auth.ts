import { getRefreshToken } from '@/utils/auth';
import request from '@/utils/request';

/**
 * 登陆表单
 * */
interface loginBody {
  username?: string; //用户名
  password?: string; //密码
  code?: string; //验证码
  uuid?: string; //验证码识别id
  rememberMe?: boolean; //记住我
}

interface forgetPasswordBody {
  value: string; //手机或邮箱
  code: string; //验证码
  uuid?: string; //验证码识别id
}

/**
 * 登陆接口
 * */
export function login(data: loginBody) {
  return request({
    url: 'auth/login',
    data,
    method: 'post',
  });
}
/**
 * 退出接口
 * */
export function logout() {
  return request({
    url: 'auth/logout',
    data: { refreshToken: getRefreshToken() || undefined },
    method: 'post',
  });
}

/**
 * 忘记密码
 * */
export function forgotPassword(data: forgetPasswordBody) {
  return request({
    url: 'auth/forgotPassword',
    data,
    method: 'post',
  });
}

/**
 * 重设密码
 * */
export function resetPassword(data: object) {
  return request({
    url: 'auth/resetPassword',
    data,
    method: 'post',
  });
}

/**
 * 用户信息
 * */
export function userInfo() {
  return request({
    url: 'auth/info',
    method: 'get',
  });
}

/**
 * 登录配置信息
 * */
export function config() {
  return request({
    url: 'auth/config',
    method: 'get',
  });
}

/**
 * 发送mfa认证码
 * */
export function sendMfaCode(params: object) {
  return request({
    url: 'auth/mfaCode',
    method: 'get',
    params: params,
  });
}

/**
 * 刷新 access token（jwt2）
 * */
export function refreshToken(refreshToken: string) {
  return request({
    url: 'auth/refresh',
    method: 'post',
    data: { refreshToken },
  });
}
