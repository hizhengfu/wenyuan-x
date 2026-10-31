/**
 * 适配portal数据
 * */
export function adopterPortal(item: any, index: number) {
  return {
    id: item.id,
    portalId: item.portalId,
    applicationId: item.application?.id,
    name: item.name,
    image: item.image,
    type: item.type,
    applicationPath: item.application?.path || '',
    url: item.url,
    link: item.link,
    color: item.color,
    backgroundColor: item.backgroundColor,
    method: item.method,
    columns: item.fields,
    isExpose: item.isExpose,
    isFrozen: item.isFrozen,
    isShadow: item.isShadow,
    isBorder: item.isBorder,
    i: index,
    x: item.x,
    y: item.y,
    h: item.h,
    w: item.w
  }
}
