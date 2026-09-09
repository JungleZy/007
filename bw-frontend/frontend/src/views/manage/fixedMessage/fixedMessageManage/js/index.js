import {ref,onMounted} from 'vue'
import {getCableType,getCableAll,saveCableType,deleteCableTypeByID,getCableAllByID,getCableFloorAllByID,deleteList} from '../../../../../common/api/CableApi'
import {message} from 'ant-design-vue'
import {useRoute,useRouter} from 'vue-router'


export default function fixedMessageManage(){
  const cableType = ref([])
  const columns = ref([
    {
      title: '标题',
      dataIndex: 'title',
      align: 'center',
      key: 'title',
      ellipsis: true
    },
    {
      title: '用途',
      dataIndex: 'scope',
      align: 'center',
      key: 'scope',
      ellipsis: true,
      slots: { customRender: 'scope' }
    },
    {
      title: '字码类型',
      dataIndex: 'codeType',
      align: 'center',
      key: 'codeType',
      ellipsis: true,
      slots: { customRender: 'codeType' }
    },
    {
      title: '长短码',
      dataIndex: 'codeSort',
      align: 'center',
      key: 'codeSort',
      ellipsis: true,
      slots: { customRender: 'codeSort' }
    },
    {
      title: '所属类型',
      dataIndex: 'typeTitle',
      align: 'center',
      key: 'typeTitle'
    },
    {
      title: '备注',
      dataIndex: 'remark',
      align: 'center',
      key: 'remark'
    },
    {
      title: '报文页数',
      dataIndex: 'floorCount',
      align: 'center',
      key: 'floorCount'
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
  const listData = ref([])
  const showData = ref([])
  const currTablePage = ref(1)
  const addDrillModal = ref(false)
  const cableTypeTitle = ref(null)
  const cableTypeTitleID = ref()
  const route = useRoute()
  const router = useRouter()
  const useType = ref([{title:'收报',type:0,active:false},{title:'发报',type:1,active:false},{title:'收发报',type:2,active:false}])
  onMounted(()=>{
    init()
  })
  const init = ()=>{

    getAllMessage({scope:[1, 2, 0]})
    getCableType().then(res=>{
      cableType.value = res.data
      cableType.value.forEach(item=>item.active=false)
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
  const openModel = (v)=>{
    if(v){
      cableTypeTitleID.value = v.id
      cableTypeTitle.value = v.title
    }else {
      cableTypeTitleID.value = null
      cableTypeTitle.value = null
    }
    addDrillModal.value = true
  }
  const cancelModal = ()=>{
    addDrillModal.value = false
  }
  //新增修改类型
  const addCableType = ()=>{
    const data = {
      title:cableTypeTitle.value
    }
    if(cableTypeTitleID.value!==null){
      data.id = cableTypeTitleID.value
    }
    saveCableType(data).then(res=>{
      addDrillModal.value = false
      if(res.code === 200){
        init()
        if(cableTypeTitleID.value){
          message.success('修改类型成功!')
        }else {
          message.success('新增类型成功!')
        }
      }else {
        if(cableTypeTitleID.value){
          message.error('修改类型失败!')
        }else {
          message.error('新增类型失败!')
        }
      }
    })
  }
  //删除类型
  const deleteCableType = (v)=>{
    deleteCableTypeByID(v.id).then(res=>{
      if(res.code === 200){
        init()
        message.success('删除类型成功!')
      }else {
        message.success('删除类型失败!')
      }
    })
  }

  const findMessageDetails = (v)=>{
    router.push({
      path:route.matched[4].path+'/fixedMessageDetails',
      query:{id:v.id}
    })
  }

  const addMessage = ()=>{
    router.push({
      path:route.matched[4].path+'/fixedMessageDetails'
    })
  }
  //根据用途查询
  const selectType = (v,type)=>{
    v.active = !v.active
   if(type==='type'){
     cableType.value.forEach(item=>{
       if (item.id!==v.id){
         item.active = false
       }
     })
   }
    let code = []
    useType.value.forEach(item=>{
      if(item.active===true){
        code.push(item.type)
      }
    })
    if(code.length===0){
      code = [0,1,2]
    }
    const data = {
      scope:code
    }
    cableType.value.forEach(item=>{
      if (item.active){
        data.typeId = item.id
      }
    })

    getAllMessage(data)
  }
  const getAllMessage = (data)=>{
    getCableAll(data).then(res=>{
      res.data.forEach(d => {
        d.key = d.id
      })
      listData.value = res.data
      showData.value = res.data.filter((item, i) => i < currTablePage.value * 10)
      selectTablePage(1)
    })
  }
  const deleteHistory = (v)=>{
    deleteList(v).then(res=>{
      if(res.data===true){
        message.success('删除成功！')
        init()
      }else {
        message.error('删除失败!')
      }

    })
  }
  return{
    cableType,
    selectTablePage,
    showData,
    currTablePage,
    columns,useType,deleteHistory,
    listData,addDrillModal,cableTypeTitle,cableTypeTitleID,openModel,cancelModal,addCableType,deleteCableType,findMessageDetails,selectType,addMessage
  }
}