import {message, Modal} from "ant-design-vue";
import {ref, reactive, toRaw, onMounted, toRefs, watch, provide, inject} from 'vue';
import {getAllDeviceType, getDeviceTypeEquipment} from "../../../../common/api/EquipmentApi";
import {groupNetTrainSaveTrain,deviceScoringRuleSave,deviceScoringRuleFindAllByDeviceId} from "../../../../common/api/TrainingDetails";
import table from '../contactDocuments/table'
import {useRouter,useRoute} from "vue-router";

export default function network(selectTablePage) {
    const {J210_742,J210_742Rule}=table()
    const addDrillModal = ref(false);
    const equipmentTypeList=ref([]);
    const currentEquipmentType=ref('');
    const equipmentList=ref([]);
    const tableDocData=ref([])
    const tableDocData2=ref([])
    const TJPRule=ref([])
    const titleHeaders=ref([])
    const selectEqu=ref({})
    const selectRule=ref({})
    const router = useRouter()
    const route = useRoute()
    const basicTrainDeployModal = ref(false);
    const deviceScoringRule=ref({})
    const isActive = ref(0)
    const isActiveTwo = ref(0)

    onMounted(() => {
        getAllDeviceTypeInfo()
    });
    /**
     * 新增训练
     */
    const addDrillModalInfo = () => {
        addDrillModal.value = true;
    };
    /*获取所有设备分类*/
    const getAllDeviceTypeInfo = () => {
        getAllDeviceType().then(res => {
            if (res.code === 200) {
                equipmentTypeList.value=res.data;
                if (equipmentTypeList.value.length!==0){
                    getDeviceTypeEquipmentInfo(equipmentTypeList.value[0].id)
                }
            }
        })
    }

    /*获取当前分类下面的所有设备信息*/
    const getDeviceTypeEquipmentInfo = (e) => {
        currentEquipmentType.value=e;
        getDeviceTypeEquipment({id: e}).then(res => {
            if (res.code === 200) {
                equipmentList.value=res.data;
                selectEquipment(equipmentList.value[0],0)
                deviceTypeRule(equipmentList.value[0],0)

            }
        })
    }
    //
    const selectEquipment=(e,i)=>{
        isActive.value = i
        selectEqu.value=e
        if (e.deviceNumber=="J210-742"){
            tableDocData.value=J210_742
        }
    }
    //
    const cliceTd=(y,x)=>{
        console.log(y,x);
    }
    //判断联络文件是否该有高亮头部
    const isTitle = (value)=>{
        if (titleHeaders.value.some(items=>items==value)){
            return true
        }else {
            return false
        }
    }

    //
    const createDrillInfo = ()=>{
        addDrillModal.value = false;
        let list=[]
        tableDocData.value.forEach((item,index)=>{
            item.forEach((items,indexs)=>{
                if (items.isParameter){
                    list.push({xy:[index,indexs],value:items})
                }
            })
        })
        let saveTrain={
            deviceType:currentEquipmentType.value,
            deviceId:selectEqu.value.id,
            topic: JSON.stringify(list),
            scoringRuleContent: "",
            answer:'',
        }
        groupNetTrainSaveTrain(saveTrain).then(res=>{
            selectTablePage(1)
          console.log(route.matched[4].path + '/equipmentUnitysHJBW')
            router.push({
                path: route.matched[4].path + '/equipmentUnitysHJBW',
                query: {deviceType: selectEqu.value.deviceNumber,Id: res.data.id,range:selectRule.value.id,}
            })
        })
    }
    const startTrain=(e)=>{
        if (e.score!==null){
            router.push({
                path: route.matched[4].path + '/testDetailsHJBW',
                query: {Id: e.id}
            })
        }else {
            message.error('该次测试暂无成绩')
        }
    }
    //
    const addRule=()=>{
        let saveTrain={
            id:deviceScoringRule.value.id,
            deviceId:selectRule.value.id,
            ruleContent: JSON.stringify(TJPRule.value),
        }
        deviceScoringRuleSave(saveTrain).then(res=>{
            basicTrainDeployModal.value=false
            message.success(('提交成功'))
        })
    }
    //
    const basicDeploy=()=>{
        basicTrainDeployModal.value=true
    }
    //
    const deviceTypeRule=(e,i)=>{
        isActiveTwo.value = i
        selectRule.value=e
        deviceScoringRuleFindAllByDeviceId({deviceId:selectRule.value.id}).then(res=>{
            deviceScoringRule.value=res.data;
            if (deviceScoringRule.value.length!==0){
                TJPRule.value=JSON.parse(deviceScoringRule.value.ruleContent)
            }else {
                if (e.deviceNumber=="J210-742"){
                    TJPRule.value=J210_742Rule
                }
            }
        })
    }
    //去重
    const unique=(arr)=>{
        const res=new Map()
        return arr.filter((e)=> !res.has(e['value']) && res.set(e['value'],1))
    }
    const blurParam=(e)=>{
        if (e.isParameter=='单台地址'){
            if ((e.range[0]<=e.value&&e.range[1]>=e.value)||(e.range[2]<=e.value&&e.range[3]>=e.value)){
                if (Number(e.value)<10){
                    e.value='00'+parseInt(e.value)
                }else if (Number(e.value)<100&&Number(e.value)>=10){
                    e.value='0'+parseInt(e.value)
                }else {
                    e.value=parseInt(e.value)
                }
                let list=[]
                tableDocData.value.forEach((item)=>{
                    item.forEach((items)=>{
                        if (items.isParameter=='单台地址'){
                            list.push(items)
                        }
                    })
                })
                let arr=unique(list)
                if (arr.length<list.length){
                    e.value=''
                    message.error('请不要输入重复值')
                }
            }else {
                e.value=''
                message.error('请输入范围值')
            }
        }else if (e.isParameter=='信道') {
            if (e.range[0]<=e.value&&e.range[1]>=e.value){
                if (Number(e.value)<10){
                    e.value='0'+Number(e.value).toFixed(5)
                }else {
                    e.value=Number(e.value).toFixed(5)
                }
            }else {
                e.value=''
                message.error('请输入范围值')
            }
        }else{
            if (e.range[0]<=e.value&&e.range[1]>=e.value){

            }else {
                e.value=''
                message.error('请输入范围值')
            }
        }
    }

    return {
        addDrillModal,
        equipmentTypeList,
        currentEquipmentType,
        equipmentList,
        tableDocData,
        tableDocData2,
        basicTrainDeployModal,
        TJPRule,
        isActive,
        isActiveTwo,

        getDeviceTypeEquipmentInfo,
        addDrillModalInfo,
        selectEquipment,
        cliceTd,
        isTitle,
        createDrillInfo,
        basicDeploy,
        addRule,
        deviceTypeRule,
        startTrain,
        blurParam
    }
}