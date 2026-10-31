import { Strategy } from './strategy';

// 语音识别
class Voice {
  constructor(action: Record<any, any>, map: Map<any, any>) {
    this._action = action;
    this._map = map;
    this._strategy = new Strategy(action);
  }
  private _action: Record<any, any>; // 语音识别指令
  private _map: Map<any, any>; // 指令字典
  private _strategy: Strategy = new Strategy(); // 语音识别策略
  private queue: any[] = []; // 指令执行队列
  private executing: boolean = false; // 是否在执行指令

  // 添加任务
  enqueue(tasks: any[]) {
    this.queue = this.queue.concat(tasks);
    this.run();
  }

  // 执行任务
  async run() {
    if (this.executing) return;
    this.executing = true; // 执行中
    // 依次从任务队列中取出任务去执行
    while (this.queue.length > 0) {
      const task = this.queue.shift();
      await task();
    }
    this.executing = false; // 重置状态
  }

  // 语音识别结果处理
  process(speech: string) {
    const words: string[] = speech.split(' '); // 识别结果
    const actions: any[] = []; // 多指令
    words.forEach((word, index) => {
      const key = this.getKeyByValue(this.map, word);
      if (key) {
        const target: string = words[index - 1] || ''; // 执行指令的对象
        const content: string = words[index + 1] || ''; // 指令实际内容
        actions.push({ command: key, params: { target, content } }); // 指令有效则添加到指令数组中，等待后续执行
      }
    });
    // 添加指令任务
    const tasks = actions.map((action) => () => this.strategy.execute(action.command, action.params));
    this.enqueue(tasks);
  }

  // 从map中根据value获取key
  getKeyByValue(map: Map<any, any>, value: any) {
    const entry = Array.from(map.entries()).find(([key, val]) => {
      if (Array.isArray(val)) {
        return val.includes(value);
      } else {
        return val === value;
      }
    });
    return entry ? entry[0] : null; // 如果找不到，返回null
  }

  // 私有变量获取
  get action() {
    return this._action;
  }
  get map() {
    return this._map;
  }
  get strategy() {
    return this._strategy;
  }
}

export { Voice };
