import {message, Modal} from "ant-design-vue";
import {ref} from 'vue';
import {useRouter, useRoute} from "vue-router";
import moment from "moment";
import 'moment/dist/locale/zh-cn.js';
import {
  deleteTheoryKnowledgeExam,
  findAllTheoryKnowledgeExam,
  findAllTheoryKnowledgeExamUser
} from "../../../../../../../common/api/TestApi";
import {findAllTheoryKnowledgeQuestionLevel} from "../../../../../../../common/api/TheoryQuestionBankApi";
import {PubSub} from "../../../../../../../common/utils/PubSub.js";
import {listSort} from "../../../../../../../components/test/nodeTree/listSort";
export default function knowledgeTabel() {
   const knowledgeShow = ref(false);
   const listData = ref([]);
   const difficulty = ref(undefined);
   const userRole = ref(JSON.parse(localStorage.getItem('userRole')));
   const params=ref({type:'1',topic:'',options:[],answer:'',analysis:''});
   const typeCheckList = ref([{name: '简单', id: '1'}, {name: '普通', id: '2'}, {name: '困难', id: '3'}]);
  const menuIcon=ref('icon-xiangshangshousuo1');
   const takeNew = () =>{
      knowledgeShow.value=true;
   };
   PubSub.subscribe('18002', (data) => {
      if (data.map){
         if (data.map.exam&&(data.map.exam.state==2||data.map.exam.state==3)){
            testPaper()
         }
      }
   });
   const testPaper = () =>{
     //学员
     if(userRole.value.id==2){
       findAllTheoryKnowledgeExamUser({state:true}).then(res => {
         if (res.code === 200) {
            listData.value=listSort(res.data.exam)
           listData.value = listData.value.filter(item=>item.userState!=3)
         }
       })
     }else {
       //教员
       findAllTheoryKnowledgeExam({state:true}).then(res => {
         if (res.code === 200) {
           listData.value=listSort(res.data)
         }
       })
     }
   };
   const takeNoTestVisible = ()=>{
      Modal.confirm({
         title: () => '您确定退出么?',
         content: () => '暂未保存!',
         // icon: () => createVNode(ExclamationCircleOutlined),
         okType: 'danger',
         okText: () => '确定',
         cancelText: () => '取消',
         onOk() {
            knowledgeShow.value=false;
         }
      })
   };
  const deleteTest = (d)=>{
    deleteTheoryKnowledgeExam({examId:d.id}).then(res => {
      if(res.code === 200){
        testPaper()
        message.success('删除成功！')
      }else {
        message.error(res.message)
      }
    })
  }
   return {
      typeCheckList,
      difficulty,
      listData,
      takeNew,
      knowledgeShow,
      params,
      menuIcon,
     userRole,
      takeNoTestVisible,
      testPaper,
     deleteTest
   }
}