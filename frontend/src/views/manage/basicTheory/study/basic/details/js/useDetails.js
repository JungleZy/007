import { message, Modal } from 'ant-design-vue'
import { ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject, onBeforeUnmount } from 'vue'
// import {getAllUser,getUserAndRoleById,getRoleAll,addUserRole,saveUser } from "../../../../../common/api/StructureApi.js";
import { useRouter, useRoute } from 'vue-router'
import { deepClone } from '../../../../../../../common/utils/Utils.js'
import { getByIdAndToken } from '../../../../../../../common/api/TheoryKnowledgeApi.js'
import { sanitizeHtml } from '../../../../../../../common/utils/sanitizeHtml.js'
import { getByKnowledgeSwfIdAndEnable, saveUserKnowledgeSwfTestContent, getTestContentByUserIdAndKnowledgeSwfId, saveTheoryKnowledgeRecord } from '../../../../../../../common/api/TestApi.js'
import moment from 'moment'
import 'moment/dist/locale/zh-cn.js'
import { log } from '@antv/g2plot/lib/utils/invariant.js'
export default function userDetails(data) {
  onMounted(() => {
    countdown()
  })
  const route = useRoute()
  const routeId = route.query.id //理论学习ID
  const knowledgeSwfsId = ref('') //当前选中的课件ID
  const joinTime = ref('') //学习开始时间
  const countDown = ref(0) //倒计时
  const showSpacing = ref('________') //想要替换的内容
  const rightValue = ref([]) //学员展示测试题
  const truelyValue = ref([])
  const rightShow = ref(false)
  const testVisible = ref(false)
  const score = ref(0)
  const Knowledge = ref(0)
  const edgeItem = ref({})
  const edgeIndex = ref(0)
  const activeTile = ref(0)
  const nowTime = ref({})
  const checkItem = ref({}) //选中的左侧测验
  const token = localStorage.getItem('token')
  const hour = ref('00')
  const min = ref('00')
  const sec = ref('00')
  const time = ref(0)
  const timer = ref(0)
  const checkKnowledge = (e, i, bool) => {
    countdown()
    edgeIndex.value = i
    Knowledge.value = i
    if (bool) {
      loginAndLogoutTwo(knowledgeSwfsId)
    }
    activeTile.value = data.value.knowledgeSwfs[i].title
    checkItem.value = data.value.knowledgeSwfs[i]
    knowledgeSwfsId.value = data.value.knowledgeSwfs[i].id
    loginAndLogout(knowledgeSwfsId) //学习时长
    score.value = data.value.knowledgeSwfs[i].score
    const safeContent = sanitizeHtml(data.value.knowledgeSwfs[i].content)
    let iframe = window.frames['iframeId']
    iframe.document.write('<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">')
    iframe.document.write('<html xmlns="http://www.w3.org/1999/xhtml">')
    iframe.document.write('<head>')
    iframe.document.write('<meta http-equiv="Content-Type" content="text/html; charset=gb2312" />')
    iframe.document.write('<style>')
    iframe.document.write('::-webkit-scrollbar { /*滚动条整体样式*/')
    iframe.document.write('width: 7px; /*高宽分别对应滚动条的尺寸*/')
    iframe.document.write('height: 8px;')
    iframe.document.write('}')
    iframe.document.write('::-webkit-scrollbar-track {')
    iframe.document.write('background-color: rgba(0, 0, 0, 0.05);')
    iframe.document.write('}')
    iframe.document.write('::-webkit-scrollbar-thumb {')
    iframe.document.write('background-color: rgba(175, 176, 176, 0.3);')
    iframe.document.write('border-radius: 2px;')
    iframe.document.write('box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.2);')
    iframe.document.write('} body,html{margin:0} p{color:#e2f2ff}')
    iframe.document.write('</style>')
    iframe.document.write('</head>')
    iframe.document.write('<body style="padding: 0 12px">')
    iframe.document.write(safeContent)
    iframe.document.write('</body>')
    iframe.document.write('</html>')
    iframe.document.close()
  }
  const getToken = () => {
    getByIdAndToken({ token, id: route.query.id }).then(res => {
      //可以移植操作
      res.data.knowledgeSwfs.forEach(item => {
        // debugger
        item.content = item.content.replaceAll("http://*9_9*/api/file/getFile",window.fileUrl)
        // const str = item.content.substring(item.content.indexOf('http'), item.content.length)
        // let url = str.substring(0, str.indexOf('/', 10))
        // if (url.indexOf('\\') > -1) {
        //   url = url.substring(0, url.indexOf('\\'))
        // }
        // if (item.content.indexOf(url + '/ueditor') > -1) {
        //   // const fileUrl = window.fileUrl.substring(0,window.fileUrl.indexOf("/",10))
        //   item.content = item.content.replaceAll(url + '/ueditor', fileUrl + '/ueditor')
        // }
      })
      data.value = res.data
      checkKnowledge(edgeItem, edgeIndex.value, false)
    })
  }
  const showTest = () => {
    const val = data.value.knowledgeSwfs[Knowledge.value]
    getByKnowledgeSwfIdAndEnable({
      id: val.id,
      type: route.query.studyType
    }).then(res => {
      truelyValue.value = deepClone(res.data)
      res.data.forEach((item, index) => {
        item.options = JSON.parse(item.options)
        item.type = item.type.toString()
        item.answer = JSON.parse(item.answer)
        if (item.type == 1) {
          item.answer = ''
        } else if (item.type == 2) {
          item.answer = []
        } else if (item.type == 3) {
          item.options = [
            { name: '对', id: '1' },
            { name: '错', id: '2' }
          ]
          item.answer = ''
        } else if (item.type == 4) {
          for (let i in item.answer) {
            item.answer[i] = ''
          }
          item.topic = item.topic.replace(/\$_\$/g, showSpacing.value)
          // item.options=[];
          // item.answer=[];
          setTimeout(() => {
            // changeTitle(question.value.topic);
          }, 5)
        } else if (item.type == 5) {
          item.options = ''
          item.answer = ''
        }
      })
      getTestContentByUserIdAndKnowledgeSwfId({
        token,
        id: val.id,
        type: route.query.studyType
      }).then(resx => {
        if (resx.data) {
          let answerList = JSON.parse(resx.data.content)
          if (answerList.length !== 0) {
            res.data.forEach(item => {
              let obj = answerList.find(value => {
                return item.id === value.id
              })
              if (obj) {
                item.answer = obj.answer
              }
            })
          }
        }
        rightValue.value = res.data
        testVisible.value = true
      })
    })
  }
  const takeNoTestVisible = () => {
    if (score.value == 100) {
      testVisible.value = false
      return
    }
    Modal.confirm({
      title: () => '您确定退出么?',
      content: () => '暂未保存!',
      // icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        testVisible.value = false
      }
    })
  }
  const bornTest = () => {
    const val = data.value.knowledgeSwfs[Knowledge.value]
    let flag = false
    let commit = true
    for (let index in rightValue.value) {
      index = Number(index)
      let item = rightValue.value[index]
      if (item.type == 1) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return false
        }
      } else if (item.type == 2) {
        if (item.answer.length == 0) {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return false
        }
      } else if (item.type == 3) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return false
        }
      } else if (item.type == 4) {
        if (item.answer == []) {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return false
        } else if (item.answer.length != 0) {
          for (let i in item.answer) {
            if (item.answer[i] == '') {
              message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
              flag = true
              return false
            }
          }
        }
      } else if (item.type == 5) {
        if (item.answer == '') {
          message.error('您的第' + (index + 1) + '题的答案暂未填写!请检查后提交!')
          flag = true
          return false
        }
      }
    }
    //判断 可以忽略
    if (flag) {
      return
    }
    let biValue = deepClone(rightValue.value)
    biValue.forEach(item => {
      item.answer = JSON.stringify(item.answer)
    })
    let rightV = 0,
      allV = 0
    for (let i in biValue) {
      i = Number(i)
      allV += 1
      if (biValue[i].type == 2) {
        let flag = true
        biValue[i].answer = JSON.parse(biValue[i].answer)
        truelyValue.value[i].answer = JSON.parse(truelyValue.value[i].answer)
        if (truelyValue.value[i].answer.length == biValue[i].answer.length) {
          for (let v in truelyValue.value[i].answer) {
            if (biValue[i].answer[v]) {
              if (truelyValue.value[i].answer.includes(biValue[i].answer[v])) {
              } else {
                flag = false
              }
            } else {
              flag = false
            }
          }
        } else {
          flag = false
        }
        truelyValue.value[i].answer = JSON.stringify(truelyValue.value[i].answer)
        if (flag) {
          rightV += 1
          rightValue.value[i].icon = true
        } else {
          rightValue.value[i].icon = false
          // message.error('您的第'+(i+1)+'题的答案好像不正确!请检查后提交!');
          commit = false
          // return;
        }
      } else {
        if (biValue[i].answer == truelyValue.value[i].answer) {
          rightV += 1
          rightValue.value[i].icon = true
        } else {
          rightValue.value[i].icon = false
          // message.error('您的第'+(Number(i)+1)+'题的答案好像不正确!请检查后提交!');
          commit = false
          // return;
        }
      }
    }
    let cent = rightV / allV
    if (!commit) {
      message.error('您的答案好像不完全正确!请检查后提交!')
      return
    }
    saveUserKnowledgeSwfTestContent({
      knowledgeId: val.knowledgeId,
      knowledgeSwfId: val.id,
      content: JSON.stringify(rightValue.value),
      score: cent * 100,
      type: route.query.studyType
    }).then(res => {
      if (res.code == 200) {
        message.success('提交成功')
        testVisible.value = false
        // getToken()
      }
    })
  }
  const loginAndLogout = e => {
    saveTheoryKnowledgeRecord({
      knowledgeId: routeId,
      knowledgeSwfId: e.value,
      joinTime: '',
      exitTime: '',
      type: route.query.studyType
    }).then(res => {
      if (res.code === 200) {
        joinTime.value = res.data.joinTime
        setTimeout(() => {
          countDown.value = 1
        }, 60000)
      }
    })
  }
  const loginAndLogoutTwo = e => {
    if (countDown.value === 1) {
      countDown.value = 0
      saveTheoryKnowledgeRecord({
        knowledgeId: routeId,
        knowledgeSwfId: e.value,
        joinTime: joinTime.value,
        exitTime: '',
        type: route.query.studyType
      }).then(res => {})
    }
  }
  let setTime
  const countdown = () => {
    clearInterval(timer.value)
    timer.value = setInterval(() => {
      time.value++
      computationTime(time.value)
    }, 1000)
    //格式化时间
  }
  onBeforeUnmount(() => {
    clearInterval(timer.value)
  })
  const computationTime = total => {
    let hour
    let min
    let sec
    let day
    let h
    let m
    let s
    hour = Math.floor((total / 60 / 60) % 24)
    min = Math.floor((total / 60) % 60)
    sec = Math.floor(total % 60)
    day = Math.floor(total / 60 / 60 / 24)
    // 计算总小时数
    hour = hour + day * 24
    if (hour < 10 && hour >= 0) {
      h = '0' + hour
    } else {
      h = hour.toString()
    }
    if (min < 10 && min >= 0) {
      m = '0' + min
    } else {
      m = min.toString()
    }
    if (sec < 10 && sec >= 0) {
      s = '0' + sec
    } else {
      s = sec.toString()
    }
    nowTime.value.h1 = h.substring(0, 1) * 1
    nowTime.value.h2 = h.substring(1, 2) * 1
    nowTime.value.m1 = m.substring(0, 1) * 1
    nowTime.value.m2 = m.substring(1, 2) * 1
    nowTime.value.s1 = s.substring(0, 1) * 1
    nowTime.value.s2 = s.substring(1, 2) * 1
  }
  return {
    score,
    knowledgeSwfsId,
    Knowledge,
    checkKnowledge,
    getToken,
    showTest,
    testVisible,
    rightShow,
    rightValue,
    bornTest,
    takeNoTestVisible,
    loginAndLogoutTwo,
    checkItem,
    activeTile,
    hour,
    min,
    sec,
    nowTime
  }
}
