import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {getAllReceivePostTrain,deleteList} from "../../../../../../common/api/ReceiveApi.js";

export default function telegramList() {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'name',
      key: 'name',
      align: "center",
      slots: {customRender: 'name'},
    },{
      title: '报文',
      key: 'isCable',
      // width: 120,
      align: "center",
      slots: {customRender: 'isCable'},
    },{
      title: '报文类型',
      dataIndex: 'type',
      key: 'type',
      // width: 120,
      align: "center",
      slots: {customRender: 'type'},
    },{
      title: '组数',
      dataIndex: 'totalNumber',
      key: 'totalNumber',
      // width: 120,
      align: "center",
      slots: {customRender: 'totalNumber'},
    },{
      title: '播报码率',
      dataIndex: 'rate',
      key: 'rate',
      // width: 120,
      align: "center",
      slots: {customRender: 'rate'},
    },{
      title: '用时',
      dataIndex: 'validTime',
      key: 'validTime',
      align: "center",
      // width: 130,
      slots: {customRender: 'validTime'},
    },{
      title: '评分',
      dataIndex: 'score',
      key: 'score',
      // width: 120,
      align: 'center',
      slots: {customRender: 'score'}
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
  const currTablePage = ref(1);
  const tableLoading = ref(true);

  onMounted(() => {
    selectTablePage(1)
  });
  const deleteHistory = (v)=>{
    deleteList(v.id).then(res=>{
      if(res.data===true){
        message.success('删除成功！')
        selectTablePage(1)
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
    if (pag < 1) return false;
    currTablePage.value = pag;
    getAllReceivePostTrain({
      page: pag,
      rows: 10
    }).then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        if(res.data.data.length>0){
          tableData.value = res.data;
        }else {
          currTablePage.value--
        }

      } else {
        message.error(res.message);
      }
    })
  };

  return {
    columns,tableData,currTablePage,selectTablePage,tableLoading,deleteHistory
  }
}







