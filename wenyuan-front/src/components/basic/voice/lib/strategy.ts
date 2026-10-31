// 策略模式 (做映射)
class Strategy {
  constructor(strategy?: Record<any, any>) {
    if (strategy) {
      this.strategy = strategy;
    }
  }
  // 策略对象
  private strategy: Record<any, any> = {
    default() {
      console.log('Strategy Default action');
    },
  };
  // 执行策略动作
  execute(command: any, params = {}) {
    if (command && typeof this.strategy[command] === 'function') {
      this.strategy[command](params);
    } else {
      this.strategy.default && this.strategy.default();
    }
  }
}

export { Strategy };
