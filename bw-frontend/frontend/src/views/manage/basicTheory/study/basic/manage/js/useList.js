import {nextTick, onMounted, ref} from "vue";
import {getBasicTheory,listPageClassify,editClassify,removeClassify} from "../../../../../../../common/api/TheoryKnowledgeApi.js";
import {useRouter, useRoute} from 'vue-router'
import {message} from "ant-design-vue";
import {fontSizeDispose} from "../../../../../../../common/utils/Utils";
export default function () {
  const columns = ref([
    {
      title: '标题',
      dataIndex: 'title',
      align: "center",
      key: 'title',
      ellipsis: true,
    },
    {
      title: '课件数',
      dataIndex: 'swfs',
      align: "center",
      key: 'swfs',
      ellipsis: true,
      slots: {customRender: 'swfs'},
    },
    {
      title: '创建人',
      dataIndex: 'createUserName',
      align: "center",
      key: 'createUserName',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      align: "center",
      key: 'createTime',
      ellipsis: true,
      slots: {customRender: 'createTime'},
    },
    {
      title: '状态',
      dataIndex: 'status',
      align: "center",
      width: 120,
      key: 'status',
      slots: {customRender: 'status'},
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      align: "center",
      slots: {customRender: 'action'},
    },
  ]);
  const listData = ref([]);
  const tableData = ref([]);
  const tableList = ref([]);
  const currTablePage = ref(0);
  const tableLoading = ref(true);
  const router = useRouter()
  const route = useRoute()
  const searchList = ref({})
  const classifyName = ref('')
  const classifyID= ref('')
  const trainData = ref({
    type:0,
    mode:0
  });
  const addtype = ref(0)
  const addDrillModal = ref(false)
  const content = ref([]);
  onMounted(() => {
    init()
    initPageClassify()
    nextTick(() => {
      fontSizeDispose();
    })
  });
  const initPageClassify = ()=>{
    listPageClassify().then(res=>{
      searchList.value = res.data
    })
  }
  const init = ()=>{
    getBasicTheory({type:route.query.studyType}).then(res => {
      tableInit(res)
    })
  }
  const selectItem = (v)=>{
    if(v.active){
      v.active = false
    }else {
      v.active = true
    }
    const data = {type:route.query.studyType}
    const difficulty = []
    const specialty = []
    searchList.value.specialtyList.forEach(item=>{
      if(item.active){
        specialty.push(item.id)
      }
    })
    searchList.value.difficultyList.forEach(item=>{
      if(item.active){
        difficulty.push(item.id)
      }
    })
    if(difficulty.length>0){
      data.difficulty = JSON.stringify(difficulty)
    }
    if(specialty.length>0){
      data.specialty = JSON.stringify(specialty)
    }
    getBasicTheory(data).then(res => {
      tableInit(res)
    })
  }
  const tableInit = (res)=>{
    tableLoading.value = false;
    if (res.code === 200) {
      // tableData.value = res.data.filter(item => item.totalNumber>0);
      tableList.value = res.data
      tableData.value = res.data
      currTablePage.value = 0
      selectTablePage(1)
    } else {
      message.error(res.message);
    }
  }
  const selectType = ()=>{

  }
  const article = ref([])
  const getArticle = ()=>{
    getArticleList({type:0}).then(res=>{
      if (res.data && res.data.length > 0) {
        trainData.value.wordId = res.data[0].id
        article.value = res.data
      }
    })
  }
  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = (pag) => {
    if (pag === '-' && currTablePage.value === 1) return false;
    else if (pag === '+' && currTablePage.value === Math.ceil(tableData.value.length/10)) return false;
    else if (pag === currTablePage.value) return false;
    if (pag === '-') {
      currTablePage.value--;
    } else if (pag === '+') {
      currTablePage.value++;
    } else {
      currTablePage.value = pag;
    }
    tableList.value = [];
    tableList.value = tableData.value.filter((item,i) => i>=(currTablePage.value-1)*10&&i<currTablePage.value*10);
    // console.log(tableData.value.filter((item,i) => i>=(currTablePage.value-1)*10&&i<currTablePage.value*10))
  };
  const addClassify = ()=>{
    const data = {
      type:addtype.value,
      name:classifyName.value
    }
    if(classifyID.value!==null){
      data.id = classifyID.value
    }
    editClassify(data).then(res=>{
      addDrillModal.value = false
      classifyName.value = ""
      classifyID.value = null
      initPageClassify()
    })
  }
  const deleteClassify = (v)=>{
    removeClassify(v).then(res=>{
      initPageClassify()
    })
  }
  return {
    columns,tableData,tableList,currTablePage,selectTablePage,tableLoading,searchList,addDrillModal,addtype,addClassify,classifyName,classifyID,deleteClassify,selectItem
  }
}