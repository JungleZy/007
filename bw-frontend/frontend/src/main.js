import './common/styles/HJ/index.less'
import './common/styles/HJ/tailwind.css'
import './common/styles/theme.less'
import 'animate.css'
import {handleClick, context} from './config/directive/waves/waves'

import {createApp} from 'vue'
import {createPinia} from 'pinia'
import VueUeditorWrap from 'vue-ueditor-wrap'
import App from './App.vue'
import 'ant-design-vue/dist/antd.less'
import router from './config/router/index'
import './config/router/guards'
import store from './config/store/index.js'
import {ButtonPermission} from './config/directive/ButtonPermission.js'
import {ImgError} from './config/directive/ImgError.js'
import ButtonStyle from './components/common/ButtonStyle.vue'
import print from 'vue3-print-nb'
import {global} from './config/pinia/index.js'

const baseImg = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAMAAABg3Am1AAAARVBMVEUAAADNzc3Ozs7MzMzNzc3Nzc3Nzc3Ozs7MzMzOzs7Ozs7JycnJycnNzc3Nzc3////y8vLT09P5+fns7Oza2trm5ubg4OCqAG0AAAAADnRSTlMAkx7XyL59cnJeXRM0M4Va10AAAAFXSURBVEjHpZbtcoMgEEXFYIyJLsvy8f6P2tSpTfeCMkzPz3hPBFxYhoJxMRPtTGYZhwav+UaK2/y6iI+GKpjT19zphHs1vlo6xW5l/kmXPDH/oAaPvjwa5Xhi9Bej2kgTxL3hBM76K1j95+J+kKjXqr7+2f0h1b7HqH70TqFHNe6CrgfRgijD7PWm8skBWT3+rsT5Wgjq8fwWdD0zCqyrHadMmC+nvcAaNYRlMH2CGaY+YRpII5gXCKDAvUJAITWEjEJsCPgKppbgBWaAwkRAFhgQLKshxAc9HvhwC5XExOJCilSyQPE1GaG8W9xgAzWZ9y2qySHwQcKj6YWHQC53XKYPBo4Zz64CezhmhjtuBsRjY7H1PB5O9tN86pWNJbjBcZ/OhVw0ocduyEk+nrWgWF2loPLQhGIQjPuj/SCbPZpVYmYR5hCypx27djb23qvDvy4n3defL7NXZtd9hOppAAAAAElFTkSuQmCC"
const app = createApp(App)
app.directive('waves', {
  //波纹
  created(el, binding) {
    el.addEventListener('mouseover', handleClick(el, binding), false)
  },
  bind(el, binding) {
    el.addEventListener('mouseover', handleClick(el, binding), false)
  },
  update(el, binding) {
    el.removeEventListener('mouseover', el[context].removeHandle, false)
    el.addEventListener('mouseover', handleClick(el, binding), false)
  },
  unbind(el) {
    el.removeEventListener('mouseover', el[context].removeHandle, false)
    el[context] = null
    delete el[context]
  }
})
app.directive('img-fallback', {
  mounted(el, binding) {
    // 设置默认图片
    const defaultImage = binding.value || baseImg
    // 错误处理函数
    const handleError = () => {
      // 防止死循环
      if (el.src !== defaultImage) {
        el.src = defaultImage;
      }
      // 移除事件监听避免重复处理
      el.removeEventListener('error', handleError);
    };
    // 监听错误事件
    el.addEventListener('error', handleError);
    // 如果图片在监听之前就已经出错
    if (el.complete && el.naturalHeight === 0) {
      handleError();
    }
  },
  updated(el, binding) {
    // 当指令绑定的值变化时更新默认图片
    el.defaultImage = binding.value || baseImg
  }
});
app.directive('debounce', {
  mounted(el, binding) {
    // 获取用户传入的等待时间（默认为200ms）默认false为等带回调返回false下次才会在执行
    const waitTime = binding.arg ? parseInt(binding.arg) : false;
    const handler = binding.value.fn;
    let loding = false
    let timer = null;
    // 绑定事件监听器，这里以 input 为例
    el.addEventListener('click', () => {
      if(waitTime===false){
        if(loding===false){
          loding = true
          handler(binding.value.data).then(res=>{
            loding = res
          })
        }
        return
      }
      if (timer) clearTimeout(timer);
      timer = setTimeout(() => {
        handler(binding.value.data); // 执行用户传入的函数，并传递当前值
      }, waitTime);
    });
  },
  unmounted(el){
    el.addEventListener('click',el.$handle)
  }
});
app.component('ButtonStyle', ButtonStyle)
app.config.productionTip = false
app.use(router)
app.use(createPinia())
const useGlobalStore = global.useGlobalStore();
useGlobalStore.changeTheme(window.interfaceStyle)
app.use(store)
app.use(print)
app.use(VueUeditorWrap)
ButtonPermission(app, store)
ImgError(app)
app.mount('#app')
