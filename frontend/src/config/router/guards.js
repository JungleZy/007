import router from './index';
import ExcessPage from '../../components/ExcessPage.vue';
import TransitionPage from '../../components/TransitionPage.vue';
import skipGuards from "./skipGuards.js";

const modules = import.meta.glob('../../views/**/*.vue');
let getRouter = undefined;

router.beforeEach((to, from, next) => {
  if (to.path.endsWith("login")) {
    window.localStorage.removeItem("tabCache");
    window.localStorage.removeItem("token");
    window.localStorage.removeItem("deviceId");
    window.localStorage.removeItem("userInfo");
    window.localStorage.removeItem("userRole");
    window.localStorage.removeItem("userRouter");
    router.options.routes[0].children[0].children = []
    router.addRoute(router.options.routes[0]);
    getRouter = undefined;
    next();
  } else {
    if (getRouter === undefined) {
      if (window.localStorage.getItem("token") === null) {
        next({path: '/login'});
      } else {
        getRouter = handleRouter();
        router.addRoute(getRouter);
        handleSkip(next, true, to, from);
      }
    } else {
      if (window.localStorage.getItem("token")) {
        handleSkip(next, false, to, from);
      } else {
        next({path: '/login'})
      }
    }
  }
})

function handleSkip(next, replace, to, from) {
  skipGuards(to, from, () => {
    nestedPatDown(to).then();
    if (replace) {
      if (to.path === '/404' && to.redirectedFrom !== undefined) {
        next({path: to.redirectedFrom?.fullPath, query: to.query, replace: true});
      } else {
        next({...to, replace: true});
      }
    } else {
      next();
    }
  })
}

/**
 * 处理动态路由
 */
function handleRouter() {
  if (router.options.routes[0].children[0].children.length === 0) {
    const routerData = JSON.parse(window.localStorage.getItem("userRouter"));
    routerData.forEach(r => {
      let child = [];
      r.children.forEach(c => {
        c.component = (c.component !== '-1' ? (c.component !== '0' ? modules[`../../views${c.component}.vue`] : ExcessPage) : TransitionPage);
        handlePermissions(c);
        child.push(c);
        if (c.children !== null && c.children.length > 0) {
          dg(c);
        }
      })
      r.component = (r.component !== '-1' ? (r.component !== '0' ? modules[`../../views${r.component}.vue`] : ExcessPage) : TransitionPage);
      handlePermissions(r);
      if (child.length > 0) {
        r.children = child;
      } else {
        let {children, ...params} = r;
        r = params;
      }
      router.options.routes[0].children[0].children.push(r);
    })
  }
  return router.options.routes[0];
}

function dg(e) {
  e.children.forEach(c => {
    c.component = (c.component !== '-1' ? (c.component !== '0' ? modules[`../../views${c.component}.vue`] : ExcessPage) : TransitionPage);
    handlePermissions(c);
    if (c.children === null) {
      c.children = []
    } else if (c.children.length > 0) {
      dg(c);
    }
  })
}

function handlePermissions(r) {
  if (r.permissions !== null) {
    r.meta.permissions = [];
    r.permissions.forEach(p => {
      r.meta.permissions.push(p.key);
    })
  }
}

/**
 * 递归处理多余的 layout : <router-view>，
 * 让需要访问的组件保持在第一层 index : <router-view> 之下
 * @param to
 */
async function nestedPatDown(to) {
  if (to.matched && to.matched.length > 2) {
    for (let i = 0; i < to.matched.length; i++) {
      const element = to.matched[i];
      if (element.components.default.name === 'TransitionPage' || element.components.default.name === 'ExcessPage') {
        to.matched.splice(i, 1);
        await nestedPatDown(to);
      }
      // 如果没有按需加载完成则等待加载
      if (typeof element.components.default === 'function') {
        await element.components.default();
        await nestedPatDown(to);
      }
    }
  }
}