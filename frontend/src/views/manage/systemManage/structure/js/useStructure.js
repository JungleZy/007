import {message, Modal} from 'ant-design-vue'
import {ExclamationCircleOutlined} from '@ant-design/icons-vue'
import {ref, onMounted, h, createVNode} from 'vue'
import {
  getAllUser,
  getUserAndRoleById,
  getRoleAll,
  addUserRole,
  saveUser,
  resetPassword,
  deleteUser
} from '../../../../../common/api/StructureApi.js'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import {parseIdCard} from '../../../../../common/utils/Utils.js'

export default function useStructure() {
  onMounted(() => {
    getUser()
    findRoleList(1)
  })
  const getUser = () => {
    getAllUser().then(res => {
      if (res.code === 200) {
        tableData.value = res.data
        tableList.value = []
        tableList.value = JSON.parse(JSON.stringify(tableData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)))
        searchUsersData.value = tableList.value
        total.value = tableData.value.length

        // tableList.value = res.data.filter((item,i) => i<currTablePage.value*10);
        // selectTablePage(currTablePage.value);
      } else {
        message.error(res.message)
      }
    })
  }
  const uploadLoading = ref(false)
  const inputValue = ref('')
  const userFormRef = ref()
  const userRoleId = ref('')
  const roleList = ref([])
  const searchUsersData = ref([])
  const total = ref(0)
  const searchData = ref({
    userName: '',
    userAccount: ''
  })
  const dataList = ref([])
  const beforeUpload = file => {
    const isJpgOrPng = file.type === 'image/jpeg' || file.type === 'image/png'
    if (!isJpgOrPng) {
      message.error('上传文件格式错误，只支持jpg或者png图片!')
    }
    const isLt2M = file.size / 1024 / 1024 < 4
    if (!isLt2M) {
      message.error('上传文件超出4MB大小限制！')
    }
    return isJpgOrPng && isLt2M
  }
  const handleIdCard = () => {
    let bday = parseIdCard(userFormState.value.idCard, 1)
    let sex = parseIdCard(userFormState.value.idCard, 2)
    if (bday.length === 10) {
      userFormState.value.bday = moment(bday)
    }
    userFormState.value.userSex = sex
  }
  const handleChange = info => {
    if (info.file.status === 'uploading') {
      uploadLoading.value = true
      return
    }
    if (info.file.status === 'done') {
      callback(`/${info.file.response.data}`)
    }
    if (info.file.status === 'error') {
      uploadLoading.value = false
      message.error('上传错误')
    }
  }
  const userFormState = ref({
    id: null,
    userImg: '/userImages/1.png',
    userAccount: '',
    userName: '',
    phone: '',
    idCard: '',
    password: 'a123456',
    userSex: 1,
    status: 0,
    bday: '',
    eday: ''
  })
  const validatePhone = async (rule, value) => {
    if (value === '') {
      return Promise.reject('请输入电话号码')
    } else {
      const reg = /^1[3-9]\d{9}$/
      if (!reg.test(value)) {
        return Promise.reject('手机号码错误，请检查后重新输入！')
      }
      return Promise.resolve()
    }
  }
  const validateIdCard = async (rule, value) => {
    if (value === '') {
      return Promise.reject('请输入身份证号码')
    } else {
      const reg = /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/
      if (!reg.test(value)) {
        return Promise.reject('身份证号码错误，请检查后重新输入！')
      }
      return Promise.resolve()
    }
  }
  const rules = {
    userAccount: [
      {required: true, message: '请输入用户名', trigger: 'change'},
      {min: 3, max: 30, message: '用户名长度在3~30字之间'}
    ],
    userName: [
      {required: true, message: '请输入用户真实姓名', trigger: 'change'},
      {min: 2, max: 9, message: '真实姓名长度在2~9字之间'}
    ],
    phone: [{required: true, validator: validatePhone, trigger: 'change'}],
    idCard: [{required: true, validator: validateIdCard, trigger: 'change'}],
    bday: [{required: true, message: '请输入出生日期', trigger: 'change', type: 'object'}],
    eday: [{required: true, message: '请输入入伍日期', trigger: 'change', type: 'object'}]
  }
  const findRoleList = type => {
    getRoleAll().then(res => {
      if (type) {
        res.data.forEach(r => {
          if (r.role.isDefault === 0) {
            userRoleId.value = r.role.id
          }
        })
      }
      roleList.value = res.data
    })
  }
  const fileUrl = ref(window.fileUrl)
  let isEdit = ref(0) //1 修改 ,0查看，2增加
  let showModal = ref(false)
  const columns = ref([
    {
      title: '头像',
      dataIndex: 'userImg',
      key: 'userImg',
      width: 100,
      align: 'center',
      slots: {customRender: 'userImg'}
    },
    {
      title: '用户名',
      dataIndex: 'userAccount',
      key: 'userAccount',
      align: 'center',
      slots: {customRender: 'userAccount'}
    },
    {
      title: '姓名',
      dataIndex: 'userName',
      key: 'userName',
      align: 'center',
      slots: {customRender: 'userName'}
    },
    {
      title: '电话',
      dataIndex: 'phone',
      key: 'phone',
      align: 'center',
      slots: {customRender: 'phone'}
    },
    {
      title: '入伍时间',
      dataIndex: 'eday',
      key: 'eday',
      align: 'center',
      slots: {customRender: 'eday'}
    },
    {
      title: '性别',
      dataIndex: 'userSex',
      key: 'userSex',
      align: 'center',
      width: 100,
      slots: {customRender: 'userSex'}
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      width: 200,
      align: 'center',
      slots: {customRender: 'action'}
    }
  ])
  const tableData = ref([])
  const tableList = ref([])
  const currTablePage = ref(1)
  const fileList = ref([])
  const selectTablePage = pag => {
    if (pag === '-' && currTablePage.value === 1) return false
    else if (pag === '+' && currTablePage.value === Math.ceil(total.value / 10)) return false
    if (total.value < 11) return false
    if (pag === '-') {
      currTablePage.value--
    } else if (pag === '+') {
      currTablePage.value++
    } else {
      currTablePage.value = pag
    }
    if (searchData.value.userName !== '') {
      dataList.value = tableData.value.filter(item => item.userName.includes(searchData.value.userName))
      tableList.value = []
      tableList.value = dataList.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
      searchUsersData.value = tableList.value
    }
    if (searchData.value.userAccount !== '') {
      dataList.value = tableData.value.filter(item => item.userAccount.includes(searchData.value.userAccount))
      tableList.value = []
      tableList.value = dataList.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
      searchUsersData.value = tableList.value
    }
    if (searchData.value.userName == '' && searchData.value.userAccount == '') {
      tableList.value = []
      tableList.value = tableData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
      searchUsersData.value = tableList.value
    }
  }
  const editUser = (id, e) => {
    showModal.value = true
    isEdit.value = e
    if (id) {
      //编辑，查看
      getUserAndRoleById({userId: id})
        .then(res => {
          if (res.code === 200) {
            userFormState.value = res.data.user
            userFormState.value.bday = moment(res.data.user.bday ? res.data.user.bday : '2021-01-01', '"YYYY-MM-DD"')
            userFormState.value.eday = moment(res.data.user.eday ? res.data.user.eday : '2021-01-01', '"YYYY-MM-DD"')
            userRoleId.value = res.data.role.id
          } else {
            message.error(res.message)
          }
        })
        .catch()
    } else {
      //新增
      userFormState.value = {
        id: null,
        userImg: '',
        userAccount: '',
        userName: '',
        phone: '',
        idCard: '',
        password: 'a123456',
        userSex: 1,
        status: 0,
        bday: '',
        eday: ''
      }
      userRoleId.value = '2'
    }
  }
  const takeModel = e => {
    //弹出层
    if (e) {
      //确定
      userFormRef.value
        .validate()
        .then(() => {
          handleSaveUser()
        })
        .catch(error => {
        })
    } else {
      //取消
      showModal.value = false
    }
  }
  // const handleSearch = () => {
  //   if (inputValue.value.userName === "" && inputValue.value.userAccount === "") {
  //     searchUsersData.value = [];
  //     return
  //   }
  //   if (inputValue.value.userName !== '') {
  //     searchUsersData.value = tableData.value.filter(item => item.userName.includes(inputValue.value.userName));
  //   }
  //   if (inputValue.value.userAccount !== '') {
  //     searchUsersData.value = inputValue.value.filter(item => item.userAccount.includes(inputValue.value.userAccount));
  //   }
  // }
  const handleSearch = () => {
    dataList.value = []
    searchUsersData.value = []
    currTablePage.value = 1
    if (searchData.value.userName !== '') {
      dataList.value = tableData.value.filter(item => item.userName.includes(searchData.value.userName))
    }
    if (searchData.value.userAccount !== '') {
      dataList.value = tableData.value.filter(item => item.userAccount.includes(searchData.value.userAccount))
    }
    if (dataList.value.length > 0) {
      searchUsersData.value = dataList.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
      total.value = dataList.value.length
    } else {
      total.value = tableData.value.length
      if (!searchData.value.userName && !searchData.value.userAccount) {
        searchUsersData.value = tableList.value
        selectTablePage(1)
      } else {
        searchUsersData.value = dataList.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
        total.value = dataList.value.length
      }
    }
  }
  const handleSaveUser = () => {
    let arr = []
    arr.push(userRoleId.value)
    let result = {
      id: userFormState.value.id ? userFormState.value.id : '',
      userImg: userFormState.value.userImg == '' ? (userFormState.value.userSex === 1 ? '/userImages/1.png' : '/userImages/0.png') : userFormState.value.userImg,
      userAccount: userFormState.value.userAccount,
      userName: userFormState.value.userName,
      phone: userFormState.value.phone,
      idCard: userFormState.value.idCard,
      password: userFormState.value.password,
      userSex: userFormState.value.userSex,
      status: userFormState.value.status,
      bday: userFormState.value.bday.format('YYYY-MM-DD'),
      eday: userFormState.value.eday.format('YYYY-MM-DD')
    }
    let role = {
      // userId:userFormState.value.id,
      userId: userFormState.value.id ? userFormState.value.id : '',
      roleIds: arr
    }
    saveUser(result).then(res => {
      if (res.code === 200) {
        message.success('编辑成功')
        // userFormState.value = res.data.user;
        // userRoleId.value = res.data.role.id;
        showModal.value = false
      } else {
        message.error(res.message)
        return
      }
      addUserRole(role).then(res => {
        if (res.code === 200) {
          // userFormState.value = res.data.user;
          // userRoleId.value = res.data.role.id;
          // showModal.value=false
          getUser()
        } else {
          message.error(res.message)
        }
      })
    })
  }
  const onResetPassword = (userId,userName) => {
    Modal.confirm({
      title: () => `确认重置用户【${userName}】的密码?`,
      icon: () => createVNode(ExclamationCircleOutlined),
      content: () => createVNode('div', {style: 'color:red;'}, '重置后新密码将在下次登陆时生效！'),
      onOk() {
        resetPassword({userId}).then((e) => {
          if (e.code !== 200) return
          Modal.success({
            title: () => `用户【${userName}】的密码重置成功`,
            content: () => h('div', {}, [
              h('p', `重置后密码：${e.data}`),
              h('p', '请告知用户，妥善保管'),
            ]),
          });
        })
      },
      onCancel() {
      },
    });

  }
  const deleteModel = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该人员？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteHistory(v)
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteUser({userId:v.id}).then(res=>{
      if(res.code === 200) {
        message.success('删除成功！')
        getUser()
      }else {
        message.error('删除失败！')
      }
    })
  }
  return {
    columns,
    tableData,
    tableList,
    selectTablePage,
    currTablePage,
    fileUrl,
    isEdit,
    editUser,
    onResetPassword,
    showModal,
    takeModel,
    uploadFileUrl: window.uploadFileUrl,
    beforeUpload,
    userFormState,
    fileList,
    labelCol: {span: 5},
    wrapperCol: {span: 17},
    rules,
    handleChange,
    handleIdCard,
    userRoleId,
    findRoleList,
    roleList,
    userFormRef,
    inputValue,
    uploadLoading,
    getUser,
    searchUsersData,
    total,
    searchData,
    handleSearch,
    deleteModel
  }
}
