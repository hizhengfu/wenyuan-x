import { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';
import request from '@/utils/request';
import { fileUrl, notification } from '@/utils';
import { ChunkUploadFile, QuickUploadFile } from '@/components/form/typings/form';
import { createMD5 } from 'hash-wasm';

/**
 * 上传地址
 * */
// export const uploadUrl = APP_BASE_URL + APP_API_PATH + '/file/upload'
export const uploadUrl = '/file/upload';
//快传地址
export const quickUploadUrl = '/file/quickUpload';
// 分片上传地址
const chunkUploadUrl = '/file/chunk';
//最小分片文件大小，超过此大小文件将分片上传,此参数需要和服务器匹配，否则会导致上传失败
const chunkMinFileSize = 2 * 1024 * 1024;
/**
 * 删除地址
 * */
export const removeUrl = APP_BASE_URL + APP_API_PATH + '/file/delete';

//重试次数
let maxRetries = 3;

/** Naive UI keeps its client id for list updates; the file service id is separate. */
export function uploadedFileId(file: UploadFileInfo): string | undefined {
  const id = (file as any).response?.id ?? (file as any).serverFileId;
  return typeof id === 'string' && id ? id : undefined;
}

/**
 * 文件分片上传
 * */
function postUploadRequest(
  action: string,
  chunk: ChunkUploadFile | FormData | QuickUploadFile,
  onProgress?: Function,
  withCredentials?: boolean,
) {
  return request.post(action, chunk, {
    headers: { 'Content-Type': 'multipart/form-data' },
    withCredentials,
    onUploadProgress: (res: any) => {
      onProgress && onProgress({ percent: Math.ceil(res.percent) });
    },
  });
}

/**
 * 分片计算
 * */
function hashChunk(chunk: Blob, hasher: any) {
  const fileReader = new FileReader();
  return new Promise((resolve, reject) => {
    fileReader.onload = async (event) => {
      const arrayBuffer: ArrayBuffer | null = event.target?.result as ArrayBuffer;
      const uintArray = new Uint8Array(arrayBuffer);
      hasher?.update(uintArray);
      resolve(true);
    };
    fileReader.readAsArrayBuffer(chunk);
  });
}

/**
 * 文件md5计算
 * */
const fileMd5 = async (file: File) => {
  /**
   * 文件hash计算
   * */
  let hasher = await createMD5();

  const chunkSize = 64 * 1024 * 1024;
  const chunkNumber = Math.floor(file.size / chunkSize);

  for (let i = 0; i <= chunkNumber; i++) {
    const chunk = file.slice(chunkSize * i, Math.min(chunkSize * (i + 1), file.size));
    await hashChunk(chunk, hasher);
  }
  const hash = hasher.digest();
  return Promise.resolve(hash);
};

/**
 * 自定义上传默认请求
 * */
export async function uploadDefaultRequest(uploadCustomRequestOptions: UploadCustomRequestOptions) {
  return uploadRequest(uploadCustomRequestOptions, { enableQuickUpload: true, enableChunk: true });
}

/**
 * 自定义上传请求
 * */
export async function uploadRequest(
  { file, action, withCredentials, data, headers, onProgress, onFinish, onError }: UploadCustomRequestOptions,
  config: {
    enableQuickUpload: boolean;
    enableChunk: boolean;
  },
) {
  let realFile = file.file as File;
  let fileSize = realFile.size;
  //获取内容id
  //@ts-ignore
  let contentId = data && data['contentId'];

  const { enableQuickUpload = true, enableChunk = false } = config;

  /**
   * 是否启用快传，启用后如果文件已经上传过，将不再上传文件
   * */
  if (enableQuickUpload) {
    try {
      //计算文件hash值以便进行快传处理
      let md5Hash = await fileMd5(realFile);

      let quickResp = await postUploadRequest(
        quickUploadUrl,
        Object.assign(
          {
            hash: md5Hash,
            name: realFile.name,
          },
          data,
        ),
      );

      //已有数据资源设置上传完毕
      if (quickResp.data) {
        file.url = fileUrl(quickResp.data.path) || null;
        //使用fullPath 存储服务器返回的相对路径，实际场景下都使用这个路径存储，以便迁移
        file.fullPath = quickResp.data.path;
        (file as any).serverFileId = quickResp.data.id;
        (file as any).response = quickResp.data;

        onFinish();
        return false;
      }
    } catch (e) {
      //上传返回异常的不再尝试
      //@ts-ignore
      if (e.status != 200) {
        onError();
        return false;
      }
      notification({
        type: 'warning',
        title: '请注意',
        content: '浏览器不支持快传，将使用正常上传模式。',
      });
    }
  }

  //当文件大小超过最小分片大小时，开始分片
  //@ts-ignore
  if (enableChunk && fileSize > chunkMinFileSize) {
    let start = 0;
    let end = chunkMinFileSize;
    let session_id: string | undefined;
    let uploadFinish = false;

    while (!uploadFinish) {
      let chunk: ChunkUploadFile;
      if (start === 0 && !session_id) {
        chunk = { phase: 'start', retries: maxRetries };
      } else {
        let chunkFile = realFile.slice(start, end);
        if (chunkFile.size === 0) {
          //@ts-ignore
          chunk = Object.assign(
            {
              phase: 'finish',
              retries: maxRetries,
              size: fileSize,
              name: realFile.name,
              contentId: contentId,
              session_id: session_id,
            },
            data,
          );
        } else {
          chunk = {
            phase: 'upload',
            chunk: chunkFile,
            start_offset: start,
            session_id: session_id,
            contentId: contentId,
            retries: maxRetries,
          };
        }
      }
      while (chunk.retries > 0) {
        try {
          let uploadChunk = await postUploadRequest(chunkUploadUrl, chunk, undefined, withCredentials);
          //每次上传重试计数器减少
          chunk.retries--;
          //上传不成功多次执行
          //@ts-ignore
          if (uploadChunk.status != 'success') {
            continue;
          }

          if (chunk.phase === 'start') {
            file.status = 'pending';
            session_id = uploadChunk.data.session_id;
            if (chunkMinFileSize != uploadChunk.data.end_offset) {
              notification({
                type: 'warning',
                title: '请注意',
                content: '文件上传分片大小不一致，请核对参数。',
              });
              onProgress({ percent: 1 });
              return false;
            }
          } else if (chunk.phase === 'finish') {
            onProgress({ percent: 100 });
            file.url = fileUrl(uploadChunk.data.path) || null;
            file.fullPath = uploadChunk.data.path;
            file.percentage = 100;
            file.status = 'finished';
            (file as any).serverFileId = uploadChunk.data.id;
            (file as any).response = uploadChunk.data;
            uploadFinish = true;
            onFinish();
          } else {
            file.status = 'uploading';
            //剩余文件大小计算分块数量
            onProgress({ percent: Math.floor((start / fileSize) * 100) });
          }
          break;
        } catch (e: any) {
          chunk.retries--;
          //对400请求跳出循环
          if (e.response && e.response.status === 400) {
            chunk.retries = 0;
          }
          //上传异常到重试次数后抛出上传错误
          if (chunk.retries === 0) {
            uploadFinish = true;
            onError();
          }
        }
      }
      //当传输的某条数据重试次数归零后不再循环
      if (chunk.retries == 0) {
        break;
      }
      //开始的情况不计数
      if (chunk.phase === 'upload') {
        start = end;
        end = start + chunkMinFileSize;
      }
    }
  } else {
    const formData = new FormData();
    if (data) {
      Object.keys(data).forEach((key) => {
        formData.append(key, data[key as keyof UploadCustomRequestOptions['data']]);
      });
    }
    formData.append('file', realFile);
    postUploadRequest(action as string, formData, onProgress, withCredentials)
      .then((res) => {
        if (res.data) {
          file.url = fileUrl(res.data.path) || null;
          //使用fullPath 存储服务器返回的相对路径，实际场景下都使用这个路径存储，以便迁移
          file.fullPath = res.data.path;
          file.status = 'finished';
          (file as any).serverFileId = res.data.id;
          (file as any).response = res.data;
        }
        onFinish();
      })
      .catch((error) => {
        onError();
      });
  }
}

/**
 * 根据文件名生成文件图标
 * */
export function fileIcon(url: string) {
  if (/.pdf$/i.test(url)) {
    return 'mdi:file-pdf';
  } else if (/.(jpg|jpeg|png|gif|webp)$/i.test(url)) {
    return 'mdi:file-image';
  } else if (/.(mp4|mov)$/i.test(url)) {
    return 'mdi:file-video';
  } else if (/.(xls|xlsx)$/i.test(url)) {
    return 'mdi:file-excel';
  } else if (/.(doc|docx)$/i.test(url)) {
    return 'mdi:file-word';
  } else {
    return 'mdi:file';
  }
}
