import { message, Modal } from 'ant-design-vue'
import moment from 'moment'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject } from 'vue'
import { findAll, saveTelexTrain } from '../../../../../../common/api/TelegramApi.js'
import { numberKey, letterKey } from '../../../../../../components/preJob/telexTrain/js/enum.js'
import { useRouter, useRoute } from 'vue-router'
import * as gr from '../../../../../../common/api/GradingRuleApi'

export default function telegramList(addDrillModal) {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: 'center',
      slots: { customRender: 'title' }
    },
    {
      title: '报文类型',
      dataIndex: 'type',
      key: 'type',
      // width: 120,
      align: 'center',
      slots: { customRender: 'type' }
    },
    {
      title: '报文总数',
      dataIndex: 'groupNumber',
      key: 'groupNumber',
      // width: 120,
      align: 'center',
      slots: { customRender: 'totalNumber' }
    },
    {
      title: '用时',
      dataIndex: 'validTime',
      key: 'validTime',
      align: 'center',
      // width: 130,
      slots: { customRender: 'duration' }
    },
    {
      title: '码率',
      dataIndex: 'speed',
      key: 'speed',
      // width: 120,
      align: 'center',
      slots: { customRender: 'speed' }
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      align: 'center',
      slots: { customRender: 'status' }
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: 'center',
      width: 120,
      slots: { customRender: 'action' }
    }
  ])
  const tableData = ref([])
  const tableList = ref([])
  const currTablePage = ref(0)
  const tableLoading = ref(true)
  const router = useRouter()
  const route = useRoute()
  const trainData = ref({
    type: 0,
    count: 1,
    mode: 0,
    ruleId: ''
  })
  const content = ref([])
  onMounted(() => {
    init()
  })
  const init = () => {
    findAll().then(res => {
      tableLoading.value = false
      if (res.code === 200) {
        // tableData.value = res.data.filter(item => item.totalNumber>0);
        res.data.forEach(item => {
          let hour = Math.floor((item.duration / 60 / 60) % 24)
          let min = Math.floor((item.duration / 60) % 60)
          let sec = Math.floor(item.duration % 60)
          item.duration = (hour > 10 ? hour : '0' + hour) + ':' + (min > 10 ? min : '0' + min) + ':' + (sec > 10 ? sec : '0' + sec)
          item.speed = item.status === 3 ? item.speed : null
        })
        tableList.value = res.data
        tableData.value = res.data
        selectTablePage(1)
      } else {
        message.error(res.message)
      }
    })
  }
  const selectType = () => {
    if (trainData.value.type == 0) {
      initContent(numberKey, 10)
    } else if (trainData.value.type == 1) {
      initContent(letterKey, 26)
    } else {
      let arr = [...numberKey, ...letterKey]
      initContent(arr, 36)
    }
  }
  //生成随机报文
  const initContent = (keyboard, num) => {
    let ctAll = []
    for (let i = 0; i < trainData.value.count * 100; i++) {
      let ct = []
      for (let j = 0; j < 4; j++) {
        const mat = Math.floor(Math.random() * num)
        ct.push(keyboard[mat].text2 ? keyboard[mat].text2 : keyboard[mat].text)
      }
      ctAll.push({
        value: null,
        text: ct
      })
    }
    content.value = ctAll
  }
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
  //新增连贯训练
  const addTelexTrain = () => {
    addDrillModal.value = false
    selectType()
    let time = moment().format('YYMMDDhhmmss')
    let title = ''
    switch (trainData.value.type) {
      case 0:
        title = '数字连贯-' + time
        break
      case 1:
        title = '字母连贯-' + time
        break
      case 2:
        title = '混合码-' + time
        break
    }
    const data = {
      content: JSON.stringify(content.value),
      name: title,
      type: trainData.value.type,
      groupNumber: trainData.value.count * 100,
      ruleId: trainData.value.ruleId
    }
    saveTelexTrain(data).then(res => {
      if (res.code == 200) {
        message.success('生成训练成功！')
        router.push({
          path: route.matched[4].path + '/postJobTelexTrain',
          query: {
            id: res.data.id
          }
        })
      } else {
        message.error('生成训练失败！')
      }
    })
  }
  const rule = ref([])
  const getRule = () => {
    gr.getGradingRuleListByType({ type: 2 }).then(res => {
      rule.value = res.data
      trainData.value.ruleId = res.data[0].id
    })
  }
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    selectType,
    initContent,
    trainData,
    addTelexTrain,
    rule,
    getRule
  }
}
