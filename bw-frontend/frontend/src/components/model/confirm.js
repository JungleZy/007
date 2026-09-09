import {createApp} from 'vue'
import Test from './Confirm.vue'
function confim(data) {
  return new Promise((resolve,reject)=>{
    const confimInstance = createApp(Test,{
      content:typeof(data.content) =='function'?data.content():data.content,
      title:typeof(data.title) =='function'?data.title():data.title,
      okType:data.okType,
      icon:data.icon,
      classStyle:data.class,
      maskClosable:data.maskClosable,
      okText:typeof(data.okText) =='function'?data.okText():data.okText ,
      cancelText:typeof(data.cancelText) =='function'?data.cancelText():data.cancelText,
      onConfirm:()=>{
        unmount()
        data.onOk()
        resolve()
      },
      onCancel:()=>{
        unmount()
        data.onCancel?data.onCancel():''
      },
    })
    const parentNode = document.createElement('div')
    const unmount = ()=>{
    confimInstance.unmount()
      document.body.removeChild(parentNode)
    }
    document.body.appendChild(parentNode)
    confimInstance.mount(parentNode)
  })
}
export default confim