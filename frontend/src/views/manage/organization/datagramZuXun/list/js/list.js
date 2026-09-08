import {ref, onMounted, createVNode} from 'vue'
import moment from 'moment'
import {getAllStudent} from '../../../../../../common/api/broaddcastTeacheingApi'
import {addTrain,findDatagramList,deleteTrain} from '../../../../../../common/api/datagramZuXun'
import {getGradingRuleListByType,} from '../../../../../../common/api/GradingRuleApi'

import {message, Modal} from 'ant-design-vue'
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function broaddcastTeacheing(selectCable) {
  const addDrillModal = ref(false) //弹框
  const checked = ref()
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'title',
      key: 'title',
      align: 'center',
      slots: {customRender: 'title'}
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
      dataIndex: 'messageType',
      key: 'messageType',
      align: 'center',
      slots: {customRender: 'messageType'}
    },
    {
      title: '报文组数',
      dataIndex: 'totalNumber',
      key: 'totalNumber',
      align: 'center',
      slots: {customRender: 'totalNumber'}
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
  const tableData = ref([])
  const ruleList = ref([])
  const totalPage = ref(1)
  const totalPageAll = ref(1)
  const currPage = ref(1)
  const isRandom = ref(true)
  const isAverage = ref(false)
  let defaultRuleId = null
  const formData = ref({
    trainType: 0,
    title: '数据报组训-' + moment().format('YYMMDDhhmmss'),
    type: 0,
    totalNumber: 100,
    ruleId: '',
    isCable: 0,
    startPage: 1,
    patType: 0
  })
  let self = ref('')
  self.value = [JSON.parse(localStorage.getItem('userInfo')).id]

  /**
   * 新增训练
   * @returns {boolean}
   */
  const loading = ref(false)
  const addTelexTrain = () => {

    if (formData.value.title == '') {
      message.error('请填写训练名称后提交')
      return false
    } else if (formData.value.totalNumber == '') {
      message.error('请填写报文组数后提交')
      return false
    } else if (checkUser.value.length == 0) {
      message.error('请选择训练人员后提交')
      return false
    }
    loading.value = true
    addDrillModal.value = false
    // formData.value.isRandom = formData.value.isRandom?1:0
    // formData.value.isAverage = formData.value.isAverage?1:0
    addTrain({
      trainType: formData.value.trainType,
      title: formData.value.title,
      type: formData.value.type,
      totalNumber:  formData.value.totalNumber,
      ruleId: formData.value.ruleId,
      userId: checkUserId.value,
      isCable: formData.value.isCable,
      cableId: formData.value.cableId,
      patType: formData.value.patType,
      startPage: formData.value.startPage
    }).then(res => {
      loading.value = false
      if (res.code == 200) {
        message.success('添加成功')
        cancelTrainModal()
        findRoomInfo()
      }
    })
  }
  const findRoomInfo = () => {
    findDatagramList({page: 1, rows: 10}).then(res => {
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
        tableData.value = data
        totalPage.value = res.data.totalPage
        totalPageAll.value = res.data.totalNumber
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
      title: '数据报组训-' + moment().format('YYMMDDhhmmss'),
      type: 0,
      totalNumber: 100,
      ruleId: defaultRuleId,
      isCable: 0,
      startPage: 1,
      patType: 0
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
    if(totalPage.value>=pag){
      currPage.value = pag
      findDatagramList({page: pag, rows: 10}).then(res => {
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
          tableData.value = data
          totalPage.value = res.data.totalPage
        }
      })
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
    getGradingRuleListByType({type: 2}).then(res => {
      if (res.code === 200) {
        res.data.map(rule => {
          if (rule.isDefault == 0) {
            formData.value.ruleId = rule.id
            defaultRuleId = rule.id
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
    totalPage,
    currPage,
    tableLoading,
    addDrillModal,
    totalPageAll,
    cancelTrainModal,
    changeListPage,
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
