import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {getPostMilitaryTrainList,deleteList} from "../../../../../../common/api/MilitaryTermApi.js";

export default function termList() {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: "center",
      slots: {customRender: 'name'},
    },{
      title: '军语类型',
      dataIndex: 'types',
      key: 'types',
      align: "center",
      slots: {customRender: 'types'},
    },{
      title: '数量',
      dataIndex: 'totalNumber',
      key: 'totalNumber',
      // width: 120,
      align: "center",
      slots: {customRender: 'totalNumber'},
    },{
      title: '用时',
      dataIndex: 'duration',
      key: 'duration',
      // width: 120,
      align: "center",
      slots: {customRender: 'duration'},
    },{
      title: '成绩',
      dataIndex: 'score',
      key: 'score',
      // width: 120,
      align: "center",
      slots: {customRender: 'score'},
    },{
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      align: "center",
      slots: {customRender: 'status'},
    }, {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: "center",
      width: 120,
      slots: {customRender: 'action'},
    }
  ]);
  const tableData = ref([]);
  const tableList = ref([]);
  const currTablePage = ref(1);
  const tableLoading = ref(true);

  onMounted(() => {
    init()
  });
  const init = ()=>{
    getPostMilitaryTrainList().then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        tableData.value = res.data;
        tableList.value = res.data.filter((item,i) => i<currTablePage.value*10);
      } else {
        message.error(res.message);
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteList(v.id).then(res=>{
      if(res.data===true){
        currTablePage.value = 1
        message.success('删除成功！')
        init()
      }else {
        message.error('删除失败!')
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
  };

  return {
    columns,tableData,tableList,currTablePage,selectTablePage,tableLoading,deleteHistory
  }
}







