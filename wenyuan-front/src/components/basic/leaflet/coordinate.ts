import proj4 from 'proj4'
import gcoord, {GeoJSON} from 'gcoord'

/**
 * 在线转换参考
 * @view https://epsg.io/transform#s_srs=4526&t_srs=4326&x=38639678.5870000&y=2782804.9650000
 * */

// proj4.defs('EPSG:4326', '+proj=longlat +datum=WGS84 +no_defs +type=crs');

/**
 * 大地坐标系转换为wgs84坐标,目前只实现三度分带
 * @param type 高斯克吕格投影-CM 中央经线 高斯克吕格投影-zone 带号
 * */
export function cGCS2000ToWgs84(x: number, y: number): [number, number] {
  //计算出数位号
  let zoneNumber = parseInt(String(x).substring(0, 2))
  //计算中央子午线
  let lon_0 = zoneNumber * 3
  //500000 500000-100=499900
  return proj4(`+proj=tmerc +lat_0=0 +lon_0=${lon_0} +k=1 +x_0=${zoneNumber}500000 +y_0=0 +ellps=GRS80 +units=m +no_defs +type=crs`, 'EPSG:4326', [x, y])
}

/**
 * 西安80转换为wgs84坐标,目前只实现三度分带
 * */
export function xian80ToWgs84(x: number, y: number): [number, number] {
  //计算出数位号
  let zoneNumber = parseInt(String(x).substring(0, 2))
  //计算中央子午线
  let lon_0 = zoneNumber * 3

  return proj4(`+proj=tmerc +lat_0=0 +lon_0=${lon_0} +k=1 +x_0=${zoneNumber}500000 +y_0=0 +ellps=IAU76 +units=m +no_defs +type=crs`, 'EPSG:4326', [x, y])
}

/**
 * 北京54转换为wgs84坐标,目前只实现三度分带
 * */
export function bj54ToWgs84(x: number, y: number): [number, number] {
  //计算出数位号
  let zoneNumber = parseInt(String(x).substring(0, 2))
  //计算中央子午线
  let lon_0 = zoneNumber * 3

  return proj4(`+proj=tmerc +lat_0=0 +lon_0=${lon_0} +k=1 +x_0=${zoneNumber}500000 +y_0=0 +ellps=krass +units=m +no_defs`, 'EPSG:4326', [x, y])
}


/**
 * wgs84转换为bd09
 * */
export function wgs84ToBd09(x: number, y: number): [number, number] {
  return gcoord.transform(
    [x, y],
    gcoord.WGS84,
    gcoord.BD09
  );
}

/**
 * wgs84转换为GCJ02
 * */
export function wgs84ToGCJ02(x: number, y: number): [number, number] {
  return gcoord.transform(
    [x, y],
    gcoord.WGS84,
    gcoord.GCJ02
  );
}

/**
 * wgs84转换为GCJ02
 * */
export function geoJsonTransform(geoJson: GeoJSON, type: string): GeoJSON {
  let crsType = gcoord.CRSTypes.GCJ02
  if (type === 'GCJ02') {
    crsType = gcoord.CRSTypes.GCJ02
  } else if (type === 'BMap') {
    crsType = gcoord.BD09
  } else if (type === 'GCJ02ToWGS84') {
    return gcoord.transform(
      geoJson,
      gcoord.GCJ02,
      gcoord.WGS84
    );
  } else if (type === 'BMapToWGS84') {
    return gcoord.transform(
      geoJson,
      gcoord.BD09,
      gcoord.WGS84
    );
  } else {
    return geoJson
  }

  return gcoord.transform(
    geoJson,
    gcoord.WGS84, crsType
  );
}

/**
 * 根据围界生成geojson
 * */
export function polygonGeoJSON(coordinates: number[][][], properties: object = {}) {
  const polygon = {
    type: 'Polygon',
    coordinates: coordinates
  };

  const feature = {
    type: 'Feature',
    properties: properties,
    geometry: polygon
  };

  const featureCollection = {
    type: 'FeatureCollection',
    features: [feature]
  };

  return featureCollection;
}

/**
 * 投影带的带号计算：通过用户输入的经纬度及定义的投影带宽度，得到该经纬度下投影带的带号
 * @param long  输入 用户输入的经度
 * @param projectedBandWidth  输入 用户定义的投影带宽度
 * @returns {number}  返回  经纬度下投影带的带号
 */
export function bandNumCalc(long: number, projectedBandWidth: number = 3) {
  let num;
  if (long <= 180) {
    if (projectedBandWidth === 6) {
      num = Math.floor(long / projectedBandWidth) + 1;
    } else if (projectedBandWidth === 3) {
      num = Math.floor((long - 1.5) / projectedBandWidth) + 1;
    } else {
      num = 0;
    }
  } else {
    num = parseInt(String(long).substring(0, 2))
  }
  return num;
}
