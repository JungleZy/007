import { ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { findAllTheoryKnowledgeQuestionLevel, saveTheoryKnowledgeQuestion } from '../../../../../../common/api/TheoryQuestionBankApi'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { saveTestPaper, findTestPaperById } from '../../../../../../common/api/TestApi'
import { message, Modal } from 'ant-design-vue'
import { treeOrganizeSb } from '../../../../../../components/test/nodeTree/organizationNodeTree'

export default function theNewTest(roomtest) {
  const knowledgeList = ref([]) //知识节点树
  const listData = ref([
    {
      title: '单选题',
      key: 1,
      isOpen: true,
      children: []
    },
    {
      title: '多选题',
      key: 2,
      isOpen: false,
      children: []
    },
    {
      title: '判断题',
      key: 3,
      isOpen: false,
      children: []
    },
    {
      title: '填空题',
      key: 4,
      isOpen: false,
      children: []
    },
    {
      title: '简答题',
      key: 5,
      isOpen: false,
      children: []
    }
  ])
  const params = ref({ type: '2', topic: '', options: [], answer: '', analysis: '' })
  const paperData = ref({
    total: 0,
    passTheExamThan: 0.6,
    singleChoice: [],
    multipleChoice: [],
    judge: [],
    completion: [],
    shortAnswer: []
  })
  const updateBank = ref({})
  const router = useRouter()
  const route = useRoute()
  const routeId = route.query.id //理论学习ID
  const arr = ref(['singleChoice', 'multipleChoice', 'judge', 'completion', 'shortAnswer'])
  const knowledgeShow = ref(false)
  paperData.value.passMark = paperData.value.total * paperData.value.passTheExamThan
  const modifyTheScores = () => {
    paperData.value.passMark = parseInt(paperData.value.total * paperData.value.passTheExamThan)
  }
  watch(
    paperData,
    () => {
      let num = 0
      for (let j of arr.value) {
        if (paperData.value[j].length !== 0) {
          num = paperData.value[j].length * paperData.value[j][0].score + num
        }
      }
      paperData.value.total = num
      modifyTheScores()
    },
    { deep: true }
  )

  const queryKnowledgeTree = e => {
    findAllTheoryKnowledgeQuestionLevel().then(res => {
      if (res.code === 200) {
        knowledgeList.value = treeOrganizeSb(res.data, [])
        paperData.value.levelId = knowledgeList.value[0].value
        selectTree(paperData.value.levelId)
      }
    })
  }
  const bornTest = () => {
    let flag = false
    const item = roomtest.value.params
    if (item.topic == '') {
      message.error('您的题目没有输入完全!请检查后提交!')
      flag = true
    }
    if (item.type == 1) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 2) {
      if (item.answer.length == 0) {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 3) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    } else if (item.type == 4) {
      if (item.answer == []) {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      } else if (item.answer.length != 0) {
        item.answer.forEach((val, i) => {
          if (val == '') {
            message.error('您的答案暂未填写!请检查后提交!')
            flag = true
          }
        })
      }
    } else if (item.type == 5) {
      if (item.answer == '') {
        message.error('您的答案暂未填写!请检查后提交!')
        flag = true
      }
    }
    if (!roomtest.value.selectTruelyValue) {
      message.error('请选择该题的知识节点!请检查后提交!')
      flag = true
    }
    if (flag) return

    if (updateBank.value.bool) {
      paperData.value[updateBank.value.key1][updateBank.value.key2] = item
      updateBank.value.bool = false
      message.success('修改成功!')
    } else {
      let obj = deepClone(item)
      delete obj.isType
      obj.options = JSON.stringify(obj.options)
      obj.answer = JSON.stringify(obj.answer)
      obj.levelId = roomtest.value.selectTruelyValue
      saveTheoryKnowledgeQuestion(obj).then(res => {
        if (res.code == 200) {
          res.data.options = JSON.parse(res.data.options)
          res.data.answer = JSON.parse(res.data.answer)
          res.data.score = 1
          if (res.data.type == '1') {
            paperData.value.singleChoice.push(res.data)
          } else if (res.data.type == '2') {
            paperData.value.multipleChoice.push(res.data)
          } else if (res.data.type == '3') {
            paperData.value.judge.push(res.data)
          } else if (res.data.type == '4') {
            paperData.value.completion.push(res.data)
          }
          findAllQuestion()
          message.success('提交成功!')
          knowledgeShow.value = false
        } else {
          message.success('提交失败!')
        }
      })
    }
  }
  const addTopic = e => {
    knowledgeShow.value = true
    params.value = { type: '1', topic: '', options: [], answer: '', analysis: '' }
    if (e.key == '2' || e.key == '4') {
      params.value.answer = []
    }
    if (e.key == '3') {
      params.value.options = [
        { name: '对', id: '1' },
        { name: '错', id: '2' }
      ]
    }
    params.value.type = e.key + ''
    params.value.isType = true
  }
  const clickMenu = (index, val) => {
    if (val === 'select') {
      listData.value[index].isOpen = true
    } else {
      listData.value[index].isOpen = !listData.value[index].isOpen
    }
  }
  const selectTree = e => {
    paperData.value.levelId = e
    findAllQuestion()
  }
  const submitTest = () => {
    let subData = {}
    subData = {
      id: routeId ? routeId : '',
      name: paperData.value.name,
      levelId: paperData.value.levelId,
      total: paperData.value.total,
      passMark: paperData.value.passMark,
      passTheExamThan: paperData.value.passTheExamThan,
      singleChoice: [],
      multipleChoice: [],
      judge: [],
      completion: [],
      shortAnswer: []
    }
    for (let j of arr.value) {
      paperData.value[j].forEach((e, index) => {
        subData[j].push({
          parentId: e.id,
          id: e.idTwo ? e.idTwo : '',
          score: e.score,
          sort: index,
          type: e.type,
          topic: e.topic,
          options: JSON.stringify(e.options),
          answer: Array.isArray(e.answer) ? JSON.stringify(e.answer) : e.answer,
          analysis: e.analysis
        })
      })
    }
    if (!subData.name) {
      message.error('请填写试卷名！')
      return
    }
    let score = 0
    for (let j of arr.value) {
      subData[j].forEach((e, index) => {
        score = score + Number(e.score)
      })
    }
    if (subData.total < score || subData.total > score) {
      message.error('请合理分配分数！')
      return
    }
    saveTestPaper(subData).then(res => {
      if (res.code === 200) {
        if (routeId) {
          message.success('修改试卷成功')
        } else {
          message.success('新建试卷成功')
        }
        skipDetails()
      }
    })
  }
  const skipDetails = e => {
    router.push({
      path: route.matched[4].path
    })
  }
  const findAllQuestion = () => {
    listData.value[0].children = paperData.value.singleChoice
    listData.value[1].children = paperData.value.multipleChoice
    listData.value[2].children = paperData.value.judge
    listData.value[3].children = paperData.value.completion
    listData.value[4].children = paperData.value.shortAnswer
  }
  const clickActive = (item, bool) => {
    clickMenu(Number(item.type) - 1, 'select')
    if (bool) {
      item.score = 1
      if (item.type == '1') {
        if (paperData.value.singleChoice[0]) {
          item.score = paperData.value.singleChoice[0].score
        }
        paperData.value.singleChoice.push(item)
      } else if (item.type == '2') {
        if (paperData.value.multipleChoice[0]) {
          item.score = paperData.value.multipleChoice[0].score
        }
        paperData.value.multipleChoice.push(item)
      } else if (item.type == '3') {
        if (paperData.value.judge[0]) {
          item.score = paperData.value.judge[0].score
        }
        paperData.value.judge.push(item)
      } else if (item.type == '4') {
        if (paperData.value.completion[0]) {
          item.score = paperData.value.completion[0].score
        }
        paperData.value.completion.push(item)
      } else if (item.type == '5') {
        if (paperData.value.shortAnswer[0]) {
          item.score = paperData.value.shortAnswer[0].score
        }
        paperData.value.shortAnswer.push(item)
      }
    } else {
      if (item.type == '1') {
        paperData.value.singleChoice = paperData.value.singleChoice.filter(ss => {
          return ss.id !== item.id
        })
      } else if (item.type == '2') {
        paperData.value.multipleChoice = paperData.value.multipleChoice.filter(ss => {
          return ss.id !== item.id
        })
      } else if (item.type == '3') {
        paperData.value.judge = paperData.value.judge.filter(ss => {
          return ss.id !== item.id
        })
      } else if (item.type == '4') {
        paperData.value.completion = paperData.value.completion.filter(ss => {
          return ss.id !== item.id
        })
      }
    }
    findAllQuestion()
  }
  const modifyTest = () => {
    if (!routeId) return
    findTestPaperById({ id: routeId }).then(res => {
      if (res.code === 200) {
        for (let j of arr.value) {
          if (res.data[j]) {
            res.data[j].forEach(e => {
              e.options = JSON.parse(e.options)
              e.answer = j === 'shortAnswer' || j === 'singleChoice' || j === 'judge' ? e.answer : JSON.parse(e.answer)
              e.idTwo = e.id
              e.id = e.parentId
            })
          }
        }
        paperData.value = res.data
        findAllQuestion()
      }
    })
  }
  const actionBar = (e, key, value, index) => {
    if (e === 5) {
      Modal.confirm({
        title: () => '您确定删除该题么?',
        okType: 'danger',
        okText: () => '确定',
        cancelText: () => '取消',
        onOk() {
          paperData.value[key] = paperData.value[key].filter(ss => {
            return ss.id !== value.id
          })
          findAllQuestion()
        }
      })
    } else if (e === 4) {
      updateBank.value.bool = true
      updateBank.value.key1 = key
      updateBank.value.key2 = index
      updateBank.value.data = paperData.value[key][index]
      updateBank.value.data.isType = true
    } else if (e === 3) {
      if (index != paperData.value[key].length - 1) {
        paperData.value[key][index] = paperData.value[key].splice(index + 1, 1, paperData.value[key][index])[0]
      } else {
        message.error('已经到底了！')
      }
    } else if (e === 2) {
      if (index != paperData.value[key].length - 1) {
        let obj = paperData.value[key].splice(index, 1)[0]
        paperData.value[key].push(obj)
      } else {
        message.error('已经到底了！')
      }
    } else if (e === 1) {
      if (index != 0) {
        paperData.value[key][index] = paperData.value[key].splice(index - 1, 1, paperData.value[key][index])[0]
      } else {
        message.error('已经到顶了！')
      }
    } else if (e === 0) {
      if (index != 0) {
        paperData.value[key].unshift(paperData.value[key].splice(index, 1)[0])
      } else {
        message.error('已经到顶了！')
      }
    }
  }
  return {
    knowledgeList,
    paperData,
    listData,
    params,
    knowledgeShow,
    updateBank,
    queryKnowledgeTree,
    clickMenu,
    selectTree,
    modifyTheScores,
    bornTest,
    addTopic,
    clickActive,
    submitTest,
    modifyTest,
    skipDetails,
    actionBar,
    findAllQuestion
  }
}
