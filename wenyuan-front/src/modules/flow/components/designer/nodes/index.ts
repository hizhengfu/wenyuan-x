/**
 * LogicFlow 自定义节点 & 边 注册
 * 节点样式：横向卡片（左侧彩色图标区 + 右侧白底文字区，全部矩形）
 *
 *   ┌───────┬──────────────────────┐
 *   │ icon  │  节点名称            │
 *   └───────┴──────────────────────┘
 */
import type { LogicFlow } from '@logicflow/core'
import {
  h,
  RectNode,
  RectNodeModel,
  PolylineEdge,
  PolylineEdgeModel,
} from '@logicflow/core'

// ─── 节点颜色 ────────────────────────────────────────────────

const NODE_COLORS: Record<string, { fill: string; stroke: string; text: string }> = {
  START:      { fill: '#67C23A', stroke: '#4e9428', text: '#fff' },
  END:        { fill: '#F56C6C', stroke: '#c94545', text: '#fff' },
  APPROVE:    { fill: '#409EFF', stroke: '#2b85e4', text: '#fff' },
  HANDLE:     { fill: '#E6A23C', stroke: '#c17f1a', text: '#fff' },
  CC:         { fill: '#909399', stroke: '#6b6d71', text: '#fff' },
  EXCLUSIVE:  { fill: '#8B5CF6', stroke: '#6d3fd4', text: '#fff' },
  SUBPROCESS: { fill: '#06B6D4', stroke: '#0891b2', text: '#fff' },
}

// ─── 图标路径（24×24 viewBox，Material Design Icons）────────

const ICONS: Record<string, string> = {
  START:      'M8 5v14l11-7z',
  END:        'M6 6h12v12H6z',
  APPROVE:    'M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  HANDLE:     'M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z',
  CC:         'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z',
  EXCLUSIVE:  'M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  SUBPROCESS: 'M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-5 14H7v-2h7v2zm3-4H7v-2h10v2zm0-4H7V7h10v2z',
}

// ─── 布局常量 ─────────────────────────────────────────────────

/** 节点统一宽度 */
const NODE_W    = 150
/** 节点统一高度 */
const NODE_H    = 48
/** 左侧图标区宽度 */
const ICON_W    = 38
/** 圆角半径 */
const RADIUS    = 8
/** 图标渲染尺寸（路径原始 24×24，缩放到此大小） */
const ICON_SIZE = 18

// ─── 通用渲染工具 ─────────────────────────────────────────────

/**
 * 渲染图标（路径缩放自 24×24 viewBox）
 */
function mkIcon(cx: number, cy: number, key: string, size = ICON_SIZE): any {
  const s = size / 24
  return h('g', {
    transform: `translate(${cx - size / 2},${cy - size / 2}) scale(${s},${s})`,
  }, [
    h('path', { d: ICONS[key], fill: '#fff' }),
  ])
}

/**
 * 生成"仅左侧圆角"的 SVG 路径（用于左侧图标背景）
 * 顺时针：top-left 圆角 → 直边至右上角（直角）→ 右侧直边 → 直边至左下 → bottom-left 圆角 → 左侧直边 → 闭合
 */
function leftRoundedPath(lx: number, ty: number, w: number, h: number, r: number): string {
  return [
    `M${lx + r},${ty}`,
    `H${lx + w}`,
    `V${ty + h}`,
    `H${lx + r}`,
    `A${r},${r} 0 0,1 ${lx},${ty + h - r}`,
    `V${ty + r}`,
    `A${r},${r} 0 0,1 ${lx + r},${ty}`,
    'Z',
  ].join(' ')
}

/**
 * 通用横向卡片节点渲染
 *
 * @param x      节点中心 x
 * @param y      节点中心 y
 * @param width  节点宽度
 * @param height 节点高度
 * @param typeKey NODE_COLORS 对应的 key
 * @param name   显示名称
 * @param extra  额外 SVG 元素（可选）
 */
