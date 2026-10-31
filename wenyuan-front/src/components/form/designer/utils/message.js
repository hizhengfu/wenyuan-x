import { useMessage } from 'naive-ui';

const message = (msg, type) => {
  const message = useMessage();
  return message.info(msg);
};

const errorMessage = (msg) => {
  const message = useMessage();
  return message.error(msg);
};

export default errorMessage;

export { message };
