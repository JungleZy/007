import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
export default function useStudentQues(params,index){
  const typeCheckList=ref([{name:'单选',id:'1'},{name:'多选',id:'2'},{name:'判断',id:'3'},{name:'填空',id:'4'}])
  //,{name:'简答',id:'5'}  //暂时不加入选择
  // const Type=ref(1)
  const Earray=["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"];

  const question=ref({type:'1',topic:'',options:[],answer:'',analysis:''});
  if(params!=undefined){
    question.value=params.value
  }
  return{
    typeCheckList,
    question,
    Earray
  }
}