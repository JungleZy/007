import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { saveTheoryKnowledge, getById } from '../../../../../../../common/api/TheoryKnowledgeApi.js'
import { message } from 'ant-design-vue'
import { saveTheoryKnowledgeTest, deleteThroyKnowledgeById, getTestBySwfId } from '../../../../../../../common/api/TestApi'

export default function (content, backFlag) {
  const route = useRoute()
  const router = useRouter()
  const userInfo = ref(JSON.parse(window.localStorage.getItem('userInfo')))
  const state = ref({
    visible: false,
    modelTitle: '',
    onlyRead: false,
    confirmLoading: false
  })
  const tData = ref({
    knowledge: {
      type: 0,
      status: true,
      cover: '/006/cover/base.jpg',
      title: '',
      createUserId: userInfo.value.id,
      createTime: new Date().getTime() + '',
      difficultyId: '',
      specialtyId: '',
      credit: 0
    },
    knowledgeSwfs: []
  })
  const selectedKnowledgeSwfs = ref({})
  const selectedKnowledgeSwfsIndex = ref(0)
  if (route.query.type === '0') {
    tData.value.knowledgeSwfs.push({
      title: '',
      content: '',
      createUserId: userInfo.value.id,
      cover: '/006/cover/base.jpg',
      createTime: new Date().getTime() + '',
      sort: 0,
      test: []
    })
    selectedKnowledgeSwfs.value = tData.value.knowledgeSwfs[0]
    content.value = selectedKnowledgeSwfs.value.content
  } else {
    getById({ id: route.query.id, type: route.query.studyType }).then(res => {
      //可以移植操作
      const n = fileUrl.indexOf('http://')
      const str = fileUrl.substring(n + 7, fileUrl.indexOf('/', n + 8))
      res.data.knowledgeSwfs.forEach(item => {
        if (item.content.indexOf('*9_9*') > -1) {
          item.content = item.content.replaceAll('*9_9*', str)
        }
      })

      res.data.knowledge.status = res.data.knowledge.status !== 0
      tData.value = res.data
      tData.value.knowledgeSwfs.forEach(item => {
        item.test.forEach(test => {
          test.knowledgeTestContents.forEach(op => {
            op.answer = JSON.parse(op.answer)
            op.options = JSON.parse(op.options)
          })
        })
      })
      handleData(tData.value.knowledgeSwfs[0])
    })
  }
  const deleteCurseware = index => {
    if (tData.value.knowledgeSwfs.length == 1) {
      message.error('请至少保留一个课件！')
      return
    }
    tData.value.knowledgeSwfs.splice(index, 1)

    handleSelectedKnowledgeSwfs(0, tData.value.knowledgeSwfs[0])
    // if (selectedKnowledgeSwfsIndex.value === index) {
    //   handleSelectedKnowledgeSwfs(selectedKnowledgeSwfsIndex.value-1,tData.value.knowledgeSwfs[selectedKnowledgeSwfsIndex.value-1]);
    // } else {
    //   handleSelectedKnowledgeSwfs(selectedKnowledgeSwfsIndex.value,tData.value.knowledgeSwfs[selectedKnowledgeSwfsIndex.value]);
    // }
  }
  const addSwf = () => {
    tData.value.knowledgeSwfs.push({
      title: '',
      content: '默认课件内容',
      cover: '/006/cover/base.jpg',
      createUserId: userInfo.value.id,
      createTime: new Date().getTime() + '',
      sort: tData.value.knowledgeSwfs.length,
      test: []
    })
    selectedKnowledgeSwfsIndex.value = tData.value.knowledgeSwfs.length - 1
    handleData(tData.value.knowledgeSwfs[tData.value.knowledgeSwfs.length - 1])
  }
  const handleSelectedKnowledgeSwfs = (index, e) => {
    selectedKnowledgeSwfs.value.content = content.value
    selectedKnowledgeSwfsIndex.value = index
    handleData(e)
  }
  const handleData = e => {
    selectedKnowledgeSwfs.value = e
    content.value = selectedKnowledgeSwfs.value.content
  }
  const handleOptions = async t => {
    backFlag.value = false
    if (t === 0) {
      if (tData.value.knowledge.id != undefined && route.query.type == 0) {
        deleteThroyKnowledgeById({
          id: tData.value.knowledge.id,
          type: route.query.studyType
        }).then(res => {
          router.go(-1)
        })
      } else {
        router.go(-1)
      }
    }
    if (t === 1) {
      if (tData.value.knowledge.title == '') {
        message.error('请输入标题!')
        return
      }
      if (!tData.value.knowledge.credit) {
        message.error('请输入学分!')
        return
      }
      if (!tData.value.knowledge.specialtyId) {
        message.error('请选择专业岗位!')
        return
      }
      if (!tData.value.knowledge.difficultyId) {
        message.error('请选择人员类别!')
        return
      }
      let a = null
      tData.value.knowledgeSwfs.forEach((item, index) => {
        if (item.title == '') {
          a = Number(index) + 1
        } else if (item.test.length == 0) {
          a = item
        }
      })
      if (a != null) {
        if (!isNaN(a)) {
          message.error('课件' + a + '未命名!')
          return
        } else {
          message.error('课件' + a.title + '未添加随堂测验！!')
          return
        }
      }
      let flag = true
      if (!flag) {
        return
      }
      for (let i of tData.value.knowledgeSwfs) {
        if (i.title == '') {
          i.title = '默认课件标题'
        }
      }
      selectedKnowledgeSwfs.value.content = content.value
      tData.value.knowledge.status = tData.value.knowledge.status === false ? 0 : 1
      tData.value.knowledge.type = route.query.studyType
      tData.value.knowledgeSwfs.forEach(item => {
        delete item.id
        item.test.forEach(v=>{
          delete v.id
        })
        //可移植
        const n = content.value.indexOf('http://')
        if (n > 1) {
          const str = content.value.substring(n + 7, content.value.indexOf('/', n + 8))
          item.content = item.content.replaceAll(str, '*9_9*')
        }
        item.test.forEach(test => {
          test.knowledgeTestContents.forEach(op => {
            op.answer = JSON.stringify(op.answer)
            op.options = JSON.stringify(op.options)
          })
        })
      })
      saveTheoryKnowledge(tData.value).then(res => {
        if (res.code === 200) {
          message.success((route.query.id ? '编辑' : '新增') + '理论知识成功！')
          router.go(-1)
        } else {
          message.error((route.query.id ? '编辑' : '新增') + '失败')
        }
      })
    }
  }
  return {
    state,
    tData,
    selectedKnowledgeSwfs,
    selectedKnowledgeSwfsIndex,
    addSwf,
    handleSelectedKnowledgeSwfs,
    handleOptions,
    deleteCurseware
  }
}