function renderCardNode(
  x: number,
  y: number,
  width: number,
  height: number,
  typeKey: string,
  name: string,
  extra?: any[],
  status?: string,
): any {
  const c   = NODE_COLORS[typeKey]
  const lx  = x - width / 2
  const ty  = y - height / 2
  const icx = lx + ICON_W / 2
  const tx  = lx + ICON_W + 10

  const isActive    = status === 'current'
  const isCompleted = status === 'completed'
  const isRejected  = status === 'rejected'

  // 右上角状态角标（已完成 / 已驳回）
  const bx   = lx + width - 10
  const by   = ty + 10
  const badge: any[] = isCompleted
    ? [
        h('circle', { cx: bx, cy: by, r: 9, fill: '#52c41a', stroke: '#fff', strokeWidth: 1.5 }),
        h('path', {
          d: `M${bx - 4},${by} L${bx - 1},${by + 3} L${bx + 4},${by - 3}`,
          fill: 'none', stroke: '#fff', strokeWidth: 1.5, strokeLinecap: 'round', strokeLinejoin: 'round',
        }),
      ]
    : isRejected
    ? [
        h('circle', { cx: bx, cy: by, r: 9, fill: '#ff4d4f', stroke: '#fff', strokeWidth: 1.5 }),
        h('path', {
          d: `M${bx - 3},${by - 3} L${bx + 3},${by + 3} M${bx + 3},${by - 3} L${bx - 3},${by + 3}`,
          fill: 'none', stroke: '#fff', strokeWidth: 1.5, strokeLinecap: 'round',
        }),
      ]
    : []

  if (isActive) {
    // 当前节点：四层叠加，确保在任何流程图中都能一眼看出
    //   ① 外层光晕（柔和填充 + 较大半径）
    //   ② 中间虚线流光环（醒目边框，"行进的蚂蚁"动画）
    //   ③ 实色卡片主体（粗描边）
    //   ④ 右上角"进行中"小圆点（呼吸闪烁）
    const ringX = lx - 6
    const ringY = ty - 6
    const ringW = width + 12
    const ringH = height + 12
    const dotCx = lx + width - 8
    const dotCy = ty + 8

    return h('g', null, [
      // ① 外层光晕（柔和，5px 偏移）
      h('rect', {
        x: lx - 5, y: ty - 5,
        width: width + 10, height: height + 10,
        rx: RADIUS + 5, ry: RADIUS + 5,
        fill: c.fill,
        opacity: 0.18,
      }),
      // ② 中间虚线流光环
      h(
        'rect',
        {
          x: ringX, y: ringY,
          width: ringW, height: ringH,
          rx: RADIUS + 6, ry: RADIUS + 6,
          fill: 'none',
          stroke: c.fill,
          strokeWidth: 2,
          strokeDasharray: '6,4',
          opacity: 0.95,
        },
        [
          // 行进的蚂蚁动画：dashoffset 持续偏移，制造流动感
          h('animate', {
            attributeName: 'stroke-dashoffset',
            from: '0',
            to: '-20',
            dur: '1s',
            repeatCount: 'indefinite',
          }),
        ],
      ),
      // ③ 实色卡片主体
      h('rect', {
        x: lx, y: ty,
        width, height,
        rx: RADIUS, ry: RADIUS,
        fill: c.fill,
        stroke: c.stroke,
        strokeWidth: 2.5,
      }),
      // 左侧图标区（深色背景增加层次）
      h('path', {
        d: leftRoundedPath(lx, ty, ICON_W, height, RADIUS),
        fill: c.stroke,
      }),
      // 图标
      mkIcon(icx, y, typeKey),
      // 白色文字
      h('text', {
        x: tx,
        y: y + 4.5,
        textAnchor: 'start',
        dominantBaseline: 'middle',
        fill: '#fff',
        fontSize: 12,
        fontWeight: '600',
        pointerEvents: 'none',
      }, name),
      ...(extra || []),
      // ④ 右上角"进行中"圆点 + 呼吸动画
      h('circle', { cx: dotCx, cy: dotCy, r: 4, fill: '#fff', stroke: c.stroke, strokeWidth: 1 }),
      h(
        'circle',
        { cx: dotCx, cy: dotCy, r: 4, fill: '#fff', opacity: 0.6 },
        [
          h('animate', { attributeName: 'r',       values: '4;9;4',     dur: '1.4s', repeatCount: 'indefinite' }),
          h('animate', { attributeName: 'opacity', values: '0.6;0;0.6', dur: '1.4s', repeatCount: 'indefinite' }),
        ],
      ),
    ])
  }

  return h('g', null, [
    // ① 白色圆角背景 + 描边
    h('rect', {
      x: lx, y: ty,
      width, height,
      rx: RADIUS, ry: RADIUS,
      fill: '#fff',
      stroke: c.stroke,
      strokeWidth: 1.5,
    }),
    // ② 左侧彩色图标区
    h('path', {
      d: leftRoundedPath(lx, ty, ICON_W, height, RADIUS),
      fill: c.fill,
    }),
    // ③ 图标
    mkIcon(icx, y, typeKey),
    // ④ 文字
    h('text', {
      x: tx,
      y: y + 4.5,
      textAnchor: 'start',
      dominantBaseline: 'middle',
      fill: '#303133',
      fontSize: 12,
      fontWeight: '500',
      pointerEvents: 'none',
    }, name),
    ...(extra || []),
    // ⑤ 状态角标（completed / rejected）
    ...badge,
  ])
}

// ─── 节点注册 ─────────────────────────────────────────────────

