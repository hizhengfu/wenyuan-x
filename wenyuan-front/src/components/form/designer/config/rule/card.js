import { localeOptions, localeProps } from '../../utils';

const label = '卡片';
const name = 'nCard';

export default {
  menu: 'layout',
  icon: 'icon-card',
  label,
  name,
  drag: true,
  inside: false,
  mask: false,
  rule({ t }) {
    return {
      type: name,
      props: {
        title: t('com.nCard.props.header'),
      },
      style: {
        width: '100%',
      },
      children: [],
    };
  },
  props(_, { t }) {
    return localeProps(t, name + '.props', [
      { type: 'input', field: 'title' },
      { type: 'switch', field: 'hoverable' },
      { type: 'switch', field: 'embedded' },
    ]);
  },
};
