import type { MuseumSpatialAnchorVo } from '../typings';

export interface SpatialAnchorGroup {
  key: string;
  primary: MuseumSpatialAnchorVo;
  anchors: MuseumSpatialAnchorVo[];
  xRatio: number;
  yRatio: number;
  clustered: boolean;
}

/**
 * 仅在查看态合并实际发生视觉重叠的设备锚点；位置锚点和编辑态锚点始终保持独立。
 */
export function groupSpatialAnchors(
  anchors: MuseumSpatialAnchorVo[],
  imageWidth: number,
  imageHeight: number,
  editing = false,
  zoom = 0,
): SpatialAnchorGroup[] {
  const groups: SpatialAnchorGroup[] = [];
  const thresholdPixels = 34 / 2 ** zoom;

  (anchors || []).forEach((anchor) => {
    const canCluster = !editing && anchor.targetType === 'DEVICE';
    const target = canCluster
      ? groups.find(
          (group) =>
            group.primary.targetType === 'DEVICE' &&
            pixelDistance(group.xRatio, group.yRatio, anchor.xRatio, anchor.yRatio, imageWidth, imageHeight) <=
              thresholdPixels,
        )
      : undefined;

    if (!target) {
      groups.push({
        key: `${anchor.targetType}:${anchor.targetId}`,
        primary: anchor,
        anchors: [anchor],
        xRatio: anchor.xRatio,
        yRatio: anchor.yRatio,
        clustered: false,
      });
      return;
    }

    target.anchors.push(anchor);
    target.xRatio = target.anchors.reduce((sum, item) => sum + item.xRatio, 0) / target.anchors.length;
    target.yRatio = target.anchors.reduce((sum, item) => sum + item.yRatio, 0) / target.anchors.length;
    target.clustered = true;
    target.key = `cluster:${target.anchors.map((item) => item.targetId).sort().join('|')}`;
  });

  return groups;
}

function pixelDistance(
  leftX: number,
  leftY: number,
  rightX: number,
  rightY: number,
  imageWidth: number,
  imageHeight: number,
) {
  return Math.hypot((leftX - rightX) * imageWidth, (leftY - rightY) * imageHeight);
}
