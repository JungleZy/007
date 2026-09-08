import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { addRole, getRoleMenusInfo } from '../../../../../../common/api/RoleApi.js'
import * as R from 'ramda'

export default function (tableLoading, getRoleList) {
  const state = reactive({
    visible: false,
    modelTitle: '',
    onlyRead: false,
    confirmLoading: false,
    treeData: []
  })
  const roleFormState = reactive({
    id: null,
    title: '',
    remark: '',
    isAdmin: 1,
    isDefault: 1
  })
  const menusAll = ref([])
  const checkedMenus = ref([])
  const cacheCheckedMenus = ref([])
  const expandedKeys = ref([])
  const selectedKeys = ref([])
  const checkedKeys = ref([])

  const permissionsList = ref([]) //权限LIST
  const permissionsActive = ref([]) //当前选中权限LIST
  const menuList = ref([]) //选中目录LIST
  const clickMenu = ref([]) //当前选中目录
  const addMenus = ref([])
  watch(checkedKeys, () => {
    let cm = []
    checkedKeys.value.forEach(c => {
      let c1 = checkedMenus.value.filter(m => m.id === c)
      if (c1.length > 0) {
        cm.push(...c1)
      } else {
        c1 = menusAll.value.filter(m => m.id === c)
        c1.forEach(m => {
          m.title = m.meta.title
          m.pc = []
          m.pa = []
          m.permissions.forEach(p => {
            m.pa.push({
              label: p.title,
              value: p.key
            })
          })
          cm.push(m)
        })
      }
    })
    checkedMenus.value = cm
  })
  const showModal = (type, record) => {
    checkedKeys.value = []
    resetFrom()
    findRoleMenusInfo(R.isNil(record) ? '' : record.key)
    switch (type) {
      case 0:
        state.modelTitle = '新增角色'
        state.onlyRead = false
        break
      case 1:
        state.modelTitle = '角色详情'
        state.onlyRead = true
        break
      case 2:
        state.modelTitle = '编辑角色'
        state.onlyRead = false
        break
      default:
        break
    }
  }
  const findRoleMenusInfo = id => {
    checkedMenus.value = []
    menusAll.value = []
    getRoleMenusInfo({ id: id }).then(res => {
      state.treeData = []
      expandedKeys.value = []
      menuList.value = []
      addMenus.value = []
      permissionsList.value = []
      state.treeData = recursionQuery(res.data.menusAll)
      if (id !== '') {
        roleFormState.id = res.data.role.id
        roleFormState.title = res.data.role.title
        roleFormState.remark = res.data.role.remark
        roleFormState.isAdmin = res.data.role.isAdmin
        roleFormState.isDefault = res.data.role.isDefault
        res.data.menusChecked.forEach(m => {
          menuList.value.push(m.id)
          addMenus.value.push({
            menusId: m.id,
            per: JSON.parse(m.per)
          })
        })
        checkedMenus.value = res.data.menusChecked
        cacheCheckedMenus.value = res.data.menusChecked
      }
      state.visible = true
    })
  }
  const handleCancel = () => {
    state.visible = false
    resetFrom()
  }
  const handleOk = () => {
    handleSaveUser()
  }
  const handleSaveUser = () => {
    state.confirmLoading = true
    let menus = []
    addMenus.value.forEach(c => {
      menus.push(JSON.stringify(c))
    })
    addRole({ role: roleFormState, menus: menus }).then(res => {
      if (res.code === 200) {
        state.visible = false
        state.confirmLoading = false
        state.usersData = []
        message.success('数据操作成功！')
        tableLoading.value = true
        getRoleList()
      } else {
        message.error(res.message)
        state.confirmLoading = false
      }
    })
  }
  const resetFrom = () => {
    roleFormState.id = null
    roleFormState.title = ''
    roleFormState.remark = ''
    roleFormState.isAdmin = 1
    roleFormState.isDefault = 1
  }

  const selectedRoleKeys = id => {
    if (checkedKeys.value.indexOf(id) > -1) {
      checkedKeys.value = checkedKeys.value.filter(item => item !== id)
    } else {
      checkedKeys.value.push(id)
      menusAll.value
        .filter(m => m.id === id)
        .forEach(m => {
          m.title = m.meta.title
          m.pc = []
          m.pa = []
          m.permissions.forEach(p => {
            m.pa.push({
              label: p.title,
              value: p.key
            })
          })
          checkedMenus.value.push(m)
        })
      cacheCheckedMenus.value.map(item => {
        if (item.id === id) {
          item.pc.forEach(key => {
            checkedMenus.value.filter(v => v.id === id)[0].pc.push(key)
          })
        }
      })
    }
  }

  const selectedRolePerKeys = (id, key) => {
    checkedMenus.value.map(item => {
      if (item.id === id) {
        if (item.pc.indexOf(key) > -1) {
          item.pc = item.pc.filter(p => p !== key)
        } else {
          item.pc.push(key)
        }
      }
    })
  }

  const recursionQuery = e => {
    for (let j of e) {
      j.title = j.meta.title
      j.key = j.id
      if (j.children) {
        recursionQuery(j.children)
      }
    }
    return e
  }
  const checkTree = (e, val) => {
    menuList.value = e
    cs(menuList, permissionsActive)
  }
  const selectTree = (e, value) => {
    permissionsList.value = value.node.dataRef.permissions
    clickMenu.value = e[0]
    for (let j of addMenus.value) {
      if (j.menusId === clickMenu.value) {
        permissionsActive.value = j.per
      }
    }
  }
  const changeSelect = e => {
    permissionsActive.value = e
    cs(menuList, permissionsActive)
  }
  const cs = (e, vs) => {
    if (e.value.length !== 0) {
      if (e.value.length > addMenus.value.length) {
        for (let j of e.value) {
          if (addMenus.value.length === 0) {
            addMenus.value.push({
              menusId: j,
              per: []
            })
          } else {
            let item = 0
            for (let i of addMenus.value) {
              if (i.menusId === j) {
                i.menusId = j
              } else {
                item++
              }
            }
            if (item === addMenus.value.length) {
              addMenus.value.push({
                menusId: j,
                per: []
              })
              item = 0
            }
          }
        }
      } else {
        let arr = []
        for (let j of e.value) {
          for (let i of addMenus.value) {
            if (j === i.menusId) {
              arr.push(i)
            }
          }
        }
        addMenus.value = arr
      }
      for (let k of addMenus.value) {
        if (k.menusId === clickMenu.value) {
          k.per = vs.value
        }
      }
    }
  }
  return {
    state,
    showModal,
    handleCancel,
    handleOk,
    labelCol: { span: 4 },
    wrapperCol: { span: 19 },
    roleFormState,
    expandedKeys,
    selectedKeys,
    checkedKeys,
    checkedMenus,
    selectedRoleKeys,
    selectedRolePerKeys,
    checkTree,
    selectTree,
    changeSelect,
    permissionsList,
    permissionsActive,
    menuList
  }
}
