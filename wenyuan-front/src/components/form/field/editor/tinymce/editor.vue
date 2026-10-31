<template>
  <tinymce-editor
    class="h-editor"
    v-bind="$attrs"
    :disabled="disabled"
    v-model="innerValue"
    style="width: 100%; height: 100%"
    :tinymceScriptSrc="innerTinymceScriptSrc"
    :init="editorConfig"
  ></tinymce-editor>
</template>
<script lang="ts" setup>
import TinymceEditor from '@tinymce/tinymce-vue';
import { computed, ref } from 'vue';
import request from '@/utils/request';
import { fileUrl } from '@/utils/path';

const mediaUploadUrl = '/file/upload';

const props = defineProps({
  value: { type: String },
  disabled: { type: Boolean, default: false }, //是否禁用
  contentId: { type: String },
  toolbar: {
    type: Array,
    default() {
      return [
        'undo redo | insert | fontfamily fontsize| bold italic underline strikethrough forecolor backcolor|  styles aligncenter alignright alignjustify | bullist numlist outdent indent | link image media code fullscreen',
      ];
    },
  },
  tinymceScriptSrc: { type: String, default: 'tinymce/6.8.2/tinymce.min.js' },
});
const emits = defineEmits(['update:value']);

const innerValue = computed({
  get: () => props.value,
  set: (val) => {
    emits('update:value', val);
  },
});

/**
 * 点击文件上传事件
 * */
function fileUploadHandler(callback: Function, value: any, meta: any) {
  const input = document.createElement('input');
  input.setAttribute('type', 'file');
  // input.setAttribute('multiple', 'multiple')

  input.onchange = function (e: any) {
    const file = e.target.files[0];
    let formData = new FormData();
    formData.append('file', file, file.name);

    request
      .post(mediaUploadUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        params: { contentId: props.contentId },
      })
      .then((result) => {
        callback(fileUrl(result.data.path), {
          title: file.name,
          text: file.name,
        });
      });
  };
  input.click();
}

/**
 * 图像上传事件
 * */
function imageUploadHandler(blobInfo: any, progress: Function) {
  let formData = new FormData();
  formData.append('file', blobInfo.blob(), blobInfo.filename());

  return request
    .post(mediaUploadUrl, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: { contentId: props.contentId },
    })
    .then((result) => {
      return fileUrl(result.data.path);
    });
}

const innerTinymceScriptSrc = computed(() => {
  const { tinymceScriptSrc } = props;
  if (/^http/.test(tinymceScriptSrc)) {
    return tinymceScriptSrc;
  } else {
    let path = location.href.split('#')[0].replace(/[^\/]+.html$/, '');
    return path + tinymceScriptSrc;
  }
});
const editorConfig = computed(() => {
  const { toolbar } = props;
  return {
    language: 'zh-Hans',
    menubar: false,
    branding: false,
    plugins: [
      'advlist',
      'autolink',
      'lists',
      'link',
      'image',
      'charmap',
      'preview',
      'anchor',
      'searchreplace',
      'visualblocks',
      'fullscreen',
      'insertdatetime',
      'media',
      'table',
      'code',
    ],
    toolbar: toolbar,
    toolbar_mode: 'sliding',
    convert_urls: false,
    font_family_formats:
      '微软雅黑=Microsoft YaHei,Helvetica Neue,PingFang SC,sans-serif;苹果苹方=PingFang SC,Microsoft YaHei,sans-serif;宋体=simsun,serif;仿宋体=FangSong,serif;黑体=SimHei,sans-serif;Arial=arial,helvetica,sans-serif;Arial Black=arial black,avant garde;Book Antiqua=book antiqua,palatino;',
    paste_data_images: true, //复制上传图片
    powerpaste_allow_local_images: true,
    images_upload_handler: imageUploadHandler,
    file_picker_callback: fileUploadHandler,
  };
});
</script>
<style lang="scss">
.tox-tinymce {
  border: 1px solid rgb(224, 224, 230) !important;
  border-radius: 3px !important;
  flex: 1;
}

.tox-tinymce-aux {
  z-index: 2300 !important;
}
</style>
