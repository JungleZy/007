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
    const ruleLoading=ref(false)
    const ruleReady=ref(false)
    let ruleRequest=0
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
            if (res.code === 200 && currentEquipmentType.value === e) {
                equipmentList.value=res.data;
                if (equipmentList.value.length) {
                    selectEquipment(equipmentList.value[0],0)
                    deviceTypeRule(equipmentList.value[0],0)
                } else {
                    selectEqu.value={}
                    selectRule.value={}
                    ruleRequest++
                    ruleReady.value=false
                    tableDocData.value=[]
                    TJPRule.value=[]
                }

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
        else tableDocData.value=[]
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
        if (!selectEqu.value.id || !tableDocData.value.length) {
            message.error('请选择支持综合组网训练的设备')
            return
        }
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
        }
        const deviceNumber=selectEqu.value.deviceNumber
        const trainingPath=route.matched[4].path + '/equipmentUnitysHJBW'
        groupNetTrainSaveTrain(saveTrain).then(res=>{
            if (res.code !== 200) return
            addDrillModal.value=false
            selectTablePage(1)
            router.push({
                path: trainingPath,
                query: {deviceType: deviceNumber, Id: res.data.id}
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
    const addRule=async()=>{
        if (ruleLoading.value || !ruleReady.value || !selectRule.value.id) {
            message.error('请重新打开配置并等待当前设备评分规则加载完成')
            return
        }
        const revision=ruleRequest
        const deviceId=selectRule.value.id
        const saveTrain={id:deviceScoringRule.value.id,deviceId,ruleContent:JSON.stringify(TJPRule.value)}
        ruleLoading.value=true
        try {
            const saved=await deviceScoringRuleSave(saveTrain)
            if (saved.code !== 200) return
            if (revision === ruleRequest) ruleReady.value=false
            const loaded=await deviceScoringRuleFindAllByDeviceId({deviceId})
            if (loaded.code !== 200 || revision !== ruleRequest) return
            if (!loaded.data || loaded.data.deviceId !== deviceId) {
                message.error('评分规则已保存，但未读取到对应设备规则，请重新打开配置')
                return
            }
            deviceScoringRule.value=loaded.data
            TJPRule.value=JSON.parse(loaded.data.ruleContent)
            ruleReady.value=true
            basicTrainDeployModal.value=false
            message.success('提交成功')
        } finally {
            if (revision === ruleRequest) ruleLoading.value=false
        }
    }
    //
    const basicDeploy=()=>{
        basicTrainDeployModal.value=true
        if (selectRule.value.id) deviceTypeRule(selectRule.value,isActiveTwo.value)
    }
    //
    const deviceTypeRule=(e,i)=>{
        const revision=++ruleRequest
        isActiveTwo.value = i
        selectRule.value=e
        ruleLoading.value=true
        ruleReady.value=false
        deviceScoringRule.value={}
        TJPRule.value=[]
        deviceScoringRuleFindAllByDeviceId({deviceId:selectRule.value.id}).then(res=>{
            if (res.code !== 200 || revision !== ruleRequest) return
            deviceScoringRule.value=res.data || {};
            if (res.data){
                TJPRule.value=JSON.parse(deviceScoringRule.value.ruleContent)
            }else {
                if (e.deviceNumber=="J210-742"){
                    TJPRule.value=J210_742Rule.map(rule => ({...rule}))
                }
                else TJPRule.value=[]
            }
            ruleReady.value=true
        }).finally(()=>{
            if (revision === ruleRequest) ruleLoading.value=false
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