<template>
    <div class="w-full h-full overflow-auto">
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
                            :style="{color:v.isParameter?v.params==v.value?'':v.params?'red':'':'', height: v.height + 'px', width: v.width + 'px' }">
                        <span >{{(v.isParameter && v.params)?v.params:v.value}}</span>
                    </td>
                </tr>
            </table>
        </div>
    </div>
</template>

<script>
    export default {
        name: "Index"
    }
</script>
<script setup>
    import {groupNetTrain} from "../../../../common/api/TrainingDetails";
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
    const score=ref(0);
    onMounted(() => {
        groupNetTrainDetails(route.query.Id,)
    })
    //获取考核信息
    const groupNetTrainDetails=(e,)=>{
        tableDocData.value=J210_742
        groupNetTrain({id:e}).then(res=>{
            let train=res.data;
            score.value=train.score;
            for (let i of JSON.parse(train.topic)){
                repeatAnswer(i,)
            }
            paramsChild(JSON.parse(train.answer))
        })
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
        tableDocData.value=analyzeChannel(tableDocData.value,e.chananel1)
        tableDocData.value=netIP(tableDocData.value,e.serialNumbers)
    }
</script>

<style scoped>
    .tableTd {
        border: 1px solid rgba(255, 255, 255, 0.5);
        text-align: center;
        color: #bfcde0;
        font-size: 14px;
    }
</style>