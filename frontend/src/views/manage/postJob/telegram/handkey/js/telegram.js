import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {getAllPostTelegramTrain,deleteList} from "../../../../../../common/api/TelegramApi.js";

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
      dataIndex: 'isCable',
      key: 'isCable',
      // width: 120,
      align: "center",
      slots: {customRender: 'isCable'},
    },{
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      // width: 120,
      align: "center",
      slots: {customRender: 'type'},
    },{
      title: '组数',
      dataIndex: 'messageNumber',
      key: 'messageNumber',
      // width: 120,
      align: "center",
      slots: {customRender: 'messageNumber'},
    },{
      title: '用时',
      dataIndex: 'validTime',
      key: 'validTime',
      align: "center",
      // width: 130,
      slots: {customRender: 'validTime'},
    },{
      title: '码率',
      dataIndex: 'speed',
      key: 'speed',
      // width: 120,
      align: "center",
      slots: {customRender: 'speed'},
    },{
      title: '评分',
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
    getAllPostTelegramTrain().then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        tableData.value = [];
        tableList.value = [];
        res.data.map((item, i) => {
          item.ruleContent = JSON.parse(item.ruleContent);
          item.speed = item.status===2?(item.speed+(item.ruleContent.wpm.type?'WPM':'码/分')):'--';
          if (item.messageNumber>0) {
            tableData.value.push(item);
            if (i<currTablePage.value*10) {
              tableList.value.push(item);
            }
          }
        })
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







