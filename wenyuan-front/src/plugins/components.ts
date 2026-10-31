import { App, defineAsyncComponent } from 'vue';

import Dialog from '@/components/basic/Dialog.vue';
import { DataIterator, DataTable, PageDataTable } from '@/components/data/table';
import { HChart } from '@/components/data/chart';
import {
  ContentPageForm,
  ContentPageView,
  DetailTable,
  DialogForm,
  DialogImportExcelForm,
  DialogUploadFileForm,
  DialogViewForm,
  Form,
  InlineForm,
  PageForm,
  PageView,
} from '@/components/form/form';
import { HAvatar, HDatePicker, HFieldLatlng, HFileList, HImage, HTime, HUpload, HUploadInput } from '@/components/form/field';
import FullScreen from '@/components/basic/FullScreen.vue';
import HIcon from '@/components/basic/DynamicIcon.vue';
import More from '@/components/basic/More.vue';
import {
  DictCheckbox,
  DictCheckboxText,
  DictRadio,
  DictRadioText,
  DictRadioButton,
  DictSelect,
  DictSelectTag,
  DictText,
  DictTreeSelect,
  DictTypeSelect,
} from '@/components/form/field/dict';
import GroupSelect from '@/modules/sys/components/form/field/GroupSelect.vue';
import GroupText from '@/modules/sys/components/form/field/GroupText.vue';
import UserText from '@/modules/sys/components/UserText.vue';
import GroupOrTextSelect from '@/modules/sys/components/form/field/GroupOrTextSelect.vue';
import DirectiveDownload from '@/directives/download';
import DirectivePermission from '@/directives/permission';
import DirectiveRole from '@/directives/role';
import FileDialogPreview from '@/components/basic/FileDialogPreview.vue';

/**
 * 异步加载编辑器
 * */
const Editor = defineAsyncComponent(() => import('@/components/form/field/editor/tinymce/editor.vue'));

/**
 * 异步加载pdf预览
 * */
const FilePreview = defineAsyncComponent(() => import('@/components/basic/FilePreview.vue'));

import MobileList from '@/components/mobile/list.vue';
import MobileForm from '@/components/mobile/form.vue';
import MobileUpload from '@/components/mobile/field/upload/upload.vue';

/**
 * 注册PC端组件
 * */
const HPcComponents = {
  Dialog,
  FullScreen,
  More,
  PageDataTable,
  DataTable,
  DataIterator,
  DictSelect,
  DictSelectTag,
  DictTreeSelect,
  DictTypeSelect,
  DictRadio,
  DictRadioText,
  DictRadioButton,
  DictCheckbox,
  DictCheckboxText,
  DictText,
  Editor,
  FilePreview,
  FileDialogPreview,
  GroupSelect,
  GroupText,
  UserText,
  GroupOrTextSelect,
  Form,
  InlineForm,
  PageForm,
  PageView,
  DetailTable,
  ContentPageForm,
  ContentPageView,
  DialogForm,
  DialogViewForm,
  DialogImportExcelForm,
  DialogUploadFileForm,
  HUploadInput,
  HAvatar,
  HUpload,
  HFileList,
  HImage,
  HIcon,
  HFieldLatlng,
  HDatePicker,
  HTime,
  HChart,
};

/**
 * 注册移动端组件
 * */
const HMobileComponents = {
  MobileList,
  MobileForm,
  DictText,
  MobileUpload,
  HIcon,
  HTime,
  HChart,
};

/**
 * 注册PC端通用组件
 * */
function setupComponents(app: App) {
  /**
   * 注册ajax下载指令
   * */
  app.use(DirectiveDownload);
  app.use(DirectivePermission);
  app.use(DirectiveRole);

  app.use(
    {
      install: (a, options) => {
        Object.entries(HPcComponents).forEach(([key, component]) => {
          if (/^H/.test(key)) {
            a.component(key, component);
          } else {
            a.component(`H${key}`, component);
          }
        });
      },
    },
    {},
  );
}

/**
 * 注册移动端通用组件
 * */
function setupMobileComponents(app: App) {
  /**
   * 注册ajax下载指令
   * */
  app.use(DirectiveDownload);
  app.use(DirectivePermission);
  app.use(DirectiveRole);

  app.use(
    {
      install: (a, options) => {
        Object.entries(HMobileComponents).forEach(([key, component]) => {
          if (/^H/.test(key)) {
            a.component(key, component);
          } else {
            a.component(`H${key}`, component);
          }
        });
      },
    },
    {},
  );
}

export { setupComponents, setupMobileComponents };
