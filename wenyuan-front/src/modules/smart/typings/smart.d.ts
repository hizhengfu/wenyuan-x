/**
 * 智能控制类型
 * */
type SmartControlType = 'CLICK' | 'DBLCLICK' | 'DIRECTION' | 'ROTATE' | 'URL' | 'SEARCH'

/**
 * 智能控制指令类型
 * */
export interface SmartControlCommand {
  type: SmartControlType,
  value: string,
}
