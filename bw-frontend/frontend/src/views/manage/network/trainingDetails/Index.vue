<template>
    <div class="h-full w-full " style="position: relative">
        <UnityEquipment :unityPath="unityPath" @addParam="paramsChild"  @isShow="isShow" ></UnityEquipment>

        <div class="popUp" v-if="isParam">
            <div class="w-full layout-center" style="height: 40px">得分：{{score}}</div>
            <div class="w-full overflow-auto" style="height: calc(100% - 40px)">
                <table style="display: flow-root">
                    <tr v-for="(item, index) of tableDocData" :key="index">
                        <td
                            class="tableTd"
                            v-for="(v, num) of item"
                            :key="num"
                            :colspan="v.colspan"
                            :rowspan="v.rowspan"
                            style="min-width: 60px"
                            :title="v.isParameter? '实际值：'+v.value:''"
                            :style="{color:v.isParameter?parseFloat(v.params)==parseFloat(v.value)?'':v.params?'red':'':'', height: v.height + 'px', width: v.width + 'px' }">
                            <span >{{(v.isParameter && v.params)?v.params:v.value}}</span>
                        </td>
                    </tr>
                </table>
            </div>
        </div>
        <div  v-if="isParam" class="submitGrades" @click="submitGrades"></div>
    </div>
</template>
<script>
    export default {
        name: "EquipmentUnity"
    }
</script>
<script setup>
    import UnityEquipment from '../../../../components/unityEquipment/UnityEquipment.vue'
    import {deviceScoringRuleFindAllByDeviceId, groupNetTrain,groupNetTrainSubmitAnswer} from "../../../../common/api/TrainingDetails";
    import table from '../contactDocuments/table'
    import {analyzeChannel,netIP} from "../contactDocuments/J210_742";
    import {ref,onMounted} from 'vue'
    import {useRoute, useRouter} from "vue-router";
    const unityPath=ref('')
    const route = useRoute();
    const router = useRouter();
    const {J210_742}=table();
    const tableDocData=ref([]);
    const answer=ref('');
    const rangeList=ref([]);
    const score=ref(0);
    const isParam=ref(false);
    onMounted(() => {
        if (route.query.deviceType && route.query.deviceType !== '') {
            unityPath.value = route.query.deviceType
        }
        groupNetTrainDetails(route.query.Id,route.query.range)
    })
    //获取考核信息
    const groupNetTrainDetails=(e,range)=>{
        tableDocData.value=J210_742
        groupNetTrain({id:e}).then(res=>{
            let train=res.data;
            for (let i of JSON.parse(train.topic)){
                repeatAnswer(i)
            }
        })
        deviceScoringRuleFindAllByDeviceId({deviceId:range}).then(res=>{
            rangeList.value=JSON.parse(res.data.ruleContent)
        })
    }
    //处理模板和考核信息
    const repeatAnswer=(i)=>{
        console.log(i.value);
        tableDocData.value.forEach((item,index)=>{
            item.forEach((v,ind)=>{
                if (i.xy[0]==index&&i.xy[1]===ind){
                    item[ind]=i.value;
                }
            })
        })
    }
    //设备子组件传得参数
   const paramsChild=(e)=>{
       answer.value=JSON.stringify(e)
       console.log(e);
       tableDocData.value=analyzeChannel(tableDocData.value,e.chananel1)
       tableDocData.value=netIP(tableDocData.value,e.serialNumbers)
       score.value=0;
       tableDocData.value.forEach(es=>{
          es.forEach(item=>{
              if (item.isParameter){
                  rangeList.value.forEach(val=>{
                      if (val.paramName==item.isParameter){
                          if (parseFloat(item.value)==parseFloat(item.params)){
                              score.value=score.value+val.weight
                          }
                      }
                  })
              }
          })
       })
   }
   //
    const isShow=()=>{
        isParam.value=true;
    }
   //提交答案退出
   const submitGrades=()=>{
        let obj={
            id:route.query.Id,
            answer:answer.value,
            score: score.value
        }
       groupNetTrainSubmitAnswer(obj).then(res=>{
           router.push({
               path: route.matched[2].path,
           })
       })
   }
</script>

<style scoped>
    .submitGrades{
        position: absolute;
        top: 0;
        right: 0;
        width: 110px;
        height: 56px;
        background: url("../../../../assets/HJ/home/submit.png")no-repeat 0 0/100% 100%;
        cursor: pointer;
    }
    .popUp{
        position: absolute;
        top: 0;
        left: 0;
        height: 800px;
        width: 1280px;
        overflow: auto;
        transform: scale(0.1);
        transform-origin: 0 0;
        transition: all 1s;
        padding: 10px 40px 40px 10px;
        background: #071633;
    }
    .popUp:hover{
        transform: scale(1);
    }
    .tableTd {
        border: 1px solid #33466D;
        text-align: center;
        color: #bfcde0;
        font-size: 14px;
    }
</style>