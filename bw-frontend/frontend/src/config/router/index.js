import {createRouter, createWebHistory, createWebHashHistory} from 'vue-router';

const routes = [
  {
    key: '1',
    path: '/',
    name: 'Manage',
    component: () => import('../../views/manage/Index.vue'),
    children: [
      {
        key: '1000',
        path: 'preview',
        name: 'Preview',
        meta: {icon: 'icon-dashboard', title: '首页', isBread: true},
        component: () => import('../../views/manage/main/Preview.vue'),
        children: []
      },
      {
        key: '2000',
        path: 'login',
        name: 'Login',
        meta: {icon: 'icon-dashboard', title: '登录'},
        component: () => import('../../views/manage/login/Login.vue'),
      },
    ]
  },
  {
    key: '3',
    path: '/demo',
    component: () => import('../../views/demo/Index.vue'),
    children: [
      {
        key: '31',
        path: 'im',
        name: 'IMDemo',
        component: () => import('../../views/demo/chil/IMDemo.vue'),
      },
      {
        key: '32',
        path: 'div',
        name: 'DivDemo',
        component: () => import('../../views/demo/chil/DivDemo.vue'),
      },
      {
        key: '33',
        path: 'orc',
        name: 'OrcDemo',
        component: () => import('../../views/demo/chil/DivDemo.vue'),
      },
      {
        key: '34',
        path: 'vico',
        name: 'VicoDemo',
        component: () => import('../../views/demo/vico/Index.vue'),
      },
      {
        key: '35',
        path: 'photo',
        name: 'PhotoDemo',
        component: () => import('../../views/demo/photo/Index.vue'),
      },
      {
        key: '36',
        path: 'hanzi',
        name: 'HanziDemo',
        component: () => import('../../views/demo/hanzi/Index.vue'),
      }
    ]
  }, {
    key: '4',
    path: '/:W+',
    component: () => import('../../components/ExcessPage.vue'),
    redirect: '/404',
    hidden: true
  }, {
    key: '5',
    path: '/404',
    component: () => import('../../views/404/Index.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes: routes
})

export default router