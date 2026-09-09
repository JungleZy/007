import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import useMorse from '../../../../../../common/mixin/useMorse.js'
import { partTimeFormatInfo } from '../../../../../../common/utils/Utils.js'
import { getTelexTrainByID, apiPostTelexPatTrainGetPage } from '../../../../../../common/api/TelegramApi.js'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import * as echarts from 'echarts'

export default function telegramList(patHairTrendBoxRef) {
  const loading = ref(true)
  const route = useRoute()
  const scoreData = ref({
    trainId: '',
    currPage: 1,
    errorNumber: 0,
    speed: 0,
    accuracy: 0,
    validTime: 0,
    preContent: [],
    content: [],
    nextContent: [],
    change: 0,
    name:""
  })
  const page = ref({
    pageAll: 0,
    current: 1
  })
  const situaData = ref([])
  const testData = ref({
    preCode: [],
    codeAll: [],
    nextCode: []
  })
  onMounted(() => {
    if (route.query.id && route.query.id !== '') {
      scoreData.value.trainId = route.query.id
      getTelexTrainByID({
        id: scoreData.value.trainId
      }).then(res => {
        loading.value = false
        if (res.code === 200) {
          let arr = formatData(res.data.existPage)
          res.data.codeAll.sort((a, b) => a.pageNumber-b.pageNumber)
          scoreData.value.content = arr.slice(0, 100)
          scoreData.value.nextContent = arr.slice(100, 200)

          scoreData.value.errorNumber = res.data.errorNumber
          scoreData.value.speed = res.data.totalSpeed
          scoreData.value.accuracy = parseInt(res.data.accuracy)
          scoreData.value.validTime = res.data.validTime
          scoreData.value.score = res.data.score
          scoreData.value.deductInfo = JSON.parse(res.data.deductInfo)
          scoreData.value.change = res.data.change
          scoreData.value.name = res.data.name
          if(res.data.isCable===1){
            page.value.pageAll = res.data.pageNumber
          }else {
            page.value.pageAll = Math.ceil(res.data.groupNumber / 100)
          }


          if (res.data.codeAll.length > 0 && res.data.codeAll[0].patValue) {
            testData.value.codeAll = res.data.codeAll[0].patValue
          }
          if (res.data.codeAll.length > 1 && res.data.codeAll[1].patValue) {
            testData.value.nextCode = res.data.codeAll[1].patValue
          }

          // testData.value.pagecode.forEach(item=>{
          //   pagecode.value.push(JSON.parse(JSON.stringify(item)))
          // })
        }
      })
    }
  })
  const pageTurn = async num => {
    if (num == 1) {
      if (page.value.current == page.value.pageAll) {
        return false
      }
      page.value.current++
      scoreData.value.preContent = deepClone(scoreData.value.content)
      scoreData.value.content = deepClone(scoreData.value.nextContent)
      testData.value.preCode = deepClone(testData.value.codeAll)
      testData.value.codeAll = deepClone(testData.value.nextCode)
    } else {
      if (page.value.current == 1) {
        return false
      }
      page.value.current--
      scoreData.value.nextContent = deepClone(scoreData.value.content)
      scoreData.value.content = deepClone(scoreData.value.preContent)
      testData.value.nextCode = deepClone(testData.value.codeAll)
      testData.value.codeAll = deepClone(testData.value.preCode)
    }
    let number = page.value.current + num
    if (number > page.value.pageAll || number < 1) return false
    const res = await apiPostTelexPatTrainGetPage({
      pageNumber: number,
      trainId: scoreData.value.trainId
    })
    if (num == 1) {
      scoreData.value.nextContent =formatData(res.data.pageVo)
      testData.value.nextCode = res.data.codeAll
    } else {
      scoreData.value.preContent = formatData(res.data.pageVo)
      testData.value.preCode = res.data.codeAll
    }
    return false
  }
  //格式化数据
  const formatData = (data)=>{
    let arr = []
    data.forEach(item => {
      let a = arr[arr.length-1]
      if(arr.length==0||arr[arr.length-1].sort!==item.sort){
        if(a?.list){
          a.list.sort((a,b)=>a[0]-b[0])
          let str = []
          a.list.forEach(item=>{
            str.push(item)
          })
          a.value = str
        }
        arr.push(item)
      }else {
        if(a.list===undefined){
          a.list = [a.value.split(',')]
          a.list.push(item.value.split(','))
        }else{
          a.list.push(item.value.split(','))
        }
      }
    })
    return arr
  }

  //获取指定分页低报
  const getPostTelexPatTrainGetPage = num => {
    // setTimeout(() => {
    //   computedCharts()
    // }, 1000)
    let number = page.value.current + num
    if (number > page.value.pageAll || number < 1) return
    apiPostTelexPatTrainGetPage({
      pageNumber: number,
      trainId: scoreData.value.trainId
    }).then(res => {
      if (num == 1) {
        scoreData.value.nextContent = res.data.pageVo
        testData.value.nextCode = res.data.codeAll
      } else {
        scoreData.value.preContent = res.data.pageVo
        testData.value.preCode = res.data.codeAll
      }
    })
  }
  const showChart = ref('total')

  /**
   * 点击查看当前报文拍发用时明细
   * @param key
   * @param i
   */
  const seeCurrKeysHairTrend = (key, i) => {
    let idTxt = 'ht_' + page.value.current + '_' + (i + 1) + '_' + key.key
    nextTick(() => {
      let ht = ref(document.getElementById(idTxt))
      patHairTrendBoxRef.value.scrollTo({ left: ht.value.offsetLeft - 80, behavior: 'smooth' })
    })
  }
  return {
    scoreData,
    loading,
    page,
    testData,
    pageTurn,
    showChart,
    situaData,
    seeCurrKeysHairTrend
  }
}
