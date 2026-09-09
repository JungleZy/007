export function ButtonPermission(app, store) {
  app.directive('per', {
    mounted(el, b, v) {
      if (hasPermission(b.value, store)) {
        if (!el.parentNode) {
          el.style.display = 'none'
        } else {
          el.parentNode.removeChild(el)
        }
      }
    }
  })
}

const hasPermission = (value, store) => {
  let userRouterPermissions = store.getters.getPermissions
  if (userRouterPermissions === undefined || userRouterPermissions === null) {
    return false
  }
  let isExist = false
  if (userRouterPermissions.indexOf(value) === -1) {
    isExist = true
  }
  return isExist
}
