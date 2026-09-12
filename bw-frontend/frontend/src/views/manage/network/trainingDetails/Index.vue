<template>
    <div class="h-full w-full " style="position: relative">
        <UnityEquipment :unityPath="unityPath" @addParam="paramsChild"  @isShow="isShow" ></UnityEquipment>

        <div class="popUp" v-if="isParam">
            <div class="w-full layout-center" style="height: 40px">得分：{{score ?? '待提交后评分'}}</div>
            <div v-if="submissionAnswer !== null && score === null">提交快照已固定；重试使用同一份答案，不采纳后续设备变化。</div>
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
                            :style="{color:v.correct === false ? 'red' : '', height: v.height + 'px', width: v.width + 'px' }">
                            <span>{{v.isParameter && v.isParameter !== '序号' ? (v.params ?? '未作答') : v.value}}</span>
                        </td>
                    </tr>
                </table>
            </div>
        </div>
        <button v-if="isParam && score === null" class="submitGrades" :disabled="submitting || !loaded" @click="submitGrades" aria-label="提交答案"></button>
    </div>
</template>
<script>
    export default {
        name: "EquipmentUnity"
    }
</script>
<script setup>
    import UnityEquipment from '../../../../components/unityEquipment/UnityEquipment.vue'
    import {groupNetTrain,groupNetTrainSubmitAnswer} from "../../../../common/api/TrainingDetails";
    import table from '../contactDocuments/table'
    import {analyzeChannel,netIP,applyScoringDetails} from "../contactDocuments/J210_742";
    import {ref,onMounted} from 'vue'
    import {useRoute, useRouter} from "vue-router";
    import {message} from 'ant-design-vue';
    const unityPath=ref('')
    const route = useRoute();
    const router = useRouter();
    const {J210_742}=table();
    const tableDocData=ref([]);
    const answer=ref('{}');
    const score=ref(null);
    const submitting=ref(false);
    const loaded=ref(false);
    const submissionAnswer=ref(null);
    const isParam=ref(false);
    onMounted(() => {
        if (route.query.deviceType && route.query.deviceType !== '') {
            unityPath.value = route.query.deviceType
        }
        groupNetTrainDetails(route.query.Id)
    })
    //获取考核信息
    const applyTrain=(train)=>{
        tableDocData.value=J210_742
        for (const item of JSON.parse(train.topic)) repeatAnswer(item)
        if (train.answer) {
            answer.value=train.answer
            const saved=JSON.parse(train.answer)
            tableDocData.value=analyzeChannel(tableDocData.value,saved.chananel1)
            tableDocData.value=netIP(tableDocData.value,saved.serialNumbers)
        }
        if (train.content && train.scoringRuleContent?.startsWith('{')) applyScoringDetails(tableDocData.value, JSON.parse(train.content))
        score.value=train.score
        loaded.value=true
    }
    const groupNetTrainDetails=async(id)=>{
        const res=await groupNetTrain({id})
        if (res.code !== 200) return
        applyTrain(res.data)
    }
    //处理模板和考核信息
    const repeatAnswer=(i)=>{
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
       if (submissionAnswer.value !== null || (score.value !== null && loaded.value)) return
       answer.value=JSON.stringify(e)
       tableDocData.value=analyzeChannel(tableDocData.value,e.chananel1)
       tableDocData.value=netIP(tableDocData.value,e.serialNumbers)
   }
   //
    const isShow=()=>{
        isParam.value=true;
    }
    const showResult=async(train)=>{
        applyTrain(train)
        await router.push({path:route.path.replace(/\/equipmentUnitysHJBW$/, '/testDetailsHJBW'),query:{Id:train.id}})
    }
    const recoverSubmittedResult=async()=>{
        try {
            const res=await groupNetTrain({id:route.query.Id})
            if (res.code !== 200) return 'unknown'
            if (res.data.score === null) return 'unsubmitted'
            await showResult(res.data)
            return 'completed'
        } catch {
            message.error('未能回读训练结果，已保留提交快照，请恢复连接后重试')
            return 'unknown'
        }
    }
    const submitGrades=async()=>{
        if (submitting.value || !loaded.value || score.value !== null) return
        if (submissionAnswer.value === null) submissionAnswer.value=answer.value
        submitting.value=true
        try {
            let res
            try {
                res=await groupNetTrainSubmitAnswer({id:route.query.Id,answer:submissionAnswer.value})
            } catch {
                if (await recoverSubmittedResult() !== 'completed') {
                    message.warning('提交结果尚未确认，再次提交将重试已固定的答案')
                }
                return
            }
            if (res.code !== 200) {
                if (await recoverSubmittedResult() === 'unsubmitted') submissionAnswer.value=null
                return
            }
            await showResult(res.data)
        } finally {
            submitting.value=false
        }
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