/** 注册所有自定义节点到 LogicFlow 实例 */
export function registerFlowNodes(lf: InstanceType<typeof LogicFlow>) {

  // ── 开始节点 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-start',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'START', p?.name || '开始', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        this.properties.nodeType = 'START'
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.START.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 结束节点 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-end',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'END', p?.name || '结束', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        this.properties.nodeType = 'END'
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.END.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 审批节点 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-approve',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'APPROVE', p?.name || '审批', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        if (!this.properties.nodeType) this.properties.nodeType = 'APPROVE'
        if (!this.properties.actions)  this.properties.actions  = ['APPROVE', 'REJECT']
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.APPROVE.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 经办节点 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-handle',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'HANDLE', p?.name || '经办', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        if (!this.properties.nodeType) this.properties.nodeType = 'HANDLE'
        if (!this.properties.actions)  this.properties.actions  = ['APPROVE']
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.HANDLE.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 抄送节点 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-cc',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'CC', p?.name || '抄送', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        if (!this.properties.nodeType) this.properties.nodeType = 'CC'
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.CC.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 条件网关 ──────────────────────────────────────────────

  lf.register({
    type: 'flow-exclusive',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p = model.properties as any
        return renderCardNode(x, y, width, height, 'EXCLUSIVE', p?.name || '条件', undefined, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        if (!this.properties.nodeType) this.properties.nodeType = 'EXCLUSIVE'
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.EXCLUSIVE.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })

  // ── 子流程节点 ────────────────────────────────────────────

  lf.register({
    type: 'flow-subprocess',
    view: class extends RectNode {
      getShape() {
        const { model } = this.props
        const { x, y, width, height } = model
        const p  = model.properties as any
        const lx = x - width / 2
        const ty = y - height / 2
        const extra = [
          h('line', {
            x1: lx + ICON_W + 6, y1: ty + height - 8,
            x2: x + width / 2 - 8, y2: ty + height - 8,
            stroke: '#ccc', strokeWidth: 1,
          }),
        ]
        return renderCardNode(x, y, width, height, 'SUBPROCESS', p?.name || '子流程', extra, p?._status)
      }
    },
    model: class extends RectNodeModel {
      initNodeData(data: any) {
        super.initNodeData(data)
        this.width  = NODE_W
        this.height = NODE_H
        this.radius = RADIUS
        if (!this.properties.nodeType) this.properties.nodeType = 'SUBPROCESS'
      }
      getNodeStyle() {
        return { fill: '#fff', stroke: NODE_COLORS.SUBPROCESS.stroke, strokeWidth: 0 }
      }
      getTextStyle() { return { fontSize: 0, fill: 'transparent' } }
    },
  })
}

// ─── 类型映射 & 导出 ──────────────────────────────────────────

/** 节点类型 → LogicFlow type 映射 */
export const NODE_TYPE_MAP: Record<string, string> = {
  START:      'flow-start',
  END:        'flow-end',
  APPROVE:    'flow-approve',
  HANDLE:     'flow-handle',
  CC:         'flow-cc',
  EXCLUSIVE:  'flow-exclusive',
  SUBPROCESS: 'flow-subprocess',
}

export const LF_TYPE_NODE_TYPE_MAP: Record<string, string> = Object.fromEntries(
  Object.entries(NODE_TYPE_MAP).map(([k, v]) => [v, k]),
)

export const NODE_TYPE_LABELS: Record<string, string> = {
  START:      '开始',
  END:        '结束',
  APPROVE:    '审批',
  HANDLE:     '经办',
  CC:         '抄送',
  EXCLUSIVE:  '条件网关',
  SUBPROCESS: '子流程',
}

/** 供侧边栏使用的图标路径 */
export const NODE_ICONS = ICONS

export { NODE_COLORS }

// ─── 自定义有向边：折线模式（直角转折，适合流程图）──────────

/** 注册自定义边到 LogicFlow 实例 */
export function registerFlowEdge(lf: InstanceType<typeof LogicFlow>) {
  lf.register({
    type: 'flow-edge',
    view: class extends PolylineEdge {},
    model: class extends PolylineEdgeModel {
      /**
       * 边线样式
       * 必须继承 super 的结果，否则丢失 fill:'none' 等基础属性
       */
      getEdgeStyle() {
        const base = super.getEdgeStyle()
        return {
          ...base,
          stroke: '#8b9ab0',
          strokeWidth: 1.5,
        }
      }
      /** 边文字样式（条件名称）*/
      getTextStyle() {
        const base = super.getTextStyle()
        return {
          ...base,
          fontSize: 11,
          color: '#606266',
          background: {
            fill: '#fff',
            fillOpacity: 0.9,
            radius: 3,
            width: 50,
            height: 20,
          },
        }
      }
      /** 箭头样式 */
      getArrowStyle() {
        const base = super.getArrowStyle()
        return {
          ...base,
          stroke: '#8b9ab0',
          fill: '#8b9ab0',
        }
      }
    },
  })
}
