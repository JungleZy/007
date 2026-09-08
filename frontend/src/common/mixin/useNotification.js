import {Button, notification} from "ant-design-vue";
import {h} from 'vue';
import {MessageFilled, SendOutlined} from '@ant-design/icons-vue';
import {useRoute, useRouter} from "vue-router";

export default function () {
  const networkBasePath = '/preview/networkUsing/equipmentNetwork'
  const router = useRouter();
  const route = useRoute()

  const notificationNewTrain = (data) => {
    const key = `open${Date.now()}`;
    const currentPath = route.path;
    if (currentPath.endsWith('datagramZuXunTrain') || currentPath.endsWith('electronKeyZuXunTrain') || currentPath.endsWith('handkeyZuXunTrain')) {
      return;
    }
    notification.open({
      message: '组训通知',
      description: h(
          'div',
          [
            h('p', {
              class: 'mt-2',
              innerHTML: `您有一场新的${data.map.type === 'telex' ? '数据报' : data.map.type === 'key' ? '电子键' : data.map.type === 'ticker' ? '手键' : ''}训练等待参加：`
            }),
            h('p', data.map.title),
          ]
      ),
      icon: h(MessageFilled, {style: 'color: #108ee9'}),
      btn: h(
          'button',
          {
            type: 'primary',
            icon: h(SendOutlined),
            class: 'layout-center',
            onClick: () => skipRouter(key, data.map),
          },
          '🚀 前往参加',
      ),
      key,
    });
  }
  const skipRouter = (key, data) => {
    if (data.type === 'telex') {
      router.push({
        path: `${networkBasePath}/datagramZuXunTrain`,
        query: {id: data.id, status: data.status}
      });
    } else if (data.type === 'key') {
      router.push({
        path: `${networkBasePath}/electronKeyZuXunTrain`,
        query: {id: data.id, status: data.status}
      });
    } else if (data.type === 'ticker') {
      router.push({
        path: `${networkBasePath}/handkeyZuXunTrain`,
        query: {id: data.id, status: data.status}
      });
    }
    notification.close(key)
  }
  return {
    notificationNewTrain
  }
}