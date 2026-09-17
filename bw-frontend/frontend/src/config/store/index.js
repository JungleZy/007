import {createStore} from 'vuex'

export default createStore({
  state: {
    permissions: [],
    online:{},
  },
  mutations: {
    // 进行数据更新，改变数据状态
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
    getPermissions(state) {
      return state.permissions;
    },
    getOnline(state){
      return state.online;
    }
  },
  plugins: []
})