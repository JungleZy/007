import { getDepartmentPostsByDepartmentIdByJob, addDepartmentPost, getDepartmentPostInfoById, getUsersByDepartmentId, addDepartmentUserPosts, deleteDepartmentPostById, editDepartmentPost } from '../../../../common/api/DeptApi.js'
import { getRoleAll } from '../../../../common/api/RoleApi.js'
import { message, Modal, notification } from 'ant-design-vue'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide } from 'vue'

export default function useJob(state, context) {
  const columns = ref([
    {
      title: '职务名称',
      dataIndex: 'title',
      key: 'title',
      width: 100,
      align: 'center',
      slots: { customRender: 'title' }
    },
    {
      title: '对应人员',
      dataIndex: 'userArray',
      key: 'userArray',
      width: 100,
      align: 'center',
      slots: { customRender: 'userArray' }
    },
    {
      title: '对应角色',
      dataIndex: 'Role',
      key: 'Role',
      width: 100,
      align: 'center',
      slots: { customRender: 'Role' }
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
      getJobList()
    }
  )
  const getJobList = () => {
    getRoleAll().then(res => {
      state.Role = res.data
    })
    getDepartmentPostsByDepartmentIdByJob({
      departmentId: state.departmentId,
      pageNo: state.p.page,
      numPerPage: state.p.row
    }).then(res => {
      if (res.code === 200) {
        res.data.departmentPostEntities.map(item => {
          item.userArray = []
          item.Role = []
        })
      } else {
        message.error(res.message)
        return
      }
      state.userData = res.data.departmentPostEntities
      state.page.total = res.data.departmentPostsNum
      for (let i in state.userData) {
        // for (let v in state.Role) {
        //   if (state.userData[i].roleId === state.Role[v].role.id) {
        //     state.userData[i].Role = state.Role[v].role.title;
        //   }
        // }
        getDepartmentPostInfoById({
          //部门职位详情
          departmentPostId: state.userData[i].id
        }).then(req => {
          state.userData[i].userArray = req.data.userEntities
          state.userData[i].Role = req.data.roleEntities
        })
      }
    })
  }
  const tableChange = (pag, filters, sorter) => {
    state.p.page = pag.current
    state.p.row = pag.pageSize
    getJobList()
  }
  const handleSearch = val => {}
  const handleChange = val => {}
  const filterOption = (value, option) => {
    return option.label.indexOf(value) >= 0
  }
  const handleOK = () => {
    if (!state.User.job) {
      message.error('请输入职务名称后，再进行提交！')
      return
    }
    if (state.selectRole.length === 0) {
      message.error('请选择对应角色后，再进行提交！')
      return
    }
    if (!state.modalInfo.isEdit) {
      addDepartmentPost({
        title: state.User.job,
        departmentId: state.departmentId,
        roleId: state.selectRole,
        userIds: state.selectValue
      }).then(res => {
        if (res.code == 200) {
          message.success('添加成功')
          state.User.job = ''
          state.modalInfo.visible = false
          state.selectRole = undefined
          getJobList()
        } else {
          message.error('添加失败')
        }
      })
    } else {
      editDepartmentPost({
        title: state.User.job,
        departmentId: state.departmentId,
        departmentPostId: state.User.jobId,
        roleId: state.selectRole,
        userIds: state.selectValue
      }).then(res => {
        if (res.code == 200) {
          context.emit('childThing')
          message.success('操作成功！')
          state.modalInfo.visible = false
          getJobList()
        } else {
          message.error('操作失败！')
        }
      })
    }

    /*if (!state.modalInfo.isEdit) {
      addDepartmentPost({
        title: state.User.job,
        departmentId: state.departmentId,
        roleId: state.selectRole
      }).then(res => {
        if (res.code == 200) {
          message.success("添加成功");
          state.User.job = "";
          state.modalInfo.visible = false;
          state.selectRole = undefined
          getJobList()
        } else {
          message.error("添加失败")
        }
      })
    } else if (state.modalInfo.isEdit) {
      // console.log(state.selectValue)
      let userIds = ""
      userIds = state.selectValue
      addDepartmentUserPosts({
        title: state.User.job,
        departmentPostId: state.User.jobId,
        roleId: state.selectRole,
        userIds
      }).then(res => {
        if (res.code == 200) {
          addDepartmentPost({
            title: state.User.job,
            departmentId: state.departmentId,
            roleId: state.selectRole,
            id: state.User.jobId,
          }).then(res => {
            message.success("修改成功");
            state.modalInfo.visible = false
            getJobList()
          })
        } else {
          message.error("修改失败")
        }
      })
    }*/
  }
  const showModal = (e, data) => {
    if (!e) {
      state.modalInfo.modelTitle = '新增职务'
      state.modalInfo.visible = true
      state.modalInfo.isEdit = false
      state.User.job = ''
      state.selectValue = []
      state.selectRole = []
      getUsersByDepartmentId({
        departmentId: state.departmentId
      }).then(res => {
        state.options = res.data
      })
    } else if (e === 1) {
      state.User.jobId = data.id
      state.modalInfo.modelTitle = '编辑职务'
      state.modalInfo.visible = true
      state.modalInfo.isEdit = true
      state.selectValue = []
      state.selectRole = data.Role.map(i => i.id)
      state.User.job = data.title
      getUsersByDepartmentId({
        departmentId: state.departmentId
      }).then(res => {
        state.options = res.data
        state.selectValue = []
        for (let i in data.userArray) {
          state.selectValue.push(data.userArray[i].id)
        }
      })
    } else if (e === 2) {
      Modal.confirm({
        content: '你确定要把该职务从该部门移除么?',
        onOk() {
          deleteDepartmentPostById({
            id: data.id
          }).then(res => {
            if (res.code == 200) {
              message.success('删除成功')
              getJobList()
            } else {
              message.error('删除失败')
            }
          })
        }
      })
    }
  }
  return {
    columns,
    getJobList,
    handleSearch,
    handleChange,
    showModal,
    handleOK,
    tableChange,
    filterOption
  }
}
