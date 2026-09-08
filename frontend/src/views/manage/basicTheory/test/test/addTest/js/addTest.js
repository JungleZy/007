import { ref, onMounted } from 'vue'
import moment from 'moment'
import { message } from 'ant-design-vue'
import 'moment/dist/locale/zh-cn.js'
import { useRoute, useRouter } from 'vue-router'
import { findAllTestPaper, savetheoryKnowledgeExam, findTheoryKnowledgeExamById, findTestPaperByLevelIdAndName } from '../../../../../../../common/api/TestApi'
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
  const params = ref({ title: '', startTime: '', duration: 120, teacher: userInfo.value.id, testPaper: '', stuId: [] })
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
      edit()
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
  //回显初始化数据
  const edit = () => {
    if (route.query.id) {
      findTheoryKnowledgeExamById({ id: route.query.id }).then(res => {
        res.data.paper.completion = JSON.parse(res.data.paper.completionList)
        res.data.paper.judge = JSON.parse(res.data.paper.judgeList)
        res.data.paper.multipleChoice = JSON.parse(res.data.paper.multipleChoiceList)
        res.data.paper.singleChoice = JSON.parse(res.data.paper.singleChoiceList)
        selectedPaper.value = res.data.paper.testPaperId
        params.value.title = res.data.exam.title
        params.value.startTime = moment(res.data.exam['start_time'])
        params.value.teacher = res.data.exam.teacher
        params.value.duration = Number(res.data.exam.duration)
        params.value.testPaper = res.data.paper
        id.value = res.data.exam.id
        for (let v of res.data.user) {
          studentsList.value.forEach(item => {
            if (item.id == v['user_id']) {
              item.active = true
            }
          })
        }
      })
    }
  }
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
    if (itemQ['completion'] && itemQ['completion'].length > 0) {
      itemQ['completion'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['judge'] && itemQ['judge'].length > 0) {
      itemQ['judge'].forEach(item => {
        item.answer = item.answer
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['multipleChoice'] && itemQ['multipleChoice'].length > 0) {
      itemQ['multipleChoice'].forEach(item => {
        item.answer = JSON.parse(item.answer)
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['singleChoice'] && itemQ['singleChoice'].length > 0) {
      itemQ['singleChoice'].forEach(item => {
        item.answer = item.answer
        item.options = JSON.parse(item.options)
      })
    }
    if (itemQ['shortAnswer'] && itemQ['shortAnswer'].length > 0) {
      itemQ['shortAnswer'].forEach(item => {
        item.answer = item.answer
        item.teacherScore = 0
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
  }
  //查询学员
  const searchStudent = () => {
    if (searchStudentStr.value !== '') {
      studentsList.value.forEach(item => {
        if (item.userName.indexOf(searchStudentStr.value) != -1) {
          item.show = true
        } else {
          item.show = false
        }
      })
    } else {
      studentsList.value.forEach(item => {
        item.show = true
      })
    }
  }
  //选取学生
  const selectStudent = (e, stu) => {
    stu.active = !stu.active
  }
  //新增考核
  const commitTest = () => {
    // let data = deepClone(params.value)
    let data = JSON.parse(JSON.stringify(params.value))
    for (let v of studentsList.value) {
      if (v.active) {
        data.stuId.push(v.id)
      }
    }
    if (data.startTime == '') {
      message.error('请选择开始时间！')
      return false
    } else if (data.stuId.length == 0) {
      message.error('请选择学员！')
      return false
    } else if (data['testPaper'] == '') {
      message.error('请选择考卷！')
      return false
    } else if (data.title.trim() == '') {
      message.error('请填写考核名称！')
      return false
    } else if (data.duration == '' || data.duration == 0) {
      message.error('请填写考核时长！')
      return false
    } else if (data.teacher == '') {
      message.error('请选择监考员！')
      return false
    }
    data.startTime = moment(data.startTime).format('YYYY-MM-DD HH:mm:ss')

    if (route.query.id) {
      data.id = id.value
    } else {
      data.testPaper.testPaperId = data.testPaper.id
      delete data.testPaper.id
    }
    // return
    savetheoryKnowledgeExam(data).then(res => {
      if (route.query.id) {
        message.success('修改成功！')
      } else {
        message.success('提交成功！')
      }
      if (res.code === 200) {
        router.go(-1)
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
    searchStudent,
    selectStudent,
    testPaper,
    selectPaper,
    previewModel
  }
}
