import {LatLngExpression} from "leaflet";

/**
 * 计算两点的距离
 * */
export function pointDistance(p1: Point, p2: Point) {
  return Math.sqrt(Math.pow((p1.x - p2.x), 2) + Math.pow((p1.y - p2.y), 2));
}

/**
 * 算法因子
 * 1像素对应15mm
 * 算法是 门口距离3.4米，底图门口距离是224像素，换算成像素后一像素是15mm,一毫米是1/15像素
 * 门口宽度1.97 81px
 * 由于机器人地图与地图访问不同，需要特殊处理，实际给到机器人的要转换x,y
 * */
// const px2mm = 15
const px2mm = 3500 / 230

/**
 * 像素到米转换
 * */
const px2m = px2mm / 1000

/**
 * 机器人像素坐标原点
 * */
const origin = {x: 21, y: 260}

/**
 * 像素转换为场地实际坐标,
 * */
export function px2physics(x: number, y: number) {
  let tx = (origin.y - y) * px2m
  let ty = (x - origin.x) * px2m

  return {x: tx.toFixed(2), y: ty.toFixed(2)}
}

/**
 * 物理位置转换为像素
 * */
export function physics2px(x: number, y: number): LatLngExpression {
  let ty = origin.y - x / px2m
  let tx = y / px2m + origin.x

  return [ty, tx]
}

const cachePoint = {}



