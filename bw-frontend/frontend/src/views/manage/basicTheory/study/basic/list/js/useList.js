import { onMounted, ref } from 'vue'
import { getBasicTheoryOpen, listPageClassify } from '../../../../../../../common/api/TheoryKnowledgeApi.js'
// import moment from "../../../../../systemManage/structure/js/useStructure";
import { useRouter, useRoute } from 'vue-router'
import moment from 'moment'
import { getBasicTheory } from '../../../../../../../common/api/TheoryKnowledgeApi'
export default function (computeCardWidth) {
  const listData = ref([])
  const columns = ref([
    {
      title: '标题',
      dataIndex: 'title',
      align: 'center',
      key: 'title',
      ellipsis: true
    },
    {
      title: '课件数',
      dataIndex: 'swfs',
      align: 'center',
      key: 'swfs',
      ellipsis: true,
      slots: { customRender: 'swfs' }
    },
    {
      title: '学习状态',
      dataIndex: 'doneCount',
      align: 'doneCount',
      key: 'doneCount',
      ellipsis: true,
      slots: { customRender: 'doneCount' }
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      align: 'center',
      key: 'createUserName'
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: 'center',
      key: 'createTime',
      ellipsis: true,
      slots: { customRender: 'createTime' }
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      align: 'center',
      slots: { customRender: 'action' }
    }
  ])
  onMounted(() => {
    getList()
    initPageClassify()
  })
  const searchList = ref({})
  const initPageClassify = () => {
    listPageClassify().then(res => {
      searchList.value = res.data
    })
  }
  const difficulty = ref([])
  const specialty = ref([])
  const showData = ref([])
  const currTablePage = ref(1)
  const route = useRoute()
  const getList = () => {
    let token = localStorage.getItem('token')
    const data = {
      token: token,
      type: route.query.studyType,
      status: JSON.stringify([1])
    }
    if (specialty.value.length != 0) {
      data.specialty = JSON.stringify(specialty.value)
    }
    if (difficulty.value.length != 0) {
      data.difficulty = JSON.stringify(difficulty.value)
    }
    getBasicTheoryOpen(data).then(res => {
      if (res.code === 200) {
        res.data.forEach(d => {
          d.cday = moment(d.createTime * 1).format('YYYY-MM-DD')
          d.key = d.id
        })
        listData.value = res.data
        showData.value = res.data.filter((item, i) => i < currTablePage.value * 10)
        // setTimeout(()=>{
        //   computeCardWidth()
        // },5)
      }
    })
  }
  const selectTablePage = pag => {
    if (pag === '-' && currTablePage.value === 1) return false
    else if (pag === '+' && currTablePage.value === Math.ceil(listData.value.length / 10)) return false
    else if (pag === currTablePage.value) return false
    if (pag === '-') {
      currTablePage.value--
    } else if (pag === '+') {
      currTablePage.value++
    } else {
      currTablePage.value = pag
    }
    showData.value = []
    showData.value = listData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10)
  }
  const selectItem = v => {
    specialty.value = []
    difficulty.value = []
    currTablePage.value = 1
    if (v.active) {
      v.active = false
    } else {
      v.active = true
    }
    searchList.value.specialtyList.forEach(item => {
      if (item.active) {
        specialty.value.push(item.id)
      }
    })
    searchList.value.difficultyList.forEach(item => {
      if (item.active) {
        difficulty.value.push(item.id)
      }
    })
    getList()
  }
  return {
    listData,
    getList,
    showData,
    columns,
    currTablePage,
    selectTablePage,
    searchList,
    selectItem,
    difficulty,
    specialty
  }
}
