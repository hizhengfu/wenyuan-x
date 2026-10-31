export const PUBLISH_LEVEL_OPTIONS = [
  '全国重点文物保护单位',
  '省级文物保护单位',
  '市级和县级文物保护单位',
  '市、县级文物保护单位',
  '尚未核定为保护单位',
].map((value) => ({ label: value, value }))

export const PUBLISH_CATEGORY_OPTIONS = [
  '古文化遗址',
  '古墓葬',
  '古建筑',
  '石窟寺及石刻',
  '近现代重要史迹及代表性建筑',
  '其他',
].map((value) => ({ label: value, value }))

export const PUBLISH_BATCH_OPTIONS = [
  '第一批',
  '第二批',
  '第三批',
  '第四批',
  '第五批',
  '第六批',
  '第七批',
  '第八批',
].map((label, index) => ({ label, value: String(index) }))
