import {ref, onMounted, createVNode} from 'vue'
import moment from 'moment'
import {getAllStudent, addRoom,} from '../../../../../../common/api/broaddcastTeacheingApi'
import {findAll, addTrain,deleteTrain} from '../../../../../../common/api/handkeyZuXun'
import {getGradingRuleListByType} from '../../../../../../common/api/GradingRuleApi'

import {message, Modal} from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function broaddcastTeacheing(selectCable) {
  const addDrillModal = ref(false) //弹框
  const {baseCode, morseCode} = useMorse()
  const trainData = ref({
    type: 0,
    count: 100,
    mode: 0,
    ruleId: ''
  })
  const checked = ref()
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: {customRender: 'name'}
    },
    {
      title: '报底类型',
      dataIndex: 'isAverage',
      key: 'isAverage',
      align: 'center',
      slots: {customRender: 'isAverage'}
    },
    {
      title: '报文类型',
      dataIndex: 'type',
      key: 'type',
      align: 'center',
      slots: {customRender: 'type'}
    },
    {
      title: '报文组数',
      dataIndex: 'messageNumber',
      key: 'messageNumber',
      align: 'center',
      slots: {customRender: 'messageNumber'}
    },
    {
      title: '创建人',
      dataIndex: 'user',
      key: 'user',
      align: 'center',
      slots: {customRender: 'user'}
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      align: 'center',
      slots: {customRender: 'status'}
    },
    {
      title: '身份',
      dataIndex: 'ident',
      key: 'ident',
      width: 120,
      align: 'center',
      slots: {customRender: 'ident'}
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: {customRender: 'action'}
    }
  ])
  const cacheData = ref([])
  const tableData = ref([])
  const ruleList = ref([])
  const totalPage = ref(1)
  const totalAll = ref(0)
  const currPage = ref(1)
  const isRandom = ref(true)
  const isAverage = ref(false)
  const formData = ref({
    trainType: 0,
    name: '手键组训-' + moment().format('YYMMDDhhmmss'),
    type: 0,
    codeSort: false,
    isRandom: true,
    messageNumber: 100,
    isAverage: false,
    ruleId: '',
    isCable: 0,
    startPage: 1
  })
  let self = ref('')
  self.value = [JSON.parse(localStorage.getItem('userInfo')).id]

  /**
   * 新增训练
   * @returns {boolean}
   */
  const loading = ref(false)
  const addTelexTrain = () => {
    if (formData.value.name === '') {
      message.error('请填写训练名称后提交')
      return false
    } else if (formData.value.messageNumber == '') {
      message.error('请填写报文组数后提交')
      return false
    } else if (checkUser.value.length == 0) {
      message.error('请选择训练人员后提交')
      return false
    }
    loading.value = true
    addDrillModal.value = false

    addTrain({
      ...formData.value,
      userId: checkUserId.value,
    }).then(res => {
      loading.value = false
      if (res.code == 200) {
        message.success('添加成功')
        cancelTrainModal()
        findRoomInfo()
      }
    })
  }
  const findRoomInfo = (currPage=1) => {
    findAll({page: currPage, rows: 10}).then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        const data = res.data.data
        data.forEach((item, i) => {
          item.userInfoList.forEach(user => {
            if (user.role == 1) {
              item['userName'] = user.userName;
              item['userImg'] = user.userImg;
              return
            }
          })
        })
        cacheData.value = data
        tableData.value = data
        totalPage.value = res.data.totalPage
        totalAll.value = res.data.totalNumber
      }
    })
  }

  const tableLoading = ref(false)
  //关闭弹框
  const cancelTrainModal = () => {
    addDrillModal.value = false
    isRandom.value = true
    isAverage.value = false
    checkUser.value = []
    checked.value = false
    formData.value = {
      trainType: 0,
      name: '手键组训-' + moment().format('YYMMDDhhmmss'),
      type: 0,
      codeSort: false,
      isRandom: false,
      messageNumber: 100,
      isAverage: false,
      ruleId: formData.value.ruleId,
      isCable: 0,
      startPage: 1
    }
  }
  let userList = ref([])
  let checkUser = ref([])
  let checkUserId = ref([])

  onMounted(() => {
    const info = JSON.parse(localStorage.getItem('userInfo'))
    getAllStudent().then(res => {
      res.data.forEach((item, i) => {
        if (res.data[i].id == self.value[0]) {
          res.data.splice(i, 1)
        }
      })
      userList.value = res.data.filter(item => item.id != info.id)
    })
    getAllRuleInfo()
    findRoomInfo()
  })
  const changeListPage = pag => {
    if (pag < 1) return false
    if(cacheData.value.length>=pag){
      currPage.value = pag
      findRoomInfo(pag)
    }
  }

  const changeChecked = () => {
    if (checked.value) {
      checkUser.value = userList.value.map((item, idx) => item.userName + idx)
      checkUserId.value = userList.value.map((item, idx) => item.id)
    } else {
      checkUser.value = []
    }
  }

  const changeUserList = (a, item) => {
    checkUserId.value = item.map(i => i.key)
  }
  /**
   * 获取规则分类
   */
  /**
   * 获取规则分类
   */
  const getAllRuleInfo = () => {
    getGradingRuleListByType({type: 0}).then(res => {
      if (res.code === 200) {
        res.data.map(rule => {
          if (rule.isDefault == 0) {
            formData.value.ruleId = rule.id
          }
        })
        ruleList.value = res.data;
      }
    })
  }
  const deleteModel = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该训练？',
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
    deleteTrain({trainId:v.id}).then(res=>{
      if(res.code === 200) {
        message.success('删除成功！')
        findRoomInfo()
      }else {
        message.error('删除失败！')
      }
    })
  }
  return {
    columns,
    tableData,
    cacheData,
    totalPage,
    currPage,
    tableLoading,
    addDrillModal,
    totalAll,
    cancelTrainModal,
    changeListPage,
    trainData,
    addTelexTrain,
    userList,
    checkUser,
    formData,
    isRandom,
    isAverage,
    checked,
    changeChecked,
    changeUserList,
    ruleList,
    loading,
    deleteModel
  }
}
