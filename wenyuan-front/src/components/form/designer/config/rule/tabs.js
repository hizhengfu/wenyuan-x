import { localeOptions, localeProps } from '../../utils';

const label = '标签页';
const name = 'nTabs';

export default {
  menu: 'layout',
  icon: 'icon-tab',
  label,
  name,
  mask: false,
  event: ['tabClick', 'tabChange', 'tabRemove', 'tabAdd', 'edit'],
  children: 'nTabPane',
  rule() {
    return {
      type: name,
      style: { width: '100%' },
      children: [],
    };
  },
  props(_, { t }) {
    return localeProps(t, name + '.props', [
      {
        type: 'select',
        field: 'type',
        options: [
          { label: 'bar', value: 'bar' },
          { label: 'line', value: 'line' },
          { label: 'card', value: 'card' },
          { label: 'segment', value: 'segment' },
        ],
      },
      { type: 'switch', field: 'closable' },
      {
        type: 'select',
        field: 'placement',
        options: localeOptions(t, [
          { label: 'top', value: 'top' },
          { label: 'right', value: 'right' },
          { label: 'left', value: 'left' },
          { label: 'bottom', value: 'bottom' },
        ]),
      },
      { type: 'switch', field: 'stretch' },
    ]);
  },
};
