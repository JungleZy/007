import { message, Modal } from 'ant-design-vue'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject, nextTick } from 'vue'
// import {getAllUser,getUserAndRoleById,getRoleAll,addUserRole,saveUser } from "../../../../../common/api/StructureApi.js";
import { saveTheoryKnowledge, getById, listPageClassify } from '../../../../../../../common/api/TheoryKnowledgeApi.js'
import { useRouter, useRoute } from 'vue-router'

import { saveTheoryKnowledgeTest, getTestBySwfId } from '../../../../../../../common/api/TestApi.js'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { fontSizeDispose } from '../../../../../../../common/utils/Utils'
export default function useEdit(roomtest, selectedKnowledgeSwfs, tData, content, selectedKnowledgeSwfsIndex, right, handleSelectedKnowledgeSwfs) {
  const route = useRoute()
  const router = useRouter()
  const textArray = ref(['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八', '十九', '二十'])
  const leftArr = ref([
    {
      title: '测验一',
      versions: 1,
      knowledgeTestContents: [{ type: '1', topic: '', options: [], answer: '', analysis: '' }]
    }
  ])
  const rightShow = ref(true)
  const rightValue = ref([])
  const leftCheck = ref(0)
  onMounted(() => {
    // editCheck(leftArr.value[0],0)
    initPageClassify()
  })
  const searchList = ref({})
  const initPageClassify = () => {
    listPageClassify().then(res => {
      searchList.value = res.data
      tData.value.knowledge.difficultyId = res.data.difficultyList[0].id
      tData.value.knowledge.specialtyId = res.data.specialtyList[0].id
    })
  }
  const testIndex = ref(0)
  const editCheck = (e, index) => {
    testIndex.value = index
    rightShow.value = false
    setTimeout(() => {
      rightShow.value = true
    }, 1)
    rightValue.value = e.knowledgeTestContents
  }
  const changeVersions = (e, index, event) => {
    if (window.event) {
      window.event.cancelBubble = true
    } else {
      event.preventDefault()
    }
    for (let i of leftArr.value) {
      i.versions = 0
    }
    leftArr.value[index].versions = 1
  }
  const addLeft = () => {
    let length = leftArr.value.length
    leftArr.value.push({
      title: '测验' + textArray.value[length],
      versions: 0,
      knowledgeTestContents: [{ type: '1', questionName: '', options: [], answer: '', analysis: '' }]
    })
  }
  const testVisible = ref(false)
  const showTest = () => {
    if (!selectedKnowledgeSwfs.value.test || selectedKnowledgeSwfs.value.test.length == 0) {
      leftArr.value = [
        {
          title: '测验一',
          versions: 1,
          knowledgeTestContents: [{ type: '1', topic: '', options: [], answer: '', analysis: '' }]
        }
      ]
    } else {
      leftArr.value = selectedKnowledgeSwfs.value.test
    }
    leftArr.value.forEach((item, index) => {
      if (item.versions == 1) {
        editCheck(item, index)
      }
    })
    testVisible.value = true
    nextTick(() => {
      fontSizeDispose()
    })
    return false
  }
  // 新增题目
  const addClass = () => {
    rightValue.value.push({ type: '1', topic: '', options: [], answer: '', analysis: '' })
    nextTick(() => {
      right.value.scrollTop = right.value.scrollHeight
    })
  }
  const deleteClassItem = i => {
    Modal.confirm({
      title: () => '确定删除该题么',
      // content: () => '',
      // icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        // PubSub.publishSync("callback_theoryEdit_close", true)
        rightValue.value.splice(i, 1)
        rightShow.value = false
        setTimeout(() => {
          rightShow.value = true
        }, 1)
      }
    })
  }
  const bornTest = () => {
    // testVisible.value=false
    let flag = false
    for (let i in leftArr.value) {
      leftArr.value[i].knowledgeTestContents.forEach((item, index) => {
        if (flag) {
          return
        }
        if (item.topic == '') {
          message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的题目没有输入完全!请检查后提交!')
          flag = true
          return
        }
        if (item.type == 1) {
          if (item.answer == '') {
            message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
            flag = true
            return
          }
        } else if (item.type == 2) {
          if (item.answer.length == 0) {
            message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
            flag = true
            return
          }
        } else if (item.type == 3) {
          if (item.answer == '') {
            message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
            flag = true
            return
          }
        } else if (item.type == 4) {
          if (item.answer == []) {
            message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
            flag = true
            return
          } else if (item.answer.length != 0) {
            item.answer.forEach((val, i) => {
              if (val == '') {
                message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
                flag = true
                return
              }
            })
          }
        } else if (item.type == 5) {
          if (item.answer == '') {
            message.error('您的' + leftArr.value[i].title + '第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
            flag = true
            return
          }
        }
      }) //判断 可以忽略
    }
    rightValue.value.forEach((item, index) => {
      if (flag) {
        return
      }
      if (item.topic == '') {
        message.error('您的第' + (index + 1) + '题的题目没有输入完全!请检查后提交!')
        flag = true
        return
      }
      if (item.type == 1) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return
        }
      } else if (item.type == 2) {
        if (item.answer.length == 0) {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return
        }
      } else if (item.type == 3) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return
        }
      } else if (item.type == 4) {
        if (item.answer == []) {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return
        } else if (item.answer.length != 0) {
          item.answer.forEach((val, i) => {
            if (val == '') {
              message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
              flag = true
              return
            }
          })
        }
      } else if (item.type == 5) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return
        }
      }
    }) //判断 可以忽略
    if (flag) {
      return
    }
    testVisible.value = false
    selectedKnowledgeSwfs.value.test = leftArr.value
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
        handleSelectedKnowledgeSwfs(selectedKnowledgeSwfsIndex.value, tData.value.knowledgeSwfs[selectedKnowledgeSwfsIndex.value])
        testVisible.value = false
      }
    })
  }
  return {
    testVisible,
    showTest,
    leftCheck,
    addLeft,
    editCheck,
    changeVersions,
    bornTest,
    leftArr,
    testIndex,
    rightValue,
    rightShow,
    searchList,
    addClass,
    deleteClassItem,
    takeNoTestVisible
  }
}
