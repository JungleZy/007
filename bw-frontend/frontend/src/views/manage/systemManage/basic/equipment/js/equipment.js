import { message, Modal } from 'ant-design-vue'
import wubi from 'qq-wubi'
import moment from 'moment'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { getEquipmentAll } from '../../../../../../common/api/equipment.js'
import { numberKey, letterKey } from '../../../../../../components/preJob/telexTrain/js/enum.js'
import { useRouter, useRoute } from 'vue-router'

export default function equipmentList(addDrillModal) {
  const columns = ref([
    {
      title: '设备名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center'
    },
    {
      title: '设备ID',
      dataIndex: 'deviceId',
      key: 'deviceId',
      align: 'center'
    },
    {
      title: '图片地址',
      dataIndex: 'image',
      key: 'image'
    },
    {
      title: '是否启用',
      dataIndex: 'isEnable',
      key: 'isEnable',
      align: 'center',
      slots: { customRender: 'isShow' }
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 240,
      slots: { customRender: 'action' }
    }
  ])
  const tableData = ref([])
  const tableList = ref([])
  const currTablePage = ref(0)
  const tableLoading = ref(true)
  const router = useRouter()
  const route = useRoute()
  const wztype = ref('0')
  const trainData = ref({
    type: 0,
    mode: 0
  })
  const content = ref([])
  onMounted(() => {
    init()
  })
  const init = () => {
    currTablePage.value = 0
    let data
    if (wztype.value == 0) {
      data = {}
    } else if (wztype.value == 1) {
      data = { type: 2 }
    } else {
      data = { type: 0 }
    }
    getEquipmentAll(data).then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        // tableData.value = res.data.filter(item => item.totalNumber>0);
        // tableList.value = res.data
        tableData.value = res.data
        selectTablePage(1)
      } else {
        message.error(res.message)
      }
    })
  }
  const selectType = () => {}
  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = pag => {
    if (pag === '-' && currTablePage.value === 1) return false
    else if (pag === '+' && currTablePage.value === Math.ceil(tableData.value.length / 10)) return false
    else if (pag === currTablePage.value) return false

    if (pag === '-') {
      currTablePage.value--
    } else if (pag === '+') {
      currTablePage.value++
    } else {
      currTablePage.value = pag
    }
    tableList.value = []
    tableList.value = tableData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
  }
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    selectType,
    trainData,
    init,
    wztype
  }
}
