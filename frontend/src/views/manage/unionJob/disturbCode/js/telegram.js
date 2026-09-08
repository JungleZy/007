import {message, Modal} from "ant-design-vue";
import {ref, onMounted, createVNode} from 'vue';
import {getDisturbCodeAllTrain,deleteTrain} from "../../../../../common/api/UnionApi.js";
import {ExclamationCircleOutlined} from "@ant-design/icons-vue";

export default function telegramList() {
  const columns = ref([
    {
      title: '训练房名称',
      dataIndex: 'name',
      key: 'name',
      align: "center",
      slots: {customRender: 'name'},
    },{
      title: '报底类型',
      dataIndex: 'bdType',
      key: 'bdType',
      align: "center",
      slots: {customRender: 'bdType'},
    },{
      title: '报文类型',
      dataIndex: 'bwType',
      key: 'bwType',
      align: "center",
      slots: {customRender: 'bwType'},
    },{
      title: '报文组数',
      dataIndex: 'bwCount',
      key: 'bwCount',
      align: "center",
      slots: {customRender: 'bwCount'},
    },{
      title: '创建人',
      dataIndex: 'user',
      key: 'user',
      width: 160,
      align: "center",
      slots: {customRender: 'user'},
    }, {
      title: '状态',
      dataIndex: 'stats',
      key: 'stats',
      width: 120,
      align: "center",
      slots: {customRender: 'stats'},
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
  const cacheData = ref([]);
  const totalPage = ref(1);
  const currPage = ref(1);
  const tableLoading = ref(true);

  onMounted(() => {
    getDisturbTrainList()
  });


  /**
   * 获取抗干扰训练列表数据
   */
  const getDisturbTrainList = () => {
    getDisturbCodeAllTrain().then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        cacheData.value = res.data;
        tableData.value = res.data.filter((item,i) => i < 10);
        totalPage.value = Math.ceil(cacheData.value.length/10);
      }
    })
  };

  /**
   * 切换分页查询数据
   * @param pag
   */
  const changeListPage = (pag) => {
    if (pag < 1) return false;
    currPage.value = pag;
    tableData.value = cacheData.value.filter((item,i) => (i>=(pag-1)*10&&i<pag*10));
  }
  const deleteModel = (v)=>{
    Modal.confirm({
      class: 'init_modal_style',
      content: '是否删除该训练？',
      icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      maskClosable: true,
      onOk: () => {
        deleteHistory(v)
      }
    })
  }
  const deleteHistory = (v)=>{
    deleteTrain({roomId:v.id}).then(res=>{
      if(res.code === 200) {
        message.success('删除成功！')
        getDisturbTrainList()
      }else {
        message.error('删除失败！')
      }
    })
  }

  return {
    columns,tableData,totalPage,currPage,tableLoading,changeListPage,deleteModel
  }
}







