import {message} from "ant-design-vue";
import moment from 'moment'
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {findPostExamTrainList, addPostExamTrainList, deleteExamList} from "../../../../../../common/api/TelegramApi.js";
import {getGradingRuleListByType} from "../../../../../../common/api/GradingRuleApi.js";
import {numberKey} from '../../../../../../components/preJob/telexTrain/js/enum.js'
import {useRouter, useRoute} from 'vue-router'
import {getCableAll} from "../../../../../../common/api/CableApi.js";

export default function telegramList(addDrillModal) {
  const columns = ref([
    {
      title: '训练名称',
      dataIndex: 'title',
      key: 'title',
      align: "center",
      slots: {customRender: 'title'},
    }, {
      title: '报文',
      dataIndex: 'isCable',
      key: 'isCable',
      // width: 120,
      align: "center",
      slots: {customRender: 'isCable'},
    }, {
      title: '类型',
      dataIndex: 'messageType',
      key: 'messageType',
      align: "center",
      slots: {customRender: 'messageType'},
    }, {
      title: '组数',
      dataIndex: 'totalNumber',
      key: 'totalNumber',
      // width: 120,
      align: "center",
      slots: {customRender: 'totalNumber'},
    }, {
      title: '用时',
      dataIndex: 'duration',
      key: 'duration',
      align: "center",
      // width: 130,
      slots: {customRender: 'duration'},
    }, {
      title: '码率',
      dataIndex: 'speed',
      key: 'speed',
      // width: 120,
      align: "center",
      slots: {customRender: 'speed'},
    },
    {
      title: '评分',
      dataIndex: 'score',
      key: 'score',
      // width: 120,
      align: 'center',
      slots: {customRender: 'score'}
    }, {
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
  const currTablePage = ref(0);
  const tableLoading = ref(true);
  const router = useRouter();
  const route = useRoute();
  const trainData = ref({
    title: "电子键拍发-" + moment().format('YYMMDDhhmmss'),
    isCable: 0,
    cableId: null,
    startPage: 1,
    type: 0,
    count: 100,
    mode: 0
  });
  const selectCable = ref(null)
  const cableList = ref([])
  const content = ref([]);
  const ruleId = ref('');
  const ruleList = ref([]);
  provide("selectCable", selectCable)
  provide("formData", trainData)
  provide("cableList", cableList)
  onMounted(() => {
    init()
  });

  /**
   * 初始化获取列表
   */
  const init = () => {
    findPostExamTrainList().then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        res.data.map(item => {
          item.speed = item.status !== 2 ? '--'
            : item.speed + (item.protocolVersion === 1 ? '四码组/分' : '码/分（历史）')
        });
        tableList.value = res.data;
        tableData.value = res.data;
        selectTablePage(1)
      } else {
        message.error(res.message);
      }
    })
  };
  const deleteHistory = (v) => {
    deleteExamList(v.id).then(res => {
      if (res.data === true) {
        message.success('删除成功！')
        init()
      } else {
        message.error('删除失败!')
      }

    })
  }
  /**
   * 获取评分规则列表
   */
  const getGradingRuleList = () => {
    getGradingRuleListByType({type: 3}).then(res => {
      if (res.code === 200) {
        ruleList.value = res.data;
        res.data.forEach(item=>{
          if(item.isDefault===0){
            ruleId.value = item.id
          }
        })
      } else {
        message.error(res.message);
      }
    })
  };

  /**
   * 列表分页切换
   * @param pag
   */
  const selectTablePage = (pag) => {
    if (pag === '-' && currTablePage.value === 1) return false;
    else if (pag === '+' && currTablePage.value === Math.ceil(tableData.value.length / 10)) return false;
    else if (pag === currTablePage.value) return false;

    if (pag === '-') {
      currTablePage.value--;
    } else if (pag === '+') {
      currTablePage.value++;
    } else {
      currTablePage.value = pag;
    }
    tableList.value = [];
    tableList.value = tableData.value.filter((item, i) => i >= (currTablePage.value - 1) * 10 && i < currTablePage.value * 10);
  };

  /**
   * 生成随机报文
   */
  // const initContent = ()=>{
  //   let ctAll = [],mat = 0;
  //   content.value = [];
  //   for (let i=0;i<trainData.value.count;i++){
  //     let ct = [];
  //     for (let j=0;j<4;j++){
  //       mat = Math.floor(Math.random()*10)+'';
  //       ct.push(mat);
  //     }
  //     ctAll.push({key:ct, value:[],time: []});
  //     if (ctAll.length >= 100 || i === trainData.value.count-1) {
  //       content.value.push(ctAll);
  //       ctAll = [];
  //     }
  //   }
  // };

  /**
   * 新增训练
   */
  const addTelexTrain = () => {
    if (trainData.value.count === '' || trainData.value.count <= 0) {
      message.error('报文组数不能为空！');
      return false;
    }
    addDrillModal.value = false;
    // initContent(numberKey);
    addPostExamTrainList({
      content: '',
      title: trainData.value.title,
      isCable: trainData.value.isCable,
      cableId: trainData.value.cableId,
      startPage: trainData.value.startPage,
      totalNumber: trainData.value.count,
      ruleId: ruleId.value,
      messageType: trainData.value.type
    }).then(res => {
      if (res.code === 200) {
        message.success("生成训练成功！");
        router.push({
          path: route.matched[4].path + "/examPostJobTrain",
          query: {id: res.data.id}
        })
      } else {
        message.error("生成训练失败！")
      }
    })
  };
  const selectIsCable = () => {
    if (trainData.value.isCable === 1) {
      getCableAll({scope: [1, 2]}).then(res => {
        cableList.value = res.data
        trainData.value.cableId = cableList.value[0].id
        trainData.value.type = cableList.value[0].codeType
        selectCable.value = cableList.value[0]
      })
    } else {
      trainData.value.cableId = null
      trainData.value.type = 0
      selectCable.value = null
    }
  }
  return {
    columns,
    tableData,
    tableList,
    currTablePage,
    selectTablePage,
    tableLoading,
    trainData,
    addTelexTrain,
    getGradingRuleList,
    selectIsCable,
    ruleId,
    ruleList,
    selectCable,
    cableList,
    deleteHistory
  }
}







