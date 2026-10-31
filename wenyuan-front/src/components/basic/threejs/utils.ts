import { Box3, Vector3, PerspectiveCamera, Object3D } from 'three';

/**
 * 相机自动根据模型展示
 * */
export function fitCameraToObject(camera: PerspectiveCamera, object: Object3D, offset = 1.2) {
  const box = new Box3().setFromObject(object);
  const center = new Vector3();
  const size = new Vector3();
  box.getCenter(center);
  box.getSize(size);

  object.position.sub(center); // 居中模型

  const maxDim = Math.max(size.x, size.y, size.z);
  const fov = camera.fov * (Math.PI / 180);
  const distance = (maxDim / (2 * Math.tan(fov / 2))) * offset;

  camera.position.set(center.x, center.y, distance);
  camera.lookAt(0, 0, 0);
}
