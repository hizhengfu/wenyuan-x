import { localeProps } from '../../utils';

const label = '面板';
const name = 'nCollapseItem';

export default {
  icon: 'icon-cell',
  label,
  name,
  drag: true,
  dragBtn: false,
  inside: true,
  mask: false,
  rule({ t }) {
    return {
      type: name,
      props: {
        title: t('com.nCollapseItem.name'),
      },
      style: {},
      children: [],
    };
  },
  props(_, { t }) {
    return localeProps(t, name + '.props', [
      {
        type: 'input',
        field: 'title',
      },
      {
        type: 'input',
        field: 'name',
      },
      {
        type: 'switch',
        field: 'disabled',
      },
    ]);
  },
};
