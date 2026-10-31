import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { FormDrawerActiveDrawer, FormDrawerWidth } from '@/components/form/typings/form';

// 辅助函数：解析宽度字符串，返回数值和单位
function parseWidth(widthString: string | number): FormDrawerWidth {
  if (typeof widthString === 'number') {
    return { value: widthString, unit: 'px' };
  }
  if (typeof widthString === 'string') {
    if (widthString.endsWith('px')) {
      return { value: parseFloat(widthString), unit: 'px' };
    } else if (widthString.endsWith('%')) {
      return { value: parseFloat(widthString), unit: '%' };
    }
  }
  // 默认处理，如果单位未知或传入非数字/字符串，则视为像素并提供默认值
  return { value: 500, unit: 'px' }; // 默认500px
}

/**
 * 表单管理store
 * */
export const useFormStore = defineStore('form-store', () => {
  /**
   * 取消表单
   * */
  const closeForm = ref(false);

  /**
   * 取消表单
   * */
  function handleCancel() {
    closeForm.value = true;
  }

  // activeDrawers 现在是一个类型化的 ref
  const activeDrawers = ref<FormDrawerActiveDrawer[]>([]);

  // 计算属性，表示当前打开的抽屉数量
  const openDrawerCount = computed<number>(() => activeDrawers.value.length);

  // 宽度递减的像素值
  const widthReductionPerLevelPx: number = 50;
  // 最小像素宽度
  const minWidthPx: number = 300;

  /**
   * 计算抽屉的实际宽度
   * @param {ParsedWidth} parsedInitialWidth - 解析后的初始宽度对象
   * @param {number} level - 抽屉的层级索引 (0-based)
   * @returns {string} 计算后的宽度字符串 (带单位)
   */
  function calculateCurrentWidth(parsedInitialWidth: FormDrawerWidth, level: number): string {
    if (parsedInitialWidth.unit === 'px') {
      const calculatedPx = Math.max(parsedInitialWidth.value - level * widthReductionPerLevelPx, minWidthPx);
      return `${calculatedPx}px`;
    } else if (parsedInitialWidth.unit === '%') {
      // 对于百分比宽度，默认不进行像素递减。
      // 如果需要百分比递减（例如 50% -> 45%），则需要额外的逻辑和参数。
      return `${parsedInitialWidth.value - level * 5}%`;
    }
    // 兜底，理论上不会走到这里，因为 parseWidth 已处理未知单位
    return `${parsedInitialWidth.value}px`;
  }

  /**
   * 注册一个新抽屉
   * @param {string} id - 抽屉的唯一ID
   * @param {string | number} initialWidthInput - 该抽屉的初始宽度（如 500, "600px", "50%"）
   * @returns {number} 返回当前注册的抽屉的层级索引 (0-based)
   */
  function registerDrawer(id: string, initialWidthInput: string | number): number {
    const currentLevel: number = activeDrawers.value.length; // 当前是第几层 (0-based)
    const parsedInitialWidth: FormDrawerWidth = parseWidth(initialWidthInput);

    const newCurrentWidth: string = calculateCurrentWidth(parsedInitialWidth, currentLevel);

    const newDrawer: FormDrawerActiveDrawer = {
      id,
      initialWidth: initialWidthInput, // 存储原始传入的宽度
      currentWidth: newCurrentWidth, // 存储计算后的当前宽度字符串
    };

    activeDrawers.value.push(newDrawer);
    return currentLevel; // 返回其层级索引
  }

  /**
   * 注销一个抽屉
   * @param {string} id - 抽屉的唯一ID
   */
  function unregisterDrawer(id: string): void {
    const index: number = activeDrawers.value.findIndex((drawer) => drawer.id === id);
    if (index !== -1) {
      activeDrawers.value.splice(index, 1);
    }
  }

  /**
   * 根据层级索引获取抽屉的宽度配置
   * @param {number} level - 抽屉的层级索引 (0-based)
   * @returns {DrawerConfig | null} - 抽屉宽度配置或 null
   */
  function getDrawerConfigByLevel(level: number): string | null {
    const drawer: FormDrawerActiveDrawer | undefined = activeDrawers.value[level];
    return drawer ? drawer.currentWidth : null;
  }

  return { closeForm, activeDrawers, handleCancel, registerDrawer, unregisterDrawer, getDrawerConfigByLevel };
});
