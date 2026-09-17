import { useGlobalStore } from '../pinia/global.js'

export function ButtonPermission(app) {
  app.directive('per', {
    mounted(el, b, v) {
      if (hasPermission(b.value)) {
        if (!el.parentNode) {
          el.style.display = 'none'
        } else {
          el.parentNode.removeChild(el)
        }
      }
    }
  })
}

const hasPermission = (value) => {
  let userRouterPermissions = useGlobalStore().permissions
  if (userRouterPermissions === undefined || userRouterPermissions === null) {
    return false
  }
  let isExist = false
  if (userRouterPermissions.indexOf(value) === -1) {
    isExist = true
  }
  return isExist
}
