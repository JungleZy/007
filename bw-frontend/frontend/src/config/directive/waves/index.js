// import {handleClick, context} from './waves'
// // import App from '../../../App.vue'
// import app from '../../../exportApp'
//
// // const app=createApp(App);
// // console.log(app)
// app.directive('waves', {
//   created(el,binding){
//     el.addEventListener('mouseover', handleClick(el, binding), false)
//   },
//   bind(el, binding) {
//     el.addEventListener('mouseover', handleClick(el, binding), false)
//   },
//   update(el, binding) {
//     el.removeEventListener('mouseover', el[context].removeHandle, false)
//     el.addEventListener('mouseover', handleClick(el, binding), false)
//   },
//   unbind(el) {
//     el.removeEventListener('mouseover', el[context].removeHandle, false)
//     el[context] = null
//     delete el[context]
//   }
// });
