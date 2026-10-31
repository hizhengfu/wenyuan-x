import fs from 'fs';
import path from 'path';
import axios from 'axios';
import cliProgress from 'cli-progress';
import pako from 'pako';
import { rewind } from '@turf/turf';

/**
 * 此脚本用于下载天地图上的行政区划数据
 *
 * */
class GeoJSONDownloader {
  /**
   * @param defaultCode 默认编码 156000000表示中国
   * @param defaultLevel 默认级别 2 省 1 市 0 县
   * */
  constructor(baseUrl, defaultCode = '156000000', defaultLevel = 2) {
    this.baseUrl = baseUrl;
    this.defaultCode = defaultCode;
    this.defaultLevel = defaultLevel;
    this.saveJson = false; //是否存储json格式
    this.dataDir = path.join(process.cwd(), '../resource/geojson');
    this.progressBar = new cliProgress.SingleBar({}, cliProgress.Presets.shades_classic);
  }

  async downloadFile(gbCode, level) {
    const url = this.baseUrl;
    const response = await axios({
      url,
      params: {
        gb: gbCode,
        level: level,
      },
      method: 'GET',
      headers: { Referer: 'https://cloudcenter.tianditu.gov.cn/administrativeDivision/' },
      responseType: 'arraybuffer',
      onDownloadProgress: (progressEvent) => {
        this.progressBar.update(progressEvent.loaded);
      },
    });

    this.progressBar.start(response.data.length, 0);
    const geoJson = this.decodeData(response.data);
    //当有下级边界信息才存储
    if (geoJson.features.length > 0) {
      let dir = `${this.dataDir}/${level}`;
      if (level === 2) {
        dir = this.dataDir;
      }

      if (this.saveJson) {
        const filePath = path.join(dir, `${gbCode}.geojson`);

        fs.writeFileSync(filePath, JSON.stringify(rewind(geoJson, { reverse: false })));
      } else {
        const filePath = path.join(dir, `${gbCode}.gzip`);
        fs.writeFileSync(filePath, Buffer.from(response.data));
      }
      await this.processGeoJSON(geoJson, gbCode, level);
    }
    this.progressBar.stop();
  }

  /**
   * 解析接口返回文件
   * */
  decodeData(data) {
    const e = new Int8Array(pako.inflate(data));
    const r = [];

    for (let i = 0; i < e.length; i += 4) {
      let n = 0;
      for (let o = i; o <= i + 3; o++) {
        n += (255 & e[o]) << (8 * (3 - (o - i)));
      }
      n >>= 2;
      r.push(n);
    }

    const a = new TextDecoder('utf-8');
    return JSON.parse(a.decode(new Int8Array(r)));
  }

  async processGeoJSON(geoJson, gbCode, level) {
    try {
      console.log(`\n计划处理${gbCode}下数据${geoJson.features.length}条`);
      for (let i = 0; i < geoJson.features.length; i++) {
        let properties = geoJson.features[i].properties;
        let gb = properties.gb;
        let nextLevel = level - 1;
        if (nextLevel < 0) continue;
        // if (properties.name == '境界线') continue;
        //延时查询，防止太频繁被封锁ip
        await new Promise((resolve) => setTimeout(resolve, 200));
        console.log(`\n开始处理第${i + 1}条，名称${properties.name},编码${gb}`);
        const childData = await this.downloadFile(gb, nextLevel);
        if (childData && childData.features.length > 0) {
          await this.processGeoJSON(childData, gb, nextLevel);
        }
      }
    } catch (error) {
      console.error(`处理json文件失败 ${gbCode}:`, error.message);
    }
  }

  async start(gbCode = this.defaultCode, level = this.defaultLevel) {
    //预先根据级别构建文件夹
    for (let i = 0; i < level; i++) {
      let path = `${this.dataDir}/${i}`;
      if (!fs.existsSync(path)) {
        fs.mkdirSync(path, { recursive: true });
      }
    }

    console.log(`开始下载地区code为: ${gbCode}`);
    await this.downloadFile(gbCode, level);
  }
}

// Usage
const downloader = new GeoJSONDownloader('https://cloudcenter.tianditu.gov.cn/api/portal/region/map');
downloader.saveJson = true;
downloader.start().catch(console.error);
