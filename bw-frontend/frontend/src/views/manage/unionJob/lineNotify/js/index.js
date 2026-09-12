import {message, Modal} from 'ant-design-vue'
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject, createVNode} from 'vue'
import {useRouter, useRoute} from 'vue-router'
import {letterKey, numberKey} from '../../../../../components/preJob/telexTrain/js/enum'
import {
  getUserAll,
  apiSimulationRouterAddRoom,
  apiSimulationRouterRoomDetail,
  apiSimulationRouterFindRoom,
  getRoomUserList,deleteTrain
} from '../../../../../common/api/UserApi'
import moment from 'moment'
import useMorse from '../../../../../common/mixin/useMorse'
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function lineNotify(selectCable) {
  const router = useRouter()
  const route = useRoute()
  const {baseCode, morseCode} = useMorse()
  const columns = ref([
    {
      title: '房间名称',
      dataIndex: 'name',
      key: 'trainName',
      align: 'center'
    },
    {
      title: '报文类型',
      dataIndex: '',
      key: 'deviceId',
      align: 'center',
      slots: {customRender: 'bwType'}
    },
    {
      title: '报底类型',
      dataIndex: 'bdType',
      key: 'deviceId',
      align: 'center',
      slots: {customRender: 'bdType'}
    },
    {
      title: '报文组数',
      dataIndex: 'bwCount',
      key: 'deviceId',
      align: 'center'
    },
    {
      title: '房间状态',
      dataIndex: 'stats',
      key: 'deviceName',
      align: 'center',
      slots: {customRender: 'stats'}
    },
    {
      title: '身份',
      dataIndex: 'ident',
      key: 'ident',
      align: 'center',
      slots: {customRender: 'ident'}
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 180,
      slots: {customRender: 'action'}
    }
  ])
  const formData = ref({
    name: '',
    rate: 0,
    type: 1,
    codeShort: 1,
    isLowRate: false,
    codeMessageBody: []
  })

  const trainData = ref({
    isRandom: true,
    bwCount: 100,
    bdType: 2,
    bwType: 1,
    content: [],
    isAverage: false,
    isCable: 0,
    startPage: 1,
    numberType: false
  })
  const checkBoxList = ref([])
  const checkboxOptions = ref([])
  const newCheckboxOptions = ref([])
  const newCheckBoxList = ref([])
  const tableData = ref([])
  const tableList = ref([])
  const dibaoList = ref([])
  const currTablePage = ref(0)
  const loading = ref(false)
  const visibleModal = ref(false)
  const tableLoading = ref(false)
  const roomName = ref('')
  const targetKeys = ref([])
  const selectedKeys = ref(['1', '4'])

  const userInfo = JSON.parse(window.localStorage.getItem('userInfo'))

  //查看
  const seeData = row => {
    if (row.createUserId == userInfo.id) {
      router.push({
        path: route.matched[3].path + '/lineNotifyTrain',
        query: {
          roomState: 'createUser',
          id: row.id
        }
      })
    } else {
      getRoomUserList(row.id).then(data => {
        if (data.code != 200 || !data.data) {
          message.error(data.msg || '读取训练房间失败')
          return
        }
        const sendList = data.data.sendUserList.map(item => item.id)
        const putAwayList = data.data.receiveUserList.map(item => item.id)
        let str = '/lineNotifyTrain'
        if (sendList.includes(userInfo.id)) {
          router.push({
            path: route.matched[3].path + str,
            query: {
              roomState: 'send',
              id: row.id
            }
          })
        } else if (putAwayList.includes(userInfo.id)) {
          router.push({
            path: route.matched[3].path + str,
            query: {
              roomState: 'putAway',
              id: row.id
            }
          })
        } else {
          message.error('当前用户不在房间收发人员中，无法进入训练')
        }
      }).catch(() => message.error('读取训练房间失败，请重试'))
    }
  }

  /**
   * 生成线路通报
   */
  const createDrillInfo = (val1, val2) => {
    if (!roomName.value) {
      message.error('请输入房间名称')
      return false
    }
    if (checkBoxList.value.length == 0) {
      message.error('至少选择一对学员')
      return false
    }
    if (checkBoxList.value.length != newCheckBoxList.value.length) {
      message.error('发报和收听的人员个数不一致')
      return false
    }
    apiSimulationRouterAddRoom({
      roomName: roomName.value,
      sendUserList: checkBoxList.value,
      receiveUserList: newCheckBoxList.value,
      content: '',
      bdType: 1,
      bwType: trainData.value.bwType,
      bwCount: trainData.value.bwCount,
      isRandom: trainData.value.isRandom ? 1 : 0,
      isAverage: trainData.value.isAverage,
      isCable: trainData.value.isCable,
      cableId: trainData.value.cableId,
      startPage: trainData.value.startPage
    }).then(res => {
      if (res.code != 200) {
        message.error('添加失败')
        return
      }
      loading.value = false
      visibleModal.value = false
      getSimulationRouterFindRoomList()
      message.success('添加成功')
      roomName.value = ''
      checkBoxList.value = []
      newCheckBoxList.value = []
    })
  }

  const getSimulationRouterFindRoomList = () => {
    apiSimulationRouterFindRoom().then(data => {
      tableList.value = data.data
      tableData.value = data.data
      tableList.value = tableData.value.filter((item, i) => {
        return i < 10
      })
    })
  }
  //获取学员列表
  const getAllTeacherList = () => {
    getUserAll().then(data => {
      if (data.code !== 200 || !Array.isArray(data.data)) return
      checkboxOptions.value = data.data.map((item, idx) => {
        const obj = {
          label: item.userName,
          value: item.id,
          key: item.id,
          title: item.userName
        }
        return obj
      })
      checkboxOptions.value = checkboxOptions.value.filter(user => user.key != userInfo.id)
      newCheckboxOptions.value = JSON.parse(JSON.stringify(checkboxOptions.value))
    })
  }

  const cancelTrianModal = () => {
    tableLoading.value = false
    roomName.value = ''
    checkBoxList.value = []
    newCheckBoxList.value = []
  }

  const handleChange = (keys, direction, moveKeys) => {
    targetKeys.value = keys
  }
  const handlSelectChange = (source, target) => {
    checkBoxList.value = source
    newCheckBoxList.value = target
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
    deleteTrain({roomId:v.id}).then(res=>{
      if(res.code === 200) {
        message.success('删除成功！')
        getSimulationRouterFindRoomList()
      }else {
        message.error('删除失败！')
      }
    })
  }
  return {
    seeData,
    columns,
    tableData,
    tableList,
    currTablePage,
    loading,
    visibleModal,
    tableLoading,
    roomName,
    trainData,
    formData,
    checkboxOptions,
    newCheckboxOptions,
    checkBoxList,
    newCheckBoxList,
    createDrillInfo,
    handleChange,
    getSimulationRouterFindRoomList,
    cancelTrianModal,
    getAllTeacherList,
    handlSelectChange,
    targetKeys,
    selectedKeys,
    deleteModel
  }
}
