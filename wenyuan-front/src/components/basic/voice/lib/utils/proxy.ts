// 代理枚举
export function Enum(baseEnum: Record<any, any>) {
  return new Proxy(baseEnum, {
    get(target, name) {
      if (!baseEnum.hasOwnProperty(name)) {
        throw new Error(`"${String(name)}" value does not exist in the enum`);
      }
      return baseEnum[String(name)];
    },
    set(target, name, value) {
      throw new Error('Cannot add a new value to the enum');
    },
  });
}
