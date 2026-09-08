import { getUsersByUserNameStartingWith, addDepartmentUser, getDepartmentUsersByDepartmentId, getUsersByDepartmentId, deleteDepartmentUserById, getDepartmentUserByDepartmentIdAndUserId, addDepartmentUsersByDepartmentId } from '../../../../common/api/DeptApi.js'
import { message, Modal, notification } from 'ant-design-vue'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'

export default function useMember(state, context) {
  const columns = ref([
    {
      title: '姓名',
      dataIndex: 'userEntity.userName',
      key: 'userEntity.userName',
      width: 100,
      align: 'center',
      slots: { customRender: 'userEntity.userName' }
    },
    {
      title: '电话',
      dataIndex: 'userEntity.phone',
      key: 'userEntity.phone',
      width: 100,
      align: 'center',
      slots: { customRender: 'userEntity.phone' }
    },
    {
      title: '职务',
      dataIndex: 'departmentPostEntities',
      key: 'departmentPostEntities',
      width: 100,
      align: 'center',
      slots: { customRender: 'departmentPostEntities' }
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      width: 100,
      align: 'center',
      slots: { customRender: 'action' }
    }
  ])
  watch(
    () => state.departmentId,
    (oid, nid) => {
      state.p.page = 1
      getUserList()
    }
  )
  const getUserList = () => {
    getUsersByDepartmentId({
      departmentId: state.departmentId,
      pageNo: state.p.page,
      numPerPage: state.p.row
    }).then(res => {
      state.userData = res.data.userPostDtos
      state.page.total = res.data.departmentUsersNum
    })
  }
  const handleSearch = val => {
    getUsersByUserNameStartingWith({
      userName: val
    }).then(res => {
      state.options = res.data
    })
  }
  const handleChange = (val, option) => {}
  const tableChange = (pag, filters, sorter) => {
    state.p.page = pag.current
    state.p.row = pag.pageSize
    getUserList()
  }
  const focusName = val => {
    if (state.User.selectUser == '') {
      getUsersByUserNameStartingWith({
        userName: ''
      }).then(res => {
        state.options = res.data
      })
    }
  }
  const filterOption = (inputValue, option) => {
    return option.userName.indexOf(inputValue) >= 0
  }
  const handleOK = () => {
    addDepartmentUsersByDepartmentId({
      departmentId: state.departmentId,
      userIds: state.target
    }).then(res => {
      if (res.code == 200) {
        context.emit('childThing')
        message.success('添加成功')
        state.modalInfo.visible = false
        getUserList()
      } else {
        message.error('添加失败')
      }
    })
  }
  const showModal = async (e, data) => {
    if (!e) {
      state.modalInfo.modelTitle = '编辑成员'
      state.modalInfo.visible = true
      getUsersByUserNameStartingWith({
        userName: ''
      }).then(res => {
        res.data.map(item => {
          item.title = item.userName
          item.key = item.id
          return item
        })
        state.options = res.data
      })
      getUsersByDepartmentId({
        departmentId: state.departmentId
      }).then(res => {
        let array = []
        res.data.map(item => {
          item.title = item.userName
          item.key = item.id
          array.push(item.key)
          return item
        })
        state.target = array
      })
    } else if (e == 1) {
      //删除
      let id = ''
      await getDepartmentUserByDepartmentIdAndUserId({
        departmentId: state.departmentId,
        userId: data.id
      }).then(res => {
        if (res.code == 200) {
          if (res.data) {
            id = res.data.id
            Modal.confirm({
              content: '你确定要从该部门把他移除么?移除后对应职务中也不再会出现他！',
              onOk() {
                deleteDepartmentUserById({
                  id
                }).then(res => {
                  if (res.code == 200) {
                    message.success('删除成功')
                    context.emit('childThing')
                    getUserList()
                  } else {
                    message.error('删除失败')
                  }
                })
              }
            })
          } else {
            message.error('数据异常')
            return
          }
        } else {
          message.error(res.message)
          return
        }
      })
    }
  }
  const transferChange = (keys, direction, movekeys) => {
    state.target = keys
  }
  const transferSearch = (dir, value) => {
    // console.log(dir,value)
  }
  return {
    getUserList,
    handleSearch,
    handleChange,
    focusName,
    handleOK,
    showModal,
    columns,
    tableChange,
    transferChange,
    transferSearch,
    filterOption
  }
}
