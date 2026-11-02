const HJ_ONLY_THEORY_MENU_NAMES = new Set([
  'ChargeEquipment',
  'DutyBusiness'
])

const filterTheoryMenus = (menus, parentName = '') => {
  if (!Array.isArray(menus)) return menus

  return menus
    .filter(menu => !(parentName === 'TheoryStudy' && HJ_ONLY_THEORY_MENU_NAMES.has(menu.name)))
    .map(menu => ({
      ...menu,
      children: Array.isArray(menu.children)
        ? filterTheoryMenus(menu.children, menu.name)
        : menu.children
    }))
}

export const filterMenusByInterface = (menus, interfaceStyle) => {
  if (interfaceStyle === 'HJ') return menus
  return filterTheoryMenus(menus)
}
