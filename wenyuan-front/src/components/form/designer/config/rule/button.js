import { localeOptions, localeProps } from '../../utils';

const label = '按钮';
const name = 'nButton';

export default {
  menu: 'aide',
  icon: 'icon-button',
  label,
  name,
  mask: true,
  event: ['click'],
  rule({ t }) {
    return {
      type: name,
      props: {},
      children: [t('com.nButton.name')],
    };
  },
  props(_, { t }) {
    return localeProps(t, name + '.props', [
      {
        type: 'input',
        field: 'formCreateChild',
      },
      {
        type: 'select',
        field: 'size',
        options: localeOptions(t, [
          { label: 'large', value: 'large' },
          { label: 'medium', value: 'medium' },
          { label: 'small', value: 'small' },
          { label: 'tiny', value: 'tiny' },
        ]),
      },
      {
        type: 'select',
        field: 'type',
        options: [
          { label: 'primary', value: 'primary' },
          {
            label: 'success',
            value: 'success',
          },
          { label: 'warning', value: 'warning' },
          { label: 'error', value: 'error' },
          {
            label: 'info',
            value: 'info',
          },
        ],
      },
      { type: 'switch', field: 'text' },
      {
        type: 'switch',
        field: 'round',
      },
      { type: 'switch', field: 'circle' },
      {
        type: 'switch',
        field: 'loading',
      },
      { type: 'switch', field: 'disabled' },
    ]);
  },
};
