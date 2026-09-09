import { message, Modal } from 'ant-design-vue'
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { deleteTheoryKnowledgeQuestionLevelById, saveTheoryKnowledgeQuestion, findAllQuestionByLevelId, deleteTheoryKnowledgeQuestion, saveBatch, exportTemplate as exportQuestionTemplate, exportQuestionBank } from '../../../../../../common/api/TheoryQuestionBankApi'
import { deepClone } from '../../../../../../common/utils/Utils.js'
import { listSort } from '../../../../../../components/test/nodeTree/listSort'
import * as mammoth from "mammoth";
import { Document, Packer, Paragraph, TextRun } from 'docx'
import * as XLSX from 'xlsx'
import { parseWordQuestions, parseSpreadsheetRows } from './questionImport.js'
export default function knowledgeTabel(selecttreeA, roomtest, topicType, emit, activeList, activeKnowledge) {
  // onMounted(()=>{
  //   radio()
  //   multiSelect()
  //   blanks()
  //   judge()
  // })
  const knowledgeShow = ref(false)
  const columns = ref([
    {
      title: '标题',
      dataIndex: 'topic',
      align: 'center',
      key: 'topic',
      ellipsis: true
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: 'center',
      key: 'createTime',
      width: 160,
      ellipsis: true,
      slots: { customRender: 'createTime' }
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      align: 'center',
      width: 160,
      key: 'createUserName'
    },
    {
      title: '类型',
      dataIndex: 'types',
      align: 'center',
      width: 80,
      key: 'types'
    },
    {
      title: '操作',
      key: 'action',
      width: 180,
      align: 'center',
      slots: { customRender: 'action' }
    }
  ])
  const findAllQuestionId = ref('')
  const listData = ref([])
  const searchStr = ref('')
  const tableLoading = ref(true)
  const difficulty = ref(undefined)
  const params = ref({ type: '1', topic: '', options: [], answer: '', analysis: '' })
  const typeCheckList = ref([
    { name: '单选', id: '1' },
    { name: '多选', id: '2' },
    { name: '判断', id: '3' },
    { name: '填空', id: '4' },
    { name: '简答', id: '5' }
  ])
  const openModel = ref({})
  const knowledgeId = ref('-1')
  const son = ref(null)
  let flag = false
  const dg = tree => {
    for (let i in tree) {
      if (tree[i].children) {
        dg(tree[i].children)
      } else if (tree[i].type) {
        flag = true
      }
    }
  }
  const takeNew = () => {
    dg(selecttreeA.value)
    if (!flag) {
      message.error('请至少建立一个知识节点!')
      return
    }
    params.value = { type: '1', topic: '', options: [], answer: '', analysis: '' }
    knowledgeShow.value = true
  }
  const takeNoTestVisible = () => {
    Modal.confirm({
      title: () => '您确定退出么?',
      content: () => '暂未保存!',
      // icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        knowledgeShow.value = false
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
    let obj = deepClone(item)
    obj.options = JSON.stringify(obj.options)
    obj.answer = JSON.stringify(obj.answer)
    obj.levelId = roomtest.value.selectTruelyValue
    saveTheoryKnowledgeQuestion(obj).then(res => {
      if (res.code == 200) {
        message.success('提交成功!')
        knowledgeShow.value = false
        findAllQuestion(findAllQuestionId.value == -1 ? findAllQuestionId.value : { key: findAllQuestionId.value })
      } else {
        message.success('提交失败!')
      }
    })
  }
  const activeAction = ref({})
  let selectID = -1 //导出题库时用
  const findAllQuestion = e => {
    activeAction.value = e
    selectID = e == -1 ? e : e.value
    knowledgeId.value = e == -1 ? e : e.parentId == 1 ? e.parentId : e.value
    tableLoading.value = true
    findAllQuestionId.value = e == -1 ? 1 : e.key
    const data = {
      levelId: findAllQuestionId.value,
      type: topicType ? topicType : ''
    }
    getQuestion(data)
    // findAllQuestionByLevelId(data).then(res => {
    //   if (res.code===200) {
    //     let list=res.data;
    //     for (let j of list) {
    //       j.types=typeCheckList.value[Number(j.type)-1].name;
    //       j.options=JSON.parse(j.options);
    //       j.isClick=false;
    //       if (activeList) {
    //         activeList.forEach(item=>{
    //           if (j.id==item) {
    //             j.isClick=true;
    //           }
    //         })
    //       }
    //     }
    //     listData.value=list;
    //     tableLoading.value=false;
    //   }
    // })
  }
  const getQuestion = obj => {
    findAllQuestionByLevelId(obj).then(res => {
      if (res.code === 200) {
        let list = res.data
        for (let j of list) {
          j.types = typeCheckList.value[Number(j.type) - 1].name
          j.options = JSON.parse(j.options)
          j.isClick = false
          if (activeList) {
            activeList.forEach(item => {
              if (j.id == item) {
                j.isClick = true
              }
            })
          }
        }
        listData.value = listSort(list)
        tableLoading.value = false
      }
    })
  }
  const serchQuestion = e => {
    const data = {
      levelId: '',
      type: '',
      name: ''
    }
    if (searchStr.value) {
      data.name = searchStr.value
    } else {
      delete data.name
    }
    if (findAllQuestionId.value) {
      data.levelId = findAllQuestionId.value
    } else {
      delete data.levelId
    }
    if (difficulty.value) {
      data.type = difficulty.value
    } else {
      data.type = topicType ? topicType : ''
    }
    if (e === 'delete') {
      searchStr.value = ''
      data.type = ''
      difficulty.value = undefined
    }
    getQuestion(data)
  }
  const deleteTheoryKnowledge = e => {
    Modal.confirm({
      title: () => '确定删除该题么',
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        deleteTheoryKnowledgeQuestion({
          id: e
        }).then(res => {
          if (res.code === 200) {
            message.success('删除成功')
            findAllQuestion(findAllQuestionId.value == -1 ? findAllQuestionId.value : { key: findAllQuestionId.value })
          }
        })
      }
    })
  }
  const queryModal = e => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    params.value = obj
    knowledgeShow.value = true
  }
  const toView = e => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    if (obj.type == 1 || obj.type == 3) {
      obj.answer = String(obj.answer)
    }
    if (obj.type == 4) {
      obj.topic = obj.topic.split('$_$').join('(___)')
    }
    openModel.value.value = obj
    openModel.value.bool = true
    nextTick(() => {
      son.value.analyzeTheTopic()
    })
  }
  const handlePrevious = () => {
    const index = listData.value.findIndex(item => item.id == openModel.value.value.id)
    if (index == 0) {
      message.error('已到第一题！')
      return
    }
    toView(listData.value[index - 1])
  }
  const handleNext = () => {
    const index = listData.value.findIndex(item => item.id == openModel.value.value.id)
    if (index == listData.value.length - 1) {
      message.error('已到最后一题！')
      return
    }
    toView(listData.value[index + 1])
  }
  const addQuestion = (e, bool) => {
    let obj = deepClone(e)
    obj.answer = JSON.parse(obj.answer)
    obj.type = String(obj.type)
    if (obj.type == 1 || obj.type == 3) {
      obj.answer = String(obj.answer)
    }
    if (obj.type == 4) {
      obj.topic = obj.topic.split('$_$').join('(___)')
    }
    if (bool) {
      e.isClick = true
    } else {
      e.isClick = false
    }
    emit('clickActive', obj, bool)
  }

  const uploadChange = async ({ file, onSuccess, onError }) => {
    try {
      if (!file) throw new Error('未收到上传文件')
      const buffer = await file.arrayBuffer()
      const isSpreadsheet = /\.xlsx?$/i.test(file.name || '')
      const workbook = isSpreadsheet ? XLSX.read(buffer, {type: 'array'}) : null
      const rows = workbook ? XLSX.utils.sheet_to_json(workbook.Sheets[workbook.SheetNames[0]], {defval: ''}) : null
      const params = isSpreadsheet
        ? parseSpreadsheetRows(rows, activeAction.value.key)
        : parseWordQuestions((await mammoth.extractRawText({arrayBuffer: buffer, preserveWhiteSpace: true})).value, activeAction.value.key)
      if (!params.length) throw new Error('未识别到有效题目')
      const response = await saveBatch(params)
      if (response.code !== 200) throw new Error(response.message || '题库批量导入失败')
      await findAllQuestion(activeAction.value)
      file.status = 'done'
      onSuccess?.(response)
      message.success('题库导入成功！')
    } catch (error) {
      file.status = 'error'
      onError?.(error)
      message.error(error.message || '题库导入失败')
    }
  }

  const exportTemplate = async type => {
    if (type == 1 && selectID == -1) {
      message.error('请先选择要导出的题库！')
      return
    }
    if (type == 0) {
      const response = await exportQuestionTemplate()
      if (response.code !== 200 || !Array.isArray(response.data)) {
        message.error(response.message || '模板获取失败')
        return
      }
      const row = Object.fromEntries(response.data.map(column => [column.field, column.example ?? '']))
      const sheet = XLSX.utils.json_to_sheet([row])
      const workbook = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(workbook, sheet, '题库模板')
      XLSX.writeFile(workbook, '题库-模板.xlsx')
      return
    }
    const response = await exportQuestionBank({levelId: selectID})
    if (response.code !== 200 || !Array.isArray(response.data)) {
      message.error(response.message || '题库导出失败')
      return
    }
    const arr = []
    for (let i = 1; i <= 5; i++) {
      const group = response.data.filter(item => item.type == i)
      if (group.length) arr.push(group)
    }
    if (arr.length) handleExportWordDataInfo(arr)
  }

  const handleExportWordDataInfo = (arr) => {
    let docChild = [];
    let arrStr = ["一、单项选择题","二、不定项选择题","三、判断题","四、填空题","五、简答题"]
    let arrOpt = ["A","B","C","D"]
    arr.forEach(item => {
      // 添加题所属类型
      docChild.push(
          new Paragraph({
            spacing: { before: 160 },
            children: [
              new TextRun({
                text: arrStr[item[0].type-1],
                bold: true,
                size: 26,
                color: '#000000'
              })
            ]
          })
      )
      item.forEach((topic, t) => {
        // 添加题目的标题
        docChild.push(
            new Paragraph({
              spacing: { before: 80 },
              text: (t+1)+'、'+topic.topic
            })
        )
        // 添加选择题选项
        topic.options = JSON.parse(topic.options);
        let optStr = '\r';
        if (topic.type == 1 || topic.type == 2) {
          topic.options.forEach(opt => {
            optStr += '    '+arrOpt[parseInt(opt.value)]+'、'+opt.label
          })
          docChild.push(
              new Paragraph({
                text: optStr
              })
          )
        }
        // 添加答案
        topic.answer = JSON.parse(topic.answer);
        let answerStr = '    答案：'
        if (topic.type == 1) {
          answerStr += arrOpt[parseInt(topic.answer)]
        } else if (topic.type == 2) {
          topic.answer.forEach(ans => {
            answerStr += arrOpt[parseInt(ans)]
          })
        } else if (topic.type == 3) {
          answerStr += (topic.answer=='1'?'对':'错')
        } else if (topic.type == 4) {
          answerStr += topic.answer.join('；')
        } else if (topic.type == 5) {
          answerStr += topic.answer
        }
        docChild.push(
            new Paragraph({
              text: answerStr
            })
        )
      })
    })

    const doc = new Document({
      sections: [{ children: docChild }]
    })
    Packer.toBlob(doc).then(blob => {
      const tempLink = document.createElement('a')
      tempLink.style.display = 'none'
      tempLink.href = URL.createObjectURL(blob)
      tempLink.download = activeAction.value.title + '.docx'
      document.body.appendChild(tempLink)
      tempLink.click()
      document.body.removeChild(tempLink)
    })
  }
  return {
    typeCheckList,
    tableLoading,
    difficulty,
    listData,
    columns,
    knowledgeShow,
    params,
    openModel,
    searchStr,
    knowledgeId,
    son,
    exportTemplate,
    uploadChange,
    takeNew,
    takeNoTestVisible,
    bornTest,
    findAllQuestion,
    deleteTheoryKnowledge,
    queryModal,
    toView,
    addQuestion,
    serchQuestion,
    handlePrevious,
    handleNext
  }
}
