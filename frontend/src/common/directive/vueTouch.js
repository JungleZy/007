import {vueTouch} from './tools.js'

export default class VueTouch {
  constructor(a) {
    this.app = a;
    this.init();
  }

  init() {
    this.app.directive('tap', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "tap");
      }
    });
    this.app.directive('longtap', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "longtap");
      }
    });
    this.app.directive('swipe', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "swipe");
      }
    });
    this.app.directive('swipeleft', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "swipeleft");
      }
    });
    this.app.directive('swiperight', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "swiperight");
      }
    });
    this.app.directive('swipedown', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "swipedown");
      }
    });
    this.app.directive('swipeup', {
      beforeMount(el, binding, vnode, prevVnode) {
        new vueTouch(el, binding, "swipeup");
      }
    });
  }
}