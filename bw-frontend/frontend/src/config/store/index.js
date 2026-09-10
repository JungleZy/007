import {createStore} from 'vuex'

export default createStore({
  state: {
    router: {},
    permissions: [],
    online:{},
  },
  mutations: {
    // 进行数据更新，改变数据状态
    setRouter(state, router) {
      state.router = router;
    },
    setPermissions(state, permissions) {
      state.permissions = permissions;
    },
    setOnline(state, online){
      state.online = online;
    },
  },
  actions: {},
  getters: {
    // 获取到最终的数据结果
    getRouter(state) {
      return state.router;
    },
    getPermissions(state) {
      return state.permissions;
    },
    getOnline(state){
      return state.online;
    }
  },
  plugins: []
})