import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {groupNetTrainListPage} from "../../../../common/api/TrainingDetails.js";
import {useRoute, useRouter} from "vue-router";

export default function telegramList() {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'createTime',
      key: 'createTime',
      align: "center",
      slots: {customRender: 'createTime'},
    },{
      title: '设备分类',
      dataIndex: 'deviceTypeName',
      key: 'deviceTypeName',
      align: "center",
      slots: {customRender: 'deviceTypeName'},
    },{
      title: '设备名称',
      dataIndex: 'deviceName',
      key: 'deviceName',
      align: "center",
      slots: {customRender: 'deviceName'},
    },{
      title: '分数',
      dataIndex: 'score',
      key: 'score',
      align: "center",
      width: 220,
      slots: {customRender: 'score'},
    },{
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      align: "center",
      width: 120,
      slots: {customRender: 'action'},
    }
  ]);
  const router = useRouter()
  const route = useRoute()
  const tableData = ref([]);
  const currTablePage = ref(0);
  const tableLoading = ref(true);
  watch(route,()=>{
    selectTablePage(1)
  })
  onMounted(() => {
    selectTablePage(1)
  });


  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = (pag) => {
    if (pag === '-' && currTablePage.value === 1) return false;
    else if (pag === '+' && currTablePage.value === Math.ceil(tableData.value.totalNumber/10)) return false;
    if (pag === '-') {
      currTablePage.value--;
    } else if (pag === '+') {
      currTablePage.value++;
    } else {
      currTablePage.value = pag;
    }
    groupNetTrainListPage({
      page: currTablePage.value,
      rows: 10,
    }).then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        tableData.value = res.data;
      } else {
        message.error(res.message);
      }
    })
  };

  return {
    columns,tableData,currTablePage,selectTablePage,tableLoading
  }
}







