const backupMenuId = 'system-database-transfer'

// 本地功能入口不依赖 t_menus / t_role_menus。服务端仍独立校验管理员权限。
export function withLocalMenus(menus, role) {
  const removeBackup = items => (Array.isArray(items) ? items : [])
    .filter(item => item.id !== backupMenuId && item.name !== 'DatabaseManagement')
    .map(item => ({ ...item, children: removeBackup(item.children) }))
  const result = removeBackup(menus)
  if (role?.isAdmin !== 0) return result

  let system = result.find(item => item.name === 'SystemManage' || item.path === 'systemManage')
  if (!system) {
    system = {
      id: '1600', key: '1600', path: 'systemManage', name: 'SystemManage',
      component: '/manage/systemManage/Index', sort: 50, permissions: [], children: [],
      meta: {
        title: '系统管理', isMenu: true, isBread: false,
        icon: '/006/img/main/menu-xtgl.png', iconF: '/006/img/main/menu-xtgl-icon.png', height: '30%'
      }
    }
    result.push(system)
  }
  system.children.push({
    id: backupMenuId, key: backupMenuId, parentId: system.id,
    path: 'database', name: 'DatabaseManagement', sort: 3,
    component: '/manage/systemManage/database/Index', permissions: [], children: [],
    meta: { title: '数据备份', icon: 'icon-database', isMenu: true, isBread: true }
  })
  return result
}
