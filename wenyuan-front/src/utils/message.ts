import { Dialog, Toast } from 'tdesign-mobile-vue';
import { createDiscreteApi, DialogOptions, ModalOptions } from 'naive-ui';

const {
  notification: NaiveUiNotification,
  dialog: NaiveUiDialog,
  modal: NaiveUiModel,
} = createDiscreteApi(['dialog', 'notification', 'modal'], {});

const cache_notification: string[] = [];

/**
 * 全局通知
 *
 * */
export function notification(option: NotificationOptions) {
  if (APP_MOBILE_UI === true) {
    let type: 'success' | 'error' = option.type === 'success' ? 'success' : 'error';

    Toast({
      theme: type,
      direction: 'column',
      message: option.content,
    });
  } else {
    let key = `${option.type}_${option.title}_${option.content}`;
    if (cache_notification.includes(key)) {
      return;
    }
    cache_notification.push(key);
    NaiveUiNotification.create({
      type: (option.type as 'error' | 'success' | 'default' | 'info' | 'warning') || 'info',
      title: option.title || '请注意',
      content: option.content || '',

      duration: option.duration || 5000,
      onAfterLeave: () => {
        let index = cache_notification.indexOf(key);
        cache_notification.splice(index, 1);
      },
    });
  }
}

/**
 * 确认对话框
 * */
export function confirm(option: ModalOptions) {
  if (APP_MOBILE_UI === true) {
    Dialog.confirm({
      title: (option.title as string) || '请注意',
      content: (option.content as string) || '点击确认后将提交数据，请注意操作。',
      buttonLayout: 'vertical',
      confirmBtn: '确认',
      cancelBtn: '取消',
      overlayProps: { zIndex: 99998 },
      zIndex: 99999,
    });
  } else {
    NaiveUiModel.create(
      Object.assign(
        {
          type: 'info',
          title: '请注意',
          content: '',
          bordered: true,
          preset: 'dialog',
          positiveText: '确定',
          negativeText: '取消',
          onPositiveClick: () => {},
        },
        option,
      ),
    );
  }
}
