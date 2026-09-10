import {message, Modal} from "ant-design-vue";
import {ref} from 'vue';
import {useRouter, useRoute} from "vue-router";
import moment from "moment";
import 'moment/dist/locale/zh-cn.js';
import {findAllTestPaper,deleteTestPaper} from "../../../../../../common/api/TestApi";
import {findAllTheoryKnowledgeQuestionLevel} from "../../../../../../common/api/TheoryQuestionBankApi";
import {listSort} from "../../../../../../components/test/nodeTree/listSort";
export default function knowledgeTabel() {
   const knowledgeShow = ref(false);
   const listData = ref([]);
   const difficulty = ref(undefined);
   const params=ref({type:'1',topic:'',options:[],answer:'',analysis:''});
   const typeCheckList = ref([{name: '简单', id: '1'}, {name: '普通', id: '2'}, {name: '困难', id: '3'}]);
   const takeNew = () =>{
      knowledgeShow.value=true;
   };
   const testPaper = () =>{
      findAllTestPaper().then(res => {
         if (res.code === 200) {
            listData.value=listSort(res.data)
         }
      })
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
  const deletePaper = (d)=>{
    deleteTestPaper({id:d.id}).then(res => {
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
      takeNoTestVisible,
      testPaper,
    deletePaper
   }
}