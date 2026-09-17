import {defineStore} from 'pinia';

export const useGlobalStore = defineStore('global', {
  state: () => ({
    theme: 'LJ',
    leftWidth: 170,
    permissions: []
  }),
  actions: {
    changeTheme(t) {
      this.theme = t;
      if (t === "HJ" ) {
        this.leftWidth = window.innerWidth < 1260 ? 200 : 232
      } else if (t==="HJJ"){
        this.leftWidth = 210
      }else {
        this.leftWidth = 170
      }
    },
    setPermissions(p) {
      this.permissions = p
    }
  }
});