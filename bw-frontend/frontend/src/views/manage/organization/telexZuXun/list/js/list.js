import {ref, onMounted} from 'vue'
import moment from 'moment'
import {getAllStudent} from '../../../../../../common/api/broaddcastTeacheingApi'
import {addTrain, findDatagramList} from '../../../../../../common/api/datagramZuXun.js'
import {getGradingRuleListByType} from '../../../../../../common/api/GradingRuleApi'

import {message} from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'

export default function broaddcastTeacheing(selectCable) {
  const addDrillModal = ref(false) //弹框
  const {baseCode, morseCode} = useMorse()
  const trainData = ref({patType: 0})
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
      dataIndex: 'patType',
      key: 'patType',
      align: 'center',
      slots: {customRender: 'patType'}
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
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: {customRender: 'action'}
    }
  ])
  const totalAll = ref(0)
  const tableData = ref([])
  const ruleList = ref([])
  const totalPage = ref(1)
  const currPage = ref(1)
  const isRandom = ref(true)
  const isAverage = ref(false)
  const formData = ref({
    trainType: 0,
    title: '电传组训-' + moment().format('YYMMDDhhmmss'),
    messageType: 0,
    patType: 0,
    isRandom: false,
    totalNumber: 100,
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
      title: formData.value.title,
      isCable: formData.value.isCable,
      cableId: formData.value.cableId,
      startPage: formData.value.startPage,
      totalNumber: formData.value.totalNumber,
      ruleId: formData.value.ruleId,
      userId: checkUserId.value,
      trainType: 0,
      patType: formData.value.patType,
      type: formData.value.messageType
    }).then(res => {
      loading.value = false
      if (res.code === 200) {
        message.success('添加成功')
        cancelTrainModal()
        findRoomInfo()
      } else {
        message.error(res.message || '添加失败')
      }
    })
  }
  const findRoomInfo = (page = 1) => {
    findDatagramList({page, rows: 10}).then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        const data = res.data.data
        data.forEach(item => {
          item.userInfoList.forEach(user => {
            if (user.role == 1) {
              item.userName = user.userName
              item.userImg = user.userImg
              return
            }
          })
        })
        tableData.value = data
        totalPage.value = res.data.totalPage
        totalAll.value = res.data.totalNumber
        currPage.value = page
      } else {
        message.error(res.message || '训练列表加载失败')
      }
    })
  }
  const changeListPage = pag => {
    if (pag < 1 || pag > totalPage.value) return false
    findRoomInfo(pag)
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
      title: '电传组训-' + moment().format('YYMMDDhhmmss'),
      messageType: 0,
      patType: 0,
      isRandom: false,
      totalNumber: 100,
      isAverage: false,
      ruleId: formData.value.ruleId,
      isCable: 0
    }
  }
  let userList = ref([])
  let checkUser = ref([])
  let checkUserId = ref([])
  const checked = ref(false)

  onMounted(() => {
    const info = JSON.parse(localStorage.getItem('userInfo'))
    getAllStudent().then(res => {
      if (res.code !== 200 || !Array.isArray(res.data)) return
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
          }
        })
        ruleList.value = res.data;
      }
    })
  }

  return {
    columns,
    tableData,
    totalAll,
    totalPage,
    currPage,
    tableLoading,
    addDrillModal,
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
    loading
  }
}
