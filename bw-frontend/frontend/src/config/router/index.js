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