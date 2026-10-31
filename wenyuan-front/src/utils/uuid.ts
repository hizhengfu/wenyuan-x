import { random, sample } from 'lodash-es';

/**
 * 生成uuid
 * */
export function uuid() {
  let d = new Date().getTime();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (d + random(16)) % 16 | 0;
    d = Math.floor(d / 16);
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

interface PasswordOptions {
  lower?: boolean;
  upper?: boolean;
  number?: boolean;
  symbol?: boolean;
}

/**
 * 随机密码生成
 * */
export function generatePassword(length: number = 8, opts: PasswordOptions = {}): string {
  const { lower = true, upper = true, number = true, symbol = true } = opts;

  const sets: string[] = [];
  if (lower) sets.push('abcdefghijklmnopqrstuvwxyz');
  if (upper) sets.push('ABCDEFGHIJKLMNOPQRSTUVWXYZ');
  if (number) sets.push('0123456789');
  if (symbol) sets.push('!@#$%^&*()_+');

  if (sets.length === 0) {
    throw new Error('至少需要启用一个字符类别');
  }

  const all = sets.join('').split('');

  let result = '';
  for (let i = 0; i < length; i++) {
    result += sample(all) as string;
  }

  return result;
}
