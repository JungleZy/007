
import {message, Modal} from "ant-design-vue";
import moment from 'moment'
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {listPage,getEquipmentAll,generalGroupNetRule,generalGroupNetRuleFindAll,generalGroupNetRuleDeleteById} from "../../../../../common/api/equipment.js";
import table from '../../trainScore/js/table'


export default function equipmentList() {
  const columns = ref([
   {
      title: '训练名称',
      dataIndex: 'trainName',
      key: 'trainName',
      align: "center",
    },
    {
      title: '设备名称',
      dataIndex: 'deviceName',
      key: 'deviceName',
      align: "center",
    },{
      title: '设备id',
      dataIndex: 'deviceId',
      key: 'deviceId',
      // width: 120,
      align: "center",
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
  const equipmentLists=ref([]);
  const liaisonDoc=ref(null);
  const tableDocData=ref([]);
  const tableDocData2=ref([]);
  const titleHeaders=ref(null);
  const activeEquipment=ref(0);
  const activeDoc=ref(0);
  const addDrillModal=ref(false);
  const scoreList=ref([]);
  const docID=ref('');
  const gradingRuleModal = ref(false)
  const addAndUpdate=ref({
    au:0,
    score:'',
    x:'',
    y:'',
  });
  const allScore=ref(0);
  const {dpData_220,tpData_220,dpData_171,tpData_171,zsyData,dpData_173,tpData_173,llwjData,llwjData2} = table();
  onMounted(() => {
    init();
  });
  //初始化页面
  const init = ()=>{
    listPage().then(res => {
      tableLoading.value = false;
      if (res.code === 200) {
        // tableData.value = res.data.filter(item => item.totalNumber>0);
        tableList.value = res.data
        tableData.value = res.data
        selectTablePage(1)
      } else {
        message.error(res.message);
      }
    });
    getEquipmentAll().then(res=>{
      if (res.code===200){
        equipmentLists.value=res.data;
        equipmentLists.value.forEach(e=>{
          switch (e.name) {
            case '400W':
              e.children=[{name:'联络文件',code:'400W01',data:llwjData,data2:llwjData2}];
              break;
            case '125W':
              e.children=[{name:'联络文件',code:'125W01',data:llwjData,data2:llwjData2}];
              break;
            case '173':
              e.children=[{name:'定频联络文件',code:'17301',data:dpData_173},{name:'跳频联络文件',code:'17302',data:tpData_173}];
              break;
            case '134A':
              e.children=[{name:'定频联络文件',code:'134A01',data:dpData_220},{name:'跳频联络文件',code:'134A02',data:tpData_220},{name:'自适应联络文件',code:'134A03',data:zsyData}];
              break;
            case '171':
              e.children=[{name:'联络文件',code:'17101',data:dpData_171}];
              break;
            case '121C':
              e.children=[{name:'定频联络文件',code:'121C01',data:dpData_171},{name:'跳频联络文件',code:'121C02',data:tpData_171}];
              break;
          }
        })
        selectDev(0)
      }
    })
  }
  //查询所有评分规则
  const queryDoc=()=>{
    docID.value="";
    scoreList.value=[];
    generalGroupNetRuleFindAll().then(res=>{
      if (res.code === 200) {
        let list=res.data;
        list.forEach(e=>{
          if (e.code===liaisonDoc.value.children[activeDoc.value].code) {
            scoreList.value=JSON.parse(e.xyScore);
            docID.value=e.id
          }
        })
        let score=0;
        scoreList.value.forEach(e=>{
          score=score+Number(e.score);
        });
        allScore.value=score
      } else {
        message.error(res.message);
      }
    })
  }
  //评分规则选择设备
  const selectDev = (e)=>{
    activeEquipment.value=e;
    liaisonDoc.value=equipmentLists.value[e];
    selectDoc(0)
  }
  //评分规则选择联络文件
  const selectDoc = (e)=>{
    activeDoc.value=e;
    switch (liaisonDoc.value.name) {
      case '400W':
        titleHeaders.value=['单台地址','网络参数号','本台地址','网络地址',
          '信道','跳频\n频率表','网号','参数号','频率表号','密钥号','中心频率','A','B'];
        break;
      case '125W':
        titleHeaders.value=['单台地址','网络参数号','本台地址','网络地址',
          '信道','跳频\n频率表','网号','参数号','频率表号','密钥号','中心频率','A','B'];
        break;
      case '173':
          if (liaisonDoc.value.children[e].name==='定频联络文件'){
            titleHeaders.value=['网络模式','工作模式','频率(MHz)','传输密钥','工作密钥']
          } else if (liaisonDoc.value.children[e].name==='跳频联络文件'){
            titleHeaders.value=['网络模式','工作模式','频率表','网号','传输密钥',"信息密钥"]
          }
        break;
      case '134A':
        if (liaisonDoc.value.children[e].name==='定频联络文件'){
          titleHeaders.value=['收频(MHz)','发频(MHz)','工作方式']
        } else if (liaisonDoc.value.children[e].name==='跳频联络文件'){
          titleHeaders.value=['网号','参数号','表号','中心频率','密钥号',"密钥"]
        }else if (liaisonDoc.value.children[e].name==='自适应联络文件'){
          // titleHeaders.value=['单台地址','单台信道','信道','常频','分组',"信道号","网号","网信道","网成员"]
          titleHeaders.value=['单台信道','常频',"信道号","网信道","网成员"]
        }
        break;
      case '171':
        titleHeaders.value=['网络模式','工作模式','频率（MHz）','传输密钥','信息密钥','接入方式']
        break;
      case '121C':
        if (liaisonDoc.value.children[e].name==='定频联络文件'){
          titleHeaders.value=['网络模式','工作模式','频率（MHz）']
        } else if (liaisonDoc.value.children[e].name==='跳频联络文件'){
          titleHeaders.value= ['网络模式','工作模式','频率表','网号',"密钥"]
        }
        break;
    }
    tableDocData.value=liaisonDoc.value.children[e].data;
    if (liaisonDoc.value.children[e].data2) {
      tableDocData2.value=liaisonDoc.value.children[e].data2;
    };
    queryDoc();
  };
  //判断联络文件是否该有高亮头部
  const isTitle = (value)=>{
    if (titleHeaders.value.some(items=>items==value)){
      return true
    }else {
      return false
    }
  }
  //点击高亮头部
  const cliceTd = (e,i,value)=>{
    if (isTitle(value)){
      addDrillModal.value=true;
      addAndUpdate.value.score='';
      addAndUpdate.value.x=e;
      addAndUpdate.value.y=i;
      addAndUpdate.value.value=newName(e, i, value);
      scoreList.value.forEach(es=>{
        if (es.value===addAndUpdate.value.value) {
          addAndUpdate.value.score=es.score;
        }
      });
    }else {
      message.error('点击高亮区域设置分数');

    }
  };
  //添加分数
  const addScore = (e)=>{
    if(addAndUpdate.value.score==null){
      message.error("请检查设置的分数！")
      return
    }
    addDrillModal.value=false;
    if (scoreList.value.length===0){
      scoreList.value.push({
        x:addAndUpdate.value.x,
        y:addAndUpdate.value.y,
        value:addAndUpdate.value.value,
        score:JSON.parse(JSON.stringify(addAndUpdate.value.score)),
      })
    } else {
      let item=0;
      scoreList.value.forEach(e=>{
        if (e.x===addAndUpdate.value.x&&e.y===addAndUpdate.value.y) {
          e.score=JSON.parse(JSON.stringify(addAndUpdate.value.score));
          e.value=JSON.parse(JSON.stringify(addAndUpdate.value.value));
        }else {
          item++
        }
      });
      if (item===scoreList.value.length){
        scoreList.value.push({
          x:addAndUpdate.value.x,
          y:addAndUpdate.value.y,
          value:addAndUpdate.value.value,
          score:JSON.parse(JSON.stringify(addAndUpdate.value.score)),
        })
      }
    }
    let score=0;
    scoreList.value.forEach(e=>{
      score=score+Number(e.score);
    });
    allScore.value=score;
    message.success('添加成功');
  };
  //保存基础练习配置
  const saveDeploy = () => {
    let score={
      id:docID.value,
      code:liaisonDoc.value.children[activeDoc.value].code,
      device:liaisonDoc.value.name,
      xyScore:JSON.stringify(scoreList.value)
    };
    generalGroupNetRule(score).then(res => {
      if (res.code === 200) {
        message.success('添加成功！');
        gradingRuleModal.value = false
      } else {
        message.error(res.message)
      }
    })
  };
  //删除当前练习配置
  const deleteById = () => {
    Modal.confirm({
      title: () => '您确定删除当前联络文件的评分规则嘛?',
      // icon: () => createVNode(ExclamationCircleOutlined),
      okType: 'danger',
      okText: () => '确定',
      cancelText: () => '取消',
      onOk() {
        let score={
          id:docID.value,
        };
        generalGroupNetRuleDeleteById(score).then(res => {
          if (res.code === 200) {
            message.success('删除成功，请重新配置');
            queryDoc();
          } else {
            message.error(res.message)
          }
        })
      }
    })


  };
  //单独修改部分头部参数名
  const newName=(x,y,value)=>{
    let name=value;
    if (x==10&&y==0){
      name='呼叫信道表号'
    } else if (x==10&&y==1){
      name='呼叫信道信道'
    }else if (x==10&&y==2){
      name='业务信道表号'
    }else if (x==10&&y==3){
      name='业务信道信道'
    }else if (x==10&&y==4){
      name='低速报信道表号'
    }else if (x==10&&y==5){
      name='低速报信道信道'
    }else if (x==2&&y==0){
      name='发频率'
    }else if (x==2&&y==1){
      name='收频率'
    }else if (x==2&&y==2){
      name='发频率'
    }else if (x==2&&y==3){
      name='收频率'
    }else if (x==2&&y==4){
      name='发频率'
    }else if (x==2&&y==5){
      name='收频率'
    }
    return name;
  }
  //列表分页切换
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
    columns,tableData,tableList,currTablePage,selectTablePage,tableLoading,equipmentLists,liaisonDoc,tableDocData,tableDocData2,activeEquipment,activeDoc,addDrillModal,addAndUpdate,gradingRuleModal,allScore,saveDeploy,addScore,selectDev,selectDoc,cliceTd,isTitle,deleteById
  }
}







