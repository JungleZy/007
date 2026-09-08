import { ref, onMounted } from 'vue'
import moment from 'moment'
import { message } from 'ant-design-vue'
import 'moment/dist/locale/zh-cn.js'
import { useRoute, useRouter } from 'vue-router'
import { findAllTestPaper, saveTheoryKnowledgeExamSelfTesting, findTheoryKnowledgeExamById, findTestPaperByLevelIdAndName } from '../../../../../../../common/api/TestApi'
import { findAllTheoryKnowledgeQuestionLevel } from '../../../../../../../common/api/TheoryQuestionBankApi'
import { getAllStudent, getAllTeacher } from '../../../../../../../common/api/UserApi'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { treeOrganizeSb } from '../../../../../../../components/test/nodeTree/organizationNodeTree'
import { listSort } from '../../../../../../../components/test/nodeTree/listSort'
export default function addTest(modelSelf) {
  const listData = ref([])
  const selectedPaper = ref('')
  const questions = ref([])
  const teachers = ref([])
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo')))
  const searchStudentStr = ref('') //搜索学员
  const studentsList = ref([]) //展示的学员
  const KnowledgeData = ref([]) //知识节点类型
  const route = useRoute()
  const router = useRouter()
  const preview = ref(false)
  const id = ref('')
  const searchPaperObj = ref({
    name: '',
    levelId: ''
  }) //按条件查询学员
  const params = ref({ title: '', startTime: '', testPaper: '', stuId: [] })
  onMounted(() => {
    testPaper()
    findAllTheoryKnowledgeQuestionLevel().then(res => {
      KnowledgeData.value = treeOrganizeSb(res.data, [])
    })
    getAllStudent().then(res => {
      studentsList.value = []
      for (let v of res.data) {
        v.show = true
        v.active = false
        studentsList.value.push(v)
      }
    })
    getAllTeacher().then(res => {
      teachers.value = []
      for (let v of res.data) {
        teachers.value.push({
          label: v.userName,
          value: v.id
        })
      }
    })
  })
  //组织知识节点类型
  const KnowledgeP = data => {
    for (let v of data) {
      if (v.parentId == 1) {
        KnowledgeData.value.push({
          label: v.name,
          value: v.id,
          options: []
        })
      }
    }
  }
  const KnowledgeChildren = data => {
    for (let v of data) {
      for (let i in KnowledgeData.value) {
        if (KnowledgeData.value[i].id == v.parentId) {
          KnowledgeData.value[i].options.push({
            value: v.id,
            label: v.name
          })
        }
      }
    }
  }
  const testPaper = () => {
    findAllTestPaper().then(res => {
      if (res.code === 200) {
        listData.value = listSort(res.data)
      }
    })
  }
  const clearSearch = () => {
    searchPaperObj.value.name = ''
    searchPaperObj.value.levelId = ''
    searchPaper('name')
  }
  const searchPaper = e => {
    if (!(e === 'name')) {
      searchPaperObj.value.levelId = e
    }
    findTestPaperByLevelIdAndName(searchPaperObj.value).then(res => {
      if (res.code === 200) {
        listData.value = listSort(res.data)
      }
    })
  }
  //点击预览，初始化题目信息
  const previewModel = item => {
    let itemQ = deepClone(item)
    if (itemQ['completion'].length > 0) {
      itemQ['completion'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['judge'].length > 0) {
      itemQ['judge'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['multipleChoice'].length > 0) {
      itemQ['multipleChoice'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['singleChoice'].length > 0) {
      itemQ['singleChoice'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    questions.value = itemQ
    preview.value = true
  }
  //选择考卷
  const selectPaper = item => {
    selectedPaper.value = item.id
    if (route.query.id) {
      const newPaper = deepClone(item)
      newPaper.testPaperId = newPaper.id
      newPaper.id = params.value.testPaper.id
      params.value.testPaper = newPaper
    } else {
      params.value.testPaper = item
    }
    commitTest()
  }
  //新增考核
  const commitTest = () => {
    // let data = deepClone(params.value)
    let data = JSON.parse(JSON.stringify(params.value))
    const userInfo = JSON.parse(localStorage.getItem('userInfo'))
    data.stuId.push(userInfo.id)
    data.startTime = moment().format('YYYY-MM-DD HH:mm:ss')
    data.title = '自主测试' + moment().format('YYMMDDhhmmss')
    if (route.query.id) {
      data.id = id.value
    } else {
      data.testPaper.testPaperId = data.testPaper.id
      delete data.testPaper.id
    }
    // return
    saveTheoryKnowledgeExamSelfTesting(data).then(res => {
      message.success('提交成功！')
      if (res.code === 200) {
        router.push({
          path: route.matched[4].path + '/studentStartTest',
          query: {
            id: res.data.id
          }
        })
      }
    })
  }
  const goBack = () => {
    router.go(-1)
  }
  const selectAllStu = e => {
    studentsList.value.forEach(item => {
      if (e.target.checked) {
        item.active = true
      } else {
        item.active = false
      }
    })
  }
  return {
    listData,
    params,
    selectedPaper,
    questions,
    studentsList,
    KnowledgeData,
    searchStudentStr,
    teachers,
    preview,
    searchPaperObj,
    selectAllStu,
    clearSearch,
    searchPaper,
    goBack,
    commitTest,
    testPaper,
    selectPaper,
    previewModel
  }
}
