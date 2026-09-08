import {ref} from 'vue';
import {deepClone} from "../../../../../../common/utils/Utils.js";
import {findAllQuestionByLevelId} from "../../../../../../common/api/TheoryQuestionBankApi";
import {message} from "ant-design-vue";

export default function addAndSelectTopic(paperData, findAllQuestion) {
   const selectDrillModal = ref(false);
   const randomValue = ref(false);
   const topicType = ref('');
   const activeList = ref([]);
   const active = ref(null);
   const randomLoading = ref(true);
   const randomBankList = ref([{
      name: '一、单选题',
      key: 'singleChoice',
      num: '1',
      activeBank: '0'
   }, {
      name: '二、多选题',
      key: 'multipleChoice',
      num: '2',
      activeBank: '0'
   }, {
      name: '三、判断题',
      key: 'judge',
      num: '3',
      activeBank: '0'
   }, {
      name: '四、填空题',
      key: 'completion',
      num: '4',
      activeBank: '0'
   } ,{
     name: '五、简答题',
     key: 'shortAnswer',
     num: '5',
     activeBank: '0'
   }]);
   const selectTopic = (e) => {
      selectDrillModal.value = !selectDrillModal.value;
      randomValue.value = false;
      topicType.value = e.key + '';
      activeList.value = [];
      if (e.key == '1') {
         paperData.value.singleChoice.forEach(ss => {
            activeList.value.push(ss.id)
         })
      } else if (e.key == '2') {
         paperData.value.multipleChoice.forEach(ss => {
            activeList.value.push(ss.id)
         })
      } else if (e.key == '3') {
         paperData.value.judge.forEach(ss => {
            activeList.value.push(ss.id)
         })
      } else if (e.key == '4') {
         paperData.value.completion.forEach(ss => {
            activeList.value.push(ss.id)
         })
      }
   };
   const randomlySelected = (e) => {
      selectDrillModal.value = !selectDrillModal.value;
      randomValue.value = true;
   };
   const selectNode = (e) => {
      findAllQuestionByLevelId({
         levelId: e,
         type: active.value.num,
      }).then(res => {
         if (res.code === 200) {
            randomBankList.value[Number(active.value.num) - 1].list = [];
            if (res.data.length === 0) {
               message.error('该节点下暂无考题');
               return;
            }
            randomBankList.value[Number(active.value.num) - 1].list = res.data;
         }
      })
   };

   const randomActive = (e) => {
      active.value = e
   };
   const randomAdd = () => {
      if (!randomLoading.value) return;
      randomLoading.value=false;
      setTimeout(() => {
        if (!randomBankList.value[Number(active.value.num) - 1].list) {
          message.error('请选择考题节点');
          randomLoading.value=true;
          return;
        }
        if (randomBankList.value[Number(active.value.num) - 1].list.length === 0) {
          message.error('请选择有考题的节点');
          randomLoading.value=true;
          return;
        }
         if (!active.value.activeBank || active.value.activeBank == '0') {
            message.error('请填写随机生成题目的数量');
            randomLoading.value=true;
            return;
         }
         let arr = [];
         arr = randomDG(arr, Number(active.value.activeBank), randomBankList.value[Number(active.value.num) - 1].list.length);
         for (let j of arr) {
            let obj = deepClone(randomBankList.value[Number(active.value.num) - 1].list[j]);
            obj.options = JSON.parse(obj.options);
            obj.answer = JSON.parse(obj.answer);
            obj.score = 1;
            if (paperData.value[active.value.key].length<randomBankList.value[Number(active.value.num) - 1].list.length) {
               paperData.value[active.value.key].push(obj);
               findAllQuestion()
            }else {
               message.error('最多只能选'+randomBankList.value[Number(active.value.num) - 1].list.length+'道题');
               randomLoading.value=true;
               return;
            }
         }
        setTimeout(()=>{
           randomLoading.value=true;
        },1000)
      }, 200)
   };
   const randomUpdate = (e) => {
      if (!randomLoading.value) return;
      randomLoading.value=false;
      setTimeout(() => {
         if (!active.value.activeBank || active.value.activeBank == '0') {
            message.error('请填写随机生成题目的数量');
            randomLoading.value=true;
            return;
         }
         if (!randomBankList.value[Number(active.value.num) - 1].list) {
            message.error('请选择考题节点');
            randomLoading.value=true;
            return;
         }
         if (randomBankList.value[Number(active.value.num) - 1].list.length === 0) {
            message.error('请选择有考题的节点');
            randomLoading.value=true;
            return;
         }
         if (paperData.value[active.value.key].length === 0) {
            message.error('请添加考题');
            randomLoading.value=true;
            return;
         }
         let num=paperData.value[active.value.key].length;
         paperData.value[active.value.key] =[];
         let arr = [];
         arr = randomDG(arr,num, randomBankList.value[Number(active.value.num) - 1].list.length);
         for (let j of arr) {
            let obj = deepClone(randomBankList.value[Number(active.value.num) - 1].list[j]);
            obj.options = JSON.parse(obj.options);
            obj.answer = JSON.parse(obj.answer);
            obj.score = 1;
            paperData.value[active.value.key].push(obj);
            findAllQuestion()
         }
         setTimeout(()=>{
            randomLoading.value=true;
         },1000)
      }, 200)
   };
   const randomEmpty = () => {
      if (!randomLoading.value) return;
      randomLoading.value=false;
      setTimeout(() => {
         paperData.value[active.value.key] =[];
         findAllQuestion();
         randomLoading.value=true;
      }, 200)
   };
   const randomDG = (arr, e, f) => {
      let rod = Math.floor((Math.random() * f));
      if (arr.indexOf(rod) == -1) {
         arr.push(rod);
      }
      if (arr.length < e) {
         return randomDG(arr, e, f)
      } else {
         return arr
      }
   };
   const deletePaper = (e) => {
      setTimeout(()=>{
         paperData.value[[active.value.key]] = paperData.value[[active.value.key]].filter(ss => {
            return ss.id !== e.id;
         });
         findAllQuestion()
      },200)
   };
   const inputNumber = (e,val) => {
      for (let j of paperData.value[e]) {
         j.score = val;
      }
   };
   return {
      selectDrillModal,
      topicType,
      activeList,
      randomValue,
      randomBankList,
      randomLoading,
      selectTopic,
      randomlySelected,
      selectNode,
      randomActive,
      randomAdd,
      randomUpdate,
      randomEmpty,
      deletePaper,
      inputNumber
   }
}